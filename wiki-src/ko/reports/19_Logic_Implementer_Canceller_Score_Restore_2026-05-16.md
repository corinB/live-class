<!-- task-19 종료 보고서 — canceller score 보존 보상 로직 수정 결과 정리 -->
---
status: done
owner: Logic Implementer + Quality Guardian
created: 2026-05-16
updated: 2026-05-16
---

# Report: Canceller-Score Restoration on Cancel-Promote Compensation (task 19)

## Input Summary

- 태스크 파일: `plan/before/19_Logic_Implementer_Canceller_Score_Restore.md`
- 이슈: https://github.com/corinB/live-class/issues/55 (P1)
- 회귀 commit: `b289ca9` (PR #54)
- DOCS.md 기반 도메인 — Enrollment context, FIFO waitlist invariant.
- ARCHITECTURE.md §4 — Redis ZSET + Lua atomic script + 보상 경로.

## What Was Done

| File | Action | Summary |
|------|--------|---------|
| `live-class/src/main/resources/lua/enrollment_cancel_promote.lua` | modified | return shape 고정 길이 3 `{cancellerScore, promotedId, promotedScore}`. promotion 없으면 빈 문자열. ZSCORE 를 ZREM 전에 호출하여 원본 score 회수. |
| `live-class/src/main/java/com/example/liveclass/application/enrollment/EnrollmentMirrorService.java` | modified | `reverseCancelPromote` 시그니처에 `double cancellerScore` 추가, `System.nanoTime()` 호출 제거. `public static long scoreOf(Instant)` 추출 — Mirror 내부 static. |
| `live-class/src/main/java/com/example/liveclass/application/enrollment/EnrollmentApplicationService.java` | modified | Lua 결과 인덱스 시프트 (0→cancellerScore, 1→promotedId, 2→promotedScore) + 빈 문자열 가드. 보상 호출 시 cancellerScore 그대로 전달. |
| `live-class/src/test/java/com/example/liveclass/application/enrollment/EnrollmentCancelTest.java` | modified | `reverseCancelPromote_restoresZset()` 시그니처 변경 반영 + `cancellerScore == 1000.0` 정확 일치 검증 추가. |
| `live-class/src/test/java/com/example/liveclass/application/enrollment/EnrollmentCancelCompensationTest.java` | modified | 신규 FIFO 시나리오 추가 — enrolled A(100), waitlist B/C/D(200/300/400), A 보상 후 ZRANGE WITHSCORES 정확 일치. `EnrollmentMirrorService` 직접 주입하여 단위 테스트 형태. |
| `live-class/src/main/java/com/example/liveclass/application/payment/MockPaymentGateway.java` | modified | 주석 "Called outside the DB transaction" → "Called inside the DB transaction (confirmPayment)". |

PR: https://github.com/corinB/live-class/pull/93 (squash merged `4ceaaaa`, 2026-05-16T04:03:16Z).

## Rationale & Tradeoffs

- 선택: Lua 가 ZSCORE 로 원본 score 를 회수해 return 첫 원소로 반환 → 보상 호출에서 그대로 ZADD. 호출자가 `Instant` 시점을 다시 계산할 필요 없이 Lua 가 진실의 원천.
- 대안 — Java 측에서 cancel 진입 시점에 ZSCORE 별도 조회 후 보상 호출에 넘기는 방안. 추가 Redis round-trip + race window 발생. 기각.
- 대안 — `LuaCancelResult` record 도입으로 인덱스 직접 접근 제거. Gemini P2 권장이지만 PR scope 확장. follow-up bundle 로 이관 (사용자 결정).
- 트레이드오프: Lua return shape 변경은 backwards-incompatible. 호출처 1곳 (`EnrollmentApplicationService.cancel`) 만 영향 — grep 으로 전수 확인 후 동일 PR 에 묶어 처리.

## Follow-ups

- [ ] Gemini PR #93 P2 — `EnrollmentApplicationService` cancel 의 Lua 결과 파싱을 `LuaCancelResult` record 로 추출. `chore/pr92-followups` 묶음 PR 에 4번째 commit 으로 추가 예정.
- [ ] Gemini PR #92 follow-up 3건 (`RedisKeyFactory`, cancel pre-fetch 주석, apply real-race 테스트) — 동일 묶음 PR.
- [ ] (조건부) §6.1 `gemini-review.yml` diff exclusion 보강 — 이번 chore 묶음 후 advisor 여유 확인하고 진행 결정.
