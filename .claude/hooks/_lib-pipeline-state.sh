#!/usr/bin/env bash
# 파이프라인 상태 배지를 계산하는 공유 헬퍼 라이브러리 (다른 훅에서 source로 사용)
set -euo pipefail

# pipeline_state_badge — 파이프라인 상태를 한 줄로 출력
# Usage: pipeline_state_badge
pipeline_state_badge() {
  local docs_mark arch_mark before_count after_count

  if [ -f "DOCS.md" ]; then
    docs_mark="✓"
  else
    docs_mark="✗"
  fi

  if [ -f "ARCHITECTURE.md" ]; then
    arch_mark="✓"
  else
    arch_mark="✗"
  fi

  # plan/before 디렉터리의 .md 파일 수 계산 (.gitkeep 제외)
  if [ -d "plan/before" ]; then
    before_count=$(find plan/before -maxdepth 1 -name "*.md" ! -name ".gitkeep" 2>/dev/null | wc -l | tr -d ' ')
  else
    before_count=0
  fi

  # plan/after 디렉터리의 .md 파일 수 계산 (.gitkeep 제외)
  if [ -d "plan/after" ]; then
    after_count=$(find plan/after -maxdepth 1 -name "*.md" ! -name ".gitkeep" 2>/dev/null | wc -l | tr -d ' ')
  else
    after_count=0
  fi

  echo "[pipeline] DOCS:${docs_mark} · ARCH:${arch_mark} · before:${before_count} · after:${after_count}"
}
