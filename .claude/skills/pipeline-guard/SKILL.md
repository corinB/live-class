---
name: pipeline-guard
description: 최근 변경(default 최근 commit/PR diff, 인자로 파일 리스트 전달 가능)이 5축 — hooks / GitHub Wiki / tests / GitHub Actions workflows / context.yaml — 에 영향을 주는지 검증한다. 영향 발견 시 EnterPlanMode 자동 호출 → 축별 반영 계획 plan 파일 작성 → ExitPlanMode → 사용자 승인 후 메인 세션이 직접 동기화. wiki·context.yaml 은 코드 정합 갱신(검증+동기화), hook·workflows·tests 는 변경 사항 반영(보강). 사용자 명시 호출 또는 /evolution·/business-card-production 의 자동 체이닝에서만 동작.
---

# /pipeline-guard

## When invoked

1. 입력 결정.
   - 인자 있음 (`/pipeline-guard <file1> <file2> ...`) → 그 파일 집합 기준.
   - 인자 없음 → `git diff origin/main..HEAD --name-only` + 최근 머지 PR diff (`gh pr view <last_merged> --json files`) 합집합 기준.
2. 5축 검증 (readonly).

   | 축 | 대상 | 검사 |
   |---|---|---|
   | hooks | `.claude/hooks/`, `.claude/settings.json` | 변경 코드가 hook regex / path 조건과 충돌하는지. hook 등록 변경 필요 여부 판단 |
   | GitHub Wiki | `wiki-src/**/*.md` | 참조 경로·코드 인용이 최신 코드와 일치하는지. broken ref 동기화 후보 |
   | tests | `live-class/src/test/`, `live-class/src/integrationTest/` | 변경 클래스·메서드 참조 테스트 식별 + invariant 보호 시나리오 점검 |
   | GitHub Actions workflows | `.github/workflows/*.yml` | step path filter / required check / cache key 정합 |
   | context.yaml | 저장소 루트 `context.yaml` | `python .claude/scripts/audit-context-yaml.py` 실행 + 새 패키지/엔티티 인벤토리 반영 여부 |

3. 영향 없음 분기.
   - 5축 모두 0 → 1줄 보고 `[pipeline-guard] 5축 영향 0건. 종료.` 후 종료.
4. 영향 있음 분기.
   - 축별 영향 표 + 우선순위 (Blocker / Major / Minor) 출력.
   - `EnterPlanMode` 자동 호출.
   - plan 파일에 축별 반영 계획 작성 — 대상 파일 / 변경 요지 / 검증 방법.
   - `ExitPlanMode` 로 사용자 승인 요청.
5. 승인 후 반영 (plan mode 종료 후 메인 세션).
   - wiki / context.yaml — 코드 정합 갱신(대체).
   - hooks / workflows / tests — 변경 사항 반영(보강).
   - 축별 semantic commit 또는 단일 commit 사용자 결정.
   - PR 생성·머지 — 사용자 게이트.

## Preconditions

- 저장소 루트가 git 저장소.
- `.claude/scripts/audit-context-yaml.py` 존재.

## Out of scope

- 사용자 승인 없는 자율 반영 금지.
- 다른 PR 의 변경 흡수 금지 — 현재 브랜치/지정 파일만.
- 새 PR 머지 결정 금지.