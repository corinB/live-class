# 32_Infra_Operator_Wiki_Pair_Sync_Guards — 작업 종료 보고서

작성일: 2026-05-16
작성자: worker (automation pipeline)
브랜치: chore/task-32-wiki-pair-sync
이슈: #70 Phase D

---

## 요약

루트 doc 4개(`ARCHITECTURE.md`, `DOCS.md`, `CONTRIBUTING.md`, `ORCHESTRATION.md`)와 각각 대응하는 `wiki-src/ko/*-detail.md` 파일이 항상 동시에 변경되도록 강제하는 guard 를 설치했다. 로컬 commit 차단(훅 2개) + Write/Edit 단계 경고(훅 1개) + PR-time CI 검증(워크플로우 1개)으로 구성된다.

---

## 변경 내역

### 신규 파일

| 경로 | 역할 |
|------|------|
| `.claude/hooks/pre-write-wiki-pair-sync.sh` | Write/Edit PreToolUse — 루트 doc 수정 시 짝 wiki 파일이 dirty 상태인지 확인, 아니면 deny |
| `.claude/hooks/pre-bash-block-commit-on-pair-skew.sh` | Bash PreToolUse — `git commit` 명령 시 staged 파일 검사, 짝 한쪽만 staged 이면 deny |
| `.claude/hooks/pre-bash-block-rm-pair-skew.sh` | Bash PreToolUse — `rm`/`git rm` 명령 시 짝 한쪽만 삭제 대상이면 deny |
| `.github/workflows/wiki-pair-sync.yml` | PR 시 루트 doc ↔ wiki-src 쌍 변경 불일치를 감지해 CI fail |

### 수정 파일

| 경로 | 변경 내용 |
|------|-----------|
| `.claude/settings.json` | `PreToolUse` 배열에 3개 훅 등록 (`Write\|Edit` 1개, `Bash` 2개) |
| `plan/before/32_Infra_Operator_Wiki_Pair_Sync_Guards.md` | 작업 파일 복사(worktree에 없었음) |

---

## 스모크 테스트 결과

테스트 환경: `C:\work\task32` worktree, Git Bash (`C:\Program Files\Git\bin\bash.exe`)

| 시나리오 | 예상 결과 | 실제 결과 |
|----------|-----------|-----------|
| `git commit` — `ARCHITECTURE.md`만 staged | deny (exit 2) | **PASS** — `{"permissionDecision":"deny","reason":"pair-skew: ARCHITECTURE.md 이 staged 되었지만 wiki-src/ko/architecture-detail.md 는 staged 되지 않았습니다..."}` |
| `git commit` — `ARCHITECTURE.md` + `wiki-src/ko/architecture-detail.md` 모두 staged | pass (exit 0) | **PASS** |
| `git commit` — `WIKI_PAIR_GUARD_OFF=1` 환경변수 설정, 한쪽만 staged | pass (exit 0) | **PASS** — 탈출 해치 동작 확인 |
| 비-commit 명령 (예: `echo hello`) | pass (exit 0) | **PASS** |
| 아무것도 staged 안 됨 + `git commit` | pass (exit 0) | **PASS** |
| `wiki-pair-sync.yml` YAML 파싱 (`python yaml.safe_load`) | 오류 없음 | **PASS** — `jobs: ['wiki-pair-sync']`, paths 8개 확인 |

---

## 설계 결정 사항

1. **bash 연관 배열(`declare -A`) 대신 병렬 배열 사용** — macOS/Git Bash 환경에서 bash 3.x 호환성을 위해 인덱스 배열 2개로 구현.
2. **`pre-write-wiki-pair-sync.sh` 는 deny, 나머지는 deny** — Write/Edit 단계에서 사전 차단하고, commit 단계에서도 이중 확인.
3. **워크플로우 job name = `wiki-pair-sync`** — Required status check 으로 추가할 때 이름 일치 보장.
4. **`fetch-depth: 0`** — PR base..HEAD diff 계산에 필요.

---

## Scope 위반 여부

없음. 변경된 파일은 모두 과제 `scope`에 명시된 `.claude/hooks/*.sh`, `.claude/settings.json`, `.github/workflows/wiki-pair-sync.yml`, `plan/before/32_*.md` 범위 내에 있다.

---

Refs: plan/before/32_Infra_Operator_Wiki_Pair_Sync_Guards.md