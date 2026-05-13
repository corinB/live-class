#!/usr/bin/env bash
# Claude Code 세션 시작 시 파이프라인 상태 배지와 _prelude.md 존재 검증을 수행하는 훅
set -euo pipefail

# 스크립트 위치 기준으로 lib 파일 경로 결정
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
PRELUDE_PATH="${REPO_ROOT}/.claude/agents/_prelude.md"
# shellcheck source=_lib-pipeline-state.sh
source "${SCRIPT_DIR}/_lib-pipeline-state.sh"

# stdin JSON 소비 (Claude Code hook payload — 이 훅에서는 내용 불필요)
_payload=$(cat)

# _prelude.md 존재 검증. sentinel 본문이 없으면 6 에이전트의 Project Context 블록이
# 부정확해질 수 있으므로 세션 시작을 차단하고 운영자에게 build script 재실행을 안내한다.
if [ ! -f "${PRELUDE_PATH}" ]; then
  cat >&2 <<EOF
[hook:session-start] 차단: .claude/agents/_prelude.md 가 없어 6 서브에이전트의 Project Context 블록을 재구성할 수 없습니다.

해결:
  1. .claude/agents/_prelude.md 를 복원하거나 git history 에서 체크아웃합니다.
  2. bash .claude/scripts/resolve-preludes.sh 를 실행해 sentinel 영역을 다시 채웁니다.
  3. 세션을 재시작합니다.
EOF
  exit 2
fi

# 파이프라인 배지를 additionalContext로 출력
pipeline_state_badge
