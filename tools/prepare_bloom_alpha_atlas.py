"""Normalize native-alpha generation without keying away colored petals.

Art is supplied by imagegen. This tool only slices, applies ONE common scale,
registers the base, and clears nearly transparent matte noise. It does not
draw, replace, fill, interpolate, or reorder anatomy.
"""
from __future__ import annotations

import argparse
import json
import math
from pathlib import Path

import numpy as np
from PIL import Image

FRAME_SIZE = 256
BASELINE = 232
PADDING = 24
ALPHA_THRESHOLD = 24


def frame_anchor(frame: Image.Image) -> tuple[float, tuple[int, int, int, int]]:
    alpha = np.asarray(frame.getchannel('A'))
    ys, xs = np.where(alpha >= ALPHA_THRESHOLD)
    if not len(xs):
        raise ValueError('Empty frame')
    left, top, right, bottom = int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1
    band_top = max(top, bottom - 32)
    weights = alpha[band_top:bottom, left:right].astype(np.float64)
    weights[weights < ALPHA_THRESHOLD] = 0
    anchor = float((weights.sum(axis=0) * np.arange(left, right)).sum() / weights.sum())
    return anchor, (left, top, right, bottom)


def recover_edges(alpha: np.ndarray, axis: int) -> list[int]:
    """Slice only through empty gaps near the five nominal cell boundaries.

    Imagegen sometimes spaces rows unevenly. Equal-height slicing then cuts a
    whole bean into two cells. Recovering an empty separator keeps the complete
    supplied art; it never deletes components or changes their ownership.
    """
    extent = alpha.shape[axis]
    occupied = (alpha >= ALPHA_THRESHOLD).any(axis=1 - axis)
    radius = math.ceil(extent / 5 * 0.2)
    edges = [0]
    for index in range(1, 5):
        nominal = round(index * extent / 5)
        candidates = [position for position in range(max(1, nominal - radius),
                      min(extent - 1, nominal + radius) + 1) if not occupied[position]]
        if not candidates:
            raise ValueError(f'No transparent separator near axis {axis} boundary {index}')
        edges.append(min(candidates, key=lambda position: (abs(position - nominal), position)))
    edges.append(extent)
    return edges


def prepare(source: Path, output: Path, preview_dir: Path | None = None) -> dict:
    original = Image.open(source)
    if original.mode != 'RGBA':
        raise ValueError(f'{source}: expected generated RGBA source, got {original.mode}')
    image = original.convert('RGBA')
    if image.width != image.height:
        raise ValueError(f'{source}: source atlas must be square, got {image.size}')
    data = np.array(image)
    if not np.any(data[:, :, 3] < ALPHA_THRESHOLD):
        raise ValueError(f'{source}: no native transparency; refusing to key artwork by color')
    data[data[:, :, 3] < ALPHA_THRESHOLD] = 0
    image = Image.fromarray(data)
    alpha = data[:, :, 3]
    if any(np.any(edge >= ALPHA_THRESHOLD) for edge in
           (alpha[0], alpha[-1], alpha[:, 0], alpha[:, -1])):
        raise ValueError(f'{source}: artwork touches the source canvas edge; regenerate clipped art')
    xs = recover_edges(alpha, axis=1)
    ys = recover_edges(alpha, axis=0)
    frames = [image.crop((xs[i % 5], ys[i // 5], xs[i % 5 + 1], ys[i // 5 + 1]))
              for i in range(25)]
    infos = [frame_anchor(f) for f in frames]
    available_height = BASELINE - PADDING + 1
    # Work in native pixels throughout. Differently sized recovered cells must
    # not be independently resized: that would introduce false growth/shrinkage.
    scales = [FRAME_SIZE / (image.width / 5)]
    for anchor, (left, top, right, bottom) in infos:
        scales.extend([available_height / (bottom - top),
                       (128 - PADDING) / max(1, anchor - left),
                       (FRAME_SIZE - PADDING - 128) / max(1, right - anchor)])
    scale = min(scales)
    atlas = Image.new('RGBA', (1280, 1280))
    records = []
    for i, (frame, (anchor, box)) in enumerate(zip(frames, infos)):
        content = frame.crop(box)
        size = (max(1, math.floor(content.width * scale)), max(1, math.floor(content.height * scale)))
        content = content.resize(size, Image.Resampling.LANCZOS)
        # Scaling is uniform for the whole sheet. Local rounding is bounded.
        x = round(128 - (anchor - box[0]) * scale)
        y = BASELINE - content.height + 1
        cell = Image.new('RGBA', (256, 256))
        cell.alpha_composite(content, (x, y))
        pixels = np.array(cell)
        pixels[pixels[:, :, 3] < ALPHA_THRESHOLD] = 0
        cell = Image.fromarray(pixels)
        _, bounds = frame_anchor(cell)
        if bounds[0] < PADDING - 1 or bounds[1] < PADDING - 1 or bounds[2] > 256 - PADDING + 1:
            raise ValueError(f'{source}: unsafe registered bounds F{i+1}: {bounds}')
        atlas.alpha_composite(cell, ((i % 5) * 256, (i // 5) * 256))
        records.append({'frame': i + 1, 'bounds': list(bounds), 'source_anchor': round(anchor, 3)})
    output.parent.mkdir(parents=True, exist_ok=True)
    atlas.save(output)
    report = {'source': str(source), 'output': str(output), 'source_size': list(image.size),
              'frame_count': 25, 'common_scale': scale, 'baseline': BASELINE,
              'padding': PADDING, 'alpha_threshold': ALPHA_THRESHOLD,
              'source_column_edges': xs, 'source_row_edges': ys, 'frames': records}
    if preview_dir:
        preview_dir.mkdir(parents=True, exist_ok=True)
        # Opaque composites avoid misleading RGB from near-transparent pixels.
        for theme, color in [('light', '#fff8f5'), ('dark', '#1a1210')]:
            preview = Image.new('RGBA', atlas.size, color)
            preview.alpha_composite(atlas)
            preview.convert('RGB').save(preview_dir / f'{output.stem}_{theme}.png')
        (preview_dir / f'{output.stem}.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', type=Path)
    parser.add_argument('output', type=Path)
    parser.add_argument('--preview-dir', type=Path)
    args = parser.parse_args()
    report = prepare(args.source, args.output, args.preview_dir)
    print(f"Prepared {args.output.name}: 25 frames, common scale {report['common_scale']:.4f}, baseline {BASELINE}, padding {PADDING}")


if __name__ == '__main__':
    main()
