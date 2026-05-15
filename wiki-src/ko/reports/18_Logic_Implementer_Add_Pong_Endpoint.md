# [18] Add /api/pong smoke endpoint — 실행 보고서

## 브랜치 및 커밋

- 브랜치: `feature/task-18-add-pong-endpoint`
- 커밋 SHA: `61c9c5a`
- 메시지: `feat(web): add GET /api/pong smoke endpoint`

## 변경 내역

| 파일 | 변경 유형 | 설명 |
|------|----------|------|
| `live-class/src/main/java/com/example/liveclass/web/pong/PongController.java` | 신규 생성 | `GET /api/pong` 엔드포인트. `{"ping": true}` 반환 |
| `live-class/src/test/java/com/example/liveclass/web/pong/PongControllerTest.java` | 신규 생성 | MockMvc standaloneSetup 슬라이스 테스트. 인증 헤더 없이 200 + `{"ping":true}` 확인 |
| `live-class/src/main/java/com/example/liveclass/web/auth/MockUserFilter.java` | 수정 | `WHITELIST_PATTERNS`에 `"/api/pong"` 추가 (화이트리스트 항목 추가만, 로직·순서 변경 없음) |

## 테스트 결과

- 단독 테스트: `./gradlew test --tests "*PongControllerTest"` — BUILD SUCCESSFUL (17s)
- 전체 테스트: `./gradlew test` — BUILD SUCCESSFUL (3m 31s)
- 실행 환경: ASCII 경로 워크트리 (`C:\work\task-18-pong`) — 한글 경로에서 Gradle JVM 테스트 프로세스가 classpath를 로드하지 못하는 문제를 우회. 코드 변경은 원본 워크트리(`agent-a8c27a75101b04038`)에서 이루어지고 테스트만 ASCII 경로에서 실행.

## MockUserFilter 화이트리스트 변경 확인

```java
// 변경 전
"/api/ping"

// 변경 후
"/api/ping",
"/api/pong"
```

PR #49의 `/api/ping` 추가 선례와 동일한 패턴.

## 워크트리 정보

- 코드 편집 워크트리: `C:\Users\qorwh\OneDrive\바탕 화면\p\.claude\worktrees\agent-a8c27a75101b04038`
- 테스트 실행 워크트리(ASCII fallback): `C:\work\task-18-pong` — 정리 필요 시 `git worktree remove C:\work\task-18-pong`로 제거 가능.

## HITL 에스컬레이션

없음. 모든 체크리스트 항목 완료, 테스트 전체 통과.
