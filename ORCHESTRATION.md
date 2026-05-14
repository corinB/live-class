# ORCHESTRATION.md

Project-specific context for Claude Code (claude.ai/code) when working in this workspace. Read this **before** acting on any user request — `CLAUDE.md` in the same directory holds general behavioral guidelines that apply on top of what is described here.

---

## What this workspace is

This directory is **not an application repository**. It is an *orchestrator workspace*: six sub-agent definitions live in `.claude/agents/` and, when invoked in the correct order, they collectively produce a Spring Boot **modular monolith course-registration (수강신청) system** from a blank slate.

The live-class Spring Boot application now lives under `live-class/` and the design documents `DOCS.md` (287 lines) and `ARCHITECTURE.md` (472 lines) are in place, with multiple worker tasks already merged (`plan/after/` contains the completed set; `plan/before/` holds pending work). The main Claude Code session (i.e., you, by default) acts as the **orchestrator**. It dispatches sub-agents and does **not** itself write Spring Boot code. Implementation happens inside `blueprint-executor-worker` instances running in isolated git worktrees.

## Target domain

Course registration with:

- Capacity-bounded enrollment (last-seat race conditions must be handled correctly)
- 7-day cancellation window after payment
- Modular monolith (single JVM, in-process module communication via Spring `ApplicationEvent`)

The agent example snippets in `.claude/agents/*.md` all reference this domain.

## Target tech stack

| Layer | Choice |
|---|---|
| Language | Java 17+ |
| Framework | Spring Boot |
| Architecture | Modular monolith (not MSA) |
| RDBMS | PostgreSQL or MySQL (decided by `infra-cicd-operator`) |
| Cache / lock | Redis (Redisson or Lua-script-based distributed locks) |
| Local dev | Docker Compose |
| CI/CD | GitHub Actions |
| VCS workflow | GitHub Flow with Conventional Commits, enforced by `git-master-conventions` |

## The pipeline

Run agents in this order. **Do not skip steps.** Each downstream agent verifies that its required input artifacts exist and stops if they do not.

| # | Agent (`subagent_type`) | Consumes | Produces |
|---|---|---|---|
| 1 | `ddd-domain-architect` | business requirements (from user) | `DOCS.md` (bounded contexts, aggregates, state lifecycles, domain events) |
| 2 | `concurrency-architect` | `DOCS.md` | `ARCHITECTURE.md` (lock strategy comparison, Redis caching layer, request flows) |
| 3 | `scrum-task-decomposer` | `DOCS.md` + `ARCHITECTURE.md` | `plan/before/NN_<role>_<slug>.md` micro-task files |
| 4a | `infra-cicd-operator` (parallel-safe) | `ARCHITECTURE.md` | `docker-compose.yml`, `Dockerfile`, `.github/workflows/ci-cd.yml`, `.env` |
| 4b | `git-master-conventions` (parallel-safe) | `plan/before/*.md` | `CONTRIBUTING.md`, `.github/pull_request_template.md` |
| 5 | `blueprint-executor-worker` (one per task, parallel in worktrees) | a single `plan/before/NN_*.md` + `DOCS.md` + `ARCHITECTURE.md` | Java/Spring code under `src/`, then moves the consumed task file to `plan/after/` |

Steps 4a and 4b can run concurrently with each other once step 3 is done. Step 5 fan-out: each worker runs with `isolation: "worktree"` so that parallel workers do not collide.

## The automation pipeline (Issue-driven)

A second, **local-driven** pipeline exists for repeating the loop continuously from a single GitHub Issue. It coexists with the human-driven pipeline above and shares the same `plan/before/` + `worktree` conventions.

| # | Actor | Consumes | Produces |
|---|---|---|---|
| 1 | User | business idea | GitHub Issue with label `maestro:auto` (template-enforced) |
| 2 | `maestro-dispatch.yml` (GitHub Actions, no LLM) | issue event | `needs-maestro` label + Issue comment instructing local invocation |
| 3 | `maestro` (`.claude/agents/maestro.md`) — invoked by main session | Issue payload + `DOCS.md` + `ARCHITECTURE.md` | `plan/before/NN_*.md` + `plan/before/manifest.json` |
| 4 | `worker` (`.claude/agents/worker.md`) × N — invoked by main session | a single task file | a single PR labeled `automation:worker` |
| 5 | `gatekeeper.yml` (GitHub Actions, no LLM) | CI + Gemini review on PR | auto-merge or `needs-human` |
| 6 | `auto-rebase.yml` (GitHub Actions, no LLM) | `push: main` event | rebase of open `automation:worker` PRs |

**Coexistence with the human-driven pipeline:**

- The two pipelines share `plan/before/` + `plan/after/` + `reports/`. Numbering is global; Maestro reserves the next available `NN`.
- `blueprint-executor-worker` (human-driven) and `worker` (automation) MAY both have open PRs at the same time; they are distinguished only by the `automation:worker` label.
- `gatekeeper.yml` and `auto-rebase.yml` only act on PRs that carry `automation:worker`. Human-driven PRs are unaffected.
- Kill switch: Repository variable `AUTOMATION_ENABLED=false` disables all three automation workflows without touching the human-driven flow.

Detail: `docs/architecture/automation-pipeline.md`, `docs/architecture/automation-pipeline-workflows.md`, and the user guides under `docs/guides/automation-*.md`.

```
ddd-domain-architect ──► DOCS.md
                            │
                            ▼
                concurrency-architect ──► ARCHITECTURE.md
                                              │
                                              ▼
                                scrum-task-decomposer ──► plan/before/*.md
                                              │
                          ┌───────────────────┼───────────────────┐
                          ▼                   ▼                   ▼
              infra-cicd-operator   git-master-conventions   blueprint-executor-worker × N
                  │                         │                      │  (one worktree each)
                  ▼                         ▼                      ▼
         docker-compose.yml,         CONTRIBUTING.md,          src/main/java/**, src/test/java/**
         Dockerfile,                 .github/pull_request      + plan/before/N.md → plan/after/N.md
         .github/workflows/ci-cd.yml _template.md
```

## Expected directory layout after the pipeline has run

```
.
├── CLAUDE.md                              # generic behavioral rules (already exists)
├── ORCHESTRATION.md                       # this file
├── DOCS.md                                # produced by ddd-domain-architect
├── ARCHITECTURE.md                        # produced by concurrency-architect
├── CONTRIBUTING.md                        # produced by git-master-conventions
├── docker-compose.yml                     # produced by infra-cicd-operator
├── Dockerfile                             # produced by infra-cicd-operator
├── .env                                   # produced by infra-cicd-operator
├── .github/
│   ├── workflows/ci-cd.yml                # produced by infra-cicd-operator
│   └── pull_request_template.md           # produced by git-master-conventions
├── plan/
│   ├── before/NN_<role>_<slug>.md         # produced by scrum-task-decomposer
│   └── after/NN_<role>_<slug>.md          # moved here by blueprint-executor-worker on completion
├── src/main/java/**                       # produced by blueprint-executor-worker
├── src/test/java/**                       # produced by blueprint-executor-worker
└── .claude/
    ├── agents/                            # 6 sub-agent definitions (already exists)
    ├── agent-memory/                      # per-agent memory (already exists)
    └── worktrees/feature-<task-slug>/     # created per worker run
```

## Build / test commands

All commands run from `live-class/`:

- `./gradlew build` — compile and produce the bootJar
- `./gradlew test` — full JUnit 5 suite (Testcontainers integration tests require a running Docker daemon; pure domain unit tests do not)
- `./gradlew test --tests <fqcn>` — run a single test class
- `./gradlew bootRun` — local server (requires PostgreSQL and Redis; Swagger UI at `http://localhost:8080/swagger-ui.html`)

Root-level infrastructure stack uses `docker compose up` / `docker compose down` via the `docker-compose.yml` emitted by `infra-cicd-operator`.

Agent dispatch is invoked via the Task tool with the matching `subagent_type`, or via the `/agents` interface. See `docs/agents/agents.md` for the routing table.

## Worktree convention for workers

`blueprint-executor-worker` is the only agent that writes code, and it runs in isolation. When dispatching a worker, supply:

- `isolation: "worktree"` so the harness creates a temporary worktree
- A path under `../worktrees/feature-<task-slug>` (relative to the repo root), matching the slug of the `plan/before/*.md` task being executed
- The exact path to the one task file the worker should consume — workers must operate on a single task at a time

After a worker finishes, the task file is moved from `plan/before/` to `plan/after/`. Use this as the source of truth for what is done vs. pending.

## Strict rules carried over from agent definitions

These are not negotiable — they are encoded in the individual agent definitions and the orchestrator must enforce them:

1. **Design first.** No application code may be written until both `DOCS.md` and `ARCHITECTURE.md` exist and reflect the latest requirements.
2. **Zero creative deviation for workers.** `blueprint-executor-worker` translates checklists into code with no architectural improvisation. If ambiguity blocks a worker, the worker escalates to the orchestrator rather than guessing.
3. **PR-to-task traceability.** Every PR must reference at least one `plan/before/*.md` task file. This is enforced by `git-master-conventions` via the PR template and CONTRIBUTING.md.
4. **Conventional Commits.** Commit messages follow the convention defined in `CONTRIBUTING.md` once that file exists.
5. **One concern per agent.** Do not ask `ddd-domain-architect` to write code, do not ask `blueprint-executor-worker` to redesign the domain, etc.

## Language convention

Per the parent `~/CLAUDE.md`:

- User-facing replies, conversational text, and clarifying questions → **Korean**
- Technical artifacts (commit messages, planning docs, code comments, design docs above) → **English**

## When to update this file

Update `ORCHESTRATION.md` when:

- The pipeline order changes (an agent is added/removed/reordered)
- The expected output path of an agent changes
- The tech stack is decided more concretely (DB vendor chosen, Java version locked, etc.)
- Build/test commands change (replace the section above)
