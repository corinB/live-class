<!-- git 브랜치·커밋 컨벤션과 PR 템플릿을 설정하는 스킬 — git-master-conventions 에이전트를 호출한다 -->
---
name: setup-git-rules
description: git 컨벤션 에이전트를 호출해 CONTRIBUTING.md와 PR 템플릿을 생성한다.
---

# /setup-git-rules

git 마스터 컨벤션 에이전트(`git-master-conventions`)를 호출해 CONTRIBUTING.md와 PR 템플릿을 생성하는 스킬이다.

## Preconditions

- `context.yaml` must exist at the repo root.
- `plan/before/` must contain at least one `.md` file (other than `.gitkeep`), produced by `/decompose-tasks`.

## Body

When the user invokes this skill, do the following:

1. 사전 조건 확인. 아래 항목 중 하나라도 없으면 해당 내용을 명시하고 STOP.

   Missing: <list>; reseed `context.yaml` from `.claude/templates/context.yaml.template` and/or run `/decompose-tasks` first.

2. Use the Task tool with `subagent_type: "git-master-conventions"` and pass the user's free-text argument as the task description.
3. Sub-agent 반환 후 다음 안내를 출력한다.

   다음 단계: `/exec-blueprint` — 개별 태스크 파일을 지정해 구현을 시작합니다.
