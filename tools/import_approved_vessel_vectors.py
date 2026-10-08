#!/usr/bin/env python3
"""Import the reviewed cup family without retracing its source contours.

python tools/import_approved_vessel_vectors.py [--check]
Uses only the standard library. Source hashes and nonzero winding are part of
the reviewed artwork contract. Android viewports come from viewBox, not the
SVG's nominal 24px display size.
Every selected contour must have a preserved image-generator source and a
passing mechanical trace. Hand-authored replacements are historical only.
"""
import argparse
import hashlib
import json
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "docs/brewing/design/2026-10-02-visual-guides/assets/cup-icons/rebuild-20261004"
DRAWABLE = ROOT / "app/src/main/res/drawable"
SVG_NS = "{http://www.w3.org/2000/svg}"
GENERATED = SOURCE.parent / "generated-consistency-20261006"


def source_for(key: str, report: dict) -> tuple[Path, str]:
    traces = json.loads((GENERATED / "trace-report.json").read_text(encoding="utf-8"))["icons"]
    provenance = json.loads((GENERATED / "generation-records.json").read_text(encoding="utf-8"))
    if provenance["tool"] != "built-in image_gen.imagegen":
        raise ValueError("Vessel artwork must come from the image generator")
    if set(traces) != set(report["icons"]) or set(provenance["icons"]) != set(report["icons"]):
        raise ValueError("Generated family must cover every native vessel")
    entry, record = traces[key], provenance["icons"][key]
    if not entry["selected"] or not entry["selected"]["passes"]:
        raise ValueError(f"Generated contour has not passed tracing checks: {key}")
    if record["anatomy_reference_sha256"] != report["icons"][key]["source_sha256"]:
        raise ValueError(f"Original generated reference changed: {key}")
    if not record["prompt"] or not record["transparent_background"] or not record["generator_file"].startswith("exec-"):
        raise ValueError(f"Missing generator provenance: {key}")
    raster = GENERATED / f"generated/{key}.png"
    actual = hashlib.sha256(raster.read_bytes()).hexdigest()
    if actual != entry["source_sha256"] or actual != record["source_sha256"]:
        raise ValueError(f"Generated source changed after tracing: {key}")
    for reference in record["references"]:
        path = (GENERATED / reference["path"]).resolve()
        if path.parent not in {(SOURCE / "generated").resolve(), (GENERATED / "generated").resolve()}:
            raise ValueError(f"Reference must be preserved generator artwork: {key}")
        if hashlib.sha256(path.read_bytes()).hexdigest() != reference["sha256"]:
            raise ValueError(f"Generator reference changed: {key}")
    return GENERATED / f"svg/{key}.svg", entry["svg_sha256"]


def convert(source: Path, expected_hash: str) -> str:
    if hashlib.sha256(source.read_bytes()).hexdigest() != expected_hash:
        raise ValueError(f"Unreviewed SVG change: {source.name}")
    root = ET.parse(source).getroot()
    x, y, width, height = root.attrib["viewBox"].split()
    if (x, y) != ("0", "0"):
        raise ValueError(f"Nonzero viewport origin: {source.name}")
    paths = []
    for element in root.iter():
        if element.tag not in {SVG_NS + tag for tag in ("svg", "g", "path")}:
            raise ValueError(f"Unsupported SVG element: {source.name}")
        if "transform" in element.attrib or "stroke" in element.attrib:
            raise ValueError(f"Unflattened SVG: {source.name}")
        if element.attrib.get("fill-rule", "nonzero") != "nonzero":
            raise ValueError(f"Unexpected winding: {source.name}")
        if element.tag == SVG_NS + "path":
            paths.append(element.attrib["d"])
    if not paths:
        raise ValueError(f"Empty SVG: {source.name}")
    lines = [
        '<!-- Image-generated vessel contour, mechanically traced; tools/import_approved_vessel_vectors.py -->',
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        '    android:width="24dp" android:height="24dp"',
        f'    android:viewportWidth="{width}" android:viewportHeight="{height}">',
    ]
    for path in paths:
        lines.extend([
            '    <path android:fillColor="#FF000000" android:fillType="nonZero"',
            f'        android:pathData="{path}" />',
        ])
    lines.append('</vector>')
    return "\n".join(lines) + "\n"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    catalog = json.loads((SOURCE / "catalog.json").read_text(encoding="utf-8"))
    report = json.loads((SOURCE / "trace-report.json").read_text(encoding="utf-8"))
    exports = {}
    for entry in catalog["entries"]:
        key = entry["key"]
        exports[DRAWABLE / f"vessel_icon_{key}.xml"] = convert(*source_for(key, report))
    # Keep the generic quantity cup on the same geometry as its preset and nav cup.
    exports[DRAWABLE / "calculation_icon_cup_output.xml"] = exports[DRAWABLE / "vessel_icon_cappuccino.xml"]
    for destination, contents in exports.items():
        if args.check:
            if not destination.exists() or destination.read_text(encoding="utf-8") != contents:
                raise ValueError(f"Native contour differs from approved SVG: {destination.name}")
        else:
            destination.write_text(contents, encoding="utf-8", newline="\n")
    print(f"{'Verified' if args.check else 'Imported'} {len(exports)} native vectors from reviewed SVGs")


if __name__ == "__main__":
    main()
