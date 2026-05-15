<!-- 본 워크스페이스에서 Context Engineering이 어떻게 적용되어 있는지와 다음 적용 패턴 -->
# Context Engineering

## 무엇인가

- 모델이 작업할 때 **무엇을 먼저 읽고**, **어떤 우선순위로**, **얼마나의 토큰**을 쓰게 할지 설계하는 분야.
- 좋은 컨텍스트는 풀텍스트 폭격이 아니라 인덱스·발췌·캐시 단계로 계층화된다.
- 핵심 지표: 동일 정보를 두 번 읽지 않게 만든다, 캐시 적중률을 높인다, 풀텍스트 의존 단계를 줄인다.

## 본 워크스페이스의 적용

### 인덱스 우선

- `context.yaml`이 단일 인덱스. `project_identity` · `business_context.domains`/`glossary` · `constraints_and_rules.strictly_prohibited`를 가장 먼저 스캔하라고 각 에이전트가 자신의 본문에 명시.
- 풀텍스트는 `metadata.related_docs`로 안내 — 필요한 항목만 점프.
- 본 시리즈(`docs/agents/*`, `docs/harness/*`)도 인덱스(`AGENTS-SKILLS-HARNESS.md`)와 세분 문서로 같은 패턴 답습.

### 라이프사이클 시점 컨텍스트

- `SessionStart` · `UserPromptSubmit` hook이 `[pipeline] DOCS:✓ · ARCH:✓ · before:N · after:M` 배지를 매 prompt 앞에 prepend.
- 모델이 어느 단계에 와 있는지 매번 한 줄로 확인 가능.
- 배지는 `_lib-pipeline-state.sh`의 한 함수가 생성 — 추가할 상태 파일이 생기면 함수 한 곳만 수정.

### 메모리 분리

- `~/.claude/projects/<id>/memory/`에 도구·언어 정책 같은 일반 룰을 `feedback_*` 파일로 저장.
- 외부 시스템 위치는 `reference_*`로.
- 이전 conversation 컨텍스트가 새 세션에서도 자동 로드.

### 워크스페이스 특화 사실

- `CLAUDE.md` — 일반 행동 규칙 + 본 프로젝트의 도메인·동시성·파이프라인 요약.
- `DOCS.md` · `ARCHITECTURE.md` — 도메인 모델과 동시성 결정의 절대 출처. 변경 시 Stop hook이 경고.
- 보조 인덱스가 본문 풀텍스트를 매번 다시 읽지 않도록 위계화.

## 적용 패턴

- **인덱스 → 발췌 → 풀텍스트 3단**. 처음엔 `context.yaml` 키 몇 개, 필요하면 `docs/harness/01-reference.md` 같은 카탈로그, 진짜 필요할 때만 풀 hook 코드.
- **상태는 배지로**. 카운트·플래그처럼 모델이 매번 알아야 할 메타는 SessionStart/UserPromptSubmit hook이 한 줄로 주입.
- **재사용 컨텍스트는 메모리로**. 같은 사용자·프로젝트에서 반복되는 룰은 conversation 안에서 다시 학습하지 않게 `feedback_*` 메모리로.
- **컨텍스트 크기 자체를 게이트**. 단일 Read가 200 KiB 넘으면 `pre-tool-read-size-guard.sh`가 deny — 사람이 모델을 신뢰하기 전에 하네스가 강제.

## 안티패턴 (본 워크스페이스에서 관측·기록된 것)

- **6개 에이전트에 같은 Project Context 블록 100% 복붙.** 약 1500+ 토큰이 6번 중복. 변경 시 6곳 sync 필요. (해결 backlog: `.claude/agent-memory/POLICY.md`로 단일 source.)
- **워커가 매 task마다 DOCS+ARCH 풀스캔 강제.** `blueprint-executor-worker.md`에 명시. 13개 task = 13번 reread. (해결 backlog: 분해 단계에서 task별 발췌 packet을 work-order 파일에 인라인.)
- **위임 시 메인 컨텍스트 자동 인계 X.** `[pipeline]` 배지가 메인에만 prepend되고 위임 에이전트엔 누락. (해결 backlog: 위임 prompt 헤더에 배지·직전 follow-up 카운트 박기.)
- **풀텍스트 의존 단계가 너무 깊음.** 인덱스 → 발췌 단계가 빠지고 풀텍스트로 바로 점프하는 에이전트 본문 다수.

## 본 세션에서 관측된 사고와 결정

- **2026-05-13 surrogate-split 사고.** 한국어 마크다운·이모지·메모리·배지 hook이 누적돼 약 670 KB 페이로드 경계에서 UTF-16 surrogate pair가 잘려 API 400. 컨텍스트 크기 자체를 게이트해야 한다는 결론 → `pre-tool-read-size-guard.sh` + `pre-bash-block-large-output.sh` 도입.
- **PR 본문·reports 한국어 정책.** 사람이 읽는 텍스트는 한국어, 빌드 산출물(커밋 메시지·PR 제목)은 영어. 컨텍스트 청중을 분리하는 결정.

## 관련 문서

- `context.yaml` — 본 워크스페이스의 인덱스 원본.
- `docs/harness/01-reference.md` — `[pipeline]` 배지 hook과 size guard의 차단 영역.
- `docs/harness/02-architecture.md` — surrogate-split 사고와 hook 도입의 사건-결정 매핑.
- `docs/engineering/prompt.md` · `agentic.md` · `harness.md` — 인접 디시플린 4축.