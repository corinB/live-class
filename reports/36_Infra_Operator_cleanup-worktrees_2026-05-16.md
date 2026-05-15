# 36_Infra_Operator cleanup-worktrees 실행 리포트 (2026-05-16)

## 작업 요약

.claude/scripts/cleanup-worktrees.sh 스크립트를 신규 작성했습니다. 머지된 PR의 워크트리만 보수적으로 정리하는 dry-run 기본 스크립트입니다.

## 스모크 테스트 결과

### 테스트 1: --help

```bash
bash .claude/scripts/cleanup-worktrees.sh --help
```bash

출력 결과 (정상).

```text
Usage: cleanup-worktrees.sh [--dry-run] [--execute] [--help]

  --dry-run   (default) Print what would be removed. No changes made.
  --execute   Actually remove merged worktrees and append to reports/cleanup-log.md.
  --help      Show this message.

Only worktrees whose branch HEAD is an ancestor of origin/main AND whose PR
has been merged are eligible for removal.
The main repo worktree and the current pwd are always skipped.
```text

### 테스트 2: dry-run (인수 없음)

```bash
bash .claude/scripts/cleanup-worktrees.sh
```bash

출력 결과 (정상 — 머지되지 않은 워크트리는 모두 SKIP).

```text
==> Fetching origin (--prune) ...
==> Querying merged PRs from GitHub ...

==> Scanning worktrees ...

SKIP (not merged PR)    : .../p  branch=chore/maestro-issue-70
SKIP (not ancestor of origin/main): .../feature-task-29-context-baseline  branch=chore/task-29-context-baseline
SKIP (main worktree)    : .../worktrees/feature-task-36-cleanup-worktrees
... (기타 워크트리 모두 SKIP)

==> Dry-run complete. Run with --execute to actually remove the listed worktrees.
```text

### 테스트 3: main worktree 보호 확인

스크립트 실행 위치인 feature-task-36 워크트리가 REPO_ROOT와 같아 SKIP (main worktree) 로 처리됨. 정상.

### 테스트 4: 비-머지 워크트리 보호 확인


ot merged PR 판정이 아닌 경우도 
ot ancestor of origin/main 이중 검증으로 SKIP 처리됨. 정상.

## scope 위반 여부

없음. 다음 파일만 변경했습니다.

- .claude/scripts/cleanup-worktrees.sh (신규 생성)
- plan/before/36_Infra_Operator_Cleanup_Worktrees_Script.md (plan/after 이동 포함)
- eports/36_Infra_Operator_cleanup-worktrees_2026-05-16.md (이 파일)

## 스크립트 줄 수

194줄 (UTF-8 no BOM).
