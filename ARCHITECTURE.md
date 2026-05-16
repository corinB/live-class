<!-- 라이브 강의 수강신청 시스템의 동시성 제어·캐싱·스케줄링 아키텍처 영문 요약 -->
<!-- Role: concurrency control, caching, and scheduling decisions for the live-class enrollment system -->
# ARCHITECTURE.md — Concurrency, Caching & Scheduling

> **Detail (Korean)**: https://github.com/corinB/live-class/wiki/ko-architecture-detail

This document summarizes the key engineering decisions. Full Korean detail at the link above.

## Core Decision: Redis ZSET + Lua Atomic Script

**Primary concurrency gate**: Redis ZSET + Lua atomic script (not PostgreSQL `SELECT FOR UPDATE`).
**PostgreSQL role**: durable source of truth + last-line defense (partial unique index, `@Version`).
**Redisson `RLock`**: removed by Pre-flight 4 decision — unnecessary for single-EC2 deployment.

Why Lua-first over DB locking:
- Single Lua call serializes the race-critical `(classId)` key within Redis single-threaded executor — no lock held.
- FIFO waitlist ordering enforced by ZSET score (`appliedAtNanos`) at data-structure level.
- Waitlist promotion is one atomic call (`ZREM enrolled` + `ZPOPMIN waitlist` + `ZADD enrolled`) — zero consistency window.
- Round-trips on the critical path: 1 Redis + 1 DB (vs. ≥2 for Redisson lock).

Tradeoff: dual source-of-truth (DB + Redis ZSET) requires 3-layer defense:
1. Compensation Lua (`enrollment_compensate.lua`) — rolls back ZSET on DB failure.
2. Boot reconcile (`ReconcileRunner`) — rebuilds ZSET from DB on every startup.
3. DB partial unique index — rejects duplicates at DB level regardless of Redis state.

## Concurrency Scenarios

| Scenario | Mechanism | Verified by |
|---|---|---|
| Last-seat race (`§2.1`) | `ClassLockService.executeWithLock(classId)` outer-wrap (Redis `SET NX PX`, 30 s TTL, token-based safe-unlock) → `enrollment_apply.lua` ZCARD vs capacity branch → ZADD enrolled or waitlist. Lock busy → `ClassLockBusyException` → 503 (`CLASS_LOCK_BUSY`). | `LastSeatRaceConcurrencyTest` |
| Double-promotion prevention (`§2.2`) | Same outer-wrap on `cancel()` → `enrollment_cancel_promote.lua` atomic ZREM + ZPOPMIN + ZADD in one call. | `WaitlistPromotionConcurrencyTest`, `EnrollmentCancelCompensationTest`, `LuaCompensationAtomicityTest` |
| 7-day cancellation window (`§2.3`) | `Enrollment.cancel(now)` domain method enforces `paidAt + 7 d` on the CONFIRMED branch and idempotent handling on already-CANCELLED; `@Version` optimistic lock on the Enrollment row stops concurrent cancels. | `CancelDoubleClickConcurrencyTest` |
| State transition integrity (`§2.4`) | `@Version` on Class row with 1-retry on optimistic lock conflict; Quartz auto-close and Creator manual close race resolved by OL. | `ClassOptimisticLockTest` |
| Reconcile vs enrollment race | `ClassLockService.tryRun(classId)` shares the same lock key — a concurrent enrollment forces reconcile to skip (false return), and a running reconcile forces enrollment to 503. | `ReconcileServiceIntegrationTest`, `ReconcileTest` |
| Redis-down fail-closed | `GlobalExceptionHandler` maps `QueryTimeoutException` / `RedisConnectionFailureException` / `ClassLockBusyException` to 503 (consistency-first). | `RedisDisconnectFailClosedTest`, `GlobalExceptionHandlerTest` |

## Redis Key Convention

| Key | Type | TTL | Role |
|---|---|---|---|
| `enrolled:{classId}` | ZSET | permanent | PENDING+CONFIRMED mirror; score=appliedAtNanos |
| `waitlist:{classId}` | ZSET | permanent | WAITLISTED mirror |
| `class:status:{classId}` | String | 300 s | Lua reads this before ZADD; app service writes after state transition |

Spring `@Cacheable` / `RedisCacheManager` are **not used** (Pre-flight 5). All Redis usage is the mirror layer above.

## Failure Policy

**Fail-closed**: Redis down → 503 immediately. No DB-only fallback for enrollment/cancel APIs.
Consistency over availability is the explicit design choice.

`GlobalExceptionHandler` maps Redis-specific exceptions narrowly (DB failures stay as 500 for clearer ops triage):

| Exception | HTTP | errorCode |
|---|---|---|
| `RedisConnectionFailureException` / `QueryTimeoutException` | 503 | `MIRROR_UNAVAILABLE` |
| `MirrorUnavailableException` | 503 | `MIRROR_UNAVAILABLE` |
| `ClassLockBusyException` | 503 | `CLASS_LOCK_BUSY` |
| `OptimisticLockingFailureException` | 409 | `OPTIMISTIC_LOCK_FAILURE` |

## AFTER_COMMIT Side-Effects

`EnrollmentEventListener` (Spring `@TransactionalEventListener(AFTER_COMMIT)`) is the single subscription seat for all four Enrollment events — `onCreated`, `onConfirmed`, `onCancelled`, `onWaitlistPromoted`. Current implementation only logs at INFO; notification, metrics, and external-queue hooks attach here in later cycles.

## Scheduled Jobs (Quartz)

`ClassAutoCloseJob` runs daily at **00:05 KST** (`0 5 0 * * ?`, `Asia/Seoul`).
- Finds OPEN classes whose `period.endDate < today(KST)` and calls `classApplicationService.close(...)`.
- In-memory `RAMJobStore` (single EC2). Scale-out requires JDBC JobStore.
- Race with Creator manual close resolved by `@Version` optimistic lock; loser gets `IllegalStateTransitionException` → INFO log, idempotent.
- Misfire policy: `FIRE_AND_PROCEED` — runs once on next boot if missed.

## Lua Scripts

Located at `src/main/resources/lua/`. Loaded via Spring Data Redis `RedisScript`, executed via `EVALSHA` with Lettuce auto-fallback to `EVAL` on cache miss.

| Script | Purpose |
|---|---|
| `enrollment_apply.lua` | Gate capacity check + ZADD enrolled or waitlist |
| `enrollment_cancel_promote.lua` | Atomic cancel ZREM + optional waitlist ZPOPMIN + ZADD |
| `enrollment_compensate.lua` | Reverse ZSET changes on DB failure |

## Cross-references

- Domain invariants that drive these decisions: [DOCS.md](./DOCS.md)
- Branch, commit, PR conventions: [CONTRIBUTING.md](./CONTRIBUTING.md)
- Pipeline policy: [ORCHESTRATION.md](./ORCHESTRATION.md)
