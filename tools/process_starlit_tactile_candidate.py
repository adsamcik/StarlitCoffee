#!/usr/bin/env python3
"""Build and inspect one transparent Starlit Tactile review candidate."""

from __future__ import annotations

import argparse
import hashlib
from pathlib import Path

from PIL import Image


DELIVERY_SIZE = (1024, 768)
PHONE_SIZE = (384, 288)
THEMES = {"light": "#F2E0D5", "dark": "#52443C"}


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def process(alpha_path: Path, webp_path: Path, preview_dir: Path) -> None:
    with Image.open(alpha_path) as source:
        image = source.convert("RGBA")

    if image.size[0] * 3 != image.size[1] * 4:
        raise ValueError(f"input is not exact 4:3: {image.size}")

    alpha = image.getchannel("A")
    corners = [
        alpha.getpixel((0, 0)),
        alpha.getpixel((image.width - 1, 0)),
        alpha.getpixel((0, image.height - 1)),
        alpha.getpixel((image.width - 1, image.height - 1)),
    ]
    if corners != [0, 0, 0, 0]:
        raise ValueError(f"corner alpha must be zero, got {corners}")

    histogram = alpha.histogram()
    transparent = histogram[0]
    partial = sum(histogram[1:255])
    bbox = alpha.getbbox()
    if bbox is None:
        raise ValueError("candidate is fully transparent")

    delivery = image.resize(DELIVERY_SIZE, Image.Resampling.LANCZOS)
    webp_path.parent.mkdir(parents=True, exist_ok=True)
    delivery.save(webp_path, "WEBP", quality=95, method=6)

    preview_dir.mkdir(parents=True, exist_ok=True)
    phone = delivery.resize(PHONE_SIZE, Image.Resampling.LANCZOS)
    for theme, color in THEMES.items():
        background = Image.new("RGBA", PHONE_SIZE, color)
        background.alpha_composite(phone)
        background.convert("RGB").save(
            preview_dir / f"{webp_path.stem}_{theme}.png",
            "PNG",
            optimize=True,
        )
        background.convert("RGB").save(
            preview_dir / f"{webp_path.stem}_{theme}.jpg",
            "JPEG",
            quality=92,
            subsampling=0,
            optimize=True,
        )

    with Image.open(webp_path) as saved:
        if saved.mode != "RGBA" or saved.size != DELIVERY_SIZE:
            raise ValueError(f"unexpected WebP {saved.mode=} {saved.size=}")

    print(f"alpha={alpha_path}")
    print(f"source_size={image.size} bbox={bbox} transparent={transparent} partial={partial}")
    print(f"webp={webp_path} bytes={webp_path.stat().st_size} sha256={sha256(webp_path)}")
    print(f"previews={preview_dir}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("alpha", type=Path)
    parser.add_argument("webp", type=Path)
    parser.add_argument("--preview-dir", type=Path, required=True)
    args = parser.parse_args()
    process(args.alpha, args.webp, args.preview_dir)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
