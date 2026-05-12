<!-- 에이전트 실행 결과 보고서 템플릿 — 입력 요약·수행 내용·근거·후속 작업을 300~500 단어로 기술한다 -->
---
status: done
owner: blueprint-executor-worker
created: 2026-05-12
updated: 2026-05-12
---

# Report: Task 02 — Redis Infra + Cache + LuaScriptConfig

## Input Summary

- 태스크 파일: `plan/before/02_Infra_Operator_Redis_And_Cache_Config.md`
- DOCS.md 기반 주요 도메인 개념: Enrollment ZSET 기반 원자 처리, Lua-first 동시성 전략 (apply / cancel_promote / compensate 3-script 패턴)
- ARCHITECTURE.md 기반 주요 결정:
  - §4.4 — Lua script atomicity guarantee for enrollment gate
  - §5.2 — `class:detail` TTL 300s, `class:enrolledCount` TTL 60s 분리 캐시
  - §5.3 — RedisScript bean registry as execution entry point
  - §6.1/6.3 — apply / compensate scripts declared here, bodies filled in task 09/10
  - §7.1 — commandTimeout 200ms, fail-closed on Lua call failure (503 in task 09/10)
  - Pre-flight 4 — Redisson NOT added; cache stampede handled via JVM-internal single-flight in task 05

## What Was Done

| File | Action | Summary |
|------|--------|---------|
| `live-class/build.gradle` | modified | Added testcontainers-postgresql, testcontainers-junit-jupiter (Spring Boot 4 renamed artifacts), com.redis:testcontainers-redis:2.2.2 |
| `live-class/src/main/java/com/example/liveclass/config/RedisConfig.java` | created | LettuceConnectionFactory (200ms commandTimeout) + StringRedisTemplate + RedisCacheManager with class:detail (TTL 300s) and class:enrolledCount (TTL 60s) |
| `live-class/src/main/java/com/example/liveclass/config/LuaScriptConfig.java` | created | 3 DefaultRedisScript beans loading from classpath lua/*.lua placeholder files |
| `live-class/src/main/resources/lua/enrollment_apply.lua` | created | Placeholder — returns 'PENDING'; actual body filled by task 09 |
| `live-class/src/main/resources/lua/enrollment_cancel_promote.lua` | created | Placeholder — returns nil; actual body filled by task 10 |
| `live-class/src/main/resources/lua/enrollment_compensate.lua` | created | Placeholder — returns 0; actual body filled by task 09 |
| `live-class/src/main/resources/application.yaml` | modified | Added spring.cache.type: redis and spring.cache.cache-names |
| `live-class/src/main/java/com/example/liveclass/LiveClassApplication.java` | modified | Added @EnableCaching annotation |
| `live-class/src/test/java/com/example/liveclass/support/RedisContainerExtension.java` | created | JUnit5 BeforeAllCallback extension that starts RedisContainer and exposes host/port for @DynamicPropertySource |
| `live-class/src/test/java/com/example/liveclass/config/RedisConfigTest.java` | created | @SpringBootTest integration test verifying 5 beans (RedisTemplate, CacheManager, 3 RedisScript), 2 cache names, Lua placeholder content, and absence of Redisson beans |

PR: feat(task-02): add Redis infra, cache manager, and Lua RedisScript beans

## Rationale & Tradeoffs

- **LettuceConnectionFactory vs JedisConnectionFactory**: Lettuce는 Netty 기반 비동기 드라이버로 Spring Data Redis default이며 Spring Boot auto-config과 일관성을 유지. Jedis는 blocking + deprecated 방향이라 선택하지 않음.
- **StringRedisSerializer vs GenericJackson2JsonRedisSerializer for Redis values**: task 02 요구사항은 Lua 스크립트 실행용 String 값 처리만 필요. JSON 직렬화는 `class:detail` 캐시가 실제 객체를 저장하는 task 05/08 단계에서 별도 `ObjectMapper` 공급과 함께 결정될 사항. 힌트 §6에서 명시적으로 언급됨.
- **Redisson 미추가**: Pre-flight 4 결정에 따라 의도적으로 제외. 단일 JVM 가정 하에 cache stampede는 task 05에서 `ConcurrentHashMap.computeIfAbsent` 또는 `synchronized` 블록으로 처리.
- **Spring Boot 4 Testcontainers 아티팩트 이름 변경**: Spring Boot 4 BOM에서 `org.testcontainers:postgresql` → `org.testcontainers:testcontainers-postgresql`, `org.testcontainers:junit-jupiter` → `org.testcontainers:testcontainers-junit-jupiter`로 변경됨. 체크리스트의 이전 아티팩트 이름은 BOM에서 해결되지 않아 실제 이름으로 수정함.
- **Local test ClassNotFoundException**: Korean 경로 + Java 21 조합으로 인한 알려진 이슈 (plan/nested-launching-ripple.md 문서화). 컴파일과 bootJar 빌드는 정상. CI(Linux)에서 Testcontainers Docker 환경으로 검증 예정.

## Follow-ups

- [ ] task 09 (apply worker): `enrollment_apply.lua` 본문 구현 — ZSET 정원·중복 검사 + ADD 원자 처리
- [ ] task 09 (apply worker): `enrollment_compensate.lua` 본문 구현 — DB 실패 시 ZSET 롤백 보상 스크립트
- [ ] task 10 (cancel worker): `enrollment_cancel_promote.lua` 본문 구현 — cancel + waitlist 승격 원자 ZSET swap
- [ ] task 05 (cache stampede): `@Cacheable` 사용 시 JVM-internal single-flight 구현 (CacheManager 빈 재사용)
- [ ] task 05/08: `class:detail` 캐시가 객체를 저장할 경우 JSON 직렬화 전략 결정 및 ObjectMapper 빈 공급
- [ ] CI 검증: Testcontainers Redis Docker 환경에서 `RedisConfigTest` 전체 통과 확인
