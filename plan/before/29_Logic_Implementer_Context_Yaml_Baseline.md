# NN 29 — Phase A — Context.yaml Baseline

- **Role**: Logic_Implementer
- **Phase**: A
- **Issue**: #70
- **Blocking external**: none
- **Refs**: `plan/before/29_Logic_Implementer_Context_Yaml_Baseline.md`

## Goal
Establish a clean, accurate `context.yaml` baseline that all downstream Phase B–G tasks can build on. Update `metadata.last_indexed` to today, fix any `docs/**` references that point to paths that no longer exist (e.g., files migrated to `wiki-src/`), and add a one-shot `audit-context-yaml.py` helper used by Phase C's pre-tool hook. Without a green baseline here, every other phase will trip the new schema guards.

## Inputs
- `context.yaml` — current root index (top-level keys `project_identity`, `business_context`, `metadata`, `constraints_and_rules`, ...).
- `wiki-src/` tree — authoritative location for migrated docs (`wiki-src/ko/docs/**`).
- `docs/` tree — what remains on disk (may be empty after Issue #58 migration).
- `gh issue view 70` for the agreed inventory; specifically the line about `docs/engineering/context.md` belonging in `wiki-src` (this is owned by NN 38, not here).
- Today's date for `metadata.last_indexed`: `2026-05-16`.

## Steps
1. Read `context.yaml` end to end. Record every `docs/**` or `wiki-src/**` path it references in a scratch note.
2. For each referenced path, verify the file exists on disk (`git ls-files`). Build a list of broken references.
3. Fix broken references in place:
   - If the file moved to `wiki-src/ko/docs/...`, update the reference.
   - If the file is genuinely gone, remove the reference and leave a TODO comment naming the migrating Issue.
   - Do **not** invent new doc paths. If unclear, halt and post an Issue comment.
4. Update `metadata.last_indexed` to `2026-05-16`.
5. Create `.claude/scripts/audit-context-yaml.py` (NEW, this is data-tooling, not application code — allowed under `.claude/scripts/`). It must:
   - Load `context.yaml` with `PyYAML`.
   - Walk every string value matching `^(docs|wiki-src)/`.
   - For each, `os.path.exists(...)` from repo root.
   - Exit `0` if all references resolve; exit `1` and print the broken set otherwise.
   - Stay under 80 lines. No external deps beyond `PyYAML` (already a transitive dev dep).
   - First line: `# context.yaml ↔ disk 정합성을 검증하는 audit 스크립트`.
6. Run `python .claude/scripts/audit-context-yaml.py`. It must exit 0 before commit.
7. Commit: `chore(context): refresh baseline and add audit script` with footer `Refs: plan/before/29_Logic_Implementer_Context_Yaml_Baseline.md`.

## Out-of-scope
- Adding new domain entries to `context.yaml` (Phase D-related).
- Wiring the audit script into hooks or CI — that is NN 31 (Phase C) and NN 33 (Phase E).
- Migrating `docs/engineering/context.md` — that is NN 38 (Phase G-4).
- Editing `wiki-src/**` content. Audit only reads `wiki-src` paths to validate, never writes.

## Acceptance
- `metadata.last_indexed: '2026-05-16'` present in `context.yaml`.
- `python .claude/scripts/audit-context-yaml.py` exits `0`.
- No `docs/**` reference inside `context.yaml` points at a non-existent path.
- `git diff origin/main -- context.yaml` is small and review-friendly.
- New file `.claude/scripts/audit-context-yaml.py` is committed and executable.

## Refs
Refs: plan/before/29_Logic_Implementer_Context_Yaml_Baseline.md
