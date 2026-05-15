# Phase 4a — Migrate `plan/after/*.md` to `wiki-src/plan-after/`

- **NN:** 23
- **Role:** Logic_Implementer
- **Phase:** 4 (sub-task a) / 8
- **Issue:** [#58](https://github.com/corinB/live-class/issues/58)
- **Blocking external:** none
- **Depends on:** ["22"]

## Goal

Move all `plan/after/*.md` (post-execution worker reports) into `wiki-src/plan-after/` so they ride the Wiki sync. This sub-task is the safe half of Phase 4 — `plan/after/` is read by Maestro/Worker prompts but never by `live-class/` code, and no in-flight PR currently touches it. The dangerous half (`plan/before/`) is split into NN 24 with a PR #56 gate.

## Inputs

- `plan/after/*.md` tree — enumerate via `git ls-files 'plan/after/*.md'`.
- `wiki-src/plan-after/` slug convention: keep the original `NN_<Role>_<Slug>.md` filename so worker dispatch logic that already knows `NN` keeps working once agent descriptions are rewritten in Phase 7.

## Steps

1. List tracked files — `git ls-files 'plan/after/*.md'`. Record the count in the PR body.
2. For each `plan/after/<name>.md`, run `git mv plan/after/<name>.md wiki-src/plan-after/<name>.md`. Do not rewrite content.
3. Leave `plan/after/` directory in place if any non-`.md` files remain (none expected); otherwise it disappears naturally.
4. Do **not** edit `plan/before/`, agent descriptions, or hooks in this PR. Those are NN 24 / 27 / 26 respectively.
5. Open one PR — title `chore(wiki): migrate plan/after to wiki-src/plan-after`. Body in Korean. Footer `Refs: plan/before/23_Logic_Implementer_Migrate_Plan_After.md`.
6. After merge, run `gh pr view 56 --json state,mergedAt` and post the result as a comment on Issue #58 so the main session knows whether NN 24 may be dispatched.

## Out of scope

- Migrating `plan/before/*` — that is NN 24 with a hard PR #56 gate.
- Updating agent descriptions / `Refs:` footer convention — Phase 7 (NN 27).
- Updating hooks that read `plan/after/` path — Phase 6 (NN 26).
- Editing `CLAUDE.md`, ARCH/DOCS/CONTRIB/ORCH, `AGENTS-SKILLS-HARNESS.md`, `context.yaml`.
- Touching `.claude/hooks/**`, `.claude/agents/**`, `.claude/settings*.json`, `.github/workflows/**`.
- Touching `live-class/`, `front/`, `reports/`, `docs/`.

## Acceptance

- `git ls-files 'plan/after/*.md'` returns empty on `main` after merge.
- `git ls-files 'wiki-src/plan-after/*.md' | wc -l` matches the starting count.
- All moved file diffs are pure renames.
- Wiki sync run for this PR concludes `success`.

## References

- Issue #58 — Inventory `plan/after/*.md`, Phase 4.
- Refs: plan/before/23_Logic_Implementer_Migrate_Plan_After.md
