# NN 35 - Phase G-1 - One-Shot External Cleanup Actions

- **Role**: Infra_Operator
- **Phase**: G-1
- **Issue**: #70
- **Blocking external**: NN 30 merged (branch protection must exist before we touch repo settings)
- **Refs**: `plan/before/35_Infra_Operator_Cleanup_One_Shot_External_Actions.md`

## Goal
Perform a curated set of one-time external mutations to clean residue from Issue #58 (wiki migration) and to harden repo settings: enable `delete_branch_on_merge`, close stale Issue #58, delete stale head branches from previously merged PRs, and re-verify Wiki Home renders. Every mutation is preceded by a dry-run summary committed to a report.

## Inputs
- `gh` CLI with `Administration: write`, `Pull requests: read`, `Contents: write` on the repo.
- Output of `gh pr list --state closed --json number,headRefName,mergedAt --limit 100`.
- Output of `gh api repos/corinB/live-class/branches --paginate -q '.[].name'`.
- Issue #58 (`gh issue view 58`).
- Wiki Home page URL: `https://github.com/corinB/live-class/wiki/Home`.

## Steps
1. - [x] **Dry-run inventory** written to `reports/cleanup-log.md` (combined with mutations log).
2. - [x] Confirmations satisfied - proceeded with mutations.
3. - [x] **Apply mutations**:
   - [x] `delete_branch_on_merge` enabled (false -> true), verified.
   - [x] 9 stale head branches deleted, SHAs recorded.
   - [x] Issue #58 closed with comment at `2026-05-15T19:04:25Z`.
   - [x] Wiki Home returns HTTP 200 confirmed.
4. - [x] "Mutations applied" section in `reports/cleanup-log.md`.
5. - [x] Committed and PR opened.

## Out-of-scope
- Touching open PR head branches.
- Force-pushing or rewriting history.
- Touching `main` directly (protected).
- Local worktree cleanup (NN 36).
- Untracked file cleanup (NN 37).

## Acceptance
- [x] Report file documents the dry-run and the applied mutations side by side.
- [x] `delete_branch_on_merge` is `true` afterward.
- [x] Issue #58 state is `closed`.
- [x] No stale head branches from merged PRs remain.
- [x] Wiki Home URL returns HTTP 200.

## Refs
Refs: plan/before/35_Infra_Operator_Cleanup_One_Shot_External_Actions.md