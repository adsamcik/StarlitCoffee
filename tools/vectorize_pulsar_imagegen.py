#!/usr/bin/env python3
"""Trace only the approved Pulsar PNG; no manually authored contour coordinates.

uv run --offline --with vtracer==1.0.0a3 --with pillow==12.3.0 --with numpy==2.3.5 \
    python tools/vectorize_pulsar_imagegen.py

Writes review assets and an Android export. Production installation is explicit.
"""

from __future__ import annotations

import hashlib
import json
import shutil
from dataclasses import asdict
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

import monochrome_icons as trace

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "docs/assets/pulsar-imagegen-vector"
TEMP = ROOT / "build/pulsar-imagegen-vector/trace"
SOURCE = OUT / "source.png"
MASK = OUT / "mask.png"
SVG = OUT / "pulsar.svg"
XML = OUT / "equipment_pulsar.xml"
SIZES = (24, 28, 34, 44, 63, 74, 88, 89, 116, 256)


def prepare_mask() -> dict[str, object]:
    with Image.open(SOURCE) as image:
        alpha = image.convert("RGBA").getchannel("A")
        source_size = image.size
    # The generated PNG includes faint alpha noise outside the visible icon.
    # Thresholding transparency keeps the silhouette, independently of ink colour.
    opaque = Image.fromarray(np.where(np.asarray(alpha) >= 128, 255, 0).astype(np.uint8))
    bounds = opaque.getbbox()
    if bounds is None:
        raise ValueError("Approved source has no visible icon")
    cropped = opaque.crop(bounds)
    cropped.thumbnail((832, 832), Image.Resampling.LANCZOS)
    normalized = Image.new("L", (1024, 1024))
    offset = ((1024 - cropped.width) // 2, (1024 - cropped.height) // 2)
    normalized.paste(cropped, offset)
    normalized = Image.fromarray(np.where(np.asarray(normalized) >= 128, 255, 0).astype(np.uint8))
    mask = Image.new("RGBA", (1024, 1024), (0, 0, 0, 0))
    mask.putalpha(normalized)
    mask.save(MASK)
    return {"source_dimensions": source_size, "source_alpha128_bounds": bounds,
            "normalized_bounds": normalized.getbbox(), "alpha_threshold": 128,
            "fit": "uniform crop and scale to at most 832px, centered in 1024px",
            "contour_edits": False}


def preview(magick: str) -> None:
    ink = "#484D5F"
    canvas = Image.new("RGBA", (840, 616), "#F5F3FA")
    draw = ImageDraw.Draw(canvas)
    font = ImageFont.truetype("C:/Windows/Fonts/segoeui.ttf", 23)
    small = ImageFont.truetype("C:/Windows/Fonts/segoeui.ttf", 16)
    draw.text((28, 20), "Approved generated silhouette → fitted vector", fill=ink, font=font)
    for source, x, label in ((MASK, 60, "Image mask"), (SVG, 430, "Vector")):
        raster = TEMP / f"preview-{label}.png"
        trace.render(magick, source, raster, 300)
        with Image.open(raster) as image:
            alpha = image.convert("RGBA").getchannel("A")
        coloured = Image.new("RGBA", alpha.size, ink)
        coloured.putalpha(alpha)
        canvas.alpha_composite(coloured, (x, 72))
        draw.text((x + 150, 378), label, fill=ink, font=small, anchor="mt")
    draw.text((28, 423), "Actual UI pixels at 420 dpi (2.625 pixels per dp)", fill=ink, font=small)
    for dp, size, x in ((24, 63, 60), (28, 74, 242), (34, 89, 426), (44, 116, 634)):
        raster = TEMP / f"preview-{size}.png"
        trace.render(magick, SVG, raster, size)
        with Image.open(raster) as image:
            alpha = image.convert("RGBA").getchannel("A")
        coloured = Image.new("RGBA", alpha.size, ink)
        coloured.putalpha(alpha)
        canvas.alpha_composite(coloured, (x, 461))
        draw.text((x + size // 2, 586), f"{dp} dp", fill=ink, font=small, anchor="mt")
    canvas.convert("RGB").save(OUT / "comparison.png")


def main() -> None:
    TEMP.mkdir(parents=True, exist_ok=True)
    magick = trace.require_tools()
    normalization = prepare_mask()
    expected = trace.binary_topology(trace.alpha_array(MASK))
    trace_input = TEMP / "trace-input.png"
    trace.make_trace_input(MASK, trace_input)
    spec = trace.IconSpec("pulsar", SOURCE, MASK, MASK, SVG, XML)
    candidates = []
    for tolerance in trace.TRACE_TOLERANCES:
        svg = TEMP / f"candidate-{tolerance}.svg"
        trace.trace_svg(trace_input, svg, tolerance)
        report = trace.evaluate_candidate(magick, spec, svg, tolerance, expected, TEMP)
        candidates.append((report, svg))
        print(f"Tolerance {tolerance}: {report.command_count} commands; passes={report.passes}", flush=True)
    passing = [candidate for candidate in candidates if candidate[0].passes]
    if not passing:
        raise RuntimeError("No trace preserves the source topology and target-size fidelity")
    winner, winner_svg = min(passing, key=lambda candidate: (candidate[0].command_count, candidate[0].svg_bytes))
    shutil.copyfile(winner_svg, SVG)
    trace.write_vector_drawable(SVG, XML)
    actual_sizes = []
    for size in SIZES:
        reference = TEMP / f"approved-{size}.png"
        candidate = TEMP / f"selected-{size}.png"
        trace.render(magick, MASK, reference, size)
        trace.render(magick, SVG, candidate, size)
        actual_sizes.append(asdict(trace.compare_rasters(reference, candidate, size)))
    preview(magick)
    trace.render(magick, SVG, OUT / "vector-1024.png", 1024)
    report = {
        "path_authoring": "mechanical trace of the approved perspective-polished image-generator PNG",
        "source_sha256": hashlib.sha256(SOURCE.read_bytes()).hexdigest(),
        "normalization": normalization,
        "source_topology": asdict(expected),
        "selected": trace.serializable_report(winner),
        "actual_size_metrics": actual_sizes,
        "candidates": [trace.serializable_report(item[0]) for item in candidates],
        "svg_sha256": hashlib.sha256(SVG.read_bytes()).hexdigest(),
        "android_sha256": hashlib.sha256(XML.read_bytes()).hexdigest(),
        "validation": "SVG render fidelity and lossless Android path export; native validation recorded separately",
    }
    (OUT / "report.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(f"Selected {winner.tolerance_px}px tolerance, {winner.path_count} paths, {winner.command_count} commands", flush=True)


if __name__ == "__main__":
    main()
