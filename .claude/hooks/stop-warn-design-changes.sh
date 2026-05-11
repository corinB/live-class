#!/usr/bin/env bash
# 세션 종료 시 DOCS.md 또는 ARCHITECTURE.md 변경 여부를 감지해 경고를 출력하는 훅
set -euo pipefail

# stdin JSON 소비
_payload=$(cat)

# git이 초기화되지 않은 경우 조용히 종료
if ! git rev-parse --is-inside-work-tree > /dev/null 2>&1; then
  exit 0
fi

# HEAD 대비 변경된 파일 목록 확인
changed_files=$(git diff --name-only HEAD 2>/dev/null || true)

if echo "${changed_files}" | grep -qE "^(DOCS\.md|ARCHITECTURE\.md)$"; then
  echo "[hook:stop] 경고: DOCS.md 또는 ARCHITECTURE.md 가 변경되었습니다. 다운스트림 에이전트(scrum-task-decomposer, infra-cicd-operator)가 최신 내용을 반영해야 할 수 있습니다."
fi

exit 0
