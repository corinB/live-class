# Enrollment Repository + partial unique index + EnrollmentMirrorService

- **Assignee:** The Logic Implementer
- **Dependencies:** 07_Logic_Implementer_Enrollment_Domain_Entity.md, 02_Infra_Operator_Redis_And_Cache_Config.md
- **Definition of Done (DoD):**
  - `EnrollmentRepository`가 정의되고 race-non-critical 쿼리를 제공한다 (ARCHITECTURE §4 채택 후 FOR UPDATE 계열은 사용하지 않음).
    - 활성 신청 중복 검사 — `findActiveByClassAndClassmate(classId, classmateId)`.
    - 카운트(검증·통계용) — `countActiveByClassId(classId)`, `countByClassIdAndStatus(classId, status)`.
    - 단일 조회 — `findById` (JPA 기본).
    - 페이징 — `findByClassmateIdAndStatusIn(classmateId, statuses, Pageable)`, `findByClassIdAndStatus(classId, status, Pageable)`.
    - **자동 close 지원** — `findByStatusAndPeriodEndDateBefore(ClassStatus, LocalDate)` 가 `Class` 쪽 Repository(`ClassRepository`) 에 추가됨 (이 태스크가 명시적으로 메모만, 실제 구현은 task 16 worker 가 추가).
  - DOCS Invariant Enrollment §3 부분 유니크 인덱스가 PostgreSQL에 적용된다 (`UNIQUE (class_id, classmate_id) WHERE status IN ('PENDING','CONFIRMED','WAITLISTED')`). ZSET mirror 가 1차 게이트지만 DB partial unique index 가 **마지막 정합성 방어선** (ARCHITECTURE §4.2 (4-c)).
  - `ddl-auto: update`는 partial index를 생성하지 못하므로 `ApplicationRunner`로 부팅 시 `CREATE UNIQUE INDEX IF NOT EXISTS ... WHERE ...` 실행.
  - `appliedAt`에 인덱스가 부여되어 FIFO 정렬 / reconcile 쿼리가 O(log n)로 동작한다.
  - **`EnrollmentMirrorService` 가 정의되어** task 02 에서 등록된 `RedisScript` 빈 3개를 주입받아 `RedisTemplate.execute(...)` 로 호출하는 thin wrapper 역할을 한다. **Lua RedisScript 빈 자체와 .lua 파일은 Pre-flight 4 결정으로 task 02 에서 이관 완료** — 본 task 는 그 빈들을 사용만 한다.

## Action Items (Checklist)

- [ ] `domain/enrollment/EnrollmentRepository.java` — `extends JpaRepository<Enrollment, UUID>`.
  - `@Query("select e from Enrollment e where e.classId = :classId and e.classmateId = :classmateId and e.status in (com.example.liveclass.domain.enrollment.EnrollmentStatus.PENDING, com.example.liveclass.domain.enrollment.EnrollmentStatus.CONFIRMED, com.example.liveclass.domain.enrollment.EnrollmentStatus.WAITLISTED)") Optional<Enrollment> findActiveByClassAndClassmate(UUID classId, UUID classmateId);`
  - `@Query("select count(e) from Enrollment e where e.classId = :classId and e.status in (PENDING, CONFIRMED)") long countActiveSeatsByClassId(UUID classId);`
  - `@Query("select count(e) from Enrollment e where e.classId = :classId and e.status = :status") long countByClassIdAndStatus(UUID classId, EnrollmentStatus status);` (reconcile 검증용)
  - `Page<Enrollment> findByClassmateIdAndStatusIn(UUID classmateId, Collection<EnrollmentStatus>, Pageable)` (my-enrollments용, 다음 태스크에서 사용).
  - `Page<Enrollment> findByClassIdAndStatus(UUID classId, EnrollmentStatus status, Pageable)` (Creator students 목록용).
  - `List<Enrollment> findByClassIdAndStatusInOrderByAppliedAtAsc(UUID classId, Collection<EnrollmentStatus>)` — reconcile 쿼리.
  - **FOR UPDATE 계열 메서드는 추가하지 않는다** — ARCHITECTURE §4 가 race-critical path 에서 PG row lock 을 채택하지 않음.
- [ ] `domain/enrollment/Enrollment.java`에 인덱스 어노테이션 추가: `@Table(name="enrollments", indexes = {@Index(name="idx_enroll_classid_appliedat", columnList="class_id, applied_at"), @Index(name="idx_enroll_classmate", columnList="classmate_id"), @Index(name="idx_enroll_classid_status", columnList="class_id, status")})`.
- [ ] `infrastructure/PartialIndexInitializer.java` — `@Component` + `ApplicationRunner`. JDBC로 다음 SQL 실행 (멱등).
  - `CREATE UNIQUE INDEX IF NOT EXISTS uniq_active_enrollment ON enrollments (class_id, classmate_id) WHERE status IN ('PENDING','CONFIRMED','WAITLISTED');`
- [ ] `application/enrollment/EnrollmentMirrorService.java` 작성 (skeleton).
  - 첫 줄 한국어 주석 `// task 02 에서 등록된 RedisScript 3개를 호출하는 thin wrapper. apply / cancelAndMaybePromote / compensateApply / primeClassStatusMirror 메서드 제공.`
  - `@Service`. 의존: `StringRedisTemplate redisTemplate`, `RedisScript<String> enrollmentApplyScript`, `RedisScript<List> enrollmentCancelPromoteScript`, `RedisScript<Long> enrollmentCompensateScript`.
  - 메서드 시그니처만 정의 (실제 호출 본문은 task 09/10 에서 구현). 본 task 에서는 빈 메서드 body 또는 `throw new UnsupportedOperationException("implemented in task 09/10")` placeholder.
  - 단 `primeClassStatusMirror(UUID classId, ClassStatus status)` 는 task 02 의 RedisTemplate 만으로 동작 가능하므로 본 task 에서 실제 구현 — `redisTemplate.opsForValue().set("class:status:" + classId, status.name(), Duration.ofMinutes(5))`.
- [ ] (Verify) `domain/enrollment/EnrollmentRepositoryIntegrationTest.java` — Testcontainers + `@DataJpaTest`.
  - 동일 (classId, classmateId)로 active 두 건 insert 시도 → `DataIntegrityViolationException` (partial unique index 동작).
  - CANCELLED 상태로 한 건 + 새 PENDING 한 건은 같은 (classId, classmateId)여도 성공.
  - `countActiveSeatsByClassId`가 PENDING+CONFIRMED만 카운트하고 CANCELLED/WAITLISTED는 제외.
  - `findByClassIdAndStatusInOrderByAppliedAtAsc`가 appliedAt 오름차순으로 정렬되어 반환 (reconcile 입력으로 사용 가능).
- [ ] (Verify) `infrastructure/PartialIndexInitializerTest.java` — 부팅 후 `pg_indexes` 시스템 뷰로 `uniq_active_enrollment` 존재 확인.
- [ ] (Verify) `config/LuaScriptConfigTest.java` — `@SpringBootTest` 로 컨텍스트 로드 후 3개 RedisScript 빈이 모두 주입되는지 검증. 스크립트 본문은 placeholder 이므로 실행 검증은 task 09/10 에서.
