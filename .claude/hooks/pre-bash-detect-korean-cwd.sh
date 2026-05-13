#!/usr/bin/env bash
# Bash 실행 직전 현재 작업 디렉토리에 비-ASCII(한글 등) 문자가 섞여 있으면 JVM 명령은 차단하고 그 외는 1회 경고하는 훅
set -euo pipefail

# stdin payload 소비
payload=$(cat)

# tool_name 게이트 — Bash 가 아니면 통과
tool_name=$(printf '%s' "${payload}" | sed -nE 's/.*"tool_name"[[:space:]]*:[[:space:]]*"([^"]+)".*/\1/p' | head -n1)
if [ -n "${tool_name}" ] && [ "${tool_name}" != "Bash" ]; then
  exit 0
fi

# command 추출 (jq 우선, node fallback)
command_str=""
if command -v jq >/dev/null 2>&1; then
  command_str=$(printf '%s' "${payload}" | jq -r 'if (.tool_input.command? | type) == "string" then .tool_input.command else "" end' 2>/dev/null || true)
elif command -v node >/dev/null 2>&1; then
  command_str=$(PAYLOAD="${payload}" node -e '
    try {
      const p = JSON.parse(process.env.PAYLOAD || "{}");
      const c = p && p.tool_input && p.tool_input.command;
      if (typeof c === "string") process.stdout.write(c);
    } catch (e) {}
  ' 2>/dev/null || true)
fi

# cwd 의 비-ASCII 바이트 검사 (LC_ALL=C 로 raw byte 처리)
cwd=$(pwd -P 2>/dev/null || pwd)
non_ascii=$(LC_ALL=C printf '%s' "${cwd}" | LC_ALL=C grep -lE $'[\x80-\xff]' 2>/dev/null || true)

# ASCII 만 있으면 그대로 통과
if [ -z "${non_ascii}" ]; then
  exit 0
fi

# JVM 관련 명령은 deny (한글 경로 ClassNotFoundException 회피)
if printf '%s' "${command_str}" | grep -qE '(^|[[:space:];&|])(\./gradlew|gradlew\.bat|mvn|mvnw|java[[:space:]]+-jar|kotlin[[:space:]]+-jar)'; then
  printf '{"permissionDecision":"deny","reason":"korean-cwd: JVM 명령은 한글 경로에서 ClassNotFoundException 위험이 큽니다. ASCII 경로 워크트리에서 실행하세요."}'
  cat >&2 <<EOF
[hook:pre-bash-detect-korean-cwd] 차단: 비-ASCII 경로에서 JVM 명령 실행 시도.

원인:
  현재 cwd: ${cwd}
  실행하려던 명령: ${command_str}

해결:
  1) ASCII 경로에 git worktree 를 만든다.
     예) git worktree add /c/work/p-ascii main
  2) 해당 워크트리로 이동해서 같은 명령을 다시 실행한다.
     cd /c/work/p-ascii/live-class && ./gradlew test

본 훅은 Spring Boot bootRun/test 가 한글 경로에서 ClassNotFoundException 으로 죽는 회귀를 방지합니다.
EOF
  exit 2
fi

# JVM 외 명령은 세션당 1회만 stderr warn. sentinel 파일로 가드.
session_id=$(printf '%s' "${payload}" | sed -nE 's/.*"session_id"[[:space:]]*:[[:space:]]*"([^"]+)".*/\1/p' | head -n1)
sentinel_dir="${TMPDIR:-/tmp}"
sentinel="${sentinel_dir}/claude-korean-cwd-warned-${session_id:-default}"

if [ ! -f "${sentinel}" ]; then
  printf '' > "${sentinel}" 2>/dev/null || true
  cat >&2 <<EOF
[hook:pre-bash-detect-korean-cwd] 경고: 비-ASCII 경로(${cwd})에서 작업 중입니다. JVM 명령(./gradlew, mvn, java -jar)은 차단됩니다. 이 메시지는 세션당 1회만 출력됩니다.
EOF
fi

exit 0
