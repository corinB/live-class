#!/usr/bin/env bash
# Bash 출력이 임계치(약 100KB)를 넘으면 stderr 경고를 내고 reports/surrogate-blocks.log에 기록하는 PostToolUse 훅
set -euo pipefail

if [ -n "${SURROGATE_GUARD_OFF:-}" ]; then
  cat >/dev/null
  exit 0
fi

THRESHOLD=102400

payload=$(cat)

size=""
if command -v jq >/dev/null 2>&1; then
  size=$(
    printf '%s' "$payload" |
      jq -r '
        (.tool_response // {}) as $t
        | (($t.output // "") | tostring) + (($t.stdout // "") | tostring) + (($t.stderr // "") | tostring)
        | length
      '
  )
elif command -v node >/dev/null 2>&1; then
  size=$(
    PAYLOAD="$payload" node -e '
      const p = JSON.parse(process.env.PAYLOAD || "{}");
      const t = (p && p.tool_response) || {};
      const parts = [t.output, t.stdout, t.stderr].map(x => typeof x === "string" ? x : "");
      process.stdout.write(String(parts.join("").length));
    '
  )
else
  exit 0
fi

if [ -z "$size" ] || ! [[ "$size" =~ ^[0-9]+$ ]]; then
  exit 0
fi

if [ "$size" -le "$THRESHOLD" ]; then
  exit 0
fi

log_dir="${CLAUDE_PROJECT_DIR:-.}/reports"
log_file="${log_dir}/surrogate-blocks.log"
mkdir -p "$log_dir" 2>/dev/null || true

cmd_excerpt=""
if command -v jq >/dev/null 2>&1; then
  cmd_excerpt=$(printf '%s' "$payload" | jq -r '.tool_input.command // ""' | tr '\n' ' ' | cut -c1-120)
fi

printf '%s\t%s\t%s\t%s\n' "$(date -u +%FT%TZ)" "post-bash" "$size" "$cmd_excerpt" >> "$log_file" 2>/dev/null || true

cat >&2 <<EOF
[hook:post-bash-output] 경고: Bash 출력이 ${size} chars (> ${THRESHOLD}). surrogate-split 위험.
다음 호출에서 출력을 잘라내세요:
  - git log → -n N 또는 --max-count=N
  - docker logs → --tail N
  - find/grep/cat → | head -N 또는 부분 경로만
의도적으로 큰 출력이 필요하면 SURROGATE_GUARD_OFF=1 로 우회.
EOF

exit 0
