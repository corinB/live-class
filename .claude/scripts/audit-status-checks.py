# branch protection contexts 와 실제 workflow job 이름의 정합성을 검사하는 audit
"""
Fetches branch protection required status check contexts and compares them against
all job names produced by .github/workflows/*.yml files.

Two failure modes:
  - missing producer: a required context has no job emitting it.
  - blocked-risk: the producer workflow has an `on.pull_request.paths`
    (whitelist) or `on.pull_request.paths-ignore` (blacklist) filter that
    can prevent it from running on certain PRs. Required checks must always
    produce a result, otherwise GitHub leaves them in the `expected` state
    and blocks merge indefinitely.

Exit codes:
  0 - all required contexts have a producer and no path-filter risk (PASS)
  2 - one or more required contexts have no producer, or producer has a
      PR path filter that could leave the check unproduced (FAIL)
"""

import argparse
import json
import os
import subprocess
import sys
from pathlib import Path

try:
    import yaml
except ImportError:
    print("PyYAML not installed. Run: pip install pyyaml", file=sys.stderr)
    sys.exit(1)


def run_gh(args: list[str], allow_fail: bool = False) -> dict | list | None:
    result = subprocess.run(
        ["gh"] + args,
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        if allow_fail:
            return None
        print(f"gh command failed: {' '.join(args)}", file=sys.stderr)
        print(result.stderr, file=sys.stderr)
        sys.exit(1)
    return json.loads(result.stdout) if result.stdout.strip() else None


def get_required_contexts(repo: str) -> list[str] | None:
    """Returns the required contexts list, or None if branch protection is not
    readable (e.g., GITHUB_TOKEN in CI lacks admin scope). In that case we skip
    the protection vs workflow comparison and only verify workflows parse."""
    data = run_gh([
        "api",
        f"repos/{repo}/branches/main/protection",
        "--jq",
        ".required_status_checks.contexts",
    ], allow_fail=True)
    if data is None:
        return None
    return data if isinstance(data, list) else []


def get_workflow_names(repo: str) -> list[str]:
    raw = run_gh([
        "api",
        f"repos/{repo}/actions/workflows",
    ])
    workflows = raw.get("workflows", [])
    return [w["name"] for w in workflows]


def _get_on_block(doc: dict) -> object:
    """
    Return the workflow `on:` block, working around PyYAML's quirk of
    interpreting the bare key `on` as the YAML 1.1 boolean True.
    Workflow files write `on:` unquoted so the parsed dict key is the
    Python True object, not the string "on".
    """
    if "on" in doc:
        return doc["on"]
    if True in doc:
        return doc[True]
    return None


def _has_pr_path_filter(on_block: object) -> bool:
    """
    Return True if the `on:` block declares a pull_request trigger with
    either `paths` or `paths-ignore` filters, which can suppress the job
    on PRs whose changed files do not match.
    Accepts the three common shapes:
      on: [push, pull_request]            -- shorthand list, no filter
      on: pull_request                    -- shorthand scalar, no filter
      on:
        pull_request:
          paths: [...]                    -- detected
        pull_request:
          paths-ignore: [...]             -- detected
        pull_request:
          types: [...]                    -- no path filter
    """
    if not isinstance(on_block, dict):
        return False
    pr = on_block.get("pull_request")
    if not isinstance(pr, dict):
        return False
    paths = pr.get("paths")
    paths_ignore = pr.get("paths-ignore")
    return bool(paths) or bool(paths_ignore)


def collect_job_contexts(workflows_dir: Path) -> dict[str, dict]:
    """
    Walk .github/workflows/*.yml and collect the check name each job publishes
    plus whether the workflow has a PR path filter that could suppress it.
    GitHub uses jobs.<key>.name if present, else jobs.<key> (the key itself).
    Returns: { workflow_name -> {"checks": [check_name, ...], "pr_path_filter": bool} }
    """
    mapping: dict[str, dict] = {}

    for wf_file in sorted(workflows_dir.glob("*.yml")):
        try:
            with open(wf_file, encoding="utf-8") as f:
                doc = yaml.safe_load(f)
        except Exception as e:
            print(f"  Warning: could not parse {wf_file.name}: {e}", file=sys.stderr)
            continue

        if not isinstance(doc, dict):
            continue

        wf_name = doc.get("name", wf_file.stem)
        jobs = doc.get("jobs", {})
        if not isinstance(jobs, dict):
            continue

        check_names: list[str] = []
        for job_key, job_def in jobs.items():
            if isinstance(job_def, dict) and "name" in job_def:
                check_names.append(job_def["name"])
            else:
                check_names.append(job_key)

        on_block = _get_on_block(doc)
        pr_path_filter = _has_pr_path_filter(on_block)

        mapping[wf_name] = {"checks": check_names, "pr_path_filter": pr_path_filter}

    return mapping


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Audit GitHub branch protection required contexts vs workflow job names."
    )
    parser.add_argument(
        "--repo",
        default=os.environ.get("GITHUB_REPOSITORY"),
        help="owner/name (defaults to GITHUB_REPOSITORY env var; explicit fail if neither set)",
    )
    parser.add_argument(
        "--workflows-dir",
        default=str(Path(os.environ.get("GITHUB_WORKSPACE", ".")) / ".github" / "workflows"),
        help="path to .github/workflows directory (defaults under GITHUB_WORKSPACE or cwd)",
    )
    return parser.parse_args(argv)


def main() -> int:
    args = parse_args()
    repo = args.repo
    if not repo:
        print(
            "Error: --repo not provided and GITHUB_REPOSITORY env var is not set. "
            "Pass --repo owner/name or set GITHUB_REPOSITORY.",
            file=sys.stderr,
        )
        return 1
    workflows_dir = Path(args.workflows_dir)

    print("=== Required status checks audit ===")

    # 1. Fetch required contexts from branch protection (may be unreadable in CI)
    required_or_none = get_required_contexts(repo)
    if required_or_none is None:
        print("\n(branch protection not readable -- GITHUB_TOKEN likely lacks admin scope.")
        print(" Skipping protection-vs-workflow comparison; verifying workflow parse only.)")
        # Best-effort: still walk workflows and report parse errors.
        local_mapping = collect_job_contexts(workflows_dir)
        print("\nWorkflow -> produced contexts mapping:")
        for wf_name, info in sorted(local_mapping.items()):
            flag = " [pr-path-filter]" if info["pr_path_filter"] else ""
            print(f"  {wf_name} -> {info['checks']}{flag}")
        print("\nResult: PASS (workflow parse only)")
        return 0
    required: list[str] = required_or_none
    print("\nRequired contexts (from branch protection):")
    for ctx in required:
        print(f"  - {ctx}")

    # 2. Walk workflow YAML files
    local_mapping = collect_job_contexts(workflows_dir)

    print("\nWorkflow -> produced contexts mapping:")
    for wf_name, info in sorted(local_mapping.items()):
        flag = " [pr-path-filter]" if info["pr_path_filter"] else ""
        print(f"  {wf_name} -> {info['checks']}{flag}")

    # 3. Build flat set of all published check names
    all_published: set[str] = set()
    for info in local_mapping.values():
        all_published.update(info["checks"])

    required_set = set(required)
    missing = required_set - all_published
    extra = all_published - required_set

    print("\nVerification:")
    found_by: dict[str, str] = {}
    blocked_risk: list[tuple[str, str]] = []  # (context, producing workflow)
    for ctx in required:
        producer = next(
            (wf for wf, info in local_mapping.items() if ctx in info["checks"]),
            None,
        )
        if producer:
            found_by[ctx] = producer
            if local_mapping[producer]["pr_path_filter"]:
                blocked_risk.append((ctx, producer))
                print(f"  BLOCKED-RISK {ctx} produced by {producer}, which has a PR path filter")
            else:
                print(f"  OK {ctx} produced by {producer}")
        else:
            print(f"  MISSING {ctx} -- NO PRODUCER FOUND")

    total = len(required)
    passed = total - len(missing) - len(blocked_risk)

    if extra:
        print(f"\nInformational -- extra contexts (published but not required): {sorted(extra)}")

    if missing or blocked_risk:
        print(f"\nResult: FAIL ({passed}/{total} contexts are safely produced)")
        if missing:
            print(f"  Missing: {sorted(missing)}")
        if blocked_risk:
            print("  Blocked-risk (path-filtered producer for a required context):")
            for ctx, wf in blocked_risk:
                print(f"    - {ctx} <- {wf}")
            print("  Fix: remove paths/paths-ignore from on.pull_request and reproduce the filter")
            print("       inline with a step that exits with success when no matching changes.")
        return 2

    print(f"\nResult: PASS ({passed}/{total} contexts have safe producers)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
