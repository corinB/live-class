<!-- task-13 종료 보고서 — README + Mermaid ERD + 11 DTO @Schema + 부수 hotfix 정리 -->
---
status: done
owner: Infra Operator
created: 2026-05-16
updated: 2026-05-16
---

# Report: README + Mermaid ERD + Swagger Schemas (task 13)

## Input Summary

- 태스크 파일: `plan/before/13_Infra_Operator_README_ERD_And_Swagger_Schemas.md`
- 핸드오프: `gemini-shimmering-book.md §3` (10 섹션 + ERD + 11 DTO + §10 한계 4건)
- DOCS.md / ARCHITECTURE.md 요약 후 README §6·§7 에 링크.
- 11 DTO 위치 (확인): web/user(2) + web/clazz(4) + web/enrollment(5).

## What Was Done

| File | Action | Summary |
|------|--------|---------|
| `README.md` (repo root) | created | 전체 한국어 10 섹션 — 개요·스택·로컬 실행·API 문서·Mermaid ERD (users/classes/enrollments PK·FK·핵심 column·cardinality)·도메인 설계·동시성 설계·테스트 분류·엔드포인트 14개 한 줄 표·한계 4건 (각 한계+현재 동작+production 해결방향 2-3줄) |
| `live-class/.../web/user/dto/RegisterUserRequest.java` | modified | `@Schema(description+example+requiredMode)` 부착 |
| `live-class/.../web/user/dto/UserResponse.java` | modified | 동일 |
| `live-class/.../web/clazz/dto/ChangeStatusRequest.java` | modified | 동일 |
| `live-class/.../web/clazz/dto/ClassResponse.java` | modified | 13개 record component 모두 부착 |
| `live-class/.../web/clazz/dto/CreateClassRequest.java` | modified | 7개 component 부착 + 기존 Bean Validation 유지 |
| `live-class/.../web/clazz/dto/PagedClassResponse.java` | modified | 5개 paging component 부착 |
| `live-class/.../web/enrollment/dto/CreateEnrollmentRequest.java` | modified | 단일 component |
| `live-class/.../web/enrollment/dto/EnrollmentResponse.java` | modified | 7개 component + nullable=true (paidAt, cancelledAt) |
| `live-class/.../web/enrollment/dto/PagedEnrollmentResponse.java` | modified | paging 5종 |
| `live-class/.../web/enrollment/dto/PagedStudentResponse.java` | modified | paging 5종 |
| `live-class/.../web/enrollment/dto/StudentResponse.java` | modified | 4개 component |
| `live-class/.../web/error/GlobalExceptionHandler.java` | modified | (hotfix) `log.error("Unhandled ...", ex)` 추가 + `HttpMessageNotReadableException` 전용 핸들러 (400 + `MALFORMED_JSON`) |
| `live-class/.../resources/application.yaml` | modified | (hotfix) `server.servlet.encoding.charset=UTF-8 + force=true` |

PR: https://github.com/corinB/live-class/pull/98 (squash merged `e2d7ff4`, 2026-05-16T06:44:53Z).

## Rationale & Tradeoffs

- 선택: README 전체 한국어 (CLAUDE.md 의 사용자 텍스트 한국어 룰 + 국내 채용 제출용 진입점). 코드/커밋/내부 기술 자산은 영어로 분리.
- 선택: Mermaid `erDiagram` 인라인 — GitHub UI 자동 렌더링 + 따로 PNG 첨부 불필요.
- 선택: §10 한계 4건은 "한계 + 현재 동작 + production 해결방향 2-3줄" 형식. 평가자에게 의도된 trade-off 임을 명확히 전달.
- 선택: 각 DTO record component 에 `@Schema` 직접 부착 — Java record 의 annotation propagation 룰에 의해 field + accessor 모두 노출. 별도 `@Schema(implementation=...)` 클래스 wrapping 불필요.
- 부수 hotfix — 검증 중 한글 RequestBody 500 으로 떨어지는 케이스에서 `GlobalExceptionHandler` 가 stack trace 를 swallow 하던 사실을 발견. 운영 진단 영구 개선 (`log.error`) + JSON parse 실패를 500→400 으로 분리 (`MALFORMED_JSON` errorCode). 한글 500 의 실제 원인은 서버 외부 (Git Bash on Windows 의 cp949 인코딩) 이며 PostgreSQL UTF-8 저장은 정상 검증됨 (23 byte stored).
- 트레이드오프: Controller `@Operation`/`@ApiResponses` 보강과 Dockerfile multi-stage 검증은 별도 chore 로 이월. 이번 PR 은 schema layer 만 처리.

## Verification

- 로컬 `./gradlew compileJava` BUILD SUCCESSFUL.
- 로컬 bootRun + curl/PowerShell 8단계 E2E (CREATOR 등록 → CLASSMATE 등록 → DRAFT → OPEN → PENDING → CONFIRMED → CANCELLED → `PagedEnrollmentResponse`) 통과.
- 11/11 DTO `/v3/api-docs` schemas 노출 + 한국어 description + example 확인.
- 한글 title 검증은 PowerShell + UTF-8 byte 직접 인코딩에서 통과 (PostgreSQL 23 byte UTF-8 저장).
- CI 4 required green (Build & Test 1m23s, gemini-review 29s, Evaluate Merge Readiness, link-check).
- Gemini P0/P1 = 0. P2 3건 모두 이월 (mock 인증, malformed JSON 핸들러 통합 테스트, log redaction 정책).

## Follow-ups

- [ ] Controller 각 메서드에 `@Operation(summary, description) + @ApiResponses({@ApiResponse(...)})` 추가 — 다음 세션 chore PR.
- [ ] `live-class/Dockerfile` multi-stage build 검증 (final image <200MB) — 별도 chore.
- [ ] `handleMessageNotReadable` MockMvc 통합 테스트 (malformed JSON → 400 + `MALFORMED_JSON`) — 다음 세션 follow-up.
- [ ] mock 인증을 JWT/OAuth2 로 교체 — production 전환 prerequisite, 별도 큰 트랙.
- [ ] `log.error` 민감 정보 redaction 정책 — production 로깅 정책 트랙.
- [ ] README §10 한계 4건 → GitHub Issue 로 추적 구조화 — operator 추적성.
