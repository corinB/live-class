#!/usr/bin/env bash
# CLAUDE.md 가 영어로 작성됐는지 검사해 한글 비율이 임계치를 넘으면 stderr 경고하는 PostToolUse 훅
set -euo pipefail

if [ -n "${CLAUDE_MD_KOREAN_OK:-}" ]; then
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
if (!/CLAUDE\.md$/i.test(fp)) process.exit(0);

let text;
try { text = fs.readFileSync(fp, 'utf8'); } catch (e) { process.exit(0); }

let korean = 0, total = 0;
for (let i = 0; i < text.length; i++) {
  const c = text.charCodeAt(i);
  if (c <= 0x20) continue; // skip whitespace
  total++;
  if ((c >= 0xAC00 && c <= 0xD7A3) || (c >= 0x1100 && c <= 0x11FF) || (c >= 0x3130 && c <= 0x318F)) {
    korean++;
  }
}
if (total < 200) process.exit(0); // ignore tiny files
const ratio = korean / total;
const THRESHOLD = 0.15;
if (ratio > THRESHOLD) {
  const msg = `[hook:claudemd-english] ${fp}: Korean ratio ${(ratio * 100).toFixed(1)}% > ${(THRESHOLD * 100).toFixed(0)}%. CLAUDE.md is an internal technical artifact and should be English. Set CLAUDE_MD_KOREAN_OK=1 to bypass intentionally.`;
  process.stderr.write(msg + '\n');
  try {
    fs.appendFileSync(process.env.LOG_FILE, `${new Date().toISOString()}\tpost-write\tclaudemd-korean\t${msg}\n`);
  } catch (e) {}
}
NODE_EOF

exit 0
