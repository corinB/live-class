# [17] Add /health endpoint to live-class

## Assignee
The Logic Implementer

## Refs
- Issue: #32
- Source: GitHub Issue (automation pipeline, label `maestro:auto`)

## Dependencies
None. Independent task. Can run on top of latest main.

## Scope (paths the worker may touch)
- `live-class/src/main/java/com/example/liveclass/web/health/` (new directory).
- `live-class/src/test/java/com/example/liveclass/web/health/` (tests).
- The worker MUST NOT touch `live-class/src/main/java/com/example/liveclass/config/` — Spring Boot component scan auto-registers the new controller.

## Definition of Done
- `GET /health` returns HTTP 200 with JSON body `{"status": "ok"}`.
- Controller-level unit test passes (status code + body assertion).
- A `MockMvc`-based slice test passes confirming the routing is wired through Spring's web layer.
- `./gradlew test` green inside `live-class/`.

## Action Items (Checklist)

- [x] Create package `com.example.liveclass.web.health`.
- [x] Create `HealthController` with `@RestController` and method `GET /health` returning a simple record / Map with `status: "ok"`. Add the one-line Korean header comment per parent CLAUDE.md rule 6.
- [x] Create `HealthControllerTest` — unit test using plain JUnit + `MockMvc` standalone setup or Spring slice (`@WebMvcTest`). Assert status 200 and body `{"status":"ok"}`.
- [x] Run `cd live-class && ./gradlew test --tests '*HealthControllerTest'` and confirm green.
- [x] Run the full `./gradlew test` once to confirm no regression elsewhere.
- [x] Open PR with the standard repo conventions (English Conventional Commits title, Korean PR body, `Refs: plan/before/17_Logic_Implementer_Add_Health_Endpoint.md` + `Refs: #32` in commit footer).

## Notes for the worker
- This is the first task produced by the automation pipeline as an E2E smoke test. Keep the implementation minimal and idiomatic Spring Boot 4.
- Do not introduce Spring Boot Actuator dependency. The point is to verify the pipeline end-to-end, not to ship Actuator.
- After the PR opens, the main session will manually add label `automation:worker` so the Gatekeeper can act on it.
