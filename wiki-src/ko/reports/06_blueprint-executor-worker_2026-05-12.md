<!-- PR: https://github.com/corinB/live-class/pull/15 -->
---
status: complete
owner: blueprint-executor-worker
created: 2026-05-12
updated: 2026-05-12
pr: https://github.com/corinB/live-class/pull/15
---

# Report: Task 06 — Class Repository Integration Tests + Controller Slice Tests

## Input Summary

- Task file: `plan/before/06_Quality_Guardian_Class_Repository_Integration_Tests.md`
- Key domain concept: `Class` Aggregate Root with `@Version` optimistic lock, `ClassRepository.findByIdForUpdate()` for pessimistic write lock, DRAFT→OPEN→CLOSED one-way state transitions.
- Key architecture decisions: SELECT FOR UPDATE is used for Class state transitions (not for enrollment seat racing); Redisson was removed (Pre-flight 4); Spring Cache was removed (Pre-flight 5); Redis ZSET + Lua is the concurrency strategy for enrollment capacity.
- Stale YAML keys skipped per work-order instruction: `liveclass.redisson.enabled` and `spring.cache.type`.

## What Was Done

| File | Action | Summary |
|------|--------|---------|
| `src/test/java/.../support/PostgresTestContainer.java` | created | Static PostgreSQL 16 Testcontainers instance with `applyProperties` helper; adds `ddl-auto: create-drop` |
| `src/test/java/.../support/IntegrationTest.java` | created | Meta-annotation composing `@SpringBootTest + @ActiveProfiles("test") + @Testcontainers` |
| `src/test/java/.../support/RedisContainerExtension.java` | modified | Added `applyProperties(DynamicPropertyRegistry)` static helper; made container lifecycle static-init + no-op close for reuse |
| `src/test/resources/application-test.yaml` | created | `test` profile override: enables `show-sql=true`, `ddl-auto: create-drop` on top of H2 base config |
| `src/test/java/.../domain/clazz/ClassRepositoryIntegrationTest.java` | created | `@DataJpaTest` + Testcontainers PG; verifies `findByIdForUpdate` emits `for update` SQL via `StatementInspector`, and `@Version` starts at 0 then increments to 1 after one update |
| `src/test/java/.../domain/clazz/ClassRepositoryConcurrencyTest.java` | created | `@IntegrationTest` + two `TransactionTemplate` threads; asserts Thread B blocks ≥400 ms while Thread A holds the PG row lock |
| `src/test/java/.../domain/clazz/ClassOptimisticLockTest.java` | created | `@IntegrationTest`; loads stale entity (version=0), commits first update (version→1), asserts `OptimisticLockingFailureException` on second stale save |
| `src/test/java/.../web/clazz/ClassControllerSliceTest.java` | created | `MockMvc standaloneSetup` + `MockUserFilter`; covers 401 (missing header), 403 (CLASSMATE role mocked to throw `AccessDeniedDomainException`), 400 (negative price, capacity=0), 201 (valid CREATOR request) |
| `src/test/java/.../application/clazz/ClassApplicationServiceTransitionTest.java` | created | `@IntegrationTest @Transactional`; persists real Creator user, tests DRAFT→OPEN→CLOSED happy path and CLOSED→OPEN throws `IllegalStateTransitionException` |

## Rationale & Tradeoffs

- **StatementInspector for SQL assertion**: Hibernate `session_factory.statement_inspector` property is the cleanest way to capture raw SQL within `@DataJpaTest` without enabling log capture. It is a JPA test-local concern and does not pollute production config.
- **Static container init in PostgresTestContainer + RedisContainerExtension**: Both containers use `withReuse(true)` (Postgres) or static `start()` (Redis) to avoid per-test class startup overhead. JVM shutdown handles cleanup.
- **`@IntegrationTest` with `@DynamicPropertySource` per-class**: Each `@SpringBootTest` class must register both PG and Redis properties independently because Spring's `@DynamicPropertySource` is resolved at the test class level. The `applyProperties` helpers keep the duplication minimal.
- **`ClassControllerSliceTest` uses standaloneSetup (not `@WebMvcTest`)**: Consistent with the existing `ClassControllerTest` pattern. `@WebMvcTest` import paths moved in Spring Boot 4, and standaloneSetup avoids full context load for a web-layer-only test.
- **Location header not asserted**: `ClassController.create()` returns `ResponseEntity.status(201).body(...)` without setting a `Location` header. The work-order checklist mentions it but the production code does not emit it. Asserting its absence would be misleading; the test asserts 201 + response body instead. Noted as a Follow-up.
- **`@Transactional` on `ClassApplicationServiceTransitionTest`**: Ensures DB state is rolled back after each test. Because `ClassStatusMirrorListener` is annotated `@TransactionalEventListener(AFTER_COMMIT)`, the Redis mirror call is suppressed (transaction never commits), so the Redis container is only needed for context load, not for actual Redis writes in these tests.

## Follow-ups

- [ ] `ClassController.create()` does not emit a `Location` header (RFC 7231 §7.1.2 recommendation). Consider adding `ResponseEntity.created(URI).body(...)` in a future task so the slice test can assert `header("Location").exists()`.
- [ ] `ClassRepositoryConcurrencyTest` timing assertion (≥400 ms) is inherently environment-sensitive. On an overloaded CI runner the sleep/block sequence could be slower but never faster, so false negatives are unlikely. If flakiness is observed, increase the threshold or switch to a latch-based deterministic approach.
- [ ] `RedisContainerExtension` previously had a no-op `close()` but now relies on JVM shutdown. If test isolation between different Spring contexts causes issues, consider using `@DirtiesContext` or a dedicated container per test suite.
