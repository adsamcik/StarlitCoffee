"""Mechanically trace the approved generated notification cup; never author contours.

uv run --no-project --with vtracer==1.0.0a3 --with numpy==2.3.5 --with pillow==12.3.0 python tools/import_timer_notification_cup.py
"""
from pathlib import Path
import hashlib
import json
import xml.etree.ElementTree as ET
import vtracer
import numpy as np
from PIL import Image
import monochrome_icons as trace
from import_approved_vessel_vectors import convert

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "docs/brewing/design/timer-notification-20261008"


def main():
    source = ASSETS / "approved-notification.png"
    pixels = np.asarray(Image.open(source).convert("RGB"), dtype=np.int16)
    height, width, _ = pixels.shape
    # The approved cup occupies the leftmost artwork region, separate from text.
    mask = (pixels.min(axis=2) > 175) & (pixels.max(axis=2) - pixels.min(axis=2) < 60)
    mask[:, :int(width * .09)] = False
    mask[:, int(width * .205):] = False
    ys, xs = np.nonzero(mask)
    bounds = (int(xs.min()) - 3, int(ys.min()) - 3, int(xs.max()) + 4, int(ys.max()) + 4)
    left, top, right, bottom = bounds
    outline = mask[top:bottom, left:right]
    holes = ~outline & ~trace.flood_from_border(~outline)
    # Largest closed cavity is the cup bowl; the handle opening stays empty.
    components = []
    visited = np.zeros_like(holes)
    for y, x in zip(*np.nonzero(holes)):
        if visited[y, x]:
            continue
        points, stack = [], [(y, x)]
        visited[y, x] = True
        while stack:
            cy, cx = stack.pop()
            points.append((cy, cx))
            for ny, nx in ((cy-1, cx), (cy+1, cx), (cy, cx-1), (cy, cx+1)):
                if 0 <= ny < holes.shape[0] and 0 <= nx < holes.shape[1] and holes[ny, nx] and not visited[ny, nx]:
                    visited[ny, nx] = True
                    stack.append((ny, nx))
        components.append(points)
    assert len(components) == 2, "Cup bowl and handle must both remain closed"
    bowl = np.zeros_like(holes)
    for y, x in max(components, key=len):
        bowl[y, x] = True
    report = {"source_sha256": hashlib.sha256(source.read_bytes()).hexdigest(),
              "crop_bounds": bounds, "paths": {}}
    for name, binary in (("outline", outline), ("bowl", bowl)):
        input_file = ASSETS / f"{name}-trace-input.png"
        svg_file = ASSETS / f"{name}.svg"
        Image.fromarray(np.where(binary, 0, 255).astype(np.uint8)).convert("RGB").save(input_file)
        # Trace pixel edges at 8x to avoid shifting a thin outline by half a source pixel.
        hires = ASSETS / f"{name}-trace-hires.png"
        Image.open(input_file).resize((binary.shape[1] * 8, binary.shape[0] * 8), Image.Resampling.NEAREST).save(hires)
        vtracer.Config(clustering="bw", mode="spline", filter_speckle=0, simplify=.1,
                       path_precision=3, optimize=0, binary_threshold=128).convert_file(str(hires), str(svg_file))
        svg = ET.parse(svg_file).getroot()
        svg.attrib["viewBox"] = f"0 0 {binary.shape[1] * 8} {binary.shape[0] * 8}"
        svg_file.write_text(ET.tostring(svg, encoding="unicode"), encoding="utf-8")
        digest = hashlib.sha256(svg_file.read_bytes()).hexdigest()
        vector = convert(svg_file, digest)
        vector = vector.replace('android:height="24dp"', f'android:height="{24*binary.shape[0]/binary.shape[1]:.3f}dp"')
        (ROOT / f"app/src/main/res/drawable/notification_cup_{name}.xml").write_text(vector, encoding="utf-8")
        report["paths"][name] = {"svg_sha256": digest, "filled_pixels": int(binary.sum())}
    (ASSETS / "trace-report.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print("Imported unchanged traced cup outline and bowl contours")


if __name__ == "__main__":
    main()
