---
name: business-card-production
description: 면접관 평가용 README + 상세 문서 세트를 doc-maestro 1개 호출로 일괄 생성. 코덱스 × N 가 docs 후보 1차 조사 → maestro 가 README 12 섹션 작성 + 코덱스 × N (또는 doc-worker × 4 + doc-troubleshooting-worker × 4) 병렬 디스패치 → pipeline-guard 체이닝. 사용자가 /business-card-production 직접 호출했을 때만 동작.
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
   - `.claude/skills/pipeline-guard/SKILL.md`
   - 루트 `DOCS.md`, `ARCHITECTURE.md`, `context.yaml`
   - codex 런타임 — `Skill(skill="codex:setup")` 사전 점검 가능 (필수 아님)
2. `Agent(subagent_type="doc-maestro")` 1개 호출. 입력으로 전달.
   - `project_root` — 저장소 루트 절대경로.
   - `requirements_text` — 인자 파일 내용 또는 표준 요구사항 체크리스트.
   - `DOCS.md`, `ARCHITECTURE.md`, `context.yaml` 경로.
   - `codex_enabled` — true (기본). false 면 폴백.
3. maestro 의 내부 동작 (스킬은 관여 X).
   - **1차 조사 단계 — 단일 메시지 다중 `Agent(subagent_type="codex:codex-rescue")` 호출로 영역별 codex 병렬 디스패치.** 영역 = API endpoint 인벤토리 / ERD 추출 / 아키텍처 결정 인용 / CI·CD 파이프라인 / 트러블슈팅 후보 4건 (git log + reports/ 스캔). 각 codex 에 한국어 개조식 + 표/Mermaid 우선 prompt 전달.
   - codex 결과 수신 → maestro 가 README.md 12 섹션 직접 작성 (요약·링크 통합).
   - **세부 문서 작성 — 단일 메시지 다중 `Agent(subagent_type="codex:codex-rescue")` 호출로 docs/api.md / docs/erd.md / docs/architecture.md / docs/cicd.md + docs/troubleshooting/{1..4}.md 병렬 작성**, 또는 폴백으로 doc-worker × 4 + doc-troubleshooting-worker × 4.
   - 워커/codex 결과 일관성 검수 + README 링크 정합 확인 + 표현 균일성 점검.
4. maestro 완료 보고 수령 — README 12 섹션 + 세부 문서 8건 + 위임한 codex/워커 수.
5. 체이닝 — `Skill(skill="pipeline-guard")` 자동 호출. 새 문서가 wiki / context.yaml / hook 가정에 미치는 영향 검증 · 동기화.
6. pipeline-guard 결과(영향 표 + plan)에 따라 추가 동기화 작업 진행.

## Preconditions

- 위 5개 의존성 파일 모두 존재.
- codex 런타임은 선택. 미설치/실패 시 doc-worker + doc-troubleshooting-worker 폴백.

## Out of scope

- 코드 변경 — 문서만.
- PR 자동 머지 — 사용자 게이트.
- 기존 문서 백업 — 덮어쓰기 (필요 시 git history 로 복원).
- 트러블슈팅 2번(다른 임팩트 이슈) 후보가 git 히스토리에 없으면 maestro 가 사용자에게 후보 1~2건 제시 후 선택받기.
- codex 결과를 검수 없이 commit — 메인 세션이 README 링크 정합 + 표현 균일성 점검 후 commit.

## Codex 활용 노트

- 1차 조사 = 영역 N개 → codex N개 병렬. API/ERD/아키텍처/CI·CD/트러블슈팅 후보 분리.
- 세부 문서 작성 = 문서 8건 → codex 8개 병렬 또는 doc-worker × 4 + doc-troubleshooting-worker × 4 폴백.
- codex prompt 템플릿은 `.claude/agents/doc-worker.md` / `.claude/agents/doc-troubleshooting-worker.md` 의 한국어 개조식 + 표/Mermaid 우선 출력 형식을 그대로 활용.
- 트러블슈팅 4건은 [문제 상황 → 원인 분석 → 의사결정·해결 → 결과] 흐름 강제 (서술형 허용 섹션 외 개조식).
