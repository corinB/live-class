<!-- 인프라 및 CI/CD 설정을 수행하는 스킬 — infra-cicd-operator 에이전트를 호출한다 -->
---
name: setup-infra
description: 인프라 CI/CD 에이전트를 호출해 docker-compose, Dockerfile, GitHub Actions 파일을 생성한다.
---

# /setup-infra

인프라 운영 에이전트(`infra-cicd-operator`)를 호출해 컨테이너 및 CI/CD 설정을 생성하는 스킬이다.

## Preconditions

- `ARCHITECTURE.md` must exist at the repo root (produced by `/design-concurrency`).

## Body

When the user invokes this skill, do the following:

1. 사전 조건 확인. `ARCHITECTURE.md`가 없으면 다음을 출력하고 STOP.

   Missing: ARCHITECTURE.md; run `/design-concurrency` first.

2. Use the Task tool with `subagent_type: "infra-cicd-operator"` and pass the user's free-text argument as the task description.
3. Sub-agent 반환 후 다음 안내를 출력한다.

   다음 단계: `/exec-blueprint` — 개별 태스크 파일을 지정해 구현을 시작합니다.
