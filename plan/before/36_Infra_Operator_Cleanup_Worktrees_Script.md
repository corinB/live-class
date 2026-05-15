# NN 36 — Phase G-2 — Cleanup Worktrees Script

- **Role**: Infra_Operator
- **Phase**: G-2
- **Issue**: #70
- **Blocking external**: none (after NN 29)
- **Refs**: `plan/before/36_Infra_Operator_Cleanup_Worktrees_Script.md`

## Goal
Ship a conservative, dry-run-by-default shell script that removes git worktrees whose tracking branch's PR has already been merged into `main`. Operator runs it locally on demand. It writes the pre-removal commit SHA of each worktree to `reports/cleanup-log.md` so recovery via `git reflog` is straightforward.

## Inputs
- `git worktree list --porcelain`.
- `gh pr list --state merged --json headRefName,number --limit 200`.
- `reports/cleanup-log.md` (append-only audit trail, create if missing).

## Steps
1. Create `.claude/scripts/cleanup-worktrees.sh`:
   - First line: `# 머지된 PR 의 worktree 만 보수적으로 정리하는 스크립트 (default: --dry-run)`.
   - Parse flags: `--dry-run` (default true), `--execute` (sets dry-run false), `--help`.
   - Build the merged-head set from `gh pr list ... --json headRefName -q '.[].headRefName'`.
   - For each entry in `git worktree list --porcelain`: extract `worktree`, `HEAD`, `branch`. If `branch` (after stripping `refs/heads/`) is in the merged-head set AND `HEAD` matches a commit that's reachable from `origin/main` (`git merge-base --is-ancestor <HEAD> origin/main`), mark for removal.
   - For each marked: print `WOULD REMOVE: <path> (branch=<x>, sha=<y>)`. If `--execute`, run `git worktree remove --force <path>` and append a line to `reports/cleanup-log.md`:
     ```
     2026-05-16 cleanup-worktrees: removed <path> branch=<x> sha=<y>
     ```
   - Refuse to remove the main repo worktree or any worktree whose path is the current `pwd`.
   - Exit 0 on success, 1 on partial failure (continue but report).
2. Test on a known-merged worktree (use a throwaway one if needed). Capture dry-run and execute output in `reports/36_Infra_Operator_cleanup-worktrees_2026-05-16.md`.
3. Commit: `chore(scripts): add cleanup-worktrees.sh` with footer `Refs: plan/before/36_Infra_Operator_Cleanup_Worktrees_Script.md`.

## Out-of-scope
- Removing unmerged worktrees.
- Removing remote branches (G-1).
- Untracked file cleanup (G-3).
- Wiring into automation (G-5).

## Acceptance
- `.claude/scripts/cleanup-worktrees.sh` exists, is executable, defaults to `--dry-run`.
- `--help` prints usage.
- Running with `--execute` on a merged worktree removes it and appends an entry to `reports/cleanup-log.md`.
- Running against a non-merged worktree never touches it.

## Refs
Refs: plan/before/36_Infra_Operator_Cleanup_Worktrees_Script.md
