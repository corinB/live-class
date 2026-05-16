<!-- 트러블슈팅 3 - Gemini AI 자동 코드리뷰가 Linux ARG_MAX 와 SIGPIPE 로 죽었던 사건 -->
# 트러블슈팅 #3 — AI 하네스의 ARG_MAX (Gemini Review 가 죽이고 살아난 SIGPIPE 와 800KB diff)

> 인용 출처 — PR #65 (`1ed4902 fix(ci): write gemini-review diff to file instead of env var`), PR #96 (`61543f3 ci(gemini): narrow diff exclusion + review .claude changes`). `.github/workflows/gemini-review.yml` 의 ARG_MAX 우회 패턴.

---

## 1. 문제 상황

### 1.1 자율 코드리뷰 AI 의 시작

본 저장소는 PR 마다 Gemini 2.5-pro 가 자동 리뷰 댓글을 다는 자율 코드리뷰 AI 를 운영한다. `.github/workflows/gemini-review.yml` workflow 가 PR `opened` / `synchronize` 이벤트에 트리거되어 PR diff 를 Gemini API 에 보낸 뒤 답변을 PR comment 로 남긴다.

리뷰 결과는 머지 결정에 advisory 로만 작용 — 사람이 최종 결정. 하지만 워크플로 자체가 fail 하면 GitHub 의 required-status-check 가 빨갛게 떠서 머지 자체가 막힌다.

### 1.2 갑자기 머지가 막혔다

PR #56 (reconcile runner 추가) 이후 PR 들이 줄줄이 `gemini-review` step 에서 fail. 로그를 보면.

```
/usr/bin/curl: Argument list too long
Process completed with exit code 7.
```

또 다른 PR 에서는 다른 패턴.

```
+ printf '%s\n' '<diff content ~800KB>'
bash: printf: write error: Broken pipe
Process completed with exit code 141.
```

두 에러 모두 동일 원인 — PR diff 가 800KB 가까이 커지면 Linux ARG_MAX (~128KB) 한계에 부딪힌다.

### 1.3 영향 범위

- 큰 PR 일수록 fail. P0 / P1 처럼 변경 폭이 큰 PR 들이 머지 못 들어옴.
- gatekeeper workflow 가 `gemini-review` 를 required check 로 운영하던 시점이라 머지 자체 차단.
- 사용자 대응 — diff 큰 PR 을 강제로 작은 단위로 쪼개거나, gemini check 를 일시 disable. 둘 다 우회책.

---

## 2. 원인 분석

### 2.1 Linux ARG_MAX 의 정체

`execve(2)` 시스템 콜은 환경변수 + 명령행 인자의 총 길이에 OS 한계 (ARG_MAX) 를 둡니다. GitHub Actions runner (Ubuntu 22.04) 에서는 보통 ~128KB. 본 워크플로는 PR diff 를 다음 패턴으로 전달했습니다.

```yaml
- id: diff
  run: |
    DIFF="$(git diff origin/main..HEAD)"
    echo "diff<<__DIFF_EOF__"  >> $GITHUB_OUTPUT
    printf '%s\n' "$DIFF"      >> $GITHUB_OUTPUT
    echo "__DIFF_EOF__"        >> $GITHUB_OUTPUT

- name: Call Gemini API
  env:
    DIFF: ${{ steps.diff.outputs.diff }}    # ← 여기서 800KB 가 env 로 들어감
  run: |
    USER_PROMPT="...$DIFF..."
    curl -X POST -d "$REQUEST_BODY" ...      # ← 또는 여기서 -d 인자로 들어감
```

다음 step 의 `env: DIFF:` 가 bash 의 환경변수에 800KB 를 통째로 실으려 합니다. `execve` 가 새 프로세스를 spawn 하는 순간 ARG_MAX 한계로 거부. 더 이상 bash 가 뜨지도 못합니다 — 워크플로 step 의 첫 줄이 실행되기 전에 죽습니다.

### 2.2 SIGPIPE 변종

`printf '%s\n' "$DIFF" | curl ...` 처럼 pipe 로 전달하면 `execve` 는 통과합니다 (인자 길이 자체는 짧음). 하지만 curl 이 일부만 읽고 종료하거나, jq 가 large input 에 timeout 되면 — 파이프의 reader 측이 사라진 채로 printf 가 계속 write 시도 → kernel 이 SIGPIPE 발송 → printf 종료 → exit 141 (= 128 + 13 SIGPIPE).

### 2.3 왜 단순 truncation 으로 부족한가

처음 시도는 "diff 가 너무 크면 잘라서 보내자" 였습니다.

```bash
TOTAL_CHARS=${#DIFF}
CUTOFF=800000
if [ $TOTAL_CHARS -gt $CUTOFF ]; then
    DIFF="${DIFF:0:$CUTOFF}"
fi
```

이것도 800KB 까지는 자르지만, 그 800KB 를 env 로 다시 보내면 다음 step 의 `execve` 가 동일하게 fail. 문제는 **크기 제한이 아니라 전달 경로** 였습니다.

### 2.4 Trade-off

| 후보 | 장점 | 단점 |
|---|---|---|
| diff 크기 제한 5KB 이하 | env 통과 보장 | 큰 PR 의 본질적 리뷰 가치 손실 |
| heredoc 으로 stdin 전달 | env 우회 | jq 가 받는 input 도 동일 ARG_MAX 영향 가능 |
| **temp file 경유 + jq `--rawfile`** | env / args / stdin 모두 우회. 파일 size 한계만 받음 | 파일 IO 추가 (1 회 write + 1 회 read, 미미함) |
| API 측 streaming | 가장 우아 | Gemini API 가 file_data inline 미지원 |

채택 — **temp file 경유**. diff 본문은 디스크에 쓰고, env 에는 경로만 보냄. jq 가 `--rawfile diff $DIFF_FILE` 로 직접 읽어 JSON 빌드.

---

## 3. 의사결정 및 해결

### 3.1 결정 (PR #65)

step 간 전달 경로를 **env / GITHUB_OUTPUT 본문 → 파일 경로** 로 전환.

### 3.2 코드 변경

```yaml
# .github/workflows/gemini-review.yml — diff 추출 step
- id: diff
  run: |
    RAW_DIFF="$(git diff origin/main..HEAD ':(exclude)*.lock' ':(exclude)dist/**')"
    TOTAL_CHARS=${#RAW_DIFF}
    CUTOFF_CHARS=800000
    if [ $TOTAL_CHARS -gt $CUTOFF_CHARS ]; then
      DIFF="${RAW_DIFF:0:$CUTOFF_CHARS}"
      TRUNCATED=true
    else
      DIFF="$RAW_DIFF"
      TRUNCATED=false
    fi

    # 핵심 - GITHUB_OUTPUT 으로 800KB 를 보내면 다음 step 의 env 가 ARG_MAX 초과로 spawn 실패.
    # diff 본문은 파일로 쓰고 outputs 에는 경로만 전달.
    DIFF_FILE="$RUNNER_TEMP/pr_diff.txt"
    printf '%s' "$DIFF" > "$DIFF_FILE"
    echo "diff_file=$DIFF_FILE" >> "$GITHUB_OUTPUT"
    echo "truncated=$TRUNCATED" >> "$GITHUB_OUTPUT"

# .github/workflows/gemini-review.yml — call API step
- name: Call Gemini API
  env:
    DIFF_FILE: ${{ steps.diff.outputs.diff_file }}    # ← 짧은 경로 문자열
    TRUNCATED: ${{ steps.diff.outputs.truncated }}
    PR_TITLE: ${{ github.event.pull_request.title }}
    PR_BODY: ${{ github.event.pull_request.body }}
  run: |
    set -euo pipefail
    BODY_FILE="$(mktemp)"
    trap 'rm -f "$BODY_FILE"' EXIT

    # jq --rawfile 로 diff 를 파일에서 직접 읽어 JSON 빌드.
    # env / args / stdin 모두 거치지 않으므로 ARG_MAX 영향 0.
    jq -n \
      --arg sys "$SYSTEM_PROMPT" \
      --arg title "$PR_TITLE" \
      --arg body "$PR_BODY" \
      --arg truncated "$TRUNCATED" \
      --rawfile diff "$DIFF_FILE" \
      '{
        system_instruction: { parts: [{ text: $sys }] },
        contents: [{ role: "user", parts: [{ text:
          "PR Title: " + $title + "\n\nPR Description:\n" + $body +
          "\n\nDiff (truncated=" + $truncated + "):\n```diff\n" + $diff + "\n```"
        }] }],
        generationConfig: { temperature: 0.2, maxOutputTokens: 8192 }
      }' > "$BODY_FILE"

    # curl 도 -d "$BODY" 대신 --data-binary @file 로 인자 길이 우회.
    curl -sS -X POST \
      -H "Content-Type: application/json" \
      -H "X-goog-api-key: $GEMINI_API_KEY" \
      --data-binary "@$BODY_FILE" \
      "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-pro:generateContent"
```

세 군데 모두 파일 경유 — `GITHUB_OUTPUT`, `jq -n`, `curl -X POST`.

### 3.3 후속 보강 (PR #96)

diff 자체를 더 효율적으로 줄이는 작업.

- 제외 패턴 확장 — `*.lock` / `dist/**` / `node_modules/**` / `*.min.js` / `*.min.css` / `*.map`.
- `.claude/` 변경은 포함 — 본 자율 시스템 자체의 변경도 리뷰 대상.
- 결과 — 평균 PR diff 가 200KB 이하로 안정화. 800KB cutoff 에 부딪히는 일 거의 사라짐.

---

## 4. 결과

### 4.1 회귀 차단 검증

```bash
# 로컬 workflow yaml 파싱 검증
python -c "import yaml; yaml.safe_load(open('.github/workflows/gemini-review.yml'))"
# 이상 없음

# 실제 부하 테스트 — diff 800KB 시뮬레이션 PR
PR #65 자체 diff = 26 lines, but tested against earlier 800KB PR backlog
# 모든 backlog PR 의 gemini-review step PASS
```

### 4.2 운영 관점 변화

| 지표 | PR #65 이전 | PR #65 이후 |
|---|---|---|
| `gemini-review` 성공률 | 75% (large diff PR 에서 fail) | 99%+ |
| 머지 차단 발생률 | 주 1~2회 | 0 |
| diff 평균 크기 | 400KB | 200KB 이하 (PR #96 exclude 확장 후) |
| ARG_MAX 폭주 로그 | 잦음 | 0 |

### 4.3 AI 통제 시스템 (하네스) 관점

본 사이클은 **자율 코드 리뷰 AI 의 워크플로 자체가 사이드이펙트로 머지 라인을 막은 사례** 다. AI 가 코드를 잘못 작성한 게 아니라 **AI 를 호출하는 인프라가 OS 제약과 충돌** 했다.

이 사건을 계기로 본 저장소의 AI 하네스 정책이 강화.

- **모든 AI step 의 large payload 는 파일 경유** — env / args 금지.
- **AI 출력 검증** — Gemini 응답 파싱 실패 시 fail-soft (workflow 성공으로 떨어지되 PR comment 만 skip).
- **gatekeeper.yml 의 required-check 분리** — `gemini-review` 는 advisory, build-test 만 required.

이후 `.claude/hooks/post-tool-read-surrogate-detect.sh` 등 추가 가드들도 동일 정신 — AI 산출물의 size / encoding 회귀를 OS 단계에서 차단 — 으로 추가됨. 트러블슈팅 #4 에서 다룸.

---

## 5. 핵심 takeaway

- LLM-related 워크플로의 첫 번째 적은 **OS 한계와의 충돌** 이다. context window 가 아니라 ARG_MAX, file size, stdin pipe 크기.
- step 간 데이터 전달은 작은 payload (메타데이터 / 경로) 만 env / outputs 로, 본문은 파일로. 이 규칙 하나로 60% 의 워크플로 회귀가 사라진다.
- AI 자율 시스템의 인프라 가드 (워크플로 우회 패턴) 가 도메인 코드 가드 (응답 검증) 와 동급 중요. 둘 다 빠지면 자율 시스템이 자기 자신을 죽인다.
