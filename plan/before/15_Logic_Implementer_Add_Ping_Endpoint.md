# [15] Add /api/ping endpoint to live-class

## Assignee
The Logic Implementer (or Generalist Worker — task is small enough either is acceptable).

## Refs
- Issue: #47
- Source: GitHub Issue (automation pipeline, label `maestro:auto`)

## Dependencies
None. Independent task. Can run on top of latest `main`.

## Scope (paths the worker may touch)
- `live-class/src/main/java/com/example/liveclass/web/` — new package or file allowed. Do not modify existing controllers.
- `live-class/src/test/java/com/example/liveclass/web/` — matching test path.
- The worker MUST NOT touch any of: `domain/`, `application/`, `infrastructure/`, `config/`, `DOCS.md`, `ARCHITECTURE.md`, build scripts, `docker-compose.yml`, `.github/workflows/`. Spring Boot component scan auto-registers the new controller; no config change is needed.

## Definition of Done
- `GET /api/ping` returns HTTP 200 with JSON body `{"pong": true}`.
- Endpoint is reachable without authentication (no `X-User-Id` header required). The existing `MockUserFilter` either ignores `/api/ping` or the endpoint is exempt from any auth requirement — verify by hitting it without a header in the test.
- Controller-level unit / slice test (`@WebMvcTest` or `MockMvc` standalone) asserts status 200 and body `{"pong": true}`.
- `./gradlew test` green inside `live-class/` with the new test included.

## Action Items (Checklist)

- [ ] Inspect `live-class/src/main/java/com/example/liveclass/web/` to confirm there is no existing `PingController` or `/api/ping` route. Pick a sensible location (e.g. a `web/ping/` subpackage, mirroring how the `/health` endpoint was added under `web/health/`).
- [ ] Create `PingController` with `@RestController` and a `GET /api/ping` mapping that returns a simple record / Map producing `{"pong": true}`. Add the one-line Korean header comment per parent CLAUDE.md rule 6 (e.g. `// 헬스/스모크용 ping 엔드포인트 컨트롤러`).
- [ ] Verify the `MockUserFilter` (from `plan/after/01_Infra_Operator_MockUserFilter_And_ExceptionHandler.md`) does not reject requests without `X-User-Id` on `/api/ping`. If it does, scope-compliant fix is to make `/api/ping` part of an existing public-path allowlist already maintained in the same `web/` package — do NOT edit `config/` or `infrastructure/`. If the only way to make the endpoint auth-free requires touching files outside scope, STOP and post an Issue comment instead of expanding scope.
- [ ] Create `PingControllerTest` under `live-class/src/test/java/com/example/liveclass/web/<chosen-package>/`. Use `@WebMvcTest(PingController.class)` (or a `MockMvc` standalone setup). Assert: HTTP 200, content type `application/json`, body matches `{"pong": true}`. Send the request WITHOUT an `X-User-Id` header to lock in the no-auth requirement.
- [ ] Run `cd live-class && ./gradlew test --tests '*PingControllerTest'` and confirm green.
- [ ] Run the full `./gradlew test` once to confirm no regression elsewhere.
- [ ] Open PR with the standard repo conventions: English Conventional Commits title (e.g. `feat(web): add GET /api/ping smoke endpoint`), Korean PR body, commit footer including `Refs: plan/before/15_Logic_Implementer_Add_Ping_Endpoint.md` and `Refs: #47`. Branch name MUST be `feature/task-15-add-ping-endpoint` per CONTRIBUTING.md.

## Notes for the worker
- This issue is the end-to-end automation pipeline smoke test. The goal is one successful Maestro → Worker → Gatekeeper auto-merge cycle. Keep the implementation minimal and idiomatic Spring Boot 4.
- Do NOT introduce Spring Boot Actuator. The /health analogue (task #17) already proved out the pattern; mirror it.
- Do NOT add integration tests against a running app, README updates, or any "while I'm here" changes. The Acceptance Criteria are exhaustive.
- After the PR opens, the main session will add label `automation:worker` so the Gatekeeper can act on it.
