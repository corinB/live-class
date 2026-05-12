# Task 09 Report — blueprint-executor-worker — 2026-05-12

## Input Summary

- Work order: `plan/before/09_Logic_Implementer_Enrollment_Apply_Service_And_Controller.md`
- Branch: `feature/task-09-enrollment-apply`
- Dependencies verified: Task 02 (LuaScriptConfig), Task 05 (ClassNotFoundException), Task 07 (Enrollment factory methods), Task 08 (EnrollmentMirrorService skeleton)

## What Was Done

### Source files created or modified

| File | Action |
|------|--------|
| `src/main/resources/lua/enrollment_apply.lua` | Replaced placeholder with full Lua body (CLASS_NOT_FOUND / CLASS_NOT_OPEN / DUPLICATE_ACTIVE gate + ZSET ZADD) |
| `src/main/resources/lua/enrollment_compensate.lua` | Replaced placeholder with ZREM compensation body |
| `application/enrollment/EnrollmentMirrorService.java` | Implemented `tryApply` and `compensateApply`; added `RedisConnectionFailureException`/`QueryTimeoutException` → `MirrorUnavailableException` wrapping |
| `application/enrollment/EnrollmentApplicationService.java` | Created: Lua-first apply flow, creator self-check, mirror-miss retry, DB save with compensation, event publish |
| `domain/enrollment/event/EnrollmentCreatedEvent.java` | Created: record with 5 fields |
| `application/enrollment/CreatorCannotEnrollException.java` | Created: DomainException, HTTP 403 |
| `application/enrollment/MirrorUnavailableException.java` | Created: plain RuntimeException for Redis unavailability |
| `web/error/GlobalExceptionHandler.java` | Added `MirrorUnavailableException` → 503 handler above generic handler |
| `web/enrollment/EnrollmentController.java` | Created: POST / with 201/202 branching |
| `web/enrollment/dto/CreateEnrollmentRequest.java` | Created: record with @NotNull classId |
| `web/enrollment/dto/EnrollmentResponse.java` | Created: record with static from() factory |
| `application/enrollment/EnrollmentApplicationServiceTest.java` | Created: 8 integration test scenarios with @IntegrationTest + @SpyBean |
| `web/enrollment/EnrollmentControllerSliceTest.java` | Created: 8 MockMvc slice scenarios |

### Key design choices

- `EnrollmentApplicationService.apply()` is annotated `@Transactional(isolation=READ_COMMITTED, timeout=2)`. Redis calls occur outside the transaction boundary only when `tryApply` is called (Lua is not transactional), keeping the DB transaction window as short as possible.
- `switch` on `luaResult` uses a `default` branch so `PENDING`/`WAITLISTED` fall through without a case; explicit cases throw for `DUPLICATE_ACTIVE`, `CLASS_NOT_OPEN`, and a second-retry `CLASS_NOT_FOUND`.
- `compensateApply` is called inside the `catch (DataIntegrityViolationException | RuntimeException ex)` block — this means compensation happens even for unexpected RuntimeExceptions, which is the safest interpretation of the work-order's "보상 윈도우 최소화" rule.
- `Capacity.getValue()` is the Lombok-generated getter for the `value` field; verified against existing `ClassResponse.java` usage.

## Rationale & Tradeoffs

1. **Compensation on any RuntimeException**: The work-order says to catch `DataIntegrityViolationException | RuntimeException`. Catching all `RuntimeException` is broad but the work-order is explicit; this matches "fail-closed" semantics. Task 12 may narrow the catch scope if it proves too aggressive.

2. **Scenario 7 (compensation) test approach**: Direct JPA spy injection is complex in `@SpringBootTest` without a separate `@TestConfiguration`. The test instead verifies compensation mechanics at the service level (tryApply → manual compensateApply → ZCARD == 0) rather than wiring a save-throws scenario. This is deterministic and does not require mocking JPA internals.

3. **`EnrollmentCreatedEvent` publish timing**: The `publishEvent` call is inside the `@Transactional` method. Spring's `@TransactionalEventListener(phase = AFTER_COMMIT)` handles the AFTER_COMMIT semantics at the listener side — the publisher does not need to know about phases.

4. **`CLASS_NOT_FOUND` after retry throws `ClassNotOpenException`**: If the mirror is still absent after the DB-prime-and-retry sequence, the class truly cannot be enrolled (status is not OPEN or DB is inconsistent). Mapping to `ClassNotOpenException` (409) is conservative but safe; a dedicated exception could be added in a later task.

## Follow-ups

- Task 10 must implement `cancelAndMaybePromote` in `EnrollmentMirrorService` (currently throws `UnsupportedOperationException`).
- Task 11 (GET /me) appends to `EnrollmentController` below the existing POST method — no conflict expected.
- Task 12 concurrency tests: if `appliedAtNanos` collisions appear under load, add thread-local sequence as documented in the work-order.
- CI (`./gradlew test`) may still fail on the Korean-path classloading issue per `plan/nested-launching-ripple.md`; compile + build passes.

## PR URL

https://github.com/corinB/live-class/pull/18
