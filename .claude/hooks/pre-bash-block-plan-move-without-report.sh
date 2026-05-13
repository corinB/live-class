#!/usr/bin/env bash
# `git mv|mv|Move-Item plan/before/NN_*.md plan/after/...` 명령 실행 직전 reports/NN_*.md 누락 시 차단하는 훅
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

# plan-move 매처: `mv`, `git mv`, `Move-Item` 셋 중 하나로 plan/before/NN_ 이동.
# 명령의 시작 또는 셸 separator(;, &, |) 직후 토큰이어야 한다.
plan_move_re='(^|[;&|][[:space:]]*)((git[[:space:]]+)?mv|[Mm]ove(-Item)?)[[:space:]]+'

# shell re-entry 안쪽 문자열 추출 (bash/sh/pwsh/powershell -c|-Command "..." / cmd /c "...").
inner_text=$(printf '%s' "${command_text}" | sed -nE "s/.*\\b(bash|sh|pwsh|powershell)\\b([[:space:]]+(-c|-Command))?[[:space:]]+['\"]([^'\"]*)['\"].*/\\4/p" | head -n1)
if [ -z "${inner_text}" ]; then
  inner_text=$(printf '%s' "${command_text}" | sed -nE 's/.*\bcmd(\.exe)?\b[[:space:]]+\/[cC][[:space:]]+["'"'"']([^"'"'"']*)["'"'"'].*/\2/p' | head -n1)
fi

matched_in_raw=0
matched_in_inner=0
if printf '%s' "${command_text}" | grep -qE "${plan_move_re}" \
   && printf '%s' "${command_text}" | grep -qE 'plan/before/[0-9]{2}_'; then
  matched_in_raw=1
fi
if [ "${matched_in_raw}" -eq 0 ] && [ -n "${inner_text}" ] \
   && printf '%s' "${inner_text}" | grep -qE "${plan_move_re}" \
   && printf '%s' "${inner_text}" | grep -qE 'plan/before/[0-9]{2}_'; then
  matched_in_inner=1
fi
if [ "${matched_in_raw}" -eq 0 ] && [ "${matched_in_inner}" -eq 0 ]; then
  exit 0
fi

# NN 2자리 추출
source_text="${command_text}"
if [ "${matched_in_inner}" -eq 1 ]; then
  source_text="${inner_text}"
fi
nn=$(printf '%s' "${source_text}" | sed -nE "s@.*plan/before/([0-9]{2})_[^[:space:]\"']*\\.md.*@\\1@p" | head -n1)

if [ -z "${nn}" ]; then
  exit 0
fi

# 매칭 보고서 검색 — reports/NN_*.md 가 존재해야 한다 (.gitkeep 제외)
matches=$(find reports -maxdepth 1 -name "${nn}_*.md" ! -name ".gitkeep" -print 2>/dev/null | head -n5)

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
  3. 작성 완료 후 다시 plan/before -> plan/after 이동 명령을 실행합니다.

본 훅은 context.yaml 의 \`pipeline_state_files\` 정책과 \`context_map.directories.reports\` 형식 규칙을 강제합니다.
EOF
  exit 2
fi

exit 0
