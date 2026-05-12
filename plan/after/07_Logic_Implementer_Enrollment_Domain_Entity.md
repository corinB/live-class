# Enrollment Aggregate 도메인 엔티티 + Value Objects + 상태전이 단위 테스트

- **Assignee:** The Logic Implementer
- **Dependencies:** 04_Logic_Implementer_Class_Domain_Entity.md
- **Definition of Done (DoD):**
  - `Enrollment` Aggregate Root와 VO(`EnrollmentId`, `EnrollmentStatus`, `CancellationWindow`)가 도메인 레이어에 Spring 의존 없이 구현된다 (DOCS §3.2).
  - 의도 표현 메서드 6개(`apply`, `waitlist`, `confirm`, `cancel`, `promoteFromWaitlist`, `isWithinCancellationWindow`)가 setter 없이 구현된다.
  - DOCS §6 Enrollment Invariants 8개가 순수 JUnit으로 검증된다 (특히 §5 7일 창, §6 중복 cancel 거부, §7 confirm은 PENDING에서만).
  - `@Version` 컬럼 부여 (ARCHITECTURE §4.3 §2.3).
  - `CancellationWindow`가 폐구간 정책 `now <= paidAt + Duration.ofDays(7)` 으로 구현된다 (ARCHITECTURE §2.3).

## Action Items (Checklist)

- [x] `domain/enrollment/EnrollmentId.java` — `record EnrollmentId(UUID value)`.
- [x] `domain/enrollment/EnrollmentStatus.java` — `enum { PENDING, CONFIRMED, CANCELLED, WAITLISTED }`.
- [x] `domain/enrollment/CancellationWindow.java` — `record CancellationWindow(Duration window)` + `boolean isWithin(Instant paidAt, Instant now) { return !now.isAfter(paidAt.plus(window)); }`. 정적 상수 `CancellationWindow.SEVEN_DAYS = new CancellationWindow(Duration.ofDays(7))`.
- [x] `domain/enrollment/Enrollment.java` — `@Entity @Table(name="enrollments")`.
  - 필드: `id (UUID)`, `classId (UUID)`, `classmateId (UUID)`, `@Enumerated(STRING) status`, `appliedAt`, `paidAt (nullable)`, `cancelledAt (nullable)`, `@Version Long version`.
  - private 기본 생성자.
  - `static Enrollment apply(ClassId, UserId, Instant)` → status PENDING.
  - `static Enrollment waitlist(ClassId, UserId, Instant)` → status WAITLISTED.
  - `void confirm(Instant now)` — status가 PENDING 아니면 `IllegalStateTransitionException`. paidAt = now, status = CONFIRMED.
  - `void cancel(Instant now)` — 상태별 분기.
    - CONFIRMED && !CancellationWindow.SEVEN_DAYS.isWithin(paidAt, now) → `OutsideCancellationWindowException`.
    - 이미 CANCELLED → `AlreadyCancelledException`.
    - status를 CANCELLED로, cancelledAt = now.
  - `void promoteFromWaitlist(Instant now)` — WAITLISTED 아니면 예외. status = PENDING. appliedAt은 유지 (DOCS).
  - `boolean isWithinCancellationWindow(Instant now)` — paidAt null이면 false, 아니면 위 정책 적용.
- [x] 도메인 예외 클래스: `domain/enrollment/OutsideCancellationWindowException.java` (status 422), `AlreadyCancelledException.java` (status 409), `DuplicateEnrollmentException.java` (status 409), `ClassNotOpenException.java` (status 409).
- [x] (Verify) `domain/enrollment/EnrollmentTest.java`.
  - `apply()` → status PENDING, paidAt null.
  - `waitlist()` → status WAITLISTED.
  - `apply().confirm(now)` → CONFIRMED, paidAt == now.
  - `apply().cancel(now)` → CANCELLED (PENDING 취소 정상).
  - `waitlist().cancel(now)` → CANCELLED.
  - `apply().confirm(t0); cancel(t0 + 6days)` → CANCELLED.
  - `apply().confirm(t0); cancel(t0 + 7days)` → CANCELLED (폐구간 경계).
  - `apply().confirm(t0); cancel(t0 + 7days + 1nano)` → `OutsideCancellationWindowException`.
  - 이미 CANCELLED인 상태에서 `cancel()` → `AlreadyCancelledException`.
  - WAITLISTED에서 `confirm()` → `IllegalStateTransitionException`.
  - `waitlist().promoteFromWaitlist(now)` → PENDING. appliedAt 변경 없음.
- [x] (Verify) `domain/enrollment/CancellationWindowTest.java` — 경계값 3개 (정확히 7일, +1ns, -1ns) 검증.
