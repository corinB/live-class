# 28_Quality_Guardian_Link_Checker_CI 작업 완료 보고서

- **작업 번호:** 28
- **역할:** Quality_Guardian
- **단계:** Phase 8 / 8 (Wiki 이전 프로그램 최종 단계)
- **이슈:** [#58](https://github.com/corinB/live-class/issues/58)
- **완료 일시:** 2026-05-16
- **PR 브랜치:** ci/task-28-link-checker

---

## 요약

Issue #58 Wiki 이전 프로그램의 마지막 단계인 Phase 8 작업을 완료하였다. PR마다 Wiki URL 활성 여부와 내부 cross-link 정합성을 자동 검사하는 GitHub Actions 워크플로우(`link-checker.yml`)와 설정 파일(`.lychee.toml`)을 추가하였다.

---

## 변경 내역

### 신규 파일

| 파일 | 설명 |
|------|------|
| `.github/workflows/link-checker.yml` | lycheeverse/lychee-action@v2.3.1 기반 link-checker 워크플로우 |
| `.lychee.toml` | lychee 설정 — Wiki URL만 HTTP 검사, 외부 URL 제외 |
| `wiki-src/plan-after/28_Quality_Guardian_Link_Checker_CI.md` | 완료된 태스크 파일 (plan-after 이동) |
| `wiki-src/ko/reports/28_Quality_Guardian_Link_Checker_CI.md` | 본 보고서 (Korean) |
| `reports/28_Quality_Guardian_Link_Checker_CI_2026-05-16.md` | 보고서 복사본 (legacy hook 대응) |

### 스코프 미손 확인

- `wiki-src/**` 콘텐츠 파일 미수정.
- `ARCHITECTURE.md`, `DOCS.md`, `CONTRIBUTING.md`, `ORCHESTRATION.md`, `CLAUDE.md`, `context.yaml` 미수정.
- `.claude/agents/**`, `.claude/hooks/**`, `.claude/settings.json` 미수정.
- 기존 워크플로우(`wiki-sync.yml`, `gemini-review.yml`, `auto-rebase.yml`, `gatekeeper.yml`, `maestro-dispatch.yml`) 미수정.

---

## 워크플로우 상세

### 트리거 경로 (6개 + workflow self)

```
context.yaml
CLAUDE.md
ARCHITECTURE.md
DOCS.md
CONTRIBUTING.md
ORCHESTRATION.md
wiki-src/**
.github/workflows/link-checker.yml
```

### 검사 범위

**A. Wiki URL 활성 여부**

`context.yaml` / `CLAUDE.md` / root docs / `wiki-src/**/*.md` 에서 `https://github.com/corinB/live-class/wiki/<slug>` 형식의 URL을 추출하여 HTTP GET 요청으로 활성 여부를 확인한다. 현재 검사 대상 URL 4개.

- `https://github.com/corinB/live-class/wiki/ko-architecture-detail`
- `https://github.com/corinB/live-class/wiki/ko-docs-detail`
- `https://github.com/corinB/live-class/wiki/ko-contributing-detail`
- `https://github.com/corinB/live-class/wiki/ko-orchestration-detail`

**B. 내부 cross-link**

`wiki-src/**/*.md` 파일 내 상대 경로 링크(`[text](path.md)`)의 대상 파일이 디스크에 존재하는지 확인한다. 존재하지 않으면 오류 URL과 줄 번호를 표시하며 워크플로우를 실패시킨다.

### 설계 결정

- **도구 선택.** `lycheeverse/lychee-action@v2.3.1`을 채택하였다. 마크다운 링크 추출·HTTP 검사·TOML 설정을 지원하며, 핀 버전으로 공급망 보안을 확보한다.
- **외부 URL 제외.** `.lychee.toml`의 `include` 배열에 `https://github.com/corinB/live-class/wiki/.*` 패턴만 등록하여 제3자 URL의 rate-limit·가용성 문제를 회피한다.
- **코드 블록 처리.** lychee는 기본적으로 코드 펜스 내 링크를 무시하며, `--no-progress` 플래그로 깔끔한 출력을 유지한다.
- **권한.** `permissions: contents: read` + `GITHUB_TOKEN`만 사용. PAT 없음.
- **동시성.** `link-checker-${{ github.ref }}` 그룹, `cancel-in-progress: true`로 동일 브랜치 중복 실행 방지.
- **타임아웃.** `timeout-minutes: 5` 하드 캡.

---

## 검증

### 로컬 검증

```bash
python3 -c "import yaml; yaml.safe_load(open('.github/workflows/link-checker.yml', encoding='utf-8'))"
# → YAML OK

python3 -c "import tomllib; tomllib.loads(open('.lychee.toml', encoding='utf-8').read())"
# → TOML OK
```

워크플로우 파일 구조 확인.
- `paths:` 8개 패턴 확인.
- `permissions.contents: read` 확인.
- `timeout-minutes: 5` 확인.
- `lycheeverse/lychee-action@v2.3.1` 핀 확인.

### PR 자체가 첫 번째 link-checker 실행 대상

이 PR이 트리거 경로(`.github/workflows/link-checker.yml`)를 포함하므로, PR CI에서 link-checker가 실행된다. 4개의 Wiki URL이 모두 활성이면 GREEN, 하나라도 404/500이면 FAIL이다. Phase 1–7이 모두 main에 머지된 상태이므로 Wiki 페이지가 존재하여 통과할 것으로 예상한다.

---

## 사후 운영 안내

머지 후 다음 작업이 필요하다 (operator 수동).

1. GitHub 브랜치 보호 규칙에서 `Link Checker / link-check` 체크를 **required** 로 설정한다.
2. 향후 Wiki 페이지 이름이 변경될 경우 `context.yaml`의 `detail_ko` 값과 root docs의 `> Detail:` 링크를 동시에 업데이트해야 link-checker를 통과할 수 있다.

---

## HITL 에스컬레이션

없음.