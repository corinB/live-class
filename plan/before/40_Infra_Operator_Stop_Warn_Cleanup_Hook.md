# NN 40 — Phase G-6 — Stop Warn Cleanup Hook

- **Role**: Infra_Operator
- **Phase**: G-6
- **Issue**: #70
- **Blocking external**: NN 36, 37 merged (hook reads the same allowlists / output format)
- **Refs**: `plan/before/40_Infra_Operator_Stop_Warn_Cleanup_Hook.md`

## Goal
Add a non-blocking `Stop` hook that, when a Claude session ends, runs the two cleanup scripts in `--dry-run` and prints a concise summary if anything matches. Pure advisory: no deletions, no blocks, just a hint so the operator notices residue at the moment they finish work. Escape hatch: `CLEANUP_HINT_OFF=1`.

## Inputs
- `.claude/scripts/cleanup-worktrees.sh` (NN 36).
- `.claude/scripts/cleanup-untracked-leftovers.sh` (NN 37).
- `.claude/settings.json` (Stop hook registry).

## Steps
1. Create `.claude/hooks/stop-warn-cleanup-candidates.sh`:
   - First line: `# 세션 종료 시 머지된 worktree·잔재가 있으면 안내만 출력하는 Stop 훅 (비차단)`.
   - Honors `CLEANUP_HINT_OFF=1` (silent exit 0).
   - Runs both cleanup scripts with `--dry-run`, captures stdout.
   - If either produced any `WOULD REMOVE` or `WOULD DELETE` line, print to stderr a short summary block:
     ```
     [cleanup-hint] N worktree(s) + M untracked path(s) ready to clean.
     Run: .claude/scripts/cleanup-worktrees.sh && .claude/scripts/cleanup-untracked-leftovers.sh
     Or set CLEANUP_HINT_OFF=1 to silence.
     ```
   - Always exit 0.
2. Register in `.claude/settings.json` under `hooks.Stop`. Use the standard matcher pattern (`""` if all-Stop, or a specific pattern matching other Stop hooks already registered).
3. Smoke-test: trigger a fake Stop event by running the hook script directly with empty stdin and verify behavior on (a) clean repo (no output) and (b) repo with a known orphan untracked file in the allowlist (output present).
4. Save logs to `reports/40_Infra_Operator_stop-warn-cleanup_2026-05-16.md`.
5. Commit: `chore(harness): add Stop cleanup-hint hook` with footer `Refs: plan/before/40_Infra_Operator_Stop_Warn_Cleanup_Hook.md`.

## Out-of-scope
- Performing deletions.
- Blocking session end on any condition.
- Adding new cleanup categories beyond what NN 36/37 cover.

## Acceptance
- New hook file present and registered in `.claude/settings.json`.
- On a clean repo, session-end produces no extra output.
- On a repo with a known orphan in the allowlist, session-end prints the hint block once.
- `CLEANUP_HINT_OFF=1` silences output entirely.
- Hook never exits non-zero (verified by inspecting hook behavior under failure injection).

## Refs
Refs: plan/before/40_Infra_Operator_Stop_Warn_Cleanup_Hook.md
