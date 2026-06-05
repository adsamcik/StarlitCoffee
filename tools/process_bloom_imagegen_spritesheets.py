#!/usr/bin/env python3
"""Normalize image-generator bloom atlases into app-ready spritesheets.

The image generator creates the art. This script makes the slicing
deterministic:

1. Recover a 5x5 atlas grid, preferably from bright guide lines in the source.
2. Remove the flat chroma-key background.
3. Copy every source cell into an exact 256x256 output frame.

That grid-first flow is deliberately different from blob detection. Bloom
frames often contain separate pieces: split beans, steam curls, bubbles,
sparkles, petals, and leaves. Those separate pieces still belong to one frame.
"""

from __future__ import annotations

import argparse
import math
import statistics
import struct
import zlib
from dataclasses import dataclass
from pathlib import Path


FRAME_COLUMNS = 5
FRAME_ROWS = 5
FRAME_SIZE = 256
DEFAULT_GUIDE_COLOR = (255, 0, 255)
DEFAULT_FRAME_COUNT = FRAME_COLUMNS * FRAME_ROWS
MODERN_GRID_TEXT = f"{FRAME_COLUMNS}x{FRAME_ROWS}"

Rgb = tuple[int, int, int]
CellBox = tuple[int, int, int, int]


@dataclass(frozen=True)
class LineCluster:
    start: int
    end: int
    score: float

    @property
    def center(self) -> float:
        return (self.start + self.end) / 2


@dataclass(frozen=True)
class AxisLayout:
    cells: list[tuple[int, int]]
    clusters: list[LineCluster]
    source: str


@dataclass(frozen=True)
class GridLayout:
    columns: int
    rows: int
    x_axis: AxisLayout
    y_axis: AxisLayout

    @property
    def source(self) -> str:
        if self.x_axis.source == self.y_axis.source:
            return self.x_axis.source
        return f"x:{self.x_axis.source}, y:{self.y_axis.source}"


def png_chunk(kind: bytes, data: bytes) -> bytes:
    checksum = zlib.crc32(kind)
    checksum = zlib.crc32(data, checksum)
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", checksum & 0xFFFFFFFF)


def paeth_predictor(left: int, above: int, upper_left: int) -> int:
    estimate = left + above - upper_left
    left_distance = abs(estimate - left)
    above_distance = abs(estimate - above)
    upper_left_distance = abs(estimate - upper_left)
    if left_distance <= above_distance and left_distance <= upper_left_distance:
        return left
    if above_distance <= upper_left_distance:
        return above
    return upper_left


def read_png(path: Path) -> tuple[int, int, bytearray]:
    data = path.read_bytes()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError(f"{path} is not a PNG")

    offset = 8
    width = height = bit_depth = color_type = None
    compressed = bytearray()
    while offset < len(data):
        length = struct.unpack(">I", data[offset:offset + 4])[0]
        kind = data[offset + 4:offset + 8]
        payload = data[offset + 8:offset + 8 + length]
        offset += length + 12
        if kind == b"IHDR":
            width, height, bit_depth, color_type, compression, filter_method, interlace = struct.unpack(
                ">IIBBBBB",
                payload,
            )
            if bit_depth != 8 or compression != 0 or filter_method != 0 or interlace != 0:
                raise ValueError(f"{path} uses an unsupported PNG encoding")
            if color_type not in (2, 6):
                raise ValueError(f"{path} must be RGB or RGBA")
        elif kind == b"IDAT":
            compressed.extend(payload)
        elif kind == b"IEND":
            break

    if width is None or height is None or color_type is None:
        raise ValueError(f"{path} has no IHDR")

    channels = 4 if color_type == 6 else 3
    stride = width * channels
    raw = zlib.decompress(bytes(compressed))
    pixels = bytearray(width * height * 4)
    previous = bytearray(stride)
    source_offset = 0

    for y in range(height):
        filter_type = raw[source_offset]
        source_offset += 1
        scanline = bytearray(raw[source_offset:source_offset + stride])
        source_offset += stride

        for i, value in enumerate(scanline):
            left = scanline[i - channels] if i >= channels else 0
            above = previous[i]
            upper_left = previous[i - channels] if i >= channels else 0
            if filter_type == 1:
                scanline[i] = (value + left) & 0xFF
            elif filter_type == 2:
                scanline[i] = (value + above) & 0xFF
            elif filter_type == 3:
                scanline[i] = (value + ((left + above) // 2)) & 0xFF
            elif filter_type == 4:
                scanline[i] = (value + paeth_predictor(left, above, upper_left)) & 0xFF
            elif filter_type != 0:
                raise ValueError(f"{path} uses unknown PNG filter {filter_type}")

        for x in range(width):
            src = x * channels
            dst = (y * width + x) * 4
            pixels[dst:dst + 3] = scanline[src:src + 3]
            pixels[dst + 3] = scanline[src + 3] if channels == 4 else 255
        previous = scanline

    return width, height, pixels


def write_png(path: Path, width: int, height: int, pixels: bytearray) -> None:
    raw = bytearray()
    stride = width * 4
    for y in range(height):
        raw.append(0)
        raw.extend(pixels[y * stride:(y + 1) * stride])
    data = bytearray(b"\x89PNG\r\n\x1a\n")
    data.extend(png_chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)))
    data.extend(png_chunk(b"IDAT", zlib.compress(bytes(raw), 9)))
    data.extend(png_chunk(b"IEND", b""))
    path.write_bytes(bytes(data))


def parse_color(text: str) -> Rgb:
    value = text.strip().lower()
    if value.startswith("#"):
        value = value[1:]
    if len(value) != 6:
        raise argparse.ArgumentTypeError(f"Expected a #rrggbb color, got {text!r}")
    try:
        return (int(value[0:2], 16), int(value[2:4], 16), int(value[4:6], 16))
    except ValueError as exc:
        raise argparse.ArgumentTypeError(f"Expected a #rrggbb color, got {text!r}") from exc


def is_modern_grid(columns: int, rows: int) -> bool:
    return columns == FRAME_COLUMNS and rows == FRAME_ROWS


def color_distance_sq(color: Rgb, target: Rgb) -> int:
    return sum((color[i] - target[i]) ** 2 for i in range(3))


def close_to_any_color(color: Rgb, targets: list[Rgb], tolerance: int) -> bool:
    tolerance_sq = tolerance * tolerance
    return any(color_distance_sq(color, target) <= tolerance_sq for target in targets)


def pixel_rgb(pixels: bytearray, index: int) -> Rgb:
    offset = index * 4
    return (pixels[offset], pixels[offset + 1], pixels[offset + 2])


def has_source_alpha(pixels: bytearray) -> bool:
    transparent = 0
    total = len(pixels) // 4
    for offset in range(3, len(pixels), 4):
        if pixels[offset] < 250:
            transparent += 1
    return transparent / max(1, total) > 0.005


def background_key(
    width: int,
    height: int,
    pixels: bytearray,
    guide_colors: list[Rgb],
    guide_tolerance: int,
) -> Rgb:
    samples: list[Rgb] = []
    x_step = max(1, width // 128)
    y_step = max(1, height // 128)

    def add_sample(x: int, y: int) -> None:
        offset = (y * width + x) * 4
        color = (pixels[offset], pixels[offset + 1], pixels[offset + 2])
        if pixels[offset + 3] > 16 and not close_to_any_color(color, guide_colors, guide_tolerance):
            samples.append(color)

    for inset in (0, 4, 8, 16, 32):
        if inset * 2 >= width or inset * 2 >= height:
            continue
        for y in (inset, height - 1 - inset):
            for x in range(inset, width - inset, x_step):
                add_sample(x, y)
        for x in (inset, width - 1 - inset):
            for y in range(inset, height - inset, y_step):
                add_sample(x, y)
        if len(samples) >= 16:
            break

    if len(samples) < 16:
        sample_step = max(1, min(width, height) // 80)
        for y in range(0, height, sample_step):
            for x in range(0, width, sample_step):
                add_sample(x, y)

    if not samples:
        return (0, 0, 0)
    return tuple(int(statistics.median(channel)) for channel in zip(*samples))


def alpha_for_key_distance(
    color: Rgb,
    key: Rgb,
    transparent_threshold: int,
    opaque_threshold: int,
) -> int:
    distance = math.sqrt(color_distance_sq(color, key))
    if distance <= transparent_threshold:
        return 0
    if distance >= opaque_threshold:
        return 255
    return int(round((distance - transparent_threshold) / (opaque_threshold - transparent_threshold) * 255))


def build_alpha(
    width: int,
    height: int,
    pixels: bytearray,
    key: Rgb,
    guide_colors: list[Rgb],
    guide_tolerance: int,
    transparent_threshold: int,
    opaque_threshold: int,
) -> tuple[bytearray, bool]:
    source_alpha = has_source_alpha(pixels)
    alpha = bytearray(width * height)

    for index in range(width * height):
        offset = index * 4
        color = (pixels[offset], pixels[offset + 1], pixels[offset + 2])
        if close_to_any_color(color, guide_colors, guide_tolerance):
            alpha[index] = 0
        elif source_alpha:
            alpha[index] = pixels[offset + 3]
        else:
            alpha[index] = alpha_for_key_distance(color, key, transparent_threshold, opaque_threshold)

    return alpha, source_alpha


def guide_axis_scores(
    width: int,
    height: int,
    pixels: bytearray,
    guide_colors: list[Rgb],
    guide_tolerance: int,
) -> tuple[list[float], list[float]]:
    x_counts = [0] * width
    y_counts = [0] * height
    tolerance_sq = guide_tolerance * guide_tolerance

    for y in range(height):
        row_matches = 0
        row_offset = y * width
        for x in range(width):
            index = row_offset + x
            color = pixel_rgb(pixels, index)
            if any(color_distance_sq(color, guide_color) <= tolerance_sq for guide_color in guide_colors):
                x_counts[x] += 1
                row_matches += 1
        y_counts[y] = row_matches

    x_scores = [count / height for count in x_counts]
    y_scores = [count / width for count in y_counts]
    return x_scores, y_scores


def line_clusters(scores: list[float], minimum_score: float) -> list[LineCluster]:
    clusters: list[LineCluster] = []
    start: int | None = None
    total = 0.0
    count = 0

    for position, score in enumerate(scores):
        if score >= minimum_score:
            if start is None:
                start = position
                total = 0.0
                count = 0
            total += score
            count += 1
        elif start is not None:
            clusters.append(LineCluster(start, position - 1, total / max(1, count)))
            start = None

    if start is not None:
        clusters.append(LineCluster(start, len(scores) - 1, total / max(1, count)))

    return clusters


def select_even_clusters(
    clusters: list[LineCluster],
    expected_count: int,
    axis_length: int,
) -> list[LineCluster] | None:
    if len(clusters) < expected_count:
        return None

    expected_spacing = axis_length / max(1, expected_count - 1)
    max_distance = max(8.0, expected_spacing * 0.42)
    selected: list[LineCluster] = []
    used: set[int] = set()

    for index in range(expected_count):
        target = index * (axis_length - 1) / max(1, expected_count - 1)
        ranked = sorted(
            (
                (abs(cluster.center - target), -cluster.score, cluster_index, cluster)
                for cluster_index, cluster in enumerate(clusters)
                if cluster_index not in used
            ),
            key=lambda item: item[:3],
        )
        if not ranked or ranked[0][0] > max_distance:
            return None
        _, _, cluster_index, cluster = ranked[0]
        selected.append(cluster)
        used.add(cluster_index)

    selected = sorted(selected, key=lambda cluster: cluster.center)
    if any(selected[i].center >= selected[i + 1].center for i in range(len(selected) - 1)):
        return None
    return selected


def equal_axis_layout(axis_length: int, cell_count: int) -> AxisLayout:
    cells = [
        (
            round(index * axis_length / cell_count),
            round((index + 1) * axis_length / cell_count),
        )
        for index in range(cell_count)
    ]
    return AxisLayout(cells=cells, clusters=[], source="equal")


def guided_cells_from_clusters(
    clusters: list[LineCluster],
    axis_length: int,
    cell_count: int,
) -> list[tuple[int, int]] | None:
    cells: list[tuple[int, int]] = []
    minimum_cell_size = max(8, axis_length // (cell_count * 3))

    for index in range(cell_count):
        left = max(0, clusters[index].end + 1)
        right = min(axis_length, clusters[index + 1].start)
        if right - left < minimum_cell_size:
            return None
        cells.append((left, right))

    return cells


def guided_axis_layout_from_scores(
    scores: list[float],
    axis_length: int,
    cell_count: int,
) -> AxisLayout | None:
    peak = max(scores) if scores else 0.0
    if peak < 0.08:
        return None

    minimum_score = max(0.06, peak * 0.45)
    clusters = line_clusters(scores, minimum_score)
    selected = select_even_clusters(clusters, cell_count + 1, axis_length)
    if selected is None:
        return None

    cells = guided_cells_from_clusters(selected, axis_length, cell_count)
    if cells is None:
        return None

    return AxisLayout(cells=cells, clusters=selected, source="guide")


def recover_grid(
    width: int,
    height: int,
    pixels: bytearray,
    columns: int,
    rows: int,
    guide_colors: list[Rgb],
    guide_tolerance: int,
    force_equal_grid: bool,
) -> GridLayout:
    if force_equal_grid:
        x_axis = equal_axis_layout(width, columns)
        y_axis = equal_axis_layout(height, rows)
    else:
        x_scores, y_scores = guide_axis_scores(width, height, pixels, guide_colors, guide_tolerance)
        x_axis = guided_axis_layout_from_scores(x_scores, width, columns)
        y_axis = guided_axis_layout_from_scores(y_scores, height, rows)
        if x_axis is None:
            x_axis = equal_axis_layout(width, columns)
        if y_axis is None:
            y_axis = equal_axis_layout(height, rows)

    return GridLayout(columns=columns, rows=rows, x_axis=x_axis, y_axis=y_axis)


def estimate_source_grid_from_guides(
    width: int,
    height: int,
    pixels: bytearray,
    guide_colors: list[Rgb],
    guide_tolerance: int,
) -> tuple[int, int] | None:
    x_scores, y_scores = guide_axis_scores(width, height, pixels, guide_colors, guide_tolerance)

    def axis_cell_count(scores: list[float]) -> int | None:
        peak = max(scores) if scores else 0.0
        if peak < 0.08:
            return None
        clusters = line_clusters(scores, max(0.12, peak * 0.42))
        if len(clusters) < 2:
            return None
        return len(clusters) - 1

    columns = axis_cell_count(x_scores)
    rows = axis_cell_count(y_scores)
    if columns is None or rows is None:
        return None
    return columns, rows


def clamp_byte(value: float) -> int:
    return max(0, min(255, int(round(value))))


def despill_color(color: Rgb, key: Rgb, alpha: int) -> Rgb:
    if alpha <= 0 or alpha >= 250:
        return color
    coverage = max(0.01, alpha / 255)
    return tuple(clamp_byte((color[index] - key[index] * (1 - coverage)) / coverage) for index in range(3))


def scale_cell_into_frame(
    source_width: int,
    source_height: int,
    pixels: bytearray,
    alpha: bytearray,
    key: Rgb,
    source_uses_alpha: bool,
    cell: CellBox,
    frame_size: int,
    out_pixels: bytearray,
    out_width: int,
    frame_index: int,
    columns: int,
    alpha_threshold: int,
    despill: bool,
    clear_label_corner_fraction: float,
    output_padding: int,
) -> int:
    left, top, right, bottom = cell
    source_cell_width = max(1, right - left)
    source_cell_height = max(1, bottom - top)
    label_clear_width = int(source_cell_width * clear_label_corner_fraction)
    label_clear_height = int(source_cell_height * clear_label_corner_fraction)
    frame_col = frame_index % columns
    frame_row = frame_index // columns
    draw_left = max(0, min(frame_size // 3, output_padding))
    draw_top = draw_left
    draw_size = max(1, frame_size - draw_left * 2)
    written = 0

    for y in range(draw_size):
        source_y = top + min(
            source_cell_height - 1,
            int((y + 0.5) * source_cell_height / draw_size),
        )
        if source_y < 0 or source_y >= source_height:
            continue

        for x in range(draw_size):
            source_x = left + min(
                source_cell_width - 1,
                int((x + 0.5) * source_cell_width / draw_size),
            )
            if source_x < 0 or source_x >= source_width:
                continue

            if (
                clear_label_corner_fraction > 0
                and source_x - left < label_clear_width
                and source_y - top < label_clear_height
            ):
                continue

            source_index = source_y * source_width + source_x
            pixel_alpha = alpha[source_index]
            if pixel_alpha <= alpha_threshold:
                continue

            source_offset = source_index * 4
            color = (
                pixels[source_offset],
                pixels[source_offset + 1],
                pixels[source_offset + 2],
            )
            if despill and not source_uses_alpha:
                color = despill_color(color, key, pixel_alpha)

            dest_x = frame_col * frame_size + draw_left + x
            dest_y = frame_row * frame_size + draw_top + y
            dest_offset = (dest_y * out_width + dest_x) * 4
            out_pixels[dest_offset:dest_offset + 3] = bytes(color)
            out_pixels[dest_offset + 3] = pixel_alpha
            written += 1

    return written


def clear_output_frame_border(
    out_pixels: bytearray,
    out_width: int,
    frame_size: int,
    frame_index: int,
    columns: int,
    border_width: int,
) -> None:
    if border_width <= 0:
        return

    frame_col = frame_index % columns
    frame_row = frame_index // columns
    left = frame_col * frame_size
    top = frame_row * frame_size
    right = left + frame_size
    bottom = top + frame_size

    for y in range(top, min(bottom, top + border_width)):
        row_offset = y * out_width
        for x in range(left, right):
            offset = (row_offset + x) * 4
            out_pixels[offset:offset + 4] = b"\x00\x00\x00\x00"

    for y in range(max(top, bottom - border_width), bottom):
        row_offset = y * out_width
        for x in range(left, right):
            offset = (row_offset + x) * 4
            out_pixels[offset:offset + 4] = b"\x00\x00\x00\x00"

    for y in range(top, bottom):
        row_offset = y * out_width
        for x in range(left, min(right, left + border_width)):
            offset = (row_offset + x) * 4
            out_pixels[offset:offset + 4] = b"\x00\x00\x00\x00"
        for x in range(max(left, right - border_width), right):
            offset = (row_offset + x) * 4
            out_pixels[offset:offset + 4] = b"\x00\x00\x00\x00"


def expanded_box(
    box: tuple[int, int, int, int],
    padding: int,
    frame_size: int,
) -> tuple[int, int, int, int]:
    left, top, right, bottom = box
    return (
        max(0, left - padding),
        max(0, top - padding),
        min(frame_size - 1, right + padding),
        min(frame_size - 1, bottom + padding),
    )


def boxes_overlap(
    first: tuple[int, int, int, int],
    second: tuple[int, int, int, int],
) -> bool:
    return not (
        first[2] < second[0]
        or second[2] < first[0]
        or first[3] < second[1]
        or second[3] < first[1]
    )


def remove_stray_frame_components(
    out_pixels: bytearray,
    out_width: int,
    frame_size: int,
    frame_index: int,
    columns: int,
    alpha_threshold: int,
    max_area: int,
    keep_padding: int,
) -> None:
    if max_area <= 0:
        return

    frame_col = frame_index % columns
    frame_row = frame_index // columns
    left = frame_col * frame_size
    top = frame_row * frame_size
    visited = bytearray(frame_size * frame_size)
    components: list[tuple[int, tuple[int, int, int, int], list[int]]] = []
    neighbors = ((1, 0), (-1, 0), (0, 1), (0, -1))

    for local_start in range(frame_size * frame_size):
        if visited[local_start]:
            continue
        sx = local_start % frame_size
        sy = local_start // frame_size
        source_offset = ((top + sy) * out_width + left + sx) * 4
        if out_pixels[source_offset + 3] <= alpha_threshold:
            visited[local_start] = 1
            continue

        visited[local_start] = 1
        stack = [local_start]
        pixels: list[int] = []
        min_x = max_x = sx
        min_y = max_y = sy

        while stack:
            current = stack.pop()
            cx = current % frame_size
            cy = current // frame_size
            pixels.append(current)
            min_x = min(min_x, cx)
            max_x = max(max_x, cx)
            min_y = min(min_y, cy)
            max_y = max(max_y, cy)

            for dx, dy in neighbors:
                nx = cx + dx
                ny = cy + dy
                if nx < 0 or ny < 0 or nx >= frame_size or ny >= frame_size:
                    continue
                neighbor = ny * frame_size + nx
                if visited[neighbor]:
                    continue
                offset = ((top + ny) * out_width + left + nx) * 4
                if out_pixels[offset + 3] <= alpha_threshold:
                    visited[neighbor] = 1
                    continue
                visited[neighbor] = 1
                stack.append(neighbor)

        components.append((len(pixels), (min_x, min_y, max_x, max_y), pixels))

    if len(components) <= 1:
        return

    largest = max(components, key=lambda item: item[0])
    keep_box = expanded_box(largest[1], keep_padding, frame_size)
    for area, box, pixels in components:
        if area > max_area or boxes_overlap(box, keep_box):
            continue
        for local in pixels:
            x = local % frame_size
            y = local // frame_size
            offset = ((top + y) * out_width + left + x) * 4
            out_pixels[offset:offset + 4] = b"\x00\x00\x00\x00"


def draw_overlay_pixel(pixels: bytearray, width: int, x: int, y: int, color: Rgb) -> None:
    if x < 0 or y < 0 or x >= width:
        return
    index = (y * width + x) * 4
    if index + 3 >= len(pixels):
        return
    pixels[index:index + 3] = bytes(color)
    pixels[index + 3] = 255


def write_debug_overlay(
    path: Path,
    width: int,
    height: int,
    pixels: bytearray,
    grid: GridLayout,
) -> None:
    overlay = bytearray(pixels)

    x_lines: list[tuple[int, int]]
    if grid.x_axis.clusters:
        x_lines = [(cluster.start, cluster.end) for cluster in grid.x_axis.clusters]
    else:
        edges = sorted({edge for cell in grid.x_axis.cells for edge in cell})
        x_lines = [(edge, edge) for edge in edges]

    y_lines: list[tuple[int, int]]
    if grid.y_axis.clusters:
        y_lines = [(cluster.start, cluster.end) for cluster in grid.y_axis.clusters]
    else:
        edges = sorted({edge for cell in grid.y_axis.cells for edge in cell})
        y_lines = [(edge, edge) for edge in edges]

    for start, end in x_lines:
        for x in range(max(0, start), min(width, end + 1)):
            for y in range(height):
                draw_overlay_pixel(overlay, width, x, y, (0, 180, 255))

    for start, end in y_lines:
        for y in range(max(0, start), min(height, end + 1)):
            for x in range(width):
                draw_overlay_pixel(overlay, width, x, y, (255, 120, 0))

    path.parent.mkdir(parents=True, exist_ok=True)
    write_png(path, width, height, overlay)


def parse_pair(pair: str) -> tuple[Path, Path]:
    if "=>" in pair:
        source_text, output_text = pair.split("=>", maxsplit=1)
    elif ":" in pair:
        source_text, output_text = pair.split(":", maxsplit=1)
    else:
        raise ValueError(f"Pair must be source.png:output.png or source.png=>output.png, got {pair!r}")
    return Path(source_text), Path(output_text)


def process(
    source: Path,
    output: Path,
    columns: int,
    rows: int,
    source_columns: int | None,
    source_rows: int | None,
    auto_source_grid: bool,
    frame_size: int,
    guide_colors: list[Rgb],
    guide_tolerance: int,
    key_color: Rgb | None,
    transparent_threshold: int,
    opaque_threshold: int,
    alpha_threshold: int,
    force_equal_grid: bool,
    despill: bool,
    debug_dir: Path | None,
    clear_label_corner_fraction: float,
    clear_output_border: int,
    output_padding: int,
    remove_stray_components: int,
    stray_keep_padding: int,
    allow_legacy_grid: bool,
) -> None:
    width, height, pixels = read_png(source)
    if auto_source_grid:
        detected_grid = estimate_source_grid_from_guides(
            width=width,
            height=height,
            pixels=pixels,
            guide_colors=guide_colors,
            guide_tolerance=guide_tolerance,
        )
        if detected_grid is not None:
            source_columns = source_columns or detected_grid[0]
            source_rows = source_rows or detected_grid[1]
    source_columns = source_columns or columns
    source_rows = source_rows or rows
    if not allow_legacy_grid and not is_modern_grid(source_columns, source_rows):
        raise ValueError(
            f"{source.name} resolved to a {source_columns}x{source_rows} source grid. "
            f"The standard bloom generation pipeline requires the modern {MODERN_GRID_TEXT} grid. "
            "Use a modern source atlas or rerun with --allow-legacy-grid for a one-off migration."
        )
    output_columns = columns
    output_rows = rows
    source_frame_count = source_columns * source_rows
    output_frame_count = output_columns * output_rows
    key = key_color if key_color is not None else background_key(
        width,
        height,
        pixels,
        guide_colors,
        guide_tolerance,
    )
    grid = recover_grid(
        width=width,
        height=height,
        pixels=pixels,
        columns=source_columns,
        rows=source_rows,
        guide_colors=guide_colors,
        guide_tolerance=guide_tolerance,
        force_equal_grid=force_equal_grid,
    )
    alpha, source_uses_alpha = build_alpha(
        width=width,
        height=height,
        pixels=pixels,
        key=key,
        guide_colors=guide_colors,
        guide_tolerance=guide_tolerance,
        transparent_threshold=transparent_threshold,
        opaque_threshold=opaque_threshold,
    )

    out_width = frame_size * output_columns
    out_height = frame_size * output_rows
    out_pixels = bytearray(out_width * out_height * 4)
    frame_coverage: list[int] = []
    frame_map: list[int] = []

    for frame_index in range(output_frame_count):
        if output_frame_count <= 1:
            source_frame_index = 0
        elif source_frame_count == output_frame_count:
            source_frame_index = frame_index
        else:
            source_frame_index = round(frame_index * (source_frame_count - 1) / (output_frame_count - 1))
        frame_map.append(source_frame_index)
        frame_col = source_frame_index % source_columns
        frame_row = source_frame_index // source_columns
        left, right = grid.x_axis.cells[frame_col]
        top, bottom = grid.y_axis.cells[frame_row]
        coverage = scale_cell_into_frame(
            source_width=width,
            source_height=height,
            pixels=pixels,
            alpha=alpha,
            key=key,
            source_uses_alpha=source_uses_alpha,
            cell=(left, top, right, bottom),
            frame_size=frame_size,
            out_pixels=out_pixels,
            out_width=out_width,
            frame_index=frame_index,
            columns=output_columns,
            alpha_threshold=alpha_threshold,
            despill=despill,
            clear_label_corner_fraction=clear_label_corner_fraction,
            output_padding=output_padding,
        )
        clear_output_frame_border(
            out_pixels=out_pixels,
            out_width=out_width,
            frame_size=frame_size,
            frame_index=frame_index,
            columns=output_columns,
            border_width=clear_output_border,
        )
        remove_stray_frame_components(
            out_pixels=out_pixels,
            out_width=out_width,
            frame_size=frame_size,
            frame_index=frame_index,
            columns=output_columns,
            alpha_threshold=alpha_threshold,
            max_area=remove_stray_components,
            keep_padding=stray_keep_padding,
        )
        frame_coverage.append(coverage)

    output.parent.mkdir(parents=True, exist_ok=True)
    write_png(output, out_width, out_height, out_pixels)

    empty_frames = [index + 1 for index, coverage in enumerate(frame_coverage) if coverage < 32]
    source_grid_text = f"{source_columns}x{source_rows}"
    output_grid_text = f"{output_columns}x{output_rows}"
    print(
        f"{source.name}: {width}x{height} {grid.source} grid -> "
        f"{output} ({out_width}x{out_height}, {output_frame_count} frames, {frame_size}px cells)"
    )
    print(f"  source grid={source_grid_text}; output grid={output_grid_text}; sampled source frames={len(set(frame_map))}")
    print(
        f"  key=#{key[0]:02x}{key[1]:02x}{key[2]:02x}; "
        f"alpha={'source' if source_uses_alpha else 'chroma'}; "
        f"coverage min/median/max={min(frame_coverage)}/"
        f"{int(statistics.median(frame_coverage))}/{max(frame_coverage)}"
    )
    if empty_frames:
        print(f"  warning: frames with very low coverage: {empty_frames}")

    if debug_dir is not None:
        debug_dir.mkdir(parents=True, exist_ok=True)
        debug_image = debug_dir / f"{source.stem}_grid_debug.png"
        debug_report = debug_dir / f"{source.stem}_grid_report.txt"
        write_debug_overlay(debug_image, width, height, pixels, grid)
        debug_report.write_text(
            "\n".join(
                [
                    f"source={source}",
                    f"output={output}",
                    f"source_size={width}x{height}",
                    f"output_size={out_width}x{out_height}",
                    f"frame_size={frame_size}",
                    f"source_grid={source_grid_text}",
                    f"output_grid={output_grid_text}",
                    f"grid={grid.source}",
                    f"key=#{key[0]:02x}{key[1]:02x}{key[2]:02x}",
                    f"alpha={'source' if source_uses_alpha else 'chroma'}",
                    f"x_cells={grid.x_axis.cells}",
                    f"y_cells={grid.y_axis.cells}",
                    f"frame_map={[index + 1 for index in frame_map]}",
                    f"coverage={frame_coverage}",
                    f"low_coverage_frames={empty_frames}",
                    f"clear_label_corner_fraction={clear_label_corner_fraction}",
                    f"clear_output_border={clear_output_border}",
                    f"output_padding={output_padding}",
                    f"remove_stray_components={remove_stray_components}",
                    f"stray_keep_padding={stray_keep_padding}",
                    "",
                ]
            ),
            encoding="utf-8",
        )
        print(f"  debug: {debug_image}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "pairs",
        nargs="+",
        help="Pairs in the form source.png:output.png. Use source.png=>output.png for absolute Windows paths.",
    )
    parser.add_argument("--columns", type=int, default=FRAME_COLUMNS)
    parser.add_argument("--rows", type=int, default=FRAME_ROWS)
    parser.add_argument(
        "--source-columns",
        type=int,
        help="Column count in the generated source atlas. Defaults to --columns.",
    )
    parser.add_argument(
        "--source-rows",
        type=int,
        help="Row count in the generated source atlas. Defaults to --rows.",
    )
    parser.add_argument(
        "--auto-source-grid",
        action="store_true",
        help="Detect the generated source atlas grid from guide lines before writing the fixed output grid.",
    )
    parser.add_argument("--frame-size", type=int, default=FRAME_SIZE)
    parser.add_argument("--guide-color", action="append", type=parse_color, default=None)
    parser.add_argument("--guide-tolerance", type=int, default=44)
    parser.add_argument("--key-color", type=parse_color)
    parser.add_argument("--transparent-threshold", type=int, default=48)
    parser.add_argument("--opaque-threshold", type=int, default=118)
    parser.add_argument("--alpha-threshold", type=int, default=2)
    parser.add_argument("--force-equal-grid", action="store_true")
    parser.add_argument("--no-despill", action="store_true")
    parser.add_argument(
        "--clear-label-corner",
        type=float,
        default=0.0,
        metavar="FRACTION",
        help="Clear the top-left FRACTION of each source cell to remove unwanted frame numbers.",
    )
    parser.add_argument(
        "--clear-output-border",
        type=int,
        default=0,
        metavar="PX",
        help="Clear PX pixels around each output frame to remove guide remnants.",
    )
    parser.add_argument(
        "--output-padding",
        type=int,
        default=0,
        metavar="PX",
        help="Scale each source cell into an inset output rectangle with PX transparent padding.",
    )
    parser.add_argument(
        "--remove-stray-components",
        type=int,
        default=0,
        metavar="AREA",
        help="Remove disconnected components up to AREA pixels when they are away from the largest frame component.",
    )
    parser.add_argument(
        "--stray-keep-padding",
        type=int,
        default=16,
        metavar="PX",
        help="Keep small components that overlap the largest component expanded by PX.",
    )
    parser.add_argument("--debug-dir", type=Path)
    parser.add_argument(
        "--allow-legacy-grid",
        action="store_true",
        help="Allow non-5x5 source/output grids for one-off migration work.",
    )
    args = parser.parse_args()

    guide_colors = args.guide_color if args.guide_color else [DEFAULT_GUIDE_COLOR]
    expected_frames = args.columns * args.rows
    if not args.allow_legacy_grid:
        if not is_modern_grid(args.columns, args.rows):
            raise SystemExit(
                f"The standard bloom generation pipeline requires the modern {MODERN_GRID_TEXT} output grid. "
                "Pass --allow-legacy-grid only for one-off migration work."
            )
        if args.source_columns is not None and args.source_columns != FRAME_COLUMNS:
            raise SystemExit(
                f"The standard bloom generation pipeline requires a {MODERN_GRID_TEXT} source grid."
            )
        if args.source_rows is not None and args.source_rows != FRAME_ROWS:
            raise SystemExit(
                f"The standard bloom generation pipeline requires a {MODERN_GRID_TEXT} source grid."
            )
    elif expected_frames != DEFAULT_FRAME_COUNT:
        print(f"warning: configured grid contains {expected_frames} frames, not {DEFAULT_FRAME_COUNT}")

    for pair in args.pairs:
        source, output = parse_pair(pair)
        process(
            source=source,
            output=output,
            columns=args.columns,
            rows=args.rows,
            source_columns=args.source_columns,
            source_rows=args.source_rows,
            auto_source_grid=args.auto_source_grid,
            frame_size=args.frame_size,
            guide_colors=guide_colors,
            guide_tolerance=args.guide_tolerance,
            key_color=args.key_color,
            transparent_threshold=args.transparent_threshold,
            opaque_threshold=args.opaque_threshold,
            alpha_threshold=args.alpha_threshold,
            force_equal_grid=args.force_equal_grid,
            despill=not args.no_despill,
            debug_dir=args.debug_dir,
            clear_label_corner_fraction=args.clear_label_corner,
            clear_output_border=args.clear_output_border,
            output_padding=args.output_padding,
            remove_stray_components=args.remove_stray_components,
            stray_keep_padding=args.stray_keep_padding,
            allow_legacy_grid=args.allow_legacy_grid,
        )


if __name__ == "__main__":
    main()
