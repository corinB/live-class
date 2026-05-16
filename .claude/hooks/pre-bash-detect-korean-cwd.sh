#!/usr/bin/env bash
# Bash 또는 PowerShell 실행 직전, 현재 작업 디렉토리에 비-ASCII(한글 등) 문자가 섞여 있으면 JVM 명령만 차단하고 그 외는 1회 경고하는 훅. heredoc body 안의 텍스트는 무시한다.
set -euo pipefail

# Escape hatch
if [ -n "${KOREAN_CWD_GUARD_OFF:-}" ]; then
  cat >/dev/null
  exit 0
fi

payload=$(cat)

tool_name=$(printf '%s' "${payload}" | sed -nE 's/.*"tool_name"[[:space:]]*:[[:space:]]*"([^"]+)".*/\1/p' | head -n1)
# Bash 와 PowerShell 외 도구는 통과
if [ -n "${tool_name}" ] && [ "${tool_name}" != "Bash" ] && [ "${tool_name}" != "PowerShell" ]; then
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

# heredoc body 제거 — bash 의 <<TOKEN ... TOKEN 형태와 PowerShell @'..'@ / @"..."@ 형태 둘 다 처리
# heredoc body 안의 텍스트는 명령 토큰이 아니므로 검사 대상이 아니다.
if command -v node >/dev/null 2>&1; then
  command_str=$(CMD="${command_str}" node -e '
    let s = process.env.CMD || "";
    s = s.replace(/<<-?\s*([\047"]?)([A-Za-z_][A-Za-z_0-9]*)\1[^\n]*\n[\s\S]*?\n\s*\2(?=\s|$)/g, "");
    s = s.replace(/@\047\s*\n[\s\S]*?\n\047@/g, "");
    s = s.replace(/@"\s*\n[\s\S]*?\n"@/g, "");
    process.stdout.write(s);
  ' 2>/dev/null || printf '%s' "${command_str}")
fi

# cwd 의 비-ASCII 바이트 검사
cwd=$(pwd -P 2>/dev/null || pwd)
non_ascii=$(LC_ALL=C printf '%s' "${cwd}" | LC_ALL=C grep -lE $'[\x80-\xff]' 2>/dev/null || true)

if [ -z "${non_ascii}" ]; then
  exit 0
fi

# JVM 관련 명령은 deny (한글 경로 ClassNotFoundException 회피)
if printf '%s' "${command_str}" | grep -qE '(^|[[:space:];&|])(\./gradlew|gradlew\.bat|mvn|mvnw|java[[:space:]]+-jar|kotlin[[:space:]]+-jar)'; then
  # ASCII 경로(/c/work/* 또는 C:/work/*)로 cd 한 뒤 JVM 을 실행하는 형태는 통과 —
  # 실제 JVM 의 user.dir 은 ASCII 경로가 되어 ClassNotFoundException 회귀 위험이 없다.
  if printf '%s' "${command_str}" | grep -qE '(^|[[:space:];&|])cd[[:space:]]+"?(/c/|C:/|/[A-Za-z]/)work/[A-Za-z0-9._/-]+'; then
    exit 0
  fi
  printf '{"permissionDecision":"deny","reason":"korean-cwd: JVM 명령은 한글 경로에서 ClassNotFoundException 위험이 큽니다. ASCII 경로 워크트리에서 실행하세요. (KOREAN_CWD_GUARD_OFF=1 로 우회 가능)"}'
  cat >&2 <<EOF
[hook:pre-bash-detect-korean-cwd] 차단: 비-ASCII 경로에서 JVM 명령 실행 시도.

원인:
  현재 cwd: ${cwd}
  실행하려던 명령(heredoc 제거 후): ${command_str}

해결:
  1) ASCII 경로에 git worktree 를 만든다.
     예) git worktree add /c/work/p-ascii main
  2) 해당 워크트리로 이동해서 같은 명령을 다시 실행한다.
     cd /c/work/p-ascii/live-class && ./gradlew test
  3) 또는 의도된 실행이라면 환경변수로 우회: KOREAN_CWD_GUARD_OFF=1.

본 훅은 Spring Boot bootRun/test 가 한글 경로에서 ClassNotFoundException 으로 죽는 회귀를 방지합니다.
EOF
  exit 2
fi

# JVM 외 명령은 세션당 1회만 stderr warn
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
