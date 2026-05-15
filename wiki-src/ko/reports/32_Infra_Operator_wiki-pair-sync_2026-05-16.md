# 32_Infra_Operator_Wiki_Pair_Sync_Guards — 작업 종료 보고서

작성일: 2026-05-16
이슈: #70 Phase D

## 요약

루트 doc과 wiki-src/ko/*-detail.md 짝을 항상 동시에 변경하도록 강제하는 guard를 설치했다.

## 변경 파일

- `.claude/hooks/pre-write-wiki-pair-sync.sh` (신규)
- `.claude/hooks/pre-bash-block-commit-on-pair-skew.sh` (신규)
- `.claude/hooks/pre-bash-block-rm-pair-skew.sh` (신규)
- `.github/workflows/wiki-pair-sync.yml` (신규)
- `.claude/settings.json` (훅 3개 등록)

## 스모크 테스트

- 한쪽만 staged + git commit → deny (PASS)
- 양쪽 staged + git commit → pass (PASS)
- WIKI_PAIR_GUARD_OFF=1 + 한쪽만 staged → pass (PASS)
- YAML safe_load → 오류 없음 (PASS)

Refs: plan/before/32_Infra_Operator_Wiki_Pair_Sync_Guards.md