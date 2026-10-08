"""Render the selected family for review at large and small sizes."""
from pathlib import Path
import hashlib
import html
import json
import shutil
import subprocess
import xml.etree.ElementTree as ET

BASE = Path(__file__).resolve().parent
ROOT = next(parent for parent in BASE.parents if (parent / "app/src/main").is_dir())
BUILD = ROOT / "build/icon-language"
ART = ROOT / "docs/assets"
NS = "{http://www.w3.org/2000/svg}"


def main():
    BUILD.mkdir(parents=True, exist_ok=True)
    catalog = json.loads((BASE.parent / "rebuild-20261004/catalog.json").read_text())["entries"]
    report = json.loads((BASE / "report.json").read_text())["icons"]
    for theme, foreground, background in (("light", "#30313A", "#F7F5FC"), ("dark", "#DFE1EC", "#202129")):
        parts = [f'<svg xmlns="http://www.w3.org/2000/svg" width="1190" height="880">',
                 f'<rect width="1190" height="880" fill="{background}"/>']
        for index, entry in enumerate(catalog):
            key, label = entry["key"], html.escape(entry["label"])
            source = BASE / f"svg/{key}.svg"
            assert hashlib.sha256(source.read_bytes()).hexdigest() == report[key]["svg_sha256"]
            paths = "".join(f'<path fill="{foreground}" fill-rule="nonzero" d="{p.attrib["d"]}"/>'
                            for p in ET.parse(source).getroot().iter(NS + "path"))
            x, y = (index % 7) * 170, (index // 7) * 220
            for size, left, top in ((100, 35, 12), (24, 47, 130), (32, 95, 130)):
                parts.append(f'<svg x="{x+left}" y="{y+top}" width="{size}" height="{size}" '
                             f'viewBox="0 0 1024 1024">{paths}</svg>')
            parts.append(f'<text x="{x+85}" y="{y+186}" font-family="Segoe UI" font-size="12" '
                         f'text-anchor="middle" fill="{foreground}">{label}</text>')
        parts.append('</svg>')
        svg = BUILD / f"vessel-family-{theme}.svg"
        svg.write_text("\n".join(parts), encoding="utf-8", newline="\n")
        output = ART / f"vessel-family-{theme}.png"
        subprocess.run([shutil.which("magick"), str(svg), str(output)], check=True)
        print(output)


if __name__ == "__main__":
    main()
