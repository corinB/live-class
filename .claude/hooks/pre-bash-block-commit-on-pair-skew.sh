#!/usr/bin/env bash
# 루트 doc과 wiki-src/ko/*-detail.md 짝 중 한쪽만 staged 되었을 때 커밋을 차단하는 가드 (jq required, no fallback)
set -euo pipefail

if [ "${WIKI_PAIR_GUARD_OFF:-0}" = "1" ]; then
  exit 0
fi

deny() {
  printf '{"permissionDecision":"deny","reason":"%s"}' "$1"
  exit 2
}

# jq hard-require: fallback 제거로 false positive/negative 위험 차단.
if ! command -v jq >/dev/null 2>&1; then
  echo "[hook:pre-bash-block-commit-on-pair-skew] jq is required but not installed. Install jq and retry." >&2
  exit 2
fi

payload=$(cat)
command_str=$(printf '%s' "$payload" | jq -r '.tool_input.command // ""')

if [ -z "$command_str" ]; then
  exit 0
fi

# Strip inline shell comments (anything after ' #' to end of line) to prevent
# false positives like `git commit-graph write # rebase commit comment`.
command_clean=$(printf '%s' "$command_str" | sed -E 's/[[:space:]]+#[^\n]*$//')

# Detect `git commit` subcommand allowing common global flags between `git` and `commit`:
#   git commit
#   git -c user.email=x@y commit
#   git -C /path commit
#   git --git-dir=path commit
#   git -c k=v -c k2=v2 commit ...
# Reject lookalikes such as `git commit-graph`, `git-commit-tree`.
if ! printf '%s' "$command_clean" \
  | grep -qE '(^|[[:space:];&|])git([[:space:]]+(-[cC][[:space:]]+("[^"]*"|[^[:space:]]+)|--[a-z][-a-z]*(=[^[:space:]]+|[[:space:]]+[^[:space:]]+)))*[[:space:]]+commit([[:space:]]|$|;|&|\|)'; then
  exit 0
fi

git_root=$(git rev-parse --show-toplevel 2>/dev/null || true)
if [ -z "$git_root" ]; then
  exit 0
fi

staged=$(git -C "$git_root" diff --cached --name-only 2>/dev/null || true)

ROOT_DOCS=("ARCHITECTURE.md" "DOCS.md" "CONTRIBUTING.md" "ORCHESTRATION.md")
WIKI_DETAILS=("wiki-src/ko/architecture-detail.md" "wiki-src/ko/docs-detail.md" "wiki-src/ko/contributing-detail.md" "wiki-src/ko/orchestration-detail.md")

for i in "${!ROOT_DOCS[@]}"; do
  root="${ROOT_DOCS[$i]}"
  wiki="${WIKI_DETAILS[$i]}"
  root_staged=0
  wiki_staged=0
  # `-Fx` requires the whole line to match the fixed string, so substrings won't trigger.
  if printf '%s\n' "$staged" | grep -qFx "$root"; then
    root_staged=1
  fi
  if printf '%s\n' "$staged" | grep -qFx "$wiki"; then
    wiki_staged=1
  fi
  if [ "$root_staged" -ne "$wiki_staged" ]; then
    if [ "$root_staged" -eq 1 ]; then
      deny "pair-skew: ${root} 이 staged 되었지만 ${wiki} 는 staged 되지 않았습니다. 함께 staged 하거나 WIKI_PAIR_GUARD_OFF=1 을 사용하세요."
    else
      deny "pair-skew: ${wiki} 이 staged 되었지만 ${root} 는 staged 되지 않았습니다. 함께 staged 하거나 WIKI_PAIR_GUARD_OFF=1 을 사용하세요."
    fi
  fi
done

exit 0
