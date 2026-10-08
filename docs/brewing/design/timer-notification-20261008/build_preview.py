"""Render unchanged traced layers for small-size light/dark review (not native UI evidence)."""
from pathlib import Path
import io
import json
import xml.etree.ElementTree as ET
import numpy as np
import cairosvg
from PIL import Image, ImageDraw, ImageFont

BASE = Path(__file__).resolve().parent
NS = "{http://www.w3.org/2000/svg}"


def paths(name, color):
    return "".join(f'<path fill="{color}" d="{p.attrib["d"]}"/>'
                   for p in ET.parse(BASE / f"{name}.svg").getroot().iter(NS + "path"))


def main():
    root = ET.parse(BASE / "outline.svg").getroot()
    _, _, width, height = map(float, root.attrib["viewBox"].split())
    sheet = Image.new("RGB", (640, 264), "#858585")
    draw = ImageDraw.Draw(sheet)
    font = ImageFont.truetype("C:/Windows/Fonts/segoeui.ttf", 14)
    report = {"rendering": "Cairo SVG contour review; native rendering is a separate instrumentation check",
              "notification_icon_sizes": [40, 48], "sizes": {}}
    source = Image.open(BASE / "outline-trace-input.png").convert("L")
    for size in (24, 32, 40, 48, source.width):
        render_height = round(size * height / width)
        svg = ET.parse(BASE / "outline.svg").getroot()
        rendered = Image.open(io.BytesIO(cairosvg.svg2png(bytestring=ET.tostring(svg), output_width=size, output_height=render_height)))
        alpha = np.asarray(rendered.convert("RGBA"))[:, :, 3] / 255
        reference_alpha = 1 - np.asarray(source.resize((size, render_height), Image.Resampling.LANCZOS)) / 255
        candidate = alpha >= .5
        reference = reference_alpha > .5
        iou = np.count_nonzero(candidate & reference) / np.count_nonzero(candidate | reference)
        error = float(np.mean(np.abs(alpha - reference_alpha)))
        report["sizes"][str(size)] = {"binary_outline_iou": round(iou, 4), "mean_alpha_error": round(error, 4)}
        # Small thin strokes depend strongly on the renderer's antialias filter.
        # Validate source geometry exactly, and separately bound visible small-size differences.
        if size in (40, 48):
            assert error <= .035, f"Tracing lost the source contour at the notification size {size}px: {error}"
        if size == source.width:
            assert iou >= .99, f"Trace altered source pixels: {iou}"
    for row, (background, outline, fill) in enumerate((("#F7F7ED", "#303128", "#657400"), ("#242628", "#FFFBEF", "#BBC56F"))):
        y = row * 128 + 4
        draw.rounded_rectangle((8, y, 632, y + 124), radius=16, fill=background)
        draw.text((24, y + 12), "Light" if row == 0 else "Dark", fill=outline, font=font)
        for column, fraction in enumerate((0, .25, .5, .75, 1)):
            # Only the liquid surface is procedural; every vessel contour is imported unchanged.
            surface = height * (1 - fraction)
            clip = f'<clipPath id="water"><rect y="{surface}" width="{width}" height="{height}"/></clipPath>'
            contents = f'<g clip-path="url(#water)">{paths("bowl", fill)}</g>' if fraction else ""
            svg = f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {width} {height}" width="48" height="{48*height/width}"><defs>{clip}</defs>{contents}{paths("outline", outline)}</svg>'
            icon = Image.open(io.BytesIO(cairosvg.svg2png(bytestring=svg.encode()))).convert("RGBA")
            x = 50 + column * 116
            sheet.paste(icon, (x, y + 46), icon)
            draw.text((x, y + 88), f"{fraction:.0%}", fill=outline, font=font)
    sheet.save(BASE / "cup-preview.png")
    (BASE / "contour-review.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(report)


if __name__ == "__main__":
    main()
