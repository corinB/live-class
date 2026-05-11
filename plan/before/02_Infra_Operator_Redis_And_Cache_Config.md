# Redis 캐시 매니저, RedisTemplate, Redisson 클라이언트 빈 구성

- **Assignee:** The Infra Operator
- **Dependencies:** 01_Infra_Operator_MockUserFilter_And_ExceptionHandler.md
- **Definition of Done (DoD):**
  - `RedisConnectionFactory`(Lettuce 기반)와 `RedisTemplate<String, String>`이 빈으로 등록되고 `spring.data.redis.*` 프로퍼티로 동작한다.
  - `CacheManager`(Spring Cache `RedisCacheManager`)가 등록되고 `class:detail` 캐시는 TTL 300초, `class:enrolledCount` 캐시는 TTL 60초로 분리된다 (ARCHITECTURE §5.2).
  - `Redisson` 클라이언트 빈이 등록되고 단일 노드 모드(`redisson://${REDIS_HOST}:${REDIS_PORT}`)로 부팅에 성공한다 (향후 분산 락 보조용, ARCHITECTURE §4.4).
  - 컨테이너 부팅 시 Redis 연결 실패해도 애플리케이션이 즉시 죽지 않고 `connection timeout 200ms` + circuit-breaker로 fallback 가능하다 (ARCHITECTURE §7.1).
  - 통합 테스트 환경에서 Testcontainers Redis 컨테이너로 캐시 빈 컨텍스트가 로드된다.

## Action Items (Checklist)

- [ ] `build.gradle`에 `implementation 'org.redisson:redisson-spring-boot-starter:3.34.1'` 의존성 추가.
- [ ] `build.gradle`에 `testImplementation 'org.testcontainers:postgresql'`, `testImplementation 'org.testcontainers:junit-jupiter'`, `testImplementation 'com.redis:testcontainers-redis:2.2.2'` 추가.
- [ ] `live-class/src/main/java/com/example/liveclass/config/RedisConfig.java` 작성.
  - `@Bean RedisTemplate<String, String> stringRedisTemplate(RedisConnectionFactory cf)` — key/value 모두 `StringRedisSerializer` 사용.
  - `@Bean RedisCacheManager cacheManager(RedisConnectionFactory cf)` — `RedisCacheConfiguration` 두 개를 `Map<String, RedisCacheConfiguration>`로 구성: `class:detail` TTL 300s, `class:enrolledCount` TTL 60s.
  - `LettuceConnectionFactory` 설정에서 `commandTimeout = Duration.ofMillis(200)`.
- [ ] `live-class/src/main/java/com/example/liveclass/config/RedissonConfig.java` 작성.
  - `@Bean(destroyMethod = "shutdown") RedissonClient redissonClient()` — `Config().useSingleServer().setAddress("redis://${host}:${port}").setConnectTimeout(200).setTimeout(200)`.
  - `@ConditionalOnProperty(prefix="liveclass.redisson", name="enabled", havingValue="true", matchIfMissing=true)` 적용.
- [ ] `application.yaml`에 `spring.cache.type: redis` 추가, `spring.cache.cache-names`에 `class:detail`, `class:enrolledCount` 명시.
- [ ] `application.yaml`에 `liveclass.redisson.enabled: ${REDISSON_ENABLED:true}` 추가하여 테스트 환경에서 끌 수 있게 함.
- [ ] `LiveClassApplication.java`에 `@EnableCaching` 추가.
- [ ] `live-class/src/test/java/com/example/liveclass/support/RedisContainerExtension.java` 작성 — JUnit5 `@RegisterExtension`로 Testcontainers Redis 컨테이너 + `@DynamicPropertySource`로 `spring.data.redis.host/port` 주입.
- [ ] (Verify) `live-class/src/test/java/com/example/liveclass/config/RedisConfigTest.java` — `@SpringBootTest`로 컨텍스트 로드 후 `RedisTemplate`, `CacheManager`, `RedissonClient` 세 빈이 모두 주입되는지 `assertThat(beans).isNotNull()` 검증.
- [ ] (Verify) `CacheManager`가 `class:detail`, `class:enrolledCount` 두 캐시 이름을 반환하는지 검증.
