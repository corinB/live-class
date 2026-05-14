# Maestro + Worker automation pipeline

> Architecture document for the live-class automation pipeline. The user authors one GitHub Issue; the system decomposes, codes, reviews, and merges autonomously, falling back to HITL only on failure.

## Scope

This document defines the **target state** of the automation pipeline. It does NOT replace `DOCS.md` (domain) or `ARCHITECTURE.md` (concurrency). It describes the **process** that produces code changes, not the application architecture itself.

## Pipeline overview

```
User --writes--> GitHub Issue (labeled `maestro:auto`)
                 |
                 v
            issues.opened webhook
                 |
                 v
   .github/workflows/maestro-dispatch.yml
                 |
                 v
      Claude Code SDK -> Maestro subagent
                 |
                 v
        plan/before/NN_*.md  +  manifest.json
                 |
                 v
   repository_dispatch: worker-task
                 |
                 v
   .github/workflows/worker-dispatch.yml  (matrix over manifest)
                 |
                 v
      Claude Code SDK -> Worker subagent  (xN parallel)
                 |
                 v
        gh pr create  (label: automation:worker)
                 |
        ---+-----------+-----------+---
           |           |           |
           v           v           v
    Build & Test  Gemini review  Codex review
           |           |           |
           +-----------+-----------+
                       |
                       v
              .github/workflows/gatekeeper.yml
                       |
              all green? ----yes----> gh pr merge --squash --auto
                       |
                       no
                       |
                       v
              label `needs-human` + Issue comment ping
                       |
                       v
              HITL: developer leaves `@claude ...` comment
                       |
                       v
       .github/workflows/comment-handler.yml -> Worker retry
```

## Components

| Component | Lives in | Owns |
|-----------|----------|------|
| **Maestro** | `.claude/agents/maestro.md` | Issue -> task graph -> manifest |
| **Worker** | `.claude/agents/worker.md` | Single task -> code -> PR |
| **Maestro dispatcher** | `.github/workflows/maestro-dispatch.yml` | `issues.opened` -> run Maestro -> push plan/before -> emit `repository_dispatch` |
| **Worker dispatcher** | `.github/workflows/worker-dispatch.yml` | matrix of Worker invocations |
| **Comment handler** | `.github/workflows/comment-handler.yml` | `@claude ...` mention -> re-run Worker or Maestro |
| **Auto-rebase bot** | `.github/workflows/auto-rebase.yml` | `push: main` -> rebase open `automation:worker` PRs |
| **Gatekeeper** | `.github/workflows/gatekeeper.yml` | CI + Gemini + Codex green -> auto-merge |
| **Kill switch** | Repository variable `AUTOMATION_ENABLED` | First-step gate on every workflow |
| **Cost meter** | Embedded in Maestro/Worker entrypoints | Counts tokens; aborts at 200K per Issue |

## State machine

```
Issue
  received  --maestro-dispatch--> planning
  planning  --plan/before pushed--> dispatching
  dispatching --workers running--> in-progress
  in-progress --all PRs merged--> succeeded
  in-progress --some PRs need-human--> partially-failed (HITL)
  any state --AUTOMATION_ENABLED=false OR cost cap--> aborted
```

`partially-failed` Issues stay open with a `needs-human` label; a developer comment with `@claude ...` transitions back to `in-progress`.

## Retry policy

- Per-Worker: 1 automatic retry on transient failure (CI flake, network).
- Per-Worker: 0 automatic retry on review failure (Gemini P0/P1 or Codex fail). The Worker requires explicit `@claude fix ...` HITL trigger.
- Per-Maestro: 0 automatic retry. Maestro failures always escalate.

## Auto-merge gate (Gatekeeper)

A PR labeled `automation:worker` is merged when **all three** conditions hold:

1. `Build & Test` check suite has conclusion `success`.
2. The latest Gemini review comment reports `P0 == 0` and `P1 == 0`.
3. A sticky PR comment contains the marker `codex-review: pass`.

If any condition fails, Gatekeeper labels the PR `needs-human` and posts an Issue comment pinging the author.

Roll-back of an incorrectly merged PR is **manual**. A developer opens a revert PR using `gh pr create --base main`.

## Cost cap

Each Maestro/Worker invocation maintains a running token count for the **originating Issue** (carried via `repository_dispatch` payload). When the cumulative count exceeds 200,000 tokens, the next invocation aborts with state `aborted` and pings the Issue.

## Security

- `ANTHROPIC_API_KEY` is a Repository **secret**, not a variable.
- No workflow uses `pull_request_target` against untrusted refs; fork PRs cannot read the secret.
- `comment-handler.yml` checks that the commenter has write access (`author_association in [OWNER, MEMBER, COLLABORATOR]`) before honoring `@claude` mentions.
- Worker uses `worktree` isolation so concurrent Workers cannot stomp each other.
- The `gh` CLI uses the workflow's ephemeral `GITHUB_TOKEN` with the minimum scope each workflow needs.

## Kill switch

Repository variable `AUTOMATION_ENABLED` (string `"true"` or `"false"`). Every automation workflow's first job step is:

```yaml
if: vars.AUTOMATION_ENABLED == 'true'
```

Setting it to `"false"` in the GitHub UI takes effect on the next event.

## Branch sync (Continuous Branch Sync)

On `push: main`, `auto-rebase.yml` enumerates open PRs labeled `automation:worker` and attempts `gh pr update-branch --rebase` on each. Conflicts trigger a PR comment with `@claude rebase` and a `needs-human` label; no force-push from the bot.

## Issue template

`.github/ISSUE_TEMPLATE/automation-feature.yml` enforces three required fields:

- `acceptance` (markdown checklist) — what "done" means.
- `scope` (markdown) — which directories or modules the work can touch.
- `priority` (dropdown: P0/P1/P2).

The template automatically applies the `maestro:auto` label.

## Out of scope

- Slack/email notifications.
- Multi-tenant Maestro (one Maestro instance per repository for now; Issues queue).
- Codex review as a GitHub App (the `codex-review: pass` marker assumes a separate posting mechanism — see follow-up work).
- Automatic roll-back of merged PRs.

## Follow-up work

- Codex review GitHub App so condition 3 of the Gatekeeper has a real producer.
- Token cost dashboard (per-Issue and per-day rollups).
- Maestro epoch-level parallelism (currently 1 active Maestro per repo).
- Auto-writing `reports/NN_*.md` after successful merge, plus `plan/before -> plan/after` move.
