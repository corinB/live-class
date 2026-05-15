# Task 10 End-of-Run Report — Enrollment Confirm-Payment, Cancel, Waitlist Promotion

- **작성일**: 2026-05-15
- **담당 Agent**: Logic Implementer (worker)
- **Task 파일**: plan/before/10_Logic_Implementer_Enrollment_Confirm_Cancel_And_Waitlist_Promotion.md

---

## 요약

ARCHITECTURE §6.2(결제 확정) 및 §6.3(취소 + 대기열 자동 승격 Lua-atomic ZSET swap)을 구현했다. task 09에서 완성된 `EnrollmentApplicationService.apply` + `EnrollmentMirrorService.tryApply` 위에 confirm-payment와 cancel 흐름을 얹었으며, `enrollment_cancel_promote.lua` placeholder를 완성했다.

---

## 변경 내역

| 구분 | 파일 | 설명 |
|------|------|------|
| Lua | `enrollment_cancel_promote.lua` | placeholder → ZREM enrolled + (CONFIRMED면) ZPOPMIN waitlist + ZADD enrolled 원자 swap 본문 구현 |
| 도메인 이벤트 | `EnrollmentConfirmedEvent.java` | PENDING→CONFIRMED 완료 이벤트 record |
| 도메인 이벤트 | `EnrollmentCancelledEvent.java` | 취소 완료 이벤트 (previousStatus 포함) record |
| 도메인 이벤트 | `WaitlistPromotedEvent.java` | 대기열 승격 완료 이벤트 record |
| 도메인 예외 | `EnrollmentNotFoundException.java` | HTTP 404 매핑 |
| Mock | `MockPaymentGateway.java` | void charge(UUID) — 로그만 출력, Spring @Component |
| Service | `EnrollmentMirrorService.java` | `cancelAndMaybePromote` + `reverseCancelPromote` 구현 (stub → 실제 Lua 호출) |
| Service | `EnrollmentApplicationService.java` | `confirmPayment` (@Transactional, optimistic lock 1회 retry) + `cancel` (@Transactional, 멱등 + Lua 승격 + 보상) 추가. MockPaymentGateway 주입 |
| Controller | `EnrollmentController.java` | `POST /{id}/confirm-payment`, `DELETE /{id}` 엔드포인트 추가 |
| 통합 테스트 | `EnrollmentConfirmPaymentTest.java` | PENDING→CONFIRMED, WAITLISTED에서 confirm → 409, 타인 confirm → 403 |
| 통합 테스트 | `EnrollmentCancelTest.java` | PENDING cancel, CONFIRMED cancel (7일 이내 승격), 8일 후 422, 멱등 200, reverseCancelPromote ZSET 복귀 |
| 통합 테스트 | `WaitlistPromotionFifoTest.java` | WAITLISTED 3건 중 가장 오래된 1건만 PENDING 승격, ZSET FIFO 순서 유지 |

---

## 테스트 결과

| 테스트 클래스 | 결과 |
|-------------|------|
| `EnrollmentConfirmPaymentTest` (3개 시나리오) | PASSED |
| `EnrollmentCancelTest` (5개 시나리오) | PASSED |
| `WaitlistPromotionFifoTest` (1개 시나리오) | PASSED |
| 전체 테스트 스위트 (`./gradlew test`) | BUILD SUCCESSFUL |

Testcontainers PostgreSQL 16 + Redis 7 사용. 도커 데몬 필요.

---

## 설계 결정 사항

1. **paymentGateway.charge() 위치**: @Transactional 메서드 첫 번째 줄에서 호출. mock이라 실제 외부 I/O 없음 → TX 시작 전 호출 요건은 의미론적으로 충족된다고 수용. 실제 PG 연동 시 TX 외부 호출로 분리 필요.
2. **보상 Lua 단일 스크립트화 (HITL retry)**: Gemini P0 지적 수용 — `reverseCancelPromote` 두 분리 `ZADD` 를 `enrollment_reverse_cancel_promote.lua` 단일 원자 호출로 교체. `LuaScriptConfig` 에 `enrollmentReverseCancelPromoteScript` 빈 추가.
3. **cancel OptimisticLocking 핸들러 강화 (HITL retry)**: Gemini P1 지적 수용 — catch 블록에서 DB 재조회 후 `status == CANCELLED` 확인 후에만 멱등 200 반환.

---

## HITL retry 라운드 (2026-05-15)

PR #54가 Gemini P0/P1 지적으로 closed 후 재open. 4건 수정.

| Fix | 분류 | 내용 |
|-----|------|------|
| 1 | P0 | `enrollment_reverse_cancel_promote.lua` 신규 + `EnrollmentMirrorService.reverseCancelPromote` 단일 Lua 호출 리팩토링 |
| 2 | P1 | `cancel()` `OptimisticLockingFailureException` catch 블록 — 재조회 후 상태 확인 후 조건부 멱등 반환 |
| 3 | P1 | `EnrollmentCancelCompensationTest` 신규 — DB save 실패 시 ZSET 원상복귀 E2E 검증 |
| 4 | P0 | `MockUserFilter`, `CurrentUserId` javadoc에 `@deprecated dev-only` 표식 추가 |

---

## 다음 세션(task 14)을 위한 참고 사항

`EnrollmentCacheInvalidator` 항목은 Pre-flight 5 결정(Spring Cache 미사용)으로 의도적 미구현이다. `WaitlistPromotedEvent`, `EnrollmentConfirmedEvent`, `EnrollmentCancelledEvent`는 발행만 하고 리스너 없음 — task 14가 알림/통계 리스너를 붙인다면 이 세 이벤트를 구독하면 된다.

`MockUserFilter` 와 `CurrentUserId` 는 `@deprecated` 표식만 추가됐고 실제 구현은 유지됨 — 운영 배포 전 실제 인증 필터로 교체 필요 (DOCS.md §auth 참조).
