# 채점 제출용 README.md + ERD Mermaid + Swagger @Schema 보강

- **Assignee:** The Infra Operator
- **Dependencies:** 12_Quality_Guardian_Concurrency_Integration_Tests.md
- **Definition of Done (DoD):**
  - `README.md`가 채용 과제 제출 표준 10 섹션 템플릿으로 작성되어 채점자가 바로 빌드/실행/테스트할 수 있다.
  - ERD 다이어그램이 Mermaid `erDiagram` 형식으로 README.md 안에 포함된다 (users, classes, enrollments 3 테이블 + 관계).
  - 모든 Request/Response DTO에 `@Schema(description=...)`가 추가되어 Swagger UI가 채점자에게 친절한 설명을 제공한다.
  - `docker-compose up`으로 PostgreSQL + Redis + 백엔드가 한 번에 기동되고 `localhost:8080/swagger-ui.html`에 접근 가능.
  - `./gradlew test`가 단위 + 통합 + 동시성 테스트 전체를 통과한다 (CI 검증).

## Action Items (Checklist)

- [ ] `README.md` 작성 — 다음 10 섹션 포함.
  1. **프로젝트 개요** — 한 문단 (라이브 강의 수강신청 시스템, Creator/Classmate).
  2. **기술 스택** — Java 21, Spring Boot 4.0.6, PostgreSQL 16, Redis 7, JPA, Gradle.
  3. **로컬 실행** — `cp .env.example .env` → `docker compose up -d` → `./gradlew bootRun`.
  4. **API 문서** — Swagger UI 링크 `http://localhost:8080/swagger-ui.html`.
  5. **ERD** — Mermaid 다이어그램.
  6. **도메인 설계 요약** — DOCS.md 링크 + Class/Enrollment 상태 전이 다이어그램.
  7. **동시성 설계 요약** — ARCHITECTURE.md 링크 + "왜 Redis 락 대신 PG pessimistic lock인가" 3줄 요약.
  8. **테스트** — `./gradlew test` 실행 방법, 동시성 테스트 50 스레드 시나리오 설명.
  9. **API 엔드포인트 목록** — 표 형태 (Method, Path, 인증, 설명, 응답 status).
  10. **트레이드오프 및 한계** — Spring Security 미사용 이유, Outbox 패턴 미적용 이유.
- [ ] README.md의 ERD Mermaid 블록 작성.
  ```
  erDiagram
    USERS ||--o{ CLASSES : creates
    USERS ||--o{ ENROLLMENTS : applies
    CLASSES ||--o{ ENROLLMENTS : has
    USERS { uuid id PK; string role; string name; instant created_at }
    CLASSES { uuid id PK; string title; numeric price_amount; int capacity; date start_date; date end_date; string status; uuid creator_id FK; long version }
    ENROLLMENTS { uuid id PK; uuid class_id FK; uuid classmate_id FK; string status; instant applied_at; instant paid_at; instant cancelled_at; long version }
  ```
- [ ] `web/clazz/dto/*.java`의 record 필드에 `@Schema(description="강의 제목", example="Spring Boot 마스터 클래스", requiredMode=REQUIRED)` 추가.
- [ ] `web/enrollment/dto/*.java`의 record 필드에 동일하게 `@Schema` 추가.
- [ ] `web/user/dto/*.java`에 `@Schema` 추가.
- [ ] 각 Controller의 모든 메서드에 `@Operation(summary="...", description="...")` + `@ApiResponses({@ApiResponse(responseCode="201", description="..."), @ApiResponse(responseCode="409", description="...")})` 추가.
- [ ] `.env.example` 파일 작성 — `POSTGRES_URL`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`, `JPA_DDL_AUTO`, `REDISSON_ENABLED` 키와 더미 값.
- [ ] `docker-compose.yml` 검토 — 4 profile(db, redis, back, front)에 헬스체크 추가 (postgres `pg_isready`, redis `redis-cli ping`, back `curl /actuator/health`).
- [ ] `live-class/Dockerfile` 검토 — multi-stage build (gradle build → JRE 21 alpine), final image size 200MB 이하.
- [ ] (Verify) `./gradlew clean test` 전체 통과 (`BUILD SUCCESSFUL`).
- [ ] (Verify) `docker compose up -d db redis back` 후 `curl http://localhost:8080/actuator/health` → `{"status":"UP"}`.
- [ ] (Verify) `curl http://localhost:8080/v3/api-docs` 응답에 모든 endpoint와 schema가 포함됨.
- [ ] (Verify) `README.md`의 ERD Mermaid 블록이 GitHub UI에서 렌더링되는지 push 후 확인.
