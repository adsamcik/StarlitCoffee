#!/usr/bin/env python3
"""Verify that bloom animation assets use the modern 5x5 grid contract."""

from __future__ import annotations

import struct
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DRAWABLE_DIR = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi"
FRAME_SIZE = 256
GRID_COLUMNS = 5
GRID_ROWS = 5
SPRITESHEET_SIZE = (GRID_COLUMNS * FRAME_SIZE, GRID_ROWS * FRAME_SIZE)
FINAL_SIZE = (FRAME_SIZE, FRAME_SIZE)


def read_png_size(path: Path) -> tuple[int, int]:
    data = path.read_bytes()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError(f"{path} is not a PNG")
    if data[12:16] != b"IHDR":
        raise ValueError(f"{path} is missing IHDR")
    width, height = struct.unpack(">II", data[16:24])
    return width, height


def collect_paths(pattern: str) -> list[Path]:
    return sorted(DRAWABLE_DIR.glob(pattern))


def verify_group(paths: list[Path], expected_size: tuple[int, int], label: str) -> list[str]:
    errors: list[str] = []
    for path in paths:
        size = read_png_size(path)
        if size != expected_size:
            errors.append(f"{label} {path.name}: expected {expected_size[0]}x{expected_size[1]}, got {size[0]}x{size[1]}")
    return errors


def main() -> int:
    spritesheets = collect_paths("bloom_*_spritesheet.png")
    finals = collect_paths("bloom_*_final.png")
    if not spritesheets:
        print("No bloom spritesheets found.", file=sys.stderr)
        return 1

    errors = []
    errors.extend(verify_group(spritesheets, SPRITESHEET_SIZE, "spritesheet"))
    errors.extend(verify_group(finals, FINAL_SIZE, "final"))

    if errors:
        print("Modern grid verification failed:", file=sys.stderr)
        for error in errors:
            print(f"  - {error}", file=sys.stderr)
        return 1

    print(
        "Modern grid verification passed: "
        f"{len(spritesheets)} spritesheets at {SPRITESHEET_SIZE[0]}x{SPRITESHEET_SIZE[1]}"
        f" and {len(finals)} final stills at {FINAL_SIZE[0]}x{FINAL_SIZE[1]}."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
