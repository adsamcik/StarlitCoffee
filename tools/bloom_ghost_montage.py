#!/usr/bin/env python3
"""Render a "ghost-of-next-frame" validation montage for a bloom spritesheet.

Each cell shows the current frame at full opacity composited with the next
frame at reduced opacity. If frames are well-anchored, the ghost lines up on
top of the current frame and you see a single coherent shape with a faint
"future state" outline. If frames drift, the ghost appears as a visibly
shifted double image.

Two output modes:
  --mode ghost  (default): per-frame ghost composite, laid out as a
                montage matching the source grid.
  --mode diff   : current frame's alpha as red, next frame's alpha as
                cyan. Drift shows as colored fringing instead of a
                cleanly-overlapping neutral gray.

Optionally, --reference PATH renders a SECOND montage from a reference
sheet (e.g. the pre-alignment version pulled from git) and stacks it
side-by-side with the current one for a direct before/after comparison.
"""

from __future__ import annotations

import argparse
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont


FRAME_SIZE = 256


def slice_frames(img: Image.Image, cols: int, rows: int) -> list[Image.Image]:
    return [
        img.crop((c * FRAME_SIZE, r * FRAME_SIZE,
                  c * FRAME_SIZE + FRAME_SIZE, r * FRAME_SIZE + FRAME_SIZE))
        for r in range(rows) for c in range(cols)
    ]


def fade_alpha(frame: Image.Image, factor: float) -> Image.Image:
    r, g, b, a = frame.split()
    faded = a.point(lambda v: int(v * factor))
    return Image.merge("RGBA", (r, g, b, faded))


def make_ghost_montage(
    sheet_path: Path,
    cols: int,
    rows: int,
    ghost_alpha: float,
    show_baseline: bool,
) -> Image.Image:
    img = Image.open(sheet_path).convert("RGBA")
    if (img.width, img.height) != (cols * FRAME_SIZE, rows * FRAME_SIZE):
        raise ValueError(
            f"{sheet_path} is {img.width}x{img.height}, "
            f"expected {cols * FRAME_SIZE}x{rows * FRAME_SIZE}"
        )
    frames = slice_frames(img, cols, rows)
    background_grid = Image.new("RGBA", img.size, (32, 32, 32, 255))
    out = background_grid.copy()
    for idx, frame in enumerate(frames):
        cell = Image.new("RGBA", (FRAME_SIZE, FRAME_SIZE), (0, 0, 0, 0))
        cell.alpha_composite(frame)
        if idx + 1 < len(frames):
            cell.alpha_composite(fade_alpha(frames[idx + 1], ghost_alpha))
        col = idx % cols
        row = idx // cols
        out.paste(cell, (col * FRAME_SIZE, row * FRAME_SIZE))
    draw = ImageDraw.Draw(out)
    # Frame gridlines so cells are visible against dark content.
    for c in range(cols + 1):
        x = c * FRAME_SIZE
        draw.line([(x, 0), (x, img.height)], fill=(255, 128, 0, 200), width=1)
    for r in range(rows + 1):
        y = r * FRAME_SIZE
        draw.line([(0, y), (img.width, y)], fill=(255, 128, 0, 200), width=1)
    # Optional: a horizontal reference line at the alignment-tool baseline
    # (y=244 within each cell) so eyeballing baseline drift is trivial.
    if show_baseline:
        for r in range(rows):
            y = r * FRAME_SIZE + 244
            for c in range(cols):
                x_start = c * FRAME_SIZE
                draw.line(
                    [(x_start, y), (x_start + FRAME_SIZE, y)],
                    fill=(0, 220, 220, 160),
                    width=1,
                )
    return out


def make_diff_montage(
    sheet_path: Path,
    cols: int,
    rows: int,
) -> Image.Image:
    img = Image.open(sheet_path).convert("RGBA")
    frames = slice_frames(img, cols, rows)
    out = Image.new("RGBA", img.size, (24, 24, 24, 255))
    for idx, frame in enumerate(frames):
        cur_alpha = np.array(frame.split()[3])
        next_alpha = (
            np.array(frames[idx + 1].split()[3])
            if idx + 1 < len(frames)
            else np.zeros_like(cur_alpha)
        )
        # Red channel = current, Cyan channel = next. Overlap = white-ish.
        rgba = np.zeros((FRAME_SIZE, FRAME_SIZE, 4), dtype=np.uint8)
        rgba[:, :, 0] = cur_alpha          # R from current
        rgba[:, :, 1] = next_alpha         # G from next
        rgba[:, :, 2] = next_alpha         # B from next (→ cyan)
        rgba[:, :, 3] = np.maximum(cur_alpha, next_alpha)
        cell = Image.fromarray(rgba, "RGBA")
        col = idx % cols
        row = idx // cols
        out.paste(cell, (col * FRAME_SIZE, row * FRAME_SIZE))
    return out


def label_montage(img: Image.Image, label: str) -> Image.Image:
    """Stack a label strip above the montage."""
    strip_h = 36
    out = Image.new("RGBA", (img.width, img.height + strip_h), (16, 16, 16, 255))
    draw = ImageDraw.Draw(out)
    try:
        font = ImageFont.truetype("arial.ttf", 22)
    except OSError:
        font = ImageFont.load_default()
    draw.text((12, 6), label, fill=(240, 240, 240, 255), font=font)
    out.paste(img, (0, strip_h))
    return out


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("sheet", type=Path, help="Path to spritesheet to validate")
    parser.add_argument("--reference", type=Path, default=None,
                        help="Optional reference sheet to render side-by-side")
    parser.add_argument("--output", type=Path, required=True,
                        help="Output PNG path")
    parser.add_argument("--columns", type=int, default=5)
    parser.add_argument("--rows", type=int, default=5)
    parser.add_argument("--ghost-alpha", type=float, default=0.45)
    parser.add_argument("--mode", choices=("ghost", "diff"), default="ghost")
    parser.add_argument("--no-baseline", action="store_true",
                        help="Hide the y=244 baseline reference line (ghost mode only)")
    parser.add_argument("--label", default="current")
    parser.add_argument("--reference-label", default="reference")
    args = parser.parse_args()

    def render(path: Path) -> Image.Image:
        if args.mode == "ghost":
            return make_ghost_montage(
                path, args.columns, args.rows,
                ghost_alpha=args.ghost_alpha,
                show_baseline=not args.no_baseline,
            )
        return make_diff_montage(path, args.columns, args.rows)

    current = label_montage(render(args.sheet), args.label)
    if args.reference is not None:
        reference = label_montage(render(args.reference), args.reference_label)
        gap = 12
        combined = Image.new(
            "RGBA",
            (reference.width + gap + current.width,
             max(reference.height, current.height)),
            (16, 16, 16, 255),
        )
        combined.paste(reference, (0, 0))
        combined.paste(current, (reference.width + gap, 0))
        out = combined
    else:
        out = current

    args.output.parent.mkdir(parents=True, exist_ok=True)
    out.save(args.output)
    print(f"wrote {args.output}")


if __name__ == "__main__":
    main()
