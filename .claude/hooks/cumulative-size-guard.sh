#!/usr/bin/env bash
# 세션 jsonl 누적 크기와 메시지 수를 감시해 임계값 초과 시 경고하는 UserPromptSubmit 훅 (비차단)
set -euo pipefail

# Escape hatch: SURROGATE_GUARD_OFF=1 이면 조용히 종료한다.
if [ -n "${SURROGATE_GUARD_OFF:-}" ]; then
  cat >/dev/null
  exit 0
fi

# 임계값 정의
MAX_BYTES=1572864   # 1.5 MB
MAX_MESSAGES=500

LOG_DIR="${CLAUDE_PROJECT_DIR:-.}/reports"
LOG_FILE="${LOG_DIR}/surrogate-blocks.log"

# stdin JSON 소비 (hook payload)
payload=$(cat)

# 세션 jsonl 경로 결정: $CLAUDE_SESSION_FILE 우선, 없으면 id 기반 추정
session_file=""
if [ -n "${CLAUDE_SESSION_FILE:-}" ] && [ -f "${CLAUDE_SESSION_FILE}" ]; then
  session_file="${CLAUDE_SESSION_FILE}"
elif [ -n "${CLAUDE_SESSION_ID:-}" ] && [ -n "${CLAUDE_PROJECT_DIR:-}" ]; then
  candidate="${CLAUDE_PROJECT_DIR}/.claude/sessions/${CLAUDE_SESSION_ID}.jsonl"
  if [ -f "$candidate" ]; then
    session_file="$candidate"
  fi
fi

# 세션 파일을 찾지 못하면 조용히 종료 (비차단)
if [ -z "$session_file" ]; then
  exit 0
fi

byte_size=$(wc -c < "$session_file" 2>/dev/null || echo 0)
byte_size=${byte_size//[[:space:]]/}

message_count=$(wc -l < "$session_file" 2>/dev/null || echo 0)
message_count=${message_count//[[:space:]]/}

# 임계값 미만이면 아무것도 하지 않는다
if [ "${byte_size:-0}" -le "$MAX_BYTES" ] && [ "${message_count:-0}" -le "$MAX_MESSAGES" ]; then
  exit 0
fi

# 임계값 초과: 로그 기록 + stderr 경고 출력 (비차단 — exit 0 유지)
session_id="${CLAUDE_SESSION_ID:-unknown}"
timestamp=$(date -u +%FT%TZ 2>/dev/null || echo "unknown")

mkdir -p "$LOG_DIR" 2>/dev/null || true
printf '%s\tcumulative\tbytes=%s\tmessages=%s\tsession=%s\n' \
  "$timestamp" "${byte_size:-0}" "${message_count:-0}" "$session_id" \
  >> "$LOG_FILE" 2>/dev/null || true

cat >&2 <<EOF
[hook:cumulative-size-guard] 세션 누적 크기 임계값 초과.
  jsonl 크기: ${byte_size:-0} bytes (임계값 ${MAX_BYTES} bytes = 1.5 MB)
  메시지 수: ${message_count:-0} (임계값 ${MAX_MESSAGES})
  권장 조치: /clear 로 컨텍스트를 초기화하거나 서브에이전트에 작업을 위임하세요.
  surrogate-split 위험이 높아졌습니다. (2026-05-15 인시던트 참조)
EOF

# 비차단 — 항상 exit 0
exit 0
