# Enrollment apply 서비스 (race-safe SELECT FOR UPDATE) + Controller

- **Assignee:** The Logic Implementer
- **Dependencies:** 08_Logic_Implementer_Enrollment_Repository_And_Schema.md, 05_Logic_Implementer_Class_Repository_Service_Controller.md
- **Definition of Done (DoD):**
  - `POST /api/enrollments` 가 ARCHITECTURE §6.1 시퀀스 다이어그램을 그대로 구현한다.
    1. `Class` row를 `findByIdForUpdate`로 잠금.
    2. `status != OPEN` → `ClassNotOpenException`.
    3. 활성 신청 중복 검사 (`findActiveByClassAndClassmate`).
    4. `countActiveSeatsByClassId` < capacity → PENDING insert, 201.
    5. >= capacity → WAITLISTED insert, 202.
  - 이벤트 `EnrollmentCreatedEvent`가 `AFTER_COMMIT`에 발행되어 `class:enrolledCount` 캐시를 evict한다.
  - Creator 본인이 자기 강의에 신청 시 거부 (DOCS Invariant Enrollment §4).
  - 동일 (classId, classmateId)로 동시 중복 신청 시 application 레벨 검사 + DB partial unique index 두 단계 방어.
  - 트랜잭션 안에서 외부 호출 없음. statement timeout 500ms 적용.

## Action Items (Checklist)

- [ ] `application/enrollment/EnrollmentApplicationService.java` — `@Service`.
  - `EnrollmentResponse apply(UUID classmateId, UUID classId, Instant now)` — `@Transactional(isolation=READ_COMMITTED, timeout=2)`.
    - `classRepository.findByIdForUpdate(classId).orElseThrow(ClassNotFoundException)`.
    - `if (!clazz.isOpenForEnrollment(now)) throw new ClassNotOpenException();`
    - `if (clazz.getCreatorId().equals(classmateId)) throw new CreatorCannotEnrollException();` (403).
    - `enrollmentRepository.findActiveByClassAndClassmate(classId, classmateId).ifPresent(e -> throw new DuplicateEnrollmentException());`
    - `long count = enrollmentRepository.countActiveSeatsByClassId(classId);`
    - `Enrollment e = (count < clazz.getCapacity().value()) ? Enrollment.apply(...) : Enrollment.waitlist(...);`
    - `enrollmentRepository.save(e);`
    - `eventPublisher.publishEvent(new EnrollmentCreatedEvent(e.getId(), classId, classmateId, e.getStatus(), now));`
    - DB partial unique index 위반(`DataIntegrityViolationException`)은 catch해서 `DuplicateEnrollmentException`으로 변환.
- [ ] `domain/enrollment/event/EnrollmentCreatedEvent.java` — `record(UUID enrollmentId, UUID classId, UUID classmateId, EnrollmentStatus status, Instant occurredAt)`.
- [ ] `application/enrollment/EnrollmentCacheInvalidator.java` — `@TransactionalEventListener(phase=AFTER_COMMIT)`로 `EnrollmentCreatedEvent` 받아 `class:enrolledCount` 캐시 evict.
- [ ] `application/enrollment/CreatorCannotEnrollException.java` — `DomainException` 상속, status 403.
- [ ] `domain/clazz/ClassNotFoundException.java` — `DomainException`, status 404.
- [ ] `web/enrollment/EnrollmentController.java` — `@RestController @RequestMapping("/api/enrollments")`.
  - `POST /` — `@CurrentUserId UUID classmateId`, body `{classId: UUID}` → 201 (PENDING) 또는 202 (WAITLISTED). HTTP status는 `ResponseEntity.status(...)`로 분기.
- [ ] `web/enrollment/dto/CreateEnrollmentRequest.java`, `EnrollmentResponse.java` (record).
- [ ] `application/enrollment/EnrollmentApplicationServiceTest.java` — `@IntegrationTest`로 다음 시나리오.
  - 정상 신청 (capacity=10, 현재 0건) → PENDING 201.
  - 정원 가득 (capacity=2, PENDING 2건 있음) → WAITLISTED 202.
  - DRAFT 강의 신청 → `ClassNotOpenException` 409.
  - 중복 신청 → `DuplicateEnrollmentException` 409.
  - Creator가 자기 강의 신청 → `CreatorCannotEnrollException` 403.
- [ ] (Verify) `web/enrollment/EnrollmentControllerSliceTest.java` — `@WebMvcTest`로 인증 헤더, body 검증, 응답 status 분기 확인.
- [ ] (Verify) `EnrollmentApplicationServiceTest`에 `Class.status = OPEN` 직후 `count = capacity` 인 경계 케이스 (마지막 자리 하나 남은 상태) 단일 스레드 검증. 동시성 케이스는 다음 태스크.
