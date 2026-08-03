#!/usr/bin/env python3
"""Install or verify only QA-complete Starlit Tactile instruction assets."""

from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
TRACKER = ROOT / "docs" / "brewing" / "starlit-tactile-production-tracker-2026-08-02.json"
DRAWABLE_DIR = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi"


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def approved_assets() -> list[tuple[str, Path, Path]]:
    data = json.loads(TRACKER.read_text(encoding="utf-8"))
    expected = data["scope"]["expected_asset_count"]
    assets: list[tuple[str, Path, Path]] = []
    for item in data["assets"]:
        asset_id = item["asset_id"]
        if item.get("status") != "accepted_candidate":
            continue
        if item.get("qa") != "complete":
            raise ValueError(f"{asset_id}: accepted candidate does not have complete QA")
        source = ROOT / item["candidate"]
        destination = DRAWABLE_DIR / f"{asset_id}.webp"
        if not source.is_file():
            raise FileNotFoundError(f"{asset_id}: missing accepted candidate {source}")
        assets.append((asset_id, source, destination))

    return assets


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--write",
        action="store_true",
        help="copy approved candidates into drawable-nodpi; otherwise only verify",
    )
    args = parser.parse_args()

    try:
        assets = approved_assets()
    except (OSError, KeyError, ValueError, json.JSONDecodeError) as error:
        print(f"Cannot load approved assets: {error}", file=sys.stderr)
        return 1

    if args.write:
        for _, source, destination in assets:
            shutil.copyfile(source, destination)

    mismatches = [
        asset_id
        for asset_id, source, destination in assets
        if not destination.is_file() or digest(source) != digest(destination)
    ]
    if mismatches:
        print("Approved assets are not installed exactly:", file=sys.stderr)
        for asset_id in mismatches:
            print(f"  - {asset_id}", file=sys.stderr)
        return 1

    action = "Installed" if args.write else "Verified"
    print(f"{action} {len(assets)} QA-complete instruction assets from the tracker.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
