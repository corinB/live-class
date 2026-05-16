# Maestro Summary — 리팩토링 사이클 (2026-05-16)

> 호출 컨텍스트 — `/evolution` 사이클. 분석 대상 — Class / Enrollment / shared(+infrastructure/web/config). 본 사이클 P0+P1 적용 예정.
> 디스패치 제약 — Agent 툴 미가용 환경. Maestro 가 worker 역할로 직접 7축 스캔 후 도메인별 리포트 분리 작성.

## 도메인 인벤토리

| 도메인 | 경로 | 스캔 파일 수 | 워커 리포트 |
|---|---|---|---|
| Class | `domain/clazz/` + `application/clazz/` | 15 | `worker-clazz-2026-05-16.md` |
| Enrollment | `domain/enrollment/` + `application/enrollment/` + `application/payment/` | 21 | `worker-enrollment-2026-05-16.md` |
| shared | `domain/shared/` + `infrastructure/` + `web/` + `config/` | 31 | `worker-shared-2026-05-16.md` |
| **합계** | — | **67** | 3건 |

User 도메인은 본 사이클 범위 외.

## 우선순위 액션 플랜

### P0 (즉시 결정 필요)

#### P0-1. Enrollment 이벤트 4종 dead-letter — DOCS 와 코드 불일치
- 근거 — `EnrollmentCreatedEvent` / `EnrollmentConfirmedEvent` / `EnrollmentCancelledEvent` / `WaitlistPromotedEvent` 모두 `publishEvent` 되지만 listener 부재. DOCS.md §49-62 는 "AFTER_COMMIT phase" + "ClassClosedEvent → bulk-cancel all WAITLISTED enrollments" 명시.
- 도메인 invariant 영향 — Class 가 CLOSED 로 전이될 때 WAITLISTED enrollment 가 자동 취소되어야 하는데 미구현. 직접적 invariant 위반은 아니지만 DOCS 정합 깨짐.
- 결정 옵션 — (A) listener 4종 신규 도입 + bulk-cancel WAITLISTED listener 도입 (B) publishEvent 제거 + DOCS 갱신 (C) 현 상태 유지하고 DOCS 만 "이벤트 발행 — 향후 hook 용" 으로 정정.
- **사용자 결정 필요** — 본 사이클에서 코드 도입은 신규 기능에 가까워 evolution 범위 밖. 권장 — 옵션 C (DOCS 갱신만, P0-DOCS 항목 참조). 코드 변경 없음.

### P1 (본 사이클 적용)

#### P1-1. ClassApplicationService.transitionStatus 중복 잠금 정리
- 위치 — `application/clazz/ClassApplicationService.java#transitionStatus` (line 67~105) + `domain/clazz/ClassRepository.java#findByIdForUpdate` (line 19~21)
- 변경 — `findByIdForUpdate(PESSIMISTIC_WRITE)` 제거 + 일반 `findById` 로 교체. retry 루프는 보존. `ClassRepository.findByIdForUpdate` 메서드 제거.
- 정합 근거 — ARCHITECTURE.md §2.4 "`@Version` on Class row; Quartz auto-close and Creator manual close race resolved by OL" — @Version 단독 사용을 명시. 비관락 + 낙관락 동시 사용은 ARCHITECTURE 와 불일치.
- 변경 범위 — `ClassApplicationService.java` 1줄 교체 + `ClassRepository.java` 메서드 1개 삭제. 단일 파일급 변경.
- 회귀 위험 — 낮음. ClassApplicationServiceConcurrencyTest 등 동시성 테스트가 존재한다면 동작 검증 가능. 본 사이클 회귀 검증 필수.

#### P1-2. EnrollmentApplicationService.cancelInTx 사전 검증 중복 정리
- 위치 — `application/enrollment/EnrollmentApplicationService.java#cancelInTx` (line 192~225) + `domain/enrollment/Enrollment.java#cancel` (line 81~91)
- 변경 — service 의 사전 검증 한 줄 (`if (e.getStatus() == CONFIRMED && !e.isWithinCancellationWindow(now)) throw ...`) 제거. idempotent CANCELLED 200 처리는 보존 (HTTP 응답 결정은 application 책임).
- 정합 근거 — Tell-Don't-Ask 준수. 도메인 메서드 `Enrollment.cancel` 이 동일 검증을 이미 수행하고 동일 예외 throw. 동작 변화 없음.
- 변경 범위 — 단일 파일 4줄 제거.
- 회귀 위험 — 매우 낮음. cancel 도메인 테스트로 검증.

#### P1-3. ReconcileService.scoreOf 중복 제거
- 위치 — `infrastructure/ReconcileService.java#scoreOf` (line 106~108) + `application/enrollment/EnrollmentMirrorService.java#scoreOf` (line 129~131)
- 변경 — ReconcileService 의 내부 `scoreOf(long, int)` 메서드 삭제. 호출 2곳 (line 74, 87) 을 `EnrollmentMirrorService.scoreOf(e.getAppliedAt())` 로 교체.
- 정합 근거 — ReconcileService.scoreOf 주석에 이미 "Issue #55 에서 EnrollmentMirrorService.scoreOf(Instant) 로 추출 예정" 명시. 미완 TODO 정리.
- 변경 범위 — ReconcileService.java 1 파일 + import 1줄 추가 + 메서드 1개 삭제 + 호출 2곳 교체.
- 회귀 위험 — 매우 낮음. 동일 공식.

### P2 (본 사이클 미적용, 다음 사이클 리포트 기록)

- **P2-1.** Class.draft version 인라인 주석 압축 (worker-clazz #3)
- **P2-2.** Enrollment 명시적 getter 7개 → `@Getter` 일원화 (worker-enrollment #3)
- **P2-3.** Enrollment 이벤트 헤더 거짓 주석 수정 (worker-enrollment #4) — P0-1 정책 결정 후 작업
- **P2-4.** MockPaymentGateway TX 내부 호출 long-form 주석 압축 (worker-enrollment #5)
- **P2-5.** CurrentUser record dead code 삭제 (worker-shared #3)

## 반려 항목

| 항목 | 사유 |
|---|---|
| Title / Description VO 추출 (worker-clazz #2) | 단일 사용 추상화. 신규 패키지/클래스 생성에 해당. 자율 반려 룰 적용. |
| EnrollmentController.parseStatuses invalid status 400 매핑 (worker-shared #2) | Public API 응답 동작 변경. 본 사이클 "Public API 변경 금지" 제약 위반. P2 로 별도 분리. |
| RedisKeyFactory 패키지 재배치 (worker-shared D1) | 신규 패키지 생성 + 도메인 결합도 상승. 자율 반려 룰. |
| ClassRepository.findByIdForUpdate 의 "last-line defense 의도" 보존 옵션 | ARCHITECTURE §2.4 명시와 어긋남. 반려 사유 없음 — P1-1 채택. |

## DOCS 갱신 후보 (별도 보고)

> 본 사이클 DOCS / ARCHITECTURE 수정 금지 — 후보만 기록.

1. **DOCS.md §49-62** — "Events are published ... in the AFTER_COMMIT phase" + "ClassClosedEvent → bulk-cancel all WAITLISTED enrollments" + "EnrollmentCancelledEvent (previousStatus=CONFIRMED) → promote oldest WAITLISTED to PENDING".
   - 실제 — Enrollment 이벤트 4종 listener 부재. ClassClosedEvent listener 도 ClassStatusMirrorListener 하나 (status mirror 갱신 전용, bulk-cancel WAITLISTED 미구현). WaitlistPromotion 은 이벤트 기반이 아니라 `EnrollmentApplicationService.cancelInTx` 동기 호출.
   - 갱신 방향 — listener 부재 사실 명시 + 향후 확장점이라는 의도 명확화. 또는 listener 도입 후 DOCS 정합.

2. **ARCHITECTURE.md §2.4** — "`@Version` on Class row" — 코드와 정합되도록 P1-1 적용 후 변경 없음.

## Risk Findings (조사·보고만, 본 사이클 수정 없음)

| 코드 | 위치 | 위험 | 현 상태 |
|---|---|---|---|
| R-Q1 | `infrastructure/scheduling/QuartzConfig.java` | in-memory JobStore. 스케일아웃 시 trigger 중복 실행. | 의도된 단일 EC2 디자인 (ARCHITECTURE §4 명시). |
| R-Q2 | `infrastructure/scheduling/ClassAutoCloseJob.java#executeInternal` | OPEN 클래스 전체 List 메모리 적재. | 운영 규모 비위험. |
| R-T1 | `application/enrollment/EnrollmentApplicationService` apply/cancel | TX 2초 timeout. Redis 락 + DB INSERT 동시 진행 시 timeout 빈번 가능. | 의도된 fail-closed. 측정 필요. |
| R-T2 | `infrastructure/ClassLockService#executeWithLock` | SETNX 즉시 실패. backoff 미적용. | 의도된 디자인. 클라이언트 backoff 위임. |
| R-M1 | `infrastructure/ReconcileRunner#run` | 부팅 시 OPEN 클래스 전체 List 적재 후 순차 reconcile. | 운영 규모 비위험. |
| R-M2 | `infrastructure/ReconcileService#doReconcileOne` | classId 별 active enrollment 전체 List 적재 + HashSet ZADD. | 운영 규모 비위험. |

ThreadPool / HikariCP 직접 위험 후보는 본 스캔 범위에서 발견되지 않음 (Spring Boot 기본값 + `@Async` 미사용).

## 후속 의사결정 포인트

1. **P0-1 정책** — listener 도입 / publish 제거 / DOCS 만 갱신. 본 사이클 권장 — DOCS 갱신만 (코드 변경 0).
2. **P1 묶음 PR vs 분리 PR** — 변경 범위가 작아 단일 PR 권장. 커밋 분리는 도메인별 (Class P1-1, Enrollment P1-2, shared P1-3) 3 커밋.
3. **테스트 동반 갱신** — P1-1 의 `ClassRepository.findByIdForUpdate` 삭제는 기존 테스트가 해당 메서드 사용 시 갱신 필요. P1-2/P1-3 은 동작 동일하므로 테스트 무변경 예상.

## 다음 액션 추천

- P1-1 / P1-2 / P1-3 묶음 PR 1건 생성 (3 커밋).
- P0-1 은 사용자 결정 대기 후 별도 PR (DOCS 갱신만일 경우 빠른 작업).
- P2 5건은 다음 사이클로 이월.
