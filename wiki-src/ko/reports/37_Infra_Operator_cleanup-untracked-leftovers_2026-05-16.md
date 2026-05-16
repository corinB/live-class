# 37_Infra_Operator_cleanup-untracked-leftovers_2026-05-16

## 요약

`.claude/scripts/cleanup-untracked-leftovers.sh` 스크립트를 신규 생성했다. 알려진 untracked 잔재 경로 4개(`.clone/worktrees`, `reports/14_*.md`, `reports/20_*.md`, `reports/27_*.md`)를 안전하게 정리하는 dry-run 기본 도구다.

## 변경 내역

### `.claude/scripts/cleanup-untracked-leftovers.sh` (신규, 105줄)

- `--dry-run` (기본값), `--execute`, `--help` 플래그 지원.
- Hard-coded allowlist 4개 패턴.
  - `.clone/worktrees`
  - `reports/14_*.md`
  - `reports/20_*.md`
  - `reports/27_*.md`
- `docs/engineering/` 는 NN 38(G-4)가 `wiki-src/ko/docs/engineering/`으로 이전 예정이므로 allowlist에서 제외.
- Tracked path 보호: `git ls-files --error-unmatch` 로 확인 후 tracked이면 refuse.
- 절대 금지 prefix: `live-class/`, `wiki-src/`, `.clone/`, `.github/`.
- `--execute` 시 `reports/cleanup-log.md`에 TSV 형식으로 append 로깅.

## Smoke Test 결과

| # | 시나리오 | 결과 |
|---|---------|------|
| 1 | dry-run, 잔재 없음 | `.clone/worktrees`, `reports/14_*`, `reports/20_*` → `skip (not present)`. `reports/27_Infra_Operator_Agent_Description_Rewrites*` → TRACKED → refuse. 정상. |
| 2 | fake `reports/27_TEST.md` 생성 후 dry-run | untracked 파일 → `WOULD DELETE (mtime=..., size=...)`. 정상. |
| 3 | `--execute` on fake file | 파일 삭제 + `reports/cleanup-log.md`에 로그 기록. 정상. |
| 4 | `git add reports/27_TEST.md` 후 dry-run | TRACKED → `refuse (tracked by git)`. 안전 차단 정상 작동. |

## 테스트 절차

```bash
# 워크트리 루트에서 실행
cd /c/work/task37
bash .claude/scripts/cleanup-untracked-leftovers.sh          # dry-run
bash .claude/scripts/cleanup-untracked-leftovers.sh --execute # 실제 삭제
bash .claude/scripts/cleanup-untracked-leftovers.sh --help    # 도움말
```

## Scope 위반 없음

- `.claude/hooks/**`, `.claude/settings.json`, `.github/**`, `context.yaml`, `live-class/**`, `front/**`, `wiki-src/**`, `docs/engineering/**` 미수정.
- 신규 파일: `.claude/scripts/cleanup-untracked-leftovers.sh` (scope 내).
- 보고서: `reports/37_Infra_Operator_cleanup-untracked-leftovers_2026-05-16.md` (scope 내).

## 참고

- Issue: #70
- Task file: `plan/before/37_Infra_Operator_Cleanup_Untracked_Leftovers_Script.md`
- NN 38(G-4) 이후 `docs/engineering/` 패턴 추가 가능.
- NN 39가 이 스크립트를 워크플로우에 통합 예정.