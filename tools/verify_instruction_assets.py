#!/usr/bin/env python3
"""Verify the delivery contract for every committed instruction illustration."""

from __future__ import annotations

import re
import sys
from pathlib import Path

from PIL import Image, UnidentifiedImageError


ROOT = Path(__file__).resolve().parents[1]
DRAWABLE_DIR = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi"
EXPECTED_SIZE = (1024, 768)
MAX_ENCODED_BYTES = 300_000
RESOURCE_NAME = re.compile(r"[a-z][a-z0-9_]*")


def verify_asset(path: Path) -> list[str]:
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
            if image.mode != "RGB":
                errors.append(f"{path.name}: expected opaque RGB, got {image.mode}")
            if getattr(image, "is_animated", False):
                errors.append(f"{path.name}: instructional assets must be static")
    except (OSError, UnidentifiedImageError) as error:
        errors.append(f"{path.name}: cannot decode image ({error})")

    return errors


def main() -> int:
    assets = sorted(DRAWABLE_DIR.glob("instruction_*.webp"))
    if not assets:
        print("No instruction WebP assets found.", file=sys.stderr)
        return 1

    errors = [error for asset in assets for error in verify_asset(asset)]
    if errors:
        print("Instruction asset verification failed:", file=sys.stderr)
        for error in errors:
            print(f"  - {error}", file=sys.stderr)
        return 1

    largest = max(assets, key=lambda asset: asset.stat().st_size)
    print(
        "Instruction asset verification passed: "
        f"{len(assets)} opaque static WebP files at "
        f"{EXPECTED_SIZE[0]}x{EXPECTED_SIZE[1]}; largest is "
        f"{largest.name} ({largest.stat().st_size:,} bytes).",
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
