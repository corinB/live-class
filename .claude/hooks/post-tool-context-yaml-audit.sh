#!/usr/bin/env bash
# Write/Edit 도구가 context.yaml을 수정한 직후 audit-context-yaml.py를 실행해 drift를 경고하는 PostToolUse 훅
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
AUDIT_SCRIPT="${REPO_ROOT}/.claude/scripts/audit-context-yaml.py"

payload=$(cat)

if command -v jq >/dev/null 2>&1; then
  file_path=$(printf '%s' "${payload}" | jq -r '.tool_input.file_path // ""')
elif command -v node >/dev/null 2>&1; then
  file_path=$(
    PAYLOAD="${payload}" node -e '
      const p = JSON.parse(process.env.PAYLOAD || "{}");
      const ti = (p && p.tool_input) || {};
      process.stdout.write(ti.file_path || "");
    '
  )
else
  exit 0
fi

case "${file_path}" in
  context.yaml|*/context.yaml)
    ;;
  *)
    exit 0
    ;;
esac

if [ ! -f "${AUDIT_SCRIPT}" ]; then
  printf '[hook:post-tool-context-yaml-audit] audit script missing: %s\n' "${AUDIT_SCRIPT}" >&2
  exit 0
fi

PY=python
command -v python >/dev/null 2>&1 || PY=python3

set +e
output=$("${PY}" "${AUDIT_SCRIPT}" 2>&1)
rc=$?
set -e

if [ "${rc}" -ne 0 ]; then
  printf '[hook:post-tool-context-yaml-audit] context.yaml drift detected after write (informational):\n' >&2
  printf '%s\n' "${output}" >&2
fi

exit 0