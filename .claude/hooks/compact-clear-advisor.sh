#!/usr/bin/env bash
# 세션 jsonl 크기를 보고 /compact 또는 /clear 실행을 권고하는 advisor 훅 (비차단)
set -euo pipefail

# Escape hatch: SURROGATE_GUARD_OFF=1 이면 조용히 종료한다.
if [ -n "${SURROGATE_GUARD_OFF:-}" ]; then
  cat >/dev/null
  exit 0
fi

# 2단계 임계값.
# SOFT 이상이면 /compact 권고, HARD 이상이면 /clear 강력 권고.
# 1.0 MB(=1048576), 1.4 MB(=1468006). surrogate-split 인시던트 분포와
# cumulative-size-guard(1.2 MB) 사이에 의도적으로 두 임계를 배치했다.
SOFT_BYTES=1048576
HARD_BYTES=1468006

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
byte_size=${byte_size:-0}

# SOFT 미만이면 침묵.
if [ "$byte_size" -lt "$SOFT_BYTES" ]; then
  exit 0
fi

# 레벨 결정
if [ "$byte_size" -ge "$HARD_BYTES" ]; then
  level="hard"
  cmd="/clear"
  ko_msg="컨텍스트가 1.4 MB를 넘었습니다. 지금 ${cmd} 로 초기화하거나 서브에이전트에 위임하세요."
  en_msg="Strongly recommend running \`${cmd}\` now to reset the conversation."
  reason="session jsonl=${byte_size} bytes >= 1.4 MB"
else
  level="soft"
  cmd="/compact"
  ko_msg="컨텍스트가 1.0 MB를 넘었습니다. 지금 ${cmd} 로 압축하면 다음 큰 작업에 안전합니다."
  en_msg="Recommend running \`${cmd}\` now to compact the conversation before the next large task."
  reason="session jsonl=${byte_size} bytes >= 1.0 MB"
fi

# 로그 append (cumulative-size-guard와 동일 파일·포맷 계열)
session_id="${CLAUDE_SESSION_ID:-unknown}"
timestamp=$(date -u +%FT%TZ 2>/dev/null || echo "unknown")
mkdir -p "$LOG_DIR" 2>/dev/null || true
printf '%s\tadvisor\tlevel=%s\tbytes=%s\tsession=%s\n' \
  "$timestamp" "$level" "$byte_size" "$session_id" \
  >> "$LOG_FILE" 2>/dev/null || true

# stdout: UserPromptSubmit 등에서 Claude가 읽는 추가 컨텍스트
printf '[advisor] %s Reason: %s.\n' "$en_msg" "$reason"

# stderr: 사용자에게 직접 보이는 한국어 권고
cat >&2 <<EOF
[hook:compact-clear-advisor] ${ko_msg}
  jsonl 크기: ${byte_size} bytes (SOFT=${SOFT_BYTES}, HARD=${HARD_BYTES})
EOF

# 비차단 — 항상 exit 0
exit 0
