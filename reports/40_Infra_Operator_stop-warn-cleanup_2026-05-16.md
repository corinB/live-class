# NN 40 — Infra_Operator — Stop Warn Cleanup Hook

**날짜**: 2026-05-16
**담당**: Worker (Infra_Operator)
**브랜치**: chore/task-40-stop-cleanup-hook
**이슈**: #70, Phase G-6

---

## 변경 요약

### 1. `.claude/hooks/stop-warn-cleanup-candidates.sh` (신규)

세션 종료 시점에 git worktree 개수와 origin/main에 머지된 로컬 브랜치 수를 검사해
후보가 있으면 stderr에 안내 메시지를 출력하는 비차단 Stop 훅을 추가했다.

주요 동작:
- `CLEANUP_HINT_OFF=1` 환경변수가 설정되면 조용히 exit 0.
- `git worktree list --porcelain`으로 메인 외 worktree 개수 산출.
- `git branch --merged origin/main`으로 머지된 로컬 브랜치 수 산출.
  - `origin/main`이 없는 리포지토리에서도 `git rev-parse --verify`로 사전 체크해 안전하게 fallback.
- 두 수치가 모두 0이면 아무것도 출력하지 않고 exit 0.
- 하나라도 0보다 크면 stderr에 안내 블록 출력 후 exit 0.
- 어떤 경우에도 exit 0 보장 (세션 종료 차단 없음).

### 2. `.claude/settings.json` — Stop 배열 추가

기존 Stop 훅 3개(stop-warn-design-changes, stop-warn-stale-followups, stop-warn-context-stale) 뒤에
`stop-warn-cleanup-candidates.sh` 항목을 추가했다. 다른 훅·배열·설정값은 일절 변경하지 않았다.

### 3. `wiki-src/plan-before/40_Infra_Operator_Stop_Warn_Cleanup_Hook.md` (신규)

태스크 명세 파일을 wiki-src/plan-before/ 에 추가했다 (plan 전환 전 상태).

---

## Scope 준수 확인

- `.claude/hooks/stop-warn-cleanup-candidates.sh` — 신규 파일 (scope 내).
- `.claude/settings.json` — Stop 배열에 1개 항목만 추가 (scope 내).
- `wiki-src/plan-before/40_*.md` — 태스크 명세 신규 (scope 내).
- `reports/40_*.md` — 리포트 신규 (scope 내).
- `wiki-src/**` 기존 콘텐츠, `.claude/scripts/**`, `.github/**`, `context.yaml`,
  `live-class/**`, `front/**` — 변경 없음.

---

## Smoke Test 결과

| # | 조건 | 기대 결과 | 실제 결과 |
|---|------|-----------|-----------|
| 1 | clean repo (worktree=0, merged=0) | 출력 없음, exit 0 | 통과 |
| 2 | 실제 리포지토리 (worktree=24, merged=3) | 안내 메시지 stderr, exit 0 | 통과 |
| 3 | `CLEANUP_HINT_OFF=1` | 출력 없음, exit 0 | 통과 |
| 4 | `.claude/settings.json` JSON 유효성 | `python3 json.load` 성공 | 통과 |

---

## 참고

- Refs: `wiki-src/plan-before/40_Infra_Operator_Stop_Warn_Cleanup_Hook.md`
- NN 36/37 cleanup scripts가 아직 main에 없으므로, 본 훅은 직접 git 명령어로 worktree/branch를 계산한다.
  NN 36/37 머지 후 훅을 확장할 여지가 있으나 현재 scope 밖이다.