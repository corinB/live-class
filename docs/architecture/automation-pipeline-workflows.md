# Automation Pipeline — Workflow Composition Reference

> How the 5 GitHub Actions workflows compose into the Maestro + Worker end-to-end pipeline.
> Read `docs/architecture/automation-pipeline.md` for the state machine and policy decisions.
> This document focuses on **how to operate and extend** the workflows.

---

## End-to-end flow

```
User opens GitHub Issue
  └─ applies label: maestro:auto
       │
       ▼
maestro-dispatch.yml                      [trigger: issues.opened / labeled]
  ├─ kill-switch check (AUTOMATION_ENABLED)
  ├─ Claude Code SDK → maestro.md subagent
  │     reads: DOCS.md, ARCHITECTURE.md, automation-pipeline.md
  │     emits: plan/before/NN_*.md  +  plan/before/manifest.json
  ├─ commit + push → chore/maestro-<issue-number>
  └─ repository_dispatch: worker-task  (payload: manifest JSON)
       │
       ▼
worker-dispatch.yml                       [trigger: repository_dispatch worker-task]
  ├─ kill-switch check
  ├─ matrix: one job per task in manifest.tasks
  └─ per worker job:
       ├─ git worktree (isolated)
       ├─ Claude Code SDK → worker.md subagent
       │     reads: TASK_FILE, DOCS.md, ARCHITECTURE.md
       │     writes: code inside worktree
       │     runs: ./gradlew test
       └─ gh pr create --label automation:worker
            │
            ├─ CI (ci.yml) — Build & Test
            ├─ Gemini review (gemini-review.yml)
            └─ Codex review (external; posts "codex-review: pass" comment)
                 │
                 ▼
            gatekeeper.yml                [trigger: pull_request_review / check_suite]
              ├─ kill-switch check
              ├─ condition 1: Build & Test → success
              ├─ condition 2: Gemini P0=0 AND P1=0
              ├─ condition 3: comment contains "codex-review: pass"
              ├─ ALL pass → gh pr merge --squash --auto
              └─ ANY fail → label needs-human + ping Issue
                   │
                   ▼
              Developer writes "@claude fix ..."
                   │
                   ▼
            comment-handler.yml           [trigger: issue_comment / pull_request_review_comment]
              ├─ guard: contains(@claude) AND author_association in [OWNER,MEMBER,COLLABORATOR]
              ├─ PR comment → Worker retry (EXTRA_INSTRUCTION)
              └─ Issue comment on maestro-managed issue → Maestro re-plan

            [independently, on push to main]
auto-rebase.yml                           [trigger: push main]
  ├─ kill-switch check
  ├─ list open PRs labeled automation:worker
  └─ gh pr update-branch --rebase each
       ├─ success → continue
       └─ conflict → label needs-human + post rebase instructions
```

---

## Workflow summary table

| Workflow file | Trigger | Key secret/var used | Output |
|---|---|---|---|
| `maestro-dispatch.yml` | `issues: [opened, labeled]` | `ANTHROPIC_API_KEY`, `AUTOMATION_ENABLED` | Plan files committed, `repository_dispatch` emitted |
| `worker-dispatch.yml` | `repository_dispatch: worker-task` | `ANTHROPIC_API_KEY`, `AUTOMATION_ENABLED`, `GITHUB_TOKEN` | PRs opened, labeled `automation:worker` |
| `comment-handler.yml` | `issue_comment`, `pull_request_review_comment` | `ANTHROPIC_API_KEY`, `AUTOMATION_ENABLED`, `GITHUB_TOKEN` | Worker retry or Maestro re-plan |
| `auto-rebase.yml` | `push: branches: [main]` | `AUTOMATION_ENABLED`, `GITHUB_TOKEN` | PRs rebased or labeled `needs-human` |
| `gatekeeper.yml` | `pull_request_review`, `check_suite` | `AUTOMATION_ENABLED`, `GITHUB_TOKEN` | PR auto-merged or labeled `needs-human` |

---

## Required GitHub configuration

### Repository secrets (Settings → Secrets and variables → Actions → Secrets)

| Secret name | Value | Used by |
|---|---|---|
| `ANTHROPIC_API_KEY` | Anthropic API key for Claude | `maestro-dispatch.yml`, `worker-dispatch.yml`, `comment-handler.yml` |
| `DOCKERHUB_USERNAME` | Docker Hub username | Existing `cd.yml` (unchanged) |
| `DOCKERHUB_TOKEN` | Docker Hub access token | Existing `cd.yml` (unchanged) |
| `EC2_HOST` | EC2 public IP/hostname | Existing `cd.yml` (unchanged) |
| `EC2_SSH_KEY` | SSH private key for EC2 | Existing `cd.yml` (unchanged) |

### Repository variables (Settings → Secrets and variables → Actions → Variables)

| Variable name | Type | Default | Purpose |
|---|---|---|---|
| `AUTOMATION_ENABLED` | string | `"false"` | Global kill switch. Set to `"true"` to activate the pipeline. Every automation workflow checks this as its first step. |

> Set `AUTOMATION_ENABLED` to `"false"` in the GitHub UI to immediately disable all automation for new events. In-flight runs are not cancelled.

### Labels (must exist before first use)

Create these labels at `github.com/<owner>/live-class/labels`:

| Label | Color (suggestion) | Used by |
|---|---|---|
| `maestro:auto` | `#0075ca` | Issue template auto-applies; triggers `maestro-dispatch.yml` |
| `automation:worker` | `#e4e669` | `worker-dispatch.yml` applies to every Worker PR |
| `needs-human` | `#d93f0b` | `gatekeeper.yml` and `auto-rebase.yml` apply on failure |

---

## Fork PR security

None of the automation workflows use `pull_request_target`. They trigger on:
- `issues` events (no fork artifact access involved)
- `repository_dispatch` (internal, requires write access to emit)
- `issue_comment` / `pull_request_review_comment` (guarded by `author_association` check)
- `push: main` (push to main is already protected)
- `pull_request_review` / `check_suite` (reads only; merge requires `GITHUB_TOKEN` write permission on the workflow's own runner)

`ANTHROPIC_API_KEY` is **never** exposed to workflows triggered from fork PRs.

---

## Operating the pipeline

### Enable the pipeline
```
gh variable set AUTOMATION_ENABLED --body "true"
```

### Disable the pipeline (kill switch)
```
gh variable set AUTOMATION_ENABLED --body "false"
```

### Trigger manually (for testing)
```
# Simulate an issue open event
gh api repos/<owner>/live-class/dispatches \
  -f event_type=worker-task \
  -f client_payload='{"manifest":{"issue":999,"tasks":[{"nn":"01","file":"plan/before/01_test.md","role":"Generalist_Worker","deps":[],"cost_budget_tokens":10000}]},"base_branch":"main","issue_number":999}'
```

### Retry a failed worker
Leave a comment on the PR:
```
@claude fix the failing test in EnrollmentServiceTest
```

The `comment-handler.yml` will pick it up if you have `OWNER`, `MEMBER`, or `COLLABORATOR` association.

### Re-plan a maestro-managed issue
Leave a comment on the Issue:
```
@claude re-plan with scope limited to web/ only
```

### Merge a stuck worker PR manually
```
gh pr merge <number> --squash
```

---

## Extending the pipeline

- **Add a new review condition to Gatekeeper.** Edit `gatekeeper.yml` steps `ci`, `gemini`, `codex` pattern and add a new `steps.newcheck.outputs.*` to the merge condition.
- **Add a new role.** Edit the `Derive worker branch name` step in `worker-dispatch.yml` to recognize the new role slug.
- **Increase cost cap.** Adjust `MAESTRO_TOKEN_BUDGET` in `maestro-dispatch.yml` and the 200K cap in `docs/architecture/automation-pipeline.md`.
- **Add Slack notifications.** Introduce a `notify` job after `evaluate` in `gatekeeper.yml`, using `slackapi/slack-github-action`.
