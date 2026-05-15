# 31_Infra_Operator Context Yaml Schema Guards — 작업 완료 보고서

**날짜**: 2026-05-16
**작업 번호**: NN 31 (Issue #70 Phase C)
**역할**: Infra_Operator
**브랜치**: chore/task-31-context-guards

---

## 요약

`context.yaml` drift를 물리적으로 차단하는 4개 Claude Code 훅을 신규 작성하고 `.claude/settings.json`에 등록했다.
이제 잘못된 경로가 포함된 `context.yaml` Write/Edit 요청, 또는 drift된 상태로 `git commit`하려는 시도가 자동으로 차단된다.

---

## 변경 내역

### 신규 파일 (4개)

| 파일 | 이벤트 | 역할 |
|------|--------|------|
| `.claude/hooks/pre-tool-context-yaml-path-guard.sh` | PreToolUse (Write\|Edit) | `context.yaml` 내 `related_docs[*].path` 값이 실제 파일로 존재하는지 사전 검증. 미존재 시 `permissionDecision:deny` 반환 |
| `.claude/hooks/post-tool-context-yaml-audit.sh` | PostToolUse (Write\|Edit) | `context.yaml` 수정 직후 `audit-context-yaml.py` 실행, drift 감지 시 stderr 경고 (informational) |
| `.claude/hooks/pre-bash-context-yaml-commit-guard.sh` | PreToolUse (Bash) | `git commit` 명령이고 `context.yaml`이 staged 상태일 때 audit 실행. exit 2이면 commit 차단 |
| `.claude/hooks/session-start-context-yaml-status.sh` | SessionStart | 세션 시작 시 audit 조용히 실행, drift 발견 시 `additionalContext` JSON으로 알림 |

### 수정 파일 (1개)

| 파일 | 변경 사항 |
|------|----------|
| `.claude/settings.json` | PreToolUse (Write\|Edit) +2, PreToolUse (Bash) +1, PostToolUse (Write\|Edit) +1, SessionStart +1 — 총 5 항목 추가 |

---

## settings.json 변경 요약

```
PreToolUse[Write|Edit] + pre-tool-context-yaml-path-guard.sh
PreToolUse[Bash]       + pre-bash-context-yaml-commit-guard.sh
PostToolUse[Write|Edit]+ post-tool-context-yaml-audit.sh
SessionStart           + session-start-context-yaml-status.sh
```

---

## 스모크 테스트 결과

### 테스트 1: 존재하지 않는 경로 → deny 기대

입력 페이로드.
```json
{"tool_name":"Write","tool_input":{"file_path":"context.yaml","content":"metadata:\n  related_docs:\n    - path: DOES_NOT_EXIST.md\n      description: test"}}
```

결과.
```
{"permissionDecision":"deny","reason":"context.yaml: path(s) not found on disk -- DOES_NOT_EXIST.md"}
exit: 2 (deny)
```
PASS.

### 테스트 2: CONTEXT_GUARD_OFF=1 → bypass 기대

동일 입력, `CONTEXT_GUARD_OFF=1` 환경변수 설정.

결과.
```
[hook:pre-tool-context-yaml-path-guard] CONTEXT_GUARD_OFF=1 -- guard bypassed
exit: 0 (pass)
```
PASS.

### 테스트 3: 유효한 경로 (DOCS.md) → pass 기대

입력 페이로드.
```json
{"tool_name":"Write","tool_input":{"file_path":"context.yaml","content":"metadata:\n  related_docs:\n    - path: DOCS.md\n      description: domain design"}}
```

결과.
```
exit: 0 (pass, 출력 없음)
```
PASS.

---

## 이스케이프 해치

모든 guard 훅은 `CONTEXT_GUARD_OFF=1` 환경변수로 우회 가능하다.
우회 시 stderr 경고를 출력한다.

---

## scope 위반 없음

- `.github/**`, `wiki-src/**` 문서 내용, 루트 docs, `live-class/**`, `front/**`, `plan/after/**` 일체 무변경.
- 변경 범위: `.claude/hooks/` (4 신규), `.claude/settings.json` (hooks 배열 수정), `wiki-src/plan-before/` (태스크 파일 복사), `wiki-src/plan-after/` (태스크 파일 이동), `wiki-src/ko/reports/` (보고서), `reports/` (보고서 사본).

---

## 참고

- Issue #70 Phase C
- Task file: `wiki-src/plan-before/31_Infra_Operator_Context_Yaml_Schema_Guards.md`
- Audit script: `.claude/scripts/audit-context-yaml.py` (변경 없음, exit 0/1/2 그대로 사용)