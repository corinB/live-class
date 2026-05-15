# CLAUDE.md

Guidance for Claude Code (claude.ai/code) when working in this repository.

> Companion docs.
> - `ORCHESTRATION.md` ??multi-agent pipeline policy (stages, deliverables, blocking rules).
> - `DOCS.md` ??DDD domain design (bounded contexts, aggregates, state transitions, domain events, invariants).
> - `ARCHITECTURE.md` ??concurrency, caching, scheduling (strategy comparison, rationale for Redis ZSET + Lua atomic script).
> - `CONTRIBUTING.md` ??branch, commit, PR conventions.
> - `context.yaml` ??structured index of the docs above for fast LLM scanning.

---

## What this workspace is

An **orchestrator workspace** for the live-class enrollment system. The main Claude Code session does not write to `src/` directly; it dispatches to 6 sub-agents in `.claude/agents/`. Application code lives under `live-class/` (Spring Boot 4.0.6, Java 21, Gradle Groovy DSL, modular monolith). `front/` is an nginx static placeholder for Swagger UI (compose `front` profile).

---

## Build / test / run commands

All commands run inside `live-class/`.

| Purpose | Command |
|---------|---------|
| Compile + bootJar | `./gradlew build` |
| Full test suite | `./gradlew test` |
| Single test | `./gradlew test --tests com.example.liveclass.domain.clazz.ClassTest` |
| Local run | `./gradlew bootRun` (needs Postgres + Redis. Swagger UI at `http://localhost:8080/swagger-ui.html`) |
| Infra stack | `docker compose --profile db --profile redis --profile back --profile front up -d` / `... down`. Export `COMPOSE_PROFILES=db,redis,back,front` to skip flags. |

**Docker stack prerequisites.** Root `.env` must define `POSTGRES_USER/PASSWORD/DB/URL` and `REDIS_HOST/PORT/PASSWORD`.

**Test prerequisites.** `./gradlew test` uses JUnit 5 + Testcontainers. Classes meta-annotated `@IntegrationTest` need a running Docker daemon. Pure domain tests run without Docker. See `live-class/src/test/java/.../support/IntegrationTest.java`.

---

## Architecture big picture

Three bounded contexts collaborate inside a single JVM via in-process calls + Spring `ApplicationEvent`. Direct aggregate-to-aggregate references are forbidden; use **ID references** only (`ClassId`, `EnrollmentId`, `UserId`). Full design in `DOCS.md`.

| Context | Aggregate Root | Responsibility |
|---------|---------------|----------------|
| Class | `domain/clazz/Class` | Class metadata, capacity, one-way state transition `DRAFT ??OPEN ??CLOSED` |
| Enrollment | `domain/enrollment/Enrollment` | Apply, confirm, cancel, waitlist (`status = WAITLISTED` instead of a separate aggregate) |
| User | `domain/user/User` | Roles (`CREATOR` / `CLASSMATE`). Auth is mocked via `X-User-Id` header |

Package layout: `com.example.liveclass.{domain, application, infrastructure, web, config}`. Domain modules: `clazz / enrollment / user / shared`.

**Concurrency decisions** (full text in `ARCHITECTURE.md 짠4`).

- PostgreSQL is the source of truth. Race-critical decisions (remaining capacity, waitlist promotion) gate first through **Redis ZSET + Lua atomic script** under `src/main/resources/lua/`.
- Redis down = fail-closed (HTTP 503). Consistency over availability.
- Class state transitions use `@Version` JPA optimistic lock.
- Auto-close runs on Quartz in-memory JobStore, daily 00:05 KST.

---

## Multi-agent pipeline & harness

- **Human-driven pipeline**: 6 stages, 6 sub-agents ??`ddd-domain-architect` ??`concurrency-architect` ??`scrum-task-decomposer` ??(`infra-cicd-operator` ??`git-master-conventions`) ??`blueprint-executor-worker` 횞 N. Details in `ORCHESTRATION.md`.
- **Automation pipeline** (local-driven): 2 additional sub-agents ??`maestro` and `worker` (`.claude/agents/`). User opens a GitHub Issue with label `maestro:auto`; the main session runs Maestro ??Worker on demand. GitHub Actions handles only notification (`maestro-dispatch.yml`), auto-rebase (`auto-rebase.yml`), and gatekeeping (`gatekeeper.yml`). Kill switch: Repository variable `AUTOMATION_ENABLED`. Details in `docs/architecture/automation-pipeline.md` and `docs/guides/automation-*.md`.
- Six slash skills wrap human-driven agents (`/design-domain`, `/design-concurrency`, `/decompose-tasks`, `/setup-infra`, `/setup-git-rules`, `/exec-blueprint`). The automation pipeline does not use slash skills ??the main session invokes Maestro/Worker via the Agent tool directly. Catalog: `docs/agents/skills.md`.
- `SessionStart` / `UserPromptSubmit` hooks prepend `[pipeline] DOCS:????쨌 ARCH:????쨌 before:N 쨌 after:M` to every message.
- Harness safety net (`.claude/settings.json` + `.claude/hooks/*.sh`) blocks destructive bash, reads of `.env`/`*.key`/`credentials*`, plan-move violations, and surrogate-split bloat (Read/Bash size caps + Read surrogate detect; see memory `surrogate-split-avoidance`). Catalog: `docs/harness/`.

---

## Workflow rules

Full text in `CONTRIBUTING.md`. Two essentials:

- **Branch naming.** `<type>/task-NN-<slug>`. `NN` must match the two-digit prefix of a file in `plan/before/NN_*.md`. `type` ??`feature / fix / refactor / perf / test / docs / chore / ci`. Harness or ops-only changes use `chore(harness): ...`.
- **Conventional Commits.** Subject ??0 chars, English imperative; footer must include `Refs: plan/before/NN_<Role>_<Slug>.md` for PR-to-task traceability.

---

## Language convention

- User-facing replies in this session = **Korean**.
- Commit messages, planning docs, code comments, design docs (DOCS/ARCHITECTURE), this CLAUDE.md = **English** (internal technical artifacts).
- **PR title = English (Conventional Commits)**, **PR body = Korean**. Identifiers (code, commands, filenames, hook names) stay verbatim inside Korean prose.
- **`reports/*.md` = Korean** (operator-facing reports).
- New source files start with a one-line Korean header comment (per parent `~/CLAUDE.md` rule 6).

---

## Behavioral rules

Ten behavioral rules (Think Before Coding 쨌 Simplicity First 쨌 Surgical Changes 쨌 Goal-Driven Execution 쨌 No Closing Colons 쨌 File Header Comments in Korean 쨌 Plan + Checklist + Context Notes 쨌 Run Tests Before Marking Complete 쨌 Semantic Commits 쨌 Read Errors Don't Guess) are defined in the user's global `~/CLAUDE.md`. Do not duplicate them here. Project rules win on conflict.
