<!-- Task 01 종료 보고서 — MockUserFilter + @CurrentUserId + DomainException + GlobalExceptionHandler 인프라 구축 -->
---
status: complete
owner: blueprint-executor-worker (dispatched by Maestro/Opus), with Maestro fix-up
created: 2026-05-12
updated: 2026-05-12
---

# Report: Task 01 — MockUserFilter + GlobalExceptionHandler

## Input Summary

- 태스크 파일: `plan/before/01_Infra_Operator_MockUserFilter_And_ExceptionHandler.md` (now at `plan/after/`)
- DOCS.md 기반 주요 도메인 개념: User Aggregate 의 식별을 mock 으로 제공 (Spring Security 미사용). 인증은 `X-User-Id` 헤더로 단순화.
- ARCHITECTURE.md 기반 주요 결정: §7 의 fail-closed 정책에 호응 — 잘못된 입력은 즉시 4xx 로 응답하고 도메인 로직까지 도달하지 않도록 한다.

## What Was Done

| File | Action | Summary |
|------|--------|---------|
| `live-class/src/main/java/com/example/liveclass/web/auth/CurrentUser.java` | created | `record CurrentUser(UUID userId)`. 향후 컨트롤러에서 사용자 컨텍스트 표현용. |
| `live-class/src/main/java/com/example/liveclass/web/auth/CurrentUserId.java` | created | `@Target(PARAMETER) @Retention(RUNTIME) @interface CurrentUserId`. 컨트롤러 메서드 파라미터 어노테이션. |
| `live-class/src/main/java/com/example/liveclass/web/auth/MockUserFilter.java` | created | `extends OncePerRequestFilter`. `X-User-Id` 헤더 파싱 → `request.setAttribute("currentUserId", uuid)`. 헤더 누락 401, 잘못된 UUID 400, swagger/actuator 화이트리스트. ProblemDetail JSON 직접 응답. |
| `live-class/src/main/java/com/example/liveclass/web/auth/CurrentUserArgumentResolver.java` | created | `HandlerMethodArgumentResolver`. `@CurrentUserId UUID` 파라미터에 currentUserId attribute 주입. |
| `live-class/src/main/java/com/example/liveclass/config/WebMvcConfig.java` | created | `@Configuration WebMvcConfigurer`. `FilterRegistrationBean<MockUserFilter>` (`/api/*`, Ordered.HIGHEST_PRECEDENCE + 10) + `addArgumentResolvers` 에서 CurrentUserArgumentResolver 등록. |
| `live-class/src/main/java/com/example/liveclass/domain/shared/DomainException.java` | created | abstract class extends RuntimeException. `errorCode: String`, `status: HttpStatus`, `message: String`. Lombok `@Getter`. |
| `live-class/src/main/java/com/example/liveclass/web/error/GlobalExceptionHandler.java` | created | `@RestControllerAdvice`. 4 handler: DomainException → status+detail+errorCode, MethodArgumentNotValidException → 400, OptimisticLockingFailureException → 409, Exception (catch-all) → 500. 모두 `ProblemDetail` 응답. |
| `live-class/src/test/java/com/example/liveclass/web/auth/MockUserFilterTest.java` | created | `MockMvc.standaloneSetup` 슬라이스 테스트. 401 (헤더 없음), 400 (잘못된 UUID), 200 (정상 UUID) 3 케이스. |
| `live-class/src/test/java/com/example/liveclass/web/error/GlobalExceptionHandlerTest.java` | created | DomainException 던지는 DummyController 가 ProblemDetail 형식 + status 422 + errorCode 속성을 반환하는지 검증. |
| (PR) | meta | `feat(task-01): add MockUserFilter and GlobalExceptionHandler` (#2). 워커 commit `9e70967` + Maestro fix `3af6c11` (squash → main `bdfb86b`). |

## Rationale & Tradeoffs

- **선택 1 (`@WebMvcTest` → `MockMvc.standaloneSetup`)**: 워커 prompt 의 fallback 지시(§8) 에 따라 단순 standaloneSetup 으로 작성. 의존성 없이 빠르게 검증 가능.
- **대안 1**: `@WebMvcTest` 슬라이스. Spring Boot 4 호환성이 검증 안 된 시점이라 회피.
- **트레이드오프 1**: standaloneSetup 은 `@RestControllerAdvice` 의 자동 등록을 받지 않아 `setControllerAdvice(new GlobalExceptionHandler())` 를 명시. Spring Boot 4 가 안정되면 향후 task 들에서 `@WebMvcTest` 로 교체 가능.
- **선택 2 (`ObjectMapper` 의존성 제거)**: Maestro fix-up 단계에서 `MockUserFilter` 가 ObjectMapper 를 생성자로 받지 않고 클래스 내부에 `static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();` 로 직접 생성. `WebMvcConfig` 의 ObjectMapper 의존성도 제거.
- **대안 2**: `@Bean ObjectMapper objectMapper()` 를 명시 등록. 모든 컨텍스트에 영향이 가서 task 02 (Redis JSON 직렬화) 와 충돌 위험 있음 — 회피.
- **트레이드오프 2**: ObjectMapper 인스턴스가 Spring 관리 빈이 아니라 클래스 static. ObjectMapper 는 thread-safe 라 동시성 문제 없음. 다만 향후 Jackson 모듈 (예: JavaTimeModule) 자동 등록을 못 받음 — task 02 에서 명시 `@Bean ObjectMapper` 등록 시 재고 가능.
- **선택 3 (`$.errorCode` jsonPath)**: 테스트가 `$.properties.errorCode` 였는데 Spring `ProblemDetail.setProperty()` 가 properties 를 JSON root 로 평탄화함을 확인. 정답은 `$.errorCode`. Maestro fix-up.

### 핵심 발견 — Spring Boot 4 변경점

- **`spring-boot-starter-webmvc` 가 `com.fasterxml.jackson.databind.ObjectMapper` 빈을 자동 등록하지 않는다.** (Spring Boot 3 의 `spring-boot-starter-web` 와 차이). 후속 task 02, 09, 10 의 worker prompt 에 미리 주의 추가 권장.
- **`ProblemDetail.setProperty()` 의 직렬화** — `properties` 맵이 JSON root 로 평탄화. jsonPath 사용 시 `$.errorCode` (not `$.properties.errorCode`).

## Follow-ups

- [x] (검증) PR #2 squash merge → main `bdfb86b` — 완료. CI Build & Test SUCCESS, gemini-review SUCCESS.
- [x] (검증) `plan/before/01_*.md` → `plan/after/` 이동 commit `a2f60de` — 완료.
- [x] task 02 worker prompt 에 "Spring Boot 4: ObjectMapper 빈 자동 등록 X — 직접 생성하거나 `@Bean` 등록 필요" 주의 추가. — **resolved**: task 02 dispatch prompt 의 implementation hint §6 에 명시. PR #7 worker 가 이를 읽고 작업 진행.
- [ ] (잠재적) `LiveClassApplicationTests` 가 `@SpringBootTest` 로 컨텍스트 로드를 요구하므로 향후 어떤 `@Configuration` 클래스든 의존성 누락 시 contextLoads 가 깨진다. 모든 task 의 PR CI 에서 이 테스트가 1차 fail-fast 안전망 역할.
- [ ] (잠재적) ObjectMapper 가 static 으로 한 번 생성됨 — 향후 Java time API 직렬화 등 요구 시 task 02 단계에서 `@Bean ObjectMapper` 명시 등록 + JavaTimeModule 등록 필요.
