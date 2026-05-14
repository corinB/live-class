// PostToolUse:Write|Edit 로 CLAUDE.md 가 한국어 비율 임계치를 넘게 작성됐는지 검사하는 노드 스크립트
'use strict';

const fs = require('fs');

let raw = '';
try {
  raw = fs.readFileSync(0, 'utf8');
} catch (e) {
  process.exit(0);
}

let p;
try {
  p = JSON.parse(raw || '{}');
} catch (e) {
  process.exit(0);
}

const fp = (p && p.tool_input && typeof p.tool_input.file_path === 'string') ? p.tool_input.file_path : '';
if (!/CLAUDE\.md$/i.test(fp)) process.exit(0);

let text;
try {
  text = fs.readFileSync(fp, 'utf8');
} catch (e) {
  process.exit(0);
}

let korean = 0;
let total = 0;
for (let i = 0; i < text.length; i++) {
  const c = text.charCodeAt(i);
  if (c <= 0x20) continue;
  total++;
  if ((c >= 0xAC00 && c <= 0xD7A3) || (c >= 0x1100 && c <= 0x11FF) || (c >= 0x3130 && c <= 0x318F)) {
    korean++;
  }
}
if (total < 200) process.exit(0);

const ratio = korean / total;
const THRESHOLD = 0.15;
if (ratio > THRESHOLD) {
  const msg = `[hook:claudemd-english] ${fp}: Korean ratio ${(ratio * 100).toFixed(1)}% > ${(THRESHOLD * 100).toFixed(0)}%. CLAUDE.md is an internal technical artifact and should be English. Set CLAUDE_MD_KOREAN_OK=1 to bypass intentionally.`;
  process.stderr.write(msg + '\n');
  if (process.env.LOG_FILE) {
    try {
      fs.appendFileSync(process.env.LOG_FILE, `${new Date().toISOString()}\tpost-write\tclaudemd-korean\t${msg}\n`);
    } catch (e) {}
  }
}
