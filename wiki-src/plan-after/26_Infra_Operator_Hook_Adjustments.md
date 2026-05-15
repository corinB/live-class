# Phase 6 — Hook Adjustments (Badge Path, `wiki-src/**` Size Threshold, Cumulative-Size Guard)

- **NN:** 26
- **Role:** Infra_Operator
- **Phase:** 6 / 8
- **Issue:** [#58](https://github.com/corinB/live-class/issues/58)
- **Blocking external:** none
- **Depends on:** ["25"]

## Goal

Update the three `.claude/hooks/` concerns enumerated in Issue #58 Deliverable §5:

1. **Badge counter** (`SessionStart` / `UserPromptSubmit` hooks) — count `wiki-src/plan-before/` and `wiki-src/plan-after/` instead of the legacy `plan/before/` / `plan/after/` paths.
2. **Read-size guard** (`pre-tool-read-size-guard.sh`) — raise the per-file threshold for paths under `wiki-src/**` (Korean detail pages routinely exceed the standard 100 KB Read cap by design).
3. **Cumulative-size guard (NEW)** — register a Stop or UserPromptSubmit hook that inspects the active session jsonl size + message count; on threshold (`> 1.5 MB` OR `> 500 messages`) emit a stderr warning and append a structured line to `reports/surrogate-blocks.log`. Non-blocking — never abort the session.

## Inputs

- Existing `.claude/hooks/*.sh`, `.claude/settings.json` (hooks registration).
- Existing `.claude/hooks/pre-tool-read-size-guard.sh` (current threshold logic).
- Existing badge-counter hook (`SessionStart` / `UserPromptSubmit`) — locate via `grep -l plan/before .claude/hooks/`.
- Memory note `surrogate-split-avoidance` (operator's prior incident notes).
- Path for cumulative log: `reports/surrogate-blocks.log` — Phase 2 migrated `reports/*.md` but the directory itself remains writable for runtime artifacts.

## Steps

1. **Badge counter** — patch the hook(s) so the counter line `[pipeline] DOCS:✓/✗ · ARCH:✓/✗ · before:N · after:M` reads from `wiki-src/plan-before/*.md` and `wiki-src/plan-after/*.md`. Keep the same exit-code contract.
2. **Read-size guard** — in `pre-tool-read-size-guard.sh`, branch on path prefix: if `$path` starts with `wiki-src/`, raise the byte threshold to (recommended) 300 KB; otherwise keep 100 KB. Comment the rationale inline (one Korean line per CLAUDE.md rule 6 — operator-facing).
3. **Cumulative-size guard (new file)** — add `.claude/hooks/cumulative-size-guard.sh`:
   - Triggered by `Stop` (preferred) or `UserPromptSubmit` (fallback). Pick one — do not register both.
   - Reads the active session jsonl path from the hook input event (per Claude Code hook schema).
   - Computes `byteSize` and `messageCount` (line count) of the jsonl.
   - If `byteSize > 1.5 * 1024 * 1024` OR `messageCount > 500`, emit one stderr warning line and append one line to `reports/surrogate-blocks.log` with timestamp + byteSize + messageCount + sessionId.
   - Exit 0 always (non-blocking — Issue #58 explicit).
4. Register the new hook in `.claude/settings.json` under the appropriate `hooks` event. Do not touch unrelated entries.
5. Open one PR — title `chore(harness): adjust hooks for wiki-src paths and add cumulative-size guard`. Body in Korean. Footer `Refs: plan/before/26_Infra_Operator_Hook_Adjustments.md`.
6. Smoke-test by opening a fresh Claude Code session in the repo: badge line should show `before:N · after:M` where N/M match `wiki-src/plan-before` / `wiki-src/plan-after` counts. Manually grow a session past the cumulative threshold and confirm one stderr warning + one log line.

## Out of scope

- Editing `CLAUDE.md`, ARCH/DOCS/CONTRIB/ORCH summaries, `context.yaml`, `AGENTS-SKILLS-HARNESS.md`.
- Touching `.claude/agents/**` — Phase 7 (NN 27).
- Touching `.github/workflows/**` — Phase 1 (NN 20) and Phase 8 (NN 28) own that.
- Touching `live-class/`, `front/`, `docs/`, `plan/before/**`, `plan/after/**` content.
- Lowering or removing the existing per-tool-call 100 KB caps for non-`wiki-src/` paths (cumulative-size guard is additive, not a replacement).

## Acceptance

- New session shows badge `before:N · after:M` consistent with `wiki-src/plan-before/` and `wiki-src/plan-after/` counts.
- `pre-tool-read-size-guard.sh` allows a Read of a `wiki-src/ko/architecture-detail.md` larger than 100 KB and still blocks the same size under `live-class/`.
- `cumulative-size-guard.sh` exists, is registered in `.claude/settings.json`, exits 0 on every invocation.
- One synthetic over-threshold event produces one stderr warning and one line in `reports/surrogate-blocks.log`.
- `.claude/settings.json` diff is minimal (only the new hook entry).

## References

- Issue #58 — Deliverable §5, Phase 6.
- Memory `feedback_surrogate_split.md` — surrogate-split avoidance ops rule.
- Refs: plan/before/26_Infra_Operator_Hook_Adjustments.md
