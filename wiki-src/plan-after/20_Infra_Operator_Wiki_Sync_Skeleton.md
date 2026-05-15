# Phase 1 — `wiki-src/` Skeleton + Wiki Sync Workflow + Smoke Test

- **NN:** 20
- **Role:** Infra_Operator
- **Phase:** 1 / 8
- **Issue:** [#58](https://github.com/corinB/live-class/issues/58)
- **Blocking external:** none
- **Depends on:** []

## Goal

Stand up the source-of-truth staging directory `wiki-src/` and the GitHub Action `.github/workflows/wiki-sync.yml` that mirrors it to `corinB/live-class.wiki.git` on every `main` push affecting `wiki-src/**`. Prove the pipeline end-to-end with one trivial seed page (`wiki-src/Home.md`) that lands on the live Wiki.

## Inputs

- Issue #58 body (Strict delegation, Deliverable §1–2, Phase 1 section).
- `corinB/live-class` repo settings — Wiki must be enabled. If disabled, halt with a comment on Issue #58 asking the operator to enable Wiki and re-dispatch.
- Default `GITHUB_TOKEN` (no PAT, no third-party action that needs extra secrets).

## Steps

- [x] 1. Confirm Wiki is enabled — `gh api repos/corinB/live-class --jq .has_wiki`. Result: `true`.
- [x] 2. Create `wiki-src/` with a `Home.md` (one-paragraph English seed referencing the Issue) and a `.gitkeep` placeholder under `wiki-src/ko/` so the language sub-tree exists for Phases 2–5.
- [x] 3. Author `.github/workflows/wiki-sync.yml`:
   - `on: push` to `main` with `paths: ['wiki-src/**']`, plus `workflow_dispatch`.
   - Clone the Wiki via `https://x-access-token:${{ secrets.GITHUB_TOKEN }}@github.com/corinB/live-class.wiki.git`.
   - Mirror `wiki-src/` into the clone with `rsync -a --delete wiki-src/ <wiki-clone>/` so deletions propagate (full mirror).
   - Commit + push using `github-actions[bot]` identity. No-op when diff is empty.
   - Concurrency group `wiki-sync` with `cancel-in-progress: false`.
- [ ] 4. Open one PR for Phase 1 only — title `feat(wiki): add wiki-src skeleton and sync workflow`. Body in Korean per project convention. Footer `Refs: plan/before/20_Infra_Operator_Wiki_Sync_Skeleton.md`.
- [ ] 5. After merge, verify smoke test: `gh run list --workflow=wiki-sync.yml --limit 1` shows success, and `git ls-remote https://github.com/corinB/live-class.wiki.git` returns a HEAD whose tree contains `Home.md`.
- [ ] 6. If the smoke push fails, do **not** widen scope — fix only the workflow file in a follow-up commit on the same PR branch.

## Out of scope

- Migrating any real content (Phases 2–5 do that).
- Editing `CLAUDE.md`, `ARCHITECTURE.md`, `DOCS.md`, `CONTRIBUTING.md`, `ORCHESTRATION.md`, `AGENTS-SKILLS-HARNESS.md`, `context.yaml`.
- Touching `.claude/hooks/**`, `.claude/agents/**`, `.claude/settings*.json`.
- Touching `live-class/`, `docs/`, `reports/`, `front/`, `plan/after/`.
- Touching any other `.github/workflows/*.yml`.

## Acceptance

- `gh pr list --search "is:merged head:chore/wiki-skeleton"` returns one merged PR (or equivalent branch name).
- `ls wiki-src/Home.md` exists on `main`.
- `.github/workflows/wiki-sync.yml` exists and uses only `GITHUB_TOKEN`.
- `gh run list --workflow=wiki-sync.yml --limit 1 --json conclusion -q '.[0].conclusion'` returns `success`.
- `https://github.com/corinB/live-class/wiki/Home` renders the seed page.

## References

- Issue #58 — Goal, Strict delegation, Deliverables §1–2, Phase 1.
- Refs: plan/before/20_Infra_Operator_Wiki_Sync_Skeleton.md
