# 워커 리포트 — Enrollment 도메인 (2026-05-16)

> 디스패치 방식 — 환경상 Agent 툴 미가용. Maestro 가 worker 역할로 직접 7축 스캔 수행.

## 모듈: Enrollment

### Summary
- 스캔 파일 수 — `domain/enrollment/` 15개 + `application/enrollment/` 5개 + `application/payment/` 1개 = 21개
- 후보 (기준별)
  - 기준1 Rich Enum — 0건 (EnrollmentStatus 의 상태 전이 룰은 Enrollment 메서드에 이미 캡슐화)
  - 기준2 도메인 로직 이동 — 1건 (cancel 사전 검증 중복)
  - 기준3 Common 의존성 — 0건
  - 기준4 코드 최적화 — 1건 (Enrollment 명시적 getter)
  - 기준5 주석 직관성 — 2건 (이벤트 주석 거짓, MockPayment TX 내부 호출 주석)
  - 기준6 AfterCommit — **2건 보고 (publish 는 트랜잭션 내부지만 listener 부재로 dead-letter)**
  - 기준7 Risk — 1건 (apply/cancel TX 2초 타임아웃 + Redis 대기)

### Findings

#### 1. [기준6] — Enrollment 이벤트 4종 dead-letter
- 대상 위치 — `domain/enrollment/event/` 4개 파일 + `application/enrollment/EnrollmentApplicationService.java` (line 123, 163, 248, 252)
- 문제점 — `EnrollmentCreatedEvent`, `EnrollmentConfirmedEvent`, `EnrollmentCancelledEvent`, `WaitlistPromotedEvent` 모두 `publishEvent` 되지만 **수신 listener 가 코드베이스에 존재하지 않음**. `ClassStatusMirrorListener` 1건만 listener — 그것도 Class 이벤트 전용. DOCS.md §49-62 에는 "Events are published ... in the AFTER_COMMIT phase" + "ClassClosedEvent → bulk-cancel all WAITLISTED enrollments" 명시되어 있으나 미구현. 이벤트 헤더 주석은 "AFTER_COMMIT 리스너에서 소비" 라고 거짓 진술.
- 개선안 — 두 가지 선택지. (A) listener 4종 추가 (DOCS 정합 확보 — 다만 신규 기능 도입에 가깝다). (B) 이벤트 publish 제거 + DOCS 갱신 (실현 가능성 낮은 hook 정리). 본 사이클에서는 **DOCS 갱신 후보로 분류 + 사용자 결정 필요**. publish 자체는 트랜잭션 내부 호출이지만 listener 가 없어 AfterCommit 위반은 사실상 무의미. **P0 보고 (도메인 invariant 불일치 가능)**.

#### 2. [기준2] — Enrollment.cancel 사전 검증과 application service 중복
- 대상 위치 — `application/enrollment/EnrollmentApplicationService.java#cancelInTx` (line 192~225) + `domain/enrollment/Enrollment.java#cancel` (line 81~91)
- 문제점 — service 가 (a) CANCELLED 라면 idempotent 200 return, (b) CONFIRMED + window 밖이면 throw 를 직접 수행. 이후 `e.cancel(now)` 호출 시 도메인 메서드 내부에서도 동일 두 가지를 다시 체크. Tell-Don't-Ask 미준수가 아니라 오히려 **검증이 두 군데** — 도메인이 이미 throw 하는데 service 가 선행 분기.
- 개선안 — service 의 idempotent 200 처리는 보존 (HTTP 응답 결정은 application 책임). window 검증은 도메인 메서드만으로 충분하므로 service 의 `if (e.getStatus() == CONFIRMED && !e.isWithinCancellationWindow(now))` 한 줄 제거. 도메인 메서드가 던지는 `OutsideCancellationWindowException` 이 동일하게 GlobalExceptionHandler 로 매핑되므로 동작 변화 없음. P1.

#### 3. [기준4] — Enrollment 명시적 getter vs Class 의 @Getter 일관성
- 대상 위치 — `domain/enrollment/Enrollment.java` (line 109~139)
- 문제점 — Class 는 `@Getter` 로 일괄 처리, Enrollment 는 7개 getter 를 수동 작성. 동일 도메인 레이어에서 스타일 일관성 손실.
- 개선안 — 클래스 상단 `@Getter` 추가 + 수동 getter 7개 삭제. version 만 노출 안 되도록 필요시 fine-grained 처리. ROI 낮음 — **P2 후보, 본 사이클 미적용**.

#### 4. [기준5] — 이벤트 헤더 거짓 주석
- 대상 위치 — `domain/enrollment/event/*.java` 4개 파일
- 문제점 — "AFTER_COMMIT 리스너에서 소비" 라고 적혀 있으나 실제 listener 부재. 항목1 과 연동.
- 개선안 — listener 추가 (항목1 결정 따라) 또는 주석 수정. P2.

#### 5. [기준5] — MockPaymentGateway TX 내부 호출 long-form 주석
- 대상 위치 — `application/enrollment/EnrollmentApplicationService.java#confirmPayment` (line 130~140)
- 문제점 — 5줄 주석으로 "mock 이라 TX 내부에서 호출해도 정책 위반 아님" 을 길게 설명. Why 자체는 중요하지만 ARCHITECTURE §6.2 참조만으로 충분.
- 개선안 — 1줄로 압축 + ARCHITECTURE 링크. P2.

### ⚠ Invariant Risk
- **Enrollment 이벤트 listener 부재가 DOCS.md §61 "ClassClosedEvent → bulk-cancel all WAITLISTED enrollments" 미구현 의미** — Class 가 CLOSED 로 전이될 때 WAITLISTED enrollment 가 자동 취소되지 않음. **사용자 결정 필요** — 의도된 미구현 / 추가 구현 / DOCS 갱신 셋 중 하나.

### Risk Findings (기준7)
- **R1. apply / cancel TX 2초 타임아웃** — `TransactionTemplate.setTimeout(2)`. Redis ZSET lock 대기 + DB INSERT 까지 2초 안에 끝나야 함. 동시 트래픽 폭증 시 timeout 발생 가능. 측정 필요. 현재는 의도된 fail-closed 정책 (ARCHITECTURE §4 fail-closed 정합). **보고만.**
- **R2. ClassLockService.executeWithLock 의 SETNX 즉시 실패** — 락 충돌 시 즉시 503 (busy backoff 없음). 운영 시 짧은 backoff 재시도 권장이지만 클라이언트 책임으로 위임됨. 의도된 디자인. **보고만.**

### Out of scope — 호출자(maestro) 결정 필요
- **D1. 이벤트 listener 4종 신규 도입 vs publish 제거 vs DOCS 갱신** — 항목1 정책 결정. 본 사이클 P0 후보로 maestro 가 사용자에 escalate.
