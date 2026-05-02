#!/usr/bin/env python3
"""Align bloom spritesheet frames around a stable bottom-center anchor."""

from __future__ import annotations

import argparse
from pathlib import Path

from PIL import Image


FRAME_SIZE = 256
FRAME_COLUMNS = 9
FRAME_ROWS = 5


def threshold_alpha(alpha: Image.Image, threshold: int) -> Image.Image:
    return alpha.point(lambda value: 255 if value > threshold else 0)


def lower_anchor_x(alpha: Image.Image, box: tuple[int, int, int, int], threshold: int) -> float:
    left, top, right, bottom = box
    band_top = max(top, bottom - 42)
    total = 0
    weighted = 0
    for y in range(band_top, bottom):
        for x in range(left, right):
            value = alpha.getpixel((x, y))
            if value > threshold:
                total += value
                weighted += x * value
    if total == 0:
        return (left + right - 1) / 2
    return weighted / total


def frame_infos(
    image: Image.Image,
    alpha_threshold: int,
) -> list[tuple[int, int, tuple[int, int, int, int] | None, float]]:
    infos: list[tuple[int, int, tuple[int, int, int, int] | None, float]] = []
    for index in range(FRAME_COLUMNS * FRAME_ROWS):
        x = (index % FRAME_COLUMNS) * FRAME_SIZE
        y = (index // FRAME_COLUMNS) * FRAME_SIZE
        frame = image.crop((x, y, x + FRAME_SIZE, y + FRAME_SIZE))
        alpha = frame.split()[3]
        box = threshold_alpha(alpha, alpha_threshold).getbbox()
        if box is None:
            infos.append((index, 0, None, FRAME_SIZE / 2))
            continue
        anchor = lower_anchor_x(alpha, box, alpha_threshold)
        area = (box[2] - box[0]) * (box[3] - box[1])
        infos.append((index, area, box, anchor))
    return infos


def sheet_scale(
    infos: list[tuple[int, int, tuple[int, int, int, int] | None, float]],
    baseline: int,
    padding: int,
) -> float:
    max_width = 1
    max_height = 1
    for _, _, box, _ in infos:
        if box is None:
            continue
        max_width = max(max_width, box[2] - box[0])
        max_height = max(max_height, box[3] - box[1])
    return min(
        1.0,
        (FRAME_SIZE - padding * 2) / max_width,
        (baseline - padding + 1) / max_height,
    )


def remove_low_alpha(image: Image.Image, threshold: int) -> Image.Image:
    if threshold <= 0:
        return image
    red, green, blue, alpha = image.split()
    alpha = alpha.point(lambda value: 0 if 0 < value < threshold else value)
    return Image.merge("RGBA", (red, green, blue, alpha))


def align_sheet(
    source: Path,
    output: Path,
    target_x: int,
    baseline: int,
    padding: int,
    alpha_threshold: int,
) -> None:
    image = Image.open(source).convert("RGBA")
    expected_size = (FRAME_COLUMNS * FRAME_SIZE, FRAME_ROWS * FRAME_SIZE)
    if image.size != expected_size:
        raise ValueError(f"{source} must be {expected_size}, got {image.size}")

    infos = frame_infos(image, alpha_threshold)
    scale = sheet_scale(infos, baseline, padding)
    output_image = Image.new("RGBA", image.size, (0, 0, 0, 0))

    for index, _, box, anchor_x in infos:
        if box is None:
            continue
        source_x = (index % FRAME_COLUMNS) * FRAME_SIZE
        source_y = (index // FRAME_COLUMNS) * FRAME_SIZE
        frame = image.crop((source_x, source_y, source_x + FRAME_SIZE, source_y + FRAME_SIZE))
        content = frame.crop(box)
        content = remove_low_alpha(content, alpha_threshold)

        local_anchor_x = anchor_x - box[0]
        if scale < 1.0:
            new_size = (
                max(1, round(content.width * scale)),
                max(1, round(content.height * scale)),
            )
            content = content.resize(new_size, Image.Resampling.LANCZOS)
            local_anchor_x *= scale

        dest_x = round(target_x - local_anchor_x)
        dest_y = baseline - content.height + 1
        dest_x = max(padding, min(FRAME_SIZE - padding - content.width, dest_x))
        dest_y = max(padding, min(FRAME_SIZE - padding - content.height, dest_y))

        target = Image.new("RGBA", (FRAME_SIZE, FRAME_SIZE), (0, 0, 0, 0))
        target.alpha_composite(content, (dest_x, dest_y))
        output_image.alpha_composite(target, (source_x, source_y))

    output.parent.mkdir(parents=True, exist_ok=True)
    output_image.save(output)
    print(f"{source.name}: aligned -> {output} (scale={scale:.3f})")


def parse_pair(pair: str) -> tuple[Path, Path]:
    if "=>" in pair:
        source_text, output_text = pair.split("=>", maxsplit=1)
    elif ":" in pair:
        source_text, output_text = pair.split(":", maxsplit=1)
    else:
        raise ValueError(f"Pair must be source.png:output.png or source.png=>output.png, got {pair!r}")
    return Path(source_text), Path(output_text)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("pairs", nargs="+", help="Pairs in the form source.png:output.png")
    parser.add_argument("--target-x", type=int, default=128)
    parser.add_argument("--baseline", type=int, default=244)
    parser.add_argument("--padding", type=int, default=10)
    parser.add_argument("--alpha-threshold", type=int, default=24)
    args = parser.parse_args()

    for pair in args.pairs:
        source, output = parse_pair(pair)
        align_sheet(
            source=source,
            output=output,
            target_x=args.target_x,
            baseline=args.baseline,
            padding=args.padding,
            alpha_threshold=args.alpha_threshold,
        )


if __name__ == "__main__":
    main()
