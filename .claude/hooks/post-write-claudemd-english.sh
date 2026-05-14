#!/usr/bin/env bash
# CLAUDE.md 가 영어로 작성됐는지 검사해 한글 비율이 임계치를 넘으면 stderr 경고하는 PostToolUse 훅 (실제 검사는 동일 디렉터리의 .js 가 담당)
set -euo pipefail

if [ -n "${CLAUDE_MD_KOREAN_OK:-}" ]; then
  cat >/dev/null
  exit 0
fi

if ! command -v node >/dev/null 2>&1; then
  cat >/dev/null
  exit 0
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="${CLAUDE_PROJECT_DIR:-.}/reports"
mkdir -p "$LOG_DIR" 2>/dev/null || true

LOG_FILE="${LOG_DIR}/surrogate-blocks.log" exec node "${SCRIPT_DIR}/post-write-claudemd-english.js"
