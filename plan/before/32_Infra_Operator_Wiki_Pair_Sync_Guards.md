# NN 32 — Phase D — Wiki ↔ Root Doc Pair Sync Guards

- **Role**: Infra_Operator
- **Phase**: D
- **Issue**: #70
- **Blocking external**: none (parallelizable with C/E after A)
- **Refs**: `plan/before/32_Infra_Operator_Wiki_Pair_Sync_Guards.md`

## Goal
Install a blocking guard (hook + CI job) so a commit cannot stage one half of a `<root doc>` ↔ `wiki-src/ko/...-detail.md` pair without the other. Pairs are: `ARCHITECTURE.md` ↔ `wiki-src/ko/architecture-detail.md`, `DOCS.md` ↔ `wiki-src/ko/docs-detail.md`, `CONTRIBUTING.md` ↔ `wiki-src/ko/contributing-detail.md`, `ORCHESTRATION.md` ↔ `wiki-src/ko/orchestration-detail.md`. Escape hatch: `WIKI_PAIR_GUARD_OFF=1`.

## Inputs
- `.claude/hooks/` (new hook here).
- `.claude/settings.json` (register).
- `.github/workflows/` (new workflow `wiki-pair-sync.yml`).
- Pair list above (canonical; encode as an array in the hook).

## Steps
1. Create `.claude/hooks/pre-bash-wiki-pair-sync-guard.sh`:
   - PreToolUse hook on Bash.
   - If command is a `git commit`, run `git diff --cached --name-only`. For each pair `(root, wiki)`: if exactly one of the two appears in the staged set and the other is unchanged in `git diff HEAD --name-only` as well, block with JSON `{"decision":"block","reason":"pair skew: <root> staged without <wiki> (or vice versa)"}`.
   - Honors `WIKI_PAIR_GUARD_OFF=1`.
   - First line: `# 루트 doc과 wiki-src/ko/*-detail.md 짝 중 한쪽만 staged 되었을 때 커밋을 차단하는 가드`.
2. Register the hook in `.claude/settings.json` (PreToolUse, matcher `Bash`).
3. Create `.github/workflows/wiki-pair-sync.yml`:
   - Triggers: `pull_request` (paths: `ARCHITECTURE.md`, `DOCS.md`, `CONTRIBUTING.md`, `ORCHESTRATION.md`, `wiki-src/ko/*-detail.md`).
   - Single job `pair-check` running on `ubuntu-latest`:
     - `actions/checkout@v4` with `fetch-depth: 0`.
     - Bash step computes `git diff --name-only origin/${{ github.base_ref }}...HEAD` and applies the same pair logic as the hook. Fails the job on skew with a clear error.
   - Token: `${{ github.token }}` (read-only is fine).
   - File header comment line: `# 짝 중 한쪽만 변경된 PR 을 막는 CI 가드`.
4. Smoke test: stage only `ARCHITECTURE.md` (a no-op whitespace edit) and attempt `git commit` — must be blocked. Stage both → pass.
5. Write `reports/32_Infra_Operator_wiki-pair-sync_2026-05-16.md` with smoke logs.
6. Commit: `chore(harness): enforce root↔wiki-src pair sync` with footer `Refs: plan/before/32_Infra_Operator_Wiki_Pair_Sync_Guards.md`.

## Out-of-scope
- Adding new pairs (out of issue scope).
- Modifying existing `wiki-sync.yml` (already merged from #67/68).
- Editing wiki-src content.

## Acceptance
- New hook present and registered.
- New workflow `wiki-pair-sync.yml` listed by `gh workflow list`.
- Staging only one half of a pair → commit blocked locally.
- PR with skewed pair → CI fails.
- `WIKI_PAIR_GUARD_OFF=1` lets local commit pass.

## Refs
Refs: plan/before/32_Infra_Operator_Wiki_Pair_Sync_Guards.md
