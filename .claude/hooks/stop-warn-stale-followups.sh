#!/usr/bin/env bash
# session 종료 시 reports/*.md 의 ## Follow-ups 섹션 안 미완료 [ ] 항목을 카운트해서 알림 (non-blocking)
set -euo pipefail

# stdin JSON payload 소비
_payload=$(cat)

if ! git rev-parse --is-inside-work-tree > /dev/null 2>&1; then
  exit 0
fi

if [ ! -d "reports" ]; then
  exit 0
fi

# reports/*.md 의 ## Follow-ups 섹션 안의 `- [ ]` 만 카운트 (Action Items 등 다른 섹션의 [ ] 는 제외).
# awk: flag=1 inside ## Follow-ups, flag=0 on next ## header.
count=$(find reports -maxdepth 1 -name "*.md" ! -name ".gitkeep" 2>/dev/null \
  | xargs -r awk '/^## Follow-ups/{flag=1; next} /^## /{flag=0} flag && /^[[:space:]]*-[[:space:]]+\[[[:space:]]\]/' \
  | wc -l | tr -d ' ')

if [ "${count:-0}" -gt 0 ]; then
  echo "[hook:stop] ⚠ reports/ 미완료 Follow-up ${count}개 — 후속 PR 로 resolved 됐는지 확인 후 [x] 갱신 검토." >&2
fi

exit 0
