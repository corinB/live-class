# NN 38 — Phase G-4 — Docs Engineering Backfill

- **Role**: Logic_Implementer
- **Phase**: G-4
- **Issue**: #70
- **Blocking external**: none (after NN 29; complements NN 22's earlier migration)
- **Refs**: `plan/before/38_Logic_Implementer_Docs_Engineering_Backfill.md`

## Goal
Finish the wiki migration that NN 22 left incomplete: move `docs/engineering/context.md` (and any sibling files added since) into `wiki-src/ko/docs/engineering/` as a pure rename. Update any internal cross-links accordingly. After this move, the entire `docs/` directory should be empty and removable (the empty-dir removal is a follow-on cleanup, handled by NN 37's allowlist).

## Inputs
- `docs/engineering/` tree (currently includes `context.md` and possibly newer files added 2026-05-15+).
- `wiki-src/ko/docs/engineering/` (create if missing).
- `context.yaml` (NN 29 will already have updated last_indexed; this task may add a new entry pointing to the migrated path).
- Cross-link references — grep the repo for `docs/engineering/` strings.

## Steps
1. Inventory `docs/engineering/`: `git ls-files docs/engineering/` and `find docs/engineering/ -type f` (untracked are also moved).
2. For each file, `git mv docs/engineering/<file> wiki-src/ko/docs/engineering/<file>` if tracked; `mkdir -p` + `mv` otherwise. Preserve filenames.
3. Grep for the old paths repo-wide (excluding `.git/`): `grep -r "docs/engineering/" --include='*.md' --include='*.yml' --include='*.yaml' --include='*.json'`. Update every hit to `wiki-src/ko/docs/engineering/`.
4. If any reference was inside `context.yaml`, update it (allowed because this is the "fix references" path, and the audit script will pass).
5. Run `python .claude/scripts/audit-context-yaml.py` — must exit 0.
6. Run `gh workflow run wiki-sync.yml` (after PR merge, but locally validate the YAML still parses).
7. Save the migration manifest into `reports/38_Logic_Implementer_docs-engineering-backfill_2026-05-16.md`.
8. Commit: `docs: migrate docs/engineering to wiki-src` with footer `Refs: plan/before/38_Logic_Implementer_Docs_Engineering_Backfill.md`.

## Out-of-scope
- Touching anything outside `docs/engineering/` and references to it.
- Deleting the now-empty `docs/` directory (handled by G-3 allowlist next run).
- Adding new content (pure rename only).

## Acceptance
- `git ls-files docs/engineering/` returns nothing.
- `wiki-src/ko/docs/engineering/` has the same file count as the source.
- `git log --follow` on a moved file shows the rename history.
- `python .claude/scripts/audit-context-yaml.py` exits 0.
- No remaining `docs/engineering/` string anywhere except in `reports/` historical artifacts.

## Refs
Refs: plan/before/38_Logic_Implementer_Docs_Engineering_Backfill.md