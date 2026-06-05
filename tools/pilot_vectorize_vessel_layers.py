#!/usr/bin/env python3
"""Build inspection artifacts for image-generated vessel icon layer atlases.

This is intentionally a pilot tool, not a production vectorizer. It answers two
questions:

1. Do image-generated layers stay aligned well enough to be recombined?
2. How large does a naive color-run SVG become compared with the current PNGs?
"""

from __future__ import annotations

import argparse
import html
import json
from dataclasses import dataclass
from pathlib import Path

from process_bloom_imagegen_spritesheets import read_png, write_png


CANVAS_SIZE = 256
GRID_TRIM_PX = 8
MIN_COMPONENT_AREA = 12


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


def offset(width: int, x: int, y: int) -> int:
    return (y * width + x) * 4


def is_guide_or_background(r: int, g: int, b: int) -> bool:
    magenta_grid = r > 120 and g < 120 and b > 120 and r + b > g * 3
    green_background = g > 100 and g > r * 1.25 and g > b * 1.25
    return magenta_grid or green_background


def clean_cell(
    source_width: int,
    source_pixels: bytearray,
    cell_box: tuple[int, int, int, int],
) -> bytearray:
    left, top, right, bottom = cell_box
    cell_width = right - left
    cell_height = bottom - top
    out = bytearray(CANVAS_SIZE * CANVAS_SIZE * 4)
    x_scale = cell_width / CANVAS_SIZE
    y_scale = cell_height / CANVAS_SIZE

    for y in range(CANVAS_SIZE):
        src_y = min(cell_height - 1, int((y + 0.5) * y_scale))
        for x in range(CANVAS_SIZE):
            src_x = min(cell_width - 1, int((x + 0.5) * x_scale))
            src = offset(source_width, left + src_x, top + src_y)
            r, g, b, a = source_pixels[src:src + 4]
            dst = offset(CANVAS_SIZE, x, y)
            in_grid_trim = (
                src_x < GRID_TRIM_PX
                or src_y < GRID_TRIM_PX
                or src_x >= cell_width - GRID_TRIM_PX
                or src_y >= cell_height - GRID_TRIM_PX
            )
            if in_grid_trim or a == 0 or is_guide_or_background(r, g, b):
                out[dst:dst + 4] = b"\x00\x00\x00\x00"
            else:
                out[dst:dst + 4] = bytes((r, g, b, 255))
    remove_small_components(out, MIN_COMPONENT_AREA)
    return out


def remove_small_components(pixels: bytearray, min_area: int) -> None:
    seen = bytearray(CANVAS_SIZE * CANVAS_SIZE)
    for y in range(CANVAS_SIZE):
        for x in range(CANVAS_SIZE):
            index = y * CANVAS_SIZE + x
            if seen[index] or pixels[index * 4 + 3] == 0:
                continue
            stack = [(x, y)]
            component: list[int] = []
            seen[index] = 1
            while stack:
                cx, cy = stack.pop()
                component_index = cy * CANVAS_SIZE + cx
                component.append(component_index)
                for nx, ny in ((cx - 1, cy), (cx + 1, cy), (cx, cy - 1), (cx, cy + 1)):
                    if nx < 0 or ny < 0 or nx >= CANVAS_SIZE or ny >= CANVAS_SIZE:
                        continue
                    neighbor_index = ny * CANVAS_SIZE + nx
                    if seen[neighbor_index] or pixels[neighbor_index * 4 + 3] == 0:
                        continue
                    seen[neighbor_index] = 1
                    stack.append((nx, ny))
            if len(component) < min_area:
                for component_index in component:
                    pixels[component_index * 4:component_index * 4 + 4] = b"\x00\x00\x00\x00"


def alpha_bounds(pixels: bytearray, alpha_threshold: int = 0) -> Bounds | None:
    left = CANVAS_SIZE
    top = CANVAS_SIZE
    right = 0
    bottom = 0
    for y in range(CANVAS_SIZE):
        for x in range(CANVAS_SIZE):
            if pixels[offset(CANVAS_SIZE, x, y) + 3] > alpha_threshold:
                left = min(left, x)
                top = min(top, y)
                right = max(right, x + 1)
                bottom = max(bottom, y + 1)
    if right <= left or bottom <= top:
        return None
    return Bounds(left, top, right, bottom)


def visible_pixel_count(pixels: bytearray) -> int:
    return sum(1 for index in range(3, len(pixels), 4) if pixels[index] > 0)


def alpha_composite(base: bytearray, layer: bytearray) -> None:
    for index in range(0, len(base), 4):
        alpha = layer[index + 3]
        if alpha == 0:
            continue
        base[index:index + 4] = layer[index:index + 4]


def resized_rgba(width: int, height: int, pixels: bytearray, size: int) -> bytearray:
    if width == size and height == size:
        return bytearray(pixels)
    out = bytearray(size * size * 4)
    x_scale = width / size
    y_scale = height / size
    for y in range(size):
        y0 = int(y * y_scale)
        y1 = max(y0 + 1, int((y + 1) * y_scale))
        for x in range(size):
            x0 = int(x * x_scale)
            x1 = max(x0 + 1, int((x + 1) * x_scale))
            total_a = 0
            total_r = 0
            total_g = 0
            total_b = 0
            samples = 0
            for sy in range(y0, min(height, y1)):
                for sx in range(x0, min(width, x1)):
                    src = offset(width, sx, sy)
                    alpha = pixels[src + 3]
                    total_a += alpha
                    total_r += pixels[src] * alpha
                    total_g += pixels[src + 1] * alpha
                    total_b += pixels[src + 2] * alpha
                    samples += 1
            dst = offset(size, x, y)
            if total_a == 0:
                out[dst:dst + 4] = b"\x00\x00\x00\x00"
            else:
                out[dst] = round(total_r / total_a)
                out[dst + 1] = round(total_g / total_a)
                out[dst + 2] = round(total_b / total_a)
                out[dst + 3] = round(total_a / samples)
    return out


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
            src = offset(image_width, x, y)
            alpha = image_pixels[src + 3]
            if alpha == 0:
                continue
            dst = offset(target_width, dest_x + x, dest_y + y)
            inv_alpha = 255 - alpha
            target_pixels[dst] = (image_pixels[src] * alpha + target_pixels[dst] * inv_alpha) // 255
            target_pixels[dst + 1] = (
                image_pixels[src + 1] * alpha + target_pixels[dst + 1] * inv_alpha
            ) // 255
            target_pixels[dst + 2] = (
                image_pixels[src + 2] * alpha + target_pixels[dst + 2] * inv_alpha
            ) // 255
            target_pixels[dst + 3] = 255


def image_from_path(path: Path, size: int) -> bytearray:
    width, height, pixels = read_png(path)
    return resized_rgba(width, height, pixels, size)


def comparison_sheet(
    results: list[dict[str, object]],
    current_icons: dict[str, Path],
    output_dir: Path,
    size: int,
    filename: str,
) -> Path:
    columns = 3
    rows = len(results)
    gutter = 18
    cell = size + gutter * 2
    width = columns * cell
    height = rows * cell
    sheet = bytearray()
    for _ in range(width * height):
        sheet.extend((246, 244, 240, 255))

    for row, result in enumerate(results):
        name = str(result["name"])
        current_path = current_icons.get(name)
        composite_path = Path(result["compositeCell"]["png"])  # type: ignore[index]
        stacked_path = Path(result["stackedLayers"]["png"])  # type: ignore[index]
        paths = [current_path, composite_path, stacked_path]
        for column, path in enumerate(paths):
            if path is None:
                continue
            icon = image_from_path(path, size)
            paste_rgba(
                target_width=width,
                target_pixels=sheet,
                image_width=size,
                image_height=size,
                image_pixels=icon,
                dest_x=column * cell + gutter,
                dest_y=row * cell + gutter,
            )

    output = output_dir / filename
    write_png(output, width, height, sheet)
    return output


def quantize(value: int, step: int) -> int:
    return min(255, max(0, round(value / step) * step))


def svg_for_pixels(name: str, pixels: bytearray, color_step: int) -> tuple[str, int]:
    groups: dict[tuple[int, int, int], list[tuple[int, int, int]]] = {}
    rect_count = 0

    for y in range(CANVAS_SIZE):
        x = 0
        while x < CANVAS_SIZE:
            pix = offset(CANVAS_SIZE, x, y)
            if pixels[pix + 3] == 0:
                x += 1
                continue
            color = (
                quantize(pixels[pix], color_step),
                quantize(pixels[pix + 1], color_step),
                quantize(pixels[pix + 2], color_step),
            )
            start = x
            x += 1
            while x < CANVAS_SIZE:
                next_pix = offset(CANVAS_SIZE, x, y)
                next_color = (
                    quantize(pixels[next_pix], color_step),
                    quantize(pixels[next_pix + 1], color_step),
                    quantize(pixels[next_pix + 2], color_step),
                )
                if pixels[next_pix + 3] == 0 or next_color != color:
                    break
                x += 1
            groups.setdefault(color, []).append((start, y, x - start))
            rect_count += 1

    parts = [
        '<svg xmlns="http://www.w3.org/2000/svg" width="256" height="256" viewBox="0 0 256 256">',
        f'<title>{html.escape(name)}</title>',
    ]
    for color, rects in sorted(groups.items(), key=lambda item: len(item[1]), reverse=True):
        r, g, b = color
        parts.append(f'<g fill="#{r:02x}{g:02x}{b:02x}">')
        for x, y, width in rects:
            parts.append(f'<rect x="{x}" y="{y}" width="{width}" height="1"/>')
        parts.append("</g>")
    parts.append("</svg>")
    return "\n".join(parts) + "\n", rect_count


def cell_boxes(width: int, height: int, columns: int, rows: int) -> list[tuple[int, int, int, int]]:
    return [
        (
            round((index % columns) * width / columns),
            round((index // columns) * height / rows),
            round(((index % columns) + 1) * width / columns),
            round(((index // columns) + 1) * height / rows),
        )
        for index in range(columns * rows)
    ]


def analyze_atlas(
    atlas: Path,
    name: str,
    output_dir: Path,
    color_step: int,
) -> dict[str, object]:
    width, height, pixels = read_png(atlas)
    boxes = cell_boxes(width, height, 3, 2)
    cells = [clean_cell(width, pixels, box) for box in boxes]
    composite_cell = cells[0]
    stacked_layers = bytearray(CANVAS_SIZE * CANVAS_SIZE * 4)
    for layer in cells[1:]:
        alpha_composite(stacked_layers, layer)

    output_dir.mkdir(parents=True, exist_ok=True)
    composite_png = output_dir / f"{name}_composite_cell_extracted.png"
    stacked_png = output_dir / f"{name}_stacked_layers_preview.png"
    composite_svg = output_dir / f"{name}_composite_cell_vectorized.svg"
    stacked_svg = output_dir / f"{name}_stacked_layers_vectorized.svg"

    write_png(composite_png, CANVAS_SIZE, CANVAS_SIZE, composite_cell)
    write_png(stacked_png, CANVAS_SIZE, CANVAS_SIZE, stacked_layers)

    composite_svg_text, composite_rects = svg_for_pixels(
        f"{name} composite cell vectorized",
        composite_cell,
        color_step,
    )
    stacked_svg_text, stacked_rects = svg_for_pixels(
        f"{name} stacked generated layers vectorized",
        stacked_layers,
        color_step,
    )
    composite_svg.write_text(composite_svg_text, encoding="utf-8")
    stacked_svg.write_text(stacked_svg_text, encoding="utf-8")

    composite_bounds = alpha_bounds(composite_cell)
    stacked_bounds = alpha_bounds(stacked_layers)
    layer_metrics: list[dict[str, object]] = []
    reference = composite_bounds or stacked_bounds
    for index, layer in enumerate(cells[1:], start=2):
        bounds = alpha_bounds(layer)
        layer_metric: dict[str, object] = {
            "cell": index,
            "visiblePixels": visible_pixel_count(layer),
            "bounds": bounds.to_json() if bounds else None,
        }
        if bounds and reference:
            layer_metric["centerDriftFromCompositePx"] = {
                "x": round(bounds.center_x - reference.center_x, 2),
                "y": round(bounds.center_y - reference.center_y, 2),
            }
            layer_metric["bottomDriftFromCompositePx"] = bounds.bottom - reference.bottom
        layer_metrics.append(layer_metric)

    return {
        "name": name,
        "source": str(atlas),
        "sourceSize": {"width": width, "height": height},
        "compositeCell": {
            "png": str(composite_png),
            "svg": str(composite_svg),
            "svgBytes": composite_svg.stat().st_size,
            "svgRectCount": composite_rects,
            "visiblePixels": visible_pixel_count(composite_cell),
            "bounds": composite_bounds.to_json() if composite_bounds else None,
        },
        "stackedLayers": {
            "png": str(stacked_png),
            "svg": str(stacked_svg),
            "svgBytes": stacked_svg.stat().st_size,
            "svgRectCount": stacked_rects,
            "visiblePixels": visible_pixel_count(stacked_layers),
            "bounds": stacked_bounds.to_json() if stacked_bounds else None,
        },
        "layers": layer_metrics,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--color-step", type=int, default=24)
    parser.add_argument(
        "--current",
        action="append",
        default=[],
        help="Optional current app icon in the form name=path.png for comparison sheets.",
    )
    parser.add_argument("atlas", nargs="+", help="name=path.png")
    args = parser.parse_args()

    results = []
    for item in args.atlas:
        if "=" not in item:
            raise SystemExit(f"Expected name=path.png, got {item!r}")
        name, path = item.split("=", maxsplit=1)
        results.append(analyze_atlas(Path(path), name, args.out, args.color_step))

    current_icons: dict[str, Path] = {}
    for item in args.current:
        if "=" not in item:
            raise SystemExit(f"Expected name=path.png, got {item!r}")
        name, path = item.split("=", maxsplit=1)
        current_icons[name] = Path(path)

    if current_icons:
        comparison_sheet(
            results,
            current_icons,
            args.out,
            size=256,
            filename="layer_pilot_contact_sheet_256.png",
        )
        comparison_sheet(
            results,
            current_icons,
            args.out,
            size=64,
            filename="layer_pilot_contact_sheet_64.png",
        )
        comparison_sheet(
            results,
            current_icons,
            args.out,
            size=32,
            filename="layer_pilot_contact_sheet_32.png",
        )

    report_path = args.out / "layer_vectorization_report.json"
    report_path.write_text(json.dumps(results, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(results, indent=2))


if __name__ == "__main__":
    main()
