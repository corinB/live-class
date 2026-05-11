#!/usr/bin/env bash
# Claude Code 세션 시작 시 파이프라인 상태 배지를 컨텍스트에 출력하는 훅
set -euo pipefail

# 스크립트 위치 기준으로 lib 파일 경로 결정
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=_lib-pipeline-state.sh
source "${SCRIPT_DIR}/_lib-pipeline-state.sh"

# stdin JSON 소비 (Claude Code hook payload — 이 훅에서는 내용 불필요)
if command -v jq > /dev/null 2>&1; then
  _payload=$(cat)
else
  _payload=$(cat)
fi

# 파이프라인 배지를 additionalContext로 출력
pipeline_state_badge
