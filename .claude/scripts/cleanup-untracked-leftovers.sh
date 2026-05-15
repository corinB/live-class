#!/usr/bin/env bash
# .clone/, reports/14_*, reports/20_*, reports/27_*, docs/engineering/* 같은 알려진 잔재만 정리하는 스크립트 (default: --dry-run)
set -euo pipefail

MODE="dry-run"
case "${1:-}" in
  --execute) MODE="execute" ;;
  --help|-h)
    echo "Usage: $0 [--dry-run|--execute|--help]"
    echo ""
    echo "  --dry-run   (default) List what would be removed without deleting."
    echo "  --execute   Actually remove untracked allowlisted paths and log to reports/cleanup-log.md."
    echo "  --help      Show this message."
    exit 0
    ;;
  --dry-run|"") MODE="dry-run" ;;
  *)
    echo "Error: unknown flag \"$1\". Use --dry-run, --execute, or --help." >&2
    exit 1
    ;;
esac

REPO_ROOT=$(git rev-parse --show-toplevel 2>/dev/null)
if [ -z "$REPO_ROOT" ]; then
  echo "Error: not in a git repository" >&2
  exit 1
fi
cd "$REPO_ROOT"

# Hard-coded allowlist of glob patterns (relative to repo root).
# docs/engineering/ is intentionally absent: NN 38 migrates it to wiki-src/ko/docs/engineering/.
# After NN 38 merges, docs/engineering/ will be empty/absent.
PATTERNS=(
  ".clone/worktrees"
  "reports/14_*.md"
  "reports/20_*.md"
  "reports/27_*.md"
)

# Prefixes that are absolutely off-limits.
REFUSED_PREFIXES=("live-class/" "wiki-src/" ".clone/" ".github/")

printf "%-45s | %-8s | %-10s | %s\n" "path" "present" "tracked?" "action"
printf "%-45s-+-%-8s-+-%-10s-+-%s\n" "---------------------------------------------" "--------" "----------" "------"

for pattern in "${PATTERNS[@]}"; do
  matches=()
  for m in $pattern; do
    if [ -e "$m" ]; then
      matches+=("$m")
    fi
  done

  if [ "${#matches[@]}" -eq 0 ]; then
    printf "%-45s | %-8s | %-10s | %s\n" "$pattern" "no" "n/a" "skip (not present)"
    continue
  fi

  for path in "${matches[@]}"; do
    refused=0
    for prefix in "${REFUSED_PREFIXES[@]}"; do
      if [[ "$path" == "$prefix"* ]]; then
        refused=1
        break
      fi
    done
    if [ "$refused" -eq 1 ]; then
      printf "%-45s | %-8s | %-10s | %s\n" "$path" "yes" "n/a" "refuse (protected prefix)"
      continue
    fi

    # SAFETY: refuse to delete tracked paths.
    if git ls-files --error-unmatch -- "$path" > /dev/null 2>&1; then
      printf "%-45s | %-8s | %-10s | %s\n" "$path" "yes" "TRACKED" "refuse (tracked by git)"
      continue
    fi

    # Collect metadata for logging.
    mtime=""
    size=""
    if [ -f "$path" ]; then
      mtime=$(date -u -r "$path" +%FT%TZ 2>/dev/null || stat -c "%y" "$path" 2>/dev/null | cut -d"." -f1 || echo "unknown")
      size=$(du -sh "$path" 2>/dev/null | cut -f1 || echo "unknown")
    elif [ -d "$path" ]; then
      mtime=$(date -u -r "$path" +%FT%TZ 2>/dev/null || stat -c "%y" "$path" 2>/dev/null | cut -d"." -f1 || echo "unknown")
      size=$(du -sh "$path" 2>/dev/null | cut -f1 || echo "unknown")
    fi

    if [ "$MODE" = "execute" ]; then
      mkdir -p reports
      printf "%s\tcleanup-untracked\tremoved\t%s\tmtime=%s\tsize=%s\n" \
        "$(date -u +%FT%TZ)" "$path" "$mtime" "$size" >> reports/cleanup-log.md
      rm -rf -- "$path"
      printf "%-45s | %-8s | %-10s | %s\n" "$path" "yes" "no" "removed"
    else
      printf "%-45s | %-8s | %-10s | %s\n" "$path" "yes" "no" "WOULD DELETE (mtime=$mtime, size=$size)"
    fi
  done
done

if [ "$MODE" = "dry-run" ]; then
  echo ""
  echo "Dry-run complete. Run with --execute to actually remove the listed untracked paths."
fi
