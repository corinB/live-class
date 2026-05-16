# 12 — Quality Guardian — Concurrency Integration Tests (잔여 3 시나리오)

- 작성일. 2026-05-16
- 담당. Worker (자동화 파이프라인)
- 작업 브랜치. `feature/task-12-remaining-3` (base d6d3977 / origin/main)

## Input Summary

PR #101 (d6d3977) 로 task 12 의 4 시나리오(LastSeatRace · CancelDoubleClick · WaitlistPromotion · LuaCompensation)가 main 에 머지된 상태였다. 잔여 3 시나리오(7일 취소 창 경계값 · Redis disconnect fail-closed · flushDb 후 reconcile)를 동일 패턴으로 추가해 task 12 DoD 를 마무리한다.

## What Was Done

세 개의 통합 테스트 파일을 `live-class/src/test/java/com/example/liveclass/application/enrollment/` 에 추가했다.

| 파일 | 케이스 | 비고 |
|------|--------|------|
| `CancellationWindowBoundaryConcurrencyTest.java` | 3 | paidAt + 7d (200), +7d+1ns (422), +6d23h (200) — 단일 실행. |
| `RedisDisconnectFailClosedTest.java` | 1 | 전용 RedisContainer 를 띄워 apply 직전 stop() → 예외 전파 + DB row 0 검증. |
| `ReconcileTest.java` | 1 | capacity=2 + 3 apply → 2 PENDING + 1 WAITLISTED 적재 후 flushDb → reconcileOne → ZSET 재구성 검증. |

추가한 테스트들은 기존 4 시나리오와 동일 패턴을 따른다.

- `@IntegrationTest` + `@ExtendWith(RedisContainerExtension.class)` (단, RedisDisconnect 는 전용 컨테이너 사용).
- `@DynamicPropertySource` 로 `PostgresTestContainer.applyProperties` + `RedisContainerExtension.applyProperties`.
- `RedisKeyFactory.enrolled/waitlist/classStatus(classId)` 사용 (인라인 키 금지).
- `EnrollmentMirrorService` 경유 (인라인 lua 호출 금지) — 다만 reconcile 은 ReconcileService 를 직접 호출.

## Rationale & Tradeoffs

### CancellationWindow 단일 실행

`@RepeatedTest` 미사용. cancel(now Instant) 파라미터로 시간을 결정론적으로 제어하므로 한 번 실행으로 충분하다. CancellationWindow VO 의 폐구간 정책 (`!now.isAfter(paidAt + 7d)`) 을 그대로 검증한다.

### RedisDisconnect — 전용 컨테이너

`RedisContainerExtension.REDIS_CONTAINER` 는 정적 싱글톤으로 모든 통합 테스트가 공유한다. 이 컨테이너를 stop() 하면 다른 테스트 클래스가 끊긴 연결을 물려받아 비결정적으로 실패한다. 전용 `RedisContainer` 인스턴스를 별도로 띄워 stop 하는 식으로 공유 컨테이너를 보호했다. `@DirtiesContext(AFTER_CLASS)` 로 Spring 컨텍스트 캐시도 격리.

### fail-closed invariant — production 갭 발견

테스트 작성 중 `ClassLockService.executeWithLock` 의 `setIfAbsent` 가 apply 흐름의 첫 Redis 호출이라는 사실을 확인했다. Redis 가 죽어 있으면 Spring 의 `PassThroughExceptionTranslationStrategy` 가 `QueryTimeoutException` (DataAccessException 하위) 으로 변환해 던지는데, `EnrollmentMirrorService.tryApply` 안으로 진입하지 못해 `MirrorUnavailableException` 매핑을 받지 못한다. `GlobalExceptionHandler` 가 `QueryTimeoutException` 을 명시 처리하지 않아 HTTP 503 이 아닌 500 으로 응답되는 production 갭이 있다.

본 테스트는 "어떤 예외라도 전파되고 DB row 가 생성되지 않는다" 라는 fail-closed 의 핵심 invariant 만 strict 검증하도록 했다. `satisfiesAnyOf(MirrorUnavailable, DataAccess)` 로 두 분기 모두 허용. 갭 자체는 다음 task 의 follow-up.

### Reconcile — capacity 해석

task 스펙은 "capacity=3, PENDING 2 + WAITLISTED 1" 이라 적혀 있으나 실제로는 capacity=3 으로는 3명 apply 시 모두 PENDING 이 되어 그 분포가 안 나온다. 분포가 자연스럽게 나오는 capacity=2 + 3 apply 로 해석했다 (테스트 코멘트에도 표기). reconcile 본체의 동작 검증이 목표라 capacity 자체에는 의미가 없다.

## Verification

로컬 (Docker 가용) 환경에서 다음 명령으로 3 시나리오 통과 확인.

```
cd C:/work/task12-rest/live-class
./gradlew test --tests "*CancellationWindow*" --tests "*RedisDisconnect*" --tests "*Reconcile*"
```

결과 (BUILD SUCCESSFUL).

- `CancellationWindowBoundaryConcurrencyTest` — 3 tests / 0 failures / 0 errors / 1.286s.
- `ReconcileTest` — 1 test / 0 failures / 0 errors / 1.613s.
- `RedisDisconnectFailClosedTest` — 1 test / 0 failures / 0 errors / 10.653s (전용 컨테이너 부팅 + 컨텍스트 dirty 비용).

추가로 전체 테스트 (`./gradlew test`) 도 BUILD SUCCESSFUL 로 회귀 없음 확인. Docker (Engine 29.2.1) 정상 가동, Testcontainers PostgreSQL 16 + Redis 7 사용.

## Follow-ups

- `ClassLockService.executeWithLock` 의 `setIfAbsent` 가 Redis 실패 시 `MirrorUnavailableException` 으로 변환되도록 보강 (또는 `GlobalExceptionHandler` 에 `QueryTimeoutException` / `RedisConnectionFailureException` 매핑 추가). fail-closed 의 HTTP 503 계약 완결.
- LuaCompensationConcurrencyTest 의 클래스명에 "Concurrency" 가 붙어 있지만 실제로는 단일 thread 보상 시나리오 (Gemini P2#1) — 다음 세션에서 분리/리네이밍.
- 4 시나리오 + 신규 3 시나리오의 setUp 코드가 중복 — TestHelper / TestFixtures 추출 (Gemini P2#2). 다음 세션.