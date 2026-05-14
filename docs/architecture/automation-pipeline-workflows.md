# Automation Pipeline — Workflow Composition Reference

> How the 3 GitHub Actions workflows compose with the **main local Claude Code session** in the realigned (local-driven) pipeline.
> Read `docs/architecture/automation-pipeline.md` for the state machine and policy decisions.

## End-to-end flow

```
User opens GitHub Issue
  └─ applies label: maestro:auto (via Issue template)
       │
       ▼
maestro-dispatch.yml                       [trigger: issues.opened / labeled]
  ├─ kill-switch check (AUTOMATION_ENABLED)
  ├─ add label: needs-maestro
  └─ post Issue comment instructing the user to invoke the main session
       │
       │  (user moves to local Claude Code)
       │  > "Issue #<n> 처리해"
       │
       ▼
Main Claude Code session
  ├─ invokes maestro agent (.claude/agents/maestro.md)
  │    reads: DOCS.md, ARCHITECTURE.md, automation-pipeline.md
  │    emits: plan/before/NN_*.md + plan/before/manifest.json
  │    commits to chore/maestro-<issue-number> and pushes
  ├─ invokes worker agents in parallel (.claude/agents/worker.md)
  │    each in its own worktree, reads one task file
  │    writes code, runs ./gradlew test locally, opens PR
  │    PR label: automation:worker
  └─ user is informed PR is open
       │
       ▼
PR-side automation (no LLM)
  ├─ ci.yml — Build & Test
  ├─ gemini-review.yml — posts P0/P1 comment
  │
  ▼
gatekeeper.yml                              [trigger: pull_request_review or check_suite]
  ├─ kill-switch check
  ├─ verify CI green
  ├─ verify Gemini comment has P0=0 AND P1=0
  ├─ all green? → gh pr merge --squash --auto
  └─ any failed? → add `needs-human` label, post PR + Issue comment

auto-rebase.yml                             [trigger: push to main]
  ├─ kill-switch check
  ├─ list open PRs labeled automation:worker
  ├─ for each: gh pr update-branch --rebase
  └─ on conflict: post @claude rebase comment + add needs-human label
```

## Workflow inventory (after realign)

| File | Trigger | Purpose | Uses Claude API? |
|------|---------|---------|------------------|
| `maestro-dispatch.yml` | `issues.opened` / `labeled` | Notify the user via Issue comment + add `needs-maestro` label | No |
| `gatekeeper.yml` | `pull_request_review`, `check_suite` | Verify CI + Gemini and auto-merge | No |
| `auto-rebase.yml` | `push: main` | Rebase open `automation:worker` PRs | No |

Removed during realign (required `ANTHROPIC_API_KEY`):

- `worker-dispatch.yml` — replaced by user-driven Worker invocation in the main session.
- `comment-handler.yml` — replaced by the user reading `@claude ...` comments and instructing the main session.

## Required secrets / variables / labels

### Repository secrets
- `GITHUB_TOKEN` — ephemeral, automatic. No setup needed.

(No `ANTHROPIC_API_KEY` required. The main local Claude Code session uses its own subscription.)

### Repository variables
- `AUTOMATION_ENABLED` — `"true"` or `"false"`. Global kill switch.

### Labels
- `maestro:auto` (auto-applied by the Issue template).
- `automation:worker` (applied by Worker agent on PR open).
- `needs-maestro` (applied by maestro-dispatch notifier).
- `needs-human` (applied by Gatekeeper on failure, or auto-rebase on conflict).

## How to extend

To add a new gatekeeper condition (e.g. a Codex marker once a producer ships), edit `.github/workflows/gatekeeper.yml`:
1. Add a `Check condition N — ...` step that sets `${{ steps.STEP.outputs.PASS }}`.
2. Add the new condition to both the "Auto-merge if all conditions pass" step's `if:` and the "Label needs-human" step's `if:`.
3. Add the new condition to the `needs-human` Issue comment.

To re-enable LLM-driven dispatch (once `CLAUDE_CODE_OAUTH_TOKEN` or similar is available):
1. Restore the deleted `worker-dispatch.yml` and `comment-handler.yml` from git history.
2. Replace `ANTHROPIC_API_KEY` references with the new credential.
3. Update `automation-pipeline.md` and this document.

## Local equivalents

Operations that used to live in GHA workflows but now happen locally:

| GHA workflow (removed) | Local replacement |
|-------------------------|-------------------|
| `worker-dispatch.yml` | User: "Issue #<n> 의 Worker 들 실행해" — main session invokes Worker subagent in matrix |
| `comment-handler.yml` | User reads `@claude ...` PR comment, instructs main session to retry Worker with extra instruction |
