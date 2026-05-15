<!-- 6개 슬래시 스킬 카탈로그. 각 스킬은 정확히 한 에이전트를 래핑한다 -->
# Skills

본 워크스페이스의 슬래시 스킬은 파이프라인 단계를 한 글자로 진행하는 단축 명령이다. 각 스킬은 정확히 한 에이전트를 래핑하고, 호출 전 전제 검증과 호출 후 다음 단계 안내를 자동으로 묶어 처리한다. 정의 본체는 `.claude/skills/<name>.md`.

## 파이프라인 스킬

### `/design-domain`

- **언제 부르나**: 파이프라인의 첫 단계. 새 도메인 설계를 시작할 때.
- **인자**: 도메인 한 줄 요약. 예: `"라이브 강의 수강신청, 정원 + 결제 후 7일 취소"`.
- **전제**: 없음.
- **호출**: `Task` 도구를 `subagent_type: "ddd-domain-architect"`로 실행.
- **다음**: `/design-concurrency`.

### `/design-concurrency`

- **언제 부르나**: `DOCS.md`가 준비된 직후. 락 전략·캐싱·동시성 시나리오를 정해야 할 때.
- **인자**: 동시성 관심사. 예: `"마지막 자리 race condition + 대기열 자동 승격"`.
- **전제**: `DOCS.md`가 repo root에 존재. 부재 시 `Missing: DOCS.md; run /design-domain first.` 출력 후 STOP.
- **호출**: `subagent_type: "concurrency-architect"`.
- **다음**: `/decompose-tasks`.

### `/decompose-tasks`

- **언제 부르나**: 두 설계 문서가 끝났고 워커가 손댈 마이크로 태스크가 필요할 때.
- **인자**: 분해 트리거 자유 텍스트. 예: `"이제 태스크로 쪼개줘"`.
- **전제**: `DOCS.md` + `ARCHITECTURE.md` 둘 다 존재. 하나라도 없으면 `Missing: <list>; run /design-domain and /design-concurrency first.` 출력 후 STOP.
- **호출**: `subagent_type: "scrum-task-decomposer"`.
- **다음**: `/setup-infra` 와 `/setup-git-rules` 병렬 가능.

### `/setup-infra`

- **언제 부르나**: 컨테이너·CI/CD 환경을 만들 때. `ARCHITECTURE.md`의 인프라 결정(Redis 인증·영속성·DB 모드)을 실제 파일로 옮긴다.
- **인자**: 인프라 요청. 예: `"Postgres 16 + Redis 7 + GitHub Actions"`.
- **전제**: `ARCHITECTURE.md` 존재. 부재 시 `Missing: ARCHITECTURE.md; run /design-concurrency first.` 출력 후 STOP.
- **호출**: `subagent_type: "infra-cicd-operator"`.
- **다음**: `/exec-blueprint`.

### `/setup-git-rules`

- **언제 부르나**: 워커들이 병렬 PR을 쏟아내기 전에 충돌·추적성 규약을 잡을 때.
- **인자**: Git 정책 요청. 예: `"GitHub Flow + Conventional Commits"`.
- **전제**: `plan/before/`에 `.gitkeep` 외 `.md` 파일이 한 건 이상 존재. 부재 시 `Missing: plan/before/*.md; run /decompose-tasks first.` 출력 후 STOP.
- **호출**: `subagent_type: "git-master-conventions"`.
- **다음**: `/exec-blueprint`.

### `/exec-blueprint`

- **언제 부르나**: 분해된 단일 태스크를 코드로 옮길 때.
- **인자**: 작업 명세 파일 경로. 예: `"plan/before/01_class-entity.md"`.
- **전제**: `DOCS.md` + `ARCHITECTURE.md` + 지정 태스크 파일 모두 존재.
- **호출 절차**:
  - 인자 파일명에서 `NN_` 접두와 `.md` 접미 제거 → slug 생성 (예: `01_class-entity.md` → `class-entity`).
  - Worktree 경로 도출: `../worktrees/feature-<slug>`.
  - `Task` 도구를 `subagent_type: "blueprint-executor-worker"` + `isolation: "worktree"` + 해당 경로로 호출.
- **워커 종료 시 산출**:
  - 워크트리 안에 `src/main/java/**` · `src/test/java/**` 코드.
  - 작업 명세의 체크리스트를 모두 `[x]`로 완료.
  - `reports/<NN>_<agent-name>_<YYYY-MM-DD>.md` 보고서 작성 (`.claude/templates/report.md.template` 기반, Input Summary / What Was Done / Rationale & Tradeoffs / Follow-ups 4개 섹션).
- **plan 이동**: 워커가 **같은 PR 안에서** `git mv plan/before/NN_*.md plan/after/NN_*.md`를 실행한다. PreToolUse hook이 두 조건(`reports/NN_*.md` 존재 + 체크리스트 전부 `[x]`)을 검증해 위반 시 이동을 deny하므로 reports와 체크리스트가 먼저 완료돼야 한다. squash merge 시 code + report + plan 전이가 atomic하게 main으로 들어간다.
- **다음**: PR 머지 후 다음 태스크 파일로 `/exec-blueprint` 재호출.

## 호출 패턴 공통

| 단계 | 동작 |
|---|---|
| 1 | 전제 파일 검증 → 누락 시 `Missing: <list>; run /<prev-skill> first.` 출력하고 STOP |
| 2 | `Task` 도구로 정확히 한 에이전트 디스패치 (사용자 자유 텍스트를 description으로 전달) |
| 3 | 서브에이전트 반환 후 다음 단계 한 줄 안내 |

이 세 단계가 모든 스킬에 동일하게 적용된다. 사용자는 `/`만으로 파이프라인을 끝까지 진행할 수 있고, 단계를 건너뛰면 즉시 어떤 스킬을 먼저 호출해야 하는지 알 수 있다.

## 유틸 스킬

위 여섯은 파이프라인 단계 스킬이다. 별개로 글로벌 또는 본 워크스페이스에 등록된 유틸 스킬이 공존한다.

- **`/ask-and-delegate`** — 큰 작업 시작 전 카테고리별 N개 질문 + 위임 프롬프트 초안 + 승인 게이트. 본 시리즈 문서 자체도 이 스킬로 라운드 1~5를 거쳐 결정됐다.
- **`/update-config`** — `settings.json` hook · permissions · env 수정.
- **Codex 계열** — `codex:setup`(토글·설치 확인), `codex:rescue`(막힌 시점 위임), `codex:gpt-5-4-prompting`(프롬프트 보조). 자세한 사용 맥락은 `docs/agents/agents.md`의 "외부 위임 — Codex" 섹션.
- **기타** — `/init` · `/review` · `/security-review` · `/loop` · `/schedule` · `/simplify` · `/fewer-permission-prompts` 등은 Anthropic 공식 또는 플러그인 제공 유틸.

유틸 스킬은 파이프라인 단계를 진행시키지 않는다. 메인 세션의 작업 보조용이다.

## 관련 문서

- `docs/agents/agents.md` — 여섯 에이전트 + 외부 위임 카탈로그.
- `AGENTS-SKILLS-HARNESS.md` — 1페이지 허브.
- `.claude/skills/<name>.md` — 각 스킬의 풀 정의.
