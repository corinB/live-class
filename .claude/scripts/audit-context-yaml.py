#!/usr/bin/env python3
# context.yaml의 schema v3 정합성(노드 id 유일성·네이밍·relationships 트리플)과 stale 항목(last_indexed/wc/path)을 검사하는 진단 스크립트
"""Audit context.yaml for graph schema v3 integrity and staleness.

Checks performed:
1. `schema_version` must equal 3. Lower versions print a migration hint.
2. Every dict node bearing an `id` key:
   - id is unique across the whole document.
   - id matches `^(domain|state|script|mirror|api|agent|skill|workflow|dir|external|invariant|term|principle|policy|doc|project|automation)/[a-z0-9_/]+$`.
3. `relationships` (when present) is a list of triples `{from, to, type}`:
   - `type` is in the closed vocabulary.
   - `from` and `to` resolve to known ids.
4. metadata.last_indexed == today (KST date).
5. metadata.related_docs[*].description containing "N lines" matches the
   real `wc -l` count of the referenced markdown.
6. Each metadata.related_docs[*].path exists on disk, with one explicit
   exception: a path whose basename is README.md is allowed to be absent
   if its description contains a "(... 작성 예정)" note.

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

ID_TYPES = (
    "domain", "state", "script", "mirror", "api", "agent", "skill",
    "workflow", "dir", "external", "invariant", "term", "principle",
    "policy", "doc", "project", "automation",
)
ID_RE = re.compile(r"^(" + "|".join(ID_TYPES) + r")/[a-z0-9_/]+$")

REL_TYPES = {
    "contains", "transitions_to", "enforced_by", "mitigated_by",
    "writes", "reads", "mirrors", "scopes", "triggered_by",
    "owns", "chains", "depends_on", "ref",
}


def today_iso() -> str:
    return datetime.date.today().isoformat()


def count_lines(path: Path) -> int | None:
    if not path.is_file():
        return None
    with path.open("rb") as fh:
        return sum(1 for _ in fh)


def walk_ids(node, ids: dict[str, list[str]], path: list[str]) -> None:
    """Collect every `id` value found inside any dict node, anywhere."""
    if isinstance(node, dict):
        if "id" in node and isinstance(node["id"], str):
            ids.setdefault(node["id"], []).append("/".join(path) or "<root>")
        for key, value in node.items():
            walk_ids(value, ids, path + [str(key)])
    elif isinstance(node, list):
        for i, item in enumerate(node):
            walk_ids(item, ids, path + [str(i)])


def load_imports(doc: dict, drifts: list[str]) -> list[dict]:
    """Load any `imports:` referenced yaml files and return their parsed docs."""
    imported: list[dict] = []
    imports = doc.get("imports") or []
    if not isinstance(imports, list):
        drifts.append("imports: must be a list of relative paths")
        return imported
    for entry in imports:
        if not isinstance(entry, str):
            drifts.append(f"imports: non-string entry {entry!r}")
            continue
        abs_path = REPO_ROOT / entry
        if not abs_path.is_file():
            drifts.append(f"imports[{entry}]: file missing")
            continue
        try:
            sub = yaml.safe_load(abs_path.read_text(encoding="utf-8"))
        except yaml.YAMLError as exc:
            drifts.append(f"imports[{entry}]: yaml parse failed - {exc}")
            continue
        if not isinstance(sub, dict):
            drifts.append(f"imports[{entry}]: top-level must be a mapping")
            continue
        imported.append(sub)
    return imported


def audit(doc: dict) -> list[str]:
    drifts: list[str] = []

    schema = doc.get("schema_version")
    if schema != 3:
        drifts.append(
            f"schema_version: got {schema!r}, expected 3. "
            "Migrate by adding id: to every node and a top-level relationships: triples list."
        )
        return drifts

    imported = load_imports(doc, drifts)

    ids: dict[str, list[str]] = {}
    walk_ids(doc, ids, [])
    for i, sub in enumerate(imported):
        walk_ids(sub, ids, [f"import[{i}]"])
    for node_id, locations in ids.items():
        if len(locations) > 1:
            drifts.append(
                f"id collision: {node_id!r} appears at {len(locations)} locations: "
                f"{', '.join(locations)}"
            )
        if not ID_RE.match(node_id):
            drifts.append(
                f"id naming: {node_id!r} does not match "
                f"<type>/<slug> where type in {sorted(ID_TYPES)}"
            )

    known_ids = set(ids.keys())
    relationships: list = []
    for src in (doc, *imported):
        rel_list = src.get("relationships") or []
        if isinstance(rel_list, list):
            relationships.extend(rel_list)
        else:
            drifts.append("relationships: must be a list of {from, to, type}")
    for idx, rel in enumerate(relationships):
        if not isinstance(rel, dict):
            drifts.append(f"relationships[{idx}]: not a mapping")
            continue
        rtype = rel.get("type")
        rfrom = rel.get("from")
        rto = rel.get("to")
        if rtype not in REL_TYPES:
            drifts.append(
                f"relationships[{idx}].type: {rtype!r} not in vocabulary "
                f"{sorted(REL_TYPES)}"
            )
        if rfrom not in known_ids:
            drifts.append(f"relationships[{idx}].from: unknown id {rfrom!r}")
        if rto not in known_ids:
            drifts.append(f"relationships[{idx}].to: unknown id {rto!r}")

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
