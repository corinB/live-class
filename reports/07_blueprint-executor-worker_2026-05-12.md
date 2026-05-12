# Task 07 — Enrollment Aggregate Entity + VOs

**Agent:** blueprint-executor-worker
**Date:** 2026-05-12
**Branch:** feature/task-07-enrollment-entity

## 1. Summary

Implemented the `Enrollment` Aggregate Root with all required Value Objects, domain exceptions, and unit tests for the enrollment domain.

## 2. Files Created

### Main Sources
- `domain/enrollment/EnrollmentId.java` — UUID wrapper record
- `domain/enrollment/EnrollmentStatus.java` — enum PENDING/CONFIRMED/CANCELLED/WAITLISTED
- `domain/enrollment/CancellationWindow.java` — closed-interval cancellation window VO with SEVEN_DAYS constant
- `domain/enrollment/Enrollment.java` — JPA entity, Aggregate Root with 6 intent methods
- `domain/enrollment/IllegalStateTransitionException.java` — 409, local copy (task-04 not yet merged)
- `domain/enrollment/OutsideCancellationWindowException.java` — 422
- `domain/enrollment/AlreadyCancelledException.java` — 409
- `domain/enrollment/DuplicateEnrollmentException.java` — 409
- `domain/enrollment/ClassNotOpenException.java` — 409
- `domain/clazz/ClassId.java` — compile dependency stub (identical to task-04 worktree version)

### Test Sources
- `domain/enrollment/EnrollmentTest.java` — 10 scenarios per work order
- `domain/enrollment/CancellationWindowTest.java` — 3 boundary value tests

## 3. Build / Test Results

- `./gradlew clean build -x test` → **BUILD SUCCESSFUL** (compile + bootJar pass)
- `./gradlew test --tests "...EnrollmentTest" --tests "...CancellationWindowTest"` → **ClassNotFoundException** at runtime — this is the known Korean-path classpath issue documented in CLAUDE.md (`plan/nested-launching-ripple.md`). Test .class files compiled successfully and exist at `build/classes/java/test/com/example/liveclass/domain/enrollment/`. The issue is not in the test code itself but in the Gradle test worker's inability to read the classpath argument file when the path contains non-ASCII characters.

## 4. Decisions / Notes

- `IllegalStateTransitionException` created locally in `domain.enrollment` package rather than cross-importing from `domain.clazz`, because task-04 branch has not been merged to main yet and the class does not exist in this worktree's source tree. The two copies are identical in behavior; they will need reconciliation when task-04 merges.
- `ClassId.java` stub created under `domain/clazz/` for compile dependency only. Content is identical to the task-04 worktree version, so merge conflict is minimal.
- `cancel()` branch order: CANCELLED check first, then CONFIRMED + out-of-window check, then catch-all. This matches the work-order hint §4 exactly.
- `appliedAt` field is `updatable=false` — `promoteFromWaitlist` intentionally does not touch it per DOCS.
- `paidAt` is nullable; `isWithinCancellationWindow` returns false when null.
