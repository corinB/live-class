#!/usr/bin/env bash
# SessionStart 시 context.yaml drift 여부를 조용히 확인해 문제가 있으면 additionalContext로 알리는 훅
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
AUDIT_SCRIPT="${REPO_ROOT}/.claude/scripts/audit-context-yaml.py"

if [ ! -f "${AUDIT_SCRIPT}" ]; then
  exit 0
fi

PY=python
command -v python >/dev/null 2>&1 || PY=python3

set +e
output=$("${PY}" "${AUDIT_SCRIPT}" 2>&1)
rc=$?
set -e

if [ "${rc}" -eq 0 ]; then
  exit 0
fi

count=$(printf '%s' "${output}" | grep -c '^\s*-' 2>/dev/null || printf '?')

printf '{"additionalContext":"[context-yaml] drift: %s broken ref(s) detected. Run: python .claude/scripts/audit-context-yaml.py"}' \
  "${count}"

exit 0