# audit-status-checks.py 의 핵심 동작을 검증하는 unit test (stdlib unittest 만 사용)
"""
Test audit-status-checks.py without external test runner dependencies.

Run: python3 .claude/scripts/test_audit_status_checks.py
Exit codes follow standard unittest convention (0 pass, non-zero fail).

Covered cases:
- parse_args defaults
- collect_job_contexts handles jobs.<key>.name and the bare-key fallback
- main returns 1 when --repo is missing
- run_gh returns None when subprocess fails and allow_fail=True
"""

import io
import os
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

import importlib.util

SCRIPT_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(SCRIPT_DIR))

# Load audit-status-checks.py by file path (hyphenated filename).
audit_path = SCRIPT_DIR / "audit-status-checks.py"
spec = importlib.util.spec_from_file_location("audit_status_checks", audit_path)
audit = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(audit)


class TestParseArgs(unittest.TestCase):
    def test_default_repo_from_env(self) -> None:
        with mock.patch.dict(os.environ, {"GITHUB_REPOSITORY": "foo/bar"}, clear=False):
            ns = audit.parse_args([])
            self.assertEqual(ns.repo, "foo/bar")

    def test_explicit_repo_arg_overrides_env(self) -> None:
        with mock.patch.dict(os.environ, {"GITHUB_REPOSITORY": "foo/bar"}, clear=False):
            ns = audit.parse_args(["--repo", "baz/qux"])
            self.assertEqual(ns.repo, "baz/qux")

    def test_no_repo_no_env(self) -> None:
        env = {k: v for k, v in os.environ.items() if k != "GITHUB_REPOSITORY"}
        with mock.patch.dict(os.environ, env, clear=True):
            ns = audit.parse_args([])
            self.assertIsNone(ns.repo)


class TestCollectJobContexts(unittest.TestCase):
    def _write_workflow(self, dirpath: Path, fname: str, body: str) -> None:
        dirpath.mkdir(parents=True, exist_ok=True)
        (dirpath / fname).write_text(body, encoding="utf-8")

    def test_named_job_uses_name_field(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            wf_dir = Path(tmp)
            self._write_workflow(
                wf_dir,
                "a.yml",
                'name: Alpha\njobs:\n  job_one:\n    name: Display Name\n    runs-on: ubuntu-latest\n    steps:\n      - run: echo ok\n',
            )
            result = audit.collect_job_contexts(wf_dir)
            self.assertEqual(result, {"Alpha": ["Display Name"]})

    def test_unnamed_job_falls_back_to_key(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            wf_dir = Path(tmp)
            self._write_workflow(
                wf_dir,
                "b.yml",
                'name: Beta\njobs:\n  build_and_test:\n    runs-on: ubuntu-latest\n    steps:\n      - run: echo ok\n',
            )
            result = audit.collect_job_contexts(wf_dir)
            self.assertEqual(result, {"Beta": ["build_and_test"]})

    def test_invalid_yaml_is_skipped_with_warning(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            wf_dir = Path(tmp)
            self._write_workflow(wf_dir, "c.yml", "this: is: not: valid: yaml")
            captured = io.StringIO()
            with mock.patch("sys.stderr", captured):
                result = audit.collect_job_contexts(wf_dir)
            # No exception; either empty mapping or partial. Just ensure no crash.
            self.assertIsInstance(result, dict)


class TestRunGh(unittest.TestCase):
    def test_allow_fail_returns_none_on_nonzero(self) -> None:
        with mock.patch("subprocess.run") as srun:
            srun.return_value = mock.Mock(returncode=1, stdout="", stderr="boom")
            out = audit.run_gh(["api", "nope"], allow_fail=True)
            self.assertIsNone(out)

    def test_strict_fail_exits(self) -> None:
        with mock.patch("subprocess.run") as srun:
            srun.return_value = mock.Mock(returncode=1, stdout="", stderr="boom")
            with self.assertRaises(SystemExit) as cm:
                audit.run_gh(["api", "nope"])
            self.assertEqual(cm.exception.code, 1)


class TestMain(unittest.TestCase):
    def test_main_exits_1_when_repo_missing(self) -> None:
        env = {k: v for k, v in os.environ.items() if k != "GITHUB_REPOSITORY"}
        with mock.patch.dict(os.environ, env, clear=True):
            with mock.patch.object(sys, "argv", ["audit-status-checks.py"]):
                rc = audit.main()
                self.assertEqual(rc, 1)


if __name__ == "__main__":
    unittest.main(verbosity=2)
