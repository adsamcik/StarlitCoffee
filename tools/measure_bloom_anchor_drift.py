#!/usr/bin/env python3
"""Measure per-frame anchor drift in a bloom spritesheet.

Mirrors the heuristic in BloomSpritesheetAnimation.kt (bounding box → weighted
center of the bottom 42-pixel band → median target → per-frame correction).
Used to compare "before vs after alignment" objectively.

NumPy is used for speed — per-pixel Python loops over a 1280x1280 sheet take
~30 s; the vectorised version finishes in under a second.
"""

from __future__ import annotations

import argparse
import statistics
from pathlib import Path

import numpy as np
from PIL import Image


FRAME_SIZE = 256
ANCHOR_BAND_HEIGHT_PX = 42
ANCHOR_ALPHA_THRESHOLD = 24


def sample_anchor(alpha_frame: np.ndarray):
    """Return (anchorX, baselineY) or None for an empty frame.

    `alpha_frame` is a FRAME_SIZE x FRAME_SIZE uint8 NumPy array.
    Coordinates are local to the frame (0..FRAME_SIZE-1).
    """
    mask = alpha_frame >= ANCHOR_ALPHA_THRESHOLD
    if not mask.any():
        return None
    ys, xs = np.where(mask)
    min_y, max_y = int(ys.min()), int(ys.max())
    min_x, max_x = int(xs.min()), int(xs.max())
    band_top = max(min_y, max_y - ANCHOR_BAND_HEIGHT_PX + 1)
    band = alpha_frame[band_top:max_y + 1, min_x:max_x + 1]
    band_mask = band >= ANCHOR_ALPHA_THRESHOLD
    if not band_mask.any():
        return float(min_x + max_x) / 2.0, max_y
    weights = np.where(band_mask, band.astype(np.float64), 0.0)
    total = weights.sum()
    if total <= 0:
        return float(min_x + max_x) / 2.0, max_y
    xs_local = np.arange(min_x, max_x + 1, dtype=np.float64)
    weighted_x = (weights.sum(axis=0) * xs_local).sum()
    return float(weighted_x / total), max_y


def analyse(path: Path, frame_size: int = FRAME_SIZE) -> dict:
    image = Image.open(path).convert("RGBA")
    width, height = image.size
    if width % frame_size or height % frame_size:
        raise ValueError(
            f"{path} dimensions {width}x{height} not divisible by frame size {frame_size}"
        )
    cols = width // frame_size
    rows = height // frame_size
    arr = np.array(image)  # H × W × 4 uint8 — last channel is alpha
    alpha = arr[:, :, 3]
    anchors_x: list[float] = []
    baselines_y: list[int] = []
    empty = 0
    for idx in range(cols * rows):
        fx = (idx % cols) * frame_size
        fy = (idx // cols) * frame_size
        anchor = sample_anchor(alpha[fy:fy + frame_size, fx:fx + frame_size])
        if anchor is None:
            empty += 1
            continue
        ax, by = anchor
        anchors_x.append(ax)
        baselines_y.append(by)
    if not anchors_x:
        return {
            "cols": cols,
            "rows": rows,
            "frames": cols * rows,
            "empty_frames": empty,
            "anchor_x_range": None,
            "baseline_y_range": None,
            "anchor_x_stdev": None,
            "baseline_y_stdev": None,
            "anchor_x_max_drift": None,
            "baseline_y_max_drift": None,
            "median_anchor_x": None,
            "median_baseline_y": None,
        }
    median_ax = statistics.median(anchors_x)
    median_by = statistics.median(baselines_y)
    return {
        "cols": cols,
        "rows": rows,
        "frames": cols * rows,
        "empty_frames": empty,
        "median_anchor_x": median_ax,
        "median_baseline_y": median_by,
        "anchor_x_range": max(anchors_x) - min(anchors_x),
        "baseline_y_range": max(baselines_y) - min(baselines_y),
        "anchor_x_stdev": statistics.pstdev(anchors_x),
        "baseline_y_stdev": statistics.pstdev(baselines_y),
        "anchor_x_max_drift": max(abs(a - median_ax) for a in anchors_x),
        "baseline_y_max_drift": max(abs(b - median_by) for b in baselines_y),
    }


def fmt(value):
    if value is None:
        return "    n/a"
    if isinstance(value, int):
        return f"{value:>7d}"
    return f"{value:>7.2f}"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("paths", nargs="+", type=Path)
    parser.add_argument("--label", default="state")
    parser.add_argument("--frame-size", type=int, default=FRAME_SIZE)
    args = parser.parse_args()

    print(f"\n=== {args.label} ===")
    print(f"{'sheet':<48} {'grid':>5} "
          f"{'anchorX_drift':>14} {'anchorX_stdev':>14} "
          f"{'baselineY_drift':>16} {'baselineY_stdev':>16}")
    totals = {"anchor_drift": [], "anchor_stdev": [], "baseline_drift": [], "baseline_stdev": []}
    for p in sorted(args.paths):
        m = analyse(p, frame_size=args.frame_size)
        if m["anchor_x_max_drift"] is not None:
            totals["anchor_drift"].append(m["anchor_x_max_drift"])
            totals["anchor_stdev"].append(m["anchor_x_stdev"])
            totals["baseline_drift"].append(m["baseline_y_max_drift"])
            totals["baseline_stdev"].append(m["baseline_y_stdev"])
        grid = f"{m['cols']}x{m['rows']}"
        print(
            f"{p.name:<48} {grid:>5} "
            f"{fmt(m['anchor_x_max_drift'])} "
            f"{fmt(m['anchor_x_stdev'])} "
            f"{fmt(m['baseline_y_max_drift'])} "
            f"{fmt(m['baseline_y_stdev'])}"
        )
    if totals["anchor_drift"]:
        print()
        print(f"{'AVERAGE across sheets':<48} {'':>5} "
              f"{fmt(statistics.mean(totals['anchor_drift']))} "
              f"{fmt(statistics.mean(totals['anchor_stdev']))} "
              f"{fmt(statistics.mean(totals['baseline_drift']))} "
              f"{fmt(statistics.mean(totals['baseline_stdev']))}")
        print(f"{'WORST across sheets':<48} {'':>5} "
              f"{fmt(max(totals['anchor_drift']))} "
              f"{fmt(max(totals['anchor_stdev']))} "
              f"{fmt(max(totals['baseline_drift']))} "
              f"{fmt(max(totals['baseline_stdev']))}")


if __name__ == "__main__":
    main()

