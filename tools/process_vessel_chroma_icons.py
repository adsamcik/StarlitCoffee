#!/usr/bin/env python3
"""Convert image-generated chroma vessel icons into app-sized PNG candidates.

The built-in image generator does not produce true alpha. This tool handles the
project-specific post-processing step without external image dependencies:

- sample/remove a cyan chroma background, including imperfect gradients,
- despill cyan edge contamination,
- drop tiny matte artifacts,
- crop to the subject,
- scale onto a 256 x 256 transparent canvas with stable bottom padding,
- write small comparison sheets against current app icons.
"""

from __future__ import annotations

import argparse
import json
import math
from dataclasses import dataclass
from pathlib import Path

from process_bloom_imagegen_spritesheets import read_png, write_png


CANVAS_SIZE = 256


@dataclass(frozen=True)
class Bounds:
    left: int
    top: int
    right: int
    bottom: int

    @property
    def width(self) -> int:
        return self.right - self.left

    @property
    def height(self) -> int:
        return self.bottom - self.top

    @property
    def center_x(self) -> float:
        return (self.left + self.right - 1) / 2

    @property
    def center_y(self) -> float:
        return (self.top + self.bottom - 1) / 2

    def to_json(self) -> dict[str, float | int]:
        return {
            "left": self.left,
            "top": self.top,
            "right": self.right,
            "bottom": self.bottom,
            "width": self.width,
            "height": self.height,
            "centerX": round(self.center_x, 2),
            "centerY": round(self.center_y, 2),
        }


def pixel_offset(width: int, x: int, y: int) -> int:
    return (y * width + x) * 4


def clamp(value: float, low: float, high: float) -> float:
    return max(low, min(high, value))


def smoothstep(value: float) -> float:
    value = clamp(value, 0.0, 1.0)
    return value * value * (3.0 - 2.0 * value)


def channel_distance(color: tuple[int, int, int], key: tuple[int, int, int]) -> int:
    return max(abs(color[index] - key[index]) for index in range(3))


def median(values: list[int]) -> int:
    if not values:
        return 0
    ordered = sorted(values)
    middle = len(ordered) // 2
    if len(ordered) % 2:
        return ordered[middle]
    return round((ordered[middle - 1] + ordered[middle]) / 2)


def border_key(width: int, height: int, pixels: bytearray) -> tuple[int, int, int]:
    band = max(4, min(width, height) // 96)
    step = max(1, min(width, height) // 320)
    samples: list[tuple[int, int, int]] = []

    def add(x: int, y: int) -> None:
        offset = pixel_offset(width, x, y)
        samples.append((pixels[offset], pixels[offset + 1], pixels[offset + 2]))

    for x in range(0, width, step):
        for y in range(band):
            add(x, y)
            add(x, height - 1 - y)
    for y in range(0, height, step):
        for x in range(band):
            add(x, y)
            add(width - 1 - x, y)

    return (
        median([sample[0] for sample in samples]),
        median([sample[1] for sample in samples]),
        median([sample[2] for sample in samples]),
    )


def spill_channels(key: tuple[int, int, int]) -> list[int]:
    strongest = max(key)
    return [
        index
        for index, value in enumerate(key)
        if strongest >= 128 and value >= strongest - 24 and value >= 128
    ]


def key_dominance(color: tuple[int, int, int], key: tuple[int, int, int]) -> float:
    channels = spill_channels(key)
    if not channels:
        return 0.0
    spill = min(color[index] for index in channels)
    non_spill = max(color[index] for index in range(3) if index not in channels)
    return float(spill - non_spill)


def chroma_alpha(
    color: tuple[int, int, int],
    key: tuple[int, int, int],
    transparent_threshold: int,
    opaque_threshold: int,
) -> int:
    distance = channel_distance(color, key)
    if distance <= transparent_threshold:
        return 0

    distance_alpha = 255
    if distance < opaque_threshold:
        distance_alpha = round(
            255
            * smoothstep((distance - transparent_threshold) / (opaque_threshold - transparent_threshold))
        )

    dominance = key_dominance(color, key)
    dominance_alpha = 255
    if dominance > 14 and min(color) > 24:
        dominance_alpha = round(255 * (1.0 - smoothstep((dominance - 14.0) / 82.0)))

    return min(distance_alpha, dominance_alpha)


def despill(
    color: tuple[int, int, int],
    key: tuple[int, int, int],
    alpha: int,
) -> tuple[int, int, int]:
    if alpha >= 255:
        return color
    channels = spill_channels(key)
    if not channels:
        return color
    result = [float(value) for value in color]
    non_spill_values = [result[index] for index in range(3) if index not in channels]
    if not non_spill_values:
        return color
    cap = max(0.0, max(non_spill_values) - 1.0)
    strength = 1.0 - alpha / 255.0
    for index in channels:
        if result[index] > cap:
            result[index] = result[index] * (1.0 - strength) + cap * strength
    return (round(result[0]), round(result[1]), round(result[2]))


def remove_chroma(
    width: int,
    height: int,
    pixels: bytearray,
    transparent_threshold: int,
    opaque_threshold: int,
) -> tuple[bytearray, tuple[int, int, int]]:
    key = border_key(width, height, pixels)
    output = bytearray(width * height * 4)
    for y in range(height):
        for x in range(width):
            offset = pixel_offset(width, x, y)
            color = (pixels[offset], pixels[offset + 1], pixels[offset + 2])
            source_alpha = pixels[offset + 3]
            alpha = round(chroma_alpha(color, key, transparent_threshold, opaque_threshold) * source_alpha / 255)
            out_color = (0, 0, 0) if alpha == 0 else despill(color, key, alpha)
            output[offset:offset + 4] = bytes((out_color[0], out_color[1], out_color[2], alpha))
    return output, key


def remove_small_components(width: int, height: int, pixels: bytearray, min_area: int) -> None:
    seen = bytearray(width * height)
    for y in range(height):
        for x in range(width):
            index = y * width + x
            if seen[index] or pixels[index * 4 + 3] == 0:
                continue
            stack = [(x, y)]
            component: list[int] = []
            seen[index] = 1
            while stack:
                cx, cy = stack.pop()
                component_index = cy * width + cx
                component.append(component_index)
                for nx, ny in ((cx - 1, cy), (cx + 1, cy), (cx, cy - 1), (cx, cy + 1)):
                    if nx < 0 or ny < 0 or nx >= width or ny >= height:
                        continue
                    neighbor_index = ny * width + nx
                    if seen[neighbor_index] or pixels[neighbor_index * 4 + 3] == 0:
                        continue
                    seen[neighbor_index] = 1
                    stack.append((nx, ny))
            if len(component) < min_area:
                for component_index in component:
                    pixels[component_index * 4:component_index * 4 + 4] = b"\x00\x00\x00\x00"


def alpha_bounds(width: int, height: int, pixels: bytearray, alpha_threshold: int) -> Bounds | None:
    left = width
    top = height
    right = 0
    bottom = 0
    for y in range(height):
        for x in range(width):
            if pixels[pixel_offset(width, x, y) + 3] > alpha_threshold:
                left = min(left, x)
                top = min(top, y)
                right = max(right, x + 1)
                bottom = max(bottom, y + 1)
    if right <= left or bottom <= top:
        return None
    return Bounds(left, top, right, bottom)


def sample_bilinear(
    width: int,
    height: int,
    pixels: bytearray,
    x: float,
    y: float,
) -> tuple[int, int, int, int]:
    x = clamp(x, 0.0, width - 1.0)
    y = clamp(y, 0.0, height - 1.0)
    x0 = int(math.floor(x))
    y0 = int(math.floor(y))
    x1 = min(width - 1, x0 + 1)
    y1 = min(height - 1, y0 + 1)
    tx = x - x0
    ty = y - y0

    accum_a = 0.0
    accum_r = 0.0
    accum_g = 0.0
    accum_b = 0.0
    for sx, wx in ((x0, 1.0 - tx), (x1, tx)):
        for sy, wy in ((y0, 1.0 - ty), (y1, ty)):
            weight = wx * wy
            offset = pixel_offset(width, sx, sy)
            alpha = pixels[offset + 3] / 255.0
            accum_a += alpha * weight
            accum_r += pixels[offset] * alpha * weight
            accum_g += pixels[offset + 1] * alpha * weight
            accum_b += pixels[offset + 2] * alpha * weight

    if accum_a <= 0.0:
        return (0, 0, 0, 0)
    return (
        round(accum_r / accum_a),
        round(accum_g / accum_a),
        round(accum_b / accum_a),
        round(accum_a * 255),
    )


def fit_to_canvas(
    width: int,
    height: int,
    pixels: bytearray,
    bounds: Bounds,
    padding: int,
    baseline: int,
) -> bytearray:
    output = bytearray(CANVAS_SIZE * CANVAS_SIZE * 4)
    scale = min(
        (CANVAS_SIZE - padding * 2) / bounds.width,
        (baseline - padding + 1) / bounds.height,
    )
    target_width = max(1, round(bounds.width * scale))
    target_height = max(1, round(bounds.height * scale))
    dest_x = round((CANVAS_SIZE - target_width) / 2)
    dest_y = baseline - target_height + 1

    for y in range(target_height):
        src_y = bounds.top + (y + 0.5) / scale - 0.5
        for x in range(target_width):
            src_x = bounds.left + (x + 0.5) / scale - 0.5
            color = sample_bilinear(width, height, pixels, src_x, src_y)
            offset = pixel_offset(CANVAS_SIZE, dest_x + x, dest_y + y)
            output[offset:offset + 4] = bytes(color)
    return output


def downsample(width: int, height: int, pixels: bytearray, size: int) -> bytearray:
    if width == size and height == size:
        return bytearray(pixels)
    output = bytearray(size * size * 4)
    scale_x = width / size
    scale_y = height / size
    for y in range(size):
        for x in range(size):
            color = sample_bilinear(width, height, pixels, (x + 0.5) * scale_x - 0.5, (y + 0.5) * scale_y - 0.5)
            offset = pixel_offset(size, x, y)
            output[offset:offset + 4] = bytes(color)
    return output


def paste_rgba(
    target_width: int,
    target_pixels: bytearray,
    image_width: int,
    image_height: int,
    image_pixels: bytearray,
    dest_x: int,
    dest_y: int,
) -> None:
    for y in range(image_height):
        for x in range(image_width):
            src = pixel_offset(image_width, x, y)
            alpha = image_pixels[src + 3]
            if alpha == 0:
                continue
            dst = pixel_offset(target_width, dest_x + x, dest_y + y)
            inv_alpha = 255 - alpha
            target_pixels[dst] = (image_pixels[src] * alpha + target_pixels[dst] * inv_alpha) // 255
            target_pixels[dst + 1] = (
                image_pixels[src + 1] * alpha + target_pixels[dst + 1] * inv_alpha
            ) // 255
            target_pixels[dst + 2] = (
                image_pixels[src + 2] * alpha + target_pixels[dst + 2] * inv_alpha
            ) // 255
            target_pixels[dst + 3] = 255


def read_resized(path: Path, size: int) -> bytearray:
    width, height, pixels = read_png(path)
    return downsample(width, height, pixels, size)


def visible_counts(pixels: bytearray) -> dict[str, int]:
    transparent = 0
    partial = 0
    opaque = 0
    for index in range(3, len(pixels), 4):
        alpha = pixels[index]
        if alpha == 0:
            transparent += 1
        elif alpha == 255:
            opaque += 1
        else:
            partial += 1
    return {"transparent": transparent, "partial": partial, "opaque": opaque}


def quantize_channel(value: int, step: int) -> int:
    return max(0, min(255, round(value / step) * step))


def quantize_visible_colors(pixels: bytearray, step: int) -> None:
    if step <= 1:
        return
    for offset in range(0, len(pixels), 4):
        if pixels[offset + 3] == 0:
            continue
        pixels[offset] = quantize_channel(pixels[offset], step)
        pixels[offset + 1] = quantize_channel(pixels[offset + 1], step)
        pixels[offset + 2] = quantize_channel(pixels[offset + 2], step)


def process_icon(
    name: str,
    source: Path,
    output_dir: Path,
    padding: int,
    baseline: int,
    transparent_threshold: int,
    opaque_threshold: int,
    min_component_area: int,
    color_step: int,
) -> dict[str, object]:
    width, height, pixels = read_png(source)
    chroma_removed, key = remove_chroma(width, height, pixels, transparent_threshold, opaque_threshold)
    remove_small_components(width, height, chroma_removed, min_component_area)
    source_bounds = alpha_bounds(width, height, chroma_removed, alpha_threshold=8)
    if source_bounds is None:
        raise ValueError(f"No visible subject remained after chroma removal for {source}")

    master_path = output_dir / f"{name}_chroma_removed_master.png"
    candidate_path = output_dir / f"{name}_candidate_256.png"
    write_png(master_path, width, height, chroma_removed)

    candidate = fit_to_canvas(width, height, chroma_removed, source_bounds, padding, baseline)
    remove_small_components(CANVAS_SIZE, CANVAS_SIZE, candidate, max(4, min_component_area // 16))
    quantize_visible_colors(candidate, color_step)
    candidate_bounds = alpha_bounds(CANVAS_SIZE, CANVAS_SIZE, candidate, alpha_threshold=8)
    write_png(candidate_path, CANVAS_SIZE, CANVAS_SIZE, candidate)

    return {
        "name": name,
        "source": str(source),
        "sourceSize": {"width": width, "height": height},
        "sampledKey": f"#{key[0]:02x}{key[1]:02x}{key[2]:02x}",
        "master": str(master_path),
        "candidate": str(candidate_path),
        "candidateBytes": candidate_path.stat().st_size,
        "colorStep": color_step,
        "sourceBounds": source_bounds.to_json(),
        "candidateBounds": candidate_bounds.to_json() if candidate_bounds else None,
        "candidateAlpha": visible_counts(candidate),
    }


def comparison_sheet(
    results: list[dict[str, object]],
    current_icons: dict[str, Path],
    output_dir: Path,
    size: int,
) -> Path:
    columns = 2
    rows = len(results)
    gutter = max(12, size // 3)
    cell = size + gutter * 2
    width = columns * cell
    height = rows * cell
    sheet = bytearray()
    for _ in range(width * height):
        sheet.extend((246, 244, 240, 255))

    for row, result in enumerate(results):
        name = str(result["name"])
        paths = [current_icons.get(name), Path(str(result["candidate"]))]
        for column, path in enumerate(paths):
            if path is None:
                continue
            icon = read_resized(path, size)
            paste_rgba(
                target_width=width,
                target_pixels=sheet,
                image_width=size,
                image_height=size,
                image_pixels=icon,
                dest_x=column * cell + gutter,
                dest_y=row * cell + gutter,
            )

    output = output_dir / f"chroma_regen_contact_sheet_{size}.png"
    write_png(output, width, height, sheet)
    return output


def keyboard_button_preview(
    results: list[dict[str, object]],
    current_icons: dict[str, Path],
    output_dir: Path,
) -> Path:
    icon_size = 26
    button_size = 52
    gap = 12
    padding = 20
    row_gap = 18
    columns = len(results)
    rows = 2 if current_icons else 1
    width = padding * 2 + columns * button_size + (columns - 1) * gap
    height = padding * 2 + rows * button_size + (rows - 1) * row_gap
    sheet = bytearray()
    for _ in range(width * height):
        sheet.extend((250, 248, 255, 255))

    colors = [
        (172, 194, 245, 255),
        (219, 226, 249, 255),
        (226, 194, 246, 255),
        (172, 194, 245, 255),
        (219, 226, 249, 255),
    ]

    def draw_circle(cx: int, cy: int, radius: int, color: tuple[int, int, int, int]) -> None:
        r2 = radius * radius
        for y in range(cy - radius, cy + radius + 1):
            if y < 0 or y >= height:
                continue
            for x in range(cx - radius, cx + radius + 1):
                if x < 0 or x >= width:
                    continue
                dx = x - cx
                dy = y - cy
                if dx * dx + dy * dy <= r2:
                    offset = pixel_offset(width, x, y)
                    sheet[offset:offset + 4] = bytes(color)

    def draw_row(row: int, paths: list[Path | None]) -> None:
        top = padding + row * (button_size + row_gap)
        for column, path in enumerate(paths):
            left = padding + column * (button_size + gap)
            draw_circle(
                cx=left + button_size // 2,
                cy=top + button_size // 2,
                radius=button_size // 2,
                color=colors[column % len(colors)],
            )
            if path is None:
                continue
            icon = read_resized(path, icon_size)
            paste_rgba(
                target_width=width,
                target_pixels=sheet,
                image_width=icon_size,
                image_height=icon_size,
                image_pixels=icon,
                dest_x=left + (button_size - icon_size) // 2,
                dest_y=top + (button_size - icon_size) // 2,
            )

    if current_icons:
        draw_row(0, [current_icons.get(str(result["name"])) for result in results])
        draw_row(1, [Path(str(result["candidate"])) for result in results])
    else:
        draw_row(0, [Path(str(result["candidate"])) for result in results])

    output = output_dir / "keyboard_button_preview.png"
    write_png(output, width, height, sheet)
    return output


def parse_named_path(raw: str) -> tuple[str, Path]:
    if "=" not in raw:
        raise argparse.ArgumentTypeError(f"Expected name=path.png, got {raw!r}")
    name, path = raw.split("=", maxsplit=1)
    return name, Path(path)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--padding", type=int, default=24)
    parser.add_argument("--baseline", type=int, default=228)
    parser.add_argument("--transparent-threshold", type=int, default=28)
    parser.add_argument("--opaque-threshold", type=int, default=132)
    parser.add_argument("--min-component-area", type=int, default=80)
    parser.add_argument(
        "--color-step",
        type=int,
        default=12,
        help="Posterize visible RGB channels to this step size; use 1 to disable.",
    )
    parser.add_argument("--current", action="append", default=[], help="Optional name=current.png")
    parser.add_argument("sources", nargs="+", help="name=source.png")
    args = parser.parse_args()

    args.out.mkdir(parents=True, exist_ok=True)
    results = [
        process_icon(
            name=name,
            source=source,
            output_dir=args.out,
            padding=args.padding,
            baseline=args.baseline,
            transparent_threshold=args.transparent_threshold,
            opaque_threshold=args.opaque_threshold,
            min_component_area=args.min_component_area,
            color_step=args.color_step,
        )
        for name, source in (parse_named_path(item) for item in args.sources)
    ]

    current_icons = dict(parse_named_path(item) for item in args.current)
    if current_icons:
        for size in (256, 64, 32):
            comparison_sheet(results, current_icons, args.out, size)
        keyboard_button_preview(results, current_icons, args.out)

    report = args.out / "chroma_regeneration_report.json"
    report.write_text(json.dumps(results, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(results, indent=2))


if __name__ == "__main__":
    main()
