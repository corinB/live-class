#!/usr/bin/env bash
# rm/git rm 명령으로 루트 doc·wiki 짝 중 한쪽만 삭제하려 할 때 차단하는 PreToolUse 훅
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
  command_str=${command_str//\\\"/\"}
  command_str=${command_str//\\\\/\\}
fi

if [ -z "$command_str" ]; then
  exit 0
fi

# rm 또는 git rm 패턴인지 확인
if ! printf '%s' "$command_str" | grep -qE '(^|[[:space:];&|])(git[[:space:]]+rm|rm)[[:space:]]'; then
  exit 0
fi

# 4개 쌍 정의
ROOT_DOCS=("ARCHITECTURE.md" "DOCS.md" "CONTRIBUTING.md" "ORCHESTRATION.md")
WIKI_DETAILS=("wiki-src/ko/architecture-detail.md" "wiki-src/ko/docs-detail.md" "wiki-src/ko/contributing-detail.md" "wiki-src/ko/orchestration-detail.md")

for i in "${!ROOT_DOCS[@]}"; do
  root="${ROOT_DOCS[$i]}"
  wiki="${WIKI_DETAILS[$i]}"

  root_removed=0
  wiki_removed=0

  # 명령어 내 파일명 포함 여부 확인 (basename 또는 경로 매칭)
  root_base=$(basename "$root")
  wiki_base=$(basename "$wiki")

  if printf '%s' "$command_str" | grep -qF "$root_base" || printf '%s' "$command_str" | grep -qF "$root"; then
    root_removed=1
  fi
  if printf '%s' "$command_str" | grep -qF "$wiki_base" || printf '%s' "$command_str" | grep -qF "$wiki"; then
    wiki_removed=1
  fi

  # 정확히 한쪽만 삭제 대상 → 차단
  if [ "$root_removed" -ne "$wiki_removed" ]; then
    if [ "$root_removed" -eq 1 ]; then
      deny "pair-skew: ${root} 를 삭제하려면 ${wiki} 도 함께 삭제해야 합니다. 탈출 해치: WIKI_PAIR_GUARD_OFF=1"
    else
      deny "pair-skew: ${wiki} 를 삭제하려면 ${root} 도 함께 삭제해야 합니다. 탈출 해치: WIKI_PAIR_GUARD_OFF=1"
    fi
  fi
done

exit 0