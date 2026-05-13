#!/usr/bin/env bash
# `git mv|mv|Move-Item plan/before/NN_*.md plan/after/...` 명령 직전 work-order 체크리스트 미완료 시 차단하는 훅
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

# plan-move 매처
plan_move_re='(^|[;&|][[:space:]]*)((git[[:space:]]+)?mv|[Mm]ove(-Item)?)[[:space:]]+'

# shell re-entry 안쪽 문자열 추출
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

# 소스 파일 경로 추출 (raw 또는 inner)
source_text="${command_text}"
if [ "${matched_in_inner}" -eq 1 ]; then
  source_text="${inner_text}"
fi
src_path=$(printf '%s' "${source_text}" | sed -nE "s@.*[[:space:]\"'](plan/before/[0-9]{2}_[^[:space:]\"']*\\.md).*@\\1@p" | head -n1)

if [ -z "${src_path}" ]; then
  exit 0
fi

# 파일이 존재하지 않으면 통과 (다른 hook 또는 git 자체가 처리)
if [ ! -f "${src_path}" ]; then
  exit 0
fi

# `- [ ]` 체크박스가 하나라도 있으면 차단. 대소문자 무시, 공백 1자 가정 ('- [ ]').
unchecked_count=$(grep -cE '^[[:space:]]*-[[:space:]]+\[[[:space:]]\]' "${src_path}" 2>/dev/null || echo 0)

if [ "${unchecked_count}" -gt 0 ]; then
  cat >&2 <<EOF
[hook:pre-bash-block-plan-move-with-unchecked] 차단: work-order 의 체크리스트가 갱신되지 않았습니다.

원인:
  실행하려던 명령: ${command_text}
  대상 파일: ${src_path}
  미완료 체크박스 수: ${unchecked_count} 개 (\`- [ ]\` 패턴)

해결:
  1. ${src_path} 를 열어 모든 \`- [ ]\` 를 \`- [x]\` 로 갱신합니다.
  2. 실제로 구현되지 않은 항목이 있다면 동일 PR 또는 별도 follow-up 으로 마무리하거나, PR 본문의 '리뷰어 주의사항' 에 사유를 명시하고 해당 항목만 의식적으로 \`- [~]\` (보류 표시) 등 다른 마커로 변경합니다.
  3. 갱신 후 다시 plan/before -> plan/after 이동 명령을 실행합니다.

본 훅은 work-order 의 DoD 와 Action Items 가 실제 구현과 동기화되도록 강제합니다.
EOF
  exit 2
fi

exit 0
