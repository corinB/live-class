<!-- 라이브 강의 수강신청 백엔드의 동시성 / 정합성 / 멱등성 / Quartz 스케줄링 상세 아키텍처 -->
# 아키텍처 — 동시성 · 정합성 · 멱등성 · 스케줄링

본 문서는 핵심 race 시나리오 5건의 결정 트리와 메커니즘을 정리. 요약은 [ARCHITECTURE.md](../ARCHITECTURE.md), 상세 결정 사건은 [docs/troubleshooting/](troubleshooting/).

## 1. 동시성 제어 전체 구도

```
            +--------------------+
            |  HTTP Controller   |
            +---------+----------+
                      |
                      v
       +--------------+--------------+
       | ClassLockService            |   outer-wrap
       | SET NX PX 30s + token       |   per classId 직렬화
       +--------------+--------------+
                      |
        +-------------+--------------+
        |                            |
        v                            v
+-------+-------+           +--------+--------+
| Lua atomic    |           | EnrollmentMirror|
| script        |           | Service catch   |
| (enrollment_  |           | → MirrorUna-    |
|  apply / can- |           | vailableEx      |
|  cel_promote /|           +--------+--------+
|  compensate)  |                    |
+-------+-------+                    |
        |                            v
        v                  +---------+---------+
+-------+-------+          | GlobalException   |
| PostgreSQL    |          | Handler 503       |
| @Version OL   |          +-------------------+
| partial uniq  |
+---------------+
```

| Layer | 책임 |
|---|---|
| Controller | `X-User-Id` 헤더 + role 검증 |
| ClassLockService | per-classId Redis 분산락 (outer-wrap) |
| Lua atomic script | race-critical decision (ZCARD vs capacity, ZREM+ZPOPMIN+ZADD swap) |
| EnrollmentMirrorService | Lua 호출 + DB INSERT/UPDATE 조합 — 실패 시 compensate Lua 실행 |
| PostgreSQL | `@Version` 낙관락 + partial unique index — 마지막 방어선 |
| GlobalExceptionHandler | Redis 다운 / lock busy / OL 충돌을 HTTP 상태로 매핑 |

## 2. Race 시나리오 5건

### 2.1 Last-seat race (마지막 자리 race)

상황 — 정원 100 강의에 동시 5,000 명 신청. 100 명만 PENDING, 4,900 명은 WAITLISTED 가 되어야 함. 한 명도 over-capacity PENDING 진입 X.

메커니즘.

```
1. ClassLockService.executeWithLock(classId)
     - Redis SET NX PX 30s, token = UUID
     - 획득 실패 → ClassLockBusyException → 503 CLASS_LOCK_BUSY
2. enrollment_apply.lua (KEYS = enrolled, waitlist, status / ARGV = capacity, classmateId, appliedAtNanos)
     - GET class:status:{classId}, OPEN 확인
     - ZSCORE 중복 검사 (enrolled / waitlist 둘 다)
     - ZCARD enrolled vs capacity
     - 미달 → ZADD enrolled, return 'PENDING'
     - 만석 → ZADD waitlist, return 'WAITLISTED'
3. JPA INSERT enrollments (status = PENDING / WAITLISTED)
4. 실패 시 enrollment_compensate.lua (ZSET 롤백)
5. 안전 해제 (SAFE_UNLOCK token 일치 확인 후 DEL)
```

검증 — `LastSeatRaceConcurrencyTest` (10회 연속 결정적 성공).

### 2.2 대기열 승격 (FIFO)

상황 — CONFIRMED enrollment 가 cancel 되면 가장 오래된 WAITLISTED 가 즉시 PENDING 으로 승격. 동시 cancel 두 건이면 두 명이 승격되어야 함 (double-promotion 미발생, missed-promotion 미발생).

메커니즘.

```
1. ClassLockService.executeWithLock(classId)
2. Enrollment.cancel(now) 도메인 메서드 — 7일창 / CANCELLED idempotent 처리
3. JPA UPDATE enrollments (status = CANCELLED)
4. enrollment_cancel_promote.lua (KEYS = enrolled, waitlist / ARGV = classmateId, wasConfirmed)
     - ZSCORE enrolled (canceller 점수 캡처, 보상용)
     - ZREM enrolled canceller
     - wasConfirmed == '1' 이면 ZPOPMIN waitlist 1
     - 승격자 ZADD enrolled (원래 점수 보존)
     - return {cancellerScore, promotedId, promotedScore}
5. 승격자 DB UPDATE (status = PENDING)
6. 실패 시 enrollment_reverse_cancel_promote.lua (canceller 복귀, 승격자 waitlist 복귀)
```

검증 — `WaitlistPromotionConcurrencyTest`, `EnrollmentCancelCompensationTest`, `LuaCompensationAtomicityTest`.

### 2.3 7일 취소 창

상황 — CONFIRMED enrollment 의 7일 cancel 창. 같은 사용자가 double-click 으로 두 번 cancel 호출.

메커니즘.

- `Enrollment.cancel(now)` 도메인 메서드 — CONFIRMED 분기에서 `CancellationWindow.SEVEN_DAYS.isWithin(paidAt, now)` 검증, 위반 시 `OutsideCancellationWindowException`.
- 이미 CANCELLED 상태면 `AlreadyCancelledException` (application 단에서 idempotent 200 처리).
- 동시 cancel 두 건은 `@Version` 낙관락이 한 쪽을 reject → 409 `OPTIMISTIC_LOCK_FAILURE`.

검증 — `CancelDoubleClickConcurrencyTest`.

### 2.4 Class 상태 전이 무결성

상황 — Quartz `ClassAutoCloseJob` (00:05 KST) 와 Creator 의 수동 `PATCH .../status` 가 동시 실행.

메커니즘.

- Class `@Version` 낙관락 — 둘 중 늦은 쪽이 `OptimisticLockingFailureException`.
- `ClassApplicationService.transitionStatus` — OL 충돌 시 1회 자동 retry. 두 번째도 실패하면 409 응답.
- 비관락 (`SELECT FOR UPDATE`) 은 PR #112 에서 제거 — ARCHITECTURE §2.4 명시 정합 회복.

검증 — `ClassOptimisticLockTest`.

### 2.5 Reconcile vs Enrollment race

상황 — 운영자가 `POST /api/admin/reconcile/{classId}` 호출하는 도중 다른 사용자의 apply / cancel 진입.

메커니즘.

- `ClassLockService.tryRun(classId)` 가 `executeWithLock` 과 **같은 락 키** (`lock:reconcile:{classId}`) 사용.
- apply / cancel 진행 중이면 reconcile 은 `false` 리턴 후 skip (admin endpoint 409 응답).
- reconcile 진행 중이면 apply / cancel 은 `ClassLockBusyException` → 503.

검증 — `ReconcileServiceIntegrationTest`, `ReconcileTest`.

## 3. 멱등성

| 동작 | 멱등 방식 |
|---|---|
| Cancel CANCELLED → CANCELLED | 도메인 `AlreadyCancelledException` → application 200 idempotent |
| ConfirmPayment 두 번 | 2번째 호출은 status 가 CONFIRMED 라 `IllegalStateTransitionException` (409) |
| Apply 동일 (classId, classmateId) 두 번 | Lua ZSCORE 중복 검사 → `DUPLICATE_ACTIVE`. DB partial unique 가 마지막 방어 |
| Reconcile 두 번 동시 | tryRun lock 으로 한 쪽 skip |

## 4. Fail-closed 정책

Redis 다운 시 503 즉시. DB-only fallback X. 정합성 > 가용성.

| 예외 | HTTP | errorCode |
|---|---|---|
| `RedisConnectionFailureException` | 503 | `MIRROR_UNAVAILABLE` |
| `QueryTimeoutException` | 503 | `MIRROR_UNAVAILABLE` |
| `MirrorUnavailableException` | 503 | `MIRROR_UNAVAILABLE` |
| `ClassLockBusyException` | 503 | `CLASS_LOCK_BUSY` |
| `OptimisticLockingFailureException` | 409 | `OPTIMISTIC_LOCK_FAILURE` |

JPA / Postgres 장애가 Redis 와 같은 503 으로 흡수되는 것을 막기 위해 `DataAccessException` 전체를 503 매핑하지 않고 Redis 한정 예외만 좁게 매핑. 자세한 결정 근거는 [트러블슈팅 1](troubleshooting/01-redis-fail-closed.md).

## 5. Redis Key Convention

| Key | Type | TTL | 역할 |
|---|---|---|---|
| `enrolled:{classId}` | ZSET | 영구 | PENDING + CONFIRMED mirror, score = `appliedAtNanos` |
| `waitlist:{classId}` | ZSET | 영구 | WAITLISTED mirror, score = `appliedAtNanos` |
| `class:status:{classId}` | String | 300s | Class 상태 핫 경로 캐시 (Lua 가 OPEN gate 시 사용) |
| `lock:reconcile:{classId}` | String | 30s | `ClassLockService` outer-wrap token |

Spring `@Cacheable` / `RedisCacheManager` 미사용 (Pre-flight 5). 모든 Redis 접근은 위 4 키 + 3 Lua 스크립트로 한정.

## 6. Lua 스크립트

`live-class/src/main/resources/lua/` 위치. Spring Data Redis `RedisScript` 로 로드, `EVALSHA` 실행 후 cache miss 시 Lettuce 가 `EVAL` 로 자동 fallback.

### 6.1 `enrollment_apply.lua`

```lua
-- KEYS[1]=enrolled:{classId}, KEYS[2]=waitlist:{classId}, KEYS[3]=class:status:{classId}
-- ARGV[1]=capacity, ARGV[2]=classmateId, ARGV[3]=appliedAtNanos
local status = redis.call('GET', KEYS[3])
if not status then return 'CLASS_NOT_FOUND' end
if status ~= 'OPEN' then return 'CLASS_NOT_OPEN' end
if redis.call('ZSCORE', KEYS[1], ARGV[2]) then return 'DUPLICATE_ACTIVE' end
if redis.call('ZSCORE', KEYS[2], ARGV[2]) then return 'DUPLICATE_ACTIVE' end
local current = tonumber(redis.call('ZCARD', KEYS[1]))
local cap = tonumber(ARGV[1])
if current < cap then
  redis.call('ZADD', KEYS[1], ARGV[3], ARGV[2])
  return 'PENDING'
else
  redis.call('ZADD', KEYS[2], ARGV[3], ARGV[2])
  return 'WAITLISTED'
end
```

### 6.2 `enrollment_cancel_promote.lua`

ZREM + ZPOPMIN + ZADD 를 단일 원자 트랜잭션으로 실행. CONFIRMED 였을 때만 승격. canceller score 와 promoted score 를 함께 반환해 보상 Lua 가 정확히 복원할 수 있도록 함.

### 6.3 `enrollment_compensate.lua`

DB INSERT 실패 시 ZSET 의 추가 항목을 단순 ZREM. canceller / promoted 양쪽 모두에서 ZREM 호출 (둘 중 한 곳에만 존재).

### 6.4 `enrollment_reverse_cancel_promote.lua`

cancel 후 promotion DB UPDATE 실패 시 ZSET 을 통째로 swap 이전 상태로 되돌리는 보상 Lua. canceller 점수와 promoted 점수를 ARGV 로 받아 그대로 복원.

## 7. Quartz 스케줄링

### 7.1 ClassAutoCloseJob

- 매일 **00:05 KST** (`0 5 0 * * ?`, `Asia/Seoul`).
- `OPEN` 강의 중 `period.endDate < today(KST)` 인 강의 → `autoClose()` 호출.
- in-memory `RAMJobStore` (단일 EC2 가정).
- 미스파이어 정책 — `FIRE_AND_PROCEED` (다운타임 후 재부팅 시 1회 실행).
- Creator 수동 close 와의 race → Class `@Version` 낙관락이 한 쪽을 reject → `IllegalStateTransitionException` → INFO 로그 후 idempotent 종료.

### 7.2 Scale-out 시 필요한 변경

| 항목 | 단일 EC2 (현재) | scale-out (production) |
|---|---|---|
| JobStore | `RAMJobStore` | `JobStoreTX` (JDBC) |
| trigger 중복 실행 | 가정 안 함 | DB row lock 으로 cluster-safe |
| Quartz schema | 미설치 | `tables_postgres.sql` 등 부트스트랩 필요 |

## 8. AFTER_COMMIT 부속 효과

`EnrollmentEventListener` 가 Spring `@TransactionalEventListener(AFTER_COMMIT)` 로 4 이벤트 구독.

| 이벤트 | listener 메서드 | 현재 동작 | 향후 hook |
|---|---|---|---|
| `EnrollmentCreatedEvent` | `onCreated` | INFO 로그 | Slack 알림 / Prometheus 메트릭 |
| `EnrollmentConfirmedEvent` | `onConfirmed` | INFO 로그 | 결제 영수증 메일 |
| `EnrollmentCancelledEvent` | `onCancelled` | INFO 로그 | 환불 큐 publish |
| `WaitlistPromotedEvent` | `onWaitlistPromoted` | INFO 로그 | 승격 통지 (이메일 / push) |

도메인 로직에 외부 호출 없이 hook 자리만 마련 — 향후 사이클에서 listener 본문 채움.

## 9. 회복성 / 정합성 정리

| 메커니즘 | 트리거 | 효과 |
|---|---|---|
| Compensation Lua | DB INSERT/UPDATE 실패 | ZSET 롤백 → 다음 reconcile 이전에도 정합 유지 |
| Boot reconcile | `ReconcileRunner` 부팅 시 | 모든 OPEN 강의 ZSET 을 DB 기준 재구성 |
| Admin reconcile | `POST /api/admin/reconcile/{classId}` | 운영자가 특정 강의 ZSET 강제 재구성 |
| Partial unique index | DB DDL | Redis 가 잘못된 결과 보내도 DB 가 중복 reject |
| `@Version` 낙관락 | JPA | double-cancel · concurrent state transition 차단 |
| ClassLockBusy 503 | apply / cancel | 클라이언트 backoff retry 책임 위임 |

## 10. 관련 코드

| 파일 | 역할 |
|---|---|
| `live-class/src/main/java/.../infrastructure/ClassLockService.java` | outer-wrap 분산락 |
| `live-class/src/main/java/.../infrastructure/RedisKeyFactory.java` | Redis key 규약 |
| `live-class/src/main/java/.../application/enrollment/EnrollmentApplicationService.java` | apply / cancel / confirm 오케스트레이션 |
| `live-class/src/main/java/.../application/enrollment/EnrollmentMirrorService.java` | Lua 호출 + Mirror 예외 변환 |
| `live-class/src/main/java/.../application/enrollment/EnrollmentEventListener.java` | AFTER_COMMIT hook 자리 |
| `live-class/src/main/java/.../infrastructure/ReconcileService.java` | ZSET 재구성 |
| `live-class/src/main/java/.../infrastructure/ReconcileRunner.java` | 부팅 시 reconcile 트리거 |
| `live-class/src/main/java/.../infrastructure/scheduling/ClassAutoCloseJob.java` | Quartz 자동 종료 |
| `live-class/src/main/java/.../web/error/GlobalExceptionHandler.java` | HTTP 상태 매핑 |
| `live-class/src/main/resources/lua/*.lua` | 4개 원자 스크립트 |
