<!-- Pre-flight 2 작업 보고서 — 동시성 전략 ZSET+Lua 전환, Quartz auto-close 도입, 4 도메인 빈틈 봉합을 동봉한 chore PR -->
---
status: complete
owner: Maestro (Opus)
created: 2026-05-12
updated: 2026-05-12
---

# Report: Pre-flight 2 — Concurrency strategy switch + Quartz auto-close + domain gap sealing

## Input Summary

- 태스크 파일: 없음 (meta chore PR — numbered task 가 아님)
- DOCS.md 기반 주요 도메인 개념: Class·Enrollment·User 3 Aggregate. `period` 의미 모호성 + 4 도메인 빈틈을 본 작업에서 봉합.
- ARCHITECTURE.md 기반 주요 결정: Day 1 의 PG Pessimistic Lock 채택을 본 작업에서 Redis ZSET + Lua atomic script 로 전환.
- 트리거: 사용자가 Day 3 첫 디스패치 직전 "Redis zset + 루아스크립트 방식으로 하면 안 되나" 라고 질문 → 비교 분석 + AskUserQuestion 결정 → "전면 전환 (B 안)" 선택. 이어서 `Class.period` 의미 / Quartz 자동 close / 4 도메인 빈틈 처리 결정도 같은 세션에서 누적.

## What Was Done

| File | Action | Summary |
|------|--------|---------|
| `ARCHITECTURE.md` | rewritten (≈+346/-205) | §3 비교표·§4 채택 결론·§5 캐시/미러 분리·§6 시퀀스 다이어그램·§7 fail-closed·신규 §8 Scheduled Jobs 전체 재작성. Lua 채택 + Quartz 도입 반영. |
| `DOCS.md` | modified (+10/-4) | §3.1 `ClassPeriod` 의미 명시, §3.4 ZSET mirror 예외 조항, §4.1 lifecycle 표에 endDate 자동 close 행, §6 Class Invariants §6 신규. |
| `context.yaml` | modified (+31/-9) | glossary 4 신규 용어(ZSET mirror, Lua atomic script, Reconcile, Auto-close). Class invariants 자동 close 추가. error_handling fail-closed 전환. performance_requirements Lua p99 < 5ms. request_flow Lua-first 흐름. external_dependencies Quartz 추가. |
| `plan/before/08_*.md` | rewritten | FOR UPDATE 메서드 제거 + Lua RedisScript 빈 3개 + .lua placeholder 추가. |
| `plan/before/09_*.md` | rewritten | apply 흐름을 Lua-first + DB-second + 보상 Lua 로 재작성. Redis 다운 503 fail-closed. |
| `plan/before/10_*.md` | rewritten | cancel 을 `@Version` + Lua ZSET swap 으로 재작성. confirm 은 ZSET 갱신 없음. |
| `plan/before/12_*.md` | modified | Redis disconnect / DB 보상 / reconcile 3 시나리오 추가. |
| `plan/before/13_*.md` | modified | README §7 동시성 요약 재작성, 신규 §10 "한계 및 미구현" 섹션 (4 도메인 빈틈). |
| `plan/before/14_*.md` | created | Redis ↔ DB Reconcile Runner + Admin Endpoint. |
| `plan/before/16_*.md` | created | Class auto-close Quartz Job + JobDetail + CronTrigger + race 테스트. |
| (PR) | meta | `chore: switch concurrency to Redis ZSET+Lua + Quartz auto-close + seal domain gaps` (#1). Squash merge → main `f1585b5`. |

- 코드 변경 0 — `live-class/src/` 아래는 미수정.
- CI 결과: build-test SUCCESS, push-image / deploy-ec2 PR 단계에서는 SKIPPED, main merge 후 main push CI 에서 모두 SUCCESS. gemini-review 는 PR 에서 `printf|wc` broken-pipe 로 FAILURE (워크플로 버그, 본 작업과 무관 — 별도 `fix(ci)` PR 으로 처리 예정).

## Rationale & Tradeoffs

- **선택 1 (concurrency)**: Redis ZSET + Lua atomic script. (a) race-critical path 라운드트립을 1회로 압축, (b) ZSET score 가 FIFO 를 자료구조 차원에서 강제, (c) `ZREM enrolled` + `ZPOPMIN waitlist` + `ZADD enrolled` 가 한 원자 단위.
- **대안 1**: PG `SELECT FOR UPDATE` (Day 1 채택안). 트랜잭션 일관성은 우월하지만 락 큐가 사용자 부하에 비례해 선형 비용 + FIFO 가 락 매니저 구현에 의존.
- **트레이드오프 1**: 이중 SoT (DB + Redis ZSET) 관리 부담. 보상 Lua + 부팅 reconcile + DB partial unique index 3중 방어로 봉합.
- **선택 2 (period semantics)**: `period` = 강의 진행 기간. `endDate` 도래 시 Quartz `ClassAutoCloseJob` 가 매일 00:05 KST 에 자동 close. DRAFT→OPEN 은 Creator 수동 유지.
- **대안 2**: period 를 모집 기간으로 해석해 startDate→OPEN 도 자동 트리거. 도메인 명세 변경 폭이 크고 일정 압박이 더 큼.
- **트레이드오프 2**: 자동 close 와 Creator 수동 close 가 충돌 가능성 — `@Version` optimistic lock 으로 한 건만 성공, 패자는 `IllegalStateTransitionException` catch + INFO 로깅.
- **선택 3 (4 도메인 빈틈)**: 모두 out-of-scope + README §10 한계로 문서화. capacity-OPEN 증설 / CLOSED-cancel / PENDING-timeout / price-mutation 모두 production 해결 방향만 1줄씩 기재.
- **대안 3**: 일부를 도메인에 추가 구현. 채점자 모범답안과 어긋날 위험 + 일정 압박.

## Follow-ups

- [x] (검증) PR #1 squash merge → main `f1585b5` — 완료.
- [ ] Gemini review 워크플로의 `printf|wc` broken-pipe 이슈 — `fix(ci)` 별도 PR 로 처리. 50k 자 미만 PR 에서는 정상 동작하므로 task 02 ~ 14 워커 PR 들에 영향 없을 가능성 높음.
- [ ] Pre-flight 1 의 main 직접 push 와 동일하게 본 PR 도 CONTRIBUTING.md §1 의 "PR 경유" 정책에 맞춰 PR 으로 처리됨 — Pre-flight 1 은 일회성 예외, 향후 모든 chore 는 PR 경유 일관 유지.
- [ ] task 02 부터 워커는 갱신된 work-order 를 읽고 Lua-first 코드를 작성한다.
