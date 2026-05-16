---
name: sync-context-wiki
description: context.yaml + .claude/context/*.yaml(Hybrid 그래프 schema v3) + wiki-src/ko 를 단일 진실(코드/DOCS/ARCHITECTURE)과 동기화하는 사이클. scope 사용자 질의 → context-yaml-maestro 1회 호출(내부에서 github-wiki-worker 병렬 디스패치) → pipeline-guard 자동 체이닝. 사용자가 /sync-context-wiki 직접 호출했을 때만 동작.
---

# /sync-context-wiki

## Inputs

- 인자 없음 → 호출 시 scope 를 사용자에게 묻고 진행.
- `/sync-context-wiki <scope>` → scope 인자 그대로 사용. 사용자 질의 생략.

## When invoked

1. 사전 점검 (없으면 명시 후 STOP).
   - `.claude/agents/context-yaml-maestro.md`
   - `.claude/agents/github-wiki-worker.md`
   - `.claude/skills/pipeline-guard.md`
   - 루트 `context.yaml`
   - `.claude/context/{agents,skills,automation,graph}.yaml`
   - `.claude/scripts/audit-context-yaml.py`
2. `AskUserQuestion` 으로 scope 결정 (인자 없을 때만).
   - `all` (default) — context.yaml + 4 외부 yaml + wiki-src/ko 전체.
   - `wiki-src/ko/reports/` — 리포트 디렉토리만.
   - `wiki-src/ko/docs/` — docs 디렉토리만 (agents/skills/harness/architecture/guides 포함).
   - `wiki-src/ko/plan-after/` — 완료 task 미러만.
   - 루트 detail md 4건 (`*-detail.md`).
   - `context-only` — context.yaml + 외부 yaml 만 (wiki 미터치).
   - 사용자 정의 경로.
3. `Agent(subagent_type="context-yaml-maestro")` 1개 호출. 전달 인자.
   - `project_root` — 저장소 루트 절대경로.
   - `scope` — 위에서 결정된 값.
   - `source_truth` — 코드 패키지 / DOCS.md / ARCHITECTURE.md / CONTRIBUTING.md / README.md / 워크플로 yml.
   - `readability_goal` — "1초 파악, 표/Mermaid 우선, 중복 제거, 누락 추가, 오래된 삭제".
4. maestro 내부 동작 (스킬은 관여 X).
   - context.yaml + `.claude/context/*.yaml` 직접 갱신.
   - 단일 메시지 다중 `Agent` 호출로 github-wiki-worker 병렬 디스패치 (디렉토리 단위 partition).
   - audit script `no drift` 확인.
5. maestro 완료 보고 수령 — 갱신 파일 수 / 워커 수 / 미해결 결정 사항.
6. 체이닝 — `Skill(skill="pipeline-guard")` 자동 호출. 변경이 5축(hooks / wiki / tests / workflows / context.yaml) 의 다른 축에 영향 주는지 검증.
7. pipeline-guard 결과(영향 표 + plan) 에 따라 추가 작업 진행. 사용자 게이트.

## Preconditions

- 위 의존성 파일 7건 모두 존재.

## Out of scope

- 코드 변경 — wiki / context yaml 만.
- PR 자동 머지 — 사용자 게이트.
- 기존 wiki 파일 백업 — 덮어쓰기 (필요 시 git history 로 복원).
- maestro 의 자체 결정 (예: 모호한 source-of-truth) → maestro 가 사용자에게 위임.
- 머지 후 main 강제 푸시 금지.
