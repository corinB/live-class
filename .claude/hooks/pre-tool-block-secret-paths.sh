#!/usr/bin/env bash
# Bash/PowerShell 명령 안에서 .env, *.key, *.pem, credentials*, secrets* 같은 시크릿 파일을 읽으려는 시도를 정규식으로 차단하는 PreToolUse 훅. permissions.deny 의 prefix-matching 한계(인자 순서·alias·.NET 직접 호출) 를 메운다.
set -euo pipefail

payload=$(cat)

tool_name=$(printf '%s' "${payload}" | sed -nE 's/.*"tool_name"[[:space:]]*:[[:space:]]*"([^"]+)".*/\1/p' | head -n1)
if [ -n "${tool_name}" ] && [ "${tool_name}" != "Bash" ] && [ "${tool_name}" != "PowerShell" ]; then
  exit 0
fi

command_str=""
if command -v jq >/dev/null 2>&1; then
  command_str=$(printf '%s' "${payload}" | jq -r 'if (.tool_input.command? | type) == "string" then .tool_input.command else "" end' 2>/dev/null || true)
elif command -v node >/dev/null 2>&1; then
  command_str=$(PAYLOAD="${payload}" node -e 'try{const p=JSON.parse(process.env.PAYLOAD||"{}");const c=p&&p.tool_input&&p.tool_input.command;if(typeof c==="string")process.stdout.write(c)}catch(e){}' 2>/dev/null || true)
fi

if [ -z "${command_str}" ]; then
  exit 0
fi

# secret 파일 경로 패턴: .env / .env.* / *.key / *.pem / credentials* / secrets*
secret_pattern='(\.env(\.[A-Za-z0-9_-]+)?|[A-Za-z0-9_./\\-]*\.(key|pem)|credentials[A-Za-z0-9_./\\-]*|secrets[A-Za-z0-9_./\\-]*)'

# read 행위를 의미하는 cmdlet / 명령 / .NET 직접 호출 패턴
read_pattern='(cat|type|more|head|tail|less|view|Get-Content|gc|Select-String|sls|Format-Hex|fhx|Get-Item|gi|Get-ItemProperty|gp|Import-Csv|Import-Clixml|ConvertFrom-Json|Tee-Object|tee|Out-File|out-file|ReadAllText|ReadAllBytes|ReadAllLines|ReadLines|File\.OpenRead|StreamReader)'

# 1) read 명령 뒤에 secret 경로가 등장 (예: `cat .env`, `Select-String -Pattern x -Path .env`)
if printf '%s' "${command_str}" | grep -qE "${read_pattern}[[:space:]].*${secret_pattern}"; then
  printf '{"permissionDecision":"deny","reason":"secret-path: secret 파일(.env, *.key, *.pem, credentials*, secrets*) 을 읽으려는 명령이 감지됐습니다. 필요한 경우 환경변수를 직접 설정하거나, KOREAN_CWD_GUARD_OFF 와 유사한 우회 정책 없이는 차단됩니다."}'
  cat >&2 <<EOF
[hook:pre-tool-block-secret-paths] 차단: 시크릿 파일 읽기 시도.
  도구: ${tool_name}
  명령: ${command_str}

이 훅은 permissions.deny 의 prefix-matching 한계(인자 순서·alias·.NET 직접 호출) 를 메우기 위해 정규식으로 read 의도+secret 경로 조합을 차단합니다.
EOF
  exit 2
fi

# 2) Get-Content / cat / type / Select-String 같은 명령 인자에 -Path 또는 -LiteralPath 로 secret 지정
if printf '%s' "${command_str}" | grep -qE "(-Path|-LiteralPath|-FilePath|--file)[[:space:]]+[\"'\047]?${secret_pattern}"; then
  printf '{"permissionDecision":"deny","reason":"secret-path: secret 파일을 -Path/-LiteralPath 로 지정해 읽는 명령이 감지됐습니다."}'
  cat >&2 <<EOF
[hook:pre-tool-block-secret-paths] 차단: -Path 인자로 시크릿 파일 지정.
  도구: ${tool_name}
  명령: ${command_str}
EOF
  exit 2
fi

# 3) .NET 메서드 직접 호출 (인자에 secret 경로)
if printf '%s' "${command_str}" | grep -qE "\[(IO\.)?File\]::Read[A-Za-z]+\([\"'\047]${secret_pattern}"; then
  printf '{"permissionDecision":"deny","reason":"secret-path: [IO.File]::Read* 로 secret 파일을 읽는 .NET 직접 호출이 감지됐습니다."}'
  cat >&2 <<EOF
[hook:pre-tool-block-secret-paths] 차단: .NET File 직접 호출로 시크릿 파일 읽기.
  도구: ${tool_name}
  명령: ${command_str}
EOF
  exit 2
fi

exit 0
