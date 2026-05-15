# 33_Infra_Operator_Context_Drift_Cron — 실행 보고서

날짜. 2026-05-16
작업자. Worker (NN 33, Phase E)
브랜치. chore/task-33-drift-cron

---

## 요약

`context.yaml` drift 를 매일 감시하는 GitHub Actions cron 워크플로우를 추가했다.
`audit-context-yaml.py` (NN 29에서 도입) 스크립트를 runner 위에서 실행하고,
exit code 에 따라 tracking Issue 를 개설하거나 닫는다.

---

## 변경 내역

| 파일 | 유형 | 설명 |
|------|------|------|
| `.github/workflows/context-drift-cron.yml` | 신규 | 매일 UTC 00:00 (KST 09:00) + workflow_dispatch 트리거, audit 실행, Issue 관리 |

워크플로우 주요 구성.

- 트리거. `schedule: cron: '0 0 * * *'` + `workflow_dispatch`.
- 권한. `contents: read`, `issues: write`.
- Python 3.12 + PyYAML 설치 후 `audit-context-yaml.py` 실행.
- exit 0. 기존 `context-drift` label 이슈가 열려 있으면 코멘트 후 close.
- exit 1. 기존 이슈가 있으면 코멘트 추가, 없으면 새 이슈 개설 (`context-drift,chore` label).
- `concurrency.group: context-drift-cron`, `cancel-in-progress: false` — 동시 실행 방지.

---

## 라벨 생성

```
gh label create context-drift --color "d73a4a" --description "context.yaml drift tracker" --force
```

실행 결과. `context-drift` label 확인됨 (`#d73a4a`).

---

## 스모크 테스트 (로컬 dry-run)

worktree `C:\work\task33` 에서 직접 audit 실행.

```
python3 .claude/scripts/audit-context-yaml.py
no drift
exit: 0
```

현재 `main` 기준으로 drift 없음 확인. exit 0 이므로 워크플로우는 Clean 경로(이슈 close/skip)를 탄다.

dirty baseline 시뮬레이션 (mental model).
- `context.yaml` 의 `metadata.last_indexed` 를 어제 날짜로 변경하면 exit 1 발생.
- 워크플로우는 `[context-drift] audit-context-yaml.py reports drift YYYY-MM-DD` 이슈를 생성.
- 다음 clean run 시 이슈 자동 close.

실제 push-to-Actions 검증은 PR merge 후 `workflow_dispatch` 로 수행 예정.

---

## 스코프 준수

수정 파일. `.github/workflows/context-drift-cron.yml` (신규 1개).
수정하지 않은 파일. `.claude/hooks/**`, `context.yaml`, `live-class/**`, `wiki-src/**` 콘텐츠.
외부 액션. `gh label create context-drift` (repo label 등록).

---

## PR 정보

- 브랜치. `chore/task-33-drift-cron`
- 커밋 제목. `ci: daily context.yaml drift audit cron`
- Refs. `plan/before/33_Infra_Operator_Context_Drift_Cron.md`