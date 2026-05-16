# 워커 리포트 — shared / infrastructure / web / config 도메인 (2026-05-16)

> 디스패치 방식 — 환경상 Agent 툴 미가용. Maestro 가 worker 역할로 직접 7축 스캔 수행.

## 모듈: shared (+infrastructure / web / config)

### Summary
- 스캔 파일 수 — `domain/shared/` 1개 + `infrastructure/` 8개 + `web/` 19개 + `config/` 3개 = 31개
- 후보 (기준별)
  - 기준1 Rich Enum — 0건
  - 기준2 도메인 로직 이동 — 0건
  - 기준3 Common 의존성 — 0건 (DomainException 은 shared 위치 적절)
  - 기준4 코드 최적화 — 2건 (ReconcileService.scoreOf 중복, EnrollmentController.parseStatuses)
  - 기준5 주석 직관성 — 1건 (CurrentUser dead record)
  - 기준6 AfterCommit — 0건
  - 기준7 Risk — 2건 (ReconcileRunner OOM, ClassAutoCloseJob 메모리 적재)

### Findings

#### 1. [기준4] — ReconcileService.scoreOf 중복
- 대상 위치 — `infrastructure/ReconcileService.java#scoreOf` (line 106~108) vs `application/enrollment/EnrollmentMirrorService.java#scoreOf` (line 129~131)
- 문제점 — 동일 공식 `epochSec * 1e9 + nano` 가 두 곳에 정의. ReconcileService 주석에도 "Issue #55 에서 EnrollmentMirrorService.scoreOf(Instant) 로 추출 예정" 명시. 미완 TODO.
- 개선안 — `ReconcileService.doReconcileOne` 의 내부 호출을 `EnrollmentMirrorService.scoreOf(e.getAppliedAt())` 로 교체. 다만 ReconcileService 가 EnrollmentMirrorService 를 주입받는 것은 도메인 결합도 상승 위험 — 대신 `EnrollmentMirrorService.scoreOf` 를 그대로 static 으로 재사용 가능 (이미 static). 변경 범위 — ReconcileService.java 1 파일, 2 곳 호출 + 로컬 메서드 삭제. P1.

#### 2. [기준4] — EnrollmentController.parseStatuses 가독성
- 대상 위치 — `web/enrollment/EnrollmentController.java#parseStatuses` (line 76~85)
- 문제점 — `EnrollmentStatus::valueOf` 가 invalid 입력 시 IllegalArgumentException 던지는데 GlobalExceptionHandler 는 별도 매핑 없음 → handleGeneric 으로 떨어져 500. 사용자 입력 검증 누락. (4축이 아니라 5축 가까울 수 있음 — 안전성 결함)
- 개선안 — invalid status 입력에 대해 try-catch 후 400 ProblemDetail 반환, 또는 GlobalExceptionHandler 에 `IllegalArgumentException` 핸들러 추가 (단 광범위 매핑은 위험). **반려 후보** — Public API 의 응답 동작 변경에 해당. 본 사이클 Public API 변경 금지 제약 위반. **P2 + DOCS 검토 항목**.

#### 3. [기준5] — CurrentUser record dead code
- 대상 위치 — `web/auth/CurrentUser.java`
- 문제점 — `CurrentUser` record 가 정의만 되어 있고 어디서도 사용되지 않음. `CurrentUserId` 어노테이션 + `CurrentUserArgumentResolver` 가 직접 UUID 주입하는 패턴으로 대체됨. record 는 잔여물.
- 개선안 — 삭제. 1 파일 제거. ROI 매우 낮지만 노이즈 정리. P2.

#### 4. [기준4 보조] — RedisKeyFactory + DomainException 의 shared 위치 적절성
- 평가 — RedisKeyFactory 는 `infrastructure/` 위치, DomainException 은 `domain/shared/` 위치. Class/Enrollment 양쪽이 모두 의존하지만 도메인 결합 상승 아님. **개선 불필요.**

### ⚠ Invariant Risk
- 없음.

### Risk Findings (기준7)
- **R1. ReconcileRunner 부팅 시 전체 OPEN 클래스 메모리 적재** — `findByStatus(OPEN)` 로 전체 OPEN 클래스 List 적재 후 순차 reconcile. 클래스가 수만 단위면 OOM. 현 운영 규모 비위험. **보고만.**
- **R2. ReconcileService.doReconcileOne 의 enrollment 전체 적재** — classId 별 active enrollment 전체를 List 로 받아 HashSet 으로 ZADD. capacity 가 수만 단위면 메모리/네트워크 부담. 현 운영 규모 비위험 (capacity ≥ 1 만 invariant). **보고만.**
- **R3. ClassLockService SAFE_UNLOCK Lua 의 static field** — `SAFE_UNLOCK` 이 static DefaultRedisScript. EVALSHA 캐시 가능. 문제 없음. **검토 결과 안전.**

### Out of scope — 호출자(maestro) 결정 필요
- **D1. RedisKeyFactory 가 `infrastructure/` 위치 — 도메인 호출자가 의존** — `application/clazz/ClassStatusMirrorListener` 가 `infrastructure.RedisKeyFactory` import. 패키지 레이어 위반은 아니지만 (application → infrastructure 는 허용) — 그냥 보고. 개선 불필요 결정.
