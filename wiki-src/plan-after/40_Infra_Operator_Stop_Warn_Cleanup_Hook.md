# NN 40 -- Phase G-6 -- Stop Warn Cleanup Hook

- **Role**: Infra_Operator
- **Phase**: G-6
- **Issue**: #70
- **Blocking external**: NN 36, 37 merged (hook reads the same allowlists / output format)
- **Refs**: `wiki-src/plan-before/40_Infra_Operator_Stop_Warn_Cleanup_Hook.md`

## Goal
Add a non-blocking `Stop` hook that, when a Claude session ends, checks git worktree count and
merged branches, then prints a concise summary if anything matches. Pure advisory: no deletions,
no blocks, just a hint so the operator notices residue at the moment they finish work.
Escape hatch: `CLEANUP_HINT_OFF=1`.

## Inputs
- `.claude/settings.json` (Stop hook registry).

## Steps
1. Create `.claude/hooks/stop-warn-cleanup-candidates.sh`:
   - First line: `# 세션 종료 시점에 cleanup 가능한 worktree/branch 후보 개수를 안내하는 비차단 Stop 훅.`
   - Honors `CLEANUP_HINT_OFF=1` (silent exit 0).
   - Counts extra worktrees (`git worktree list`) and merged local branches.
   - If either count > 0, prints to stderr a short summary block.
   - Always exit 0.
2. Register in `.claude/settings.json` under `hooks.Stop` (after existing three entries).
3. Smoke-test: verify behavior on (a) 0/0 -- no output, (b) worktrees > 0 -- message, (c) CLEANUP_HINT_OFF=1 -- silent.
4. Save report to `reports/40_Infra_Operator_stop-warn-cleanup_2026-05-16.md`.
5. Commit: `chore(hooks): stop-warn-cleanup-candidates (G-6)`.

## Out-of-scope
- Performing deletions.
- Blocking session end on any condition.
- Adding new cleanup categories beyond worktrees/merged branches.

## Acceptance
- [x] New hook file present and registered in `.claude/settings.json`.
- [x] On a clean repo (0 worktrees, 0 merged branches), session-end produces no extra output.
- [x] On a repo with extra worktrees or merged branches, session-end prints the hint block.
- [x] `CLEANUP_HINT_OFF=1` silences output entirely.
- [x] Hook never exits non-zero.
- [x] JSON valid after settings.json edit.

## Refs
Refs: wiki-src/plan-before/40_Infra_Operator_Stop_Warn_Cleanup_Hook.md