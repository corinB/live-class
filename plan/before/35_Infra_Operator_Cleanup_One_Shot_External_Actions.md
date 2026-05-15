# NN 35 — Phase G-1 — One-Shot External Cleanup Actions

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
1. **Dry-run inventory** (write to `reports/35_Infra_Operator_g1-dryrun_2026-05-16.md`):
   - List current `delete_branch_on_merge` value (`gh api repos/corinB/live-class -q .delete_branch_on_merge`).
   - List all closed-and-merged PRs and their `headRefName`. Intersect with the actual `branches` list. The intersection is the set of stale head branches to delete.
   - Print Issue #58 state.
   - `curl -sI https://github.com/corinB/live-class/wiki/Home` (HTTP status).
2. Get human confirmation **only** by posting the dry-run as an Issue #70 comment and waiting for an explicit `/g1-go` reply — if working as the unattended Worker, treat the absence of `/g1-go` within the same run as halt-and-exit-success. The reply mechanism is documented; the Worker may skip if the inventory shows nothing to do.
3. **Apply mutations** (only if step 2 confirmation present or set is empty):
   - `gh api -X PATCH repos/corinB/live-class -F delete_branch_on_merge=true` — verify with a follow-up GET.
   - For each stale head branch in the intersection set: `git push origin --delete <branch>`. Skip protected branches.
   - `gh issue close 58 --reason completed --comment "Closed as part of #70 cleanup; follow-up issues link migration receipts."`.
   - Refresh Wiki Home: re-run `gh workflow run wiki-sync.yml` and verify HTTP 200 at the Home URL.
4. Append a "Mutations applied" section to the report with exit codes, deleted branches, and the new repo settings JSON.
5. Commit: `chore(cleanup): one-shot external G-1 cleanup` with footer `Refs: plan/before/35_Infra_Operator_Cleanup_One_Shot_External_Actions.md`.

## Out-of-scope
- Touching open PR head branches.
- Force-pushing or rewriting history.
- Touching `main` directly (protected).
- Local worktree cleanup (NN 36).
- Untracked file cleanup (NN 37).

## Acceptance
- Report file documents the dry-run and the applied mutations side by side.
- `delete_branch_on_merge` is `true` afterward.
- Issue #58 state is `closed`.
- No stale head branches from merged PRs remain in `gh api repos/corinB/live-class/branches`.
- Wiki Home URL returns HTTP 200.

## Refs
Refs: plan/before/35_Infra_Operator_Cleanup_One_Shot_External_Actions.md
