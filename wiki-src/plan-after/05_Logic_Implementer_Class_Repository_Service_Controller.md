# Class Repository, ApplicationService, Controller, 캐시 연동

- **Assignee:** The Logic Implementer
- **Dependencies:** 04_Logic_Implementer_Class_Domain_Entity.md, 02_Infra_Operator_Redis_And_Cache_Config.md
- **Definition of Done (DoD):**
  - `ClassRepository`가 JPA로 동작하고 `findByIdForUpdate(UUID)` 메서드가 `@Lock(LockModeType.PESSIMISTIC_WRITE)`로 `SELECT FOR UPDATE`를 발행한다 (ARCHITECTURE §4.3 §2.1).
  - `POST /api/classes`, `PATCH /api/classes/{id}/status`(DRAFT→OPEN→CLOSED), `GET /api/classes/{id}`, `GET /api/classes` 4개 endpoint가 동작한다.
  - `GET /api/classes/{id}` 응답은 매 호출 DB 직접 조회. **Spring Cache 미사용 (Pre-flight 5 결정)** — ZSET mirror 가 결정 경로 캐시 역할을 하므로 별도 metadata cache 도입 안 함. `Class.open()/close()` 성공 후 `AFTER_COMMIT` 단계에서 `class:status:{classId}` Redis String mirror 만 `redisTemplate.opsForValue().set` 으로 갱신 (Lua 가 GET 으로 읽음).
  - Creator만 `POST/PATCH` 호출 가능. `X-User-Id`의 사용자가 CLASSMATE 역할이면 403.
  - `OptimisticLockingFailureException` 발생 시 한 번 재시도 후 그래도 실패하면 409로 변환 (ARCHITECTURE §2.4).

## Action Items (Checklist)

- [x] `domain/clazz/ClassRepository.java` — `extends JpaRepository<Class, UUID>`.
  - `@Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select c from Class c where c.id = :id") Optional<Class> findByIdForUpdate(@Param("id") UUID id);`
  - `Page<Class> findByStatusOrderByCreatedAtDesc(ClassStatus, Pageable)`.
- [x] `application/clazz/ClassApplicationService.java` — `@Service`.
  - `createDraft(UUID creatorId, CreateClassCommand)` — `@Transactional`. Creator 역할 검증 후 `Class.draft()` → `save()` → `ClassDto` 반환.
  - `transitionStatus(UUID classId, UUID requesterId, ClassStatus target)` — `@Transactional`. `findByIdForUpdate`로 잠금, target에 따라 `open()` 또는 `close()` 호출, save.
  - `getById(UUID classId)` — `@Transactional(readOnly=true)`. **`@Cacheable` 미사용 (Pre-flight 5)**. 매 호출 DB 직접 조회.
  - `listOpenClasses(Pageable pageable)` — `@Transactional(readOnly=true)`.
  - `OptimisticLockingFailureException` 재시도 1회는 `@Retryable(maxAttempts=2)` 또는 수동 try-catch.
- [x] `application/clazz/ClassStatusMirrorListener.java` — `@Component`. `@TransactionalEventListener(phase=AFTER_COMMIT)` 두 메서드로 `ClassOpenedEvent`, `ClassClosedEvent`를 받아 `redisTemplate.opsForValue().set("class:status:" + classId, status.name(), Duration.ofMinutes(5))` 호출. Spring Cache 가 아닌 직접 Redis String 갱신 (Pre-flight 5).
- [x] `domain/clazz/event/ClassOpenedEvent.java`, `ClassClosedEvent.java` — `record(ClassId, UserId, Instant occurredAt)`.
- [x] `Class.open()/close()` 메서드는 직접 이벤트 발행하지 않음. `ClassApplicationService`가 save 후 `ApplicationEventPublisher.publishEvent()` 호출.
- [x] `web/clazz/ClassController.java` — `@RestController @RequestMapping("/api/classes")`.
  - `POST /` — `@CurrentUserId UUID creatorId`, `@Valid @RequestBody CreateClassRequest` → 201.
  - `PATCH /{id}/status` — body `{target: "OPEN"|"CLOSED"}` → 200 또는 409.
  - `GET /{id}` → 200 (캐시 적용).
  - `GET /` (paged) → 200 with `Page<ClassResponse>`.
- [x] `web/clazz/dto/CreateClassRequest.java`, `ClassResponse.java`, `ChangeStatusRequest.java` — record + Bean Validation 어노테이션.
- [x] (Verify) `web/clazz/ClassControllerTest.java` — `@WebMvcTest`로 인증/입력 검증 케이스 작성, 서비스는 `@MockBean`.
- [x] (Verify) `application/clazz/ClassStatusMirrorListenerTest.java` — `Class.open()` 성공 → AFTER_COMMIT 이벤트 발행 → `class:status:{id}` Redis String 값이 `OPEN` 으로 갱신되는지 Testcontainers Redis 로 검증. `@Cacheable` 캐시 동작 검증은 **삭제** — Spring Cache 미사용 (Pre-flight 5).
