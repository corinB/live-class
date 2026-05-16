---
name: business-card-production
description: 면접관 평가용 README + 상세 문서 세트를 doc-maestro 1개 호출로 일괄 생성한다. doc-maestro 가 README.md 12 섹션을 직접 작성하고 내부에서 doc-worker × 4 (api/erd/architecture/cicd) + doc-troubleshooting-worker × 4 를 병렬 디스패치. 기존 문서는 덮어쓴다. 완료 후 pipeline-guard 자동 실행. 사용자가 /business-card-production 직접 호출했을 때만 동작.
---

# /business-card-production

## Inputs

- 인자 없음 → 표준 요구사항 텍스트 사용 (이 저장소의 과제 명세).
- `/business-card-production <requirements-file.md>` → 명시 요구사항 파일 사용.

## When invoked

1. 사전 점검 (없으면 명시 후 STOP).
   - `.claude/agents/doc-maestro.md`
   - `.claude/agents/doc-worker.md`
   - `.claude/agents/doc-troubleshooting-worker.md`
   - `.claude/skills/pipeline-guard.md`
   - 루트 `DOCS.md`, `ARCHITECTURE.md`, `context.yaml`
2. `Agent(subagent_type="doc-maestro")` 1개 호출. 입력으로 전달.
   - `project_root` — 저장소 루트 절대경로.
   - `requirements_text` — 인자 파일 내용 또는 표준 요구사항 체크리스트.
   - `DOCS.md`, `ARCHITECTURE.md`, `context.yaml` 경로.
3. maestro 의 내부 동작 (스킬은 관여 X).
   - README.md 12 섹션 직접 작성.
   - `Agent` 다중 호출로 doc-worker × 4 (api / erd / architecture / cicd) 병렬 디스패치.
   - 트러블슈팅 4건 (Redis+Lua / 다른 임팩트 이슈 / AI 하네스 / 컨텍스트 매니지먼트) doc-troubleshooting-worker × 4 병렬 디스패치.
   - 워커 결과 일관성 검수 + README 링크 정합 확인 + 표현 균일성 점검.
4. maestro 완료 보고 수령 — README 12 섹션 + 세부 문서 8건 + 위임한 워커 수/완료 수.
5. 체이닝 — `Skill(skill="pipeline-guard")` 자동 호출. 새 문서가 wiki / context.yaml / hook 가정에 미치는 영향 검증 · 동기화.
6. pipeline-guard 결과(영향 표 + plan)에 따라 추가 동기화 작업 진행.

## Preconditions

- 위 5개 의존성 파일 모두 존재.

## Out of scope

- 코드 변경 — 문서만.
- PR 자동 머지 — 사용자 게이트.
- 기존 문서 백업 — 덮어쓰기 (필요 시 git history 로 복원).
- 트러블슈팅 2번(다른 임팩트 이슈) 후보가 git 히스토리에 없으면 maestro 가 사용자에게 후보 1~2건 제시 후 선택받기.