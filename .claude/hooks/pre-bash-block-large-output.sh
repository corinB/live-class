#!/usr/bin/env bash
# 대용량 stdout을 만들 가능성이 높은 Bash 명령을 차단해 surrogate-split을 예방하는 PreToolUse 훅입니다.
set -euo pipefail

deny() {
  local category="$1"
  local message="$2"
  printf '{"permissionDecision":"deny","reason":"%s: %s"}' "$category" "$message"
  exit 2
}

payload=$(cat)

if command -v jq >/dev/null 2>&1; then
  command_str=$(
    printf '%s' "$payload" |
      jq -r 'if (.tool_input.command? | type) == "string" then .tool_input.command else "" end'
  )
elif command -v node >/dev/null 2>&1; then
  command_str=$(
    PAYLOAD="$payload" node -e '
      const payload = JSON.parse(process.env.PAYLOAD || "{}");
      const command = payload && payload.tool_input && payload.tool_input.command;
      if (typeof command === "string") process.stdout.write(command);
    '
  )
else
  deny "parser-missing" "install jq or node to parse Claude hook JSON safely"
fi

if [ -z "$command_str" ]; then
  exit 0
fi

has_git_log_limit() {
  local text="$1"
  if printf '%s' "$text" | grep -qE '(-n[[:space:]]+[0-9]+|--max-count[=[:space:]]+[0-9]+|[[:space:]]-[0-9]+([[:space:]]|$)|\|[[:space:]]*head\b|\|[[:space:]]*tail\b)'; then
    return 0
  fi
  return 1
}

has_docker_logs_limit() {
  local text="$1"
  if printf '%s' "$text" | grep -qE '(--tail[=[:space:]]+[0-9]+|\|[[:space:]]*head\b|\|[[:space:]]*tail\b)'; then
    return 0
  fi
  return 1
}

is_large_output() {
  local text="$1"

  if printf '%s' "$text" | grep -qE '(^|[[:space:];&|])git[[:space:]]+(-c[[:space:]]+[^[:space:]]+[[:space:]]+)*status[[:space:]]+(-[a-zA-Z]*[[:space:]]+)*(-uall|--untracked-files=all)\b'; then
    deny "git-status-uall" "git status -uall floods output; drop -uall or pipe through head"
  fi

  if printf '%s' "$text" | grep -qE '(^|[[:space:];&|])git[[:space:]]+(-c[[:space:]]+[^[:space:]]+[[:space:]]+)*log\b'; then
    if ! has_git_log_limit "$text"; then
      deny "git-log" "git log without a row cap floods output; add -n 100, --max-count=N, or | head -200"
    fi
  fi

  if printf '%s' "$text" | grep -qE '(^|[[:space:];&|])docker[[:space:]]+(compose[[:space:]]+)?logs\b'; then
    if ! has_docker_logs_limit "$text"; then
      deny "docker-logs" "docker logs without --tail floods output; add --tail 200 or | head -200"
    fi
  fi
}

extract_shell_reentry_inner() {
  local text="$1"

  if [[ "$text" =~ ^bash[[:space:]]+-c[[:space:]]+\"([^\"]*)\"$ ]]; then
    printf '%s' "${BASH_REMATCH[1]}"
  elif [[ "$text" =~ ^bash[[:space:]]+-c[[:space:]]+\'([^\']*)\'$ ]]; then
    printf '%s' "${BASH_REMATCH[1]}"
  elif [[ "$text" =~ ^sh[[:space:]]+-c[[:space:]]+\"([^\"]*)\"$ ]]; then
    printf '%s' "${BASH_REMATCH[1]}"
  elif [[ "$text" =~ ^sh[[:space:]]+-c[[:space:]]+\'([^\']*)\'$ ]]; then
    printf '%s' "${BASH_REMATCH[1]}"
  elif [[ "$text" =~ ^cmd[[:space:]]+/c[[:space:]]+\"([^\"]*)\"$ ]]; then
    printf '%s' "${BASH_REMATCH[1]}"
  elif [[ "$text" =~ ^cmd\.exe[[:space:]]+/c[[:space:]]+\"([^\"]*)\"$ ]]; then
    printf '%s' "${BASH_REMATCH[1]}"
  elif [[ "$text" =~ ^powershell[[:space:]]+-Command[[:space:]]+\"([^\"]*)\"$ ]]; then
    printf '%s' "${BASH_REMATCH[1]}"
  elif [[ "$text" =~ ^pwsh[[:space:]]+-Command[[:space:]]+\"([^\"]*)\"$ ]]; then
    printf '%s' "${BASH_REMATCH[1]}"
  elif [[ "$text" =~ ^(bash[[:space:]]+-c|sh[[:space:]]+-c|cmd[[:space:]]+/c|cmd\.exe[[:space:]]+/c|powershell[[:space:]]+-Command|pwsh[[:space:]]+-Command) ]]; then
    deny "shell-reentry" "shell re-entry command could not be parsed safely"
  else
    return 1
  fi
}

is_large_output "$command_str"

if inner_str=$(extract_shell_reentry_inner "$command_str"); then
  if [ -z "$inner_str" ]; then
    deny "shell-reentry" "shell re-entry command could not be parsed safely"
  fi
  is_large_output "$inner_str"
fi

exit 0
