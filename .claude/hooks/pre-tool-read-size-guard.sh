#!/usr/bin/env bash
# 큰 파일을 offset/limit 없이 Read하려는 시도를 차단해 surrogate-split을 예방하는 PreToolUse 훅입니다.
set -euo pipefail

# Escape hatch: 의도적으로 큰 파일을 통째로 읽어야 할 때 SURROGATE_GUARD_OFF=1 로 우회.
if [ -n "${SURROGATE_GUARD_OFF:-}" ]; then
  cat >/dev/null
  exit 0
fi

THRESHOLD_BYTES=102400

log_block() {
  local category="$1"
  local detail="$2"
  local log_dir="${CLAUDE_PROJECT_DIR:-.}/reports"
  local log_file="${log_dir}/surrogate-blocks.log"
  mkdir -p "$log_dir" 2>/dev/null || return 0
  printf '%s\t%s\t%s\t%s\n' "$(date -u +%FT%TZ)" "pre-read" "$category" "$detail" >> "$log_file" 2>/dev/null || true
}

deny() {
  local category="$1"
  local message="$2"
  log_block "$category" "$message"
  printf '{"permissionDecision":"deny","reason":"%s: %s"}' "$category" "$message"
  exit 2
}

payload=$(cat)

if command -v jq >/dev/null 2>&1; then
  file_path=$(
    printf '%s' "$payload" |
      jq -r 'if (.tool_input.file_path? | type) == "string" then .tool_input.file_path else "" end'
  )
  offset_val=$(
    printf '%s' "$payload" |
      jq -r 'if (.tool_input.offset? | type) == "number" then "set" else "" end'
  )
  limit_val=$(
    printf '%s' "$payload" |
      jq -r 'if (.tool_input.limit? | type) == "number" then "set" else "" end'
  )
elif command -v node >/dev/null 2>&1; then
  parsed=$(
    PAYLOAD="$payload" node -e '
      const payload = JSON.parse(process.env.PAYLOAD || "{}");
      const ti = (payload && payload.tool_input) || {};
      const fp = typeof ti.file_path === "string" ? ti.file_path : "";
      const off = typeof ti.offset === "number" ? "set" : "";
      const lim = typeof ti.limit === "number" ? "set" : "";
      process.stdout.write(fp + "\n" + off + "\n" + lim);
    '
  )
  file_path=$(printf '%s' "$parsed" | sed -n '1p')
  offset_val=$(printf '%s' "$parsed" | sed -n '2p')
  limit_val=$(printf '%s' "$parsed" | sed -n '3p')
else
  deny "parser-missing" "install jq or node to parse Claude hook JSON safely"
fi

if [ -z "$file_path" ]; then
  exit 0
fi

if [ ! -f "$file_path" ]; then
  exit 0
fi

if [ -n "$offset_val" ] || [ -n "$limit_val" ]; then
  exit 0
fi

file_size=$(wc -c < "$file_path" 2>/dev/null || echo 0)
file_size=${file_size//[[:space:]]/}

if [ -z "$file_size" ] || [ "$file_size" -le "$THRESHOLD_BYTES" ]; then
  exit 0
fi

deny "read-size" "file is ${file_size} bytes (> ${THRESHOLD_BYTES}); pass offset+limit (e.g. offset=0, limit=1000) to bound the Read and avoid surrogate-split payload bloat. Set SURROGATE_GUARD_OFF=1 in env to bypass intentionally. file=${file_path}"
