# 자동화 파이프라인 HITL 가이드

> 대상: 자동화로 만든 PR · Issue 에 사람이 개입할 때.
> 사용 흐름의 전체 그림은 `docs/guides/automation-usage.md`.

## 한 줄 요약

PR · Issue 에 `@claude ...` 로 시작하는 코멘트를 남기면, 사용자가 그것을 보고 메인 Claude Code 세션에 같은 내용을 전달한다. 메인 세션이 Maestro · Worker 를 다시 invoke 해서 대응한다. 자동 트리거는 없다.

> 본 파이프라인은 비용 절감을 위해 `ANTHROPIC_API_KEY` 를 GitHub Actions 에 두지 않는다. 그래서 `comment-handler.yml` 도 없다. HITL 의 마지막 한 단계 — "코멘트 → LLM 재호출" — 은 사람이 메인 세션을 통해 수행한다.

## 코멘트 prefix 표준

PR 또는 Issue 본문에 `@claude` 멘션을 포함한다. 메인 세션이 `gh pr view` / `gh issue view` 로 코멘트를 스캔할 때 이 prefix 로 골라낸다.

| 의도 | 예시 |
|------|------|
| 수정 지시 | `@claude fix the null check on line 42 of HealthController.java` |
| 추가 instruction | `@claude also add a @Tag("ops") on the integration test` |
| Re-plan 요청 | `@claude split task 03 into 03a and 03b` (Issue 코멘트, Maestro 재실행) |
| Rebase 요청 | `@claude rebase` (자동 rebase 가 실패한 PR 에서 수동 트리거) |
| 작업 중단 요청 | `@claude stop this PR — wrong direction` |

`@claude` 가 없으면 자동화 흐름과는 무관한 일반 코멘트로 간주한다.

## 흐름

1. **사용자가 코멘트 작성**: PR (또는 Issue) 에 `@claude <지시>` 작성.
2. **사용자가 메인 세션에 알림**: 메인 Claude Code 세션에 `PR #<n> 의 @claude 코멘트 반영해` 또는 `Issue #<n> 다시 분해해` 라고 말한다.
3. **메인 세션이 코멘트 읽기**: `gh pr view <n> --json comments` 로 최신 `@claude` 코멘트를 가져와 의도 파싱.
4. **메인 세션이 Worker/Maestro 재호출**:
   - PR 코멘트 → 해당 PR 의 task NN 을 식별해서 Worker 를 `extra_instruction` 과 함께 재호출. 새 commit 이 PR 에 추가된다.
   - Issue 코멘트 → Maestro 를 재호출. `plan/before/` 가 갱신되고 필요 시 새 Worker 들이 추가 PR 을 연다.
5. **자동 머지 재시도**: Worker 가 새 commit 을 push 하면 CI · Gemini 가 다시 회전하고, 통과 시 Gatekeeper 가 머지한다.

## PR review comment vs Issue comment

| 채널 | 사용 시점 |
|------|-----------|
| PR review comment (코드 줄 옆) | 특정 코드 라인을 가리키는 수정 (`@claude fix the null check on line 42`) |
| PR conversation comment | PR 전체에 대한 일반 코멘트 (`@claude also add ...`) |
| Issue comment | Maestro 의 재분해 요청 또는 새 task 추가 요청 |

기능상 차이는 없다. 사용자는 가장 자연스러운 위치를 고른다.

## 외부 사용자 / fork PR

- 메인 세션은 사용자 본인이 운영하므로 코멘트 작성자 검증은 사람이 한다 (메인 세션에 전달할지 말지 사용자 판단).
- fork PR 의 코멘트는 GitHub Actions 가 secret 을 노출하지 않게 처리하므로 자동화 무해. 단 메인 세션이 fork PR 의 코드를 실행하면 임의 코드를 돌리는 셈이므로, 검토 후 처리한다.

## Anti-patterns

- `@claude` 없이 일반 코멘트만 남기기 → 메인 세션이 지나갈 수 있다.
- 한 PR 에 `@claude` 코멘트 여러 개 누적 → 메인 세션은 가장 최근 것만 본다. 추가 지시가 있다면 새 코멘트로.
- 메인 세션 종료 상태에서 코멘트만 남기기 → 누구도 처리 안 함. 사용자가 다음에 세션을 열 때 직접 트리거해야.

## 다음 단계

- PR 이 `needs-human` 라벨로 멈춰있다면 → `docs/guides/automation-troubleshooting.md`.
- 자동화 자체 사용 가이드 → `docs/guides/automation-usage.md`.
