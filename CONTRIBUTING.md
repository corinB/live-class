<!-- 브랜치 전략·커밋 컨벤션·PR 절차·CI/CD 워크플로우 규약 영문 요약 -->
<!-- Role: branch, commit, PR, and CI/CD conventions for contributors and agents -->
# CONTRIBUTING.md — Contributing Guide

> **Detail (Korean)**: https://github.com/corinB/live-class/wiki/ko-contributing-detail

This document summarizes contribution conventions. Full Korean detail at the link above.

## Branch Strategy (GitHub Flow)

- `main` is always deployable. All changes via **PR + Squash Merge** only.
- Direct push to `main` is forbidden.

**Branch naming**: `<type>/task-NN-<slug>`
- `NN` must match the two-digit prefix of `wiki-src/plan-before/NN_*.md`.
- `slug` is lowercase kebab-case.

**Allowed types**: `feature` | `fix` | `refactor` | `perf` | `test` | `docs` | `chore` | `ci`

**One branch = one task = one PR.** Never bundle multiple tasks into one branch.

After merge, delete the feature branch immediately.

## Commit Convention (Conventional Commits)

Format: `<type>(<scope>): <subject>`

- Subject: ≤50 chars, English imperative, no trailing period.
- Body: Korean allowed.
- Footer (required): `Refs: wiki-src/plan-before/NN_<Role>_<Slug>.md`

**Allowed types**: `feat` | `fix` | `refactor` | `perf` | `test` | `docs` | `build` | `ci` | `chore` | `style` | `revert`

**Scope examples**: `class` | `enrollment` | `user` | `infra` | `ci` | `auth` | `waitlist` | `config`

**Footer for automation PRs** also includes `Refs: #<issue-number>`.

## PR Process

1. Confirm `wiki-src/plan-before/NN_*.md` task file.
2. Branch from `main`: `feature/task-NN-<slug>`.
3. Commit following Conventional Commits.
4. `./gradlew test` must pass.
5. Open PR with checklist from `.github/pull_request_template.md`.
6. Address Gemini AI review (P0/P1 findings required before merge).
7. Squash Merge → delete branch.

## CI/CD Workflows

| File | Trigger | Purpose |
|---|---|---|
| `.github/workflows/ci.yml` | PR + push to main | `./gradlew test --build-cache`. Skips doc-only paths. |
| `.github/workflows/cd.yml` | push to main (code paths only) | Docker image push → EC2 deploy. Never runs on PRs. |

Branch protection requires `ci.yml`'s `build-test` status check to pass before merge.

## Automation Pipeline PRs

Issues labeled `maestro:auto` → Maestro creates `wiki-src/plan-before/NN_*.md` → Worker opens PR labeled `automation:worker`.

- Gatekeeper auto-merges when CI green + Gemini P0=0, P1=0.
- Auto-rebase bot rebases open automation PRs when main advances.
- Kill switch: repository variable `AUTOMATION_ENABLED=false`.

Detail: `docs/architecture/automation-pipeline.md`, `docs/guides/automation-*.md`.

## Absolute Prohibitions

- No direct push to `main`.
- No `git push --force` or `git reset --hard`.
- No merging with incomplete PR checklist.
- No hardcoded passwords, API keys, or DB URLs.

## Cross-references

- Domain design: [DOCS.md](./DOCS.md)
- Concurrency architecture: [ARCHITECTURE.md](./ARCHITECTURE.md)
- Pipeline policy: [ORCHESTRATION.md](./ORCHESTRATION.md)
