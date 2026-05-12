# Redis 인프라 + Cache + LuaScriptConfig (Lua RedisScript 빈)

- **Assignee:** The Infra Operator
- **Dependencies:** 01_Infra_Operator_MockUserFilter_And_ExceptionHandler.md
- **Definition of Done (DoD):**
  - `RedisConnectionFactory`(Lettuce 기반)와 `RedisTemplate<String, String>`이 빈으로 등록되고 `spring.data.redis.*` 프로퍼티로 동작한다.
  - `CacheManager`(Spring Cache `RedisCacheManager`)가 등록되고 `class:detail` 캐시는 TTL 300초, `class:enrolledCount` 캐시는 TTL 60초로 분리된다 (ARCHITECTURE §5.2).
  - **`LuaScriptConfig`가 등록되어 `enrollment_apply.lua`, `enrollment_cancel_promote.lua`, `enrollment_compensate.lua` 3 개의 `RedisScript` 빈을 classpath 의 `lua/*.lua` 에서 로드한다 (ARCHITECTURE §4.4 §5.3 §6.1 §6.3).** task 02 단계에서는 .lua 파일에 placeholder 본문만 둔다 — 실제 본문은 task 09 (apply), task 10 (cancel_promote), task 09 (compensate) 가 채운다.
  - 컨테이너 부팅 시 Redis 연결 실패해도 애플리케이션이 즉시 죽지 않고 `connection timeout 200ms` + circuit-breaker 로 fallback 가능하다 (ARCHITECTURE §7.1 — 단 신청·취소 API 는 Lua 호출 실패 시 503 fail-closed 정책. task 09/10 에서 enforce).
  - 통합 테스트 환경에서 Testcontainers Redis 컨테이너로 캐시 빈 + Lua 빈 컨텍스트가 로드된다.
  - **Redisson 의존성은 추가하지 않는다 (Pre-flight 4 결정).** cache stampede 방어는 단일 JVM 가정으로 `synchronized` 또는 `ConcurrentHashMap.computeIfAbsent` 같은 JVM-내 single-flight 로 task 05 단계에서 처리. 다중 인스턴스 확장 시 분산 single-flight 도입은 본 채용 과제 범위 외 (README §10 가능).

## Action Items (Checklist)

- [ ] `build.gradle` 에 Testcontainers 의존성 추가.
  - `testImplementation 'org.testcontainers:postgresql'`
  - `testImplementation 'org.testcontainers:junit-jupiter'`
  - `testImplementation 'com.redis:testcontainers-redis:2.2.2'`
  - **Redisson starter 는 추가하지 않는다.**
- [ ] `live-class/src/main/java/com/example/liveclass/config/RedisConfig.java` 작성.
  - 첫 줄 한국어 주석 `// Lettuce ConnectionFactory + StringRedisTemplate + RedisCacheManager 를 등록하는 Redis 인프라 설정`.
  - `@Bean LettuceConnectionFactory redisConnectionFactory()` — `commandTimeout = Duration.ofMillis(200)`, `spring.data.redis.host/port/password` 프로퍼티 바인딩 (`@Value` 또는 `RedisProperties` autowiring).
  - `@Bean RedisTemplate<String, String> stringRedisTemplate(RedisConnectionFactory cf)` — key/value 모두 `StringRedisSerializer` 사용.
  - `@Bean RedisCacheManager cacheManager(RedisConnectionFactory cf)` — `RedisCacheConfiguration` 두 개를 `Map<String, RedisCacheConfiguration>` 로 구성. `class:detail` TTL 300s, `class:enrolledCount` TTL 60s.
- [ ] `live-class/src/main/java/com/example/liveclass/config/LuaScriptConfig.java` 작성.
  - 첫 줄 한국어 주석 `// Lua atomic script 3개를 classpath:lua/*.lua 에서 로드해 RedisScript 빈으로 등록`.
  - `@Bean DefaultRedisScript<String> enrollmentApplyScript()` — `setLocation(new ClassPathResource("lua/enrollment_apply.lua"))`, `setResultType(String.class)`.
  - `@Bean DefaultRedisScript<List> enrollmentCancelPromoteScript()` — `setLocation(new ClassPathResource("lua/enrollment_cancel_promote.lua"))`, `setResultType(List.class)`.
  - `@Bean DefaultRedisScript<Long> enrollmentCompensateScript()` — `setLocation(new ClassPathResource("lua/enrollment_compensate.lua"))`, `setResultType(Long.class)`.
- [ ] `live-class/src/main/resources/lua/enrollment_apply.lua` 파일 생성 (placeholder).
  - 첫 줄 한국어 주석 `-- enrollment_apply.lua — Lua 1차 게이트 (정원·중복 검사 + ZSET ADD). task 09 worker 가 본문 작성 예정.`
  - 본문은 단일 라인 `return 'PENDING'` placeholder.
- [ ] `live-class/src/main/resources/lua/enrollment_cancel_promote.lua` 파일 생성 (placeholder).
  - 첫 줄 한국어 주석 `-- enrollment_cancel_promote.lua — cancel + waitlist 승격 원자 ZSET swap. task 10 worker 가 본문 작성 예정.`
  - 본문 `return nil`.
- [ ] `live-class/src/main/resources/lua/enrollment_compensate.lua` 파일 생성 (placeholder).
  - 첫 줄 한국어 주석 `-- enrollment_compensate.lua — Lua 성공 후 DB 실패 시 ZSET 갱신을 되돌리는 보상 스크립트. task 09 worker 가 본문 작성 예정.`
  - 본문 `return 0`.
- [ ] `application.yaml` 에 `spring.cache.type: redis` 추가, `spring.cache.cache-names` 에 `class:detail`, `class:enrolledCount` 명시.
- [ ] `LiveClassApplication.java` 에 `@EnableCaching` 추가.
- [ ] `live-class/src/test/java/com/example/liveclass/support/RedisContainerExtension.java` 작성 — JUnit5 `@RegisterExtension` 으로 Testcontainers Redis 컨테이너 + `@DynamicPropertySource` 로 `spring.data.redis.host/port` 주입.
- [ ] (Verify) `live-class/src/test/java/com/example/liveclass/config/RedisConfigTest.java` — `@SpringBootTest` 로 컨텍스트 로드 후 `RedisTemplate`, `CacheManager`, `enrollmentApplyScript`, `enrollmentCancelPromoteScript`, `enrollmentCompensateScript` 5 개 빈이 모두 주입되는지 검증. RedissonClient 빈은 존재하지 **않음** 을 확인 (`assertThatThrownBy(() -> context.getBean(...))`).
- [ ] (Verify) `CacheManager` 가 `class:detail`, `class:enrolledCount` 두 캐시 이름을 반환하는지 검증.
- [ ] (Verify) 각 RedisScript 빈의 `getScriptAsString()` 이 placeholder 본문을 반환하는지 검증 (스크립트 로딩 자체 확인).
