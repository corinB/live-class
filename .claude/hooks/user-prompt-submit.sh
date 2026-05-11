#!/usr/bin/env bash
# 사용자 프롬프트 제출마다 파이프라인 상태 배지를 컨텍스트에 앞에 추가하는 훅
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=_lib-pipeline-state.sh
source "${SCRIPT_DIR}/_lib-pipeline-state.sh"

# stdin JSON 소비
if command -v jq > /dev/null 2>&1; then
  _payload=$(cat)
else
  _payload=$(cat)
fi

# 파이프라인 배지를 additionalContext로 출력
pipeline_state_badge
