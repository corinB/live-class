# 채점 제출용 README.md + ERD Mermaid + Swagger @Schema 보강 (§7 ZSET+Lua, §10 한계 신설)

- **Assignee:** The Infra Operator
- **Dependencies:** 12_Quality_Guardian_Concurrency_Integration_Tests.md, 14_Infra_Operator_Redis_DB_Reconcile_Runner.md, 16_Infra_Operator_Class_AutoClose_Quartz_Job.md
- **Definition of Done (DoD):**
  - `README.md`가 채용 과제 제출 표준 10 섹션 템플릿으로 작성되어 채점자가 바로 빌드/실행/테스트할 수 있다.
  - ERD 다이어그램이 Mermaid `erDiagram` 형식으로 README.md 안에 포함된다 (users, classes, enrollments 3 테이블 + 관계).
  - 모든 Request/Response DTO에 `@Schema(description=...)`가 추가되어 Swagger UI가 채점자에게 친절한 설명을 제공한다.
  - `docker-compose up`으로 PostgreSQL + Redis + 백엔드가 한 번에 기동되고 `localhost:8080/swagger-ui.html`에 접근 가능.
  - `./gradlew test`가 단위 + 통합 + 동시성 테스트 전체를 통과한다 (CI 검증).
  - **§7 동시성 설계 요약** 이 ZSET+Lua 채택 근거 + dual-SoT 트레이드오프 + reconcile 절차를 3~5줄로 압축해 설명한다.
  - **신규 §10 "한계 및 미구현"** 섹션이 4가지 도메인 빈틈을 명시한다 (각 항목: 결과 1줄 + production 해결 방향 1줄).

## Action Items (Checklist)

- [x] `README.md` 작성 — 다음 10 섹션 포함.
  1. **프로젝트 개요** — 한 문단 (라이브 강의 수강신청 시스템, Creator/Classmate).
  2. **기술 스택** — Java 21, Spring Boot 4.0.6, PostgreSQL 16, Redis 7 (ZSET mirror + Lua), Quartz (in-memory), JPA, Gradle.
  3. **로컬 실행** — `cp .env.example .env` → `docker compose up -d` → `./gradlew bootRun`.
  4. **API 문서** — Swagger UI 링크 `http://localhost:8080/swagger-ui.html`.
  5. **ERD** — Mermaid 다이어그램.
  6. **도메인 설계 요약** — DOCS.md 링크 + Class/Enrollment 상태 전이 다이어그램 (Class 에 endDate 자동 close 분기 표시).
  7. **동시성 설계 요약** — ARCHITECTURE.md 링크 + "왜 Lua atomic + ZSET FIFO인가" 3~5 줄 요약. (a) race-critical path 라운드트립 1회 압축. (b) ZSET score 가 자료구조 차원 FIFO 강제. (c) 트레이드오프 — 이중 SoT 를 보상 Lua + 부팅 reconcile + partial unique index 3중 방어로 봉합. (d) `POST /api/admin/reconcile/{classId}` 운영 escape hatch 언급. (e) **Cache 계층 미사용** — ZSET mirror 가 결정 경로 캐시 역할을 하므로 별도 Spring Cache 도입 안 함 (Pre-flight 5). production 에서는 RPS·hit-rate·TTL 측정 후 도입 검토.
  8. **테스트** — `./gradlew test` 실행 방법, 동시성 테스트 50 스레드 시나리오 + Redis disconnect + 보상 + reconcile 시나리오 설명.
  9. **API 엔드포인트 목록** — 표 형태 (Method, Path, 인증, 설명, 응답 status). admin reconcile 엔드포인트 포함.
  10. **한계 및 미구현** — 4개 도메인 빈틈 명시.
      - (1) `Class.changeCapacity` 는 DRAFT 상태에서만 가능. OPEN 후 정원 증설 불가. *Production 해결*: 증설 시 WAITLISTED 일부를 PENDING 으로 자동 승격하는 Lua 스크립트 추가.
      - (2) CLOSED 강의의 enrollment cancel 은 `paidAt + 7d` 만 게이트로 사용 (close 시점과 무관). *Production 해결*: 강의별 `closedClassCancelPolicy` enum (ALLOW_INSIDE_WINDOW / DENY / REFUND_ONLY).
      - (3) WAITLISTED 승격 후 PENDING 결제 timeout 없음. 사용자가 수동 cancel 안 하면 뒷사람은 무한 대기. *Production 해결*: `WaitlistPromotedEvent` 수신 N시간 후 자동 cancel + 다음 승격 트리거하는 Quartz one-shot job.
      - (4) Class 등록 후 price 변경 불가 (immutable). *Production 해결*: Class 에 versioned pricing + Enrollment 에 `paidAmount` 스냅샷.
- [x] README.md의 ERD Mermaid 블록 작성.
  ```
  erDiagram
    USERS ||--o{ CLASSES : creates
    USERS ||--o{ ENROLLMENTS : applies
    CLASSES ||--o{ ENROLLMENTS : has
    USERS { uuid id PK; string role; string name; instant created_at }
    CLASSES { uuid id PK; string title; numeric price_amount; int capacity; date start_date; date end_date; string status; uuid creator_id FK; long version }
    ENROLLMENTS { uuid id PK; uuid class_id FK; uuid classmate_id FK; string status; instant applied_at; instant paid_at; instant cancelled_at; long version }
  ```
- [x] `web/clazz/dto/*.java`의 record 필드에 `@Schema(description="강의 제목", example="Spring Boot 마스터 클래스", requiredMode=REQUIRED)` 추가.
- [x] `web/enrollment/dto/*.java`의 record 필드에 동일하게 `@Schema` 추가.
- [x] `web/user/dto/*.java`에 `@Schema` 추가.
- [~] 각 Controller의 모든 메서드에 `@Operation(summary="...", description="...")` + `@ApiResponses({...})` 추가. — **이월** (별도 후속 chore PR).
- [x] `.env.example` 파일 — 기존에 이미 존재 (`/c/Users/qorwh/.../p/.env.example`). 키 검증 완료.
- [x] `docker-compose.yml` 헬스체크 — postgres `pg_isready` + redis `redis-cli ping` + back `wget /actuator/health` 이미 적용됨.
- [~] `live-class/Dockerfile` multi-stage build / image size 200MB 이하 검증 — **이월** (별도 chore).
- [x] (Verify) `./gradlew clean test` 전체 통과 — PR #98 CI Build & Test pass.
- [x] (Verify) `docker compose up -d db redis` 후 `curl http://localhost:8080/actuator/health` → `{"status":"UP"}` — bootRun 수동 검증 완료.
- [x] (Verify) `curl http://localhost:8080/v3/api-docs` 응답에 11개 DTO 스키마 포함 — curl 검증 완료.
- [x] (Verify) `README.md`의 ERD Mermaid 블록이 GitHub UI에서 렌더링 — PR #98 페이지에서 확인.
