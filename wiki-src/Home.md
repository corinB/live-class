# live-class Wiki

This Wiki is the mirror destination for the live-class documentation migration program
tracked in [Issue #58](https://github.com/corinB/live-class/issues/58).

## Source of truth

All Wiki content is authored under `wiki-src/` in the main repository
([corinB/live-class](https://github.com/corinB/live-class)).
Changes to `wiki-src/**` on the `main` branch are automatically mirrored here
by the `.github/workflows/wiki-sync.yml` workflow.

**Do not edit this Wiki directly** — edits will be overwritten on the next push
that touches `wiki-src/**`.

## Migration phases

| Phase | Task | Description |
|-------|------|-------------|
| 1 | #20 | `wiki-src/` skeleton + sync workflow (this page) |
| 2 | #21 | Migrate `reports/*.md` |
| 3 | #22 | Migrate `docs/**/*.md` |
| 4 | #23–24 | Migrate `plan/after/` and `plan/before/` |
| 5 | #25 | English summaries for root docs |
| 6 | #26 | Hook adjustments |
| 7 | #27 | Agent description rewrites |
| 8 | #28 | Link-checker CI |

## Language layout

- `wiki-src/` — English root pages (this file, index pages)
- `wiki-src/ko/` — Korean-language content (reports, docs, plan archives)
