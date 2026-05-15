# Phase 7 — Agent Description Rewrites + `Refs:` Footer Convention Migration

- **NN:** 27
- **Role:** Infra_Operator
- **Phase:** 7 / 8
- **Issue:** [#58](https://github.com/corinB/live-class/issues/58)
- **Blocking external:** none
- **Depends on:** ["26"]

## Goal

Update the four sub-agent description files (`.claude/agents/maestro.md`, `.claude/agents/scrum-task-decomposer.md`, `.claude/agents/blueprint-executor-worker.md`, `.claude/agents/worker.md`) so every reference to `plan/before/`, `plan/after/`, or `reports/` points to the new `wiki-src/plan-before/`, `wiki-src/plan-after/`, `wiki-src/ko/reports/` paths. Flip the `Refs:` footer convention in `CONTRIBUTING.md` summary from `Refs: plan/before/NN_<Role>_<Slug>.md` to `Refs: wiki-src/plan-before/NN_<Role>_<Slug>.md`. After this phase merges, all future agent dispatches use Wiki-aware paths.

## Inputs

- `.claude/agents/maestro.md`, `.claude/agents/scrum-task-decomposer.md`, `.claude/agents/blueprint-executor-worker.md`, `.claude/agents/worker.md`.
- `CONTRIBUTING.md` (English summary post-Phase 5).
- Migrated `wiki-src/plan-before/`, `wiki-src/plan-after/`, `wiki-src/ko/reports/` directories.
- Existing `Refs:` footer convention text in CONTRIBUTING and CLAUDE.md.

## Steps

1. For each of the four agent description files, find and replace path references:
   - `plan/before/` → `wiki-src/plan-before/`
   - `plan/after/` → `wiki-src/plan-after/`
   - `reports/` → `wiki-src/ko/reports/` (only for prose references; preserve `reports/surrogate-blocks.log` runtime path if mentioned).
   - `manifest.json` references — keep filename, update path prefix to `wiki-src/plan-before/manifest.json`.
2. Verify nothing else in those four files is touched — diffs should be path-only.
3. `CONTRIBUTING.md` — flip the `Refs:` footer example/spec to `Refs: wiki-src/plan-before/NN_<Role>_<Slug>.md`. Do not edit `CLAUDE.md` beyond what Phase 5 already mandated.
4. Audit one more time with `grep -rn "plan/before\|plan/after" .claude/agents/ CONTRIBUTING.md` — expect zero hits except the historical reference noting the migration date (operator may add one acknowledgement line).
5. Open one PR — title `chore(agents): rewrite paths to wiki-src and flip Refs footer convention`. Body in Korean. Footer `Refs: wiki-src/plan-before/27_Infra_Operator_Agent_Description_Rewrites.md` — this PR is the **first** to use the new footer convention (because by the time this PR runs, NN 24 has already moved this very file into `wiki-src/plan-before/`).
6. After merge, run `gh issue view 58` and post a comment confirming the convention flip with the merged PR URL.

## Out of scope

- Editing `CLAUDE.md` (only the AGENTS-SKILLS-HARNESS index line was allowed in Phase 5).
- Editing `ARCHITECTURE.md`, `DOCS.md`, `ORCHESTRATION.md` (Phase 5 owns those summaries).
- Editing `.claude/hooks/**`, `.claude/settings*.json` (Phase 6).
- Editing `.github/workflows/**` (Phase 1 / 8).
- Editing `live-class/`, `front/`, `docs/`, `reports/`, `wiki-src/**` file contents.
- Renaming any files (only path strings inside agent descriptions and CONTRIBUTING).

## Acceptance

- `grep -rn "plan/before/\|plan/after/" .claude/agents/` returns empty (or only an explicit migration-acknowledgement comment line).
- `grep -n "Refs: plan/before" CONTRIBUTING.md` returns empty.
- `grep -n "Refs: wiki-src/plan-before" CONTRIBUTING.md` returns at least one match (the new convention example).
- All four agent description files have non-empty diffs limited to path strings.
- A subsequent Maestro dispatch (manual trigger by operator) emits a task file with the new `Refs:` footer.

## References

- Issue #58 — Deliverable §6, Phase 7.
- Refs: wiki-src/plan-before/27_Infra_Operator_Agent_Description_Rewrites.md
