<!-- 에이전트 실행 결과 보고서 — task 05 Class repository/service/controller + status mirror listener -->
---
status: done
owner: blueprint-executor-worker
created: 2026-05-12
updated: 2026-05-12
pr: (filled after gh pr create)
---

# Report: Task 05 — Class Repository + ApplicationService + Controller + Status Mirror Listener

## Input Summary

- 태스크 파일: `plan/before/05_Logic_Implementer_Class_Repository_Service_Controller.md`
- 의존 산출물:
  - task 04 — `Class` entity + 4 VOs + `ClassStatus` + `AccessDeniedDomainException` + `IllegalStateTransitionException` (`domain/clazz/**`).
  - task 03 — `UserApplicationService.getById(UUID)` + `User.isCreator()`.
  - task 02 — Spring Boot 자동 등록 `StringRedisTemplate` (Lettuce, `spring.data.redis.*` 프로퍼티 기반).
  - task 01 — `MockUserFilter` + `CurrentUserArgumentResolver` + `GlobalExceptionHandler` (`DomainException` → ProblemDetail, `OptimisticLockingFailureException` → 409).
- DoD 핵심: 4 endpoint 동작, `findByIdForUpdate(UUID)` 비관 잠금, Creator 역할 검증, AFTER_COMMIT Redis mirror 갱신, `OptimisticLockingFailureException` 1회 재시도 → 409 변환.

## What Was Done

| File | Action | Summary |
|------|--------|---------|
| `domain/clazz/ClassRepository.java` | created | `JpaRepository<Class, UUID>` + `findByIdForUpdate` (`@Lock(PESSIMISTIC_WRITE)` + JPQL) + `findByStatusOrderByCreatedAtDesc` |
| `domain/clazz/ClassNotFoundException.java` | created | DomainException, HTTP 404 — `transitionStatus`/`getById` 에서 throw |
| `domain/clazz/ConcurrentClassUpdateException.java` | created | DomainException, HTTP 409 — `OptimisticLockingFailureException` 재시도 1회 실패 시 변환 |
| `domain/clazz/event/ClassOpenedEvent.java` | created | `record(ClassId, UserId creatorId, Instant occurredAt)` |
| `domain/clazz/event/ClassClosedEvent.java` | created | `record(ClassId, UserId creatorId, Instant occurredAt)` |
| `application/clazz/ClassApplicationService.java` | created | `@Service`. `createDraft / transitionStatus / getById / listOpenClasses`. Creator 역할 검증, 수동 retry-once, AFTER_COMMIT 이벤트 발행은 service 가 publishEvent |
| `application/clazz/ClassStatusMirrorListener.java` | created | `@Component` + `@TransactionalEventListener(AFTER_COMMIT)` 두 메서드 → `class:status:{id}` String SET TTL=5m |
| `web/clazz/ClassController.java` | created | 4 endpoint: POST(201) / PATCH {id}/status(200,409) / GET {id}(200) / GET (paged 200) |
| `web/clazz/dto/CreateClassRequest.java` | created | record + Bean Validation (`@NotBlank`, `@Size`, `@PositiveOrZero`, `@Min`, `@FutureOrPresent`) |
| `web/clazz/dto/ChangeStatusRequest.java` | created | record + `@NotNull ClassStatus target` |
| `web/clazz/dto/ClassResponse.java` | created | record + `from(Class)` 정적 팩토리 |
| `test/web/clazz/ClassControllerTest.java` | created | MockMvc `standaloneSetup` 8 케이스 (생성/검증실패/OPEN/IllegalTransition 409/ConcurrentUpdate 409/GET/404/페이지) |
| `test/application/clazz/ClassStatusMirrorListenerTest.java` | created | `@SpringBootTest(classes=...)` + Testcontainers Redis. `TransactionTemplate` 안에서 이벤트 발행 → AFTER_COMMIT → Redis mirror 검증 |
| `test/application/clazz/ClassStatusMirrorListenerTestConfig.java` | created | 경량 컨텍스트 — Redis auto-config + 임베디드 H2 DataSource + `DataSourceTransactionManager` + listener bean |
| `plan/before/05_Logic_Implementer_Class_Repository_Service_Controller.md` | modified | 9개 항목 전체 `- [ ]` → `- [x]` |

## Rationale & Tradeoffs

(a) **`CreateClassRequest` DTO 를 application service 시그니처에 그대로 전달** — work-order Implementation hint §3에서 `CreateClassCommand` 는 "권장 표현이지 강제 아님"이라고 명시. 별도 command record 신설을 피해 매핑 코드를 한 곳으로 모았다 (service 메서드 안에서 `Money.of / Capacity.of / ClassPeriod.of` 호출).

(b) **`@Retryable` 대신 수동 try-catch (loop)** — `spring-retry` 가 의존성에 없음. work-order hint 본문에서도 수동 try-catch 패턴을 권장. `for (int attempt = 0; attempt < 2; attempt++)` 안에서 `OptimisticLockingFailureException` 만 잡고, 두 번째 시도에서도 실패하면 `ConcurrentClassUpdateException` 으로 변환해 GlobalExceptionHandler 의 `DomainException` 핸들러 경로로 409 응답.

(c) **`AFTER_COMMIT` 이벤트 발행 책임 — service 가 담당, 도메인은 순수 유지** — work-order 명시(“`Class.open()/close()` 메서드는 직접 이벤트 발행하지 않음”). task 04 의 `Class.open()/close()` 시그니처를 건드리지 않고, service 의 `transitionStatus` 가 `classRepository.save(c)` 직후 `ApplicationEventPublisher.publishEvent(...)` 를 호출한다. `ClassStatusMirrorListener` 는 `@TransactionalEventListener(phase=AFTER_COMMIT)` 로만 Redis 갱신 — DB 커밋 성공 후에만 mirror 가 갱신됨.

(d) **Creator 역할 검증 위치** — `ClassApplicationService.requireCreator(UUID)` 는 `UserApplicationService.getById` → `user.isCreator()` 를 본다. CLASSMATE 면 `AccessDeniedDomainException` (HTTP 403). `createDraft` + `transitionStatus` 진입 직후 한 번 호출. `Class.checkCreator(UserId)` 는 entity 가 자체 보유한 creator-id 일치 검증으로 그대로 둠 (이미 task 04 에서 구현).

(e) **`ClassStatusMirrorListenerTest` 의 경량 컨텍스트** — `@SpringBootTest(classes=ClassStatusMirrorListenerTestConfig.class)` 로 메인 `LiveClassApplication` 자동 컨텍스트를 우회. 의도적으로 (i) Redis auto-config 만 활성화, (ii) `DataSourceTransactionManager` 를 임베디드 H2 위에 띄워 `TransactionTemplate.executeWithoutResult` 안에서 `AFTER_COMMIT` 단계가 정상적으로 실행되도록 함. JPA/Postgres/Quartz 자동 설정을 모두 우회해 테스트 컨텍스트가 가볍고, 한글 경로 + 메인 컴포넌트 스캔으로 인한 실패를 피한다.

(f) **로컬 테스트 실행 — BLOCKED (한글 경로 + Java 21).** `compileJava` + `compileTestJava` 성공, `bootJar`/`build -x test` 성공. `./gradlew test --tests ...` 는 CLAUDE.md / `plan/nested-launching-ripple.md` 에 사전 보고된 `ClassNotFoundException` (test runner classpath URL decoding 이슈) 으로 실패. 생성된 `.class` 파일은 `build/classes/java/test/com/example/liveclass/web/clazz/ClassControllerTest.class` + `application/clazz/ClassStatusMirrorListenerTest.class` 로 정상 존재. CI Linux 에서 통과 검증 예정.

## Follow-ups

- [ ] CI Linux 환경에서 `ClassControllerTest` + `ClassStatusMirrorListenerTest` 통과 검증.
- [ ] task 08 (Enrollment Repository): `enrollment_apply.lua` 의 `class:status:{classId}` mirror miss 시 application service 가 DB fallback + mirror 채우고 Lua 재시도 — 현재 task 에서 mirror 갱신 path 는 완성됐고, miss 시 fill-back path 는 enrollment service 측에서 구현.
- [ ] task 16 (Quartz AutoClose): `ClassAutoCloseJob` 이 본 `classApplicationService.transitionStatus(classId, SYSTEM_USER_ID, CLOSED)` 를 호출하려면 시스템 사용자 ID 우회 또는 별도 `Class.autoClose()` 가 필요 — work-order ARCHITECTURE §8.2 에 명시되어 task 16 에서 확정.
- [ ] (선택) `ConcurrentClassUpdateException` 케이스의 통합 테스트 — 두 동시 트랜잭션 시뮬레이션은 task 06 (Quality Guardian: Class repository integration tests) 범위.
