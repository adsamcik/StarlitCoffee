"""Trace generated cup contours; preserve curves and openings, never author paths.
Run from the repository with its pinned vtracer/Pillow/NumPy tool environment.
"""
from pathlib import Path
from dataclasses import asdict
import argparse
import hashlib
import json
import shutil
import sys
import tempfile
import xml.etree.ElementTree as ET
import numpy as np
from PIL import Image

BASE = Path(__file__).resolve().parent
ROOT = next(parent for parent in BASE.parents if (parent / "tools/monochrome_icons.py").is_file())
sys.path.insert(0, str(ROOT / "tools"))
import monochrome_icons as trace

trace.TARGET_SIZES = (24, 28, 34, 48)
trace.REPORT_SIZES = trace.TARGET_SIZES + (256,)
trace.MIN_BINARY_IOU_BY_SIZE.update({28: .94, 34: .94})


def mask_from_alpha(source, output):
    with Image.open(source) as image:
        alpha = np.asarray(image.convert("RGBA").getchannel("A"))
    binary = Image.fromarray(np.where(alpha >= 128, 255, 0).astype(np.uint8))
    bounds = binary.getbbox()
    if bounds is None:
        raise ValueError(f"Empty generated image: {source}")
    cropped = binary.crop(bounds)
    cropped.thumbnail((832, 832), Image.Resampling.LANCZOS)
    canvas = Image.new("L", (1024, 1024))
    canvas.paste(cropped, ((1024-cropped.width)//2, (1024-cropped.height)//2))
    canvas = Image.fromarray(np.where(np.asarray(canvas) >= 128, 255, 0).astype(np.uint8))
    mask = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    mask.putalpha(canvas)
    mask.save(output)
    return list(bounds)


def tintable_svg(source, target):
    svg = ET.parse(source).getroot()
    assert not any("transform" in element.attrib for element in svg.iter())
    svg.attrib.update({"viewBox": "0 0 1024 1024", "width": "24", "height": "24"})
    for element in svg.iter():
        if "fill" in element.attrib:
            element.attrib["fill"] = "currentColor"
    ET.register_namespace("", trace.SVG_NAMESPACE)
    target.write_text(ET.tostring(svg, encoding="unicode") + "\n", encoding="utf-8", newline="\n")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("keys", nargs="*")
    args = parser.parse_args()
    catalog = json.loads((BASE / "catalog.json").read_text())
    selected = [entry for entry in catalog["entries"] if not args.keys or entry["key"] in args.keys]
    report_file = BASE / "trace-report.json"
    report = json.loads(report_file.read_text()) if report_file.exists() else {
        "source": "Generated RGBA alpha; original images preserved unchanged",
        "path_authoring": "Mechanical spline tracing through tools/monochrome_icons.py; no hand-authored coordinates",
        "normalization": "Alpha threshold128, bounding-box crop, isotropic fit832 inside1024, centered",
        "target_sizes": list(trace.TARGET_SIZES), "icons": {}}
    for folder in ("masks", "svg", "renders"):
        (BASE / folder).mkdir(exist_ok=True)
    magick = trace.require_tools()
    failures = []
    with tempfile.TemporaryDirectory(prefix="starlit-cup-trace-") as scratch:
        temp = Path(scratch)
        for entry in selected:
            key = entry["key"]
            source = BASE / "generated" / f"{key}.png"
            mask = BASE / "masks" / f"{key}.png"
            bounds = mask_from_alpha(source, mask)
            expected = trace.binary_topology(trace.alpha_array(mask))
            spec = trace.IconSpec(key, source, mask, mask, BASE / "svg" / f"{key}.svg", temp / f"{key}.xml")
            trace_input = temp / f"{key}-input.png"
            trace.make_trace_input(mask, trace_input)
            attempts = []
            for tolerance in (2.0, 1.0, .5, .25):
                fitted = temp / f"{key}-{tolerance}.svg"
                trace.trace_svg(trace_input, fitted, tolerance)
                candidate = trace.evaluate_candidate(magick, spec, fitted, tolerance, expected, temp)
                attempts.append(trace.serializable_report(candidate))
                if candidate.passes:
                    tintable_svg(fitted, spec.traced_svg)
                    shutil.copyfile(temp / f"{key}-candidate-{tolerance}-256.png", BASE / "renders" / f"{key}.png")
                    break
            else:
                report["icons"][key] = {"source_sha256": hashlib.sha256(source.read_bytes()).hexdigest(),
                    "source_bounds": bounds, "source_topology": asdict(expected),
                    "attempts": attempts, "selected": None}
                report_file.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8", newline="\n")
                failures.append(key)
                print(f"FAIL {key}: no trace passed contour/topology gates; diagnostics saved", flush=True)
                continue
            report["icons"][key] = {"source_sha256": hashlib.sha256(source.read_bytes()).hexdigest(),
                "svg_sha256": hashlib.sha256(spec.traced_svg.read_bytes()).hexdigest(),
                "source_bounds": bounds, "source_topology": asdict(expected),
                "selected": trace.serializable_report(candidate), "attempts": attempts}
            report_file.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8", newline="\n")
            print(f"PASS {key}: {candidate.command_count} spline commands, {candidate.path_count} paths, tolerance{tolerance}", flush=True)
    if failures:
        raise RuntimeError(f"No trace passed contour/topology gates: {', '.join(failures)}")

if __name__ == "__main__":
    main()
