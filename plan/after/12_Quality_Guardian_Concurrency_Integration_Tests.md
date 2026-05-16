# 동시성 통합 테스트 — Lua 1차 race, ZSET swap 승격, 7일 창, 보상·reconcile

- **Assignee:** The Quality Guardian
- **Dependencies:** 11_Logic_Implementer_Creator_Students_And_My_Enrollments.md, 10_Logic_Implementer_Enrollment_Confirm_Cancel_And_Waitlist_Promotion.md, 14_Infra_Operator_Redis_DB_Reconcile_Runner.md
- **Definition of Done (DoD):**
  - 50개 스레드가 capacity=1인 강의에 동시 신청 시 정확히 1건 PENDING, 49건 WAITLISTED. 결과가 10회 연속 결정론적으로 동일하다. DB 행 수와 Redis ZCARD enrolled/waitlist 가 일치.
  - 50개 스레드가 capacity=10에 동시 신청 시 정확히 10건 PENDING, 40건 WAITLISTED.
  - 동시 cancel 2건이 동일 CONFIRMED enrollment를 대상으로 도착해도 한 건은 200(취소 성공), 다른 한 건은 200 멱등 응답. ZSET enrolled 에서 정확히 1회 ZREM.
  - WAITLISTED 5건이 있을 때 CONFIRMED 2건이 동시 cancel → 정확히 2건이 WAITLISTED→PENDING 승격. double promotion 없음, lost promotion 없음. ZSET 미러와 DB 모두 일관.
  - 7일 창 경계값 (`paidAt + 7d` 정확, `paidAt + 7d + 1ns`)이 각각 200/422로 응답.
  - **신규 — Redis disconnect during apply** → expect 503 fail-closed, DB enrollment row 없음, ZSET 도 변동 없음.
  - **신규 — DB INSERT 실패 보상** → `enrollmentRepository.save` 가 강제 throw 하도록 모킹 → Lua 가 ZADD 한 멤버가 `enrollment_compensate.lua` 로 ZREM 됨을 ZCARD 로 검증.
  - **신규 — Reconcile** → `redis-cli FLUSHDB` 후 reconcile runner 호출 → 모든 OPEN Class 의 ZCARD enrolled/waitlist 가 DB COUNT 와 정확히 일치.
  - 모든 테스트는 Testcontainers PostgreSQL + Redis로 실제 인프라 위에서 실행.

## Action Items (Checklist)

- [x] `src/test/java/com/example/liveclass/support/ConcurrencyTestSupport.java` — 헬퍼.
  - `void runConcurrently(int threadCount, Runnable task)` — `ExecutorService` + `CountDownLatch`로 starting gun 패턴. 모든 스레드가 동시에 시작하도록 `CyclicBarrier` 사용.
  - 결과 집계 헬퍼 `Map<String, AtomicInteger>` (status별 카운트).
- [x] `application/enrollment/LastSeatRaceConcurrencyTest.java` — `@IntegrationTest`.
  - `setUp()`: User CREATOR 1명 + Classmate 50명 생성, capacity=1 Class를 OPEN 상태로 준비.
  - `@RepeatedTest(10)` — 50 스레드가 동시에 `EnrollmentApplicationService.apply()` 호출.
  - 검증: `enrollmentRepository.countByClassIdAndStatus(classId, PENDING) == 1`, `WAITLISTED == 49`, `CANCELLED == 0`.
  - capacity=10 변형 케이스: PENDING == 10, WAITLISTED == 40.
- [x] `application/enrollment/CancelDoubleClickConcurrencyTest.java` — `@IntegrationTest`.
  - 같은 enrollment에 2 스레드가 동시 DELETE → 정확히 1건만 실제 상태 전이, 다른 1건은 멱등 응답. `Enrollment.version` 증가 1회.
- [x] `application/enrollment/WaitlistPromotionConcurrencyTest.java` — `@IntegrationTest`.
  - `setUp`: capacity=2, CONFIRMED 2건 + WAITLISTED 5건 (appliedAt 5개 서로 다름).
  - 2 스레드가 동시에 CONFIRMED 각각 cancel.
  - 검증: PENDING == 2 (승격된 두 명), WAITLISTED == 3, 승격된 두 명은 appliedAt이 가장 오래된 순서 2명.
  - `@RepeatedTest(10)`로 10회 연속 동일 결과 확인.
- [x] `application/enrollment/CancellationWindowBoundaryTest.java` — `@IntegrationTest`.
  - `Clock` 빈을 `@MockBean`으로 주입하거나 `EnrollmentApplicationService.cancel(now)`에 `Instant` 명시 전달.
  - paidAt = t0, cancel at t0 + Duration.ofDays(7) → 200.
  - cancel at t0 + Duration.ofDays(7).plusNanos(1) → 422.
  - cancel at t0 + Duration.ofDays(6).plusHours(23) → 200.
- [x] (Verify) `LastSeatRaceConcurrencyTest`의 10회 반복 실행 시간이 30초 이내인지 확인 (CI 타임아웃 방지). ZCARD enrolled / waitlist 와 DB COUNT 가 매 반복 후 일치하는지 검증.
- [x] (Verify) 동시성 테스트에 `@Order` 또는 `@DirtiesContext`로 테스트 간 격리 보장. 각 `@RepeatedTest`는 자체 `setUp/tearDown`에서 enrollments 테이블 truncate **및 Redis FLUSHDB**.
- [x] (Verify) 부분 유니크 인덱스로 인해 동일 (classId, classmateId) 50번 중복 신청은 49번 `DuplicateEnrollmentException`임을 별도 테스트로 확인. Lua 의 ZSCORE 중복 검사가 1차, DB partial unique index 가 마지막 방어선.
- [x] (NEW) `RedisDisconnectFailClosedTest.java` — Testcontainers Redis 컨테이너를 테스트 도중 `stop()` 시킨 후 apply 호출 → 503 응답 확인. enrollments 테이블 row count 변동 없음.
- [x] (NEW) `LuaCompensationTest.java` — `@SpyBean` 으로 `EnrollmentRepository.save` 가 첫 호출에서 `RuntimeException` 을 던지도록 설정. apply 호출 → 예외 응답 확인 + `ZCARD enrolled` 가 호출 전 값과 동일 (보상 Lua 가 ZADD 를 되돌렸음).
- [x] (NEW) `ReconcileTest.java` — capacity=3 Class 에 PENDING 2, WAITLISTED 1 적재한 뒤 `redisTemplate.getConnectionFactory().getConnection().flushDb()` 실행 → `reconcileRunner.reconcile(classId)` 호출 → `ZCARD enrolled == 2` && `ZCARD waitlist == 1`. ZRANGE 의 score 순서가 DB appliedAt 순과 일치.
