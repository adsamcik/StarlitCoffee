#!/usr/bin/env python3
"""Normalize image-generator bloom sheets into app-ready spritesheets.

The image generator is used for the artwork. This script only removes the
chroma-key background and normalizes the generated 9x5 grid into app cells.
"""

from __future__ import annotations

import argparse
import math
import struct
import zlib
from collections import deque
from pathlib import Path


FRAME_COUNT = 45
FRAME_COLUMNS = 9
FRAME_ROWS = 5
FRAME_SIZE = 256
FRAME_OVERLAP_FRACTION = 0.20
SUBJECT_MAX_SIZE = 224


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


def distance_sq(
    color: tuple[int, int, int],
    key: tuple[int, int, int],
) -> int:
    return sum((color[i] - key[i]) ** 2 for i in range(3))


def background_key(width: int, height: int, pixels: bytearray) -> tuple[int, int, int]:
    samples: list[tuple[int, int, int]] = []
    for y in (0, height - 1):
        for x in range(0, width, max(1, width // 64)):
            offset = (y * width + x) * 4
            samples.append(tuple(pixels[offset:offset + 3]))
    for x in (0, width - 1):
        for y in range(0, height, max(1, height // 64)):
            offset = (y * width + x) * 4
            samples.append(tuple(pixels[offset:offset + 3]))
    return tuple(sorted(channel)[len(channel) // 2] for channel in zip(*samples))


def alpha_for_pixel(
    color: tuple[int, int, int],
    key: tuple[int, int, int],
) -> int:
    distance = math.sqrt(distance_sq(color, key))
    if distance <= 48:
        return 0
    if distance >= 118:
        return 255
    return int(round((distance - 48) / 70 * 255))


def build_alpha(width: int, height: int, pixels: bytearray, key: tuple[int, int, int]) -> bytearray:
    alpha = bytearray(width * height)
    for index in range(width * height):
        offset = index * 4
        alpha[index] = min(
            pixels[offset + 3],
            alpha_for_pixel(tuple(pixels[offset:offset + 3]), key),
        )
    return alpha


def components(width: int, height: int, alpha: bytearray) -> list[tuple[int, int, int, int, int]]:
    visited = bytearray(width * height)
    found: list[tuple[int, int, int, int, int]] = []
    neighbors = ((1, 0), (-1, 0), (0, 1), (0, -1))

    for start in range(width * height):
        if visited[start] or alpha[start] <= 96:
            continue
        visited[start] = 1
        queue: deque[int] = deque([start])
        min_x = max_x = start % width
        min_y = max_y = start // width
        area = 0

        while queue:
            current = queue.popleft()
            area += 1
            x = current % width
            y = current // width
            min_x = min(min_x, x)
            max_x = max(max_x, x)
            min_y = min(min_y, y)
            max_y = max(max_y, y)

            for dx, dy in neighbors:
                nx = x + dx
                ny = y + dy
                if nx < 0 or ny < 0 or nx >= width or ny >= height:
                    continue
                neighbor = ny * width + nx
                if visited[neighbor] or alpha[neighbor] <= 96:
                    continue
                visited[neighbor] = 1
                queue.append(neighbor)

        box_width = max_x - min_x + 1
        box_height = max_y - min_y + 1
        if area >= 450 and box_width >= 18 and box_height >= 18:
            found.append((min_x, min_y, max_x, max_y, area))

    return found


def reading_order(items: list[tuple[int, int, int, int, int]]) -> list[tuple[int, int, int, int, int]]:
    items = sorted(items, key=lambda box: ((box[1] + box[3]) / 2, (box[0] + box[2]) / 2))
    rows: list[list[tuple[int, int, int, int, int]]] = []
    row_threshold = 90
    for item in items:
        center_y = (item[1] + item[3]) / 2
        for row in rows:
            row_center = sum((box[1] + box[3]) / 2 for box in row) / len(row)
            if abs(center_y - row_center) < row_threshold:
                row.append(item)
                break
        else:
            rows.append([item])

    ordered: list[tuple[int, int, int, int, int]] = []
    for row in sorted(rows, key=lambda group: sum((box[1] + box[3]) / 2 for box in group) / len(group)):
        ordered.extend(sorted(row, key=lambda box: (box[0] + box[2]) / 2))
    return ordered


def select_twenty(items: list[tuple[int, int, int, int, int]]) -> list[tuple[int, int, int, int, int]]:
    if not items:
        raise ValueError("No frames detected in generated image")
    if len(items) == FRAME_COUNT:
        return items
    if len(items) > FRAME_COUNT:
        return [items[round(i * (len(items) - 1) / (FRAME_COUNT - 1))] for i in range(FRAME_COUNT)]
    return [items[round(i * (len(items) - 1) / (FRAME_COUNT - 1))] for i in range(FRAME_COUNT)]


def composite_frame(
    source_width: int,
    source_height: int,
    source_pixels: bytearray,
    source_alpha: bytearray,
    box: tuple[int, int, int, int, int],
    out_pixels: bytearray,
    frame_index: int,
) -> None:
    padding = 24
    min_x, min_y, max_x, max_y, _ = box
    min_x = max(0, min_x - padding)
    min_y = max(0, min_y - padding)
    max_x = min(source_width - 1, max_x + padding)
    max_y = min(source_height - 1, max_y + padding)
    crop_width = max_x - min_x + 1
    crop_height = max_y - min_y + 1
    scale = min(SUBJECT_MAX_SIZE / crop_width, SUBJECT_MAX_SIZE / crop_height)
    draw_width = max(1, round(crop_width * scale))
    draw_height = max(1, round(crop_height * scale))
    frame_col = frame_index % FRAME_COLUMNS
    frame_row = frame_index // FRAME_COLUMNS
    x_offset = frame_col * FRAME_SIZE + (FRAME_SIZE - draw_width) // 2
    y_offset = frame_row * FRAME_SIZE + (FRAME_SIZE - draw_height) // 2

    for y in range(draw_height):
        source_y = min_y + min(crop_height - 1, int(y / scale))
        for x in range(draw_width):
            source_x = min_x + min(crop_width - 1, int(x / scale))
            source_index = source_y * source_width + source_x
            alpha = source_alpha[source_index]
            if alpha == 0:
                continue
            source_offset = source_index * 4
            dest_x = x_offset + x
            dest_y = y_offset + y
            dest_offset = (dest_y * FRAME_SIZE * FRAME_COLUMNS + dest_x) * 4
            out_pixels[dest_offset:dest_offset + 3] = source_pixels[source_offset:source_offset + 3]
            out_pixels[dest_offset + 3] = alpha


def selected_frame_pixels(
    source_width: int,
    source_height: int,
    source_alpha: bytearray,
    cell_left: float,
    cell_top: float,
    cell_width: float,
    cell_height: float,
    overlap_x: float,
    overlap_y: float,
) -> set[int]:
    crop_left = max(0, int(math.floor(cell_left - overlap_x)))
    crop_top = max(0, int(math.floor(cell_top - overlap_y)))
    crop_right = min(source_width - 1, int(math.ceil(cell_left + cell_width + overlap_x)))
    crop_bottom = min(source_height - 1, int(math.ceil(cell_top + cell_height + overlap_y)))
    crop_width = crop_right - crop_left + 1
    crop_height = crop_bottom - crop_top + 1
    visited = bytearray(crop_width * crop_height)
    selected: set[int] = set()
    center_x = cell_left + cell_width / 2
    center_y = cell_top + cell_height / 2
    max_center_distance = max(cell_width, cell_height) * 0.72
    neighbors = ((1, 0), (-1, 0), (0, 1), (0, -1))

    for local_start in range(crop_width * crop_height):
        if visited[local_start]:
            continue
        x = local_start % crop_width
        y = local_start // crop_width
        source_index = (crop_top + y) * source_width + crop_left + x
        if source_alpha[source_index] <= 72:
            visited[local_start] = 1
            continue

        visited[local_start] = 1
        queue: deque[int] = deque([local_start])
        pixels: list[int] = []
        min_x = max_x = crop_left + x
        min_y = max_y = crop_top + y
        sum_x = 0
        sum_y = 0

        while queue:
            current = queue.popleft()
            current_x = current % crop_width
            current_y = current // crop_width
            absolute_x = crop_left + current_x
            absolute_y = crop_top + current_y
            absolute_index = absolute_y * source_width + absolute_x
            pixels.append(absolute_index)
            sum_x += absolute_x
            sum_y += absolute_y
            min_x = min(min_x, absolute_x)
            max_x = max(max_x, absolute_x)
            min_y = min(min_y, absolute_y)
            max_y = max(max_y, absolute_y)

            for dx, dy in neighbors:
                next_x = current_x + dx
                next_y = current_y + dy
                if next_x < 0 or next_y < 0 or next_x >= crop_width or next_y >= crop_height:
                    continue
                next_local = next_y * crop_width + next_x
                if visited[next_local]:
                    continue
                next_index = (crop_top + next_y) * source_width + crop_left + next_x
                if source_alpha[next_index] <= 72:
                    visited[next_local] = 1
                    continue
                visited[next_local] = 1
                queue.append(next_local)

        area = len(pixels)
        if area < 16:
            continue

        overlap_left = max(min_x, int(math.floor(cell_left)))
        overlap_top = max(min_y, int(math.floor(cell_top)))
        overlap_right = min(max_x, int(math.ceil(cell_left + cell_width)))
        overlap_bottom = min(max_y, int(math.ceil(cell_top + cell_height)))
        overlap_area = max(0, overlap_right - overlap_left + 1) * max(0, overlap_bottom - overlap_top + 1)
        centroid_x = sum_x / area
        centroid_y = sum_y / area
        distance = math.hypot(centroid_x - center_x, centroid_y - center_y)

        if overlap_area > 0 and distance <= max_center_distance:
            selected.update(pixels)

    return selected


def process(source: Path, output: Path) -> None:
    width, height, pixels = read_png(source)
    key = background_key(width, height, pixels)
    alpha = build_alpha(width, height, pixels, key)
    out_width = FRAME_SIZE * FRAME_COLUMNS
    out_height = FRAME_SIZE * FRAME_ROWS
    out_pixels = bytearray(out_width * out_height * 4)
    source_cell_width = width / FRAME_COLUMNS
    source_cell_height = height / FRAME_ROWS
    overlap_x = source_cell_width * FRAME_OVERLAP_FRACTION
    overlap_y = source_cell_height * FRAME_OVERLAP_FRACTION

    for frame_index in range(FRAME_COUNT):
        frame_col = frame_index % FRAME_COLUMNS
        frame_row = frame_index // FRAME_COLUMNS
        source_left = frame_col * source_cell_width
        source_top = frame_row * source_cell_height
        virtual_left = source_left - overlap_x
        virtual_top = source_top - overlap_y
        virtual_width = source_cell_width + overlap_x * 2
        virtual_height = source_cell_height + overlap_y * 2
        selected = selected_frame_pixels(
            source_width=width,
            source_height=height,
            source_alpha=alpha,
            cell_left=source_left,
            cell_top=source_top,
            cell_width=source_cell_width,
            cell_height=source_cell_height,
            overlap_x=overlap_x,
            overlap_y=overlap_y,
        )

        for y in range(FRAME_SIZE):
            source_y = min(
                height - 1,
                max(0, int(virtual_top + (y + 0.5) / FRAME_SIZE * virtual_height)),
            )
            for x in range(FRAME_SIZE):
                source_x = min(
                    width - 1,
                    max(0, int(virtual_left + (x + 0.5) / FRAME_SIZE * virtual_width)),
                )
                source_index = source_y * width + source_x
                if source_index not in selected:
                    continue
                source_offset = source_index * 4
                pixel_alpha = alpha[source_index]

                dest_x = frame_col * FRAME_SIZE + x
                dest_y = frame_row * FRAME_SIZE + y
                dest_offset = (dest_y * out_width + dest_x) * 4
                out_pixels[dest_offset:dest_offset + 3] = pixels[source_offset:source_offset + 3]
                out_pixels[dest_offset + 3] = pixel_alpha

    output.parent.mkdir(parents=True, exist_ok=True)
    write_png(output, out_width, out_height, out_pixels)
    print(f"{source.name}: normalized {FRAME_COLUMNS}x{FRAME_ROWS} generated grid -> {output}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "pairs",
        nargs="+",
        help="Pairs in the form source.png:output.png",
    )
    args = parser.parse_args()

    for pair in args.pairs:
        source_text, output_text = pair.split(":", maxsplit=1)
        process(Path(source_text), Path(output_text))


if __name__ == "__main__":
    main()
