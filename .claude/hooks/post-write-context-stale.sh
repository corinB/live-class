#!/usr/bin/env bash
# Write/Edit 도구가 추적 대상 문서를 만진 직후 audit-context-yaml.py를 호출해 stale 경고를 띄우는 훅
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
AUDIT_SCRIPT="${REPO_ROOT}/.claude/scripts/audit-context-yaml.py"

payload=$(cat)

extract_file_path() {
  local p="$1"
  if command -v jq >/dev/null 2>&1; then
    printf '%s' "${p}" | jq -r 'if (.tool_input.file_path? | type) == "string" then .tool_input.file_path else "" end'
  elif command -v node >/dev/null 2>&1; then
    PAYLOAD="${p}" node -e '
      const payload = JSON.parse(process.env.PAYLOAD || "{}");
      const fp = payload && payload.tool_input && payload.tool_input.file_path;
      if (typeof fp === "string") process.stdout.write(fp);
    '
  else
    printf ''
  fi
}

file_path=$(extract_file_path "${payload}")

if [ -z "${file_path}" ]; then
  exit 0
fi

# 추적 대상 매칭 — 정규식 union으로 좁게.
case "${file_path}" in
  *DOCS.md|*ARCHITECTURE.md|*README.md|*AGENTS-SKILLS-HARNESS.md|*ORCHESTRATION.md|*CLAUDE.md|\
  *.claude/agents/*.md|*.claude/skills/*.md|*docs/*.md)
    ;;
  *)
    exit 0
    ;;
esac

if [ ! -f "${AUDIT_SCRIPT}" ]; then
  printf '[hook:post-write-context-stale] audit 스크립트가 없습니다. .claude/scripts/audit-context-yaml.py 를 복원하세요.\n' >&2
  exit 0
fi

if ! command -v python >/dev/null 2>&1 && ! command -v python3 >/dev/null 2>&1; then
  exit 0
fi

PY=python
if ! command -v python >/dev/null 2>&1; then
  PY=python3
fi

set +e
output=$("${PY}" "${AUDIT_SCRIPT}" 2>&1)
exit_code=$?
set -e

case "${exit_code}" in
  0)
    exit 0
    ;;
  *)
    printf '[hook:post-write-context-stale] %s 변경 후 context.yaml drift 감지.\n' "${file_path}" >&2
    printf '%s\n' "${output}" >&2
    exit 0
    ;;
esac
