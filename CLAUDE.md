# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

> 보조 문서.
> - `ORCHESTRATION.md` — multi-agent 파이프라인 정책 (단계·산출물·차단 규칙).
> - `AGENTS-SKILLS-HARNESS.md` — 1페이지 허브 (세부 카탈로그는 `docs/agents/*.md`·`docs/harness/*.md`).
> - `DOCS.md` — DDD 도메인 설계 (바운디드 컨텍스트·애그리거트·상태 전이·도메인 이벤트·불변식).
> - `ARCHITECTURE.md` — 동시성·캐싱·스케줄링 설계 (전략 비교, Redis ZSET + Lua atomic script 채택 사유).
> - `CONTRIBUTING.md` — 브랜치·커밋·PR 규약.
> - `context.yaml` — 위 문서들을 LLM이 인덱스 형태로 빠르게 스캔하기 위한 구조화된 컨텍스트.

---

## What this workspace is

라이브 강의 수강신청(live-class) 시스템을 개발하는 **orchestrator workspace**다. 메인 Claude Code 세션은 직접 `src/`에 코드를 쓰지 않고 6개 sub-agent(`.claude/agents/`)를 디스패치한다. 실제 애플리케이션 코드는 `live-class/`에 있으며 Spring Boot 4.0.6 · Java 21 · Gradle Groovy DSL · 모듈러 모놀리스 구조다.

---

## Build / test / run commands

모든 명령은 `live-class/` 디렉터리 안에서 실행한다.

| 목적 | 명령 |
|------|------|
| 컴파일 + bootJar | `./gradlew build` |
| 전체 테스트 | `./gradlew test` |
| 단일 테스트 | `./gradlew test --tests com.example.liveclass.domain.clazz.ClassTest` |
| 로컬 실행 | `./gradlew bootRun` (Postgres + Redis 필요. Swagger UI: `http://localhost:8080/swagger-ui.html`) |
| 인프라 스택 | `docker compose up` / `docker compose down` (루트 `docker-compose.yml`) |

**테스트 실행 전제.** `./gradlew test`는 JUnit 5 + Testcontainers 기반이다.
- 통합 테스트(`@IntegrationTest` 메타 어노테이션을 단 클래스)가 PostgreSQL/Redis 컨테이너를 띄우므로 **로컬 Docker 데몬이 떠 있어야 통과한다**.
- 도메인 단위 테스트는 외부 의존이 없어 Docker 없이도 동작한다.
- 일부 테스트는 H2 + 로컬 Redis fallback 프로파일(`src/test/resources/application.yaml`)을 사용한다.

---

## Architecture big picture

### 바운디드 컨텍스트 (모듈러 모놀리스)

세 컨텍스트가 **단일 JVM 안에서 in-process 호출 + Spring `ApplicationEvent`**로 협력한다. Aggregate 간 직접 객체 참조는 금지하고 **ID 참조**(`ClassId`, `EnrollmentId`, `UserId`)만 사용한다.

| Context | Aggregate Root | 책임 |
|---------|---------------|------|
| Class | `domain/clazz/Class` | 강의 메타·정원·`DRAFT → OPEN → CLOSED` 일방향 상태 전이 |
| Enrollment | `domain/enrollment/Enrollment` | 신청·확정·취소·대기열. 대기열은 별도 Aggregate가 아니라 `status = WAITLISTED`로 표현 |
| User | `domain/user/User` | 역할(`CREATOR` / `CLASSMATE`) 식별. 인증은 mock(`X-User-Id` 헤더) |

### 패키지 레이아웃 (`com.example.liveclass`)

```
domain/{clazz, enrollment, user, shared}    Entity · VO · 도메인 예외 · 도메인 이벤트
application/{clazz, enrollment, user}       orchestration · mock payment · read query
infrastructure/scheduling                   Quartz ClassAutoCloseJob, QuartzConfig
infrastructure/PartialIndexInitializer      DB partial unique index 부팅 시 보장
web/{clazz, enrollment, user}               REST controller + DTO
web/auth                                    MockUserFilter, CurrentUserId, ArgumentResolver
web/error                                   GlobalExceptionHandler
config/                                     OpenApiConfig, WebMvcConfig, LuaScriptConfig
```

### 핵심 동시성 결정 (ARCHITECTURE.md §4)

- **Source of truth는 PostgreSQL**, 그러나 race-critical 결정(잔여 정원 분기, 대기열 승격)은 **Redis ZSET (`enrolled:{classId}` / `waitlist:{classId}`) + Lua atomic script**가 1차 게이트다.
- Lua 스크립트는 `src/main/resources/lua/` — `enrollment_apply.lua`, `enrollment_cancel_promote.lua`, `enrollment_compensate.lua`. Redis 단일 스레드 직렬화로 read-modify-write 경합 윈도우가 0이다.
- DB 쓰기는 같은 application 트랜잭션 안에서 Lua 결정 직후 수행. Lua 성공 후 DB 실패 시 보상 Lua로 ZSET 갱신을 되돌린다.
- **Redis 다운 = fail-closed (503)**. 정합성 우선 정책.
- Class 상태 전이 동시성(수동 close ↔ Quartz auto-close 충돌)은 `@Version` JPA optimistic lock으로 해소.
- 시간 기반 자동 close는 Quartz **in-memory** JobStore — `infrastructure/scheduling/ClassAutoCloseJob`, 매일 00:05 KST. 멀티 인스턴스 확장 시 JDBC JobStore 전환.

### 테스트 전략

- 도메인 단위: `ClassTest`, `EnrollmentTest`, `MoneyTest`, `CapacityTest`, `ClassPeriodTest`, `CancellationWindowTest` 등 — 외부 의존 없음.
- 통합: `@IntegrationTest` 메타 어노테이션(`support/IntegrationTest.java` = `@SpringBootTest + @ActiveProfiles("test") + @Testcontainers`) + `PostgresTestContainer` / `RedisContainerExtension`.
- 동시성 회귀: `ClassRepositoryConcurrencyTest`, `ClassOptimisticLockTest`, `AutoCloseManualCloseRaceTest`, `MisfireRecoveryTest`, `EnrollmentRepositoryIntegrationTest`.

---

## Multi-agent pipeline & harness

### 파이프라인

6개 sub-agent를 다음 순서로 실행한다(상세는 `ORCHESTRATION.md`).

1. `ddd-domain-architect` → `DOCS.md`
2. `concurrency-architect` → `ARCHITECTURE.md`
3. `scrum-task-decomposer` → `plan/before/NN_<role>_<slug>.md`
4. (parallel) `infra-cicd-operator` + `git-master-conventions`
5. `blueprint-executor-worker` × N — 한 태스크 파일을 워크트리 격리로 코드화. **유일하게 `src/`에 쓰는 에이전트이며 `isolation: "worktree"`가 강제된다.** 메인 세션이 직접 `src/`에 쓰는 것을 지양한다.

### 슬래시 스킬

`/design-domain` · `/design-concurrency` · `/decompose-tasks` · `/setup-infra` · `/setup-git-rules` · `/exec-blueprint` — 각 스킬이 정확히 하나의 에이전트를 래핑한다(`docs/agents/skills.md`).

### 파이프라인 상태 배지

`SessionStart` / `UserPromptSubmit` 훅이 매 메시지 앞에 `[pipeline] DOCS:✓/✗ · ARCH:✓/✗ · before:N · after:M`을 자동 주입한다. `plan/before/`는 미완료 태스크, `plan/after/`는 워커 완료 태스크다. `reports/`에 워커별 종료 보고서가 쌓인다.

### 하네스 안전망 (`.claude/settings.json` + `.claude/hooks/*.sh`)

- 자동 차단(pre-bash 훅): `rm -rf`, `git push --force`, `git reset --hard`, 보고서 누락 상태의 `plan/before → plan/after` 이동, 체크박스 미완료 상태의 이동.
- 자동 차단(permissions deny): `.env`, `.env.*`, `*.key`, `*.pem`, `credentials*`, `secrets*` 읽기.
- 자동 허용: 읽기·빌드 위주 명령(`ls`, `git status/diff/log`, `cat`, `./gradlew test/build/bootRun/clean`, `docker compose up/down/ps/logs`).
- post-write: `.md` 변경 시 `markdownlint-cli` 실행(없으면 no-op), 빈 충돌 가능성 경고.
- `Stop`: `DOCS.md`/`ARCHITECTURE.md` 변경이 있었으면 종료 전 경고, 미해결 후속 작업 경고.

---

## Workflow rules (CONTRIBUTING.md 핵심)

- **GitHub Flow + Squash Merge.** `main` 직접 push 금지.
- **브랜치 네이밍.** `<type>/task-NN-<slug>` — `type` ∈ `feature` / `fix` / `refactor` / `perf` / `test` / `docs` / `chore` / `ci`. `NN`은 `plan/before/NN_*.md`의 두 자리와 정확히 일치.
- **한 브랜치 = 한 태스크 = 한 PR.** 두 태스크를 한 브랜치에 묶지 않는다.
- **Conventional Commits.** subject는 50자 이내 영어 명령형, 마침표 금지. 푸터에 `Refs: plan/before/NN_<Role>_<Slug>.md`를 **반드시** 명시한다(PR-태스크 추적성).
- **PR 머지 조건.** `.github/pull_request_template.md` 체크리스트 충족 + Gemini AI 자동 리뷰 P0/P1 대응 완료.

---

## Language convention

- 사용자 응답·대화 = **한국어**.
- 커밋 메시지 · planning docs · 코드 주석 · 설계 문서(DOCS/ARCHITECTURE) = **영어**.
- **PR 제목 = 영어 (Conventional Commits)**, **PR 본문(summary·테스트 절차 등 body 전체) = 한국어**. Squash Merge 시 PR 제목이 그대로 squash 커밋 subject가 되므로 제목은 커밋 메시지 규약(50자 이내 영어 명령형, `<type>(scope): ...`)을 따른다. 본문은 사람이 읽는 텍스트라 한국어. 코드/명령/파일명/hook 이름 등 식별자는 한국어 본문 안에서도 원문 유지.
- **`reports/*.md` (워커 end-of-run 보고서) = 한국어.** 사람이 읽는 운영 보고서이며 PR 본문과 동일 정책. 코드/명령/파일명/식별자는 원문 유지.
- 새 소스 파일 첫 줄에는 한 줄짜리 한국어 헤더 주석을 넣는다(부모 `~/CLAUDE.md` 규칙 6).

---

## Behavioral rules

10개 행동 규칙(Think Before Coding · Simplicity First · Surgical Changes · Goal-Driven Execution · No Closing Colons · File Header Comments in Korean · Plan + Checklist + Context Notes · Run Tests Before Marking Complete · Semantic Commits · Read Errors Don't Guess)은 사용자 글로벌 `~/CLAUDE.md`에 정의되어 있다. 프로젝트 파일에서 중복 기재하지 않는다. 프로젝트 규약과 충돌 시 프로젝트 규약이 우선한다.
