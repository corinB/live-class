# 자동화 파이프라인 트러블슈팅 가이드

> 대상: 파이프라인이 멈추거나 비정상 동작할 때.
> 사용 가이드 · HITL 흐름은 `docs/guides/automation-usage.md`, `docs/guides/automation-hitl.md` 참고.

## 빠른 진단 체크리스트

순서대로 확인한다.

1. `gh variable list` 에 `AUTOMATION_ENABLED = "true"` 가 있는가? 없거나 `"false"` 면 자동화 전체가 비활성 상태다.
2. `gh label list` 에 `maestro:auto`, `automation:worker`, `needs-maestro`, `needs-human` 네 라벨이 존재하는가?
3. 메인 Claude Code 세션이 띄워져 있는가? 자동화 파이프라인은 GitHub Actions 가 LLM 을 호출하지 않으므로, 메인 세션이 죽어 있으면 Maestro/Worker 가 돌지 않는다.
4. 막 머지된 `main` commit 이 있는가? 그렇다면 `auto-rebase.yml` 이 열린 PR 을 rebase 중일 수 있다.

## 시나리오별 대응

### S1: Issue 를 만들었는데 `needs-maestro` 라벨이 안 붙는다

원인 후보:
- `AUTOMATION_ENABLED != "true"`. → `gh variable set AUTOMATION_ENABLED --body "true"`.
- Issue 에 `maestro:auto` 라벨이 없음. → 템플릿을 통해 새로 만들거나 라벨을 수동 추가.
- `maestro-dispatch.yml` 실행 자체가 실패. → Actions 탭 → `Maestro Dispatch (notify)` workflow 로그 확인.

### S2: Maestro 가 halt 했다 (Issue 코멘트로 알림)

Maestro 가 다음 사유로 halt 한다.

| 사유 | 대응 |
|------|------|
| `DOCS.md` 또는 `ARCHITECTURE.md` 미존재 | 문서 복원 후 Issue 에 `@claude restart` 코멘트 |
| `acceptance` / `scope` 필드 누락 | Issue 를 닫고 템플릿대로 다시 만든다 |
| Issue 가 `scope` 밖을 요구 | Issue 를 닫고 scope 를 정정해 다시 만든다 |
| 토큰 예산 초과 (200K) | Issue 를 잘게 쪼개서 다시 만든다 |

### S3: Worker 가 PR 을 못 열고 실패

Worker 는 `./gradlew test` 실패 시 PR 을 안 연다. 대신 stdout 으로 halt 사유를 보고한다.

대응:
1. 메인 세션 출력에서 어느 task NN 이 실패했는지 확인.
2. 실패 사유가 명확하면 메인 세션에 `task NN <지시>` 로 재시도. Worker 가 worktree 안에서 재실행된다.
3. 실패 사유가 불명확하면 worktree 안에서 직접 디버그: `cd .claude/worktrees/<id> && ./gradlew test`.
4. 영속적으로 막혔다면 사용자가 직접 코드를 짜고 push 한다 (자동화 포기).

### S4: PR 에 `needs-human` 라벨이 붙었다

Gatekeeper 가 머지를 거부했다. PR 코멘트에 어떤 조건이 실패했는지 적혀 있다.

- **Build & Test fail**: 코드를 고쳐야 한다. PR 에 `@claude fix the failing test FooBarTest` 코멘트 → HITL 흐름.
- **Gemini P0/P1 > 0**: Gemini 가 critical 결함을 발견. 코멘트 본문에서 결함 위치 확인 → `@claude fix ...` 로 HITL.

수정 commit 이 push 되면 라벨이 자동으로 사라지지는 않는다. Worker 재가동 시 메인 세션이 `gh pr edit <n> --remove-label needs-human` 도 수행한다.

### S5: auto-rebase 가 충돌로 멈췄다

PR 에 `needs-human` 라벨 + `@claude rebase` 코멘트가 자동으로 달린다.

대응:
1. 메인 세션에 `PR #<n> rebase 충돌 해결해` 라고 지시.
2. 메인 세션이 worktree 에 들어가 충돌을 해결하고 push.
3. 충돌이 너무 복잡하면 PR 을 닫고 Maestro 에게 재분해를 시킨다 (새 Issue 코멘트 `@claude re-plan based on latest main`).

### S6: 자동 머지가 일어났는데 main 이 깨졌다

`docs/architecture/automation-pipeline.md` 의 명시한 대로 roll-back 은 **수동**.

```
gh pr create --base main \
  --title "Revert \"<원본 PR 제목>\"" \
  --body "Reverts #<머지된 PR 번호>"
```

또는 GitHub UI 의 "Revert" 버튼.

### S7: 모든 것을 멈추고 싶다 (kill switch)

```
gh variable set AUTOMATION_ENABLED --body "false"
```

이후 모든 자동화 workflow 의 첫 step 이 `if: vars.AUTOMATION_ENABLED == 'true'` 에서 막혀 no-op 한다. 메인 세션도 사용자가 트리거 안 하면 안 돈다.

`true` 로 다시 올리려면:

```
gh variable set AUTOMATION_ENABLED --body "true"
```

### S8: 비용 cap 을 넘었다

Maestro/Worker 가 자동으로 멈추고 Issue · PR 에 코멘트를 단다. Issue 를 닫고 작업을 잘게 쪼개서 새 Issue 를 만든다. 한 Issue 의 한도는 200K 토큰 (`docs/architecture/automation-pipeline.md` 에서 조정).

## 로그·증적 위치

| 종류 | 위치 |
|------|------|
| GitHub Actions 실행 로그 | Repository → Actions 탭 (workflow run 별) |
| Worker 종료 보고 | `reports/NN_<Role>_<slug>.md` (Worker 가 성공 시 push) |
| 메인 세션 호출 기록 | 메인 Claude Code 세션의 transcript |
| Hook 차단 로그 (size/surrogate) | `reports/surrogate-blocks.log` (gitignored) |
| PR/Issue 코멘트 | GitHub UI 또는 `gh pr view`, `gh issue view` |

## 자주 묻는 실수

- **AUTOMATION_ENABLED 를 secret 에 등록**: secret 이 아니라 **variable**. `gh variable set AUTOMATION_ENABLED ...`.
- **fork PR 에서 Gatekeeper 가 안 돈다**: 의도된 동작. fork PR 은 secret 노출을 막기 위해 자동화에서 제외.
- **`@claude` 코멘트를 남기고 답을 기다림**: 메인 세션이 안 돌면 아무 일도 일어나지 않는다. 사용자가 메인 세션에 명시 트리거.

## 회복 절차 요약

| 증상 | 한 줄 처방 |
|------|-----------|
| 자동화 안 도는 듯 | `gh variable list` 로 `AUTOMATION_ENABLED` 확인 |
| Worker 실패 누적 | `gh pr list --label automation:worker --label needs-human` 로 점검 |
| 모든 것 멈추고 싶음 | `gh variable set AUTOMATION_ENABLED --body "false"` |
| 빠르게 origin 으로 돌아가기 | 메인 세션에 `자동화 진행상황 보고해` |
