#!/usr/bin/env bash
# 세션 종료 시점에 cleanup 가능한 worktree/branch 후보 개수를 안내하는 비차단 Stop 훅.
set -euo pipefail

# Escape hatch
if [ "${CLEANUP_HINT_OFF:-0}" = "1" ]; then
  exit 0
fi

# stdin payload (Stop hooks may receive an empty/json payload -- ignore content)
cat >/dev/null 2>&1 || true

REPO_ROOT="${CLAUDE_PROJECT_DIR:-$(git rev-parse --show-toplevel 2>/dev/null)}"
if [ -z "$REPO_ROOT" ] || { [ ! -d "$REPO_ROOT/.git" ] && [ ! -f "$REPO_ROOT/.git" ]; }; then
  exit 0
fi

cd "$REPO_ROOT" || exit 0

# Worktree count (excluding main = $REPO_ROOT)
wt_count=$(git worktree list --porcelain 2>/dev/null | awk '/^worktree /{c++} END{print c}')
extra_wt=$((wt_count - 1))
[ "$extra_wt" -lt 0 ] && extra_wt=0

# Merged local branches (excluding main); graceful fallback when origin/main absent
merged_local=0
if git rev-parse --verify "origin/main" >/dev/null 2>&1; then
  merged_local=$(git branch --merged origin/main 2>/dev/null | grep -vcE '^\*?[[:space:]]*main$' || echo 0)
  merged_local=$(echo "$merged_local" | tr -d ' ')
fi

# Nothing to suggest
if [ "$extra_wt" -eq 0 ] && [ "$merged_local" -eq 0 ]; then
  exit 0
fi

cat >&2 <<EOF
[hook:cleanup-candidates] cleanup 후보 감지: worktree=${extra_wt}개, 머지된 로컬 branch=${merged_local}개.
권장 조치:
  bash .claude/scripts/cleanup-worktrees.sh          # dry-run 확인
  bash .claude/scripts/cleanup-worktrees.sh --execute # 실제 정리
끄려면 환경변수 CLEANUP_HINT_OFF=1 을 사용하세요.
EOF

exit 0