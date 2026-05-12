#!/usr/bin/env bash
# `git mv plan/before/NN_*.md plan/after/...` 명령 실행 직전에 reports/NN_*.md 가 있는지 검사해 누락 시 차단하는 훅
set -euo pipefail

# stdin JSON payload 소비. Claude Code 가 PreToolUse 훅에 ToolUse JSON 을 전달한다.
_payload=$(cat)

# Bash 도구가 아닌 호출은 그대로 통과 (matcher 가 Bash 이지만 안전망)
tool_name=$(printf '%s' "${_payload}" | sed -nE 's/.*"tool_name"[[:space:]]*:[[:space:]]*"([^"]+)".*/\1/p' | head -n1)
if [ -n "${tool_name}" ] && [ "${tool_name}" != "Bash" ]; then
  exit 0
fi

# command 필드 추출
command_text=$(printf '%s' "${_payload}" | sed -nE 's/.*"command"[[:space:]]*:[[:space:]]*"((\\.|[^"\\])*)".*/\1/p' | head -n1)

# JSON escape 해제 (가장 흔한 \" \\ \n 만 처리)
command_text=${command_text//\\\"/\"}
command_text=${command_text//\\\\/\\}

# `git mv plan/before/NN_xxx.md plan/after/...` 패턴이 아니면 통과
# 명령의 시작 또는 셸 separator(;, &, |) 직후 토큰이 `git mv plan/before/NN_` 이어야 매칭.
# 이는 echo 인자 등 다른 명령 안에 포함된 같은 텍스트가 false-positive 로 잡히지 않도록 함.
if ! printf '%s' "${command_text}" | grep -qE '(^|[;&|][[:space:]]*)git[[:space:]]+mv[[:space:]]+plan/before/[0-9]{2}_'; then
  exit 0
fi

# plan/before/NN_*.md 에서 NN 2자리 추출 (여러 개여도 첫 번째만)
nn=$(printf '%s' "${command_text}" | sed -nE 's@.*plan/before/([0-9]{2})_[^[:space:]]*\.md.*@\1@p' | head -n1)

if [ -z "${nn}" ]; then
  exit 0
fi

# 매칭 보고서 검색 — reports/NN_*.md 가 존재해야 한다 (.gitkeep 제외)
matches=$(find reports -maxdepth 1 -name "${nn}_*.md" ! -name ".gitkeep" 2>/dev/null | head -n5)

if [ -z "${matches}" ]; then
  cat >&2 <<EOF
[hook:pre-bash-block-plan-move-without-report] 차단: task ${nn} 의 종료 보고서가 누락되었습니다.

원인:
  실행하려던 명령: ${command_text}
  필수 보고서 경로 패턴: reports/${nn}_<agent-name>_<YYYY-MM-DD>.md
  현재 reports/ 에서 ${nn}_* 매칭 파일이 발견되지 않았습니다.

해결:
  1. .claude/templates/report.md.template 을 기반으로 reports/${nn}_*.md 를 작성합니다.
  2. Input Summary / What Was Done / Rationale & Tradeoffs / Follow-ups 4개 섹션을 채웁니다.
  3. 작성 완료 후 다시 plan/before → plan/after 이동 명령을 실행합니다.

본 훅은 context.yaml 의 \`pipeline_state_files\` 정책과 \`context_map.directories.reports\` 형식 규칙을 강제합니다.
EOF
  exit 2
fi

exit 0
