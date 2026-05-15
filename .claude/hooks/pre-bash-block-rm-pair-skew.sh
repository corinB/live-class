#!/usr/bin/env bash
# rm/git rm 명령으로 루트 doc·wiki 짝 중 한쪽만 삭제하려 할 때 차단하는 PreToolUse 훅 (jq required, no fallback)
set -euo pipefail

if [ "${WIKI_PAIR_GUARD_OFF:-0}" = "1" ]; then
  exit 0
fi

deny() {
  printf '{"permissionDecision":"deny","reason":"%s"}' "$1"
  exit 2
}

if ! command -v jq >/dev/null 2>&1; then
  echo "[hook:pre-bash-block-rm-pair-skew] jq is required but not installed. Install jq and retry." >&2
  exit 2
fi

payload=$(cat)
command_str=$(printf '%s' "$payload" | jq -r '.tool_input.command // ""')

if [ -z "$command_str" ]; then
  exit 0
fi

# Strip inline shell comments first so trailing `# ARCHITECTURE.md 참고` 등이 false positive 안 일으키게.
command_clean=$(printf '%s' "$command_str" | sed -E 's/[[:space:]]+#[^\n]*$//')

# Detect `rm` or `git rm` as a complete subcommand token (not part of another word like `vrm` or `armed`).
if ! printf '%s' "$command_clean" | grep -qE '(^|[[:space:];&|])(rm|git[[:space:]]+rm)([[:space:]]|$)'; then
  exit 0
fi

# Tokenize by whitespace. Quoted file names are not perfectly handled (bash 3 limitation), but
# this gives us "full-token" matching for the common rm patterns.
tokens=$(printf '%s' "$command_clean" | tr ' \t' '\n\n')

ROOT_DOCS=("ARCHITECTURE.md" "DOCS.md" "CONTRIBUTING.md" "ORCHESTRATION.md")
WIKI_DETAILS=("wiki-src/ko/architecture-detail.md" "wiki-src/ko/docs-detail.md" "wiki-src/ko/contributing-detail.md" "wiki-src/ko/orchestration-detail.md")

for i in "${!ROOT_DOCS[@]}"; do
  root="${ROOT_DOCS[$i]}"
  wiki="${WIKI_DETAILS[$i]}"
  root_rm=0
  wiki_rm=0
  # `-Fx`: 전체 라인(=토큰) 매칭. substring false positive 차단.
  if printf '%s\n' "$tokens" | grep -qFx "$root"; then
    root_rm=1
  fi
  if printf '%s\n' "$tokens" | grep -qFx "$wiki"; then
    wiki_rm=1
  fi
  if [ "$root_rm" -ne "$wiki_rm" ]; then
    if [ "$root_rm" -eq 1 ]; then
      deny "pair-skew: ${root} 를 삭제하려면 ${wiki} 도 함께 삭제해야 합니다. 탈출 해치: WIKI_PAIR_GUARD_OFF=1"
    else
      deny "pair-skew: ${wiki} 를 삭제하려면 ${root} 도 함께 삭제해야 합니다. 탈출 해치: WIKI_PAIR_GUARD_OFF=1"
    fi
  fi
done

exit 0
