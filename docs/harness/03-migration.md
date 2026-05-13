<!-- 본 하네스를 다른 프로젝트로 이식하기 위한 체크리스트·환경 가정·알려진 함정 -->
# Harness Migration

본 문서는 이 워크스페이스의 하네스를 **다른 프로젝트로 가져갈 때** 따라야 할 체크리스트와 자주 만나는 함정을 정리한다. 워크스페이스 특화 결정은 `docs/harness/01-reference.md`·`02-architecture.md`에서 다루고, 본 문서는 일반화된 이식 절차에 집중한다.

## 환경 가정

| 항목 | 필수 여부 | 부재 시 동작 |
|---|---|---|
| Git Bash (Windows) 또는 bash (macOS/Linux) | 필수 | hook가 실행 불가, settings.json의 `command: "bash ..."` 호출 실패 |
| `jq` | 권장 | hook의 JSON 파싱 1차 경로. 없으면 node로 fallback |
| `node` | 권장 (jq 없을 때 필수) | hook JSON 파싱 fallback. 둘 다 없으면 fail-closed deny |
| `gh` CLI | 권장 | PR 생성·머지·상태 확인용. 부재 시 GitHub 웹 또는 curl로 대체 |
| `markdownlint-cli` | 선택 | `post-write-md-lint.sh`가 호출. 없으면 no-op |
| `gawk` | 선택 | `post-write-warn-bean-collision.sh`가 사용 가능성. 부재 시 bean 충돌 경고 비활성 |

- 환경 변수: `CLAUDE_PROJECT_DIR`은 Claude Code가 자동 주입. hook에서 `$CLAUDE_PROJECT_DIR`로 프로젝트 루트 참조.
- 워크트리 작업 시 `CLAUDE_WORKTREE_PATH` 환경변수를 메인 세션이 주입 — `pre-write-worktree-guard.sh`가 활용.

## 이식 체크리스트

### Step 1 — `.claude/` 복사

- `.claude/settings.json` · `.claude/hooks/*.sh` · `.claude/templates/*` 전체 복사.
- `.claude/agents/*.md` · `.claude/skills/*.md`는 도메인 다르면 재작성 또는 제거.
- `.claude/settings.local.json`은 `.gitignore`에 들어가 있는 개인용. 복사 X.

### Step 2 — 메모리 분리

- `~/.claude/projects/<old-project-id>/memory/`는 옛 프로젝트 종속 사실이 들어 있다. 새 프로젝트로 그대로 가져가지 않는다.
- 다음 두 종류만 선별 이관.
  - `feedback_*` 중 도구·언어 정책 같은 일반 룰 (예: `feedback_pr_body_korean.md`, `feedback_surrogate_split.md`).
  - `reference_*` 중 외부 시스템 위치 (Linear 프로젝트 ID, Grafana URL 등).
- 새 `MEMORY.md` 인덱스는 빈 상태에서 시작.

### Step 3 — `.claudeignore` 동기화

- 시크릿 패턴(`*.env*` · `*.key` · `*.pem` · `credentials*` · `secrets*`)은 프로젝트 무관 공통.
- 본 워크스페이스 특화 항목(예: `.claude/agent-memory/**`)은 새 프로젝트의 실제 디렉터리 구조에 맞춰 조정.

### Step 4 — 보조 문서 프로젝트화

- `CLAUDE.md` · `ORCHESTRATION.md` · `CONTRIBUTING.md` · `context.yaml` · `DOCS.md` · `ARCHITECTURE.md` 전부 재작성 필요.
- 본 시리즈(`docs/harness/*.md` · `docs/agents/*.md`)는 하네스 메타 문서라 일반화 부분만 가져가고 워크스페이스 특화 부분(파이프라인 배지 · plan/before-after · 한글 cwd 사건 등)은 제거.

### Step 5 — Hook 안 매칭 패턴 조정

| Hook | 조정 포인트 |
|---|---|
| `pre-bash-block-plan-move-*.sh` | `plan/before/NN_*.md` 경로 패턴이 새 프로젝트와 다르면 정규식 갱신 |
| `pre-bash-detect-korean-cwd.sh` | JVM 명령(`./gradlew` · `mvn` · `java -jar`) 외 다른 빌드 도구(Node · Cargo · Go)도 비-ASCII 경로 영향 받으면 패턴 추가 |
| `pre-bash-block-large-output.sh` | 새 프로젝트가 다른 대용량 출력 명령(`kubectl logs` · `pm2 logs` 등)을 자주 쓰면 카테고리 확장 |
| `_lib-pipeline-state.sh` | pipeline 배지에서 추적할 상태 파일·디렉터리가 다르면 함수 본문 갱신 |
| `stop-warn-design-changes.sh` | 추적할 설계 문서 파일명이 다르면(`DOCS.md` · `ARCHITECTURE.md` 외) grep 패턴 조정 |

### Step 6 — Allow/Deny 패턴 좁힘

- `settings.json`의 allow-list `Bash(./gradlew bootRun:*)` 같은 와일드카드는 args 인젝션 위험. 새 프로젝트의 실제 호출 패턴만 좁게 허용.
- deny-list는 시크릿 패턴(`.env` · `*.key`) 그대로 가져가되, 새 프로젝트의 추가 시크릿 위치도 보강.

### Step 7 — 회귀 케이스 작성

- 신규 매처 추가 시 `/c/tmp/hook-test-<name>.sh`에 DENY/ALLOW 케이스 12개 이상.
- 기본 골격은 `docs/harness/02-architecture.md` 회귀 테스트 패턴 섹션 참조.

## 알려진 함정

### 한글/non-ASCII parent path

- Spring Boot · JVM 일부 도구가 cwd의 non-ASCII 바이트에서 `ClassNotFoundException` 또는 클래스로더 오류 발생.
- 회피: ASCII 경로의 git worktree에서 JVM 명령 실행. 한글 경로는 read-only 작업만.
- `pre-bash-detect-korean-cwd.sh`가 JVM 호출 차단으로 강제.

### Surrogate-split API 400

- 누적 페이로드가 약 670 KB 근처에서 UTF-16 surrogate pair가 경계에서 잘려 Anthropic API JSON validator가 거부.
- 회피: 큰 Read는 `offset`·`limit`으로 잘라 읽기, `git log`·`docker logs`는 `-n N`·`--tail N`·`| head` 사용.
- 두 hook(`pre-tool-read-size-guard.sh` · `pre-bash-block-large-output.sh`)이 하네스 차원 강제.

### Windows 경로 정규화

- `pre-write-worktree-guard.sh`에서 `\\` vs `/` 혼용 시 prefix 매칭 실패 가능.
- `pwd -P` · `realpath`로 normalize 후 비교. trailing slash 보장 필요.

### GNU vs BSD 도구 차이

- `find -delete` · `xargs -r` · `awk` 3-인자 `match()`는 GNU 전용. macOS BSD에서 동작 다름.
- 본 워크스페이스 hook은 GNU 전제(Git Bash 포함). macOS 이식 시 `find ... -print0 | xargs -0`, `gawk` 명시적 사용 등 조정.

### `npx --yes` supply-chain

- `post-write-md-lint.sh`의 `npx --yes markdownlint-cli`는 패키지 탈취 시 임의 코드 실행 가능.
- 회피: `package.json`의 devDependencies에 markdownlint-cli 고정 + lockfile 사용. 캐시 부재 시 hook을 no-op.

### Hook이 자기 commit 메시지를 잡는 false-positive

- `pre-bash-block-large-output.sh`가 commit 메시지 본문의 패턴 예시(`git log` · `git status -uall` · `docker logs`)를 그대로 인용하면 `git commit` 호출 자체가 deny됨.
- 회피: commit 메시지에서 차단 패턴 예시를 산문으로 추상화 (예: "row cap 없는 history dump"). 본 워크스페이스 PR #22에서 실제 발생.

### Stop hook의 `git -C` 미사용

- `stop-warn-design-changes.sh`가 현재 hook CWD 기준으로 `git diff HEAD`를 본다. 워커가 worktree 안에서 동작할 때 메인 저장소의 변경을 놓치거나 잘못 잡을 수 있음.
- 회피: `CLAUDE_WORKTREE_PATH` 우선, fallback `CLAUDE_PROJECT_DIR`로 `git -C "$target"` 명시 (Backlog P2).

## Troubleshooting

### "PR mergeStateStatus가 계속 CONFLICTING"

- 원인: GitHub가 mergeability 재계산에 수 초~수십 초 걸린다. push 직후엔 stale 결과가 보일 수 있음.
- 대응: `gh pr view <N> --json mergeable,mergeStateStatus`를 5~10초 간격으로 폴링. `MERGEABLE / CLEAN`이 뜨면 종료.

### "Hook deny 안내가 안 보임"

- 원인: hook stderr 출력은 모델 컨텍스트로 들어가지 않을 수 있음. 사용자만 stderr 보고 hook 작성자는 모를 수 있음.
- 대응: 진단용 안내는 stdout의 `reason` 필드에도 짧게 담기. stderr는 `cat >&2 <<EOF ... EOF`로 풍부한 안내 제공.

### "한 hook은 통과인데 다른 hook이 deny"

- 동작: 같은 matcher에 등록된 여러 hook은 **순서대로 모두 평가**되며, 어느 하나라도 deny면 전체 deny.
- 대응: settings.json의 hook 순서를 deny 빈도 높은 것부터 두면 빠른 fail. 단 의존 hook(예: 공유 lib source)가 있으면 순서 조정 주의.

### "회귀 테스트 19/19인데 실제 호출에서 우회"

- 원인: 회귀 테스트의 payload encoding이 실제 Claude Code 주입 payload와 다를 수 있음.
- 대응: 실제 발생한 deny 사례의 payload를 그대로 (jq 한 줄) 추출해 케이스 추가. encoding 차이로 안 잡히면 hook 매처를 더 관용적으로.

## 백로그 (이식 관련)

| 우선순위 | 항목 |
|---|---|
| P1 | `_lib-payload-parse.sh` 같은 공유 파싱 헬퍼 추출 — 새 hook 작성 시 보일러플레이트 감소 |
| P1 | settings.json 템플릿 분리 — 본 워크스페이스 특화 entries와 일반 entries 시각적 구분 |
| P2 | 이식 자동화 스크립트 — 새 프로젝트 디렉터리에 `.claude/` · `.claudeignore`를 일괄 복사 + 매칭 패턴을 프로젝트별 값으로 치환 |
| P2 | PowerShell-only 환경 지원 — `.ps1` 변종 hook 작성. 현재는 Git Bash 가정만 |
| P3 | 회귀 테스트 케이스 공유 — `tests/hooks/` 디렉터리로 in-repo화, CI 통합 |

## 관련 문서

- `docs/harness/01-reference.md` — hook 카탈로그 (어떤 hook가 무엇을 차단하는가).
- `docs/harness/02-architecture.md` — 구조·라이프사이클·구현 패턴 (왜 이 모양인가).
- `docs/agents/agents.md` · `docs/agents/skills.md` — 에이전트·스킬 카탈로그.
