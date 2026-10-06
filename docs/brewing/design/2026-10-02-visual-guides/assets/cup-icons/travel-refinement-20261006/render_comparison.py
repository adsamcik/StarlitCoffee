"""Render the original and refined vector sources without changing either."""
from pathlib import Path
import shutil
import subprocess
import xml.etree.ElementTree as ET

BASE = Path(__file__).resolve().parent
ROOT = next(parent for parent in BASE.parents if (parent / "app/src/main").is_dir())
BUILD = ROOT / "build/travel-icon"
OUTPUT = ROOT / "docs/assets/travel-icon-refinement.png"
NS = "{http://www.w3.org/2000/svg}"


def glyph(path: Path, x: float, y: float, size: float, color: str) -> str:
    paths = ET.parse(path).getroot().iter(NS + "path")
    return (
        f'<svg x="{x}" y="{y}" width="{size}" height="{size}" viewBox="0 0 1024 1024">'
        + "".join(f'<path fill="{color}" fill-rule="nonzero" d="{p.attrib["d"]}"/>' for p in paths)
        + "</svg>"
    )


def main() -> None:
    BUILD.mkdir(parents=True, exist_ok=True)
    old = BASE.parent / "rebuild-20261004/svg/travel.svg"
    new = BASE / "travel.svg"
    content = ['<svg xmlns="http://www.w3.org/2000/svg" width="840" height="630">',
               '<rect width="840" height="630" rx="24" fill="#F7F5FC"/>',
               '<rect y="405" width="840" height="225" fill="#202129"/>']
    for column, (label, path) in enumerate((("Before", old), ("After", new))):
        left = column * 420
        content.append(f'<text x="{left + 210}" y="42" text-anchor="middle" '
                       f'font-family="Segoe UI" font-size="22" fill="#555764">{label}</text>')
        content.append(glyph(path, left + 98, 64, 224, "#30313A"))
        for index, size in enumerate((24, 28, 32, 44)):
            x = left + 61 + index * 100
            actual_size = size * 2.625  # Native screenshot density: 420 dpi.
            for y, color, text_color in ((319, "#30313A", "#626472"), (476, "#E0E1EB", "#B4B6C4")):
                content.append(glyph(path, x - actual_size / 2, y - actual_size / 2, actual_size, color))
                content.append(f'<text x="{x}" y="{y + 79}" text-anchor="middle" '
                               f'font-family="Segoe UI" font-size="14" fill="{text_color}">{size} dp</text>')
    content.append('<text x="420" y="610" text-anchor="middle" font-family="Segoe UI" '
                   'font-size="14" fill="#B4B6C4">Vector previews at 420 dpi</text></svg>')
    svg = BUILD / "comparison.svg"
    svg.write_text("\n".join(content), encoding="utf-8", newline="\n")
    subprocess.run([shutil.which("magick"), "-background", "none", str(svg), str(OUTPUT)], check=True)
    print(OUTPUT)


if __name__ == "__main__":
    main()
