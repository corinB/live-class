# Enrollment Repository, partial unique index, FOR UPDATE SKIP LOCKED 쿼리

- **Assignee:** The Logic Implementer
- **Dependencies:** 07_Logic_Implementer_Enrollment_Domain_Entity.md
- **Definition of Done (DoD):**
  - `EnrollmentRepository`가 정의되고 다음 4개 핵심 쿼리가 동작한다.
    - 활성 신청 중복 검사 — `findActiveByClassAndClassmate(classId, classmateId)`.
    - 잔여 정원 계산 — `countActiveByClassId(classId)` (PENDING + CONFIRMED 합).
    - FOR UPDATE — `findByIdForUpdate(enrollmentId)`.
    - 대기열 선두 SKIP LOCKED — `findNextWaitlistedForUpdateSkipLocked(classId)`.
  - DOCS Invariant Enrollment §3 부분 유니크 인덱스가 PostgreSQL에 적용된다 (`UNIQUE (class_id, classmate_id) WHERE status IN ('PENDING','CONFIRMED','WAITLISTED')`).
  - `ddl-auto: update`는 partial index를 생성하지 못하므로 `@PostConstruct` 또는 `ApplicationRunner`로 부팅 시 `CREATE UNIQUE INDEX IF NOT EXISTS ... WHERE ...` 한 줄 실행한다.
  - `appliedAt`에 인덱스가 부여되어 FIFO 정렬이 O(log n)로 동작한다.

## Action Items (Checklist)

- [ ] `domain/enrollment/EnrollmentRepository.java` — `extends JpaRepository<Enrollment, UUID>`.
  - `@Query("select e from Enrollment e where e.classId = :classId and e.classmateId = :classmateId and e.status in (com.example.liveclass.domain.enrollment.EnrollmentStatus.PENDING, com.example.liveclass.domain.enrollment.EnrollmentStatus.CONFIRMED, com.example.liveclass.domain.enrollment.EnrollmentStatus.WAITLISTED)") Optional<Enrollment> findActiveByClassAndClassmate(UUID classId, UUID classmateId);`
  - `@Query("select count(e) from Enrollment e where e.classId = :classId and e.status in (PENDING, CONFIRMED)") long countActiveSeatsByClassId(UUID classId);`
  - `@Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select e from Enrollment e where e.id = :id") Optional<Enrollment> findByIdForUpdate(UUID id);`
  - `findNextWaitlistedForUpdateSkipLocked(UUID classId)` — `@Lock(LockModeType.PESSIMISTIC_WRITE) @QueryHints({@QueryHint(name="jakarta.persistence.lock.timeout", value="-2")})` 로 SKIP_LOCKED 활성화. JPQL: `select e from Enrollment e where e.classId = :classId and e.status = WAITLISTED order by e.appliedAt asc`. `setMaxResults(1)`은 `Pageable.ofSize(1)`로 전달.
  - `Page<Enrollment> findByClassmateIdAndStatusIn(UUID classmateId, Collection<EnrollmentStatus>, Pageable)` (my-enrollments용, 다음 태스크에서 사용).
  - `Page<Enrollment> findByClassIdAndStatus(UUID classId, EnrollmentStatus status, Pageable)` (Creator students 목록용).
- [ ] `domain/enrollment/Enrollment.java`에 인덱스 어노테이션 추가: `@Table(name="enrollments", indexes = {@Index(name="idx_enroll_classid_appliedat", columnList="class_id, applied_at"), @Index(name="idx_enroll_classmate", columnList="classmate_id")})`.
- [ ] `infrastructure/PartialIndexInitializer.java` — `@Component` + `ApplicationRunner`. JDBC로 다음 SQL 실행.
  - `CREATE UNIQUE INDEX IF NOT EXISTS uniq_active_enrollment ON enrollments (class_id, classmate_id) WHERE status IN ('PENDING','CONFIRMED','WAITLISTED');`
  - 멱등하므로 매 부팅 시 실행해도 안전.
- [ ] (Verify) `domain/enrollment/EnrollmentRepositoryIntegrationTest.java` — Testcontainers + `@DataJpaTest`.
  - 동일 (classId, classmateId)로 active 두 건 insert 시도 → `DataIntegrityViolationException`.
  - CANCELLED 상태로 한 건 + 새 PENDING 한 건은 같은 (classId, classmateId)여도 성공 (partial index).
  - `countActiveSeatsByClassId`가 PENDING+CONFIRMED만 카운트하고 CANCELLED/WAITLISTED는 제외하는지 검증.
  - `findNextWaitlistedForUpdateSkipLocked`가 appliedAt 오름차순으로 최오래된 WAITLISTED 1건 반환.
- [ ] (Verify) `infrastructure/PartialIndexInitializerTest.java` — 부팅 후 `pg_indexes` 시스템 뷰로 `uniq_active_enrollment` 존재 확인.
