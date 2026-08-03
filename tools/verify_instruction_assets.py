#!/usr/bin/env python3
"""Verify the delivery contract for every committed instruction illustration.

Tracker-approved exact-stage assets use transparency so their expressive dark
stage can sit naturally on both light and dark app surfaces. Untracked review
inputs remain opaque until they pass through the same production loop. Retired
legacy resource names are rejected so obsolete artwork cannot be repackaged.
"""

from __future__ import annotations

import hashlib
import json
import re
import sys
from pathlib import Path

from PIL import Image, UnidentifiedImageError


ROOT = Path(__file__).resolve().parents[1]
DRAWABLE_DIR = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi"
TRACKER_PATH = (
    ROOT / "docs" / "brewing" / "starlit-tactile-production-tracker-2026-08-02.json"
)
EXPECTED_SIZE = (1024, 768)
MAX_ENCODED_BYTES = 300_000
RESOURCE_NAME = re.compile(r"[a-z][a-z0-9_]*")
RETIRED_RESOURCE_NAMES = frozenset(
    {
        "instruction_steep_and_release_clever_style_"
        "clever_style_insert_and_rinse_filter_default",
        "instruction_steep_and_release_hario_switch_"
        "hario_switch_add_coffee_default",
        "instruction_restricted_flow_gravity_concentrate_vietnamese_phin_"
        "vietnamese_phin_place_on_stable_cup_default",
    }
)


def sha256(path: Path) -> str:
    """Return a stable digest without decoding or rewriting the asset."""
    return hashlib.sha256(path.read_bytes()).hexdigest()


def load_approved_candidates() -> tuple[dict[str, Path], list[str]]:
    """Load the exact assets that completed the documented visual QA loop."""
    errors: list[str] = []
    try:
        tracker = json.loads(TRACKER_PATH.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        return {}, [f"cannot load production tracker ({error})"]

    expected_count = tracker.get("scope", {}).get("expected_asset_count")
    candidates: dict[str, Path] = {}
    for item in tracker.get("assets", []):
        asset_id = item.get("asset_id")
        if item.get("status") != "accepted_candidate":
            continue
        if item.get("qa") != "complete":
            errors.append(f"{asset_id or '<missing id>'}: accepted candidate is not QA-complete")
            continue
        candidate_value = item.get("candidate")
        if not asset_id or not candidate_value:
            errors.append("tracker item is missing asset_id or candidate")
            continue
        candidate = ROOT / candidate_value
        if not candidate.is_file():
            errors.append(f"{asset_id}: accepted candidate does not exist: {candidate_value}")
            continue
        if asset_id in candidates:
            errors.append(f"{asset_id}: duplicate tracker entry")
            continue
        candidates[asset_id] = candidate


    return candidates, errors


def verify_asset(path: Path, approved_candidate: Path | None) -> list[str]:
    """Return every contract violation for one drawable."""
    errors: list[str] = []
    resource_name = path.stem
    if RESOURCE_NAME.fullmatch(resource_name) is None:
        errors.append(f"{path.name}: invalid Android resource name")

    encoded_size = path.stat().st_size
    if encoded_size > MAX_ENCODED_BYTES:
        errors.append(
            f"{path.name}: expected at most {MAX_ENCODED_BYTES:,} bytes, "
            f"got {encoded_size:,}",
        )

    try:
        with Image.open(path) as image:
            image.load()
            if image.format != "WEBP":
                errors.append(f"{path.name}: expected WebP, got {image.format or 'unknown'}")
            if image.size != EXPECTED_SIZE:
                errors.append(
                    f"{path.name}: expected {EXPECTED_SIZE[0]}x{EXPECTED_SIZE[1]}, "
                    f"got {image.width}x{image.height}",
                )
            if approved_candidate is None:
                if image.mode != "RGB":
                    errors.append(f"{path.name}: expected legacy opaque RGB, got {image.mode}")
            else:
                if image.mode != "RGBA":
                    errors.append(f"{path.name}: expected reviewed RGBA, got {image.mode}")
                else:
                    alpha = image.getchannel("A")
                    alpha_min, alpha_max = alpha.getextrema()
                    corners = (
                        alpha.getpixel((0, 0)),
                        alpha.getpixel((image.width - 1, 0)),
                        alpha.getpixel((0, image.height - 1)),
                        alpha.getpixel((image.width - 1, image.height - 1)),
                    )
                    if any(value != 0 for value in corners):
                        errors.append(f"{path.name}: transparent perimeter has an opaque corner")
                    if alpha_min != 0 or alpha_max != 255:
                        errors.append(
                            f"{path.name}: expected both transparent and opaque pixels, "
                            f"got alpha range {alpha_min}..{alpha_max}",
                        )
            if getattr(image, "is_animated", False):
                errors.append(f"{path.name}: instructional assets must be static")
    except (OSError, UnidentifiedImageError) as error:
        errors.append(f"{path.name}: cannot decode image ({error})")

    if approved_candidate is not None and sha256(path) != sha256(approved_candidate):
        errors.append(f"{path.name}: deployed bytes differ from the accepted tracker candidate")

    return errors


def main() -> int:
    assets = sorted(DRAWABLE_DIR.glob("instruction_*.webp"))
    if not assets:
        print("No instruction WebP assets found.", file=sys.stderr)
        return 1

    approved_candidates, errors = load_approved_candidates()
    deployed_names = {asset.stem for asset in assets}
    for retired_name in RETIRED_RESOURCE_NAMES & deployed_names:
        errors.append(f"{retired_name}: retired legacy instruction asset is packaged")
    for asset_id in approved_candidates.keys() - deployed_names:
        errors.append(f"{asset_id}: approved tracker asset has no deployed drawable")

    errors.extend(
        error
        for asset in assets
        for error in verify_asset(asset, approved_candidates.get(asset.stem))
    )
    if errors:
        print("Instruction asset verification failed:", file=sys.stderr)
        for error in errors:
            print(f"  - {error}", file=sys.stderr)
        return 1

    largest = max(assets, key=lambda asset: asset.stat().st_size)
    print(
        "Instruction asset verification passed: "
        f"{len(assets)} static WebP files at "
        f"{EXPECTED_SIZE[0]}x{EXPECTED_SIZE[1]}; largest is "
        f"{largest.name} ({largest.stat().st_size:,} bytes). "
        f"{len(approved_candidates)} tracker-approved exact assets are transparent; "
        f"{len(assets) - len(approved_candidates)} untracked review inputs are opaque.",
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
