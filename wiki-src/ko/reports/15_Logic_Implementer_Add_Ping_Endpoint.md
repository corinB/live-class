# 15번 태스크 완료 보고서 — Add /api/ping endpoint

## 요약

`GET /api/ping` 스모크 엔드포인트를 구현했다. HTTP 200과 JSON 바디 `{"pong": true}`를 반환하며, `X-User-Id` 헤더 없이도 호출할 수 있다.

## 변경 내역

| 파일 | 변경 유형 | 설명 |
|------|-----------|------|
| `live-class/src/main/java/com/example/liveclass/web/ping/PingController.java` | 신규 | `GET /api/ping` 핸들러. `Pong(boolean pong)` record 반환. |
| `live-class/src/test/java/com/example/liveclass/web/ping/PingControllerTest.java` | 신규 | `MockMvc standaloneSetup` 기반 슬라이스 테스트. `X-User-Id` 헤더 없이 200, `application/json`, `{"pong": true}` 검증. |
| `live-class/src/main/java/com/example/liveclass/web/auth/MockUserFilter.java` | 수정 | `WHITELIST_PATTERNS`에 `/api/ping` 추가. |
| `plan/before/manifest.json` | 수정 | NN=15 태스크 항목 제거 (plan/after로 이동 완료). |
| `plan/after/15_Logic_Implementer_Add_Ping_Endpoint.md` | 이동 | plan/before → plan/after (체크리스트 전체 완료). |

## MockUserFilter 분석

`MockUserFilter`는 `WebMvcConfig`에서 `/api/*` URL 패턴으로만 등록된다. 따라서 `/api/ping`은 필터 적용 대상이다. 기존 `WHITELIST_PATTERNS`에는 swagger/actuator 경로만 있어 `/api/ping`에 헤더 없이 접근하면 401이 반환된다.

범위 내 수정(`web/auth/MockUserFilter.java`)으로 `/api/ping`을 화이트리스트에 추가했다. `config/` 또는 `infrastructure/` 수정 없이 해결했다.

## 테스트 결과

한글 경로(`바탕 화면`)로 인해 Gradle 테스트 워커의 classpath 파일에 인코딩 오류가 발생하여 Korean-path 워크트리에서 `ClassNotFoundException`이 발생하는 사전 환경 문제가 있었다. ASCII 경로(`C:\work\task-15-test`)에 임시 워크트리를 생성하여 테스트를 실행했다.

```
cd C:\work\task-15-test\live-class
.\gradlew test --rerun-tasks

BUILD SUCCESSFUL in 3m 23s
32개 테스트 클래스 전체 통과, 실패 0건
```

`PingControllerTest` 결과:
- 테스트: 1건 (`ping_returns200WithPongTrue_withoutAuthHeader`)
- 실패: 0건
- 소요 시간: 0.007s

## 참고

- 태스크 파일: `plan/after/15_Logic_Implementer_Add_Ping_Endpoint.md`
- 이슈: #47
- ASCII 테스트 워크트리(`C:\work\task-15-test`)는 PR 병합 후 정리 필요.
