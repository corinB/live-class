# 자동화 파이프라인 사용 가이드

> 대상: 자동화 파이프라인으로 작업을 의뢰하려는 일반 사용자.
> 전체 설계는 `docs/architecture/automation-pipeline.md` 참고.

## 한 줄 요약

GitHub 에 Issue 한 개를 만들고, 메인 Claude Code 세션에 `Issue #<n> 처리해` 라고 말하면 Maestro 가 작업을 잘게 쪼개고 Worker 들이 각각 PR 을 연다. CI · Gemini 가 통과하면 자동으로 main 에 머지된다.

## 사전 조건

다음 셋업이 끝나 있어야 한다. 안 돼 있으면 `docs/guides/automation-troubleshooting.md` 를 본다.

- Repository variable `AUTOMATION_ENABLED = "true"`.
- Labels 4 개 존재: `maestro:auto`, `automation:worker`, `needs-maestro`, `needs-human`.
- 메인 Claude Code 세션을 띄울 수 있는 환경 (이 작업의 LLM 호출이 거기서 일어난다).
- `DOCS.md` · `ARCHITECTURE.md` 가 최신 상태.

## 절차

### 1. Issue 작성

GitHub UI 의 `New issue` → `Automation feature` 템플릿 선택. 다음 세 필드는 필수다.

- **acceptance** (markdown 체크리스트): "이 작업이 끝났다고 볼 수 있는 기준" 을 체크박스로 나열.
  - 예시:
    ```
    - [ ] GET /health 가 200 과 {"status": "ok"} 를 반환한다.
    - [ ] 통합 테스트 `HealthEndpointIT` 가 통과한다.
    ```
- **scope** (markdown): 작업이 건드릴 수 있는 디렉터리·모듈을 명시.
  - 예시: `live-class/src/main/java/com/example/liveclass/web/health/**` 와 그 테스트.
  - `scope` 밖의 파일을 수정해야 한다면 Issue 를 닫고 새로 작성한다.
- **priority** (dropdown): `P0` (긴급) / `P1` (중요) / `P2` (일반).

템플릿이 자동으로 `maestro:auto` 라벨을 붙인다.

### 2. 자동 알림 확인

`maestro-dispatch.yml` 이 약 30초 안에 Issue 에 코멘트를 달고 `needs-maestro` 라벨을 추가한다. 코멘트가 안 보이면:

- Repository variable `AUTOMATION_ENABLED` 가 `"true"` 인지 확인.
- Actions 탭에서 `Maestro Dispatch (notify)` workflow 의 최신 run 을 본다. 실패면 troubleshooting 가이드.

### 3. 메인 세션 트리거

메인 Claude Code 세션에 한 문장으로 지시한다:

```
Issue #<n> 처리해
```

세션이 자동으로:

1. `gh issue view <n>` 로 Issue 본문을 읽는다.
2. Maestro agent 를 invoke 한다. Maestro 가 `plan/before/NN_*.md` 와 `manifest.json` 을 만들고 `chore/maestro-<n>` 브랜치로 push.
3. manifest 를 읽고 Worker agent 들을 의존 그래프에 따라 병렬·직렬로 dispatch.
4. 각 Worker 가 worktree 안에서 코드 작성, `./gradlew test` 로 자체 검증, `gh pr create --label automation:worker` 로 PR 을 연다.

진행 중에는 메인 세션의 출력으로 단계별 결과가 보고된다.

### 4. PR 자동 머지 대기

각 PR 에서 다음이 자동으로 일어난다.

- `ci.yml` (Build & Test) 가 회전.
- `gemini-review.yml` 이 P0/P1 분석 코멘트를 단다.
- 둘 다 그린이면 `gatekeeper.yml` 이 자동으로 `gh pr merge --squash --auto` 를 호출한다.

Issue 와 PR 의 라벨로 진행 상황을 추적한다.

| 라벨 | 의미 |
|------|------|
| `maestro:auto` | Issue 가 자동화 대상 |
| `needs-maestro` | 메인 세션에서 Maestro 호출 대기 |
| `automation:worker` | Worker 가 연 PR |
| `needs-human` | 자동화가 멈춤. 사람 개입 필요 |

### 5. main 동기화

main 에 새 commit 이 들어가면 `auto-rebase.yml` 이 열린 `automation:worker` PR 들을 자동 rebase 한다. 충돌이 나면 `needs-human` 라벨과 PR 코멘트가 붙는다 — troubleshooting 가이드를 본다.

## 비용 추정

Issue 한 개당 토큰 한도는 200,000 (Maestro 분해 + Worker 들 코딩 합산). 한도를 넘으면 Maestro/Worker 가 자동으로 멈추고 Issue 에 알림을 단다. 큰 작업은 Issue 를 잘게 쪼개서 다시 만든다.

## Anti-patterns

- **acceptance 가 모호한 Issue**: "성능 개선" 같은 자유 텍스트는 Maestro 가 받아들이지 않는다. 체크박스로 측정 가능한 기준을 적는다.
- **scope 가 비어 있거나 너무 넓은 Issue**: Maestro 가 halt 하고 Issue 에 코멘트를 단다. 명확한 디렉터리/패턴을 적는다.
- **여러 Issue 동시 처리**: 한 epoch (메인 세션)당 Maestro 인스턴스는 1 개. 여러 Issue 를 동시에 던지면 큐로 대기한다.
- **PR 직접 머지**: 자동화 PR 을 사용자가 손으로 `gh pr merge` 하면 라벨 추적이 깨진다. Gatekeeper 가 돌게 둔다.

## 다음 단계

- HITL 개입이 필요할 때 → `docs/guides/automation-hitl.md`.
- 파이프라인이 멈췄을 때 → `docs/guides/automation-troubleshooting.md`.
- 설계 세부 → `docs/architecture/automation-pipeline.md`.
