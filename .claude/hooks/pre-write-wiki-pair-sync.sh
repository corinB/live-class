#!/usr/bin/env bash
# Write/Edit 대상 파일이 루트 doc 4개 중 하나일 때 짝이 되는 wiki-src/ko/*-detail.md 수정 여부를 검사하는 PreToolUse 훅 (jq required, no fallback)
set -euo pipefail

if [ "${WIKI_PAIR_GUARD_OFF:-0}" = "1" ]; then
  exit 0
fi

deny() {
  printf '{"permissionDecision":"deny","reason":"%s"}' "$1"
  exit 2
}

if ! command -v jq >/dev/null 2>&1; then
  echo "[hook:pre-write-wiki-pair-sync] jq is required but not installed. Install jq and retry." >&2
  exit 2
fi

payload=$(cat)
file_path=$(printf '%s' "$payload" | jq -r '.tool_input.file_path // .tool_input.path // ""')

if [ -z "${file_path}" ]; then
  exit 0
fi

basename_path=$(basename "$file_path")

ROOT_DOCS=("ARCHITECTURE.md" "DOCS.md" "CONTRIBUTING.md" "ORCHESTRATION.md")
WIKI_DETAILS=("wiki-src/ko/architecture-detail.md" "wiki-src/ko/docs-detail.md" "wiki-src/ko/contributing-detail.md" "wiki-src/ko/orchestration-detail.md")

wiki_detail=""
for i in "${!ROOT_DOCS[@]}"; do
  if [ "${ROOT_DOCS[$i]}" = "$basename_path" ]; then
    wiki_detail="${WIKI_DETAILS[$i]}"
    break
  fi
done

if [ -z "$wiki_detail" ]; then
  exit 0
fi

git_root=$(git rev-parse --show-toplevel 2>/dev/null || true)
if [ -z "$git_root" ]; then
  exit 0
fi

# wiki-src 파일이 working tree (unstaged 또는 staged) 에서 dirty 한지 확인.
dirty_files=$(
  {
    git -C "$git_root" diff --name-only 2>/dev/null
    git -C "$git_root" diff --cached --name-only 2>/dev/null
  } | sort -u
)

if printf '%s\n' "$dirty_files" | grep -qFx "$wiki_detail"; then
  exit 0
fi

deny "pair-skew: ${basename_path} 를 수정하려면 짝이 되는 ${wiki_detail} 도 함께 수정해야 합니다. 탈출 해치: WIKI_PAIR_GUARD_OFF=1"
