#!/usr/bin/env bash
# Write/Edit 대상 파일이 루트 doc 4개 중 하나일 때 짝이 되는 wiki-src/ko/*-detail.md 수정 여부를 검사하는 PreToolUse 훅
set -euo pipefail

# 탈출 해치: WIKI_PAIR_GUARD_OFF=1 이면 즉시 통과
if [ "${WIKI_PAIR_GUARD_OFF:-0}" = "1" ]; then
  exit 0
fi

deny() {
  local reason="$1"
  printf '{"permissionDecision":"deny","reason":"%s"}' "$reason"
  exit 2
}

# stdin에서 hook payload 읽기
payload=$(cat)

# tool_input.file_path 추출
if command -v jq > /dev/null 2>&1; then
  file_path=$(printf '%s' "$payload" | jq -r '.tool_input.file_path // .tool_input.path // ""')
else
  file_path=$(printf '%s' "$payload" | grep -o '"file_path"[[:space:]]*:[[:space:]]*"[^"]*"' \
    | sed 's/"file_path"[[:space:]]*:[[:space:]]*"\(.*\)"/\1/' | head -1 || true)
fi

if [ -z "${file_path}" ]; then
  exit 0
fi

# 경로 정規화: 절대경로에서 basename만 사용해 루트 doc 매칭
basename_path=$(basename "$file_path")

# 4개 쌍 정의: 루트doc → wiki 상대경로 배열 (bash 3 호환 순차 탐색)
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

# git repo 루트 찾기
git_root=$(git rev-parse --show-toplevel 2>/dev/null || true)
if [ -z "$git_root" ]; then
  exit 0
fi

# wiki-src 파일이 working tree 에서 dirty(수정됨)한지 확인
# git diff --name-only: unstaged, git diff --cached --name-only: staged
dirty_files=$(
  {
    git -C "$git_root" diff --name-only 2>/dev/null
    git -C "$git_root" diff --cached --name-only 2>/dev/null
  } | sort -u
)

if printf '%s\n' "$dirty_files" | grep -qF "$wiki_detail"; then
  exit 0
fi

deny "pair-skew: ${basename_path} 를 수정하려면 짝이 되는 ${wiki_detail} 도 함께 수정해야 합니다. 탈출 해치: WIKI_PAIR_GUARD_OFF=1"