# Maestro + Worker automation pipeline (local-driven)

> Architecture document for the live-class automation pipeline. The user writes one GitHub Issue, the **local Claude Code session** runs Maestro+Worker on demand, and GitHub Actions handles notification, auto-rebase, and gatekeeping only.

## Scope

This document defines the **target state** of the automation pipeline. It does NOT replace `DOCS.md` (domain) or `ARCHITECTURE.md` (concurrency). It describes the **process** that produces code changes, not the application architecture itself.

## Why local-driven

An earlier draft put Maestro and Worker inside GitHub Actions and required an `ANTHROPIC_API_KEY` Repository secret. We rejected that to avoid separate API billing on top of the existing Claude Code subscription. The current design moves the LLM-side work to the **main local Claude Code session**, while GitHub Actions retains only the parts that need no LLM authentication.

## Pipeline overview

```
User --writes--> GitHub Issue (labeled `maestro:auto`)
                 |
                 v
            issues.opened webhook
                 |
                 v
   .github/workflows/maestro-dispatch.yml  (notify-only)
                 |
                 v
   Adds label `needs-maestro` + Issue comment
                 |
                 |   (user reads notification, opens local Claude Code)
                 v
   User to main session: "Issue #<n> 처리해"
                 |
                 v
   Main session invokes Maestro agent (.claude/agents/maestro.md)
                 |
                 v
   Maestro -> plan/before/NN_*.md + manifest.json (commit + push)
                 |
                 v
   Main session invokes Worker agents in parallel (.claude/agents/worker.md)
                 |
                 v
   Each Worker -> code in worktree -> `gh pr create` (label automation:worker)
                 |
        ---+-----------+---
           |           |
           v           v
    Build & Test  Gemini review
           |           |
           +-----------+
                       |
                       v
              .github/workflows/gatekeeper.yml
                       |
              both green? ----yes----> gh pr merge --squash --auto
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
              User reads it, instructs main session, Worker retries
```

## Components

| Component | Lives in | Owns |
|-----------|----------|------|
| **Maestro** | `.claude/agents/maestro.md` | Issue -> task graph -> manifest. Invoked by main session, not GHA. |
| **Worker** | `.claude/agents/worker.md` | Single task -> code -> PR. Invoked by main session, not GHA. |
| **Notifier** | `.github/workflows/maestro-dispatch.yml` | `issues.opened`/`labeled` -> add `needs-maestro` label + Issue comment. No LLM call. |
| **Auto-rebase bot** | `.github/workflows/auto-rebase.yml` | `push: main` -> rebase open `automation:worker` PRs |
| **Gatekeeper** | `.github/workflows/gatekeeper.yml` | CI + Gemini both green -> auto-merge |
| **Kill switch** | Repository variable `AUTOMATION_ENABLED` | First-step gate on every workflow |
| **Cost meter** | Embedded in Maestro/Worker entrypoints (subscription tokens, not Anthropic API) | Tracks tokens; aborts at 200K per Issue |

There is **no** worker-dispatch.yml or comment-handler.yml — those required an `ANTHROPIC_API_KEY` and were removed.

## State machine

```
Issue
  received  --maestro-dispatch (notify)--> awaiting-maestro
  awaiting-maestro --user runs Maestro locally--> planning
  planning --plan/before pushed--> dispatching
  dispatching --user runs Workers locally--> in-progress
  in-progress --all PRs merged--> succeeded
  in-progress --some PRs need-human--> partially-failed (HITL)
  any state --AUTOMATION_ENABLED=false OR cost cap--> aborted
```

`partially-failed` Issues stay open with a `needs-human` label on the PR; a developer comment with `@claude ...` is read by the user, who instructs the main session to retry.

## Retry policy

- Per-Worker: 1 manual retry by the user via "다시 시도" in the main session, after fixing the named blocker. No automatic retry.
- Per-Maestro: no automatic retry. Maestro failures always surface to the user.

## Auto-merge gate (Gatekeeper)

A PR labeled `automation:worker` is merged when **both** conditions hold:

1. `Build & Test` check suite has conclusion `success`.
2. The latest Gemini review comment reports `P0 == 0` and `P1 == 0`.

A third condition (`codex-review: pass` marker) was considered and permanently dropped on 2026-05-15. Codex review continues to run only as the local stop-time gate inside the main Claude Code session, not as a PR-level producer.

If either condition fails, Gatekeeper labels the PR `needs-human` and posts an Issue comment pinging the author. Roll-back of an incorrectly merged PR is **manual**.

## Cost cap

Each Maestro/Worker invocation tracks tokens used against the **originating Issue**. When the cumulative count exceeds 200,000 tokens, the next invocation aborts and pings the Issue. The token accounting is best-effort inside the main session (using the SDK or just session-internal monitoring).

## Security

- No Repository secret is required for the automation pipeline. `GITHUB_TOKEN` (the workflow's ephemeral token) is the only credential used by Actions.
- No workflow uses `pull_request_target` against untrusted refs.
- Comment-handler is removed; HITL flows through the user.
- Worker uses `worktree` isolation so concurrent Workers cannot stomp each other.

## Kill switch

Repository variable `AUTOMATION_ENABLED` (string `"true"` or `"false"`). Every automation workflow's first job step is:

```yaml
if: vars.AUTOMATION_ENABLED == 'true'
```

Setting it to `"false"` in the GitHub UI takes effect on the next event.

## Branch sync (Continuous Branch Sync)

On `push: main`, `auto-rebase.yml` enumerates open PRs labeled `automation:worker` and attempts `gh pr update-branch --rebase` on each. Conflicts trigger a PR comment and a `needs-human` label; no force-push from the bot.

## Issue template

`.github/ISSUE_TEMPLATE/automation-feature.yml` enforces three required fields:

- `acceptance` (markdown checklist) — what "done" means.
- `scope` (markdown) — which directories or modules the work can touch.
- `priority` (dropdown: P0/P1/P2).

The template automatically applies the `maestro:auto` label.

## Out of scope

- Anthropic-API-driven autonomous mode (rejected for cost).
- Slack/email notifications.
- Multi-tenant Maestro (one Maestro instance per repository for now; Issues queue inside the main session).
- Automatic roll-back of merged PRs.

## Follow-up work

- Token cost dashboard (per-Issue and per-day rollups).
- Optional `CLAUDE_CODE_OAUTH_TOKEN` path if subscription auth in CI becomes officially supported, which would let us re-enable worker-dispatch.yml.
- Auto-writing `reports/NN_*.md` after successful merge, plus `plan/before -> plan/after` move.
