# Redis 인프라 + LuaScriptConfig (Lua RedisScript 빈) — Cache 제거

- **Assignee:** The Infra Operator
- **Dependencies:** 01_Infra_Operator_MockUserFilter_And_ExceptionHandler.md
- **Definition of Done (DoD):**
  - Spring Boot 4 가 자동 등록하는 `LettuceConnectionFactory` + `StringRedisTemplate` 를 그대로 사용한다 (별도 빈 정의 없음). `application.yaml` 의 `spring.data.redis.timeout = 200ms` 로 Lettuce commandTimeout 을 짧게 설정 (ARCHITECTURE §4.4 fail-closed 정책).
  - **`LuaScriptConfig` 가 등록되어 `enrollment_apply.lua`, `enrollment_cancel_promote.lua`, `enrollment_compensate.lua` 3 개의 `RedisScript` 빈을 classpath 의 `lua/*.lua` 에서 로드한다 (ARCHITECTURE §4.4 §5.3 §6.1 §6.3).** task 02 단계에서는 .lua 파일에 placeholder 본문만 둔다 — 실제 본문은 task 09 (apply), task 10 (cancel_promote), task 09 (compensate) 가 채운다.
  - 통합 테스트 환경에서 Testcontainers Redis 컨테이너로 RedisTemplate + Lua 빈 컨텍스트가 로드된다.
  - **Redisson 의존성은 추가하지 않는다 (Pre-flight 4 결정).**
  - **Spring Cache (`@Cacheable` + `RedisCacheManager`) 도 사용하지 않는다 (Pre-flight 5 결정).** ZSET mirror 가 결정 경로 캐시 역할을 이미 하므로 별도 metadata cache 의 ROI 가 낮음. 채용 과제 트래픽에서 perf 차이 무시 가능. 면접 시 "production 에서 RPS·hit-rate·TTL 측정 후 도입" 으로 설명. `RedisCacheManager` 빈도 `@EnableCaching` 도 사용하지 않는다.

## Action Items (Checklist)

- [x] `build.gradle` 에 Testcontainers 의존성 추가 (Spring Boot 4 BOM 좌표 사용).
- [x] `live-class/src/main/java/com/example/liveclass/config/LuaScriptConfig.java` 작성 — `DefaultRedisScript` 3개 빈.
- [x] `live-class/src/main/resources/lua/enrollment_apply.lua` placeholder (`return 'PENDING'`).
- [x] `live-class/src/main/resources/lua/enrollment_cancel_promote.lua` placeholder (`return nil`).
- [x] `live-class/src/main/resources/lua/enrollment_compensate.lua` placeholder (`return 0`).
- [x] `application.yaml` 에 `spring.data.redis.timeout = 200ms` 설정 (Lettuce commandTimeout). **`spring.cache.*` 키는 추가하지 않는다.**
- [x] **`RedisConfig.java` 제거** — Spring Boot default LettuceConnectionFactory + StringRedisTemplate 만 사용. 별도 ConnectionFactory / Template / CacheManager 빈 정의 없음 (BeanDefinitionOverrideException 회피).
- [x] **`LiveClassApplication.java` 에서 `@EnableCaching` 제거** — Spring Cache 미사용 (Pre-flight 5).
- [x] `live-class/src/test/java/com/example/liveclass/support/RedisContainerExtension.java` 작성 — Testcontainers Redis + `@DynamicPropertySource`.
- [x] (Verify) `live-class/src/test/java/com/example/liveclass/config/RedisConfigTest.java` — `RedisTemplate` + Lua 3개 빈 주입 검증, `RedissonClient` 부재 검증, `liveClassCacheManager` 부재 검증.
- [x] (Verify) 각 RedisScript 빈의 `getScriptAsString()` placeholder 본문 검증.
