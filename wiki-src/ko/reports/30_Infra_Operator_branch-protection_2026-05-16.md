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

## Refs

Refs: wiki-src/plan-before/30_Infra_Operator_Branch_Protection_Setup.md