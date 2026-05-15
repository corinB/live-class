# Phase 2 — Migrate `reports/*.md` to `wiki-src/ko/reports/`

- **NN:** 21
- **Role:** Logic_Implementer
- **Phase:** 2 / 8
- **Issue:** [#58](https://github.com/corinB/live-class/issues/58)
- **Blocking external:** none
- **Depends on:** ["20"]

## Goal

Move all 22 `reports/*.md` operator-facing Korean reports into `wiki-src/ko/reports/` so the Wiki sync (Phase 1) publishes them, and delete the originals from the repo. This is the lowest-risk migration — reports are append-only operator artifacts, never read by `live-class/` code.

## Inputs

- Existing `reports/*.md` tree (22 files). Enumerate with `git ls-files 'reports/*.md'` so untracked drafts are not swept up.
- `wiki-src/ko/` directory created in Phase 1.
- Project convention `reports/*.md = Korean` (CLAUDE.md). Preserve content byte-for-byte; this phase only relocates.

## Steps

1. List tracked reports — `git ls-files 'reports/*.md'`. Confirm count matches the Issue inventory (22 files). If it does not, halt and comment on Issue #58 with the actual count rather than guessing.
2. For each file `reports/<name>.md`, `git mv reports/<name>.md wiki-src/ko/reports/<name>.md`. Slug stays `<name>.md`; do not rename to kebab-case in this phase (operators already grep by date prefix).
3. After all moves, `reports/` directory should be empty of tracked `.md` files. Leave any non-`.md` artifacts (e.g., logs, CSVs) untouched.
4. Do **not** edit content of any moved file. No header rewrites, no link fixes, no formatting normalisation.
5. Open one PR — title `chore(wiki): migrate reports to wiki-src/ko/reports`. Body in Korean. Footer `Refs: plan/before/21_Logic_Implementer_Migrate_Reports.md`.
6. After merge, verify each migrated file appears under `https://github.com/corinB/live-class/wiki/ko/reports/<name>` (Wiki renders the `/` in path as a slug separator — Phase 8 link-checker will validate cross-links systematically).

## Out of scope

- Translating any report content.
- Editing `CLAUDE.md`, ARCH/DOCS/CONTRIB/ORCH, `AGENTS-SKILLS-HARNESS.md`, `context.yaml`.
- Touching `.claude/hooks/**`, `.claude/agents/**`, `.claude/settings*.json`.
- Touching `live-class/`, `docs/`, `front/`, `plan/before/**`, `plan/after/**`.
- Updating cross-links from other documents to the new Wiki URLs (Phase 5 handles in-repo doc rewrites; Phase 8 link-checker enforces).

## Acceptance

- `git ls-files 'reports/*.md'` returns empty on `main` after merge.
- `git ls-files 'wiki-src/ko/reports/*.md' | wc -l` returns the same count Phase 2 started with.
- No diffs in moved file contents — `git log --follow --diff-filter=R` shows pure renames.
- Wiki sync run for this PR concludes `success`.

## References

- Issue #58 — Inventory `reports/*.md (22 files)`, Phase 2.
- CLAUDE.md — `reports/*.md = Korean` convention.
- Refs: plan/before/21_Logic_Implementer_Migrate_Reports.md
