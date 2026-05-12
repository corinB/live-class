# User Aggregate (Entity, VO, Repository, Service, Controller) 구현

- **Assignee:** The Logic Implementer
- **Dependencies:** 01_Infra_Operator_MockUserFilter_And_ExceptionHandler.md
- **Definition of Done (DoD):**
  - `User` Aggregate Root와 `UserId`, `UserRole` VO가 도메인 레이어(`domain.user`)에 Spring 어노테이션 없이 구현된다 (DOCS §3.3).
  - `UserRole` 변경 불가 (DOCS Invariant User §1), `name` 비어 있을 수 없음 (Invariant §2) 보장.
  - `POST /api/users` (역할/이름 입력) → 201, `GET /api/users/me` (헤더 X-User-Id) → 200 두 endpoint 동작.
  - `MockUserFilter`가 통과시킨 `X-User-Id`가 DB에 없으면 401이 아니라 404로 응답한다 (`UserNotFoundException`).
  - JPA `users` 테이블이 `ddl-auto: update`로 자동 생성된다.

## Action Items (Checklist)

- [x] `domain/user/UserId.java` — `record UserId(UUID value)` + `static UserId of(UUID)` + `static UserId newId()`.
- [x] `domain/user/UserRole.java` — `enum UserRole { CREATOR, CLASSMATE }`.
- [x] `domain/user/User.java` — JPA `@Entity @Table(name="users")` + 도메인 의도 메서드 `register()`, `isCreator()`, `isClassmate()` 구현, setter 없음.
  - 필드: `id (UUID)`, `role (Enum)`, `name (String, not null, length<=50)`, `createdAt (Instant)`.
  - private 기본 생성자 + 정적 팩토리 `User.register(UserRole, String, Instant)`.
- [x] `domain/user/UserRepository.java` — `extends JpaRepository<User, UUID>` + `Optional<User> findByIdAndRole(UUID, UserRole)`.
- [x] (Verify) `domain/user/UserTest.java` — 순수 JUnit으로 `register("")` → `IllegalArgumentException`, `register(null role)` → `IllegalArgumentException`, `isCreator()/isClassmate()` 동작 검증.
- [x] `web/user/UserController.java` — `@RestController @RequestMapping("/api/users")`.
  - `POST /api/users` body `{role, name}` → `UserApplicationService.register()` → 201 + `UserResponse`.
  - `GET /api/users/me` (`@CurrentUserId UUID userId`) → `UserResponse`.
- [x] `web/user/dto/RegisterUserRequest.java` (`record`, `@NotBlank name`, `@NotNull role`), `UserResponse.java` (`record`).
- [x] `application/user/UserApplicationService.java` — `@Service @Transactional` + `register()`, `getById(UUID)` 두 메서드. `getById`에서 미존재 시 `UserNotFoundException(404)` 던짐.
- [x] `domain/user/UserNotFoundException.java` — `DomainException` 상속, status 404, errorCode `"USER_NOT_FOUND"`.
- [x] (Verify) `web/user/UserControllerTest.java` — `@WebMvcTest`로 `POST /api/users` 검증, 잘못된 body는 400 ProblemDetail.
- [x] (Verify) `application/user/UserApplicationServiceTest.java` — `@DataJpaTest` + 서비스 직접 인스턴스로 register/getById 동작 검증.
