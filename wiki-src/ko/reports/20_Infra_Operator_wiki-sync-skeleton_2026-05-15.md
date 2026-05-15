# 작업 보고서 — Task 20: Wiki Sync Skeleton (Phase 1)

- **작성일:** 2026-05-15
- **담당 에이전트:** Worker (Infra_Operator)
- **이슈:** [#58](https://github.com/corinB/live-class/issues/58)
- **브랜치:** feature/task-20-wiki-sync-skeleton

---

## 요약

Issue #58 Phase 1에 해당하는 `wiki-src/` 디렉토리 골격과 GitHub Actions
Wiki 동기화 워크플로우를 신규 작성하였다.

---

## 변경 사항

### 신규 파일

| 파일 | 설명 |
|------|------|
| `wiki-src/Home.md` | Wiki 미러 목적지를 설명하는 영문 랜딩 페이지. Phase 1~8 마이그레이션 로드맵 포함. |
| `wiki-src/ko/.gitkeep` | Phase 2~5에서 사용할 한국어 콘텐츠 서브트리 자리 표시자. |
| `.github/workflows/wiki-sync.yml` | `main` 브랜치에서 `wiki-src/**` 경로 변경 시 `corinB/live-class.wiki.git`으로 전체 미러링하는 워크플로우. |

### 수정 파일

| 파일 | 설명 |
|------|------|
| `plan/before/20_Infra_Operator_Wiki_Sync_Skeleton.md` | 완료 항목 체크박스 표시 후 `plan/after/`로 이동 예정. |

---

## 사전 검증

1. **Wiki 활성화 확인.**
   `gh api repos/corinB/live-class --jq .has_wiki` → `true`. 조건 충족, 진행.

2. **YAML 문법 검사.**
   `python3 -c "import yaml; yaml.safe_load(...)"` → `YAML OK`.

3. **스코프 준수 확인.**
   변경된 파일이 모두 허용된 경로(`wiki-src/**`, `.github/workflows/wiki-sync.yml`,
   `plan/before/20_*.md`, `reports/20_*.md`) 이내에 있음을 확인.

---

## 워크플로우 설계 요점

- **트리거.** `push` to `main` with `paths: wiki-src/**` + `workflow_dispatch`.
- **인증.** 기본 `GITHUB_TOKEN`만 사용. PAT 또는 GitHub App 없음.
- **동기화 방식.** `rsync -a --delete wiki-src/ wiki-clone/` — 추가/수정/삭제 모두 전파 (full mirror).
- **봇 커밋 아이덴티티.** `github-actions[bot]` 이름 및 noreply 이메일.
- **노옵 처리.** `git diff --cached --quiet` 시 push 생략.
- **동시 실행 방지.** concurrency group `wiki-sync`, `cancel-in-progress: false`.
- **타임아웃.** `timeout-minutes: 5`.

---

## 사후 스모크 테스트 (머지 후 수행)

머지 후 `main` 브랜치에 `wiki-src/` 변경이 push되면 워크플로우가 자동 실행된다.
다음 두 명령으로 결과를 확인할 수 있다.

```bash
gh run list --workflow=wiki-sync.yml --limit 1 --json conclusion -q '.[0].conclusion'
# 기대값: success

git ls-remote https://github.com/corinB/live-class.wiki.git
# HEAD가 Home.md를 포함하는 트리를 가리켜야 함
```

브라우저에서 `https://github.com/corinB/live-class/wiki/Home`이 랜더링되는지도
확인한다.

---

## HITL 에스컬레이션

없음. 모든 체크리스트 항목이 정상 완료되었다.
