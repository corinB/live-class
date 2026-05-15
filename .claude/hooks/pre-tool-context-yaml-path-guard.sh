#!/usr/bin/env bash
# context.yaml 변경 시 새로 추가된 docs/ 경로가 실재하는지 검사하는 PreToolUse 가드
set -euo pipefail

# Escape hatch
if [ "${CONTEXT_GUARD_OFF:-}" = "1" ]; then
  printf '[hook:pre-tool-context-yaml-path-guard] CONTEXT_GUARD_OFF=1 -- guard bypassed\n' >&2
  exit 0
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

deny() {
  local reason="$1"
  local safe="${reason//\"/\'}"
  printf '{"permissionDecision":"deny","reason":"%s"}' "${safe}"
  exit 2
}

payload=$(cat)

# Extract file_path and content from PreToolUse payload
if command -v jq >/dev/null 2>&1; then
  file_path=$(printf '%s' "${payload}" | jq -r '.tool_input.file_path // ""')
  content=$(printf '%s' "${payload}" | jq -r '.tool_input.content // .tool_input.new_string // ""')
elif command -v node >/dev/null 2>&1; then
  file_path=$(
    PAYLOAD="${payload}" node -e '
      const p = JSON.parse(process.env.PAYLOAD || "{}");
      const ti = (p && p.tool_input) || {};
      process.stdout.write(ti.file_path || "");
    '
  )
  content=$(
    PAYLOAD="${payload}" node -e '
      const p = JSON.parse(process.env.PAYLOAD || "{}");
      const ti = (p && p.tool_input) || {};
      process.stdout.write(ti.content || ti.new_string || "");
    '
  )
else
  exit 0
fi

# Only guard context.yaml writes
case "${file_path}" in
  context.yaml|*/context.yaml)
    ;;
  *)
    exit 0
    ;;
esac

if [ -z "${content}" ]; then
  exit 0
fi

# Extract path values from proposed content and verify each file exists
broken_paths=()
while IFS= read -r line; do
  stripped="${line#"${line%%[! ]*}"}"
  if [[ "${stripped}" =~ ^-?[[:space:]]*path:[[:space:]]*\"?([^\"#]+)\"?[[:space:]]*(#.*)?$ ]]; then
    raw_path="${BASH_REMATCH[1]}"
    raw_path="${raw_path%"${raw_path##*[![:space:]]}"}"
    [ -z "${raw_path}" ] && continue
    [[ "${raw_path}" == *"*"* ]] && continue
    abs_path="${REPO_ROOT}/${raw_path}"
    if [ ! -f "${abs_path}" ]; then
      broken_paths+=("${raw_path}")
    fi
  fi
done <<< "${content}"

if [ ${#broken_paths[@]} -gt 0 ]; then
  joined=$(printf '%s, ' "${broken_paths[@]}")
  joined="${joined%, }"
  deny "context.yaml: path(s) not found on disk -- ${joined}"
fi

exit 0