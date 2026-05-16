<!-- 동시성 및 락 전략을 설계하는 스킬 — concurrency-architect 에이전트를 호출해 ARCHITECTURE.md를 생성한다 -->
---
name: design-concurrency
description: 동시성 설계 에이전트를 호출해 ARCHITECTURE.md(정원 레이스 컨디션·Redisson 락·캐싱 전략)를 생성한다.
---

# /design-concurrency

동시성 전문가 에이전트(`concurrency-architect`)를 호출해 ARCHITECTURE.md를 생성하는 스킬이다.

## Preconditions

- `context.yaml` must exist at the repo root.
- `DOCS.md` must exist at the repo root (produced by `/design-domain`).

## Body

When the user invokes this skill, do the following:

1. 사전 조건 확인. 아래 파일 중 하나라도 없으면 해당 항목을 명시하고 STOP.

   Missing: <list>; reseed `context.yaml` from `.claude/templates/context.yaml.template` and/or run `/design-domain` first.

2. Use the Task tool with `subagent_type: "concurrency-architect"` and pass the user's free-text argument as the task description.
3. Sub-agent 반환 후 다음 안내를 출력한다.

   다음 단계: `/decompose-tasks` — 작업을 스크럼 태스크로 분해하고 plan/before/*.md 파일을 생성합니다.
