---
name: evolution
description: 리팩토링 사이클 자동화. plan mode 진입 → ask-and-delegate → 코덱스 × N 도메인별 1차 스캔 → refactoring-maestro 취합·플랜 → 코덱스 × N 적용·보고 → pipeline-guard 체이닝. 사용자가 /evolution 직접 호출했을 때만 동작.
---

# /evolution

## When invoked

1. 사전 점검 (없으면 명시 후 STOP).
   - `.claude/agents/refactoring-maestro.md`
   - `.claude/agents/refactoring-worker.md`
   - `.claude/skills/pipeline-guard/SKILL.md`
   - user-level `~/.claude/skills/ask-and-delegate/SKILL.md`
   - codex 런타임 — `Skill(skill="codex:setup")` 으로 사전 점검 가능 (필수 아님)
   - 현재 브랜치가 main 이 아닐 것 (리팩토링 PR 브랜치 권장).
2. `EnterPlanMode` 호출.
3. 요구사항 명세 — `Skill(skill="ask-and-delegate")` 호출. 사용자에게 리팩토링 범위 · 우선순위 · 제약 · codex 활용 단계를 묻고 위임 프롬프트 초안까지 받음.
4. ask-and-delegate 결과를 plan 파일에 정리 + refactoring-maestro 위임 프롬프트 본문 + codex 스캔 prompt 템플릿 포함. `ExitPlanMode` 로 사용자 승인.
5. 승인 후 `Agent(subagent_type="refactoring-maestro")` 1개 호출. maestro 내부 동작.
   - 도메인 경계 식별 (`context.yaml.business_context.domains` 참조).
   - **1차 스캔 디스패치 — 단일 메시지 다중 `Agent(subagent_type="codex:codex-rescue")` 호출로 도메인별 codex 병렬 디스패치.** 각 codex 에 5축(또는 사용자 합의된 N축) 스캔 prompt 전달.
   - codex 결과 수신 + 도메인별 리포트 저장 (`reports/refactoring/codex-<domain>-<YYYY-MM-DD>.md`).
   - 결과 취합 · 중복 제거 · 오버엔지 컷 · P0/P1/P2/반려 정렬.
   - `reports/refactoring/maestro-summary-<YYYY-MM-DD>.md` 산출.
   - 폴백 — codex 호출 실패 또는 사용 불가 시 기존 `refactoring-worker` 병렬 디스패치로 자동 전환.
6. 적용 단계 — maestro 의 P0/P1 액션을 다음 두 방식 중 사용자 합의된 방식으로 처리.
   - **codex 위임 방식 (Recommended for 큰 변경)** — 적용 항목별로 `Agent(subagent_type="codex:codex-rescue")` 호출, 변경 prompt + DoD 전달. 메인 세션이 결과 검수 + commit.
   - **메인 세션 직접** — 간단한 항목 또는 codex 결과 회귀 위험 큰 항목.
   - P2 는 사용자 결정.
7. 회귀 검증 — `./gradlew test` 그린 확인. 신규 테스트 필요 항목 (도메인 로직 이동 / AfterCommit 분리 등) 은 codex 또는 메인 세션이 추가.
8. 체이닝 — 적용 commit 직후 `Skill(skill="pipeline-guard")` 자동 호출.
9. pipeline-guard 결과(영향 표 + plan)에 따라 추가 동기화 작업 진행.

## Preconditions

- 위 4개 의존성 파일 모두 존재.
- codex 런타임은 선택 — 없으면 폴백 동작.
- 현재 브랜치가 main 이 아님.

## Out of scope

- PR 자동 머지 — 사용자 게이트.
- 도메인 경계 신설/이동 — maestro 가 사용자에게 별도 결정 요청.
- 머지 후 main 강제 푸시 금지.
- codex 결과를 검수 없이 commit — 메인 세션이 항상 검수 + 회귀 검증 후 commit.

## Codex 활용 노트

- 1차 스캔 = 도메인 N개 → codex N개 병렬. 토큰 분산 + 모델 일관성.
- 적용 단계 codex 위임은 변경 범위가 크고 회귀 위험 낮은 항목 (AfterCommit 분리, 단순 도메인 로직 이동) 에 적합. 임의 변경 / 복잡 invariant 영향 항목은 메인 세션 직접.
- codex 결과 prompt 템플릿은 `.claude/agents/refactoring-worker.md` 의 출력 형식 + 7축 기준을 그대로 활용.
