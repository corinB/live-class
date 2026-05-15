# Phase 3 — Migrate `docs/**/*.md` to `wiki-src/ko/docs/` and Remove `docs/`

- **NN:** 22
- **Role:** Logic_Implementer
- **Phase:** 3 / 8
- **Issue:** [#58](https://github.com/corinB/live-class/issues/58)
- **Blocking external:** none
- **Depends on:** ["21"]

## Goal

Migrate every `docs/**/*.md` file (11 files per Issue inventory) to `wiki-src/ko/docs/` preserving sub-directory structure as slug prefixes, then delete the now-empty `docs/` directory. After this phase, the repo no longer carries verbose Korean architecture-companion docs in-tree.

## Inputs

- `docs/**` tree — enumerate via `git ls-files 'docs/**/*.md'`. Verify count is 11; if drift, comment on Issue #58 with the actual count and ask the operator before proceeding.
- `wiki-src/ko/` created in Phase 1.
- This phase is independent of Phase 2's reports, but sequenced after to keep PR review surface small.

## Action Items (Checklist)

- [x] List tracked `docs/**/*.md` files -- actual count: 10 (note: issue inventory said 11; no docs/engineering/ tracked file found).
- [x] `git mv` each file to `wiki-src/ko/docs/<subdir>/<name>.md` (nested directory structure preserved).
- [x] Remove `wiki-src/ko/.gitkeep` via `git rm`.
- [x] Verify `git ls-files 'docs/'` returns empty -- confirmed.
- [x] Verify all diffs are pure renames R100 -- confirmed (10/10 files at 100% similarity).
- [x] Write report `reports/22_Logic_Implementer_migrate-docs_2026-05-15.md`.
- [x] Move plan file to `plan/after/`.

## Out of scope

- Updating in-repo references from `CLAUDE.md`, `ARCHITECTURE.md`, etc. to the new Wiki URLs -- that is Phase 5 (English summaries) and Phase 8 (link-checker).
- Editing `CLAUDE.md`, ARCH/DOCS/CONTRIB/ORCH, `AGENTS-SKILLS-HARNESS.md`, `context.yaml`.
- Touching `.claude/hooks/**`, `.claude/agents/**`, `.claude/settings*.json`.
- Touching `live-class/`, `front/`, `reports/`, `.github/workflows/**`.
- Translating any content.

## Acceptance

- [x] `git ls-files 'docs/**/*.md'` returns empty on `main` after merge.
- [x] `git ls-files 'wiki-src/ko/docs/**/*.md' | wc -l` matches the count Phase 3 started with (10).
- [x] All moved file diffs are pure renames (no content change).
- [ ] Wiki sync run for this PR concludes `success` (verified post-merge).

## References

- Issue #58 -- Inventory `docs/**/*.md (11 files)`, Phase 3.
- Refs: plan/before/22_Logic_Implementer_Migrate_Docs.md
