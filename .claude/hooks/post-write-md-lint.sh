#!/usr/bin/env bash
# Write/Edit 후 .md 파일에 대해 markdownlint를 실행해 결과를 컨텍스트에 출력하는 PostToolUse 훅
set -euo pipefail

# stdin에서 hook payload 읽기
payload=$(cat)

# 작성된 파일 경로 추출
if command -v jq > /dev/null 2>&1; then
  file_path=$(echo "${payload}" | jq -r '.tool_input.file_path // ""')
else
  file_path=$(echo "${payload}" | grep -o '"file_path"[[:space:]]*:[[:space:]]*"[^"]*"' | sed 's/"file_path"[[:space:]]*:[[:space:]]*"\(.*\)"/\1/' | head -1 || true)
fi

# .md 파일이 아니면 종료
case "${file_path}" in
  *.md)
    ;;
  *)
    exit 0
    ;;
esac

# npx가 없으면 노-옵
if ! command -v npx > /dev/null 2>&1; then
  exit 0
fi

# markdownlint 실행 — 실패해도 훅 자체는 통과(|| true)
echo "[hook:post-write] markdownlint 결과 (${file_path})."
npx --yes markdownlint-cli "${file_path}" 2>&1 || true

exit 0
