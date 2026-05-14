#!/usr/bin/env bash
# Read 결과에서 unpaired UTF-16 surrogate 코드 유닛이 발견되면 stderr 경고하는 PostToolUse 훅 (실제 검사는 동일 디렉터리의 .js 가 담당)
set -euo pipefail

if [ -n "${SURROGATE_GUARD_OFF:-}" ]; then
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

LOG_FILE="${LOG_DIR}/surrogate-blocks.log" exec node "${SCRIPT_DIR}/post-read-surrogate-detect.js"
