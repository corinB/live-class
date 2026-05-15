# NN 34 -- Phase F -- Required Status Check Audit

- **Role**: Quality_Guardian
- **Phase**: F
- **Issue**: #70
- **Blocking external**: NN 30, 31, 32, 33 merged (verifies all guards together)
- **Refs**: `plan/before/34_Quality_Guardian_Required_Status_Check_Audit.md`

## Goal
Verify end-to-end that the names of jobs published by every CI workflow exactly match the `contexts` array in `branches/main/protection`. The most common branch-protection failure mode is silent: a workflow gets renamed and protection accepts merges with a missing check. This audit fails loudly if there is any divergence.

## Inputs
- `gh api repos/corinB/live-class/branches/main/protection` (read-only).
- `.github/workflows/*.yml` files (parse for `jobs.*.name` or job id).
- `gh api repos/corinB/live-class/actions/runs` for ground-truth recent run names.

## Steps
1. Create `.claude/scripts/audit-status-checks.py`:
   - Reads `branches/main/protection` and extracts the `required_status_checks.contexts` set.
   - Walks `.github/workflows/*.yml` and collects every job's published check name (`jobs.<key>.name` if set, else key).
   - Computes three sets: `required intersect published` (good), `required - published` (missing -- protection references a name nothing publishes, fatal), `published - required` (extra -- informational, not fatal).
   - Exits 2 if `missing` is non-empty.
   - First line: `# branch protection contexts 와 실제 workflow job 이름의 정합성을 검사하는 audit`.
2. Run the script locally and on CI. On CI, wire it into a new `.github/workflows/status-check-audit.yml` workflow:
   - Triggers: `push` to `main` and `workflow_dispatch`.
   - Single job runs the audit and fails on missing context.
3. Manually trigger; capture output into `reports/34_Quality_Guardian_Required_Status_Check_Audit_2026-05-16.md`.
4. Validate Phases B/C/D/E together:
   - Confirm `branches/main/protection` (from NN 30) is still in place.
   - Confirm new hooks from NN 31, 32 are registered in `.claude/settings.json`.
   - Confirm `context-drift-cron.yml` (NN 33) is in `gh workflow list`.
   - Append each verification to the report file.
5. Commit: `ci: required status check audit (F)` with footer `Refs: wiki-src/plan-before/34_Quality_Guardian_Required_Status_Check_Audit.md`.

## Action Items (Checklist)
- [x] `.claude/scripts/audit-status-checks.py` written and passes locally (exit 0)
- [x] `.github/workflows/status-check-audit.yml` written
- [x] Phase B (NN 30) branch protection verified
- [x] Phase C (NN 31) context.yaml guard hooks verified
- [x] Phase D (NN 32) wiki-pair-sync hooks + workflow verified
- [x] Phase E (NN 33) context-drift-cron.yml + cancel-in-progress verified
- [x] `wiki-src/ko/reports/34_Quality_Guardian_Required_Status_Check_Audit.md` written
- [x] `reports/34_Quality_Guardian_Required_Status_Check_Audit_2026-05-16.md` written
- [x] Plan transitioned to plan/after

## Out-of-scope
- Modifying branch protection (would conflict with NN 30 ownership).
- Renaming workflow jobs.
- Cleanup phases.

## Acceptance
- `.claude/scripts/audit-status-checks.py` exists and runs.
- `.github/workflows/status-check-audit.yml` exists and is green on `main`.
- Audit output shows zero items in the `missing` set.
- Report file documents the verification of all four upstream phases.

## Refs
Refs: plan/before/34_Quality_Guardian_Required_Status_Check_Audit.md
