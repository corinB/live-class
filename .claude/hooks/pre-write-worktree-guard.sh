#!/usr/bin/env bash
# Write/Edit 도구 실행 전 워크트리 경계 이탈 여부를 감지해 경고하는 PreToolUse 훅
set -euo pipefail

# stdin에서 hook payload 읽기
payload=$(cat)

# tool_input.file_path 추출
if command -v jq > /dev/null 2>&1; then
  file_path=$(echo "${payload}" | jq -r '.tool_input.file_path // ""')
else
  file_path=$(echo "${payload}" | grep -o '"file_path"[[:space:]]*:[[:space:]]*"[^"]*"' | sed 's/"file_path"[[:space:]]*:[[:space:]]*"\(.*\)"/\1/' | head -1 || true)
fi

# CLAUDE_WORKTREE_PATH 환경 변수가 설정되지 않았으면 검사 생략
if [ -z "${CLAUDE_WORKTREE_PATH:-}" ]; then
  exit 0
fi

# file_path가 비어있으면 통과
if [ -z "${file_path}" ]; then
  exit 0
fi

# 파일 경로가 지정된 워크트리 접두사로 시작하는지 확인
case "${file_path}" in
  "${CLAUDE_WORKTREE_PATH}"*)
    # 워크트리 내부 — 정상
    exit 0
    ;;
  *)
    echo "[hook:pre-write] 경고: 파일 경로(${file_path})가 현재 워크트리(${CLAUDE_WORKTREE_PATH}) 밖에 있습니다. 의도된 변경인지 확인해 주세요."
    exit 0
    ;;
esac
