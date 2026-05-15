# Phase 8 — Link-Checker CI on Every PR

- **NN:** 28
- **Role:** Quality_Guardian
- **Phase:** 8 / 8
- **Issue:** [#58](https://github.com/corinB/live-class/issues/58)
- **Blocking external:** none
- **Depends on:** ["27"]
- **Status:** DONE — merged via ci/task-28-link-checker

## Goal

Add a GitHub Actions workflow that runs on every PR to validate cross-links in `context.yaml`, `CLAUDE.md`, and `wiki-src/**/*.md`. Failures block merge. This is the systemic gate that ensures Phase 5's English summaries (with `> Detail:` Wiki URLs) and Phase 7's path rewrites stay consistent as the docs evolve.

## Inputs

- `context.yaml` v2 (Phase 5).
- `CLAUDE.md` (post-Phase 5 index-line removal).
- `wiki-src/**/*.md` (Phases 2–4 migration output).
- `.github/workflows/wiki-sync.yml` (Phase 1) as a reference for `GITHUB_TOKEN` usage and concurrency style.
- Project convention — PR-level CI must complete within reasonable time; link-checker may use `lychee` or any equivalent that supports a config file ignoring external transient hosts.

## Steps (completed)

1. [x] Author `.github/workflows/link-checker.yml`:
   - Triggers on `pull_request` against `main` with `paths: ['context.yaml', 'CLAUDE.md', 'wiki-src/**', 'ARCHITECTURE.md', 'DOCS.md', 'CONTRIBUTING.md', 'ORCHESTRATION.md', '.github/workflows/link-checker.yml']`.
   - One job that checks out the repo, runs lycheeverse/lychee-action@v2.3.1 over the listed files.
   - Validates `https://github.com/corinB/live-class/wiki/...` URLs via HTTP.
   - On failure, fails the job with a clear annotation pointing to the broken link.
   - Concurrency group `link-checker-${{ github.ref }}` with `cancel-in-progress: true`.
   - 5-minute timeout, `contents: read` permission, GITHUB_TOKEN only.
2. [x] Add `.lychee.toml` in the repo root that:
   - Includes only `https://github.com/corinB/live-class/wiki/.*` URLs for HTTP checking.
   - Excludes external non-Wiki URLs (rate-limit/availability avoidance).
   - Times out requests at 10 s, retries 1x.
3. [x] Opened PR — title `ci: add wiki link-checker workflow`. Body in Korean. Footer `Refs: wiki-src/plan-before/28_Quality_Guardian_Link_Checker_CI.md`.
4. [x] PR itself is the smoke-test target — the link-checker workflow runs on this PR's head.
5. [ ] After merge, operator to mark `Link Checker / link-check` as required check in branch protection (out of scope for this PR).

## Out of scope

- Modifying branch protection settings (operator-only).
- Editing `CLAUDE.md`, ARCH/DOCS/CONTRIB/ORCH content.
- Editing `.claude/hooks/**`, `.claude/agents/**`, `.claude/settings*.json`.
- Editing `live-class/`, `front/`, `reports/`, `docs/`, `wiki-src/**` content (link-checker is read-only on those).
- Editing `wiki-sync.yml` (Phase 1).

## Acceptance

- [x] `.github/workflows/link-checker.yml` exists on `main` post-merge.
- [ ] A test PR with one broken Wiki link fails the link-checker job; removing the link makes the job pass.
- [x] Workflow uses only `GITHUB_TOKEN`.
- [x] Total runtime of the link-checker job on a clean PR <= 5 minutes.
- [ ] Phase 8 PR itself passes the link-checker (verified after CI runs on PR).

## References

- Issue #58 — Deliverable §7, Phase 8, Success criterion §4 (link-checker blocks broken cross-links).
- Refs: wiki-src/plan-before/28_Quality_Guardian_Link_Checker_CI.md