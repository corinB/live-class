# NN 30 — Phase B — Branch Protection Setup

- **Role**: Infra_Operator
- **Phase**: B
- **Issue**: #70
- **Blocking external**: requires NN 29 baseline merged
- **Refs**: `plan/before/30_Infra_Operator_Branch_Protection_Setup.md`

## Goal
Introduce `main` branch protection on `corinB/live-class` for the first time. The configuration must be strict: required pull-request reviews, required status checks (gatekeeper + CI + gemini-review + link-checker), force-push and deletion disabled, admin enforcement on, and a CODEOWNERS file owning everything. This blocks accidental direct-to-`main` pushes from any agent.

## Inputs
- `gh` CLI with token having `Administration: write` on the repo.
- Existing workflow names: `CI / build-and-test`, `gatekeeper / decide`, `gemini-review / review`, `link-checker / link-check` (Worker verifies actual job names with `gh api repos/corinB/live-class/actions/runs ... -q '.workflow_runs[].name'`).
- `CODEOWNERS` does not yet exist; create it at `.github/CODEOWNERS`.
- Issue #70 success criteria: `required_status_checks / required_pull_request_reviews / enforce_admins / allow_force_pushes` must match the plan.

## Steps
1. Enumerate current status-check job names by inspecting the latest 5 successful `main` workflow runs: `gh api repos/corinB/live-class/actions/runs --jq '.workflow_runs[] | select(.head_branch=="main") | .name' | sort -u`. Record the exact set.
2. Create `.github/CODEOWNERS` owning `*` to `@corinB`. Single line: `* @corinB`. First line is a comment `# 모든 변경은 corinB 리뷰가 필요한 기본 CODEOWNERS`.
3. PUT branch protection via `gh api -X PUT repos/corinB/live-class/branches/main/protection` with body:
   ```json
   {
     "required_status_checks": {
       "strict": true,
       "contexts": ["build-and-test", "gatekeeper", "gemini-review", "link-check"]
     },
     "enforce_admins": true,
     "required_pull_request_reviews": {
       "required_approving_review_count": 1,
       "dismiss_stale_reviews": true,
       "require_code_owner_reviews": true
     },
     "restrictions": null,
     "allow_force_pushes": false,
     "allow_deletions": false,
     "required_linear_history": false,
     "required_conversation_resolution": true
   }
   ```
   Replace contexts with the exact names observed in step 1 if they differ.
4. Verify with `gh api repos/corinB/live-class/branches/main/protection`. Capture the JSON to `reports/30_Infra_Operator_branch-protection_2026-05-16.md`.
5. Smoke test: attempt `git push origin HEAD:main` from a feature branch — must fail with `protected branch` error. Document the failure log in the report.
6. Commit: `chore(ci): enable branch protection on main` with footer `Refs: plan/before/30_Infra_Operator_Branch_Protection_Setup.md`.

## Out-of-scope
- Loosening protection for any role.
- Changing required-check names (Phase F audit will validate alignment).
- Updating workflows themselves.

## Acceptance
- `gh api repos/corinB/live-class/branches/main/protection` returns 200.
- Response JSON has `enforce_admins.enabled: true`, `allow_force_pushes.enabled: false`, `required_pull_request_reviews.required_approving_review_count: 1`, `required_pull_request_reviews.require_code_owner_reviews: true`, and `required_status_checks.contexts` includes all four names.
- `.github/CODEOWNERS` exists on `main` (via the merged PR).
- Direct push to `main` is rejected.
- Report file under `reports/` documents the verification.

## Refs
Refs: plan/before/30_Infra_Operator_Branch_Protection_Setup.md
