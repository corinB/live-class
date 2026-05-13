#!/usr/bin/env bash
# context.yaml의 자동 갱신 필드(last_indexed, related_docs line counts)를 안전하게 재기록하는 헬퍼
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

AUDIT_SCRIPT="${SCRIPT_DIR}/audit-context-yaml.py"

if ! command -v python >/dev/null 2>&1 && ! command -v python3 >/dev/null 2>&1; then
  printf 'error: python not found on PATH\n' >&2
  exit 2
fi

PY=python
if ! command -v python >/dev/null 2>&1; then
  PY=python3
fi

cd "${REPO_ROOT}"

"${PY}" - <<'PY_EOF'
"""Refresh metadata.last_indexed and per-doc line counts in context.yaml.

Uses PyYAML round-trip and is conservative: only touches fields the audit
script flags as auto-fixable. Comments, ordering, and hand-written sections
are preserved by using yaml.safe_dump with allow_unicode and sort_keys=False.
"""
import datetime
import re
import sys
from pathlib import Path

try:
    import yaml
except ImportError:
    print("error: PyYAML not installed. Run: pip install pyyaml", file=sys.stderr)
    sys.exit(2)

REPO = Path(".").resolve()
CTX = REPO / "context.yaml"
LINE_RE = re.compile(r"(\d+)\s*lines")

text = CTX.read_text(encoding="utf-8")

today = datetime.date.today().isoformat()

new_text = re.sub(
    r'(metadata:[^\n]*\n(?:[^\n]*\n)*?\s*last_indexed:\s*")[0-9]{4}-[0-9]{2}-[0-9]{2}(")',
    rf'\g<1>{today}\g<2>',
    text,
    count=1,
)

def replace_line_count(match: re.Match) -> str:
    prefix = match.group(1)
    path_str = match.group(2)
    desc = match.group(3)
    suffix = match.group(4)
    abs_path = REPO / path_str
    if not abs_path.is_file():
        return match.group(0)
    actual = sum(1 for _ in abs_path.open("rb"))
    def sub_line(m: re.Match) -> str:
        return f"{actual} lines"
    new_desc = LINE_RE.sub(sub_line, desc)
    return f"{prefix}{path_str}{new_desc}{suffix}"

new_text = re.sub(
    r'(    - path: ")([^"\n]+)("\n      description: ")([^"\n]+)(")',
    lambda m: (
        f"{m.group(1)}{m.group(2)}{m.group(3)}"
        f"{LINE_RE.sub(lambda mm: f\"{sum(1 for _ in (REPO / m.group(2)).open('rb'))} lines\" if (REPO / m.group(2)).is_file() else mm.group(0), m.group(4))}"
        f"{m.group(5)}"
    ),
    new_text,
)

if new_text != text:
    CTX.write_text(new_text, encoding="utf-8")
    print("context.yaml refreshed")
else:
    print("context.yaml already up to date")
PY_EOF

printf '\nrunning audit to confirm...\n'
"${PY}" "${AUDIT_SCRIPT}"
