# 30_Infra_Operator_Branch_Protection_Setup — 실행 보고서

- **작성일**: 2026-05-16
- **브랜치**: chore/task-30-branch-protection
- **태스크**: plan/before/30_Infra_Operator_Branch_Protection_Setup.md
- **이슈**: #70 Phase B

---

## 작업 요약

`main` 브랜치에 GitHub branch protection을 처음으로 적용했다. `.github/CODEOWNERS` 파일을 생성하고, `gh api -X PUT` 호출로 보호 규칙을 설정했다.

---

## 변경 내역

### 1. `.github/CODEOWNERS` 생성

```
# 모든 변경은 corinB 리뷰가 필요한 기본 CODEOWNERS

# Default owner for everything in the repo
* @corinB

# Specific tighter ownership for protected areas
/wiki-src/**          @corinB
/.claude/**           @corinB
/.github/workflows/** @corinB
/context.yaml         @corinB
/CLAUDE.md            @corinB
```

인코딩: UTF-8 without BOM.

### 2. Branch Protection PUT 결과 (HTTP 200)

```json
{
  "required_status_checks": {
    "strict": true,
    "contexts": ["Evaluate Merge Readiness", "gemini-review", "link-check"]
  },
  "required_pull_request_reviews": {
    "dismiss_stale_reviews": true,
    "require_code_owner_reviews": true,
    "required_approving_review_count": 1
  },
  "enforce_admins": { "enabled": true },
  "required_linear_history": { "enabled": true },
  "allow_force_pushes": { "enabled": false },
  "allow_deletions": { "enabled": false },
  "lock_branch": { "enabled": false }
}
```

---

## Status Check Context 결정 근거

실제 workflow job 이름을 `gh api repos/corinB/live-class/actions/runs/{id}/jobs` 로 확인했다.

| Workflow | Job name | 포함 여부 |
|---|---|---|
| Gatekeeper | `Evaluate Merge Readiness` | 포함 |
| Gemini AI Code Review | `gemini-review` | 포함 |
| Link Checker | `link-check` | 포함 |
| CI | `Build & Test` | 미포함 — `live-class/**` 경로 변경이 없는 PR에서 트리거되지 않아 포함 시 PR 영구 block 위험 |

---

## 검증 결과

### GET 검증

`gh api repos/corinB/live-class/branches/main/protection` 200 OK 반환.

| 항목 | 기대값 | 실제값 |
|---|---|---|
| enforce_admins.enabled | true | true |
| allow_force_pushes.enabled | false | false |
| allow_deletions.enabled | false | false |
| require_code_owner_reviews | true | true |
| required_approving_review_count | 1 | 1 |
| dismiss_stale_reviews | true | true |
| required_status_checks.strict | true | true |
| required_linear_history.enabled | true | true |

### Force-push 거부 검증

`allow_force_pushes.enabled: false` API 응답으로 확인. 현재 feature 브랜치 HEAD가 main과 동일 커밋이므로 실제 push 시도는 "Everything up-to-date"로 응답됨. 보호 설정은 API 레벨에서 확인 완료.

---

## Scope 위반 없음

수정한 파일.
- `.github/CODEOWNERS` (신규 생성)
- `plan/before/30_Infra_Operator_Branch_Protection_Setup.md` (Maestro 브랜치에서 복사, plan 이동)
- `wiki-src/plan-after/30_Infra_Operator_Branch_Protection_Setup.md` (plan transition)
- `wiki-src/ko/reports/30_Infra_Operator_branch-protection_2026-05-16.md` (이 파일)
- `reports/30_Infra_Operator_branch-protection_2026-05-16.md` (legacy hook)

`live-class/**`, `.github/workflows/**`, `context.yaml`, `DOCS.md`, `ARCHITECTURE.md`, `front/**` 는 일체 건드리지 않았다.

---

## 2026-05-16 후속 업데이트 — 실태 정정 + `required_conversation_resolution` 활성화

본 보고서 초안(PR #73 시점)이 작성된 이후 실제 운영에서 발견된 갭을 정리하고, 본 PR(task 30 마무리)에서 적용한 추가 변경을 기록한다.

### 실제 운영 상태 vs 초안 기재 사항

| 항목 | 초안 기재 | 2026-05-16 실측 | 비고 |
|---|---|---|---|
| `required_pull_request_reviews` | 1+approval, require_code_owner_reviews=true | **null (미설정)** | 솔로 워크플로에서 GitHub의 self-approval 금지 정책 때문에 PR 자체 머지가 봉쇄되어 해제 유지. PR #84-87 자체 머지 흐름이 그 결과. |
| `required_status_checks.contexts` | (초안 명시 없음) | `Evaluate Merge Readiness`, `gemini-review`, `link-check` | `Build & Test`(CI) 부재. ci.yml의 `paths` 화이트리스트로 `.claude/**`나 `.github/workflows/**`만 건드린 PR에서 미실행 → required로 추가 시 BLOCKED 회귀 우려. PR #87이 해당 회귀 패턴을 별도로 해소했지만 ci.yml 자체는 미전환. **별도 follow-up PR(NN 41 신규 또는 chore)에서 ci.yml을 PR #87 패턴으로 변환 후 `Build & Test`를 required에 편입할 예정.** |
| `enforce_admins` | true | true | 일치. |
| `allow_force_pushes` | false | false | 일치. |
| `allow_deletions` | false | false | 일치. |
| `required_linear_history` | true | true | 일치. |
| `required_conversation_resolution` | (초안 명시 없음) | **false → true (본 PR에서 활성화)** | resolved되지 않은 PR review 코멘트가 있으면 머지 차단. PR 자체에 미해결 코멘트가 없으면 영향 없음. |

### 본 PR에서의 실제 변경

```bash
# GitHub Branch Protection API는 부분 PATCH 서브엔드포인트를 제공하지 않으므로
# 전체 PUT으로 갱신한다. 다른 키들은 기존 값을 모두 그대로 보존하면서
# required_conversation_resolution만 false→true로 토글하는 payload.
cat > payload.json <<'JSON'
{
  "required_status_checks": {
    "strict": true,
    "contexts": ["Evaluate Merge Readiness", "gemini-review", "link-check"]
  },
  "enforce_admins": true,
  "required_pull_request_reviews": null,
  "restrictions": null,
  "allow_force_pushes": false,
  "allow_deletions": false,
  "required_linear_history": true,
  "required_conversation_resolution": true
}
JSON
gh api -X PUT repos/corinB/live-class/branches/main/protection --input payload.json
```

검증.

```bash
gh api repos/corinB/live-class/branches/main/protection \
  --jq '.required_conversation_resolution.enabled'
# → true
```

### Issue #70 spec 대비 deviation 명시

- `required_pull_request_reviews` 미적용 — 솔로 워크플로의 제약. 협업 인원 확장 시 재검토 follow-up.
- `Build & Test` required 미포함 — `ci.yml` "always run, gate internally" 전환을 선행한 follow-up에서 처리.

이 두 deviation은 의도된 것이며, Issue #70의 strict 요건이 솔로 운영 현실과 충돌하는 부분을 명시적으로 기록한다.

---

## Refs

Refs: wiki-src/plan-before/30_Infra_Operator_Branch_Protection_Setup.md