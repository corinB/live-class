# NN 34 — Phase F — Required Status Check Audit

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
- [x] 1. Create `.claude/scripts/audit-status-checks.py`
- [x] 2. Create `.github/workflows/status-check-audit.yml`
- [x] 3. Run script locally — exit 0, PASS (3/3 contexts)
- [x] 4. Validate Phases B/C/D/E — all confirmed
- [x] 5. Commit and open PR

## Acceptance
- [x] `.claude/scripts/audit-status-checks.py` exists and runs.
- [x] `.github/workflows/status-check-audit.yml` exists.
- [x] Audit output shows zero items in the `missing` set.
- [x] Report file documents the verification of all four upstream phases.

## Refs
Refs: plan/before/34_Quality_Guardian_Required_Status_Check_Audit.md
