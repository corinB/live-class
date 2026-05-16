# refactor(test): rename misleading concurrency test names

> 2026-05-16 · 브랜치 `refactor/rename-concurrency-tests` · 기반 `origin/main@5686cf5`

## 배경

PR #101 / #102 의 Gemini P2#1 권고 — 두 테스트 클래스가 이름에 "Concurrency" 를 달고 있지만 실제로는 단일 thread 시나리오이므로 race / 동시성 테스트로 오인될 수 있다. 동시성을 실제로 검증하는 `LastSeatRace`, `CancelDoubleClick`, `WaitlistPromotion` 과 명확히 구분하기 위해 rename.

> Gemini PR #101 P2#1: "LuaCompensationConcurrencyTest 는 단일 thread 로 보상 원자성을 검증하므로 클래스명에서 Concurrency 를 빼고 의도를 드러내는 이름이 적절하다."

## 변경 표

| Before | After | 사유 |
|--------|-------|------|
| `LuaCompensationConcurrencyTest` | `LuaCompensationAtomicityTest` | 단일 thread 에서 Lua 보상(rollback) 스크립트의 원자성을 검증. race 시나리오 아님. |
| `CancellationWindowBoundaryConcurrencyTest` | `CancellationWindowBoundaryTest` | 단일 thread 에서 paidAt + 7d 경계값(200 / 422 / 200) 을 결정론적으로 검증. race 시나리오 아님. |

## 변경 범위

- `git mv` 로 파일 rename (history 보존).
- 클래스 선언 1줄씩 갱신. 자기참조는 클래스명 한 곳뿐.
- 외부 import / reference 없음 (`git grep` 검증).
- 테스트 로직 / 메타(`@Order`, `@DisplayName`) 변경 없음.
- 과거 reports(`12_Quality_Guardian_Concurrency_Integration_Tests_2026-05-16.md`) 의 옛 이름 언급은 historical 기록이므로 보존.

## Out of scope

- 실제 race 시나리오 테스트 (`LastSeatRaceConcurrencyTest`, `CancelDoubleClickConcurrencyTest`, `WaitlistPromotionConcurrencyTest`) — rename 대상 아님.
- 단일 thread 지만 "Concurrency" 이름 없는 테스트 (`RedisDisconnectFailClosedTest`, `ReconcileTest`) — 무관.