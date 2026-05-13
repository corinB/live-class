#!/usr/bin/env bash
# .claude/agents/*.md 안의 include sentinel을 _prelude.md 본문으로 치환하는 build-time 헬퍼
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
AGENTS_DIR="${REPO_ROOT}/.claude/agents"
PRELUDE_PATH="${AGENTS_DIR}/_prelude.md"

if [ ! -f "${PRELUDE_PATH}" ]; then
  printf 'error: %s missing — cannot resolve sentinels\n' "${PRELUDE_PATH}" >&2
  exit 2
fi

if ! command -v python >/dev/null 2>&1 && ! command -v python3 >/dev/null 2>&1; then
  printf 'error: python not found on PATH\n' >&2
  exit 2
fi

PY=python
if ! command -v python >/dev/null 2>&1; then
  PY=python3
fi

PRELUDE_PATH="${PRELUDE_PATH}" AGENTS_DIR="${AGENTS_DIR}" "${PY}" - <<'PY_EOF'
"""Resolve `<!-- include:_prelude.md -->` sentinels inside .claude/agents/*.md.

The script is idempotent: re-running on a file that already has the inlined
block replaces the body between the sentinel markers with the current
_prelude.md contents.

Encoding:
- Sentinel start: `<!-- include:_prelude.md -->`
- Sentinel end:   `<!-- /include:_prelude.md -->`
Both markers are emitted around the inlined body so future runs detect the
existing block. The sentinel start line is preserved verbatim so source
files keep showing the intent.
"""
import os
import re
import sys
from pathlib import Path

agents_dir = Path(os.environ["AGENTS_DIR"])
prelude = Path(os.environ["PRELUDE_PATH"]).read_text(encoding="utf-8").rstrip("\n")

START_MARKER = "<!-- include:_prelude.md -->"
END_MARKER = "<!-- /include:_prelude.md -->"

# Drop the file-header HTML comment from _prelude.md so the inlined body
# does not duplicate the header on every agent file.
prelude_lines = [
    line for line in prelude.splitlines()
    if not line.strip().startswith("<!--") or line.strip().endswith(START_MARKER)
]
prelude_body = "\n".join(prelude_lines).strip()

processed = 0
for path in sorted(agents_dir.glob("*.md")):
    if path.name == "_prelude.md":
        continue
    text = path.read_text(encoding="utf-8")
    if START_MARKER not in text:
        continue

    pattern = re.compile(
        re.escape(START_MARKER) + r"(.*?)" + re.escape(END_MARKER),
        re.DOTALL,
    )
    inlined_block = (
        f"{START_MARKER}\n{prelude_body}\n{END_MARKER}"
    )
    if pattern.search(text):
        new_text = pattern.sub(inlined_block.replace("\\", r"\\"), text)
    else:
        new_text = text.replace(START_MARKER, inlined_block, 1)

    if new_text != text:
        path.write_text(new_text, encoding="utf-8")
        processed += 1

print(f"resolve-preludes: {processed} file(s) updated")
PY_EOF
