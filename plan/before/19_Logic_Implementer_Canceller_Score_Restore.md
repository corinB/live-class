# Canceller-Score Restoration on Cancel-Promote Compensation

- **Assignee:** The Logic Implementer + Quality Guardian
- **Dependencies:** []
- **Definition of Done (DoD):**
  1. `enrollment_cancel_promote.lua` 가 cancellerScore 를 첫 번째 원소로 return (길이 3, promoted 없으면 ARGV[2]/[3] 빈문자열).
  2. `EnrollmentMirrorService.reverseCancelPromote` 시그니처 변경 — `cancellerScore` 인자 받음, `System.nanoTime()` 호출 제거.
  3. `EnrollmentMirrorService.scoreOf(Instant)` 공용 static method 추출 — apply / cancel / reverse / 후속 reconcile 모두 동일 공식 사용 (epochSec × 1_000_000_000L + nano).
  4. `EnrollmentCancelTest.reverseCancelPromote_restoresZset()` 에 `assertThat(cancellerScore).isEqualTo(1000.0)` 한 줄 추가.
  5. `EnrollmentCancelCompensationTest` 에 신규 FIFO 시나리오 — capacity=1, enrolled A(score=100), waitlist B(200), C(300), D(400). A.confirm → A.cancel → promoted (B) save 강제 RuntimeException → 보상 발동 → ZRANGE WITHSCORES = enrolled `[(A,100)]`, waitlist `[(B,200),(C,300),(D,400)]` 정확 일치.
  6. `MockPaymentGateway` 주석을 실제 호출 위치(`@Transactional` 내부)에 맞게 정정.

## 배경

PR #54 머지 commit `b289ca9` 의 보상 로직이 canceller 측 score 를 복구하지 못해 FIFO 순서가 어긋남. Explore 진단 결과 — `live-class/src/main/java/com/example/liveclass/application/enrollment/EnrollmentMirrorService.java` 의 `reverseCancelPromote` 가 `long cancellerScore = System.nanoTime()` 로 시각을 새로 부여. 결과: cancel → DB save 실패 → 보상 발동 시 canceller 가 enrolled ZSET 맨 뒤로 이동. promoted 측은 정상 (원본 waitlist score 보존).

부수 fix — `MockPaymentGateway` 주석 "Called outside the DB transaction" 이 거짓 (실제는 `@Transactional` 내부 호출, `EnrollmentApplicationService.confirmPayment` line ~115).

## Action Items (Checklist)

- [ ] `live-class/src/main/resources/lua/enrollment_cancel_promote.lua` — cancellerScore 를 return 첫 번째 원소로 추가.
  - return 배열 길이 3 으로 변경 — `{cancellerScore, promotedUserId, promotedScore}`.
  - promoted 가 없는 경우 ARGV[2] (promotedUserId) 및 ARGV[3] (promotedScore) 자리에 빈 문자열 반환.
  - 첫 줄 주석에 return shape 명시.
- [ ] `live-class/src/main/java/com/example/liveclass/application/enrollment/EnrollmentMirrorService.java` — `reverseCancelPromote` 시그니처 변경.
  - 새 인자 `double cancellerScore` 추가 (호출자 측에서 Lua 가 반환한 값 그대로 전달).
  - 메서드 내부 `long cancellerScore = System.nanoTime()` 호출 **제거**.
  - 인자로 받은 `cancellerScore` 를 그대로 ZADD 시 사용.
- [ ] `EnrollmentMirrorService.scoreOf(Instant)` 공용 static method 추출.
  - 공식: `epochSec × 1_000_000_000L + nano` (apply Lua 와 동일).
  - apply / cancel / reverse / 후속 reconcile 호출부 모두 이 static method 사용하도록 통일.
- [ ] `EnrollmentApplicationService` (또는 cancel-promote 호출 지점) 에서 Lua return 의 첫 번째 원소 (cancellerScore) 를 추출해 보상 경로(`reverseCancelPromote`) 인자로 전달.
- [ ] `EnrollmentCancelTest.reverseCancelPromote_restoresZset()` 에 `assertThat(cancellerScore).isEqualTo(1000.0)` 한 줄 추가 (기존 검증 + canceller score 정확성).
- [ ] `EnrollmentCancelCompensationTest` 에 신규 FIFO 시나리오 추가.
  - 설정: capacity=1, enrolled A(score=100), waitlist B(200), C(300), D(400).
  - 흐름: A.confirm → A.cancel → promoted (B) save 강제 RuntimeException → 보상 발동.
  - 단언: ZRANGE WITHSCORES enrolled == `[(A,100)]`, waitlist == `[(B,200),(C,300),(D,400)]` 정확 일치.
- [ ] `MockPaymentGateway` 의 "Called outside the DB transaction" 주석 정정.
  - 실제 호출 위치는 `EnrollmentApplicationService.confirmPayment` (`@Transactional` 내부, line ~115).
  - 주석을 실제 호출 컨텍스트에 맞게 다시 작성.

## Reuse

- `enrollment_reverse_cancel_promote.lua` 는 ARGV[2] (cancellerScore) 이미 정의 — Java 측 `System.nanoTime()` 호출만 제거하면 됨.
- apply Lua 의 score 공식 (epochSec × 1_000_000_000L + nano) 그대로 따름.

## Out of scope

- Mock 인증 변경 (DOCS.md 사양 그대로 유지, operator 명시 제외).
- `confirmPayment` retry catch 의 방어적 재검증 (그대로 유지).
- 다른 `plan/before/*` 파일.

## Constraints

- 단일 task / 단일 PR.
- branch `feature/task-19-canceller-score-restore`, worktree `C:/work/task19`.
- base `main`.
- Conventional Commits + footer `Refs: plan/before/19_Logic_Implementer_Canceller_Score_Restore.md`.

## References

- Issue: https://github.com/corinB/live-class/issues/55
- 회귀 PR: #54 (commit `b289ca9`).
- DOCS.md — Enrollment context, FIFO waitlist invariant.
- ARCHITECTURE.md §4 — Redis ZSET + Lua atomic script.
