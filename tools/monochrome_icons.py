#!/usr/bin/env python3
"""Reusable contour fitting, Android vector export and target-size fidelity checks.

Used by vectorize_equipment_icons.py; derived from the calculation-icon workflow.
All contours originate in raster pixels. No coordinates are authored by hand.
"""

from __future__ import annotations

import math
import re
import shutil
import subprocess
import xml.etree.ElementTree as ElementTree
from dataclasses import asdict, dataclass
from pathlib import Path

import numpy as np
import vtracer
from PIL import Image, ImageDraw, ImageFont


TRACE_TOLERANCES = (0.1, 0.25, 0.5, 0.75, 1.0, 1.5, 2.0)
TARGET_SIZES = (24, 32, 48)
REPORT_SIZES = TARGET_SIZES + (60, 256)

# These gates are evaluated after both the raster source and fitted SVG have
# been rendered by the same ImageMagick installation. They are intentionally
# strict enough to catch collapsed holes, displaced edges, and oversmoothing.
MIN_SOFT_IOU = 0.94
MIN_BINARY_IOU_BY_SIZE = {24: 0.95, 32: 0.94, 48: 0.94}
MAX_BOUNDARY_P95_PX = 1.0

SVG_NAMESPACE = "http://www.w3.org/2000/svg"
COMMAND_PATTERN = re.compile(r"[A-Za-z]")


@dataclass(frozen=True)
class IconSpec:
    key: str
    imagegen_source: Path
    raster_reference: Path
    mask: Path
    traced_svg: Path
    vector_drawable: Path


@dataclass(frozen=True)
class RasterMetrics:
    size_px: int
    soft_iou: float
    binary_iou: float
    mean_absolute_alpha_error: float
    boundary_p95_px: float
    boundary_max_px: float
    reference_topology: Topology
    candidate_topology: Topology


@dataclass(frozen=True)
class Topology:
    components: int
    holes: int


@dataclass(frozen=True)
class CandidateReport:
    tolerance_px: float
    path_count: int
    command_count: int
    svg_bytes: int
    topology: Topology
    metrics: tuple[RasterMetrics, ...]
    passes: bool


def run(*args: str | Path) -> None:
    subprocess.run([str(arg) for arg in args], check=True)


def require_tools() -> str:
    magick = shutil.which("magick")
    if magick is None:
        raise RuntimeError("ImageMagick 'magick' is required for deterministic rendering")
    return magick


def alpha_array(path: Path) -> np.ndarray:
    with Image.open(path) as image:
        return np.asarray(image.convert("RGBA").getchannel("A"), dtype=np.float32) / 255.0


def make_trace_input(mask: Path, output: Path) -> None:
    with Image.open(mask) as source:
        alpha = source.convert("RGBA").getchannel("A")
    trace = Image.new("L", alpha.size, 255)
    trace.paste(0, mask=alpha)
    trace.convert("RGB").save(output)


def trace_svg(trace_input: Path, output: Path, tolerance: float) -> None:
    config = vtracer.Config(
        clustering="bw",
        mode="spline",
        filter_speckle=0,
        simplify=tolerance,
        path_precision=3,
        optimize=2,
        binary_threshold=128,
    )
    config.convert_file(str(trace_input), str(output))


def render(magick: str, source: Path, output: Path, size: int) -> None:
    run(
        magick,
        "-background",
        "none",
        source,
        "-resize",
        f"{size}x{size}",
        output,
    )


def binary_topology(alpha: np.ndarray, minimum_feature_area: int = 1) -> Topology:
    foreground = alpha >= 0.5
    components = count_components(foreground, minimum_feature_area)
    background = ~foreground
    exterior = flood_from_border(background)
    holes = count_components(background & ~exterior, minimum_feature_area)
    return Topology(components=components, holes=holes)


def count_components(mask: np.ndarray, minimum_area: int = 1) -> int:
    height, width = mask.shape
    visited = np.zeros_like(mask, dtype=bool)
    count = 0
    for y in range(height):
        for x in range(width):
            if not mask[y, x] or visited[y, x]:
                continue
            stack = [(y, x)]
            visited[y, x] = True
            area = 0
            while stack:
                cy, cx = stack.pop()
                area += 1
                for ny, nx in ((cy - 1, cx), (cy + 1, cx), (cy, cx - 1), (cy, cx + 1)):
                    if (
                        0 <= ny < height
                        and 0 <= nx < width
                        and mask[ny, nx]
                        and not visited[ny, nx]
                    ):
                        visited[ny, nx] = True
                        stack.append((ny, nx))
            if area >= minimum_area:
                count += 1
    return count


def flood_from_border(mask: np.ndarray) -> np.ndarray:
    height, width = mask.shape
    reached = np.zeros_like(mask, dtype=bool)
    stack: list[tuple[int, int]] = []
    for x in range(width):
        stack.extend(((0, x), (height - 1, x)))
    for y in range(height):
        stack.extend(((y, 0), (y, width - 1)))
    while stack:
        y, x = stack.pop()
        if reached[y, x] or not mask[y, x]:
            continue
        reached[y, x] = True
        for ny, nx in ((y - 1, x), (y + 1, x), (y, x - 1), (y, x + 1)):
            if 0 <= ny < height and 0 <= nx < width and not reached[ny, nx]:
                stack.append((ny, nx))
    return reached


def boundary_points(mask: np.ndarray) -> np.ndarray:
    padded = np.pad(mask, 1, constant_values=False)
    eroded = (
        padded[1:-1, 1:-1]
        & padded[:-2, 1:-1]
        & padded[2:, 1:-1]
        & padded[1:-1, :-2]
        & padded[1:-1, 2:]
    )
    return np.argwhere(mask & ~eroded).astype(np.float32)


def boundary_distances(reference: np.ndarray, candidate: np.ndarray) -> tuple[float, float]:
    reference_points = boundary_points(reference >= 0.5)
    candidate_points = boundary_points(candidate >= 0.5)
    if len(reference_points) == 0 or len(candidate_points) == 0:
        return math.inf, math.inf
    pairwise = np.sqrt(
        np.sum(
            (reference_points[:, None, :] - candidate_points[None, :, :]) ** 2,
            axis=2,
        )
    )
    distances = np.concatenate((pairwise.min(axis=1), pairwise.min(axis=0)))
    return float(np.percentile(distances, 95)), float(distances.max())


def compare_rasters(reference: Path, candidate: Path, size: int) -> RasterMetrics:
    expected = alpha_array(reference)
    actual = alpha_array(candidate)
    minimum = np.minimum(expected, actual).sum()
    maximum = np.maximum(expected, actual).sum()
    expected_binary = expected >= 0.5
    actual_binary = actual >= 0.5
    intersection = np.count_nonzero(expected_binary & actual_binary)
    union = np.count_nonzero(expected_binary | actual_binary)
    boundary_p95, boundary_max = boundary_distances(expected, actual)
    minimum_feature_area = 4 if size in TARGET_SIZES else 1
    return RasterMetrics(
        size_px=size,
        soft_iou=float(minimum / maximum) if maximum else 1.0,
        binary_iou=float(intersection / union) if union else 1.0,
        mean_absolute_alpha_error=float(np.mean(np.abs(expected - actual))),
        boundary_p95_px=boundary_p95,
        boundary_max_px=boundary_max,
        reference_topology=binary_topology(expected, minimum_feature_area),
        candidate_topology=binary_topology(actual, minimum_feature_area),
    )


def svg_paths(svg: Path) -> tuple[str, ...]:
    tree = ElementTree.parse(svg)
    paths = []
    for element in tree.iter(f"{{{SVG_NAMESPACE}}}path"):
        path_data = element.attrib.get("d")
        if path_data:
            paths.append(path_data)
    if not paths:
        raise RuntimeError(f"No paths found in {svg}")
    return tuple(paths)


def candidate_passes(topology: Topology, expected_topology: Topology, metrics: list[RasterMetrics]) -> bool:
    target_metrics = [metric for metric in metrics if metric.size_px in TARGET_SIZES]
    return (
        topology == expected_topology
        and all(metric.soft_iou >= MIN_SOFT_IOU for metric in target_metrics)
        and all(
            metric.binary_iou >= MIN_BINARY_IOU_BY_SIZE[metric.size_px]
            for metric in target_metrics
        )
        and all(metric.boundary_p95_px <= MAX_BOUNDARY_P95_PX for metric in target_metrics)
    )


def evaluate_candidate(
    magick: str,
    spec: IconSpec,
    svg: Path,
    tolerance: float,
    expected_topology: Topology,
    temp: Path,
) -> CandidateReport:
    metrics: list[RasterMetrics] = []
    topology = Topology(components=0, holes=0)
    for size in REPORT_SIZES:
        reference = temp / f"{spec.key}-reference-{size}.png"
        candidate = temp / f"{spec.key}-candidate-{tolerance}-{size}.png"
        render(magick, spec.mask, reference, size)
        render(magick, svg, candidate, size)
        metrics.append(compare_rasters(reference, candidate, size))
    with Image.open(spec.mask) as mask:
        source_size = mask.width
    full_render = temp / f"{spec.key}-full-{tolerance}.png"
    render(magick, svg, full_render, source_size)
    topology = binary_topology(alpha_array(full_render))
    paths = svg_paths(svg)
    return CandidateReport(
        tolerance_px=tolerance,
        path_count=len(paths),
        command_count=sum(len(COMMAND_PATTERN.findall(path)) for path in paths),
        svg_bytes=svg.stat().st_size,
        topology=topology,
        metrics=tuple(metrics),
        passes=candidate_passes(topology, expected_topology, metrics),
    )


def write_vector_drawable(svg: Path, output: Path) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    root = ElementTree.parse(svg).getroot()
    width, height = root.attrib["width"], root.attrib["height"]
    lines = [
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        '    android:width="24dp"',
        '    android:height="24dp"',
        f'    android:viewportWidth="{width}"',
        f'    android:viewportHeight="{height}">',
    ]
    for path in svg_paths(svg):
        lines.extend(
            (
                "    <path",
                '        android:fillColor="#FF000000"',
                '        android:fillType="nonZero"',
                f'        android:pathData="{path}" />',
            )
        )
    lines.append("</vector>")
    output.write_text("\n".join(lines) + "\n", encoding="utf-8")


def make_comparison_sheet(
    icon_results: list[tuple[IconSpec, Path]],
    output: Path,
    temp: Path,
    magick: str,
) -> None:
    background = (36, 36, 40, 255)
    accent = (63, 124, 255, 255)
    width = 720
    row_height = 190
    canvas = Image.new("RGBA", (width, 62 + row_height * len(icon_results)), background)
    draw = ImageDraw.Draw(canvas)
    font = ImageFont.load_default(size=18)
    small_font = ImageFont.load_default(size=14)
    draw.text((24, 20), "ImageGen mask -> fitted vector (24 dp at 2.5x density)", fill="white", font=font)
    for row, (spec, svg) in enumerate(icon_results):
        top = 62 + row * row_height
        draw.text((24, top + 8), spec.key, fill="white", font=font)
        reference_path = temp / f"{spec.key}-sheet-reference.png"
        vector_path = temp / f"{spec.key}-sheet-vector.png"
        render(magick, spec.mask, reference_path, 60)
        render(magick, svg, vector_path, 60)
        with Image.open(reference_path) as image:
            reference = image.convert("RGBA")
        with Image.open(vector_path) as image:
            vector = image.convert("RGBA")
        reference_color = Image.new("RGBA", reference.size, accent)
        reference_color.putalpha(reference.getchannel("A"))
        vector_color = Image.new("RGBA", vector.size, accent)
        vector_color.putalpha(vector.getchannel("A"))
        canvas.alpha_composite(reference_color, (110, top + 58))
        canvas.alpha_composite(vector_color, (330, top + 58))
        reference_alpha = np.asarray(reference.getchannel("A"), dtype=np.int16)
        vector_alpha = np.asarray(vector.getchannel("A"), dtype=np.int16)
        difference = np.abs(reference_alpha - vector_alpha).astype(np.uint8)
        difference_image = Image.new("RGBA", reference.size, (255, 92, 92, 255))
        difference_image.putalpha(Image.fromarray(difference, mode="L"))
        canvas.alpha_composite(difference_image, (550, top + 58))
        for x, label in ((110, "ImageGen mask"), (330, "Vector"), (550, "Alpha difference")):
            draw.text((x - 12, top + 132), label, fill=(210, 210, 216, 255), font=small_font)
    output.parent.mkdir(parents=True, exist_ok=True)
    canvas.convert("RGB").save(output, quality=95)


def make_small_size_sheet(
    icon_results: list[tuple[IconSpec, Path]],
    output: Path,
    temp: Path,
    magick: str,
) -> None:
    """Expose the actual target-size pixels instead of a smooth large preview."""
    background = (36, 36, 40, 255)
    accent = (63, 124, 255, 255)
    preview_size = 72
    column_width = 190
    width = 28 + column_width * len(TARGET_SIZES)
    row_height = 190
    canvas = Image.new("RGBA", (width, 72 + row_height * len(icon_results)), background)
    draw = ImageDraw.Draw(canvas)
    font = ImageFont.load_default(size=18)
    small_font = ImageFont.load_default(size=13)
    draw.text((24, 18), "Actual target pixels, enlarged with nearest-neighbour", fill="white", font=font)
    for column, size in enumerate(TARGET_SIZES):
        x = 28 + column * column_width
        draw.text((x + 56, 48), f"{size} px", fill=(210, 210, 216, 255), font=small_font)
    for row, (spec, svg) in enumerate(icon_results):
        top = 72 + row * row_height
        draw.text((24, top + 8), spec.key, fill="white", font=font)
        for column, size in enumerate(TARGET_SIZES):
            reference_path = temp / f"{spec.key}-small-reference-{size}.png"
            vector_path = temp / f"{spec.key}-small-vector-{size}.png"
            render(magick, spec.mask, reference_path, size)
            render(magick, svg, vector_path, size)
            x = 28 + column * column_width
            for offset, source, label in (
                (0, reference_path, "mask"),
                (86, vector_path, "vector"),
            ):
                with Image.open(source) as image:
                    alpha = image.convert("RGBA").getchannel("A")
                glyph = Image.new("RGBA", alpha.size, accent)
                glyph.putalpha(alpha)
                glyph = glyph.resize(
                    (preview_size, preview_size),
                    resample=Image.Resampling.NEAREST,
                )
                canvas.alpha_composite(glyph, (x + offset, top + 48))
                draw.text(
                    (x + offset + 18, top + 126),
                    label,
                    fill=(210, 210, 216, 255),
                    font=small_font,
                )
    output.parent.mkdir(parents=True, exist_ok=True)
    canvas.convert("RGB").save(output, quality=95)


def serializable_report(report: CandidateReport) -> dict[str, object]:
    data = asdict(report)
    data["topology"] = asdict(report.topology)
    data["metrics"] = [asdict(metric) for metric in report.metrics]
    return data
