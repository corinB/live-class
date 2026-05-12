# Enrollment apply 서비스 (Lua-first race-safe) + Controller

- **Assignee:** The Logic Implementer
- **Dependencies:** 08_Logic_Implementer_Enrollment_Repository_And_Schema.md, 05_Logic_Implementer_Class_Repository_Service_Controller.md
- **Definition of Done (DoD):**
  - `POST /api/enrollments` 가 ARCHITECTURE §6.1 시퀀스 다이어그램을 그대로 구현한다 (Lua-first, DB-second).
    1. `enrollment_apply.lua` 호출 (KEYS=[enrolled:{c}, waitlist:{c}, class:status:{c}], ARGV=[capacity, classmateId, appliedAtNanos]).
    2. 반환값 `PENDING` / `WAITLISTED` 면 DB INSERT.
    3. 반환값 `DUPLICATE_ACTIVE` → 409. `CLASS_NOT_OPEN` → 409. `CLASS_NOT_FOUND` (mirror miss) → DB fallback + mirror 채움 + Lua 재시도 1회.
    4. DB INSERT 실패 시 즉시 `enrollment_compensate.lua` 호출하여 ZSET 갱신 되돌림.
  - 이벤트 `EnrollmentCreatedEvent`가 `AFTER_COMMIT` 에 발행된다. **Spring Cache 미사용 (Pre-flight 5) — cache evict 핸들러 없음.** 이벤트는 향후 read-model / 통계 / 알림 등 다른 listener 가 받을 수 있도록 발행만.
  - Creator 본인이 자기 강의에 신청 시 거부 (DOCS Invariant Enrollment §4). Lua 보다 먼저 application service 가 검사.
  - 트랜잭션 안에서 외부 호출 없음 (보상 윈도우 최소화). statement timeout 500ms.
  - Lua 호출이 Redis 연결 실패로 throw 하면 503 fail-closed (ARCHITECTURE §7.1).

## Action Items (Checklist)

- [x] `src/main/resources/lua/enrollment_apply.lua` — task 08 placeholder 를 본문으로 채움. 다음 로직.
  ```lua
  -- KEYS[1]=enrolled:{classId}, KEYS[2]=waitlist:{classId}, KEYS[3]=class:status:{classId}
  -- ARGV[1]=capacity (number string), ARGV[2]=classmateId (UUID string), ARGV[3]=appliedAtNanos (number string)
  local status = redis.call('GET', KEYS[3])
  if not status then return 'CLASS_NOT_FOUND' end
  if status ~= 'OPEN' then return 'CLASS_NOT_OPEN' end
  if redis.call('ZSCORE', KEYS[1], ARGV[2]) then return 'DUPLICATE_ACTIVE' end
  if redis.call('ZSCORE', KEYS[2], ARGV[2]) then return 'DUPLICATE_ACTIVE' end
  local current = tonumber(redis.call('ZCARD', KEYS[1]))
  local cap = tonumber(ARGV[1])
  if current < cap then
    redis.call('ZADD', KEYS[1], ARGV[3], ARGV[2])
    return 'PENDING'
  else
    redis.call('ZADD', KEYS[2], ARGV[3], ARGV[2])
    return 'WAITLISTED'
  end
  ```
  - 첫 줄 한국어 주석 `-- enrollment_apply.lua — Lua 1차 게이트: 중복 검사 + 정원 검사 + ZSET ADD 원자 실행`.
- [x] `src/main/resources/lua/enrollment_compensate.lua` — 본문 작성.
  ```lua
  -- KEYS[1]=enrolled:{classId}, KEYS[2]=waitlist:{classId}, ARGV[1]=classmateId
  local r1 = redis.call('ZREM', KEYS[1], ARGV[1])
  local r2 = redis.call('ZREM', KEYS[2], ARGV[1])
  return r1 + r2
  ```
- [x] `application/enrollment/EnrollmentMirrorService.java` — `@Service`. Lua 실행 래퍼.
  - `String tryApply(UUID classId, UUID classmateId, long appliedAtNanos, int capacity)` — `redisTemplate.execute(enrollmentApplyScript, List.of("enrolled:"+classId, "waitlist:"+classId, "class:status:"+classId), String.valueOf(capacity), classmateId.toString(), String.valueOf(appliedAtNanos))` 반환.
  - `void compensateApply(UUID classId, UUID classmateId)` — `enrollmentCompensateScript` 호출.
  - `void primeClassStatusMirror(UUID classId, ClassStatus status)` — `redisTemplate.opsForValue().set("class:status:"+classId, status.name(), Duration.ofMinutes(5))`.
- [x] `application/enrollment/EnrollmentApplicationService.java` — `@Service`.
  - `EnrollmentResponse apply(UUID classmateId, UUID classId, Instant now)` — `@Transactional(isolation=READ_COMMITTED, timeout=2)`.
    - `Class clazz = classRepository.findById(classId).orElseThrow(ClassNotFoundException::new);`
    - `if (clazz.getCreatorId().equals(classmateId)) throw new CreatorCannotEnrollException();` (403, Lua 전에 검사).
    - `long appliedAtNanos = now.getEpochSecond() * 1_000_000_000L + now.getNano();` (간단형, task 12 가 동시성 테스트에서 충돌 시 thread-local sequence 추가).
    - `String luaResult = mirrorService.tryApply(classId, classmateId, appliedAtNanos, clazz.getCapacity().getValue());`
    - `if ("CLASS_NOT_FOUND".equals(luaResult))` → `mirrorService.primeClassStatusMirror(classId, clazz.getStatus()); luaResult = mirrorService.tryApply(...);` (재시도 1회).
    - `switch (luaResult)` — `DUPLICATE_ACTIVE` → 409 `DuplicateEnrollmentException`. `CLASS_NOT_OPEN` → 409 `ClassNotOpenException`. `PENDING` / `WAITLISTED` 만 다음으로.
    - `Enrollment e = "PENDING".equals(luaResult) ? Enrollment.apply(...) : Enrollment.waitlist(...);`
    - `try { enrollmentRepository.save(e); } catch (DataIntegrityViolationException | RuntimeException ex) { mirrorService.compensateApply(classId, classmateId); throw mapDbException(ex); }`
    - `eventPublisher.publishEvent(new EnrollmentCreatedEvent(e.getId(), classId, classmateId, e.getStatus(), now));`
- [x] `domain/enrollment/event/EnrollmentCreatedEvent.java` — `record(UUID enrollmentId, UUID classId, UUID classmateId, EnrollmentStatus status, Instant occurredAt)`.
- [x] ~~`application/enrollment/EnrollmentCacheInvalidator.java`~~ — **항목 삭제 (Pre-flight 5 결정)**. Spring Cache 미사용 → cache evict listener 불요.
- [x] `application/enrollment/CreatorCannotEnrollException.java` — `DomainException` 상속, status 403.
- [x] `application/enrollment/MirrorUnavailableException.java` — Redis 연결 실패 매핑용. `GlobalExceptionHandler` 에서 503 매핑. `EnrollmentMirrorService` 가 `RedisConnectionFailureException` / `QueryTimeoutException` 을 catch 해 변환.
- [x] `domain/clazz/ClassNotFoundException.java` — `DomainException`, status 404. (task 05 에서 이미 존재, 확인 완료)
- [x] `web/enrollment/EnrollmentController.java` — `@RestController @RequestMapping("/api/enrollments")`.
  - `POST /` — `@CurrentUserId UUID classmateId`, body `{classId: UUID}` → 201 (PENDING) 또는 202 (WAITLISTED). `ResponseEntity.status(...)` 로 분기.
- [x] `web/enrollment/dto/CreateEnrollmentRequest.java`, `EnrollmentResponse.java` (record).
- [x] (Verify) `application/enrollment/EnrollmentApplicationServiceTest.java` — `@IntegrationTest` (Testcontainers Postgres + Redis).
  - 정상 신청 (capacity=10, 현재 0건) → PENDING 201. ZCARD enrolled == 1.
  - 정원 가득 (capacity=2, PENDING 2건 있음) → WAITLISTED 202. ZCARD waitlist == 1.
  - DRAFT 강의 신청 → `ClassNotOpenException` 409 (Redis class:status mirror 가 "DRAFT" 일 때).
  - 중복 신청 → `DuplicateEnrollmentException` 409.
  - Creator가 자기 강의 신청 → `CreatorCannotEnrollException` 403.
  - Redis 연결 실패 시뮬레이션 → 503.
  - 보상 시나리오 — DB INSERT 가 의도적으로 실패하도록 모킹 → ZCARD enrolled 가 호출 전 상태로 복귀.
  - Class status mirror miss → application service 가 DB fallback 후 mirror 채우고 Lua 재시도, 결과 정상.
- [x] (Verify) `web/enrollment/EnrollmentControllerSliceTest.java` — `@WebMvcTest`로 인증 헤더, body 검증, 응답 status 분기 확인 (service 는 `@MockBean`).
