# NN 37 — Phase G-3 — Cleanup Untracked Leftovers Script

- **Role**: Infra_Operator
- **Phase**: G-3
- **Issue**: #70
- **Blocking external**: none (after NN 29)
- **Refs**: `plan/before/37_Infra_Operator_Cleanup_Untracked_Leftovers_Script.md`

## Goal
Ship a script that proposes deletion of well-known untracked residue paths: `.clone/worktrees/`, `reports/14_*`, `reports/20_*`, `reports/27_*`, and orphaned `docs/engineering/` (after NN 38 moves it). Dry-run by default. Every proposed deletion records the file's last-modified time and size in `reports/cleanup-log.md`.

## Inputs
- `git status --porcelain` for the untracked file list.
- Allowlist of glob patterns (hard-coded in the script for review).
- `reports/cleanup-log.md` (append-only).

## Steps
1. Create `.claude/scripts/cleanup-untracked-leftovers.sh`:
   - First line: `# .clone/, reports/14_*, reports/20_*, reports/27_*, docs/engineering/* 같은 알려진 잔재만 정리하는 스크립트 (default: --dry-run)`.
   - Flags: `--dry-run` (default), `--execute`, `--help`.
   - Allowlist (hard-coded array):
     ```
     .clone/worktrees/
     reports/14_*
     reports/20_*
     reports/27_*
     docs/engineering/
     ```
   - For each pattern, expand against the actual untracked set from `git status --porcelain -u`. If the path is not in the untracked set, skip with a "not present" note.
   - For each candidate: print `WOULD DELETE: <path> (mtime=<x>, size=<y>)`. If `--execute`, `rm -rf <path>` (only paths in the allowlist) and append:
     ```
     2026-05-16 cleanup-untracked: removed <path> mtime=<x> size=<y>
     ```
   - Never delete tracked files. Sanity check: `git ls-files <path>` must be empty before delete.
   - Refuse paths starting with `live-class/`, `wiki-src/`, `.claude/`, `.github/`.
2. Test in dry-run and execute mode. Save transcript to `reports/37_Infra_Operator_cleanup-untracked-leftovers_2026-05-16.md`.
3. Commit: `chore(scripts): add cleanup-untracked-leftovers.sh` with footer `Refs: plan/before/37_Infra_Operator_Cleanup_Untracked_Leftovers_Script.md`.

## Out-of-scope
- Touching anything tracked.
- Removing `plan/before/14_*` (PR #56 ongoing).
- Migrating `docs/engineering/context.md` content (NN 38).
- Workflow integration (NN 39).

## Acceptance
- Script exists, executable, dry-run default.
- Dry run lists only untracked allowlisted paths.
- `--execute` deletes them and logs to `reports/cleanup-log.md`.
- Tracked-file safety check prevents accidental deletion (test: try to delete a tracked path explicitly — script refuses).

## Refs
Refs: plan/before/37_Infra_Operator_Cleanup_Untracked_Leftovers_Script.md
