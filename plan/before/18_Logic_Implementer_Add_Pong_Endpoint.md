# [18] Add /api/pong endpoint to live-class

## Assignee
The Logic Implementer (or Generalist Worker — task is small enough either is acceptable).

## Refs
- Issue: #51
- Source: GitHub Issue (automation pipeline, label `maestro:auto`)
- Precedent: `plan/after/15_Logic_Implementer_Add_Ping_Endpoint.md` — mirror its shape and minimalism. The current task is intentionally near-identical (swap `ping` ↔ `pong`, swap response body shape).

## Dependencies
None. Independent task. Can run on top of latest `main`. PR #50 (the Gatekeeper `workflow_run` trigger fix) is already merged on `main`.

## Scope (paths the worker may touch)
- `live-class/src/main/java/com/example/liveclass/web/` — new package or file allowed. Mirror the `web/ping/` layout from task #15 (e.g. create `web/pong/PongController.java`). Do not modify the existing `PingController` or any other controller.
- `live-class/src/test/java/com/example/liveclass/web/` — matching test path (e.g. `web/pong/PongControllerTest.java`).
- `live-class/src/main/java/com/example/liveclass/web/auth/MockUserFilter.java` — adding `/api/pong` to the existing whitelist is permitted, identical to the precedent set in PR #49 for `/api/ping`. Whitelist add only — do not refactor or restructure the filter.
- The worker MUST NOT touch any of: `domain/`, `application/`, `infrastructure/`, `config/`, `DOCS.md`, `ARCHITECTURE.md`, build scripts, `docker-compose.yml`, `.github/workflows/`. Spring Boot component scan auto-registers the new controller; no config change is needed.

## Definition of Done
- `GET /api/pong` returns HTTP 200 with JSON body `{"ping": true}`. Note: response key is `ping`, not `pong` (mirror of task #15 where path was `/api/ping` and body was `{"pong": true}`).
- Endpoint is reachable without authentication (no `X-User-Id` header required). `MockUserFilter` whitelist must include `/api/pong`. Verify by hitting it without a header in the test.
- Controller-level unit / slice test (`@WebMvcTest` or `MockMvc` standalone) asserts status 200, content type `application/json`, and body `{"ping": true}`.
- `./gradlew test` green inside `live-class/` with the new test included.

## Action Items (Checklist)

- [ ] Inspect `live-class/src/main/java/com/example/liveclass/web/ping/` to refresh on the canonical shape from task #15. Mirror it under `web/pong/`.
- [ ] Create `PongController` with `@RestController` and a `GET /api/pong` mapping that returns a simple record / Map producing `{"ping": true}`. Add the one-line Korean header comment per parent CLAUDE.md rule 6 (e.g. `// 자동화 파이프라인 스모크용 pong 엔드포인트 컨트롤러`).
- [ ] Open `live-class/src/main/java/com/example/liveclass/web/auth/MockUserFilter.java` and add `/api/pong` to the same public-path whitelist used by `/api/ping`. Whitelist add only — do not change the filter's logic, ordering, or any other path. If the whitelist mechanism cannot be located or has changed shape since PR #49, STOP and post an Issue comment instead of inventing a new mechanism.
- [ ] Create `PongControllerTest` under `live-class/src/test/java/com/example/liveclass/web/pong/`. Use `@WebMvcTest(PongController.class)` (or a `MockMvc` standalone setup). Assert: HTTP 200, content type `application/json`, body matches `{"ping": true}`. Send the request WITHOUT an `X-User-Id` header to lock in the no-auth requirement.
- [ ] Run `cd live-class && ./gradlew test --tests '*PongControllerTest'` and confirm green.
- [ ] Run the full `./gradlew test` once to confirm no regression elsewhere (including the existing `PingControllerTest`).
- [ ] Open PR with the standard repo conventions: English Conventional Commits title (e.g. `feat(web): add GET /api/pong smoke endpoint`), Korean PR body, commit footer including `Refs: plan/before/18_Logic_Implementer_Add_Pong_Endpoint.md` and `Refs: #51`. Branch name MUST be `feature/task-18-add-pong-endpoint` per CONTRIBUTING.md.

## Notes for the worker
- This issue is the **second** end-to-end automation pipeline smoke run. The first (Issue #47 / PR #49) proved Maestro + Worker work; this run verifies that PR #50's Gatekeeper trigger fix (`workflow_run: workflows: [CI, Gemini AI Code Review]` replacing the dead `check_run/check_suite: completed` triggers) actually causes auto-merge to fire end-to-end without `needs-human` ever appearing.
- Keep the implementation minimal and idiomatic Spring Boot 4. The only intentional difference from task #15 is the path (`pong` instead of `ping`) and the body key (`ping` instead of `pong`).
- Do NOT introduce Spring Boot Actuator. Do NOT add integration tests against a running app, README updates, or any "while I'm here" changes. The Acceptance Criteria are exhaustive.
- After the PR opens, the main session will add label `automation:worker` so the Gatekeeper can act on it.
