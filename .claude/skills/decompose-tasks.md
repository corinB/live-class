<!-- 도메인 설계 문서를 기반으로 스크럼 태스크를 분해하는 스킬 — scrum-task-decomposer 에이전트를 호출한다 -->
---
name: decompose-tasks
description: 스크럼 태스크 분해 에이전트를 호출해 plan/before/*.md 태스크 파일을 생성한다.
---

# /decompose-tasks

DOCS.md와 ARCHITECTURE.md를 기반으로 구현 태스크를 분해하는 스킬이다.

## Preconditions

- `DOCS.md` must exist at the repo root.
- `ARCHITECTURE.md` must exist at the repo root.

## Body

When the user invokes this skill, do the following:

1. 사전 조건 확인. 아래 파일 중 하나라도 없으면 해당 항목을 명시하고 STOP.

   Missing: <list>; run `/design-domain` and `/design-concurrency` first.

2. Use the Task tool with `subagent_type: "scrum-task-decomposer"` and pass the user's free-text argument as the task description.
3. Sub-agent 반환 후 다음 안내를 출력한다.

   다음 단계: `/setup-infra` (인프라 설정)와 `/setup-git-rules` (git 컨벤션)를 병렬로 실행할 수 있습니다.
