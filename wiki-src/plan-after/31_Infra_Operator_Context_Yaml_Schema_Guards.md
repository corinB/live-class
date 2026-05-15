# NN 31 — Phase C — Context.yaml Schema Guards

- **Role**: Infra_Operator
- **Phase**: C
- **Issue**: #70
- **Blocking external**: none (parallelizable with D/E after A)
- **Refs**: `plan/before/31_Infra_Operator_Context_Yaml_Schema_Guards.md`

## Goal
Install four cooperating Claude Code hooks that make `context.yaml` drift physically impossible to commit. The hooks must run at PreToolUse (Write/Edit), at pre-bash (`git commit`), and at SessionStart (informational). They use the `audit-context-yaml.py` script from NN 29 as the single source of truth. Escape hatch: `CONTEXT_GUARD_OFF=1` env var skips enforcement for emergencies.

## Inputs
- `.claude/hooks/` (existing directory; new files to be added here).
- `.claude/settings.json` (registry of hooks — must be edited to wire the new ones).
- `.claude/scripts/audit-context-yaml.py` (from NN 29, must exist on disk).
- `context.yaml` (target of guards).

## Steps
1. Create `.claude/hooks/pre-tool-context-yaml-path-guard.sh`:
   - PreToolUse hook (Write/Edit).
   - Reads tool input JSON from stdin; if `file_path` matches `^context\.yaml$`, fork-exec `python .claude/scripts/audit-context-yaml.py` against the *prospective* state by writing the new content to a temp file and patching `context.yaml` virtually.
   - Simpler MVP: just run audit against current disk after the operation in a PostToolUse counterpart (see step 3). For PreToolUse, ensure proposed content (passed via `tool_input.content` for Write or `tool_input.new_string` for Edit) does not introduce a string `docs/...` that doesn't exist. Exit 2 with message + JSON `{"decision":"block","reason":...}` on violation.
   - First line: `# context.yaml 변경 시 새로 추가된 docs/ 경로가 실재하는지 검사하는 PreToolUse 가드`.
   - Honors `CONTEXT_GUARD_OFF=1` (exit 0 with stderr warning).
2. Create `.claude/hooks/post-tool-context-yaml-audit.sh`:
   - PostToolUse hook.
   - When the just-edited file is `context.yaml`, run `python .claude/scripts/audit-context-yaml.py`. On non-zero, print warning (informational only — already past tool, no block).
3. Create `.claude/hooks/pre-bash-context-yaml-commit-guard.sh`:
   - PreToolUse hook for the Bash tool.
   - Parses the command; if the command is a `git commit` and `context.yaml` is among staged files (`git diff --cached --name-only`), runs `python .claude/scripts/audit-context-yaml.py`. On non-zero exit, blocks the commit with `{"decision":"block","reason":"context.yaml drift detected: <broken paths>"}`.
4. Create `.claude/hooks/session-start-context-yaml-status.sh`:
   - SessionStart hook.
   - Runs `python .claude/scripts/audit-context-yaml.py` quietly. On non-zero, prepends `[context-yaml] drift: N broken refs` to the session message (additionalContext).
5. Register all four hooks in `.claude/settings.json` under the appropriate event arrays. Match the matcher patterns already used by sibling hooks (e.g., `"Write|Edit"` for PreToolUse).
6. Smoke-test by:
   - Editing `context.yaml` to point at `docs/does-not-exist.md` — Edit must be blocked.
   - Setting `CONTEXT_GUARD_OFF=1` — Edit must succeed.
   - Reverting and committing — commit succeeds.
7. Write `reports/31_Infra_Operator_context-yaml-guards_2026-05-16.md` with hook list + smoke-test transcript.
8. Commit: `chore(harness): add context.yaml schema guard hooks` with footer `Refs: plan/before/31_Infra_Operator_Context_Yaml_Schema_Guards.md`.

## Out-of-scope
- Wiki pair sync (NN 32).
- Cron job (NN 33).
- Changing existing hooks unrelated to context.yaml.

## Acceptance
- Four new hook files exist under `.claude/hooks/` and are listed in `.claude/settings.json`.
- Attempting to Write/Edit `context.yaml` with a bad path → tool blocked.
- `git commit` with drifted `context.yaml` staged → bash tool blocked.
- `CONTEXT_GUARD_OFF=1` env var bypasses the guard with a stderr warning.
- SessionStart prepends drift count when drift exists; silent when clean.

## Refs
Refs: plan/before/31_Infra_Operator_Context_Yaml_Schema_Guards.md
