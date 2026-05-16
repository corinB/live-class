---
name: evolution
description: 리팩토링 사이클 자동화. plan mode 진입 → ask-and-delegate 스킬로 요구사항 명세 → refactoring-maestro 1개 호출(내부에서 refactoring-worker 도메인별 병렬 디스패치) → 액션 적용 → pipeline-guard 5축 검증 자동 체이닝. 사용자가 /evolution 직접 호출했을 때만 동작.
---

# /evolution

## When invoked

1. 사전 점검 (없으면 명시 후 STOP).
   - `.claude/agents/refactoring-maestro.md`
   - `.claude/agents/refactoring-worker.md`
   - `.claude/skills/pipeline-guard.md`
   - user-level `~/.claude/skills/ask-and-delegate/SKILL.md`
   - 현재 브랜치가 main 이 아닐 것 (리팩토링 PR 브랜치 권장).
2. `EnterPlanMode` 호출.
3. 요구사항 명세 — `Skill(skill="ask-and-delegate")` 호출. 사용자에게 리팩토링 범위 · 우선순위 · 제약을 묻고 위임 프롬프트 초안까지 받음.
4. ask-and-delegate 결과를 plan 파일에 정리 + refactoring-maestro 위임 프롬프트 본문 포함. `ExitPlanMode` 로 사용자 승인.
5. 승인 후 `Agent(subagent_type="refactoring-maestro")` 1개 호출. maestro 내부 동작.
   - 도메인 경계 식별 (`context.yaml.business_context.domains` 참조).
   - 단일 메시지 다중 `Agent` 호출로 도메인별 refactoring-worker 병렬 디스패치.
   - 결과 취합 · 중복 제거 · 오버엔지 컷 · P0/P1/P2/반려 정렬.
   - `reports/refactoring/maestro-summary-<YYYY-MM-DD>.md` 산출.
6. 리팩토링 적용 — maestro 의 P0/P1 액션을 메인 세션 또는 worker 위임으로 실제 코드 변경에 반영. P2 는 사용자 결정.
7. 체이닝 — 적용 commit 직후 `Skill(skill="pipeline-guard")` 자동 호출.
8. pipeline-guard 결과(영향 표 + plan)에 따라 추가 동기화 작업 진행.

## Preconditions

- 위 4개 의존성 파일 모두 존재.
- 현재 브랜치가 main 이 아님.

## Out of scope

- PR 자동 머지 — 사용자 게이트.
- 도메인 경계 신설/이동 — maestro 가 사용자에게 별도 결정 요청.
- 머지 후 main 강제 푸시 금지.