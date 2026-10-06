#!/usr/bin/env python3
"""Import the reviewed cup family without retracing its source contours.

python tools/import_approved_vessel_vectors.py [--check]
Uses only the standard library. Source hashes and nonzero winding are part of
the reviewed artwork contract. Android viewports come from viewBox, not the
SVG's nominal 24px display size.
The authored travel refinement is separate from the historical traced sources.
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
REFINEMENT = SOURCE.parent / "travel-refinement-20261006"


def source_for(key: str, report: dict) -> tuple[Path, str]:
    refinements = json.loads((REFINEMENT / "report.json").read_text(encoding="utf-8"))["icons"]
    if key in refinements:
        entry = refinements[key]
        if entry["based_on_svg_sha256"] != report["icons"][key]["svg_sha256"]:
            raise ValueError(f"Refinement baseline changed: {key}")
        return REFINEMENT / f"{key}.svg", entry["svg_sha256"]
    return SOURCE / f"svg/{key}.svg", report["icons"][key]["svg_sha256"]


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
        '<!-- Authored travel cup refinement; tools/import_approved_vessel_vectors.py -->'
        if source.parent == REFINEMENT else
        '<!-- Approved generated cup contour; tools/import_approved_vessel_vectors.py -->',
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
    for destination, contents in exports.items():
        if args.check:
            if not destination.exists() or destination.read_text(encoding="utf-8") != contents:
                raise ValueError(f"Native contour differs from approved SVG: {destination.name}")
        else:
            destination.write_text(contents, encoding="utf-8", newline="\n")
    print(f"{'Verified' if args.check else 'Imported'} {len(exports)} native vectors from reviewed SVGs")


if __name__ == "__main__":
    main()
