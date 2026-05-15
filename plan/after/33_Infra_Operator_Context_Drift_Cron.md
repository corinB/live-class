# NN 33 — Phase E — Context Drift Cron

- **Role**: Infra_Operator
- **Phase**: E
- **Issue**: #70
- **Blocking external**: none (parallelizable with C/D after A)
- **Refs**: `plan/before/33_Infra_Operator_Context_Drift_Cron.md`

## Goal
Add a daily GitHub Actions workflow that runs the `audit-context-yaml.py` script against `main` and opens (or updates) a tracking Issue when drift appears. This catches drift that slips past local hooks (e.g., contributors with `CONTEXT_GUARD_OFF=1` set).

## Inputs
- `.claude/scripts/audit-context-yaml.py` from NN 29.
- `.github/workflows/` (new file).
- `gh` CLI within Actions runner.

## Steps
1. Create `.github/workflows/context-drift-cron.yml`:
   - Triggers: `schedule: cron: '0 0 * * *'` (daily 00:00 UTC) and `workflow_dispatch` for manual runs.
   - Single job `drift-check` on `ubuntu-latest`:
     - `actions/checkout@v4` with `fetch-depth: 1`.
     - Set up Python 3.12 + `pip install pyyaml`.
     - Run `python .claude/scripts/audit-context-yaml.py`. Capture stdout/exit.
     - On exit != 0: use `gh issue list --label context-drift --state open --json number -q '.[0].number'` to find existing tracking issue. If found, comment the new drift report onto it. Else create new issue titled `[context-drift] daily audit YYYY-MM-DD failed` with the audit output as body, labels `context-drift,chore,maestro:auto`.
     - On exit 0: if a tracking issue exists, post a "resolved" comment and close it.
   - Permissions: `issues: write`, `contents: read`.
   - Token: `${{ github.token }}`.
   - File header comment: `# context.yaml drift 를 매일 감시하고 Issue 로 리포트하는 cron 워크플로우`.
2. Add a `context-drift` label to the repo via `gh label create context-drift --color 'd73a4a' --description 'context.yaml drift tracker'` (skip if exists).
3. Run the workflow once manually via `gh workflow run context-drift-cron.yml` and verify behavior with both a clean and a dirty `context.yaml` (use a throwaway test branch — never push the dirty state to `main`).
4. Write `reports/33_Infra_Operator_context-drift-cron_2026-05-16.md` with the workflow YAML + dispatch run URLs.
5. Commit: `chore(ci): add daily context.yaml drift cron` with footer `Refs: plan/before/33_Infra_Operator_Context_Drift_Cron.md`.

## Out-of-scope
- Auto-fixing drift (only reports).
- Cleaning up other workflows (Phase G-5).
- Editing hooks (NN 31).

## Acceptance
- `.github/workflows/context-drift-cron.yml` exists and is listed by `gh workflow list`.
- Manual dispatch succeeds; with clean baseline, exits 0 and creates no issue.
- Forcing drift on a test branch produces exactly one tracking issue with label `context-drift`.
- After drift is resolved, the tracking issue auto-closes on the next run.
- Repo has `context-drift` label.

## Refs
Refs: plan/before/33_Infra_Operator_Context_Drift_Cron.md
