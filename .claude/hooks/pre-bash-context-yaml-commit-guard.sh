#!/usr/bin/env bash
# git commit 실행 전 context.yaml이 staged된 경우 audit-context-yaml.py로 drift를 검사해 차단하는 PreToolUse 훅
set -euo pipefail

if [ "${CONTEXT_GUARD_OFF:-}" = "1" ]; then
  printf '[hook:pre-bash-context-yaml-commit-guard] CONTEXT_GUARD_OFF=1 -- guard bypassed\n' >&2
  exit 0
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
AUDIT_SCRIPT="${REPO_ROOT}/.claude/scripts/audit-context-yaml.py"

deny() {
  local reason="$1"
  local safe="${reason//\"/\'}"
  printf '{"permissionDecision":"deny","reason":"%s"}' "${safe}"
  exit 2
}

payload=$(cat)

if command -v jq >/dev/null 2>&1; then
  command_str=$(printf '%s' "${payload}" | jq -r '.tool_input.command // ""')
elif command -v node >/dev/null 2>&1; then
  command_str=$(
    PAYLOAD="${payload}" node -e '
      const p = JSON.parse(process.env.PAYLOAD || "{}");
      const ti = (p && p.tool_input) || {};
      process.stdout.write(ti.command || "");
    '
  )
else
  exit 0
fi

if ! printf '%s' "${command_str}" | grep -qE '(^|[[:space:];|&])git[[:space:]]+commit([[:space:]]|$)'; then
  exit 0
fi

cd "${REPO_ROOT}"
if ! git diff --cached --name-only 2>/dev/null | grep -q '^context\.yaml$'; then
  exit 0
fi

if [ ! -f "${AUDIT_SCRIPT}" ]; then
  printf '[hook:pre-bash-context-yaml-commit-guard] audit script missing\n' >&2
  exit 0
fi

PY=python
command -v python >/dev/null 2>&1 || PY=python3

set +e
output=$("${PY}" "${AUDIT_SCRIPT}" 2>&1)
rc=$?
set -e

case "${rc}" in
  0)
    exit 0
    ;;
  1)
    printf '[hook:pre-bash-context-yaml-commit-guard] WARNING: context.yaml drift (last_indexed stale). Fix before merging.\n' >&2
    printf '%s\n' "${output}" >&2
    exit 0
    ;;
  *)
    deny "context.yaml drift detected: ${output}"
    ;;
esac