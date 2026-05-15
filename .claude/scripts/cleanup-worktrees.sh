#!/usr/bin/env bash
# 머지된 PR 의 worktree 만 보수적으로 정리하는 스크립트 (default: --dry-run)
set -euo pipefail

# ---------------------------------------------------------------------------
# Flags
# ---------------------------------------------------------------------------
DRY_RUN=true

print_usage() {
  cat <<'USAGE'
Usage: cleanup-worktrees.sh [--dry-run] [--execute] [--help]

  --dry-run   (default) Print what would be removed. No changes made.
  --execute   Actually remove merged worktrees and append to reports/cleanup-log.md.
  --help      Show this message.

Only worktrees whose branch HEAD is an ancestor of origin/main AND whose PR
has been merged are eligible for removal.
The main repo worktree and the current pwd are always skipped.
USAGE
}

for arg in "$@"; do
  case "$arg" in
    --dry-run)  DRY_RUN=true ;;
    --execute)  DRY_RUN=false ;;
    --help|-h)  print_usage; exit 0 ;;
    *)
      echo "Unknown argument: $arg" >&2
      print_usage >&2
      exit 1
      ;;
  esac
done

# ---------------------------------------------------------------------------
# Locate repo root
# ---------------------------------------------------------------------------
REPO_ROOT=$(git rev-parse --show-toplevel 2>/dev/null || true)
if [ -z "$REPO_ROOT" ]; then
  echo "Error: not in a git repository." >&2
  exit 1
fi

CURRENT_PWD=$(pwd)

# ---------------------------------------------------------------------------
# Refresh remote tracking refs
# ---------------------------------------------------------------------------
echo "==> Fetching origin (--prune) ..."
git -C "$REPO_ROOT" fetch --prune origin 2>&1 | sed 's/^/    /'

# ---------------------------------------------------------------------------
# Build merged-head set from GitHub PR list
# ---------------------------------------------------------------------------
echo "==> Querying merged PRs from GitHub ..."
if ! command -v gh &>/dev/null; then
  echo "Error: 'gh' CLI not found. Install GitHub CLI and run 'gh auth login'." >&2
  exit 1
fi

# headRefName of every merged PR (up to 200)
merged_heads=$(gh pr list \
  --state merged \
  --json headRefName \
  --limit 200 \
  --jq '.[].headRefName' \
  2>/dev/null || true)

if [ -z "$merged_heads" ]; then
  echo "Warning: no merged PRs found (or gh query failed). Nothing to remove." >&2
fi

# Helper: check if a branch name is in the merged set
is_merged_pr() {
  local branch="$1"
  echo "$merged_heads" | grep -qxF "$branch"
}

# ---------------------------------------------------------------------------
# Parse worktree list
# ---------------------------------------------------------------------------
echo
echo "==> Scanning worktrees ..."
echo

partial_failure=0

# git worktree list --porcelain emits blocks separated by blank lines:
#   worktree <path>
#   HEAD <sha>
#   branch refs/heads/<name>   (or "detached")
#   [bare]

parse_and_process() {
  local wt_path="" sha="" branch=""

  process_entry() {
    [ -z "$wt_path" ] && return

    # Skip main worktree
    if [ "$wt_path" = "$REPO_ROOT" ]; then
      printf "SKIP (main worktree)    : %s\n" "$wt_path"
      return
    fi

    # Skip current pwd
    if [ "$wt_path" = "$CURRENT_PWD" ]; then
      printf "SKIP (current pwd)      : %s\n" "$wt_path"
      return
    fi

    # Skip detached / bare / no-branch worktrees
    if [ -z "$branch" ]; then
      printf "SKIP (no branch/detached): %s\n" "$wt_path"
      return
    fi

    # Check merged PR
    if ! is_merged_pr "$branch"; then
      printf "SKIP (not merged PR)    : %s  branch=%s\n" "$wt_path" "$branch"
      return
    fi

    # Verify HEAD is ancestor of origin/main (double-safety)
    if ! git -C "$REPO_ROOT" merge-base --is-ancestor "$sha" origin/main 2>/dev/null; then
      printf "SKIP (not ancestor of origin/main): %s  branch=%s  sha=%s\n" \
        "$wt_path" "$branch" "$sha"
      return
    fi

    # Eligible for removal
    if $DRY_RUN; then
      printf "WOULD REMOVE            : %s  (branch=%s, sha=%s)\n" \
        "$wt_path" "$branch" "$sha"
    else
      printf "REMOVING                : %s  (branch=%s, sha=%s)\n" \
        "$wt_path" "$branch" "$sha"

      # Append to audit log
      LOG="$REPO_ROOT/reports/cleanup-log.md"
      mkdir -p "$REPO_ROOT/reports"
      printf '%s cleanup-worktrees: removed %s branch=%s sha=%s\n' \
        "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$wt_path" "$branch" "$sha" \
        >> "$LOG"

      # Remove worktree
      if ! git -C "$REPO_ROOT" worktree remove --force "$wt_path" 2>&1; then
        echo "  (worktree remove failed -- continuing)" >&2
        partial_failure=1
      fi

      # Delete local branch (skip if unmerged guard triggers -- use -d not -D)
      git -C "$REPO_ROOT" branch -d "$branch" 2>/dev/null \
        || echo "  (local branch delete skipped -- may already be gone)"
    fi
  }

  while IFS= read -r line; do
    case "$line" in
      worktree\ *)
        process_entry
        wt_path="${line#worktree }"
        sha=""
        branch=""
        ;;
      HEAD\ *)
        sha="${line#HEAD }"
        ;;
      branch\ refs/heads/*)
        branch="${line#branch refs/heads/}"
        ;;
      branch\ *)
        # detached or other -- leave branch empty
        branch=""
        ;;
    esac
  done < <(git -C "$REPO_ROOT" worktree list --porcelain)

  # Process last entry
  process_entry
}

parse_and_process

echo
if $DRY_RUN; then
  echo "==> Dry-run complete. Run with --execute to actually remove the listed worktrees."
else
  echo "==> Execute complete. Audit trail appended to reports/cleanup-log.md (if any removals)."
fi

exit $partial_failure