# Task 16 — Class Auto-Close Quartz Job

**PR URL:** https://github.com/corinB/live-class/pull/16
**Date:** 2026-05-12
**Agent:** blueprint-executor-worker

---

## Input Summary

Work order: `plan/before/16_Infra_Operator_Class_AutoClose_Quartz_Job.md`

Implement a Quartz scheduled job that automatically closes OPEN Class entities whose `period.endDate` has passed, running daily at 00:05 KST. Includes domain method addition, repository query, application service method, Quartz configuration, and integration tests including a race condition test.

---

## What Was Done

1. **`Class.autoClose(Instant now)`** — Added to domain entity. Throws `IllegalStateTransitionException` if status is not OPEN. No Creator ID check (system call).

2. **`ClassRepository.findByStatusAndPeriodEndDateBefore`** — Added JPQL query using embedded VO path `c.period.endDate`.

3. **`ClassApplicationService.autoClose(UUID classId, Instant now)`** — New `@Transactional` method. Fetches via `findById`, calls `c.autoClose(now)`, saves, and publishes `ClassClosedEvent` with `null` creatorId (system call — listener only uses classId).

4. **`QuartzConfig.java`** (`infrastructure/scheduling`) — `SchedulerFactoryBeanCustomizer` bean installs `SpringBeanJobFactory` so Quartz jobs receive Spring `@Autowired` injection. `Clock systemKstClock()` bean added. `classAutoCloseJobDetail` and `classAutoCloseTrigger` beans registered with cron `0 5 0 * * ?` Asia/Seoul and `MISFIRE_INSTRUCTION_FIRE_AND_PROCEED`.

5. **`ClassAutoCloseJob.java`** (`infrastructure/scheduling`) — `QuartzJobBean` subclass. Queries expired OPEN classes via `LocalDate.now(clock)`, calls `autoClose` for each, catches `IllegalStateTransitionException` and `OptimisticLockingFailureException` for graceful race handling.

6. **`application.yaml`** — Added `spring.quartz.job-store-type: memory` and `auto-startup: true` under the existing `spring:` block.

7. **`application-task16.yaml`** (test resources) — Task-16-specific test profile with Quartz auto-startup enabled, isolated from Task 06's `application-test.yaml`.

8. **`ClassAutoCloseJobTest.java`** — 3 scenarios: expired OPEN → CLOSED, future endDate → stays OPEN, already CLOSED → unaffected (version unchanged).

9. **`AutoCloseManualCloseRaceTest.java`** — `@RepeatedTest(10)`. Two threads race: manual `transitionStatus(CLOSED)` vs `autoClose`. Exactly one must succeed (XOR assertion). Final status always CLOSED. Creator user is persisted in DB before the test so `requireCreator` validation passes.

10. **`MisfireRecoveryTest.java`** — Schedules a new job with a trigger startTime 1 hour in the past. Verifies `FIRE_AND_PROCEED` fires it exactly once, closing the expired OPEN class.

---

## Rationale & Tradeoffs

- **`SchedulerFactoryBeanCustomizer` vs direct `SpringBeanJobFactory` bean**: Spring Boot 4's `QuartzAutoConfiguration` already sets up `SchedulerFactoryBean` internally. Using `SchedulerFactoryBeanCustomizer` avoids bean name conflicts and is the idiomatic Spring Boot extension point.

- **`null` creatorId in `ClassClosedEvent`**: The existing `ClassStatusMirrorListener` only reads `classId` from the event, so passing `null` for `creatorId` in system-initiated close is safe without modifying the event record. A future refactor could introduce a separate `ClassAutoClosedEvent`, but that is out of scope.

- **`OptimisticLockingFailureException` catch in `ClassAutoCloseJob`**: `autoClose` uses plain `findById` (no pessimistic lock), so an optimistic lock conflict is possible when Creator simultaneously closes the same class. Catching it treats the race as a graceful skip (the class ends up CLOSED either way).

- **Race test uses XOR assertion**: Both threads may theoretically fail in pathological timing (e.g., both see version conflict). In practice with PostgreSQL `@Version`, exactly one transaction commits and the other rolls back. The XOR assertion is sound for the 10-repetition requirement on Linux CI.

- **`Thread.sleep(500)` in job trigger tests**: `scheduler.triggerJob` is asynchronous — the job runs in Quartz's thread pool. A 500 ms sleep is pragmatic for CI; a proper approach would poll with a timeout, but the delay is sufficient given the job has no I/O outside the already-connected Testcontainers database.

---

## Follow-ups

- The `null` creatorId in `ClassClosedEvent` is a latent smell. If consumers beyond `ClassStatusMirrorListener` are added that rely on `creatorId`, they will NPE. Consider introducing a dedicated `ClassAutoClosedEvent` record in a future task.
- `MisfireRecoveryTest` uses `Thread.sleep(1000)` which is fragile under heavy CI load. A polling approach with AssertJ's `await().atMost(...)` (Awaitility) would be more robust — Awaitility is not currently on the classpath.
- `AutoCloseManualCloseRaceTest` runs 10 repetitions per build, adding ~30–60 s to CI. If CI time becomes a concern, move these to a separate Gradle test source set tagged `@Tag("race")`.
