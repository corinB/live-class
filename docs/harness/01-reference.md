<!-- 12개 hook + 5개 라이프사이클 이벤트 + permissions 자기참조 카탈로그 -->
# Harness Reference

본 문서는 `.claude/settings.json` + `.claude/hooks/*.sh`로 구성된 하네스의 자기참조 카탈로그다. 어디서 무엇이 deny/allow되는지 빠르게 찾기 위한 용도. 동작 원리·구현 패턴은 `docs/harness/02-architecture.md`.

## 라이프사이클 이벤트

| Event | 호출 시점 | matcher 사용 |
|---|---|---|
| `SessionStart` | Claude Code 세션 시작 직후 1회 | 없음 |
| `UserPromptSubmit` | 사용자 메시지 제출 직후 | 없음 |
| `PreToolUse` | 도구 호출 직전 | 도구 이름(`Bash` · `Read` · `Write|Edit` 등) |
| `PostToolUse` | 도구 호출 직후 | 도구 이름 |
| `Stop` | 세션·turn 종료 직전 | 없음 |

- `PreToolUse`가 `permissionDecision: deny`를 반환하면 도구 호출 자체가 취소된다.
- `Stop`이 비-zero exit하면 세션 종료가 차단되어 다음 turn으로 이어진다 (Codex stop-review-gate가 이 메커니즘을 사용).

## 14개 hook 카탈로그

| 이름 | Event / matcher | 차단/감지 대상 |
|---|---|---|
| `session-start.sh` | `SessionStart` | (감지 X) — 매 세션 첫 줄에 pipeline 배지 prepend + `_prelude.md` 존재 검증(부재 시 세션 차단) |
| `user-prompt-submit.sh` | `UserPromptSubmit` | (감지 X) — 매 사용자 메시지 앞 pipeline 배지 prepend |
| `stop-warn-design-changes.sh` | `Stop` | 세션 중 `DOCS.md`/`ARCHITECTURE.md` 변경 시 경고 |
| `stop-warn-stale-followups.sh` | `Stop` | `reports/` 안 follow-up 항목이 stale일 때 경고 |
| `stop-warn-context-stale.sh` | `Stop` | `context.yaml`의 `last_indexed`·라인 수·`related_docs` 경로가 실제와 어긋날 때 stderr 경고 (warn-only) |
| `pre-bash-block-destructive.sh` | `PreToolUse:Bash` | `rm -rf` · `find -delete` · `git push --force` · `git reset --hard` · `git clean -f` · `Remove-Item -Recurse` 등 (shell re-entry 포함) |
| `pre-bash-block-plan-move-without-report.sh` | `PreToolUse:Bash` | `plan/before/NN_*.md → plan/after/` 이동 시 `reports/NN_*.md` 부재면 deny |
| `pre-bash-block-plan-move-with-unchecked.sh` | `PreToolUse:Bash` | 같은 이동에서 작업 체크리스트가 모두 `[x]` 아니면 deny |
| `pre-bash-detect-korean-cwd.sh` | `PreToolUse:Bash` | 한글/non-ASCII cwd + JVM 명령(`./gradlew`·`mvn`·`java -jar`) deny, 그 외 명령은 세션당 1회 warn |
| `pre-bash-block-large-output.sh` | `PreToolUse:Bash` | row cap 없는 `git log`, `git status -uall`/`--untracked-files=all`, `--tail` 없는 `docker (compose )?logs` deny (shell re-entry 포함) |
| `pre-tool-read-size-guard.sh` | `PreToolUse:Read` | file_path가 200 KiB 초과 + `offset`·`limit` 둘 다 부재 시 deny |
| `pre-write-worktree-guard.sh` | `PreToolUse:Write|Edit` | `CLAUDE_WORKTREE_PATH` 환경변수가 설정된 경우 그 경로 밖 쓰기 경고 |
| `post-write-md-lint.sh` | `PostToolUse:Write|Edit` | `.md` 파일 변경 시 `markdownlint-cli` 실행(없으면 no-op) |
| `post-write-warn-bean-collision.sh` | `PostToolUse:Write|Edit` | Spring `@Service`/`@Component` 빈 이름 충돌 가능성 감지 시 경고 |
| `post-write-context-stale.sh` | `PostToolUse:Write|Edit` | 추적 대상(DOCS·ARCH·README·`.claude/agents/*`·`.claude/skills/*`·`docs/**` 등) 편집 직후 audit 호출, drift 시 stderr 경고 |

공유 헬퍼.

- `_lib-pipeline-state.sh` — `pipeline_state_badge()` 함수 제공. `SessionStart`/`UserPromptSubmit` 두 hook이 source.

## Hook별 짧은 동작 노트

### `pre-bash-block-destructive.sh`
- 차단 시 stderr에 어떤 카테고리(`rm-rf` · `find-delete` · `push-force` · `reset-hard` · `clean-force` · `ps-recurse`)가 잡혔는지 출력.
- 정규식 union으로 short flag·long flag·동의어 모두 매칭. `(^|[[:space:];&|])` 앵커로 quoted string 내부는 통과.
- jq 필수, 부재 시 node fallback, 둘 다 없으면 fail-closed deny.

### `pre-bash-block-plan-move-{without-report,with-unchecked}.sh`
- 매처 union: `mv` · `git mv` · `Move-Item`. `bash -c "..."` · `cmd /c "..."` 등 shell 재진입 내부도 재검사.
- `plan/before/NN_*.md` 패턴에서 NN 두 자리 추출 → `reports/NN_*.md` 존재(전자) 또는 작업 명세 체크리스트 모두 `[x]`(후자) 검증.
- 위반 시 stderr 안내에 누락된 보고서 경로 패턴(`reports/NN_<agent-name>_<YYYY-MM-DD>.md`)과 템플릿 위치(`.claude/templates/report.md.template`)를 명시.

### `pre-bash-detect-korean-cwd.sh`
- `LC_ALL=C pwd -P` 결과 바이트에서 `[\x80-\xff]` 포함 여부로 한글/non-ASCII 판정.
- JVM 호출(`./gradlew` · `mvn` · `java -jar`)이면 deny — Spring Boot가 non-ASCII parent path에서 `ClassNotFoundException`을 일으키는 회귀를 막기 위함.
- 그 외 명령은 `/tmp/claude-korean-cwd-warned-${session_id}` sentinel로 세션당 1회만 warn.

### `pre-bash-block-large-output.sh`
- 세 패턴 카테고리 — `git-log` · `git-status-uall` · `docker-logs`.
- 각 카테고리는 row cap(`-n N` · `--max-count=N` · 음수 short flag · `| head` · `| tail`)이 있으면 통과.
- surrogate-split API 400을 유발하는 페이로드 폭증 경로를 좁게 차단. false-positive 0 관대도.

### `pre-tool-read-size-guard.sh`
- `wc -c < "$file_path"`로 byte 크기 측정. 임계값 204800 (200 KiB).
- `offset` 또는 `limit` 둘 중 하나라도 지정되면 호출자가 분할 읽기 의사를 표명한 것으로 보고 통과.
- 같은 surrogate-split 회피 목적 — 단일 Read가 누적 페이로드의 1/3 이하로 묶이도록 산정.

## Payload 예시

이벤트별 hook가 stdin으로 받는 JSON의 핵심 필드만 발췌.

`PreToolUse:Bash`.

```json
{
  "tool_name": "Bash",
  "tool_input": { "command": "git log -n 50" }
}
```

`PreToolUse:Read`.

```json
{
  "tool_name": "Read",
  "tool_input": { "file_path": "/abs/path/DOCS.md", "offset": 0, "limit": 200 }
}
```

`PreToolUse:Write|Edit`.

```json
{
  "tool_name": "Write",
  "tool_input": { "file_path": "/abs/path/src/Foo.java", "content": "..." }
}
```

`PostToolUse:Write|Edit`.

```json
{
  "tool_name": "Edit",
  "tool_input": { "file_path": "/abs/path/README.md", "old_string": "...", "new_string": "..." },
  "tool_response": { "success": true }
}
```

`SessionStart` · `UserPromptSubmit` · `Stop`.

- payload 본문은 사용하지 않음. 본 워크스페이스의 두 헬퍼 hook은 stdin을 `cat`으로 소비하고 무시한다.

## Deny 출력 형식

`PreToolUse` hook가 차단할 때의 표준 출력.

```
{"permissionDecision":"deny","reason":"<category>: <message>"}
```

- exit code 2와 함께 stdout으로 출력.
- `reason`은 stderr에 보조 안내(권장 수정 방법, 관련 문서)와 함께 별도 출력 가능.

## Permissions (settings.json)

### Allow (자동 통과)

| 영역 | 패턴 |
|---|---|
| 읽기 | `ls:*` · `git status:*` · `git diff:*` · `git log:*` · `cat:*` |
| 빌드/테스트 | `./gradlew test:*` · `./gradlew build:*` · `./gradlew bootRun:*` · `./gradlew clean:*` |
| 인프라 | `docker compose up:*` · `docker compose down:*` · `docker compose ps:*` · `docker compose logs:*` |

> `git log:*` · `docker compose logs:*`는 allow-list로 자동 허용되지만, `pre-bash-block-large-output.sh`가 row cap이나 `--tail` 없는 호출은 별도로 deny한다. allow가 deny보다 우선하지 않는다.

### Deny (자동 차단)

| 카테고리 | 패턴 |
|---|---|
| Read 시크릿 | `Read(.env)` · `Read(.env.*)` · `Read(*.key)` · `Read(*.pem)` · `Read(credentials*)` · `Read(secrets*)` |
| Bash 시크릿 출력 | `Bash(cat .env*)` · `Bash(cat *.key)` · `Bash(cat *.pem)` · `Bash(cat credentials*)` · `Bash(cat secrets*)` |
| Windows/PowerShell 시크릿 출력 | `Bash(type .env*)` · `Bash(Get-Content .env*)` · `Bash(more .env*)` · `Bash(head .env*)` · `Bash(tail .env*)` |

- `.claudeignore`와 함께 시크릿 노출 경로를 이중 방어.
- 위 외 명령은 매 호출마다 사용자 prompt로 확인.

## 관련 문서

- `docs/harness/02-architecture.md` — 라이프사이클 다이어그램, hook 입출력 계약, 구현 패턴, 설계 동기, 회귀 테스트 패턴.
- `docs/harness/03-migration.md` — 다른 프로젝트로 옮길 때의 체크리스트와 알려진 함정.
- `docs/agents/agents.md` · `docs/agents/skills.md` — 6 에이전트 + 6 스킬 카탈로그.
- `.claude/settings.json` · `.claude/hooks/<name>.sh` — 본 카탈로그의 실제 원본.
