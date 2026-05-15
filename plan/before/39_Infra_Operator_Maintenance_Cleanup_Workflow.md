# NN 39 — Phase G-5 — Maintenance Cleanup Workflow

- **Role**: Infra_Operator
- **Phase**: G-5
- **Issue**: #70
- **Blocking external**: NN 36, 37 merged (workflow shells out to those scripts)
- **Refs**: `plan/before/39_Infra_Operator_Maintenance_Cleanup_Workflow.md`

## Goal
Wrap the two cleanup scripts (NN 36, 37) into a single GitHub Actions workflow that runs on three triggers: weekly cron (Sunday 00:00 UTC), every successful CI run on `main` (`workflow_run`), and on issue label events for `maestro:auto`. The workflow always runs the scripts in `--dry-run` mode and posts the diff as a sticky comment on the most recent open Issue with `chore` label; only manual `workflow_dispatch` with `execute=true` runs them for real.

## Inputs
- `.claude/scripts/cleanup-worktrees.sh` (NN 36).
- `.claude/scripts/cleanup-untracked-leftovers.sh` (NN 37).
- `gh` CLI inside Actions.

## Steps
1. Create `.github/workflows/maintenance-cleanup.yml`:
   - File header comment line: `# 머지된 worktree/untracked 잔재를 주기적으로 점검하고 dry-run 결과를 코멘트하는 워크플로우`.
   - Triggers:
     ```yaml
     on:
       schedule:
         - cron: '0 0 * * 0'
       workflow_run:
         workflows: ['CI']
         types: [completed]
         branches: [main]
       issues:
         types: [labeled]
       workflow_dispatch:
         inputs:
           execute:
             description: 'Set to true to actually delete'
             required: false
             default: 'false'
     ```
   - Job guard: when `github.event_name == 'issues'`, run only if `github.event.label.name == 'maestro:auto'`. When `workflow_run`, run only if `github.event.workflow_run.conclusion == 'success'`.
   - Single job `cleanup`:
     - Checkout with `fetch-depth: 0`.
     - Set up `gh` and shell.
     - Run `.claude/scripts/cleanup-worktrees.sh` (default dry-run; pass `--execute` only if `inputs.execute == 'true'` AND event is `workflow_dispatch`).
     - Run `.claude/scripts/cleanup-untracked-leftovers.sh` similarly.
     - Capture outputs to `${RUNNER_TEMP}/cleanup-report.md`.
     - Find the most recent open issue with label `chore` (`gh issue list --label chore --state open --json number --limit 1 -q '.[0].number'`). If found, sticky-comment the report (use a unique marker so subsequent runs edit the same comment instead of stacking). If none, open a new issue titled `[maintenance] cleanup report <date>` with the report as body and labels `chore,maestro:auto`.
   - Permissions: `issues: write`, `contents: read`, `actions: read`.
2. Run `gh workflow run maintenance-cleanup.yml -f execute=false` once to smoke-test. Verify a comment appears on the right issue.
3. Save the run URL and the resulting comment URL into `reports/39_Infra_Operator_maintenance-cleanup_2026-05-16.md`.
4. Commit: `chore(ci): add maintenance cleanup workflow` with footer `Refs: plan/before/39_Infra_Operator_Maintenance_Cleanup_Workflow.md`.

## Out-of-scope
- Actually deleting in CI without explicit `execute=true` workflow_dispatch.
- Doing anything other than calling the two scripts.
- Adding new triggers beyond the three above.

## Acceptance
- `.github/workflows/maintenance-cleanup.yml` exists.
- `gh workflow list` shows it.
- Manual dispatch with `execute=false` produces a comment on an existing chore issue (or a new one).
- The label trigger with `maestro:auto` invokes the workflow once.
- `workflow_run` trigger fires after a successful CI run on main (verify by waiting one CI cycle).
- Within 1 week of merge, at least one cleanup report comment exists on a chore issue.

## Refs
Refs: plan/before/39_Infra_Operator_Maintenance_Cleanup_Workflow.md
