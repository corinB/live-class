#!/usr/bin/env bash
# Bash 도구 실행 전 파괴적 명령(rm -rf, force push, hard reset)을 차단하는 PreToolUse 훅
set -euo pipefail

# stdin에서 hook payload 읽기
payload=$(cat)

# tool_input.command 추출 — jq 우선, 없으면 grep/sed fallback
if command -v jq > /dev/null 2>&1; then
  command_str=$(echo "${payload}" | jq -r '.tool_input.command // ""')
else
  # jq 미설치 환경을 위한 간단한 fallback
  command_str=$(echo "${payload}" | grep -o '"command"[[:space:]]*:[[:space:]]*"[^"]*"' | sed 's/"command"[[:space:]]*:[[:space:]]*"\(.*\)"/\1/' | head -1 || true)
fi

# 명령이 비어있으면 통과
if [ -z "${command_str}" ]; then
  exit 0
fi

# 파괴적 패턴 검사
if echo "${command_str}" | grep -qE 'rm[[:space:]]+-[a-zA-Z]*r[a-zA-Z]*f|rm[[:space:]]+-[a-zA-Z]*f[a-zA-Z]*r'; then
  printf '{"permissionDecision":"deny","reason":"rm -rf 는 차단됩니다. 삭제 범위를 좁혀 명시적 경로를 지정해 주세요."}'
  exit 2
fi

if echo "${command_str}" | grep -qE 'git[[:space:]]+push[[:space:]]+.*--force'; then
  printf '{"permissionDecision":"deny","reason":"git push --force 는 차단됩니다. --force-with-lease 사용을 권장하거나 필요 여부를 다시 확인해 주세요."}'
  exit 2
fi

if echo "${command_str}" | grep -qE 'git[[:space:]]+reset[[:space:]]+--hard'; then
  printf '{"permissionDecision":"deny","reason":"git reset --hard 는 차단됩니다. 되돌려야 할 커밋 범위를 명시하고 사용자 승인을 받으세요."}'
  exit 2
fi

exit 0
