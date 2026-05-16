# 워커 리포트 — Class 도메인 (2026-05-16)

> 디스패치 방식 — 환경상 Agent 툴 미가용. Maestro 가 worker 역할로 직접 7축 스캔 수행.

## 모듈: Class

### Summary
- 스캔 파일 수 — `domain/clazz/` 13개 + `application/clazz/` 2개 = 15개
- 후보 (기준별)
  - 기준1 Rich Enum — 0건 (ClassStatus 는 단순 상수가 적절)
  - 기준2 도메인 로직 이동 — 1건 (검증 분기 잔존, 후술)
  - 기준3 Common 의존성 — 0건
  - 기준4 코드 최적화 — 1건 (transitionStatus 재시도 루프)
  - 기준5 주석 직관성 — 1건 (Class.draft 인라인 주석 압축)
  - 기준6 AfterCommit — **0건 (이미 정상)**
  - 기준7 Risk — 1건 (QuartzConfig in-memory JobStore)

### Findings

#### 1. [기준4] — ClassApplicationService.transitionStatus 중복 잠금 + 수동 retry
- 대상 위치 — `application/clazz/ClassApplicationService.java#transitionStatus` (line 67~105)
- 문제점 — `ClassRepository.findByIdForUpdate` (PESSIMISTIC_WRITE) + `@Version` 낙관적 락 + 2회 수동 retry 루프가 동시에 사용. 비관락이 직렬화하면 OptimisticLockingFailure 발생 가능성이 매우 낮아 retry 루프가 사실상 불필요. `Class.@Version` 만으로 충분하거나 (DOCS.md ARCH §2.4 명시 — `@Version` 만 사용한다고 적힘) 비관락 단독.
- 개선안 — `findByIdForUpdate` 제거 후 일반 `findById` + 기존 retry 루프 유지. ARCHITECTURE §2.4 의 "@Version optimistic lock on Class row" 와 정합. ClassRepository.findByIdForUpdate 도 함께 제거 (다른 사용처 없음).

#### 2. [기준2] — Class.draft 의 검증 분기 vs Value Object 검증 책임 분리
- 대상 위치 — `domain/clazz/Class.java#draft` (line 62~102)
- 문제점 — `Class.draft` 가 `title`, `description` 길이/null 을 직접 검증. 다른 VO (Money/Capacity/ClassPeriod) 는 자체 생성자에서 검증하는 패턴인데 title/description 만 Class 에 분산. 일관성 손실.
- 개선안 — **컷 후보로 보고**. `Title` / `Description` VO 신규 생성은 단일 사용 추상화 → 오버엔지. 현 상태 유지 권장. **반려**.

#### 3. [기준5] — Class.draft 의 version 인라인 주석 압축
- 대상 위치 — `domain/clazz/Class.java#draft` (line 97~101)
- 문제점 — `// version stays null until ...` 주석 5줄이 매우 자세. Why 가 길게 적혀 있어 다음 독자에게 부담. 동작 번역형은 아니지만 압축 가능.
- 개선안 — 1~2줄로 줄여서 핵심만 유지 ("version 은 Hibernate 가 @PrePersist 에서 할당. 명시 0L 은 save() merge path 유발 — 회피"). P2.

#### 4. [기준6] — 이미 정상 AfterCommit
- 위치 — `application/clazz/ClassStatusMirrorListener.java#onOpened/onClosed`
- 평가 — `@TransactionalEventListener(AFTER_COMMIT)` 사용. transitionStatus 가 트랜잭션 내부에서 `publishEvent` 호출하지만 listener 가 AFTER_COMMIT 으로 받음. **정상**.

### ⚠ Invariant Risk
- 없음.

### Risk Findings (기준7)
- **R1. QuartzConfig in-memory JobStore** — `QuartzConfig.java` 에 `storeDurably()` 만 사용, JDBC JobStore 아님. 단일 EC2 운영 가정에 따른 의도된 디자인 (ARCHITECTURE §4 명시). 스케일아웃 시 trigger 중복 실행 위험. **현재 의도된 동작이라 보고만 진행.**
- **R2. ClassAutoCloseJob.executeInternal** — `findByStatusAndPeriodEndDateBefore` 가 전체 OPEN 클래스 리스트를 메모리에 적재 후 순차 `classApplicationService.autoClose` 호출. 클래스 수가 수천 단위로 늘어나면 OOM 후보. 현재 운영 규모 (제출 과제 단일 EC2) 에서는 비위험. **보고만.**

### Out of scope — 호출자(maestro) 결정 필요
- **D1. ClassRepository.findByIdForUpdate 제거 시 ARCHITECTURE §2.4 와 코드 정합** — 제거가 정합에 맞지만 회귀 위험 (last-line defense 의도였을 가능성). 사용자 결정 필요.
