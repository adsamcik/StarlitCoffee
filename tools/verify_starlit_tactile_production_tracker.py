#!/usr/bin/env python3
"""Validate closed-loop coverage for the Starlit Tactile P1 illustration queue."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
TRACKER = ROOT / "docs/brewing/starlit-tactile-production-tracker-2026-08-02.json"
MATRIX = ROOT / "docs/brewing/p1-exact-stage-matrix.md"
ASSET_RE = re.compile(r"instruction_p1_[a-z0-9_]+_default")
VALID_STATUSES = {"research_blocked", "generation_pending", "regeneration_required", "review_pending", "accepted_candidate"}


def queue_assets() -> set[str]:
    return set(ASSET_RE.findall(MATRIX.read_text(encoding="utf-8")))


def validate(require_complete: bool) -> tuple[list[str], list[str]]:
    errors: list[str] = []
    warnings: list[str] = []
    data = json.loads(TRACKER.read_text(encoding="utf-8"))
    rows = data.get("assets", [])
    ids = [row.get("asset_id") for row in rows]
    expected = queue_assets()

    if len(ids) != len(set(ids)):
        errors.append("tracker contains duplicate asset IDs")
    missing = sorted(expected - set(ids))
    extra = sorted(set(ids) - expected)
    if missing:
        errors.append(f"missing queued assets: {', '.join(missing)}")
    if extra:
        errors.append(f"unrecognized tracker assets: {', '.join(extra)}")
    if len(expected) != data["scope"]["expected_asset_count"]:
        errors.append(
            f"queue contains {len(expected)} assets, expected_asset_count is "
            f"{data['scope']['expected_asset_count']}"
        )

    for row in rows:
        asset_id = row.get("asset_id", "<missing>")
        status = row.get("status")
        if status not in VALID_STATUSES:
            errors.append(f"{asset_id}: invalid status {status!r}")
            continue
        open_todos = row.get("open_todos", [])
        attempts = row.get("attempts", [])
        revisions = {attempt.get("revision") for attempt in attempts}

        for attempt in attempts:
            revision = attempt.get("revision", "<missing>")
            verdict = attempt.get("verdict")
            if verdict == "rejected":
                if not attempt.get("issues"):
                    errors.append(f"{asset_id} {revision}: rejected attempt has no recorded issue")
                todo = attempt.get("regeneration_todo")
                if not todo:
                    errors.append(f"{asset_id} {revision}: rejected attempt has no regeneration todo")
                elif todo.get("status") == "resolved":
                    resolved_by = todo.get("resolved_by")
                    if resolved_by not in revisions:
                        errors.append(
                            f"{asset_id} {revision}: regeneration resolved_by {resolved_by!r} is not an attempt"
                        )
                elif todo.get("status") != "open":
                    errors.append(f"{asset_id} {revision}: invalid regeneration todo status")

        if status == "accepted_candidate":
            if row.get("qa") != "complete":
                errors.append(f"{asset_id}: accepted candidate does not have complete QA")
            candidate = row.get("candidate")
            if not candidate or not (ROOT / candidate).is_file():
                errors.append(f"{asset_id}: accepted candidate artifact is missing")
            if open_todos:
                errors.append(f"{asset_id}: accepted candidate still has open todos")
        else:
            if not open_todos:
                errors.append(f"{asset_id}: incomplete asset has no open todo")
            warnings.append(f"{asset_id}: {status} ({len(open_todos)} open todos)")

        if status == "regeneration_required":
            if not attempts or attempts[-1].get("verdict") != "rejected":
                errors.append(f"{asset_id}: regeneration_required without a latest rejected attempt")
            elif attempts[-1].get("regeneration_todo", {}).get("status") != "open":
                errors.append(f"{asset_id}: regeneration_required without an open regeneration todo")
        if status == "review_pending":
            if not attempts or attempts[-1].get("verdict") != "provisionally_accepted":
                errors.append(f"{asset_id}: review_pending without a provisionally accepted latest attempt")
        if status == "research_blocked" and not str(row.get("blocker", "")).startswith("BLOCK-"):
            errors.append(f"{asset_id}: research_blocked without a BLOCK-* reason")

    if require_complete and warnings:
        errors.append(f"{len(warnings)} assets are not accepted candidates")
    return errors, warnings


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--require-complete",
        action="store_true",
        help="fail unless every queued asset is an accepted candidate",
    )
    args = parser.parse_args()
    errors, warnings = validate(args.require_complete)
    for warning in warnings:
        print(f"TODO: {warning}")
    for error in errors:
        print(f"ERROR: {error}", file=sys.stderr)
    if errors:
        return 1
    total = len(json.loads(TRACKER.read_text(encoding="utf-8")).get("assets", []))
    print(f"Tracker valid: {total - len(warnings)}/{total} accepted; {len(warnings)} still open.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
