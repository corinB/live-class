# Enrollment confirm-payment, cancel, 대기열 자동 승격 (Lua-atomic ZSET swap)

- **Assignee:** The Logic Implementer
- **Dependencies:** 09_Logic_Implementer_Enrollment_Apply_Service_And_Controller.md
- **Definition of Done (DoD):**
  - `POST /api/enrollments/{id}/confirm-payment`이 ARCHITECTURE §6.2를 구현한다. mock 결제 → `@Version` optimistic lock UPDATE → 충돌 시 1회 재시도. **ZSET 갱신 없음** (enrolled ZSET 이 PENDING + CONFIRMED 둘 다 포함).
  - `DELETE /api/enrollments/{id}`이 ARCHITECTURE §6.3를 구현한다.
    1. DB SELECT enrollment by id + version (no FOR UPDATE).
    2. 소유자 검증 (`classmateId != owner` → 403) + 7-day window 검증 (`CONFIRMED && now > paidAt + 7d` → 422).
    3. DB UPDATE status=CANCELLED, version++ (optimistic lock). rows=0 이면 동시 cancel 다른 건이 이미 성공한 것으로 보고 멱등 200.
    4. `enrollment_cancel_promote.lua` 호출 (KEYS=[enrolled:{classId}, waitlist:{classId}], ARGV=[classmateId, wasConfirmed]).
    5. Lua 가 promoted 정보를 반환하면 해당 Enrollment 의 DB 상태를 `WAITLISTED → PENDING` 으로 UPDATE.
    6. DB UPDATE 실패 시 보상 Lua reverse 호출 (`ZADD enrolled` 복귀 + promoted 를 waitlist 로 복귀).
  - 이미 CANCELLED인 enrollment에 DELETE → 200 멱등 응답.
  - `CONFIRMED + paidAt + 7일 + 1ns` 시점 cancel → 422 `OutsideCancellationWindowException`.
  - `EnrollmentConfirmedEvent`, `EnrollmentCancelledEvent`, `WaitlistPromotedEvent`가 AFTER_COMMIT 단계에서 발행되고 캐시 evict이 동작한다.

## Action Items (Checklist)

- [ ] `src/main/resources/lua/enrollment_cancel_promote.lua` — task 08 placeholder 를 본문으로 채움.
  ```lua
  -- KEYS[1]=enrolled:{classId}, KEYS[2]=waitlist:{classId}
  -- ARGV[1]=classmateId, ARGV[2]=wasConfirmed (0/1)
  redis.call('ZREM', KEYS[1], ARGV[1])
  if ARGV[2] == '1' then
    local popped = redis.call('ZPOPMIN', KEYS[2], 1)
    if popped and #popped >= 2 then
      local promotedId = popped[1]
      local promotedScore = popped[2]
      redis.call('ZADD', KEYS[1], promotedScore, promotedId)
      return {promotedId, promotedScore}
    end
  end
  return nil
  ```
  - 첫 줄 한국어 주석 `-- enrollment_cancel_promote.lua — cancel 시 ZREM enrolled + (CONFIRMED 였으면) ZPOPMIN waitlist + ZADD enrolled 원자 swap`.
- [ ] `src/main/resources/lua/enrollment_compensate_cancel.lua` — (선택, 보상용). 별도 파일 또는 `enrollment_compensate.lua` 를 인자 다형성으로 확장. 본 task 에서는 application service 가 두 개의 단순 Lua call (ZADD enrolled, ZADD waitlist) 로 reverse 하므로 별도 스크립트 불요. 단 reverse 가 두 호출로 분리되므로 그 사이 race 가 발생할 수 있어 (실패 시점이 이미 예외 상황이고 reconcile 로 복구 가능) 트레이드오프로 수용.
- [ ] `domain/enrollment/event/EnrollmentConfirmedEvent.java` — `record(UUID enrollmentId, UUID classId, UUID classmateId, Instant paidAt, Instant occurredAt)`.
- [ ] `domain/enrollment/event/EnrollmentCancelledEvent.java` — `record(UUID enrollmentId, UUID classId, UUID classmateId, EnrollmentStatus previousStatus, Instant cancelledAt, Instant occurredAt)`.
- [ ] `domain/enrollment/event/WaitlistPromotedEvent.java` — `record(UUID enrollmentId, UUID classId, UUID classmateId, Instant occurredAt)`.
- [ ] `application/enrollment/EnrollmentApplicationService.confirmPayment(UUID enrollmentId, UUID classmateId, Instant now)` 메서드 추가.
  - 외부 호출(mock `paymentGateway.charge()`)은 트랜잭션 시작 **전** 호출.
  - `@Transactional` 안에서 `Enrollment e = enrollmentRepository.findById(enrollmentId).orElseThrow(...);`
  - 소유자 검증: `if (!e.getClassmateId().equals(classmateId)) throw new AccessDeniedDomainException();`
  - `e.confirm(now)` 호출 → save. `OptimisticLockingFailureException` 캐치해 1회 재시도, 그래도 실패 시 409.
  - AFTER_COMMIT으로 `EnrollmentConfirmedEvent` 발행. **ZSET 갱신 안 함.**
- [ ] `application/payment/MockPaymentGateway.java` — `@Component`. `void charge(UUID enrollmentId)` 메서드는 단순히 로그만 출력.
- [ ] `application/enrollment/EnrollmentApplicationService.cancel(UUID enrollmentId, UUID classmateId, Instant now)` 메서드 추가.
  - `@Transactional`.
  - `Enrollment e = enrollmentRepository.findById(enrollmentId).orElseThrow(...);`
  - 소유자 검증 (403).
  - 이미 CANCELLED면 멱등 응답 (`return EnrollmentResponse.from(e);`).
  - 7-day window: `if (e.getStatus() == CONFIRMED && !e.isWithinCancellationWindow(now)) throw new OutsideCancellationWindowException();`
  - previousStatus 보존: `EnrollmentStatus prev = e.getStatus();`.
  - `e.cancel(now)` 호출 → save. UPDATE rows=0 (낙관적 충돌) → 멱등 200.
  - `List<String> luaResult = mirrorService.cancelAndMaybePromote(e.getClassId(), classmateId, prev == CONFIRMED);`
  - `if (luaResult != null)` → promoted 가 있으면 해당 enrollment 의 DB 상태를 PENDING 으로 UPDATE.
    - `UUID promotedId = UUID.fromString(luaResult.get(0));`
    - `Enrollment promoted = enrollmentRepository.findActiveByClassAndClassmate(e.getClassId(), promotedId).orElseThrow(...);` (또는 별도 메서드 추가)
    - `try { promoted.promoteFromWaitlist(now); enrollmentRepository.save(promoted); } catch (Exception ex) { mirrorService.reverseCancelPromote(e.getClassId(), classmateId, promotedId, Long.parseLong(luaResult.get(1))); throw ex; }`
    - `eventPublisher.publishEvent(new WaitlistPromotedEvent(promoted.getId(), e.getClassId(), promotedId, now));`
  - `EnrollmentCancelledEvent` AFTER_COMMIT 발행.
- [ ] `application/enrollment/EnrollmentMirrorService` 에 메서드 추가.
  - `List<String> cancelAndMaybePromote(UUID classId, UUID classmateId, boolean wasConfirmed)` — `enrollmentCancelPromoteScript` 호출.
  - `void reverseCancelPromote(UUID classId, UUID canceller, UUID promoted, long promotedScore)` — `ZADD enrolled` (canceller 복귀, score=현재 시각 ns 또는 보존 필요) + `ZADD waitlist` (promoted 복귀, score=promotedScore). 두 단순 호출.
- [ ] `EnrollmentCacheInvalidator`에 `EnrollmentConfirmedEvent`, `EnrollmentCancelledEvent`, `WaitlistPromotedEvent` 핸들러 추가 — 모두 `class:enrolledCount::{classId}` evict.
- [ ] `web/enrollment/EnrollmentController.java`에 두 endpoint 추가.
  - `POST /{id}/confirm-payment` → 200 + `EnrollmentResponse`.
  - `DELETE /{id}` → 200 + `EnrollmentResponse(CANCELLED)`.
- [ ] `domain/enrollment/EnrollmentNotFoundException.java` (status 404).
- [ ] (Verify) `EnrollmentConfirmPaymentTest.java` — `@IntegrationTest`.
  - 정상 PENDING → confirm → CONFIRMED + paidAt 기록. ZSET 변동 없음 (enrolled 카드 동일).
  - WAITLISTED 상태에서 confirm → `IllegalStateTransitionException` 409.
  - 다른 사용자가 confirm → `AccessDeniedDomainException` 403.
- [ ] (Verify) `EnrollmentCancelTest.java` — `@IntegrationTest`.
  - PENDING cancel → CANCELLED, ZSET 에서 ZREM 만 일어남 (승격 없음).
  - CONFIRMED 강의 cancel (7일 이내) → CANCELLED + 다음 WAITLISTED 한 명이 PENDING 으로 승격 (DB + ZSET 동기).
  - CONFIRMED + paidAt + 8일 cancel → 422.
  - 이미 CANCELLED인 enrollment DELETE → 200 멱등 응답.
  - 보상 시나리오 — promoted DB UPDATE 가 실패하도록 모킹 → ZSET 이 cancel 직전 상태로 복귀 (canceller 가 enrolled 에 복귀, promoted 가 waitlist 에 복귀).
- [ ] (Verify) `WaitlistPromotionFifoTest.java` — WAITLISTED 3건(appliedAt 다름)이 있을 때 CONFIRMED 1건 cancel → 가장 오래된 1건만 PENDING이 되는지 검증. ZSET 의 score 순서가 그대로 FIFO 보장하는지도 확인.
