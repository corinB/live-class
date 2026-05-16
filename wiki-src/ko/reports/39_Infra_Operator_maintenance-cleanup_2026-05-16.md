# 39_Infra_Operator Maintenance Cleanup Workflow — 작업 리포트

**작성일**: 2026-05-16
**브랜치**: chore/task-39-maintenance-cleanup
**태스크**: plan/before/39_Infra_Operator_Maintenance_Cleanup_Workflow.md

---

## 요약

NN 39 (Phase G-5) 태스크를 완료했습니다.
`.github/workflows/maintenance-cleanup.yml` 워크플로우 파일을 신규 생성했습니다.

---

## 변경 내역

### 신규 파일: `.github/workflows/maintenance-cleanup.yml` (163줄)

4가지 트리거로 동작하는 GitHub Actions 워크플로우를 작성했습니다.

- **schedule**: 매주 일요일 UTC 00:00 cron
- **workflow_run**: CI 워크플로우가 main 브랜치에서 성공(conclusion=success) 완료 시
- **issues/labeled**: 이슈에 라벨 부착 시 (maestro:auto 라벨인 경우에만 실제 실행)
- **workflow_dispatch**: 수동 실행 (execute=true 입력 시 실제 삭제, 기본값 dry-run)

워크플로우 동작 흐름은 다음과 같습니다.

1. `cleanup` 단일 잡에서 `if` 조건으로 불필요한 트리거를 필터링합니다.
2. 병합된 PR의 헤드 브랜치 중 remote에 여전히 존재하는 stale 브랜치를 탐지합니다.
3. `.claude/scripts/cleanup-worktrees.sh` 및 `.claude/scripts/cleanup-untracked-leftovers.sh` 를 실행합니다 (NN 36, 37이 머지되면 활성화; 파일이 없으면 skip 처리).
4. `${RUNNER_TEMP}/cleanup-report.md`에 리포트를 취합합니다.
5. `chore` 라벨이 있는 열린 이슈에 sticky 코멘트를 업데이트합니다 (마커 `<!-- maintenance-cleanup-sticky -->` 로 중복 방지). 없으면 새 이슈를 생성합니다.

---

## 라벨 생성 결과

```
gh label create maintenance-cleanup --color "FBCA04" \
  --description "Tracked by maintenance-cleanup workflow" --force
```

결과: `maintenance-cleanup` 라벨 생성 완료 (#FBCA04 색상).

---

## YAML 파싱 smoke test

```
python3 -c "import yaml; yaml.safe_load(open('.github/workflows/maintenance-cleanup.yml', encoding='utf-8'))"
```

결과: **OK** — 파싱 오류 없음.
- Top-level keys: `name`, `on`(True), `permissions`, `concurrency`, `jobs`
- Jobs: `cleanup`
- Triggers: `schedule`, `workflow_run`, `issues`, `workflow_dispatch`

---

## scope 위반 없음 확인

변경된 파일 목록.
- `.github/workflows/maintenance-cleanup.yml` (신규) — 허용
- `plan/before/39_Infra_Operator_Maintenance_Cleanup_Workflow.md` (신규, plan 파일) — 허용
- `plan/after/39_Infra_Operator_Maintenance_Cleanup_Workflow.md` (신규, plan 전환) — 허용
- `reports/39_Infra_Operator_maintenance-cleanup_2026-05-16.md` (신규, 리포트) — 허용

`wiki-src/`, `live-class/`, `front/`, `.claude/hooks/`, `.claude/settings.json`,
`context.yaml`, 기타 `.github/workflows/*.yml` 에는 변경 없음.

---

## 사후 smoke test (PR 머지 후 실행 권장)

PR 머지 후 다음 명령으로 수동 트리거 가능합니다.

```bash
gh workflow run maintenance-cleanup.yml -f execute=false
```

`gh run list --workflow=maintenance-cleanup.yml --limit 5` 로 실행 상태 확인.
stale 브랜치가 발견되면 `chore` 라벨 이슈에 sticky 코멘트가 생성됩니다.

---

## 참고

- Issue #70 (Phase G-5)
- Refs: plan/before/39_Infra_Operator_Maintenance_Cleanup_Workflow.md
- 의존 태스크: NN 36 (cleanup-worktrees.sh), NN 37 (cleanup-untracked-leftovers.sh) — 스크립트 파일이 존재하지 않을 경우 skip 처리로 설계함.