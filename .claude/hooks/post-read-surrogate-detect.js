// PostToolUse:Read 페이로드에서 unpaired UTF-16 surrogate 코드 유닛을 검출해 stderr 경고하는 노드 스크립트
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
  if (process.env.LOG_FILE) {
    try {
      fs.appendFileSync(process.env.LOG_FILE, `${new Date().toISOString()}\tpost-read\tsurrogate\t${msg}\n`);
    } catch (e) {}
  }
}
