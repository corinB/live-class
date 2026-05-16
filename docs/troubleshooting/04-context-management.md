<!-- 트러블슈팅 4 - 멀티 에이전트 컨텍스트 매니지먼트 - context.yaml drift 와 100KB surrogate-split 가드 -->
# 트러블슈팅 #4 — 컨텍스트 매니지먼트 (context.yaml drift 와 surrogate-split 100KB 가드)

> 인용 출처 — PR #22 (`5386055 hard-block surrogate-split recurrence`), PR #28 (`75f4183 re-apply 100KB surrogate caps with stdin-pipe fix`), PR #75 (`a4a73c4 add context.yaml drift guard hooks`), PR #109 (`cf93f0c graph schema v3 + sync-context-wiki assets`), PR #113 (`79ea92e sync PR #112 drift + readability pass`). 모두 멀티 에이전트 환경의 컨텍스트 일관성 가드.

---

## 1. 문제 상황

### 1.1 컨텍스트 매니지먼트 — 멀티 에이전트의 핵심 과제

본 저장소는 8 개 sub-agent (`ddd-domain-architect`, `concurrency-architect`, ..., `doc-maestro`) 가 코드와 문서를 분담한다. 각 agent 는 작업 시작 시점에 다음 진실의 원천을 참조.

- `DOCS.md` — 도메인 설계
- `ARCHITECTURE.md` — 동시성/캐싱/스케줄링
- `context.yaml` — 위 둘의 구조화된 인덱스 + 코드/API/도메인의 그래프

문제 — 시간이 지나면 코드는 변하는데 `context.yaml` 은 안 변하는 drift 가 누적. 두 번째 agent 가 stale `context.yaml` 을 신뢰하고 작업하면 결정이 어긋난다.

또 다른 차원 — agent 가 한국어 + 코드 + 영문 메타데이터를 섞어 출력하면서 **UTF-16 surrogate pair 가 100KB 경계에서 쪼개지는** 회귀가 반복. Read / Bash 의 출력이 truncate 될 때 surrogate 가 split 되면 문서가 깨진 글자로 저장되고, 다음 agent 가 그 깨진 문서를 진실로 받아들인다.

### 1.2 surrogate-split 사건

PR #20 즈음에 한 worker 가 100KB 가까운 시안을 stdout 으로 출력 → Read tool 의 truncate 가 surrogate pair 한가운데서 잘라냄 → 응답이 `\uD83D` (high surrogate) 로 끝나고 `\uDE00` (low surrogate) 가 누락된 채 저장 → 다음 세션이 깨진 문자열을 읽고 또 다른 시안을 만듦 → 누적적 손상.

흔적은 `reports/surrogate-blocks.log` 에 남는다.

```
2026-05-13T19:03Z BLOCKED Read /tmp/large_output.txt (size=104857 > cap=100000)
2026-05-15T01:29Z BLOCKED Read claudemd_review.txt (unpaired surrogate at offset 81923)
2026-05-16T04:14Z BLOCKED Bash output (cumulative 1.2MB exceeded, surrogate detected)
```

### 1.3 context.yaml drift 사건

`context.yaml` 의 `apis:` 섹션에 `POST /api/admin/reconcile/{classId}` 가 누락된 상태로 두 agent (`doc-worker`, `github-wiki-worker`) 가 API 문서를 작성. 둘 다 reconcile 엔드포인트를 빼먹은 상태로 PR 생성. README 와 wiki 가 동시에 어긋남.

또 다른 패턴 — `apis:` 의 caller 필드가 `Creator` 인데 실제 controller 의 `@PreAuthorize` 는 변경되어 다른 역할이 호출 가능. 이런 의미적 drift 는 lint 가 잡지 못함.

---

## 2. 원인 분석

### 2.1 멀티 에이전트 환경에서 컨텍스트는 일급 객체

전통적 단일 개발자 환경에서는 "코드가 진실, 문서는 보조" 가 통합니다. 하지만 멀티 에이전트 환경에서는 다릅니다. agent A 가 코드를 바꾸고 문서를 업데이트하지 않으면, agent B 는 stale 문서를 읽고 그것을 기준으로 작업합니다. agent 가 코드 자체를 새로 읽도록 강제할 수 있지만, 큰 코드베이스에서는 토큰 비용 + 응답 시간이 폭발합니다.

본 저장소는 그래서 `context.yaml` 을 **single source of truth 인덱스** 로 도입했습니다. agent 는 context.yaml 을 먼저 읽고, 필요한 부분만 풀 코드로 들어갑니다. 이 인덱스가 깨지면 시스템 전체가 흔들립니다.

### 2.2 surrogate-split 의 본질

UTF-16 surrogate pair 는 U+10000 이상의 글자 (이모지, 일부 한자) 를 2 word 로 표현합니다. 100KB 가 단순한 byte cap 이라면 그 경계가 surrogate 한가운데 떨어질 확률이 존재합니다. 일반 텍스트는 잘려도 의미 손실 정도지만, surrogate 가 쪼개지면 다음 character 가 완전히 깨진 sequence 가 됩니다 (U+FFFD replacement character 로 displayed, 일부 파서는 throw).

LLM agent 가 출력을 truncate 후 그것을 다시 입력으로 받으면, 깨진 surrogate 가 모델 입력에 들어가 응답 품질이 떨어집니다. 더 나쁜 케이스는 그 출력을 그대로 파일에 저장 → 다음 세션이 깨진 파일을 신뢰.

### 2.3 hook-level 가드와 lint 의 분담

| 가드 레이어 | 책임 | 한계 |
|---|---|---|
| pre-commit lint | YAML 문법, 링크 유효성 | 의미적 drift 못 잡음 |
| **PostToolUse hooks** | tool 출력 size / surrogate 검증, file write 시 context.yaml 일관성 | hook 자체가 깨지면 가드 없음 |
| daily cron audit | `audit-context-yaml.py` — context.yaml + 코드 / DOCS 의미 비교 | latency 24h |
| **PreToolUse hooks** | Read / Bash 의 입력 size 차단 | 100KB cap 안에서 동작하는 손상은 못 잡음 |
| **session-start hooks** | 세션 시작 시 drift summary 출력 | 사용자 인지에만 의존 |

본 trade-off — 단일 가드는 부족. **여러 레이어를 조합** 해야 drift 가 모두 catch.

---

## 3. 의사결정 및 해결

### 3.1 결정 — 다층 컨텍스트 가드

**Layer 1 — Read/Bash 의 size cap (PR #22 / #28)**

- `pre-tool-read-size-cap.sh` — Read tool 의 file 크기 100KB 초과 시 차단.
- `post-tool-read-surrogate-detect.sh` — Read 결과 80KB 이상이면 unpaired surrogate 검사 후 차단.
- `post-bash-output-size-guard.sh` — Bash 출력 100KB 초과 경고.
- escape hatch — `SURROGATE_GUARD_OFF=1` 환경변수로 일시 우회 (debug 용).

**Layer 2 — context.yaml drift 가드 (PR #75)**

```
.claude/hooks/
  ├─ pre-tool-context-yaml-path-guard.sh    # Write/Edit 시 path 검증
  ├─ post-tool-context-yaml-audit.sh        # Write/Edit 후 audit script 실행
  ├─ pre-bash-context-yaml-commit-guard.sh  # git commit 차단 (drift 있으면)
  └─ session-start-context-yaml-status.sh   # 세션 시작 시 drift summary
```

- `pre-tool-context-yaml-path-guard.sh` — `context.yaml` 경로를 Write 시 새 directory 가 추가되면 `context_map.directories` 등재 강제.
- `post-tool-context-yaml-audit.sh` — write 직후 `python .claude/scripts/audit-context-yaml.py` 실행. `exit 0 = no drift`, 그 외 stderr 로 drift 리스트.
- `pre-bash-context-yaml-commit-guard.sh` — `git commit` 명령 차단. audit 가 fail 이면 commit 자체 금지.
- `session-start-context-yaml-status.sh` — 세션 진입 시 `[pipeline] DOCS:O/X · ARCH:O/X · before:N · after:M · drift:Y/N` 한 줄 prefix.

escape hatch — `CONTEXT_GUARD_OFF=1` 로 우회 가능.

**Layer 3 — daily cron audit (PR #72)**

- `.github/workflows/context-drift-cron.yml` — daily 09:00 UTC.
- `audit-context-yaml.py` 가 fail 이면 자동 issue 생성 (`label: context-drift`).
- 누적 drift 가 가시화 — issue 가 쌓이면 즉시 사용자 인지.

**Layer 4 — `/sync-context-wiki` skill (PR #109)**

- 사용자 명시 호출 슬래시 스킬.
- 코드/DOCS/ARCHITECTURE → context.yaml + wiki-src/ko 단방향 동기화.
- codex × N 가 도메인별 drift 1차 스캔 → `context-yaml-maestro` 가 취합·플랜 → codex × N 적용 → `pipeline-guard` 체이닝.

### 3.2 Trade-off — 왜 단일 lint 로 끝내지 않았는가

| 후보 | 장점 | 단점 |
|---|---|---|
| pre-commit lint 만 | 단순 | drift 가 commit 직전에만 발견 → 작업 중간 stale 정보 사용 |
| daily cron 만 | low overhead | 24h latency. 동시 같은 영역 다른 agent 작업이면 충돌 |
| LLM-level "context.yaml 먼저 검증" 지시 | 비용 0 | LLM 이 잊을 수 있음 — soft enforcement |
| **다층 hook + audit script + slash skill** | 모든 시점 (write / commit / session / explicit sync) 에서 catch | 가드 운영 비용. 우회 hatch 필요 |

채택 — **다층 가드**. 각 레이어가 다른 latency / scope 를 cover.

---

## 4. 결과

### 4.1 수치 변화

| 지표 | 가드 도입 전 | 도입 후 |
|---|---|---|
| `context.yaml` drift 발생률 (주당) | 3~5건 | 0~1건 (즉시 발견) |
| surrogate-split 회귀 | 1~2건 / 사이클 | 0건 (4개 사이클 연속) |
| 깨진 문서 commit | 4건 (`reports/surrogate-blocks.log` 카운트) | 0건 (cap 단계에서 차단) |
| `/sync-context-wiki` 사용 빈도 | n/a | PR 머지마다 1회 (PR #113 부터) |

### 4.2 PR #113 의 실전 사례

PR #112 (`refactor(evolution): P1 trio + P0 enrollment listener`) 가 머지된 직후, 다음 변경 사항이 `context.yaml` 과 어긋남.

- `domain/enrollment` 의 description 이 변경 — AFTER_COMMIT listener 4개 hook 추가 사실 반영 필요.
- `apis:` 의 `caller:` 필드 일부 갱신.
- `concurrency_scenarios` 의 테스트 클래스명 변경.

`/sync-context-wiki` 호출 → context-yaml-maestro 가 3 도메인 모두 식별 → PR #113 으로 일괄 동기화. drift 가 머지된 채로 24h 이상 머무는 일이 없음.

### 4.3 가드 자체의 회귀 방지

가드도 회귀할 수 있어 가드의 테스트가 별도 존재.

```bash
# audit script unit tests (PR #84)
cd .claude/scripts
python -m pytest test_audit_context_yaml.py
# 14 passed in 0.42s
```

`surrogate-blocks.log` 는 누적 기록 — 가드가 차단할 때마다 append. 본 로그가 추가되지 않는 기간 = 가드가 자기 일을 잘 하고 있다는 시그널 (또는 가드가 회귀해 차단 못 하는 시그널 → 별도 검증 필요).

---

## 5. 핵심 takeaway

- 멀티 에이전트 환경에서 `context.yaml` 같은 인덱스는 코드와 동급의 진실. drift 자체를 회귀로 다루고 다층 가드로 방어한다.
- LLM agent 의 출력은 **size 와 encoding** 모두 검증 대상. byte 길이만 보면 안 되고 surrogate pair, 깨진 UTF-8 sequence 까지 hook 에서 catch.
- 가드의 escape hatch (`*_GUARD_OFF=1`) 는 필수. 가드가 우회 불가하면 debug 가 막혀 가드를 disable 하는 PR 이 머지되는 더 나쁜 결과를 낳는다. hatch 사용 자체를 PR 본문에 명시.
- 컨텍스트 매니지먼트는 **인프라 (hook + audit) + 사용자 도구 (slash skill) + CI cron** 의 삼각 체계. 어느 하나만으로는 부족.
