#!/usr/bin/env python3
"""Keep exact-stage matrix asset dispositions aligned with the QA tracker."""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MATRIX = ROOT / "docs" / "brewing" / "p1-exact-stage-matrix.md"
TRACKER = ROOT / "docs" / "brewing" / "starlit-tactile-production-tracker-2026-08-02.json"
ASSET_RE = re.compile(r"instruction_p1_[a-z0-9_]+_default")
DISPOSITION_RE = re.compile(r"(`P1-STAGE-CARD` · `)(?:NOT_PRODUCED|ACCEPTED_CANDIDATE)(`)")
SUMMARY_RE = re.compile(
    r"The production tracker currently records \d+ accepted exact-stage assets and \d+ open assets\."
)


def expected_text() -> tuple[str, int, int]:
    tracker = json.loads(TRACKER.read_text(encoding="utf-8"))
    rows = {row["asset_id"]: row for row in tracker["assets"]}
    accepted = {
        asset_id
        for asset_id, row in rows.items()
        if row.get("status") == "accepted_candidate" and row.get("qa") == "complete"
    }

    lines = MATRIX.read_text(encoding="utf-8").splitlines()
    seen: set[str] = set()
    output: list[str] = []
    for line in lines:
        asset_match = ASSET_RE.search(line)
        if not asset_match:
            output.append(line)
            continue
        asset_id = asset_match.group(0)
        if asset_id not in rows:
            raise ValueError(f"matrix asset is absent from tracker: {asset_id}")
        if asset_id in seen:
            raise ValueError(f"matrix contains duplicate asset: {asset_id}")
        seen.add(asset_id)
        disposition = "ACCEPTED_CANDIDATE" if asset_id in accepted else "NOT_PRODUCED"
        updated, count = DISPOSITION_RE.subn(rf"\g<1>{disposition}\g<2>", line)
        if count != 1:
            raise ValueError(f"matrix row has no unique P1 disposition: {asset_id}")
        output.append(updated)

    missing = set(rows) - seen
    if missing:
        raise ValueError(f"tracker assets are absent from matrix: {sorted(missing)}")

    accepted_count = len(accepted)
    open_count = len(rows) - accepted_count
    text = "\n".join(output) + "\n"
    summary = (
        f"The production tracker currently records {accepted_count} accepted exact-stage assets "
        f"and {open_count} open assets."
    )
    if SUMMARY_RE.search(text):
        text = SUMMARY_RE.sub(summary, text, count=1)
    else:
        anchor = (
            "Every proposed exact-stage asset is currently `NOT_PRODUCED`. Existing\n"
            "generic partial candidates are called out, remain `PENDING_REVIEW`, and do\n"
            "not satisfy an exact composite stage.\n"
        )
        replacement = (
            f"{summary} Accepted candidates remain subject to the separate runtime,\n"
            "accessibility, localization, and release gates; open assets remain\n"
            "`NOT_PRODUCED`.\n"
        )
        if anchor not in text:
            raise ValueError("matrix production-status summary anchor was not found")
        text = text.replace(anchor, replacement, 1)
    return text, accepted_count, open_count


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--write", action="store_true", help="update the matrix in place")
    args = parser.parse_args()

    expected, accepted, open_count = expected_text()
    current = MATRIX.read_text(encoding="utf-8")
    if current == expected:
        print(f"Matrix asset dispositions are current: {accepted} accepted, {open_count} open.")
        return 0
    if not args.write:
        print("Matrix asset dispositions are stale; run with --write.")
        return 1
    MATRIX.write_text(expected, encoding="utf-8")
    print(f"Matrix asset dispositions updated: {accepted} accepted, {open_count} open.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
