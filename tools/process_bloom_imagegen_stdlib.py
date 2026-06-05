#!/usr/bin/env python3
"""Process image-generated bloom atlases without third-party dependencies.

The image generation tool tends to return a near-5x5 atlas with a cyan
background. This normalizes it to the app contract: 5x5 cells, 256 px per
cell, RGBA PNG, transparent background.
"""

from __future__ import annotations

import argparse
import math
import struct
import zlib
from pathlib import Path


FRAME_SIZE = 256
COLUMNS = 5
ROWS = 5
CHANNELS_RGB = 3
CHANNELS_RGBA = 4
REMOVE_LIGHT_LINES = False
BACKGROUND_KEY = (0, 255, 255)
GRID_KEY: tuple[int, int, int] | None = None
KEY_TOLERANCE = 95.0
BACKGROUND_TOLERANCE = 95.0
GRID_TOLERANCE = 95.0
GRID_BAND_THRESHOLD_RATIO = 0.55


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("input", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--background-key", default="#00ffff")
    parser.add_argument("--grid-key")
    parser.add_argument("--key-tolerance", type=float, default=95.0)
    parser.add_argument("--background-tolerance", type=float)
    parser.add_argument("--grid-tolerance", type=float)
    parser.add_argument("--grid-band-threshold", type=float, default=0.55)
    parser.add_argument("--detect-grid", action="store_true")
    parser.add_argument("--cell-trim", type=int, default=4)
    parser.add_argument("--clear-border", type=int, default=3)
    parser.add_argument("--clear-dense-lines", action="store_true")
    parser.add_argument("--clear-boundary-lines", action="store_true")
    parser.add_argument("--clear-key-spill", action="store_true")
    parser.add_argument("--remove-light-lines", action="store_true")
    return parser.parse_args()


class Image:
    def __init__(self, width: int, height: int, rgba: bytearray) -> None:
        self.width = width
        self.height = height
        self.rgba = rgba

    def pixel(self, x: int, y: int) -> tuple[int, int, int, int]:
        x = max(0, min(self.width - 1, x))
        y = max(0, min(self.height - 1, y))
        idx = (y * self.width + x) * CHANNELS_RGBA
        return tuple(self.rgba[idx : idx + CHANNELS_RGBA])  # type: ignore[return-value]


def read_png(path: Path) -> Image:
    data = path.read_bytes()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError(f"{path} is not a PNG")

    pos = 8
    width = height = bit_depth = color_type = interlace = None
    idat = bytearray()
    while pos < len(data):
        length = struct.unpack(">I", data[pos : pos + 4])[0]
        name = data[pos + 4 : pos + 8]
        payload = data[pos + 8 : pos + 8 + length]
        pos += 12 + length
        if name == b"IHDR":
            width, height, bit_depth, color_type, _, _, interlace = struct.unpack(">IIBBBBB", payload)
        elif name == b"IDAT":
            idat.extend(payload)
        elif name == b"IEND":
            break

    if width is None or height is None or bit_depth != 8 or interlace != 0:
        raise ValueError(f"{path} must be an 8-bit non-interlaced PNG")
    if color_type not in (2, 6):
        raise ValueError(f"{path} color type {color_type} is not supported")

    channels = CHANNELS_RGB if color_type == 2 else CHANNELS_RGBA
    stride = width * channels
    raw = zlib.decompress(bytes(idat))
    rows: list[bytearray] = []
    read_pos = 0
    previous = bytearray(stride)
    for _ in range(height):
        filter_type = raw[read_pos]
        read_pos += 1
        row = bytearray(raw[read_pos : read_pos + stride])
        read_pos += stride
        unfilter(row, previous, filter_type, channels)
        rows.append(row)
        previous = row

    rgba = bytearray(width * height * CHANNELS_RGBA)
    for y, row in enumerate(rows):
        for x in range(width):
            src = x * channels
            dst = (y * width + x) * CHANNELS_RGBA
            rgba[dst : dst + 3] = row[src : src + 3]
            rgba[dst + 3] = row[src + 3] if channels == CHANNELS_RGBA else 255
    return Image(width, height, rgba)


def unfilter(row: bytearray, previous: bytearray, filter_type: int, channels: int) -> None:
    for i in range(len(row)):
        left = row[i - channels] if i >= channels else 0
        up = previous[i]
        upper_left = previous[i - channels] if i >= channels else 0
        if filter_type == 0:
            value = row[i]
        elif filter_type == 1:
            value = row[i] + left
        elif filter_type == 2:
            value = row[i] + up
        elif filter_type == 3:
            value = row[i] + ((left + up) >> 1)
        elif filter_type == 4:
            value = row[i] + paeth(left, up, upper_left)
        else:
            raise ValueError(f"Unsupported PNG filter {filter_type}")
        row[i] = value & 0xFF


def paeth(a: int, b: int, c: int) -> int:
    p = a + b - c
    pa = abs(p - a)
    pb = abs(p - b)
    pc = abs(p - c)
    if pa <= pb and pa <= pc:
        return a
    if pb <= pc:
        return b
    return c


def write_png(path: Path, image: Image) -> None:
    def chunk(name: bytes, payload: bytes) -> bytes:
        return (
            struct.pack(">I", len(payload))
            + name
            + payload
            + struct.pack(">I", zlib.crc32(name + payload) & 0xFFFFFFFF)
        )

    raw = bytearray()
    stride = image.width * CHANNELS_RGBA
    for y in range(image.height):
        raw.append(0)
        start = y * stride
        raw.extend(image.rgba[start : start + stride])

    path.parent.mkdir(parents=True, exist_ok=True)
    png = bytearray(b"\x89PNG\r\n\x1a\n")
    png.extend(chunk(b"IHDR", struct.pack(">IIBBBBB", image.width, image.height, 8, 6, 0, 0, 0)))
    png.extend(chunk(b"IDAT", zlib.compress(bytes(raw), 9)))
    png.extend(chunk(b"IEND", b""))
    path.write_bytes(png)


def process(
    source: Image,
    detect_grid: bool,
    cell_trim: int,
    clear_border: int,
    clear_dense_lines: bool,
    clear_boundary_lines: bool,
    clear_key_spill: bool,
) -> Image:
    output = Image(FRAME_SIZE * COLUMNS, FRAME_SIZE * ROWS, bytearray(FRAME_SIZE * COLUMNS * FRAME_SIZE * ROWS * CHANNELS_RGBA))
    cell_boxes = detect_cell_boxes(source) if detect_grid else uniform_cell_boxes(source)
    for row in range(ROWS):
        for column in range(COLUMNS):
            left, top, right, bottom = cell_boxes[row][column]
            left += cell_trim
            right -= cell_trim
            top += cell_trim
            bottom -= cell_trim
            resample_cell(source, output, left, top, right, bottom, column * FRAME_SIZE, row * FRAME_SIZE)
            clear_cell_border(output, column * FRAME_SIZE, row * FRAME_SIZE, border=clear_border)
    if clear_dense_lines:
        clear_dense_axis_artifacts(output)
    if clear_boundary_lines:
        clear_boundary_axis_artifacts(output)
    if clear_key_spill:
        clear_background_connected_key_spill(output)
    return output


def uniform_cell_boxes(source: Image) -> list[list[tuple[int, int, int, int]]]:
    return [
        [
            (
                round(column * source.width / COLUMNS),
                round(row * source.height / ROWS),
                round((column + 1) * source.width / COLUMNS),
                round((row + 1) * source.height / ROWS),
            )
            for column in range(COLUMNS)
        ]
        for row in range(ROWS)
    ]


def detect_cell_boxes(source: Image) -> list[list[tuple[int, int, int, int]]]:
    if GRID_KEY is None:
        raise ValueError("--detect-grid requires --grid-key")

    row_bands = detect_grid_bands(source, axis="row")
    column_bands = detect_grid_bands(source, axis="column")
    row_bands = select_expected_grid_bands(row_bands, source.height, ROWS + 1)
    column_bands = select_expected_grid_bands(column_bands, source.width, COLUMNS + 1)
    if len(row_bands) != ROWS + 1 or len(column_bands) != COLUMNS + 1:
        raise ValueError(
            "Expected 6 horizontal and 6 vertical grid bands, got "
            f"{len(row_bands)} horizontal and {len(column_bands)} vertical"
        )

    boxes: list[list[tuple[int, int, int, int]]] = []
    for row in range(ROWS):
        top = row_bands[row][1] + 1
        bottom = row_bands[row + 1][0]
        row_boxes: list[tuple[int, int, int, int]] = []
        for column in range(COLUMNS):
            left = column_bands[column][1] + 1
            right = column_bands[column + 1][0]
            row_boxes.append((left, top, right, bottom))
        boxes.append(row_boxes)
    return boxes


def select_expected_grid_bands(
    bands: list[tuple[int, int]],
    length: int,
    expected_count: int,
) -> list[tuple[int, int]]:
    if len(bands) <= expected_count:
        return bands

    selected: list[tuple[int, int]] = []
    used: set[int] = set()
    for index in range(expected_count):
        expected_center = index * (length - 1) / (expected_count - 1)
        best_index = min(
            (candidate for candidate in range(len(bands)) if candidate not in used),
            key=lambda candidate: abs(((bands[candidate][0] + bands[candidate][1]) / 2) - expected_center),
        )
        selected.append(bands[best_index])
        used.add(best_index)
    return sorted(selected)


def detect_grid_bands(source: Image, axis: str) -> list[tuple[int, int]]:
    length = source.height if axis == "row" else source.width
    cross_length = source.width if axis == "row" else source.height
    threshold = int(cross_length * GRID_BAND_THRESHOLD_RATIO)
    values: list[int] = []
    for primary in range(length):
        hits = 0
        for cross in range(cross_length):
            x, y = (cross, primary) if axis == "row" else (primary, cross)
            if is_grid_pixel(source.pixel(x, y)[:3]):
                hits += 1
        if hits >= threshold:
            values.append(primary)

    bands: list[tuple[int, int]] = []
    start: int | None = None
    last: int | None = None
    for value in values:
        if start is None:
            start = value
        elif last is not None and value > last + 1:
            bands.append((start, last))
            start = value
        last = value
    if start is not None and last is not None:
        bands.append((start, last))
    return bands


def resample_cell(
    source: Image,
    output: Image,
    left: int,
    top: int,
    right: int,
    bottom: int,
    out_left: int,
    out_top: int,
) -> None:
    src_width = max(1, right - left)
    src_height = max(1, bottom - top)
    for y in range(FRAME_SIZE):
        sy = top + (y + 0.5) * src_height / FRAME_SIZE - 0.5
        y0 = math.floor(sy)
        y1 = y0 + 1
        wy = sy - y0
        for x in range(FRAME_SIZE):
            sx = left + (x + 0.5) * src_width / FRAME_SIZE - 0.5
            x0 = math.floor(sx)
            x1 = x0 + 1
            wx = sx - x0
            rgba = bilinear(source, x0, y0, x1, y1, wx, wy)
            rgba = remove_background(rgba)
            dst = ((out_top + y) * output.width + out_left + x) * CHANNELS_RGBA
            output.rgba[dst : dst + CHANNELS_RGBA] = bytes(rgba)


def bilinear(source: Image, x0: int, y0: int, x1: int, y1: int, wx: float, wy: float) -> tuple[int, int, int, int]:
    c00 = source.pixel(x0, y0)
    c10 = source.pixel(x1, y0)
    c01 = source.pixel(x0, y1)
    c11 = source.pixel(x1, y1)
    channels = []
    for i in range(CHANNELS_RGBA):
        top = c00[i] * (1.0 - wx) + c10[i] * wx
        bottom = c01[i] * (1.0 - wx) + c11[i] * wx
        channels.append(round(top * (1.0 - wy) + bottom * wy))
    return tuple(channels)  # type: ignore[return-value]


def remove_background(rgba: tuple[int, int, int, int]) -> tuple[int, int, int, int]:
    r, g, b, a = rgba
    if is_background_pixel((r, g, b)) or is_grid_pixel((r, g, b)):
        return 0, 0, 0, 0

    if REMOVE_LIGHT_LINES and r > 160 and g > 160 and b > 160 and max(r, g, b) - min(r, g, b) < 45:
        return 0, 0, 0, 0
    cyan_strength = min(g, b) - r
    cyan_balance = abs(g - b)
    pale_grid_or_key = g > 160 and b > 160 and r < 230 and cyan_balance < 50 and cyan_strength > 20
    if pale_grid_or_key:
        edge = max(0.0, min(1.0, (cyan_strength - 20) / 70.0))
        alpha = round(a * (1.0 - edge))
        if alpha <= 35:
            return 0, 0, 0, 0
        spill = edge * 0.85
        neutral = max(r, min(g, b) - 110, 24)
        return r, round(g * (1.0 - spill) + neutral * spill), round(b * (1.0 - spill) + neutral * spill), alpha

    cyan_like = g > 100 and b > 100 and r < 125 and cyan_balance < 95
    if cyan_like and cyan_strength > 25:
        edge = max(0.0, min(1.0, (cyan_strength - 25) / 90.0))
        alpha = round(a * (1.0 - edge))
        if alpha <= 20:
            return 0, 0, 0, 0
        # Despill cyan antialiasing from the generated chroma background.
        spill = edge * 0.70
        neutral = max(r, min(g, b) - 90, 24)
        g = round(g * (1.0 - spill) + neutral * spill)
        b = round(b * (1.0 - spill) + neutral * spill)
        return r, g, b, alpha

    if BACKGROUND_KEY == (255, 0, 255):
        magenta_strength = min(r, b) - g
        magenta_balance = abs(r - b)
        magenta_like = r > 95 and b > 95 and g < 120 and magenta_balance < 100
        if magenta_like and magenta_strength > 30:
            edge = max(0.0, min(1.0, (magenta_strength - 30) / 110.0))
            alpha = round(a * (1.0 - edge))
            if alpha <= 35:
                return 0, 0, 0, 0
            spill = edge * 0.80
            neutral = max(g, min(r, b) - 105, 24)
            r = round(r * (1.0 - spill) + neutral * spill)
            b = round(b * (1.0 - spill) + neutral * spill)
            return r, g, b, alpha

    return r, g, b, a


def clear_background_connected_key_spill(output: Image) -> None:
    for row in range(ROWS):
        for column in range(COLUMNS):
            clear_cell_background_connected_key_spill(output, column * FRAME_SIZE, row * FRAME_SIZE)


def clear_cell_background_connected_key_spill(output: Image, out_left: int, out_top: int) -> None:
    visited = bytearray(FRAME_SIZE * FRAME_SIZE)
    stack: list[tuple[int, int]] = []

    def push(x: int, y: int) -> None:
        if 0 <= x < FRAME_SIZE and 0 <= y < FRAME_SIZE and not visited[y * FRAME_SIZE + x]:
            stack.append((x, y))

    for x in range(FRAME_SIZE):
        push(x, 0)
        push(x, FRAME_SIZE - 1)
    for y in range(1, FRAME_SIZE - 1):
        push(0, y)
        push(FRAME_SIZE - 1, y)

    while stack:
        x, y = stack.pop()
        local_idx = y * FRAME_SIZE + x
        if visited[local_idx]:
            continue
        visited[local_idx] = 1

        idx = ((out_top + y) * output.width + out_left + x) * CHANNELS_RGBA
        r, g, b, a = output.rgba[idx : idx + CHANNELS_RGBA]
        transparent = a <= 15
        spill = is_key_spill_pixel((r, g, b, a))
        if not transparent and not spill:
            continue
        if spill:
            output.rgba[idx : idx + CHANNELS_RGBA] = b"\x00\x00\x00\x00"

        push(x + 1, y)
        push(x - 1, y)
        push(x, y + 1)
        push(x, y - 1)


def is_key_spill_pixel(rgba: tuple[int, int, int, int]) -> bool:
    r, g, b, a = rgba
    if a <= 15:
        return False
    if BACKGROUND_KEY == (255, 0, 255):
        magenta_strength = min(r, b) - g
        magenta_balance = abs(r - b)
        return r > 45 and b > 45 and g < 90 and magenta_strength > 18 and magenta_balance < 120
    if BACKGROUND_KEY == (255, 0, 0):
        red_strength = r - max(g, b)
        return r > 70 and g < 95 and b < 95 and red_strength > 20
    if BACKGROUND_KEY == (0, 255, 0):
        green_strength = g - max(r, b)
        return g > 70 and r < 95 and b < 95 and green_strength > 20
    if BACKGROUND_KEY == (0, 255, 255):
        cyan_strength = min(g, b) - r
        cyan_balance = abs(g - b)
        return g > 75 and b > 75 and r < 95 and cyan_strength > 18 and cyan_balance < 120
    return False


def parse_hex_color(value: str) -> tuple[int, int, int]:
    normalized = value.strip().removeprefix("#")
    if len(normalized) != 6:
        raise ValueError(f"Expected #rrggbb color, got {value!r}")
    return int(normalized[0:2], 16), int(normalized[2:4], 16), int(normalized[4:6], 16)


def color_distance(left: tuple[int, int, int], right: tuple[int, int, int]) -> float:
    return math.sqrt(sum((left[i] - right[i]) ** 2 for i in range(3)))


def is_background_pixel(rgb: tuple[int, int, int]) -> bool:
    return color_distance(rgb, BACKGROUND_KEY) <= BACKGROUND_TOLERANCE


def is_grid_pixel(rgb: tuple[int, int, int]) -> bool:
    return GRID_KEY is not None and color_distance(rgb, GRID_KEY) <= GRID_TOLERANCE


def clear_cell_border(output: Image, out_left: int, out_top: int, border: int) -> None:
    for y in range(FRAME_SIZE):
        for x in range(FRAME_SIZE):
            if x >= border and x < FRAME_SIZE - border and y >= border and y < FRAME_SIZE - border:
                continue
            idx = ((out_top + y) * output.width + out_left + x) * CHANNELS_RGBA
            output.rgba[idx : idx + CHANNELS_RGBA] = b"\x00\x00\x00\x00"


def clear_dense_axis_artifacts(output: Image) -> None:
    row_threshold = int(output.width * 0.70)
    for y in range(output.height):
        count = sum(1 for x in range(output.width) if output.rgba[(y * output.width + x) * CHANNELS_RGBA + 3] > 20)
        if count > row_threshold:
            for x in range(output.width):
                idx = (y * output.width + x) * CHANNELS_RGBA
                output.rgba[idx : idx + CHANNELS_RGBA] = b"\x00\x00\x00\x00"
    column_threshold = int(output.height * 0.70)
    for x in range(output.width):
        count = sum(1 for y in range(output.height) if output.rgba[(y * output.width + x) * CHANNELS_RGBA + 3] > 20)
        if count > column_threshold:
            for y in range(output.height):
                idx = (y * output.width + x) * CHANNELS_RGBA
                output.rgba[idx : idx + CHANNELS_RGBA] = b"\x00\x00\x00\x00"


def clear_boundary_axis_artifacts(output: Image) -> None:
    row_threshold = int(output.width * 0.34)
    for boundary in range(FRAME_SIZE, output.height, FRAME_SIZE):
        for y in range(max(0, boundary - 64), min(output.height, boundary + 10)):
            count = sum(1 for x in range(output.width) if output.rgba[(y * output.width + x) * CHANNELS_RGBA + 3] > 20)
            if count <= row_threshold:
                continue
            for x in range(output.width):
                idx = (y * output.width + x) * CHANNELS_RGBA
                output.rgba[idx : idx + CHANNELS_RGBA] = b"\x00\x00\x00\x00"


def main() -> int:
    global REMOVE_LIGHT_LINES, BACKGROUND_KEY, GRID_KEY, KEY_TOLERANCE, BACKGROUND_TOLERANCE, GRID_TOLERANCE
    global GRID_BAND_THRESHOLD_RATIO
    args = parse_args()
    REMOVE_LIGHT_LINES = args.remove_light_lines
    BACKGROUND_KEY = parse_hex_color(args.background_key)
    GRID_KEY = parse_hex_color(args.grid_key) if args.grid_key else None
    KEY_TOLERANCE = args.key_tolerance
    BACKGROUND_TOLERANCE = args.background_tolerance if args.background_tolerance is not None else KEY_TOLERANCE
    GRID_TOLERANCE = args.grid_tolerance if args.grid_tolerance is not None else KEY_TOLERANCE
    GRID_BAND_THRESHOLD_RATIO = args.grid_band_threshold
    source = read_png(args.input)
    output = process(
        source,
        args.detect_grid,
        args.cell_trim,
        args.clear_border,
        args.clear_dense_lines,
        args.clear_boundary_lines,
        args.clear_key_spill,
    )
    write_png(args.output, output)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
