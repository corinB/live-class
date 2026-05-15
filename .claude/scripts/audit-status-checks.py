# branch protection contexts 와 실제 workflow job 이름의 정합성을 검사하는 audit
"""
Fetches branch protection required status check contexts and compares them against
all job names produced by .github/workflows/*.yml files.

Exit codes:
  0 - all required contexts have a producer (PASS)
  2 - one or more required contexts have no producer (FAIL)
"""

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


def run_gh(args: list[str]) -> dict | list:
    result = subprocess.run(
        ["gh"] + args,
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        print(f"gh command failed: {' '.join(args)}", file=sys.stderr)
        print(result.stderr, file=sys.stderr)
        sys.exit(1)
    return json.loads(result.stdout)


def get_required_contexts(repo: str) -> list[str]:
    data = run_gh([
        "api",
        f"repos/{repo}/branches/main/protection",
        "--jq",
        ".required_status_checks.contexts",
    ])
    return data if isinstance(data, list) else []


def get_workflow_names(repo: str) -> list[str]:
    raw = run_gh([
        "api",
        f"repos/{repo}/actions/workflows",
    ])
    workflows = raw.get("workflows", [])
    return [w["name"] for w in workflows]


def collect_job_contexts(workflows_dir: Path) -> dict[str, list[str]]:
    """
    Walk .github/workflows/*.yml and collect the check name each job publishes.
    GitHub uses jobs.<key>.name if present, else jobs.<key> (the key itself).
    Returns: { workflow_name -> [check_name, ...] }
    """
    mapping: dict[str, list[str]] = {}

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

        mapping[wf_name] = check_names

    return mapping


def main() -> int:
    # Determine repo from git remote or environment
    repo = os.environ.get("GITHUB_REPOSITORY", "corinB/live-class")

    # Locate .github/workflows relative to repo root
    repo_root = Path(os.environ.get("GITHUB_WORKSPACE", "."))
    workflows_dir = repo_root / ".github" / "workflows"

    print("=== Required status checks audit ===")

    # 1. Fetch required contexts from branch protection
    required: list[str] = get_required_contexts(repo)
    print("\nRequired contexts (from branch protection):")
    for ctx in required:
        print(f"  - {ctx}")

    # 2. Walk workflow YAML files
    local_mapping = collect_job_contexts(workflows_dir)

    print("\nWorkflow -> produced contexts mapping:")
    for wf_name, contexts in sorted(local_mapping.items()):
        print(f"  {wf_name} -> {contexts}")

    # 3. Build flat set of all published check names
    all_published: set[str] = set()
    for contexts in local_mapping.values():
        all_published.update(contexts)

    required_set = set(required)
    missing = required_set - all_published
    extra = all_published - required_set

    print("\nVerification:")
    found_by: dict[str, str] = {}
    for ctx in required:
        producer = next(
            (wf for wf, jobs in local_mapping.items() if ctx in jobs),
            None,
        )
        if producer:
            found_by[ctx] = producer
            print(f"  OK {ctx} produced by {producer}")
        else:
            print(f"  MISSING {ctx} -- NO PRODUCER FOUND")

    total = len(required)
    passed = total - len(missing)

    if extra:
        print(f"\nInformational -- extra contexts (published but not required): {sorted(extra)}")

    if missing:
        print(f"\nResult: FAIL ({passed}/{total} contexts have producers)")
        print(f"  Missing: {sorted(missing)}")
        return 2

    print(f"\nResult: PASS ({passed}/{total} contexts have producers)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
