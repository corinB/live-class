<!-- 라이브 강의 수강신청 시스템의 DDD 도메인 설계 영문 요약 -->
<!-- Role: DDD domain design — bounded contexts, aggregates, state lifecycles, events, invariants -->
# DOCS.md — Live Class Domain Design

> **Detail (Korean)**: https://github.com/corinB/live-class/wiki/ko-docs-detail

This document summarizes the domain model. Full Korean detail at the link above.

## Bounded Contexts

Three contexts collaborate in a single JVM via Spring `ApplicationEvent`. Direct object references between aggregates are forbidden — use ID references only.

| Context | Aggregate Root | Responsibility |
|---|---|---|
| Class | `Class` | Metadata, capacity, one-way state `DRAFT → OPEN → CLOSED` |
| Enrollment | `Enrollment` | Apply, confirm, cancel, waitlist (status = WAITLISTED — no separate aggregate) |
| User | `User` | Roles (`CREATOR` / `CLASSMATE`). Auth is mock (`X-User-Id` header). |

## Aggregates

### Class

Key fields: `id (ClassId)`, `title`, `price (Money VO)`, `capacity (Capacity VO, ≥1)`, `period (ClassPeriod VO)`, `status (ClassStatus)`, `creatorId`.

Intent methods (no setters): `draft()`, `open()`, `close()`, `autoClose()`, `changeCapacity()` (DRAFT only), `isOpenForEnrollment()`.

### Enrollment

Key fields: `id`, `classId`, `classmateId`, `status (PENDING|CONFIRMED|CANCELLED|WAITLISTED)`, `appliedAt` (waitlist sort key), `paidAt` (nullable), `cancelledAt` (nullable).

Intent methods: `apply()`, `waitlist()`, `confirm()`, `cancel()`, `promoteFromWaitlist()`, `isWithinCancellationWindow()`.

### User

Key fields: `id (UserId)`, `role (CREATOR|CLASSMATE)`, `name`.
`UserId` is shared across all contexts. Role is immutable after creation.

## State Lifecycles

**Class**: `DRAFT → OPEN → CLOSED` (one-way). Quartz `ClassAutoCloseJob` auto-closes at 00:05 KST when `endDate` passes. Concurrent manual + auto close resolved by `@Version`.

**Enrollment**:
- Entry: `→ PENDING` (capacity available) or `→ WAITLISTED` (capacity full).
- `WAITLISTED → PENDING`: promotion when a CONFIRMED slot cancels (FIFO by `appliedAt`).
- `PENDING → CONFIRMED`: mock payment.
- `PENDING|WAITLISTED|CONFIRMED → CANCELLED`: cancel (CONFIRMED only within paidAt + 7 days).

## Domain Events

| Event | Trigger |
|---|---|
| `ClassOpenedEvent` | `Class.open()` |
| `ClassClosedEvent` | `Class.close()` / `Class.autoClose()` |
| `EnrollmentCreatedEvent` | `apply()` or `waitlist()` |
| `EnrollmentConfirmedEvent` | `confirm()` |
| `EnrollmentCancelledEvent` | `cancel()` |
| `WaitlistPromotedEvent` | `promoteFromWaitlist()` |

Events are published via Spring `ApplicationEventPublisher` in the AFTER_COMMIT phase.

`ClassClosedEvent` → bulk-cancel all WAITLISTED enrollments for that class.
`EnrollmentCancelledEvent` (previousStatus=CONFIRMED) → promote oldest WAITLISTED to PENDING.

## Key Invariants

**Class**: capacity ≥ 1; period endDate ≥ startDate; one-way state; capacity change only in DRAFT; OPEN class auto-closed by Quartz when endDate passes.

**Enrollment**: only when class is OPEN; CONFIRMED+PENDING ≤ capacity (concurrent enforcement via Redis Lua — see [ARCHITECTURE.md](./ARCHITECTURE.md)); no duplicate active `(classId, classmateId)`; Creator cannot enroll in own class; CONFIRMED cancellation only within paidAt + 7 days; waitlist promotion is FIFO.

## Glossary (key terms)

| Term | Definition |
|---|---|
| Capacity | `CONFIRMED + PENDING` upper bound. |
| Waitlist | FIFO queue of WAITLISTED enrollments ordered by `appliedAt`. |
| Cancellation Window | `paidAt + 7 days (168 h)` — CONFIRMED → CANCELLED allowed period. |
| ZSET Mirror | Two Redis Sorted Sets (`enrolled:{classId}`, `waitlist:{classId}`) acting as Enrollment index. |
| Lua Atomic Script | Redis server-side script; no distributed lock needed. |
| Reconcile | Rebuild ZSET from DB at boot. Force-trigger via `POST /api/admin/reconcile/{classId}`. |

## Cross-references

- Concurrency control implementation: [ARCHITECTURE.md](./ARCHITECTURE.md)
- Branch, commit, PR conventions: [CONTRIBUTING.md](./CONTRIBUTING.md)
- Pipeline policy: [ORCHESTRATION.md](./ORCHESTRATION.md)
