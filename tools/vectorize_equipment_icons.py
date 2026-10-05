#!/usr/bin/env python3
"""Rebuild generated equipment artwork as tintable Android vectors.

uv run --with vtracer==1.0.0a3 --with pillow==12.3.0 --with numpy==2.3.5 \
    python tools/vectorize_equipment_icons.py
"""
import hashlib
import json
import shutil
import tempfile
from dataclasses import asdict
from pathlib import Path

import numpy as np
from PIL import Image

import monochrome_icons as trace


ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "docs/assets"
DRAWABLES = ROOT / "app/src/main/res/drawable"


def prepare_mask(source: Path, mask: Path) -> None:
    """Threshold and normalize generated black-on-white art, without contour edits."""
    with Image.open(source) as image:
        darkness = 255 - np.asarray(image.convert("L"), dtype=np.uint8)
    alpha = Image.fromarray(np.where(darkness >= 128, 255, 0).astype(np.uint8))
    bounds = alpha.getbbox()
    if bounds is None:
        raise ValueError(f"Empty icon: {source}")
    cropped = alpha.crop(bounds)
    cropped.thumbnail((832, 832), Image.Resampling.LANCZOS)
    normalized = Image.new("L", (1024, 1024))
    normalized.paste(cropped, ((1024 - cropped.width) // 2, (1024 - cropped.height) // 2))
    normalized = Image.fromarray(np.where(np.asarray(normalized) >= 128, 255, 0).astype(np.uint8))
    output = Image.new("RGBA", (1024, 1024), (0, 0, 0, 0))
    output.putalpha(normalized)
    output.save(mask)


def main() -> None:
    magick = trace.require_tools()
    manifest = json.loads((ASSETS / "equipment-imagegen-manifest.json").read_text(encoding="utf-8"))
    approved_methods = json.loads((ASSETS / "native-method-family/manifest.json").read_text(encoding="utf-8"))
    prototype_source = ROOT / approved_methods["prototype_source"]
    if hashlib.sha256(prototype_source.read_bytes().replace(b"\r\n", b"\n")).hexdigest() != approved_methods["prototype_sha256"]:
        raise ValueError("Method recognition source changed; review before rebuilding equipment art")
    selected = []
    failures = []
    reports = {"path_authoring": "mechanical traces plus explicit approved vector overrides",
               "normalization": "128 darkness threshold; crop and fit 832px inside 1024px; centered",
               "target_sizes": trace.TARGET_SIZES, "icons": {}}
    with tempfile.TemporaryDirectory(prefix="starlit-equipment-icons-") as temporary:
        temp = Path(temporary)
        for entry in manifest:
            key = entry["key"]
            approved_hash = approved_methods["native_sha256"].get(f"equipment_{key}.xml")
            if approved_hash is not None:
                installed = DRAWABLES / f"equipment_{key}.xml"
                if hashlib.sha256(installed.read_bytes().replace(b"\r\n", b"\n")).hexdigest() != approved_hash:
                    raise ValueError(f"Approved method vector differs: {key}; run import_approved_method_vectors.cjs")
                print(f"Preserved approved method vector: {key}", flush=True)
                continue
            spec = trace.IconSpec(key, ROOT / entry["source"], ASSETS / f"equipment-{key}-mask.png",
                                 ASSETS / f"equipment-{key}-mask.png", ASSETS / f"equipment-{key}-traced.svg",
                                 DRAWABLES / f"equipment_{key}.xml")
            if entry.get("authored_vector"):
                approved = ROOT / entry["authored_vector"]
                shutil.copyfile(approved, spec.vector_drawable)
                reports["icons"][key] = {
                    "path_authoring": "approved authored vector; excluded from raster tracing comparisons",
                    "source": entry["authored_vector"],
                    "android_sha256": hashlib.sha256(approved.read_bytes()).hexdigest(),
                }
                print(f"Copied approved vector: {key}", flush=True)
                continue
            prepare_mask(spec.imagegen_source, spec.mask)
            expected = trace.binary_topology(trace.alpha_array(spec.mask))
            trace_input = temp / f"{key}-input.png"
            trace.make_trace_input(spec.mask, trace_input)
            candidates = []
            for tolerance in trace.TRACE_TOLERANCES:
                svg = temp / f"{key}-{tolerance}.svg"
                trace.trace_svg(trace_input, svg, tolerance)
                report = trace.evaluate_candidate(magick, spec, svg, tolerance, expected, temp)
                candidates.append((report, svg))
            passing = [candidate for candidate in candidates if candidate[0].passes]
            if not passing:
                shutil.copyfile(candidates[0][1], ASSETS / f"equipment-{key}-failed-trace.svg")
                trace.render(magick, candidates[0][1], ASSETS / f"equipment-{key}-failed-render.png", 256)
                (ASSETS / f"equipment-{key}-failed-report.json").write_text(
                    json.dumps({"expected": asdict(expected), "candidates": [trace.serializable_report(c[0]) for c in candidates]}, indent=2))
                failures.append(key)
                print(f"FAILED {key}", flush=True)
                continue
            winner, svg = min(passing, key=lambda candidate: (candidate[0].command_count, candidate[0].svg_bytes))
            shutil.copyfile(svg, spec.traced_svg)
            trace.write_vector_drawable(spec.traced_svg, spec.vector_drawable)
            selected.append((spec, spec.traced_svg))
            reports["icons"][key] = {
                "source_sha256": hashlib.sha256(spec.imagegen_source.read_bytes()).hexdigest(),
                "mask_sha256": hashlib.sha256(spec.mask.read_bytes()).hexdigest(),
                "selected": trace.serializable_report(winner),
                "candidates": [trace.serializable_report(candidate[0]) for candidate in candidates],
                "source_topology": asdict(expected),
            }
            print(f"Traced {key}: {winner.command_count} commands", flush=True)
        trace.make_comparison_sheet(selected, ASSETS / "equipment-vector-comparison.png", temp, magick)
        trace.make_small_size_sheet(selected, ASSETS / "equipment-small-size-comparison.png", temp, magick)
    (ASSETS / "equipment-vector-report.json").write_text(json.dumps(reports, indent=2) + "\n", encoding="utf-8")
    if failures:
        raise RuntimeError(f"No contour passed fidelity and topology gates: {failures}")


if __name__ == "__main__":
    main()
