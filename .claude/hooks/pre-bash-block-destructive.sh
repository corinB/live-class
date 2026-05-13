#!/usr/bin/env bash
# Bash 명령 실행 전에 파괴적인 작업을 감지해 차단하는 PreToolUse 훅입니다.
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
  deny "shell-reentry" "install jq or node to parse Claude hook JSON safely"
fi

if [ -z "$command_str" ]; then
  exit 0
fi

is_destructive() {
  local text="$1"

  if printf '%s' "$text" | grep -qE '(^|[[:space:];&|])rm[[:space:]]+(-[a-zA-Z]*[rR][a-zA-Z]*[fF]|-[a-zA-Z]*[fF][a-zA-Z]*[rR]|--recursive([[:space:]]+--force)?|--force[[:space:]]+--recursive)'; then
    deny "rm-rf" "recursive forced rm is blocked"
  fi

  if printf '%s' "$text" | grep -qE '(^|[[:space:];&|])find[[:space:]]+.*-delete([[:space:]]|$)'; then
    deny "find-delete" "find -delete is blocked"
  fi

  if printf '%s' "$text" | grep -qE '(^|[[:space:];&|])git[[:space:]]+(-c[[:space:]]+[^[:space:]]+[[:space:]]+)*push[[:space:]]+([^#[:space:]]+[[:space:]]+)*(-f([[:space:]]|$)|--force([[:space:]]|$)|--force-with-lease=[^[:space:]]*)'; then
    deny "push-force" "forced git push is blocked"
  fi

  if printf '%s' "$text" | grep -qE '(^|[[:space:];&|])git[[:space:]]+(-c[[:space:]]+[^[:space:]]+[[:space:]]+)*reset[[:space:]]+--hard'; then
    deny "reset-hard" "git reset --hard is blocked"
  fi

  if printf '%s' "$text" | grep -qE '(^|[[:space:];&|])git[[:space:]]+(-c[[:space:]]+[^[:space:]]+[[:space:]]+)*clean[[:space:]]+-[a-z]*f'; then
    deny "clean-force" "git clean -f is blocked"
  fi

  if printf '%s' "$text" | grep -qE '(^|[[:space:];&|])(Remove-Item|rd|rmdir)[[:space:]]+([^|]*(-Recurse|/[sS]\b))'; then
    deny "ps-recurse" "recursive Windows or PowerShell removal is blocked"
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

is_destructive "$command_str"

if inner_str=$(extract_shell_reentry_inner "$command_str"); then
  if [ -z "$inner_str" ]; then
    deny "shell-reentry" "shell re-entry command could not be parsed safely"
  fi
  is_destructive "$inner_str"
fi

exit 0
