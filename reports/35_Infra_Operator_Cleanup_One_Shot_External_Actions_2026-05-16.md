# NN 35 Infra_Operator — One-Shot External Cleanup Actions (Phase G-1) 작업 완료 보고서

작성일: 2026-05-16
브랜치: chore/task-35-cleanup-externals
이슈: #70

---

## 변경 요약

Issue #70 Phase G-1 작업으로 GitHub 저장소에 네 가지 외부 변경을 적용했다.

### 1. delete_branch_on_merge 활성화

`false`에서 `true`로 변경했다. 이후 PR이 병합될 때 헤드 브랜치가 자동으로 삭제된다.

```
gh api -X PATCH repos/corinB/live-class -F delete_branch_on_merge=true
```
검증 결과: `true` 반환 확인.

### 2. Issue #58 종료

- 제목: `[docs] Migrate non-essential .md to GitHub Wiki + sync automation + hook adjustment`
- 상태 변경: `OPEN` -> `CLOSED`
- 종료 시각: `2026-05-15T19:04:25Z`
- 코멘트: PR #59-69 (8개 Phase) 전부 병합 완료, NN 24는 별도 추적으로 분리됨.

### 3. Stale 헤드 브랜치 9개 삭제

모든 브랜치가 이미 MERGED 상태의 PR과 연결되어 있거나 (maestro-issue-58는 PR 없는 orphan) 안전하게 삭제 가능한 것을 확인 후 삭제했다. 삭제 전 각 브랜치의 마지막 커밋 SHA를 `cleanup-log.md`에 기록했다.

삭제된 브랜치 목록:
- chore/ci-pr-a-cache-fix (PR #20)
- chore/harness-automation-workflows (PR #30)
- chore/harness-surrogate-guard-100kb (PR #25)
- chore/harness-track-a-p0 (PR #21, #22)
- chore/maestro-32 (PR #33)
- chore/maestro-issue-55 (PR #57)
- chore/maestro-issue-58 (orphan, no PR)
- chore/move-task-17-to-after (PR #35)
- chore/pre-flight-6-recurrence-hooks (PR #9)

스킵된 브랜치 없음.

### 4. Wiki Home 확인

https://github.com/corinB/live-class/wiki/Home HTTP 응답: `200 OK`

---

## 테스트 절차

이 작업은 애플리케이션 코드 변경이 없으므로 별도 테스트 없음. 검증은 다음 gh/curl 명령으로 수행했다.

1. `gh api repos/corinB/live-class --jq '.delete_branch_on_merge'` -> `true`
2. `gh issue view 58 --json state` -> `CLOSED`
3. `gh api repos/corinB/live-class/branches` -> 삭제된 9개 브랜치 부재 확인
4. `Invoke-WebRequest https://github.com/corinB/live-class/wiki/Home` -> HTTP 200

---

## 참고

- 상세 변경 로그: `reports/cleanup-log.md`
- 작업 계획서: `plan/before/35_Infra_Operator_Cleanup_One_Shot_External_Actions.md`
- 관련 이슈: #70 (Phase G-1), #58 (종료)
- scope 위반 없음. live-class/**, front/**, .github/workflows/**, .claude/** 미변경.