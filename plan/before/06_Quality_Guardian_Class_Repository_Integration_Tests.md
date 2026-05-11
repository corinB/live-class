# Class Repository 통합 테스트 + Controller 슬라이스 테스트

- **Assignee:** The Quality Guardian
- **Dependencies:** 05_Logic_Implementer_Class_Repository_Service_Controller.md
- **Definition of Done (DoD):**
  - `ClassRepository.findByIdForUpdate()`가 실제로 PostgreSQL에서 `SELECT ... FOR UPDATE`를 발행하는지 Testcontainers + Hibernate SQL 로그로 검증된다.
  - 두 트랜잭션이 같은 `Class` row를 `findByIdForUpdate`로 잡으면 두 번째가 첫 번째 커밋 또는 롤백까지 대기하는지 동시성 테스트로 확인된다.
  - `Class` 엔티티의 `@Version` 컬럼이 UPDATE 시 자동 증가하고, stale version으로 UPDATE 시 `OptimisticLockingFailureException`이 발생한다.
  - `ClassController` 슬라이스 테스트가 인증 헤더, 권한(Creator만), 입력 검증 3축 시나리오를 모두 커버한다.

## Action Items (Checklist)

- [ ] `src/test/java/com/example/liveclass/support/PostgresTestContainer.java` — Testcontainers `PostgreSQLContainer<?>` static 인스턴스, `@DynamicPropertySource` 헬퍼.
- [ ] `src/test/java/com/example/liveclass/support/IntegrationTest.java` — 메타 어노테이션 (`@SpringBootTest @ActiveProfiles("test") @Testcontainers`).
- [ ] `src/test/resources/application-test.yaml` — `spring.jpa.hibernate.ddl-auto: create-drop`, `liveclass.redisson.enabled: false`, `spring.cache.type: none` (캐시 테스트가 아닌 경우).
- [ ] `domain/clazz/ClassRepositoryIntegrationTest.java` — `@DataJpaTest` + Testcontainers.
  - `findByIdForUpdate()` 호출 시 `show_sql=true`로 `for update` SQL 발행 확인 (`StatementInspector` 사용).
  - `save()` 후 `findById().get().getVersion() == 0`, 한 번 더 save 후 `version == 1`.
- [ ] `domain/clazz/ClassRepositoryConcurrencyTest.java` — `@SpringBootTest` + 두 개의 `TransactionTemplate`.
  - Thread A: `findByIdForUpdate` + `Thread.sleep(500)` + commit.
  - Thread B: `findByIdForUpdate` 호출 → A가 commit할 때까지 블로킹되는지 시간 측정으로 검증 (`assertThat(elapsedMs).isGreaterThanOrEqualTo(400)`).
- [ ] `domain/clazz/ClassOptimisticLockTest.java` — stale 엔티티 인스턴스 2개를 만들어 동시에 save → 하나는 성공, 다른 하나는 `OptimisticLockingFailureException`.
- [ ] (Verify) `web/clazz/ClassControllerSliceTest.java` — `@WebMvcTest`.
  - 헤더 누락 → 401.
  - CLASSMATE 헤더로 `POST /api/classes` → 403.
  - 잘못된 body (price 음수, capacity 0) → 400 ProblemDetail.
  - 정상 body + CREATOR 헤더 → 201 + `Location` 헤더.
- [ ] (Verify) `application/clazz/ClassApplicationServiceTransitionTest.java` — `@IntegrationTest`로 DRAFT→OPEN→CLOSED 정상 흐름과 CLOSED→OPEN 실패(`IllegalStateTransitionException` → 409) 검증.
