<!-- 라이브 강의 수강신청 시스템의 멀티 에이전트 파이프라인 정책 영문 요약 -->
<!-- Role: multi-agent pipeline policy — stages, deliverables, blocking rules, automation pipeline -->
# ORCHESTRATION.md — Multi-Agent Pipeline Policy

> **Detail (Korean)**: https://github.com/corinB/live-class/wiki/ko-orchestration-detail

This document summarizes pipeline policy. Full Korean detail at the link above.

## What This Workspace Is

An **orchestrator workspace**: the main Claude Code session dispatches sub-agents rather than writing Spring Boot code directly. Application code lives under `live-class/` (Spring Boot 4.0.6, Java 21, modular monolith).

## Human-Driven Pipeline (6 Stages)

Run in order — **do not skip steps**. Each agent verifies required inputs exist.

| # | Agent | Consumes | Produces |
|---|---|---|---|
| 1 | `ddd-domain-architect` | Business requirements | `DOCS.md` |
| 2 | `concurrency-architect` | `DOCS.md` | `ARCHITECTURE.md` |
| 3 | `scrum-task-decomposer` | `DOCS.md` + `ARCHITECTURE.md` | `plan/before/NN_*.md` task files |
| 4a | `infra-cicd-operator` (parallel-safe) | `ARCHITECTURE.md` | `docker-compose.yml`, `Dockerfile`, CI/CD workflows |
| 4b | `git-master-conventions` (parallel-safe) | `plan/before/*.md` | `CONTRIBUTING.md`, PR template |
| 5 | `blueprint-executor-worker` × N | Single `plan/before/NN_*.md` | Java/Spring code in isolated worktree; moves task to `plan/after/` |

Steps 4a and 4b run concurrently. Step 5 workers run with `isolation: "worktree"`.

## Automation Pipeline (Issue-Driven)

| # | Actor | Produces |
|---|---|---|
| 1 | User | GitHub Issue with `maestro:auto` label |
| 2 | `maestro-dispatch.yml` (no LLM) | `needs-maestro` label + comment |
| 3 | `maestro` agent | `plan/before/NN_*.md` + `manifest.json` |
| 4 | `worker` agent × N | PR labeled `automation:worker` |
| 5 | `gatekeeper.yml` | Auto-merge (CI green + Gemini P0=0, P1=0) or `needs-human` label |
| 6 | `auto-rebase.yml` | Rebase open automation PRs when main advances |

**Coexistence**: both pipelines share `plan/before/` + `plan/after/` + `reports/`. Numbering is global.
**Kill switch**: repository variable `AUTOMATION_ENABLED=false` disables all 3 automation workflows.

## Strict Rules

1. No application code until both `DOCS.md` and `ARCHITECTURE.md` exist.
2. Workers translate checklists into code — no architectural improvisation. Escalate ambiguity.
3. Every PR references at least one `plan/before/NN_*.md` task file.
4. Conventional Commits as defined in `CONTRIBUTING.md`.
5. One concern per agent — do not cross-task agents.

## Build / Test Commands

All commands run from `live-class/`:

```
./gradlew build          # compile + bootJar
./gradlew test           # full JUnit 5 suite (Testcontainers for integration tests)
./gradlew bootRun        # local server (needs PostgreSQL + Redis)
docker compose up -d     # infra stack (profiles: db, redis, back, front)
```

## Worktree Convention

Workers run with `isolation: "worktree"` under `../worktrees/feature-<task-slug>`. After completion, the task file moves from `plan/before/` to `plan/after/` (canonical "done" marker).

## Cross-references

- Domain design: [DOCS.md](./DOCS.md)
- Concurrency architecture: [ARCHITECTURE.md](./ARCHITECTURE.md)
- Branch, commit, PR conventions: [CONTRIBUTING.md](./CONTRIBUTING.md)
