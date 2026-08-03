#!/usr/bin/env python3
"""Record one generated illustration verdict and maintain its regeneration todo."""

from __future__ import annotations

import argparse
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
TRACKER = ROOT / "docs" / "brewing" / "starlit-tactile-production-tracker-2026-08-02.json"


def relative_file(value: str) -> str:
    path = Path(value)
    absolute = path if path.is_absolute() else ROOT / path
    if not absolute.is_file():
        raise argparse.ArgumentTypeError(f"file does not exist: {value}")
    return absolute.relative_to(ROOT).as_posix()


def resolve_previous_todo(attempts: list[dict[str, object]], revision: str) -> None:
    if not attempts:
        return
    todo = attempts[-1].get("regeneration_todo")
    if isinstance(todo, dict) and todo.get("status") == "open":
        todo.update({"status": "resolved", "resolved_by": revision})


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("verdict", choices=("rejected", "accepted"))
    parser.add_argument("--asset", required=True)
    parser.add_argument("--revision", required=True)
    parser.add_argument("--prompt", required=True, type=relative_file)
    parser.add_argument("--raw-cache")
    parser.add_argument("--issue", action="append", default=[])
    parser.add_argument("--candidate", type=relative_file)
    parser.add_argument("--notes")
    parser.add_argument("--reused-from")
    args = parser.parse_args()

    data = json.loads(TRACKER.read_text(encoding="utf-8"))
    rows = [row for row in data["assets"] if row["asset_id"] == args.asset]
    if len(rows) != 1:
        raise ValueError(f"expected one tracker row for {args.asset}, found {len(rows)}")
    row = rows[0]
    attempts = row.setdefault("attempts", [])
    if any(attempt.get("revision") == args.revision for attempt in attempts):
        raise ValueError(f"{args.asset}: duplicate revision {args.revision}")
    resolve_previous_todo(attempts, args.revision)

    attempt: dict[str, object] = {
        "revision": args.revision,
        "prompt": args.prompt,
        "verdict": args.verdict,
    }
    if args.raw_cache:
        attempt["raw_cache"] = Path(args.raw_cache).name
    if args.reused_from:
        attempt["reused_from"] = args.reused_from

    if args.verdict == "rejected":
        if not args.raw_cache or not args.issue:
            raise ValueError("a rejected attempt requires --raw-cache and at least one --issue")
        attempt.update(
            {
                "issues": args.issue,
                "regeneration_todo": {"status": "open"},
            },
        )
        row.update(
            {
                "status": "regeneration_required",
                "open_todos": [
                    {
                        "kind": "regenerate",
                        "status": "open",
                        "detail": " ".join(args.issue),
                    },
                ],
                "qa": "pending",
            },
        )
    else:
        if not args.candidate or not args.notes:
            raise ValueError("an accepted attempt requires --candidate and --notes")
        attempt.update(
            {
                "full_resolution_review": "pass",
                "phone_light_review": "pass",
                "phone_dark_review": "pass",
                "notes": args.notes,
            },
        )
        row.update(
            {
                "status": "accepted_candidate",
                "accepted_revision": args.revision,
                "candidate": args.candidate,
                "qa": "complete",
                "open_todos": [],
                "notes": args.notes,
            },
        )

    attempts.append(attempt)
    data["updated_on"] = "2026-08-03"
    TRACKER.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"Recorded {args.asset} {args.revision}: {args.verdict}.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
