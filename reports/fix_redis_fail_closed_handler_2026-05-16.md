# fix(web): Redis 다운 503 fail-closed 핸들러

작성일: 2026-05-16
브랜치: `fix/handle-redis-querytimeout`
관련 PR: (gh pr create 후 추가)
Discovered in: #102

---

## 1. 발견 맥락

PR #102 (`RedisDisconnectFailClosedTest`) 에서 Redis 컨테이너를 apply 직전 stop() 시키고 fail-closed
계약을 검증하던 중, 실 운영 코드 경로에서 Redis 첫 호출 지점이 `ClassLockService.executeWithLock` 의
`redisTemplate.opsForValue().setIfAbsent(...)` 임을 확인했다.

이 지점은 Spring Data Redis 의 `PassThroughExceptionTranslationStrategy` 가 raw Redis 예외를
`org.springframework.dao.QueryTimeoutException` (혹은 `RedisConnectionFailureException`) 으로
번역한 채 그대로 전파시키므로, `EnrollmentMirrorService` 의 `try { } catch (... ex) { throw new
MirrorUnavailableException(...) }` 변환 로직을 거치지 않는다. 결과적으로 `GlobalExceptionHandler` 의
`@ExceptionHandler(MirrorUnavailableException.class)` 가 매칭되지 못하고, catch-all
`handleGeneric(Exception)` 으로 빠져 HTTP **500 INTERNAL_ERROR** 가 응답됐다.

정책 (ARCHITECTURE.md §4): Redis 다운 = **HTTP 503 fail-closed**. 500 응답은 운영 모니터링/SLO 산정·
재시도 로직에 잘못된 신호를 보낸다. 본 PR 은 핸들러 한 줄로 갭을 메운다.

`RedisDisconnectFailClosedTest` 가 이 갭을 우회하기 위해 `satisfiesAnyOf(MirrorUnavailableException,
DataAccessException)` 로 작성된 사실 자체가 production 갭의 증거. 본 PR 머지 후 follow-up 으로
해당 테스트를 strict 화할 수 있다 (아래 §3).

---

## 2. 변경 사항

### `GlobalExceptionHandler.java`
- import 추가: `org.springframework.dao.QueryTimeoutException`,
  `org.springframework.data.redis.RedisConnectionFailureException`.
- 신규 핸들러 `handleRedisUnavailable(Exception)` —
  `@ExceptionHandler({QueryTimeoutException.class, RedisConnectionFailureException.class})`.
  - HTTP 503 SERVICE_UNAVAILABLE.
  - `errorCode = "MIRROR_UNAVAILABLE"` (기존 `MirrorUnavailableException` 매핑과 동일 → 클라이언트 입장 응답 호환).
  - `log.error(...)` 로 stack trace 1줄 로깅.
- 핸들러 위에 트레이드오프 javadoc 명시 (왜 DataAccessException 전체가 아닌지 — §3 참조).
- 다른 핸들러·기존 한국어 file header 주석 그대로 유지.

### `GlobalExceptionHandlerTest.java`
- 기존 `DummyController` 에 `/redis-timeout`, `/redis-conn` 두 엔드포인트 추가 (각각 두 예외를 throw).
- 케이스 2개 추가.
  - `redisQueryTimeout_returns503MirrorUnavailable` → 503 + errorCode `MIRROR_UNAVAILABLE`.
  - `redisConnectionFailure_returns503MirrorUnavailable` → 503 + errorCode `MIRROR_UNAVAILABLE`.
- 기존 `domainException_returnsProblemDetail` 케이스는 path 만 `/api/test-error/domain` 으로 분리.

### Out of scope
- `ClassLockService` 자체 변경 (`catch + throw MirrorUnavailableException` 으로 변환하는 방안) — 핸들러
  추가만으로 동일 효과를 얻고, application 패키지 → infrastructure 패키지로 application 예외를 옮기는
  의존 역전을 피하기 위해 선택하지 않았다.
- `RedisDisconnectFailClosedTest` 의 `satisfiesAnyOf` 제거 (strict 화). 다음 세션 follow-up.

---

## 3. 트레이드오프

### Why not `@ExceptionHandler(DataAccessException.class)` ?
`DataAccessException` 은 Spring Data 의 데이터 접근 계층 통합 예외다. **JPA / Postgres 장애도 같은
계층에서 raise** 된다 (예: `JpaSystemException`, `DataIntegrityViolationException`,
`CannotAcquireLockException`). 이걸 일괄 503 으로 잡으면.

- Postgres 다운 (운영 P0) 이 Redis 다운과 같은 응답 코드로 흡수돼 알람·on-call 진단이 모호해진다.
- DB unique constraint violation 같은 4xx 후보까지 503 으로 떨어진다.

대신 Redis 한정 두 예외만 좁게 매핑.
- `RedisConnectionFailureException` (Spring Data Redis 전용 — 연결 자체 실패).
- `QueryTimeoutException` (`DataAccessException` 하위 공통이지만, 실측상 Redis stop 시 raise 되는
  것을 PR #102 테스트가 확인).

JPA 도 `QueryTimeoutException` 을 raise 할 수 있지만 현재 코드베이스의 `@Transactional` 타임아웃
설정상 발생 빈도가 낮고, 발생하더라도 503 fail-closed 응답이 의미상 부적합하진 않다 (재시도 권장
시그널). 만약 JPA 쿼리 타임아웃을 별도로 분류해야 한다면 follow-up 으로 `JpaSystemException` 등을
구분 매핑하는 방안이 가능하다.

### Why `errorCode = "MIRROR_UNAVAILABLE"` (별도 코드 신설 X) ?
- `MirrorUnavailableException` 응답과 동일 코드로 통일하면 클라이언트 측 retry 로직이 한 코드만 보면 된다.
- 별도 `REDIS_UNAVAILABLE` 코드를 신설하면 클라이언트 측 매핑 테이블이 분기돼 운영 표면이 늘어난다.
- 정책 (Redis 다운 = 503 fail-closed) 관점에서는 두 경로의 의미가 동일.

---

## 4. 검증

### 로컬 테스트
```
cd C:/work/fix-redis-503/live-class

./gradlew test --tests "*GlobalExceptionHandler*"
# BUILD SUCCESSFUL — 3 tests passed.

./gradlew test --tests "*RedisDisconnect*"
# BUILD SUCCESSFUL — RedisDisconnectFailClosedTest 통과 (Docker 필요).

./gradlew test
# BUILD SUCCESSFUL — 회귀 0.
```

### Production 동작 확인 시나리오 (수동)
1. Postgres + Redis 컨테이너 up.
2. apply 호출이 정상 흐름으로 동작.
3. `docker compose stop redis`.
4. apply 호출 → HTTP 503 + body `{"errorCode":"MIRROR_UNAVAILABLE", ...}` (이전엔 500 `INTERNAL_ERROR`).

---

## 5. Follow-up

- `RedisDisconnectFailClosedTest` strict 화 — `satisfiesAnyOf(MirrorUnavailableException,
  DataAccessException)` 를 `isInstanceOf(... 503 매핑 대상 예외)` 로 강화. 본 핸들러가 merge 된 후
  fail-closed 의 강한 invariant 를 표현할 수 있게 된다.
- ClassLockService 자체에서 raw Redis 예외를 `MirrorUnavailableException` 으로 변환하는 방안은
  의존 방향상 무리 (`infrastructure` → `application` 역전) 이므로 본 PR 에서는 보류.
