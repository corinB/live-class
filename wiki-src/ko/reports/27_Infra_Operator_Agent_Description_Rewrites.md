# NN 27 — Agent Description Rewrites + Refs Footer

작업일: 2026-05-16

## Input Summary

- Task: Issue #58 Phase 7 — `.claude/agents/**` 의 `plan/before/`·`plan/after/`·`reports/` 경로를 `wiki-src/` 대응 경로로 일괄 치환하고, `CONTRIBUTING.md` 의 Refs footer 규약을 `Refs: wiki-src/plan-before/...` 로 전환한다.
- 워커가 socket 끊김으로 commit 직전에 종료됨. 메인 세션이 마무리 (편집은 유효).

## What Was Done

1. 4개 agent 파일 path 치환 (maestro / scrum-task-decomposer / blueprint-executor-worker / worker, 합산 64 lines).
2. `.claude/agents/git-master-conventions.md` 추가 갱신.
3. `CONTRIBUTING.md` Refs footer 규약 갱신.
4. `wiki-src/ko/contributing-detail.md` 동일 규약 동기화.
5. `context.yaml` 잔존 path 정리.
6. NN 27 plan transition.

## Rationale & Tradeoffs

- `<example>` 블록 historical illustrative context 유지. 미래 동작 영향 없음.
- hook `pre-bash-block-plan-move-without-report.sh` 가 `reports/27_*.md` 요구. NN 27 범위 밖. 임시로 reports/ 와 wiki-src/ko/reports/ 양쪽 보고서 작성.

## Follow-ups

- chore: hook 을 `wiki-src/ko/reports/` 도 허용하도록 갱신. 별도 PR.
- NN 24 머지 후 hook 정합성 재확인.
