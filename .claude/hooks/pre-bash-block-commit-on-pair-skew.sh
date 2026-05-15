#!/usr/bin/env bash
# 루트 doc과 wiki-src/ko/*-detail.md 짝 중 한쪽만 staged 되었을 때 커밋을 차단하는 가드
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

# command 필드 추출
if command -v jq > /dev/null 2>&1; then
  command_str=$(printf '%s' "$payload" | jq -r '.tool_input.command // ""')
else
  command_str=$(printf '%s' "$payload" \
    | grep -o '"command"[[:space:]]*:[[:space:]]*"[^"]*"' \
    | sed 's/"command"[[:space:]]*:[[:space:]]*"\(.*\)"/\1/' | head -1 || true)
  # JSON unescape 기본 처리
  command_str=${command_str//\\\"/\"}
  command_str=${command_str//\\\\/\\}
fi

if [ -z "$command_str" ]; then
  exit 0
fi

# git commit 명령인지 확인 (git commit / git -C <path> commit 포함)
if ! printf '%s' "$command_str" | grep -qE '(^|[[:space:];&|])git[[:space:]]+(-[a-zA-Z][[:space:]]+[^[:space:]]+[[:space:]]+)*commit[[:space:]]'; then
  # AMEND commit 포함
  if ! printf '%s' "$command_str" | grep -qE '(^|[[:space:];&|])git[[:space:]].*commit'; then
    exit 0
  fi
fi

# staged 파일 목록 조회
git_root=$(git rev-parse --show-toplevel 2>/dev/null || true)
if [ -z "$git_root" ]; then
  exit 0
fi

staged=$(git -C "$git_root" diff --cached --name-only 2>/dev/null || true)

# 4개 쌍 순차 검사
ROOT_DOCS=("ARCHITECTURE.md" "DOCS.md" "CONTRIBUTING.md" "ORCHESTRATION.md")
WIKI_DETAILS=("wiki-src/ko/architecture-detail.md" "wiki-src/ko/docs-detail.md" "wiki-src/ko/contributing-detail.md" "wiki-src/ko/orchestration-detail.md")

for i in "${!ROOT_DOCS[@]}"; do
  root="${ROOT_DOCS[$i]}"
  wiki="${WIKI_DETAILS[$i]}"

  root_staged=0
  wiki_staged=0

  if printf '%s\n' "$staged" | grep -qF "$root"; then
    root_staged=1
  fi
  if printf '%s\n' "$staged" | grep -qF "$wiki"; then
    wiki_staged=1
  fi

  # 정확히 한쪽만 staged → 차단
  if [ "$root_staged" -ne "$wiki_staged" ]; then
    if [ "$root_staged" -eq 1 ]; then
      deny "pair-skew: ${root} 이 staged 되었지만 ${wiki} 는 staged 되지 않았습니다. 함께 staged 하거나 WIKI_PAIR_GUARD_OFF=1 을 사용하세요."
    else
      deny "pair-skew: ${wiki} 이 staged 되었지만 ${root} 는 staged 되지 않았습니다. 함께 staged 하거나 WIKI_PAIR_GUARD_OFF=1 을 사용하세요."
    fi
  fi
done

exit 0