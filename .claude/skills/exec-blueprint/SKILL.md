<!-- 개별 태스크 파일을 워크트리 격리 환경에서 구현하는 스킬 — blueprint-executor-worker 에이전트를 호출한다 -->
---
name: exec-blueprint
description: 지정한 plan/before/{task-file}.md를 blueprint-executor-worker에게 위임해 워크트리 격리 환경에서 구현한다.
---

# /exec-blueprint

블루프린트 실행 워커 에이전트(`blueprint-executor-worker`)를 호출해 단일 태스크를 구현하는 스킬이다.

## Preconditions

- `context.yaml` must exist at the repo root.
- `DOCS.md` must exist at the repo root.
- `ARCHITECTURE.md` must exist at the repo root.
- The task file the user passes (e.g. `plan/before/01_class-entity.md`) must exist.

## Body

When the user invokes this skill, do the following:

1. 사전 조건 확인. 아래 항목 중 하나라도 없으면 해당 내용을 명시하고 STOP.

   Missing: <list>; ensure context.yaml, DOCS.md, ARCHITECTURE.md, and the specified task file all exist.

2. Worktree 경로 자동 도출.

   - 사용자가 전달한 태스크 파일명에서 `NN_` 접두사와 `.md` 접미사를 제거해 slug를 생성한다.
     - 예: `01_class-entity.md` → `class-entity`
   - Worktree 경로: `../worktrees/feature-<slug>`
     - 예: `../worktrees/feature-class-entity`
   - 이 경로를 `isolation: "worktree"` 파라미터와 함께 에이전트에 전달한다.

3. Use the Task tool with `subagent_type: "blueprint-executor-worker"`, pass the derived worktree path and the task file path, and set `isolation: "worktree"`.

4. Sub-agent 반환 후 다음 안내를 출력한다.

   다음 태스크가 있으면 `/exec-blueprint <다음 태스크 파일>` 을 실행하세요.
