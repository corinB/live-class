// PostToolUse:Bash 출력이 임계치를 넘으면 stderr 경고 + log append 하는 노드 스크립트
'use strict';

const fs = require('fs');

const THRESHOLD = 102400;

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

const t = (p && p.tool_response) || {};
const parts = [t.output, t.stdout, t.stderr].map(x => typeof x === 'string' ? x : '');
const size = parts.join('').length;

if (size <= THRESHOLD) process.exit(0);

const cmdRaw = (p && p.tool_input && typeof p.tool_input.command === 'string') ? p.tool_input.command : '';
const cmdExcerpt = cmdRaw.replace(/\s+/g, ' ').slice(0, 120);

if (process.env.LOG_FILE) {
  try {
    fs.appendFileSync(
      process.env.LOG_FILE,
      `${new Date().toISOString()}\tpost-bash\t${size}\t${cmdExcerpt}\n`
    );
  } catch (e) {}
}

process.stderr.write(
  `[hook:post-bash-output] 경고: Bash 출력이 ${size} chars (> ${THRESHOLD}). surrogate-split 위험.\n` +
  `다음 호출에서 출력을 잘라내세요:\n` +
  `  - git log → -n N 또는 --max-count=N\n` +
  `  - docker logs → --tail N\n` +
  `  - find/grep/cat → | head -N 또는 부분 경로만\n` +
  `의도적으로 큰 출력이 필요하면 SURROGATE_GUARD_OFF=1 로 우회.\n`
);
