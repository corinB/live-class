<!-- 에이전트 실행 결과 보고서 — Redis 인프라 + Lua RedisScript 빈 등록 (Cache 제거 결정 반영) -->
---
status: done
owner: blueprint-executor-worker + Maestro fix-up
created: 2026-05-12
updated: 2026-05-12
---

# Report: Task 02 — Redis Infra + LuaScriptConfig (Cache 제거)

## Input Summary

- 태스크 파일: `plan/before/02_Infra_Operator_Redis_And_Cache_Config.md` (Pre-flight 5 결정으로 Cache 제거된 갱신본 기준)
- DOCS.md 기반 주요 도메인 개념: Enrollment ZSET 미러 기반 race 결정, Lua-first 동시성 (apply / cancel_promote / compensate 3-script)
- ARCHITECTURE.md 기반 주요 결정:
  - §4.4 — Lua atomic gate, ZSET 미러 운영
  - §5 — Mirror vs Cache 분리 (Pre-flight 5 결정으로 Cache 계층 out-of-scope, Mirror 만 남음)
  - §6.1/6.3 — apply/cancel/compensate 시퀀스
  - §7.1 — Lua 호출 실패 시 503 fail-closed (commandTimeout 200ms)
  - Pre-flight 4 — Redisson 미추가
  - Pre-flight 5 — Spring Cache (`@Cacheable` + `RedisCacheManager` + `@EnableCaching`) 미사용

## What Was Done

| File | Action | Summary |
|------|--------|---------|
| `live-class/build.gradle` | modified | Testcontainers postgresql + junit-jupiter + com.redis:testcontainers-redis:2.2.2. Spring Boot 4 BOM 좌표 자동 조정. Redisson starter 미추가. |
| `live-class/src/main/java/com/example/liveclass/config/LuaScriptConfig.java` | created | 3개 `DefaultRedisScript` 빈 — `enrollmentApplyScript` / `enrollmentCancelPromoteScript` / `enrollmentCompensateScript` 를 classpath `lua/*.lua` placeholder 에서 로드. |
| `live-class/src/main/resources/lua/enrollment_apply.lua` | created | Placeholder `return 'PENDING'`. 본문은 task 09 에서. |
| `live-class/src/main/resources/lua/enrollment_cancel_promote.lua` | created | Placeholder `return nil`. 본문은 task 10 에서. |
| `live-class/src/main/resources/lua/enrollment_compensate.lua` | created | Placeholder `return 0`. 본문은 task 09 에서. |
| `live-class/src/main/resources/application.yaml` | modified | `spring.data.redis.timeout` 200ms 로 단축 (fail-closed). **`spring.cache.*` 키 추가하지 않음.** |
| `live-class/src/test/java/com/example/liveclass/support/RedisContainerExtension.java` | created | JUnit5 `BeforeAllCallback` 으로 Testcontainers Redis 시작 + host/port 노출. |
| `live-class/src/test/java/com/example/liveclass/config/RedisConfigTest.java` | created | `@SpringBootTest` 로 4개 빈 주입 검증 (`RedisTemplate` + 3 RedisScript) + `liveClassCacheManager` 부재 검증 + `Redisson` 부재 검증 + 각 RedisScript placeholder 본문 검증. |
| `live-class/src/main/java/com/example/liveclass/config/RedisConfig.java` | **created → deleted (Maestro fix-up)** | 초기 워커는 `LettuceConnectionFactory` + `StringRedisTemplate` + `RedisCacheManager` 3개 빈을 정의했지만 Spring Boot 4 의 `DataRedisAutoConfiguration` 과 같은 이름으로 `BeanDefinitionOverrideException` 발생. Pre-flight 5 결정 (Cache 제거) 적용 후 파일 자체 삭제 — Spring Boot default 빈을 그대로 사용. |
| `live-class/src/main/java/com/example/liveclass/LiveClassApplication.java` | **modified (Maestro fix-up)** | 초기 워커가 추가한 `@EnableCaching` 제거. Spring Cache 미사용. |
| `plan/before/02_*.md` | modified | 체크리스트 모두 `[x]` flip + Pre-flight 5 결정 반영 (Cache 관련 항목 제거, RedisConfig 미생성, `@EnableCaching` 미추가 명시). |

PR: `feat(task-02): add Redis infra and Lua RedisScript beans` (#7, after fix-up commits)

## Rationale & Tradeoffs

- **LettuceConnectionFactory + StringRedisTemplate 를 직접 정의하지 않음 (Maestro fix)** — Spring Boot 4 의 `DataRedisAutoConfiguration` 이 이미 동일 이름의 빈을 등록. 우리 빈과 충돌해 `BeanDefinitionOverrideException`. Boot default 가 우리 요구를 모두 충족하므로 (`spring.data.redis.timeout=200ms` 로 commandTimeout 단축) 별도 정의 불요.
- **`@EnableCaching` + `RedisCacheManager` 제거 (Pre-flight 5)** — ZSET 미러가 결정 경로 캐시 역할을 이미 하므로 별도 metadata cache 의 ROI 가 낮음. 채용 과제 트래픽에서 perf 차이 무시 가능. 면접에서 "production RPS·hit-rate·TTL 측정 후 도입" 으로 설명.
- **Lua RedisScript 빈 등록 (LuaScriptConfig 유지)** — task 09/10 의 Lua-first 동시성 전략 의 entry point. placeholder .lua 파일이 함께 있어 빈 로딩 자체는 task 02 단계에서 검증.
- **Redisson 미추가 (Pre-flight 4)** — cache stampede single-flight 용도가 단일 EC2 환경에서 ROI 낮음.
- **Spring Boot 4 Testcontainers 아티팩트 좌표 변경** — `org.testcontainers:postgresql` → `org.testcontainers:testcontainers-postgresql`, `:junit-jupiter` → `:testcontainers-junit-jupiter`. BOM 에서 자동 해결.

## Follow-ups

- [ ] task 05 (Class service) — `@Cacheable` 사용 안 함. 매 조회 DB 직접 (Pre-flight 5).
- [ ] task 09 (apply worker) — `enrollment_apply.lua` + `enrollment_compensate.lua` 본문 작성.
- [ ] task 10 (cancel/promote) — `enrollment_cancel_promote.lua` 본문 작성.
- [x] ARCHITECTURE.md §5 의 Cache 섹션을 별도 chore PR 로 out-of-scope 명시 — **resolved**: Pre-flight 5 chore PR 에서 §5 전체 재작성 (Mirror only), §5.1·§5.2·§5.3·§5.4·§5.5 본문 모두 Cache 미사용으로 일관.
- [x] plan/before/05·09·10·13 의 cache 관련 항목 정리 — **resolved**: 같은 Pre-flight 5 chore PR 에서 `@Cacheable`/`CacheInvalidator`/`EnrollmentCacheInvalidator` 항목 모두 제거 또는 `ClassStatusMirrorListener` (직접 Redis SET) 로 대체.
- [ ] CI 검증 — Testcontainers Redis Docker 환경에서 `RedisConfigTest` 통과 확인.
