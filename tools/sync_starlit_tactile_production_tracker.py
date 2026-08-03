#!/usr/bin/env python3
"""Reconcile the closed-loop illustration tracker with the exact-stage matrix."""

from __future__ import annotations

import json
import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MATRIX = ROOT / "docs" / "brewing" / "p1-exact-stage-matrix.md"
TRACKER = ROOT / "docs" / "brewing" / "starlit-tactile-production-tracker-2026-08-02.json"
ASSET_RE = re.compile(r"instruction_p1_[a-z0-9_]+_default")
BLOCKER_RE = re.compile(r"`((?:NONE|BLOCK)-[A-Z0-9-]+)`")
RESOLUTIONS = {
    "BLOCK-V60-VARIANT": "hario-kasuya-kdc-02-b",
    "BLOCK-WEDGE-VARIANT": "melitta-one-cup-number-2",
    "BLOCK-GENERIC-CONE": "bounded-app-reference-cone-02",
    "BLOCK-CEZVE-HARDWARE": "stc-pro-1-or-2-low-gas",
    "BLOCK-AUTO-BATCH-HARDWARE": "moccamaster-kbgv-select-cone-glass",
}
RESOLUTION_DOCUMENT = "docs/brewing/p1-exact-stage-blocker-resolutions-2026-08-03.md"


def matrix_rows() -> list[dict[str, str | int]]:
    rows: list[dict[str, str | int]] = []
    for number, line in enumerate(MATRIX.read_text(encoding="utf-8").splitlines(), start=1):
        asset_match = ASSET_RE.search(line)
        if not asset_match:
            continue
        blocker_matches = BLOCKER_RE.findall(line)
        if "safety-critical" in line:
            priority = "safety-critical"
        elif "`mandatory`" in line:
            priority = "mandatory"
        elif "`optional`" in line:
            priority = "optional"
        else:
            raise ValueError(f"line {number}: exact asset has no visual priority")
        if not blocker_matches:
            raise ValueError(f"line {number}: exact asset has no blocker code")
        rows.append(
            {
                "asset_id": asset_match.group(0),
                "priority": priority,
                "blocker": blocker_matches[-1],
                "matrix_line": number,
            },
        )
    ids = [row["asset_id"] for row in rows]
    if len(ids) != len(set(ids)):
        raise ValueError("matrix contains duplicate exact asset IDs")
    return rows


def production_todo(resolved: bool) -> list[dict[str, str]]:
    return [
        {
            "kind": "generate_and_review" if resolved else "research_and_generate",
            "status": "open",
            "detail": (
                "Generate the resolved configuration and complete every visual QA gate."
                if resolved
                else "Research mechanics, generate, and complete every visual QA gate."
            ),
        },
    ]


def main() -> int:
    data = json.loads(TRACKER.read_text(encoding="utf-8"))
    existing = {row["asset_id"]: row for row in data["assets"]}
    reconciled: list[dict[str, object]] = []
    for matrix_row in matrix_rows():
        asset_id = str(matrix_row["asset_id"])
        blocker = str(matrix_row["blocker"])
        resolution = RESOLUTIONS.get(blocker)
        if asset_id in existing:
            row = existing[asset_id]
            row.update(matrix_row)
            if row.get("status") == "research_blocked" and resolution:
                row.update(
                    {
                        "status": "generation_pending",
                        "research": "complete",
                        "blocker_resolution": resolution,
                        "resolution_document": RESOLUTION_DOCUMENT,
                        "open_todos": production_todo(resolved=True),
                    },
                )
            reconciled.append(row)
            continue

        reconciled.append(
            {
                **matrix_row,
                "batch": None,
                "status": "generation_pending",
                "research": "complete" if resolution else "queued",
                **(
                    {
                        "blocker_resolution": resolution,
                        "resolution_document": RESOLUTION_DOCUMENT,
                    }
                    if resolution
                    else {}
                ),
                "attempts": [],
                "open_todos": production_todo(resolved=resolution is not None),
                "qa": "pending",
            },
        )

    data["schema_version"] = 2
    data["updated_on"] = "2026-08-03"
    data["scope"].update(
        {
            "source_matrix": "docs/brewing/p1-exact-stage-matrix.md",
            "expected_asset_count": len(reconciled),
        },
    )
    data["assets"] = reconciled
    TRACKER.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    accepted = sum(row.get("status") == "accepted_candidate" for row in reconciled)
    blocked = sum(row.get("status") == "research_blocked" for row in reconciled)
    pending = len(reconciled) - accepted - blocked
    print(f"Tracker reconciled: {accepted} accepted, {pending} queued, {blocked} research-blocked.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
