# MockUserFilter, WebMvc 등록, GlobalExceptionHandler 인프라 구성

- **Assignee:** The Infra Operator
- **Dependencies:** None
- **Definition of Done (DoD):**
  - `X-User-Id` 헤더에서 UUID를 파싱해 요청 컨텍스트(`HttpServletRequest` attribute `currentUserId`)에 주입하는 `MockUserFilter`가 모든 `/api/**` 요청에 적용된다.
  - `@ControllerAdvice` 기반 `GlobalExceptionHandler`가 도메인 예외(`DomainException` 계열), `IllegalStateTransitionException`, `DuplicateEnrollmentException`, `OutsideCancellationWindowException`, `OptimisticLockException`을 RFC 7807 `ProblemDetail`로 매핑한다.
  - `application.yaml`에 PostgreSQL/Redis 환경변수가 모두 채워진 상태로 `./gradlew bootRun`이 부팅에 성공한다.
  - 헤더 없이 `/api/health` 호출 시 401, 잘못된 UUID 형식이면 400으로 응답한다 (Verify 테스트로 확정).

## Action Items (Checklist)

- [ ] `live-class/src/main/java/com/example/liveclass/web/auth/CurrentUser.java` 작성 (record `CurrentUser(UUID userId)`).
- [ ] `live-class/src/main/java/com/example/liveclass/web/auth/MockUserFilter.java`를 `OncePerRequestFilter` 상속으로 구현해 `X-User-Id` 헤더를 파싱하고 `request.setAttribute("currentUserId", uuid)`로 주입.
- [ ] `MockUserFilter`에서 헤더 누락 시 401, UUID 파싱 실패 시 400 `ProblemDetail` JSON으로 직접 응답.
- [ ] Swagger 경로(`/v3/api-docs/**`, `/swagger-ui/**`)와 헬스체크(`/actuator/**`)는 필터 화이트리스트로 통과시킴.
- [ ] `live-class/src/main/java/com/example/liveclass/config/WebMvcConfig.java`에 `FilterRegistrationBean<MockUserFilter>` 등록 (`urlPatterns = "/api/*"`, order = `Ordered.HIGHEST_PRECEDENCE + 10`).
- [ ] `live-class/src/main/java/com/example/liveclass/web/auth/CurrentUserArgumentResolver.java` 작성 — `@AuthenticationPrincipal` 대신 커스텀 `@CurrentUserId` 어노테이션으로 컨트롤러 메서드 파라미터에 `UUID` 주입.
- [ ] `WebMvcConfig`에서 `addArgumentResolvers`로 `CurrentUserArgumentResolver` 등록.
- [ ] `live-class/src/main/java/com/example/liveclass/domain/shared/DomainException.java` 추상 클래스 (`RuntimeException` 상속, `errorCode: String`, `status: HttpStatus`) 정의.
- [ ] `live-class/src/main/java/com/example/liveclass/web/error/GlobalExceptionHandler.java`에 `@ExceptionHandler(DomainException.class)`, `@ExceptionHandler(MethodArgumentNotValidException.class)`, `@ExceptionHandler(OptimisticLockingFailureException.class)`, `@ExceptionHandler(Exception.class)` 4개 핸들러 작성하여 `ProblemDetail`로 매핑.
- [ ] (Verify) `live-class/src/test/java/com/example/liveclass/web/auth/MockUserFilterTest.java`에 `@WebMvcTest` 슬라이스 테스트 작성 — 헤더 없음 401, 잘못된 UUID 400, 정상 UUID 200을 한 줄짜리 더미 컨트롤러로 검증.
- [ ] (Verify) `GlobalExceptionHandlerTest`로 `DomainException` 던지는 더미 컨트롤러가 ProblemDetail 형식(`type`, `title`, `status`, `detail`)으로 응답하는지 검증.
