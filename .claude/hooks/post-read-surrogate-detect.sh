#!/usr/bin/env bash
# Read 결과에서 unpaired UTF-16 surrogate 코드 유닛이 발견되면 stderr 경고하는 PostToolUse 훅
set -euo pipefail

if [ -n "${SURROGATE_GUARD_OFF:-}" ]; then
  cat >/dev/null
  exit 0
fi

payload=$(cat)

if ! command -v node >/dev/null 2>&1; then
  exit 0
fi

log_dir="${CLAUDE_PROJECT_DIR:-.}/reports"
log_file="${log_dir}/surrogate-blocks.log"
mkdir -p "$log_dir" 2>/dev/null || true

PAYLOAD="$payload" LOG_FILE="$log_file" node <<'NODE_EOF' || true
const fs = require('fs');
const p = JSON.parse(process.env.PAYLOAD || '{}');
const fp = (p && p.tool_input && typeof p.tool_input.file_path === 'string') ? p.tool_input.file_path : '';
const tr = (p && p.tool_response) || {};
const content = typeof tr.content === 'string' ? tr.content
              : typeof tr.output === 'string' ? tr.output
              : '';

const SIZE_MIN = 81920;
if (!content || content.length < SIZE_MIN) process.exit(0);

let unpaired = 0;
let where = -1;
for (let i = 0; i < content.length; i++) {
  const code = content.charCodeAt(i);
  if (code >= 0xD800 && code <= 0xDBFF) {
    if (i + 1 >= content.length) { unpaired++; where = i; break; }
    const next = content.charCodeAt(i + 1);
    if (next < 0xDC00 || next > 0xDFFF) { unpaired++; where = i; break; }
    i++;
  } else if (code >= 0xDC00 && code <= 0xDFFF) {
    unpaired++; where = i; break;
  }
}

if (unpaired > 0) {
  const msg = `[hook:post-read-surrogate] unpaired surrogate at index ${where} in ${fp} (len=${content.length}). 다음 Read는 offset/limit 으로 영역을 좁히세요.`;
  process.stderr.write(msg + '\n');
  try {
    fs.appendFileSync(process.env.LOG_FILE, `${new Date().toISOString()}\tpost-read\tsurrogate\t${msg}\n`);
  } catch (e) {}
}
NODE_EOF

exit 0
