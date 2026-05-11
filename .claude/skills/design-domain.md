<!-- DDD 기반 도메인 설계를 수행하는 스킬 — ddd-domain-architect 에이전트를 호출해 DOCS.md를 생성한다 -->
---
name: design-domain
description: DDD 도메인 설계 에이전트를 호출해 DOCS.md(바운디드 컨텍스트·애그리거트·상태 전이)를 생성한다.
---

# /design-domain

도메인 설계 전문가 에이전트(`ddd-domain-architect`)를 호출해 DOCS.md를 생성하는 스킬이다.

## Preconditions

None — this is the first step in the pipeline.

## Body

When the user invokes this skill, do the following:

1. Preconditions: 없음. 파이프라인의 첫 번째 단계이므로 바로 진행한다.
2. Use the Task tool with `subagent_type: "ddd-domain-architect"` and pass the user's free-text argument as the task description.
3. Sub-agent 반환 후 다음 안내를 출력한다.

   다음 단계: `/design-concurrency` — 동시성·락 전략을 설계하고 ARCHITECTURE.md를 생성합니다.
