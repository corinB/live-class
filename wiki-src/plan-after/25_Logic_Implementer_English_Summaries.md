# Phase 5 — English Summaries for ARCH / DOCS / CONTRIB / ORCH + Delete `AGENTS-SKILLS-HARNESS.md` + `context.yaml` v2

- **NN:** 25
- **Role:** Logic_Implementer
- **Phase:** 5 / 8
- **Issue:** [#58](https://github.com/corinB/live-class/issues/58)
- **Blocking external:** none
- **Depends on:** ["24"]

## Goal

Replace the four bloated root docs (`ARCHITECTURE.md` 37 KB, `DOCS.md` 15 KB, `CONTRIBUTING.md` 11.6 KB, `ORCHESTRATION.md`) with terse English summaries pointing to the Korean Wiki detail pages migrated in earlier phases. Delete `AGENTS-SKILLS-HARNESS.md` outright (covered by `CLAUDE.md` companion section + `context.yaml`). Extend `context.yaml` schema to v2 (per-doc `summary` + `detail_ko` + `audience`). Also delete `live-class/HELP.md` (Spring Initializr boilerplate). Remove the `AGENTS-SKILLS-HARNESS.md` line from `CLAUDE.md`'s companion docs index.

## Inputs

- Current `ARCHITECTURE.md`, `DOCS.md`, `CONTRIBUTING.md`, `ORCHESTRATION.md`, `AGENTS-SKILLS-HARNESS.md`, `CLAUDE.md`, `context.yaml`, `live-class/HELP.md`.
- Wiki pages created in Phases 2–4 (Korean detail destinations). For each summary, the matching Wiki URL pattern is `https://github.com/corinB/live-class/wiki/ko-<slug>` (verify actual rendered URL via `gh api repos/corinB/live-class/contents/wiki-src` or Wiki UI).
- Project rule: Korean Wiki = detail, English in-repo = summary.

## Steps

1. For each of `ARCHITECTURE.md`, `DOCS.md`, `ORCHESTRATION.md`:
   - Read the existing file, distil the **English** summary (one paragraph per major section, max ~150 lines total).
   - Overwrite the file with the summary + a `> Detail: <Wiki URL>` line at top + section anchors that match the Wiki page.
   - Migrate the original full-text Korean (or English-with-Korean-comments) content into the corresponding `wiki-src/ko/<slug>-detail.md` page if not already there from earlier phases; otherwise just link.
2. `CONTRIBUTING.md` — keep English (GitHub renders it on PRs) but trim to a summary; full detail moves to `wiki-src/ko/contributing-detail.md`. Do **not** change the `Refs:` footer convention in this PR — that flips in Phase 7 (NN 27) bundled with the agent rewrites.
3. `AGENTS-SKILLS-HARNESS.md` — `git rm AGENTS-SKILLS-HARNESS.md`. The role is fully covered by `CLAUDE.md`'s multi-agent section and `context.yaml`.
4. `live-class/HELP.md` — `git rm live-class/HELP.md` (Spring Initializr boilerplate, never referenced).
5. `CLAUDE.md` — remove the line listing `AGENTS-SKILLS-HARNESS.md` from the companion docs index. **Do not** modify other content of `CLAUDE.md` (it is explicitly out-of-scope per Issue inventory `CLAUDE.md = Keep in repo (full)` — except this single index-line removal which the Issue mandates).
6. `context.yaml` — extend schema to v2:
   - For each doc entry, add `summary: <repo path>`, `detail_ko: <Wiki URL>`, `audience: [ai-en, human-ko]`.
   - Bump a `schema_version: 2` top-level field if a version field exists; otherwise add one.
   - Do not change unrelated keys.
7. Open one PR — title `docs(repo): replace bloated docs with English summaries; delete AGENTS-SKILLS-HARNESS`. Body in Korean. Footer `Refs: plan/before/25_Logic_Implementer_English_Summaries.md` (Phase 7 flips this convention; this PR is the last one allowed to use the old footer alongside NN 24, and 25 lands after 24 per `deps`).
8. After merge, the link-checker CI from Phase 8 (NN 28) will catch any orphan cross-link; Phase 8 runs last so this PR can merge before that gate exists.

## Out of scope

- Translating any content to English/Korean beyond producing the summaries.
- Editing `CLAUDE.md` beyond removing the single `AGENTS-SKILLS-HARNESS.md` index line.
- Touching `.claude/hooks/**`, `.claude/agents/**`, `.claude/settings*.json`, `.github/workflows/**`.
- Touching `live-class/` source code (only `live-class/HELP.md` deletion).
- Touching `front/`, `reports/`, `docs/`, `plan/after/**`, `plan/before/**` content (those moved in earlier phases).
- Rewriting agent descriptions or `Refs:` footer convention — Phase 7.

## Acceptance

- `wc -l ARCHITECTURE.md DOCS.md CONTRIBUTING.md ORCHESTRATION.md` — each ≤ 200 lines.
- Each summary has a top-line `> Detail:` Wiki URL.
- `AGENTS-SKILLS-HARNESS.md` does not exist on `main`.
- `live-class/HELP.md` does not exist on `main`.
- `grep -n AGENTS-SKILLS-HARNESS CLAUDE.md` returns empty.
- `context.yaml` parses as valid YAML and contains `schema_version: 2` plus per-doc `summary` / `detail_ko` / `audience` fields.
- Wiki sync run for this PR concludes `success`.

## References

- Issue #58 — Inventory table, Deliverables §3–4, Phase 5.
- Refs: plan/before/25_Logic_Implementer_English_Summaries.md
