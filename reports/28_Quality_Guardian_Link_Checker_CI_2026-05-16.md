# 28_Quality_Guardian_Link_Checker_CI 작업 완료 보고서

> 본 파일은 legacy pre-bash-block-plan-move-without-report.sh hook 대응을 위한 복사본이다.
> 원본: wiki-src/ko/reports/28_Quality_Guardian_Link_Checker_CI.md

- **작업 번호:** 28
- **역할:** Quality_Guardian
- **단계:** Phase 8 / 8 (Wiki 이전 프로그램 최종 단계)
- **이슈:** [#58](https://github.com/corinB/live-class/issues/58)
- **완료 일시:** 2026-05-16
- **PR 브랜치:** ci/task-28-link-checker

---

## 요약

Issue #58 Wiki 이전 프로그램의 마지막 단계인 Phase 8 작업을 완료하였다. PR마다 Wiki URL 활성 여부와 내부 cross-link 정합성을 자동 검사하는 GitHub Actions 워크플로우(`link-checker.yml`)와 설정 파일(`.lychee.toml`)을 추가하였다.

## 변경 내역

- `.github/workflows/link-checker.yml` 신규 생성: lycheeverse/lychee-action@v2.3.1 기반
- `.lychee.toml` 신규 생성: Wiki URL만 HTTP 검사, 외부 URL 제외
- 트리거 경로 8개: context.yaml, CLAUDE.md, ARCHITECTURE.md, DOCS.md, CONTRIBUTING.md, ORCHESTRATION.md, wiki-src/**, .github/workflows/link-checker.yml
- `permissions: contents: read`, GITHUB_TOKEN only, timeout-minutes: 5

## 스코프 미손 확인

wiki-src/** 콘텐츠, root docs, .claude/**, 기존 워크플로우 미수정.