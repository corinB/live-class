# Task 10 Blueprint Executor Worker 종료 보고서

- **날짜**: 2026-05-14 (실제 실행: 2026-05-15 자정 걸침)
- **태스크**: `10_Logic_Implementer_Enrollment_Confirm_Cancel_And_Waitlist_Promotion.md`
- **PR**: https://github.com/corinB/live-class/pull/24
- **브랜치**: `feature/task-10-enrollment-confirm-cancel-waitlist`

---

## 추가/수정 파일 목록

### 신규 파일

| 파일 | 역할 |
|------|------|
| `src/main/resources/lua/enrollment_cancel_promote.lua` | cancel + waitlist 승격 원자 ZSET swap Lua 스크립트 (placeholder → 실구현) |
| `src/main/java/.../domain/enrollment/event/EnrollmentConfirmedEvent.java` | 결제 확정 도메인 이벤트 record |
| `src/main/java/.../domain/enrollment/event/EnrollmentCancelledEvent.java` | 취소 도메인 이벤트 record |
| `src/main/java/.../domain/enrollment/event/WaitlistPromotedEvent.java` | 대기열 승격 도메인 이벤트 record |
| `src/main/java/.../domain/enrollment/EnrollmentNotFoundException.java` | 404 도메인 예외 |
| `src/main/java/.../application/payment/MockPaymentGateway.java` | 모의 결제 게이트웨이 bean |
| `src/test/java/.../application/enrollment/EnrollmentConfirmPaymentTest.java` | confirm-payment 통합 테스트 (3 케이스) |
| `src/test/java/.../application/enrollment/EnrollmentCancelTest.java` | cancel 통합 테스트 (5 케이스) |
| `src/test/java/.../application/enrollment/WaitlistPromotionFifoTest.java` | FIFO 대기열 승격 통합 테스트 (1 케이스) |

### 수정 파일

| 파일 | 변경 내용 |
|------|-----------|
| `src/main/java/.../application/enrollment/EnrollmentApplicationService.java` | `confirmPayment()`, `confirmPaymentTx()`, `cancel()` 메서드 추가; `MockPaymentGateway` 주입 |
| `src/main/java/.../application/enrollment/EnrollmentMirrorService.java` | `cancelAndMaybePromote()` stub 실구현, `reverseCancelPromote()` 추가; null 원소 체크 버그 수정 |
| `src/main/java/.../web/enrollment/EnrollmentController.java` | `POST /{id}/confirm-payment`, `DELETE /{id}` 엔드포인트 추가 |

---

## 테스트 결과

```
./gradlew test — BUILD SUCCESSFUL in 3m 49s

신규 테스트 (9건):
  EnrollmentConfirmPaymentTest: pendingToConfirmed_recordsPaidAt            PASSED
  EnrollmentConfirmPaymentTest: waitlistedEnrollment_confirmThrowsIllegal   PASSED
  EnrollmentConfirmPaymentTest: otherUser_confirmThrowsAccessDenied         PASSED
  EnrollmentCancelTest:         pendingCancel_removedFromEnrolledZset        PASSED
  EnrollmentCancelTest:         confirmedCancelWithWaitlisted_promotesOldest PASSED
  EnrollmentCancelTest:         confirmedCancelAfter8Days_throwsOutsideWindow PASSED
  EnrollmentCancelTest:         alreadyCancelled_idempotent200               PASSED
  EnrollmentCancelTest:         cancelExactlyAt7DayBoundary_allowed          PASSED
  WaitlistPromotionFifoTest:    threeWaitlisted_cancelConfirmed_onlyOldest   PASSED

기존 테스트 regression: 없음
```

---

## 블루프린트 대비 편차

### 편차 1: `confirmPayment` self-proxy 패턴

**블루프린트**: "OptimisticLockingFailureException 캐치해 1회 재시도"

**구현**: Spring `@Lazy` self-injection을 통해 `confirmPayment()`(non-TX) → `confirmPaymentTx()`(`@Transactional REQUIRES_NEW`) 구조로 재시도 시 새 트랜잭션을 열도록 함. 블루프린트가 재시도 구체 패턴을 명시하지 않았고, private method의 `@Transactional`이 동작하지 않는 Spring AOP 특성상 self-proxy가 가장 간단한 해결책.

**근거**: ARCHITECTURE §6.2의 "retry 1회" 요구사항을 준수하면서, 새 트랜잭션 없이 같은 세션에서 재시도하면 JPA dirty session 문제가 발생함.

### 편차 2: Lua nil 반환 시 `[null]` 리스트 처리

**블루프린트**: `if (luaResult != null)`으로 null 체크

**구현**: `if (raw == null || raw.isEmpty() || raw.get(0) == null)` — null 원소 체크 추가

**근거**: Spring Data Redis가 Lua `return nil`을 `null`이 아닌 `[null]` (null 원소 포함 리스트)로 변환하는 동작을 테스트 실행 중 발견. `UUID.fromString(null)` NPE 방지를 위해 원소 레벨 null 체크 필수.

---

## 특이사항

- 한글 경로 제약으로 인해 JVM 명령은 `/c/p-junc`(ASCII junction) 경로를 통해 실행.
- `.clone/worktrees/agent-a30d3e60def8c00c1`에 `feature/task-10-build-verify` 브랜치를 생성해 빌드 검증 수행.
- Docker Desktop이 초기 비활성 상태였으나 재시작 후 Testcontainers 통합 테스트 정상 실행됨.
