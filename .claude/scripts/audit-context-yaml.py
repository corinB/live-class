#!/usr/bin/env python3
# context.yaml의 stale 항목(날짜·라인 수·related_docs 부재)을 감지해 drift 목록을 출력하는 진단 스크립트
"""Audit context.yaml for staleness against the actual workspace state.

Checks performed:
1. metadata.last_indexed == today (KST date).
2. metadata.related_docs[*].description containing "N lines" matches the
   real `wc -l` count of the referenced markdown.
3. Each metadata.related_docs[*].path exists on disk, with one explicit
   exception: a path whose basename is README.md is allowed to be absent
   if its description contains a "(... 작성 예정)" note. The pending-note
   exemption does NOT apply to any other path — a missing DOCS.md or
   ARCHITECTURE.md is always drift, even if its description mentions
   "예정".

Exit code 0 = no drift, 1 = drift found, 2 = configuration error.
"""
from __future__ import annotations

import datetime
import re
import sys
from pathlib import Path

try:
    import yaml
except ImportError:
    print("error: PyYAML not installed. Run: pip install pyyaml", file=sys.stderr)
    sys.exit(2)


REPO_ROOT = Path(__file__).resolve().parent.parent.parent
CONTEXT_PATH = REPO_ROOT / "context.yaml"
LINE_COUNT_RE = re.compile(r"(\d+)\s*lines")
PENDING_NOTE_RE = re.compile(r"작성\s*예정")


def today_iso() -> str:
    return datetime.date.today().isoformat()


def count_lines(path: Path) -> int | None:
    if not path.is_file():
        return None
    with path.open("rb") as fh:
        return sum(1 for _ in fh)


def audit(doc: dict) -> list[str]:
    drifts: list[str] = []

    metadata = doc.get("metadata") or {}
    last_indexed = metadata.get("last_indexed")
    if last_indexed != today_iso():
        drifts.append(
            f"metadata.last_indexed: {last_indexed!r} != today {today_iso()!r}"
        )

    related = metadata.get("related_docs") or []
    for entry in related:
        if not isinstance(entry, dict):
            continue
        path_str = entry.get("path")
        description = entry.get("description", "")
        if not path_str:
            continue

        abs_path = REPO_ROOT / path_str
        exists = abs_path.is_file()

        if not exists:
            is_readme = Path(path_str).name.lower() == "readme.md"
            has_pending_note = PENDING_NOTE_RE.search(description) is not None
            if is_readme and has_pending_note:
                continue
            drifts.append(f"related_docs[{path_str}]: file missing")
            continue

        match = LINE_COUNT_RE.search(description)
        if not match:
            continue
        recorded = int(match.group(1))
        actual = count_lines(abs_path)
        if actual is None:
            continue
        if recorded != actual:
            drifts.append(
                f"related_docs[{path_str}]: description says {recorded} lines, "
                f"actual {actual}"
            )

    return drifts


def main() -> int:
    if not CONTEXT_PATH.is_file():
        print(f"error: {CONTEXT_PATH} not found", file=sys.stderr)
        return 2
    try:
        doc = yaml.safe_load(CONTEXT_PATH.read_text(encoding="utf-8"))
    except yaml.YAMLError as exc:
        print(f"error: yaml parse failed — {exc}", file=sys.stderr)
        return 2
    if not isinstance(doc, dict):
        print("error: context.yaml top-level must be a mapping", file=sys.stderr)
        return 2

    drifts = audit(doc)
    if drifts:
        print("drift found:")
        for line in drifts:
            print(f"  - {line}")
        return 1
    print("no drift")
    return 0


if __name__ == "__main__":
    sys.exit(main())
