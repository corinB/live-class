<!-- DDD 기반 도메인 설계를 수행하는 스킬 — ddd-domain-architect 에이전트를 호출해 DOCS.md를 생성한다 -->
---
name: design-domain
description: DDD 도메인 설계 에이전트를 호출해 DOCS.md(바운디드 컨텍스트·애그리거트·상태 전이)를 생성한다.
---

# /design-domain

도메인 설계 전문가 에이전트(`ddd-domain-architect`)를 호출해 DOCS.md를 생성하는 스킬이다.

## Preconditions

- `context.yaml` must exist at the repo root.

## Body

When the user invokes this skill, do the following:

1. 사전 조건 확인. `context.yaml`이 repo root에 없으면 다음을 출력하고 STOP.

   Missing: context.yaml; reseed from .claude/templates/context.yaml.template before continuing.

2. Use the Task tool with `subagent_type: "ddd-domain-architect"` and pass the user's free-text argument as the task description.
3. Sub-agent 반환 후 다음 안내를 출력한다.



   다음 단계: `/design-concurrency` — 동시성·락 전략을 설계하고 ARCHITECTURE.md를 생성합니다.
