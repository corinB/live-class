<!-- 트러블슈팅 1 - Redis 다운 시 500 응답 회귀 + 503 fail-closed 핸들러 도입 + t3.nano 한계 부하에서 자체 방어막 작동 검증 -->
# 트러블슈팅 #1 — Redis 다운 503 매핑 갭과 t3.nano 자체 방어막

> 인용 출처 — PR #104 (`5a03b3b fix(web): map redis QueryTimeout to 503 fail-closed`), PR #102 (`5686cf5 test(concurrency): complete task 12`), PR #112 부하 보고서 (`reports/load-test/t3-small-limit-2026-05-16.md`).

---

## 1. 문제 상황

### 1.1 발견 경로

PR #102 에서 `RedisDisconnectFailClosedTest` 가 추가되었다. Redis 컨테이너를 apply 직전 `stop()` 시키고, fail-closed 계약 — Redis 다운 = HTTP 503 — 을 검증하는 통합 테스트였다.

테스트는 통과했다. 그런데 그 테스트의 assertion 형태가 묘했다.

```java
// RedisDisconnectFailClosedTest 단편 (PR #102 시점)
assertThat(thrown)
    .satisfiesAnyOf(
        t -> assertThat(t).isInstanceOf(MirrorUnavailableException.class),
        t -> assertThat(t).isInstanceOf(DataAccessException.class));
```

본 라인은 사실상 **정책 위반의 증거** 였다. ARCHITECTURE.md §4 가 명시한다. "Redis 다운 = HTTP 503 fail-closed". 그렇다면 단일 예외 타입이 던져져야 하는데, 테스트는 두 후보를 `satisfiesAnyOf` 로 묶고 있었다.

### 1.2 production 응답 추적

실제 production 경로에서 apply 호출이 Redis 다운 상태에 부딪히면 어떻게 되는가? 첫 Redis 호출 지점이 어디인지 추적했다.

```
EnrollmentApplicationService.apply()
  └─> ClassLockService.executeWithLock(classId, ...)
        └─> redisTemplate.opsForValue().setIfAbsent(key, token, TTL, MS)   // ← 여기
```

이 지점에서 Redis 가 다운되어 있으면 Spring Data Redis 의 `PassThroughExceptionTranslationStrategy` 가 raw Redis 예외를 `QueryTimeoutException` 또는 `RedisConnectionFailureException` 으로 번역한 채 그대로 전파시킨다.

그 다음에 호출될 예정이었던 `EnrollmentMirrorService` 의 `try { } catch (... ex) { throw new MirrorUnavailableException(...) }` 변환 로직은 — **호출되지 못한다**. 이미 outer 단계에서 예외가 발생해 mirror 단계로 들어가지 못하기 때문이다.

결과적으로 `GlobalExceptionHandler` 의 `@ExceptionHandler(MirrorUnavailableException.class)` 가 매칭되지 못하고, catch-all `handleGeneric(Exception)` 으로 떨어져 — HTTP **500 INTERNAL_ERROR** 가 응답되었다.

```
[ERROR] Unhandled exception reached GlobalExceptionHandler
org.springframework.dao.QueryTimeoutException: Command timed out after 200 millisecond(s)
  at io.lettuce.core.internal.ExceptionFactory.createTimeoutException(...)
  ...
```

정책 (ARCHITECTURE.md §4) 은 Redis 다운 = 503. 500 응답은.

- 운영 모니터링이 `5xx` 가 아닌 `INTERNAL_ERROR` 알람을 잘못 잡는다.
- 클라이언트가 retry 결정을 못 한다 (`5xx` 일반은 retriable, 500 은 일반적으로 non-retriable 로 해석).
- SLO 산정에서 `availability` 가 잘못 떨어진다.

---

## 2. 원인 분석

### 2.1 Spring Data Redis 예외 번역 계층의 사각

Spring Data 의 데이터 접근 예외 통합은 `DataAccessException` 계층 구조로 표현됩니다. JPA·JDBC·Redis 모두 이 계층에 속한 예외를 던지며, 애플리케이션 코드는 어느 storage 인지 신경 쓰지 않고 동일하게 처리하도록 설계되었습니다. 좋은 의도지만, 본 사이클에서 이게 부메랑이 되었습니다.

`EnrollmentMirrorService` 는 Lua 호출 주변을 try-catch 로 감싸 `MirrorUnavailableException` 으로 변환합니다. 하지만 `ClassLockService.executeWithLock` 의 첫 `setIfAbsent` 는 그 try-catch 진입 전이라, 변환이 적용되지 않습니다. 의존 방향상 `ClassLockService` 는 `infrastructure` 패키지에 있고 `MirrorUnavailableException` 은 `application` 패키지에 있어 인프라가 애플리케이션 예외를 던지려면 의존 역전이 필요합니다. 그래서 ClassLockService 안에서 변환하는 방안은 깔끔하지 않습니다.

### 2.2 왜 통합 테스트는 통과했는가

`RedisDisconnectFailClosedTest` 가 `satisfiesAnyOf(MirrorUnavailableException, DataAccessException)` 로 작성된 이유가 여기 있었습니다. 작성 당시 production 경로의 일관성이 깨져 있었지만 테스트를 통과시키기 위해 두 타입을 묶었던 것입니다. 테스트의 strict 화를 미뤘던 결정이 production 갭의 증거로 남았습니다.

### 2.3 왜 `DataAccessException` 전체를 503 매핑하면 안 되는가

Trade-off 비교.

| 후보 | 매핑 범위 | 장점 | 단점 |
|---|---|---|---|
| `DataAccessException` 전체 | Redis + JPA + JDBC 전체 | 한 줄로 끝 | Postgres 다운 (P0) 이 Redis 다운과 같은 503 → on-call 진단 모호. DB unique constraint violation 도 503 → 4xx 후보 손상 |
| `RedisConnectionFailureException` + `QueryTimeoutException` | Redis 전용 두 가지 | 좁고 의도적 | `QueryTimeoutException` 은 JPA 도 던질 수 있음 (현재 코드는 `@Transactional` timeout 설정상 발생 빈도 낮음, 발생해도 503 응답이 의미상 무해 — retriable 시그널) |
| `ClassLockService` 안 변환 | 변환을 인프라 단에서 처리 | 핸들러 추가 없음 | `infrastructure` → `application` 패키지 의존 역전 |

채택 — **두 번째**. Redis 한정 두 예외만 좁게 매핑. 의존 방향과 운영 진단 명료성을 동시에 만족.

---

## 3. 의사결정 및 해결

### 3.1 결정

`GlobalExceptionHandler` 에 핸들러 한 줄 추가 — `@ExceptionHandler({QueryTimeoutException.class, RedisConnectionFailureException.class})`.

코드.

```java
// GlobalExceptionHandler.java — 신규 핸들러
@ExceptionHandler({QueryTimeoutException.class, RedisConnectionFailureException.class})
public ResponseEntity<ProblemDetail> handleRedisUnavailable(Exception ex) {
    log.error("Redis unavailable — mapping to 503", ex);
    ProblemDetail detail = ProblemDetail.forStatusAndDetail(
            HttpStatus.SERVICE_UNAVAILABLE, "Enrollment service temporarily unavailable. Please retry.");
    detail.setProperty("errorCode", "MIRROR_UNAVAILABLE");
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(detail);
}
```

`errorCode` 는 기존 `MirrorUnavailableException` 매핑과 동일한 `MIRROR_UNAVAILABLE` 로 통일 — 클라이언트 retry 로직이 단일 코드만 보면 됨.

### 3.2 회피한 대안

- `DataAccessException` 전체 매핑 — 진단 모호 / 4xx 후보 손상 사유로 기각.
- `ClassLockService` 자체 변환 — 의존 역전 비용 사유로 기각.
- 새 `REDIS_UNAVAILABLE` 코드 신설 — 클라이언트 분기 비용 사유로 기각.

---

## 4. 결과

### 4.1 production 검증 시나리오

```bash
# 1. 인프라 기동
docker compose --profile db --profile redis up -d

# 2. 백엔드 부팅
cd live-class && ./gradlew bootRun

# 3. 정상 apply 호출 — 201 PENDING

# 4. Redis 중지
docker compose stop redis

# 5. apply 재호출
curl -sS -X POST http://localhost:8080/api/enrollments \
  -H "X-User-Id: $STUDENT" \
  -H "Content-Type: application/json" \
  -d "{\"classId\":\"$CLASS\"}"

# 응답 (수정 전): HTTP 500 + {"errorCode":"INTERNAL_ERROR", ...}
# 응답 (수정 후): HTTP 503 + {"errorCode":"MIRROR_UNAVAILABLE", ...}
```

### 4.2 자체 방어막 작동 — t3.nano 한계 부하

본 핸들러 머지 후 한 달 뒤, 격리 부하 테스트 환경에서 자체 방어막이 의도대로 작동함을 확인했다 (`reports/load-test/t3-small-limit-2026-05-16.md`).

t3.nano 시뮬레이션 (2 vCPU / 0.5 GiB, app heap 220m, Redis 0.3 cpu / 35m) 에서 50k 사용자 / 100 정원 부하를 11분간 가한 결과.

```
[22:25:57] app mem=99.98% cpu=158%  (burst 직후 over-measure)
[22:26:29] app mem=99.91% cpu=135%
[22:27:33] app mem=99.96% cpu=136%
[22:28:04] app mem=98.17% cpu=138%  (GC 회수)
[22:29:09] app mem=96.45% cpu= 54%  (fail-closed 안정화로 burst 진정)
[22:30:12] app mem=92.69% cpu=  4%  (throttle 진입)

[ERROR] io.lettuce.core.RedisCommandTimeoutException: Command timed out after 200 millisecond(s)
[ERROR] Redis unavailable — mapping to 503
```

| 지표 | 결과 |
|---|---|
| app 컨테이너 생존 | 11분 부하에서 healthy 유지 — **fail-closed 가 자체 방어막** |
| 데이터 무결성 (DB) | 손상 없음 — Redis timeout 발생 시 DB 변경 전 503 매핑 |
| 운영 가능성 (사용자 관점) | t3.nano 는 사실상 불가 — 대부분 503. **t3.small 이 최소 운영선** (10k/100 PASS, mem 37%) |

운영 시 사용자 관점에서는 503 폭주 = 실질 사용 불가다. 하지만 시스템 자체는 죽지 않고, 데이터는 깨지지 않는다. **정합성 우선 정책의 의도가 한계 상황에서 정확히 작동한 사례** 였다.

### 4.3 검증 테스트

```bash
./gradlew test --tests "*GlobalExceptionHandler*"
# BUILD SUCCESSFUL — 3 tests passed
#   - domainException_returnsProblemDetail
#   - redisQueryTimeout_returns503MirrorUnavailable    (신규)
#   - redisConnectionFailure_returns503MirrorUnavailable (신규)

./gradlew test --tests "*RedisDisconnect*"
# BUILD SUCCESSFUL — 통과
```

### 4.4 후속 작업

- `RedisDisconnectFailClosedTest` 의 `satisfiesAnyOf` 제거 (strict 화) — 본 핸들러 머지 후 강한 invariant 표현 가능.
- t3.nano 이하 사양으로 운영하지 않도록 README 11.5 / `reports/load-test/` 명시.

---

## 5. 핵심 takeaway

- 테스트 assertion 의 형태가 production 갭의 증거가 되는 경우가 있다. `satisfiesAnyOf` 가 등장하면 정책 일관성을 의심한다.
- Spring Data 의 통합 예외 계층은 강력하지만, 핸들러를 좁게 잡지 않으면 storage 별 정책을 운영에서 분리할 수 없다.
- fail-closed 정책은 한계 부하 상황에서 시스템을 살리는 자체 방어막으로 작동한다. 가용성을 일부 포기하는 대가로 정합성과 생존이 확보된다.
