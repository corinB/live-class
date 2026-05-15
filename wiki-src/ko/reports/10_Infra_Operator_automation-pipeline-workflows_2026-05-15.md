# 작업 보고서 — Infra & CI/CD Operator

**작업 날짜:** 2026-05-15
**브랜치:** `chore/harness-automation-workflows`
**PR:** [#30](https://github.com/corinB/live-class/pull/30)
**담당 에이전트:** infra-cicd-operator

---

## 작업 개요

`docs/architecture/automation-pipeline.md`에 정의된 Maestro + Worker 자동화 파이프라인을
GitHub Actions 워크플로로 구현했다. 총 5개 워크플로 YAML, 1개 Issue 템플릿, 1개 참조 문서를
단일 커밋으로 `chore/harness-automation-workflows` 브랜치에 추가하고 PR #30을 열었다.

---

## 생성 파일 목록

| 파일 | 역할 |
|------|------|
| `.github/workflows/maestro-dispatch.yml` | `issues.opened/labeled` 이벤트 → Maestro 서브에이전트 → `plan/before/` 커밋 → `repository_dispatch` 발행 |
| `.github/workflows/worker-dispatch.yml` | `repository_dispatch: worker-task` → 매니페스트 태스크 매트릭스 → Worker 서브에이전트 병렬 실행 → PR 생성 |
| `.github/workflows/comment-handler.yml` | `issue_comment`/`pull_request_review_comment` → `@claude` 멘션 파싱 → PR 코멘트면 Worker 재실행, Issue 코멘트면 Maestro 재계획 |
| `.github/workflows/auto-rebase.yml` | `push: main` → `automation:worker` 레이블 오픈 PR 전체에 `gh pr update-branch --rebase` → 충돌 시 `needs-human` 레이블 및 코멘트 |
| `.github/workflows/gatekeeper.yml` | `pull_request_review`/`check_suite` → Build & Test green + Gemini P0=0 AND P1=0 + `codex-review: pass` 마커 세 조건 충족 시 `gh pr merge --squash --auto` |
| `.github/ISSUE_TEMPLATE/automation-feature.yml` | `acceptance`(체크리스트) / `scope`(마크다운) / `priority`(P0/P1/P2 드롭다운) 필수 필드. `maestro:auto` 레이블 자동 적용 |
| `docs/architecture/automation-pipeline-workflows.md` | 5개 워크플로 구성 다이어그램, 운영 가이드, 보안 모델, 필요한 Secret/Variable/Label 목록 |

---

## 설계 결정 사항

### 킬 스위치 구현
모든 워크플로의 첫 번째 job step에 `if: vars.AUTOMATION_ENABLED == 'true'`를 삽입했다.
Repository Variable 한 곳만 `"false"`로 변경하면 신규 이벤트에 대해 즉시 비활성화된다.
실행 중인 run은 취소되지 않으므로, 긴급 차단이 필요하면 GitHub UI에서 workflow를 직접 비활성화해야 한다.

### Fork PR 보안
`pull_request_target` 트리거를 일절 사용하지 않았다.
- `maestro-dispatch`: `issues` 이벤트 → 외부 코드 실행 없음.
- `worker-dispatch`: `repository_dispatch` → write access가 있어야 emit 가능.
- `comment-handler`: `author_association in [OWNER, MEMBER, COLLABORATOR]` 가드.
- `auto-rebase` / `gatekeeper`: `push: main` / `pull_request_review` — fork에서 트리거 불가.

`ANTHROPIC_API_KEY`는 fork PR 컨텍스트에서 절대 노출되지 않는다.

### Worker 격리
`worker-dispatch.yml`에서 각 Worker는 독립 `git worktree`를 `/tmp/worktree-NN-PID` 경로에 생성한다.
`fail-fast: false` 설정으로 한 Worker 실패가 다른 Worker를 취소하지 않는다.

### Gatekeeper 조건 확인 방식
- Build & Test: `gh api .../check-suites`로 `conclusion: success` 건수와 전체 건수를 비교.
- Gemini: PR 코멘트에서 `P0: 0` / `P1: 0` 패턴을 정규식으로 파싱.
- Codex: PR 코멘트에서 `codex-review: pass` 문자열 존재 여부 확인.
  (Codex review 마커 생성기는 별도 후속 작업으로 out-of-scope.)

---

## 전제 조건 및 선행 작업

| 항목 | 상태 |
|------|------|
| PR #29 (`chore/harness-automation-design`) 머지 | **선행 필요** — `maestro.md`와 `worker.md`가 `main`에 있어야 워크플로 실행 가능 |
| Repository Secret `ANTHROPIC_API_KEY` 등록 | **수동 설정 필요** |
| Repository Variable `AUTOMATION_ENABLED` = `"true"` 설정 | **수동 설정 필요** (기본 `"false"`) |
| GitHub Labels `maestro:auto` / `automation:worker` / `needs-human` 생성 | **수동 생성 필요** |

---

## 미해결 사항 및 후속 작업

1. **Codex review 마커 생성기 미구현.** Gatekeeper의 조건 3(`codex-review: pass`)은 소비자만 구현됐다.
   마커를 PR에 게시하는 Codex review GitHub App 또는 Actions step은 out-of-scope로 남겨뒀다.
2. **비용 토큰 계수 검증.** `MAESTRO_TOKEN_BUDGET` / `WORKER_TOKEN_BUDGET` 환경변수를 전달하지만,
   실제 Claude Code SDK가 이를 어떻게 소비하는지는 SDK 문서 확인 후 Maestro/Worker 에이전트 내부 로직에서 다뤄야 한다.
3. **peter-evans/repository-dispatch 액션 의존.** `maestro-dispatch.yml`이 `peter-evans/repository-dispatch@v3`를 사용한다.
   이 액션이 없다면 `gh api` 직접 호출로 대체할 수 있다.
