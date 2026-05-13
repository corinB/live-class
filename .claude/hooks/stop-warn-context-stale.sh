#!/usr/bin/env bash
# 세션 종료 직전 audit-context-yaml.py를 호출해 stale 항목을 stderr 경고로만 알리는 훅(warn-only)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
AUDIT_SCRIPT="${REPO_ROOT}/.claude/scripts/audit-context-yaml.py"

# stdin payload 소비 (사용하지 않음).
_payload=$(cat)

if [ ! -f "${AUDIT_SCRIPT}" ]; then
  printf '[hook:stop-warn-context-stale] audit 스크립트가 없습니다. .claude/scripts/audit-context-yaml.py 를 복원하세요.\n' >&2
  exit 0
fi

if ! command -v python >/dev/null 2>&1 && ! command -v python3 >/dev/null 2>&1; then
  printf '[hook:stop-warn-context-stale] python 미설치로 audit 을 실행하지 못했습니다.\n' >&2
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
  1)
    printf '[hook:stop-warn-context-stale] context.yaml drift detected — refresh before next session.\n' >&2
    printf '%s\n' "${output}" >&2
    exit 0
    ;;
  *)
    printf '[hook:stop-warn-context-stale] audit failed (exit %d).\n' "${exit_code}" >&2
    printf '%s\n' "${output}" >&2
    exit 0
    ;;
esac
