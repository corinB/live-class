# 26_Infra_Operator Hook Adjustments 작업 보고서

**작업 번호:** 26
**역할:** Infra_Operator
**Phase:** 6 / 8 — Issue #58 Wiki 마이그레이션 프로그램
**작업 완료일:** 2026-05-16

---

## 작업 요약

Issue #58 Deliverable §5(Phase 6)에서 요구하는 세 가지 훅 조정을 완료했다.

1. **배지 카운터 경로 확장** — `_lib-pipeline-state.sh`가 `wiki-src/plan-before/`와 `wiki-src/plan-after/`를 합산하도록 갱신했다.
2. **`pre-tool-read-size-guard.sh` 임계값 상향** — `wiki-src/**` 경로에 한해 300 KB(307200 bytes)로 올렸다.
3. **`cumulative-size-guard.sh` 신설** — 세션 jsonl 크기 > 1.5 MB 또는 메시지 수 > 500 초과 시 경고를 출력하고 `reports/surrogate-blocks.log`에 기록하는 비차단 훅을 추가했다.
4. **`settings.json` 등록** — 신설 훅을 `UserPromptSubmit` 이벤트에 등록했다.

---

## 변경 파일 목록

| 파일 | 변경 내용 |
|------|---------|
| `.claude/hooks/_lib-pipeline-state.sh` | `wiki-src/plan-before`·`wiki-src/plan-after` 카운트 합산 로직 추가 |
| `.claude/hooks/pre-tool-read-size-guard.sh` | `wiki-src/**` 경로 임계값을 300 KB로 상향하는 `case` 분기 추가 |
| `.claude/hooks/cumulative-size-guard.sh` | 신설 — 세션 jsonl 누적 크기·메시지 수 임계값 감시 훅 |
| `.claude/settings.json` | `UserPromptSubmit` 훅 목록에 `cumulative-size-guard.sh` 등록 |

---

## 로컬 검증 결과

**배지 카운터 시뮬레이션.**
`plan/before/`에 2개, `wiki-src/plan-after/`에 3개 파일을 생성한 임시 디렉터리에서 `pipeline_state_badge()`를 실행한 결과.
```
[pipeline] DOCS:✓ · ARCH:✓ · before:2 · after:3
```
합산이 정확히 반영됐다.

**`pre-tool-read-size-guard.sh` 3종 시뮬레이션.**
- `wiki-src/ko/arch-detail.md` (200 KB) → exit 0 (통과)
- `normal-110kb.md` (110 KB, 비 wiki-src) → deny, exit 2 (기존 차단 유지)
- `wiki-src/ko/huge.md` (350 KB) → deny, exit 2 (300 KB 상한 초과 차단)

**`cumulative-size-guard.sh` 3종 시뮬레이션.**
- 500 KB 세션 → 출력 없음, exit 0
- 1.6 MB 세션 → 경고 출력 + `reports/surrogate-blocks.log` 기록, exit 0
- 510 메시지 세션 → 경고 출력, exit 0 (항상 비차단)

**JSON 유효성 검증.**
`python3 -c "import json; json.load(open('.claude/settings.json'))"` 파싱 성공.

---

## Scope 위반 없음 확인

- `wiki-src/**` 콘텐츠 미손 (보고서 파일만 추가)
- `.claude/agents/**` 미손 (Phase 7 영역)
- 루트 docs(`ARCHITECTURE.md`, `DOCS.md` 등) 미손
- `live-class/**`, `front/**`, `docs/**` 미손

---

## 참고

- Issue #58 — Deliverable §5, Phase 6
- Memory `feedback_surrogate_split.md` — surrogate-split 방지 운영 규칙
- Refs: plan/before/26_Infra_Operator_Hook_Adjustments.md