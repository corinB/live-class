# NN 27 — Agent Description Rewrites + Refs Footer

작업일: 2026-05-16

## Input Summary

- Task: Issue #58 Phase 7 — `.claude/agents/**` 의 `plan/before/`·`plan/after/`·`reports/` 경로를 `wiki-src/` 대응 경로로 일괄 치환하고, `CONTRIBUTING.md` 의 Refs footer 규약을 `Refs: wiki-src/plan-before/...` 로 전환한다.
- 워커가 socket 끊김으로 commit 직전에 종료됨. 메인 세션이 마무리 (편집은 유효).

## What Was Done

1. 4개 agent 파일 path 치환.
   - `.claude/agents/maestro.md` — 20 lines 변경.
   - `.claude/agents/scrum-task-decomposer.md` — 12 lines.
   - `.claude/agents/blueprint-executor-worker.md` — 12 lines.
   - `.claude/agents/worker.md` — 20 lines.
2. `.claude/agents/git-master-conventions.md` 추가 갱신 (worker 누락분, 메인이 마무리).
3. `CONTRIBUTING.md` (영문 요약) Refs footer 규약 갱신: `Refs: wiki-src/plan-before/NN_<Role>_<Slug>.md`.
4. `wiki-src/ko/contributing-detail.md` 동일 규약 동기화.
5. `context.yaml` 잔존 `plan/before` 참조 정리.
6. NN 27 plan transition: `plan/before/27_*.md` → `wiki-src/plan-after/27_*.md`.

## Rationale & Tradeoffs

- worker가 끝까지 못 돌고 메인이 인계받았기에, 본 보고서는 메인 세션 작성. 워커 본인의 자기 검증 데이터는 부재.
- `<example>` 블록 안의 사용자 dialog 텍스트(예: "plan/before/01-course-crud.md 지시서를...")는 historical illustrative context 라 의도적으로 유지. 미래 워커 동작에 영향 없음.
- 기존 hook `pre-bash-block-plan-move-without-report.sh` 가 여전히 `reports/27_*.md` 를 요구. 이 hook 자체를 새 convention 으로 옮기는 작업은 NN 27 범위 밖이라 본 PR 에 포함하지 않음. 임시로 reports/ 와 wiki-src/ko/reports/ 양쪽에 보고서를 둠.

## Follow-ups

- chore: `pre-bash-block-plan-move-without-report.sh`, `pre-bash-block-plan-move-with-unchecked.sh` 를 `wiki-src/ko/reports/NN_*.md` 도 허용하도록 갱신. 별도 PR.
- NN 24 (PR #56 게이트) 머지 후, 기존 legacy plan/before/{12,13,14,19}.md 이전 시 동일 hook 정합성 확인.
