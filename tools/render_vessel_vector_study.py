"""Export Android paths and preview the hand-authored refined SVG study.

The committed SVGs are the editable artwork, not generated traces. This tool
does not modify them or the production PNGs. It renders directly from vectors.
Run: uv run --offline --with pillow==12.3.0 python tools/render_vessel_vector_study.py
"""

from __future__ import annotations

import hashlib
import json
import shutil
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'docs/assets/vessel-vector-study/refined'
TEMP = ROOT / 'build/vessel-vector-study/refined'
KEYS = ('espresso', 'cortado', 'cappuccino', 'mug', 'travel')
NS = '{http://www.w3.org/2000/svg}'
BACKGROUND = '#F5F3FA'
INK = '#484D60'
MAGICK = shutil.which('magick') or 'C:/Program Files/ImageMagick-7.1.2-Q16-HDRI/magick.exe'


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def source(key: str) -> Path:
    return ROOT / f'app/src/main/res/drawable-nodpi/vessel_icon_{key}.png'


def export_android(svg: Path, output: Path) -> list[ET.Element]:
    tree = ET.parse(svg)
    root = tree.getroot()
    if root.get('viewBox') != '0 0 256 256':
        raise ValueError(f'Unexpected viewport: {svg}')
    paths = list(root)
    if not paths or any(p.tag != NS + 'path' for p in paths):
        raise ValueError(f'Only direct path geometry is supported: {svg}')
    vector = ET.Element('vector', {
        'xmlns:android': 'http://schemas.android.com/apk/res/android',
        'android:width': '34dp', 'android:height': '34dp',
        'android:viewportWidth': '256', 'android:viewportHeight': '256',
    })
    for path in paths:
        if set(path.attrib) - {'fill', 'd', 'fill-rule'}:
            raise ValueError(f'Unsupported path attributes in {svg}: {path.attrib}')
        ET.SubElement(vector, 'path', {
            'android:fillColor': path.attrib['fill'],
            'android:fillType': 'evenOdd' if path.get('fill-rule') == 'evenodd' else 'nonZero',
            'android:pathData': path.attrib['d'],
        })
    ET.indent(vector, space='    ')
    output.write_text(ET.tostring(vector, encoding='unicode') + '\n', encoding='utf-8', newline='\n')
    return paths


def render(key: str, size: int) -> Image.Image:
    svg = OUT / 'svg' / f'vessel_icon_{key}.svg'
    target = TEMP / f'{key}-{size}.png'
    subprocess.run([MAGICK, '-background', 'none', '-density', '384',
                    str(svg), '-resize', f'{size}x{size}', str(target)], check=True)
    return Image.open(target).convert('RGBA')


def font(size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf', size)


def enlarged_sheet() -> None:
    sheet = Image.new('RGBA', (1152, 1800), BACKGROUND)
    draw = ImageDraw.Draw(sheet)
    draw.text((40, 22), 'Redrawn vector study', font=font(30), fill=INK)
    draw.text((40, 64), 'Smooth paths, simplified warm shading', font=font(22), fill='#73778A')
    for i, key in enumerate(KEYS):
        x = 40 + (i % 2) * 560 if i < 4 else 320
        y = 128 + (i // 2) * 550
        draw.text((x, y), key.title(), font=font(25), fill=INK)
        sheet.alpha_composite(render(key, 512), (x, y + 32))
        render(key, 1024)
    sheet.convert('RGB').save(OUT / 'enlarged.png')


def comparison(size: int) -> None:
    width = 960 if size == 89 else 690
    gap = 152 if size == 89 else 102
    first = 165 if size == 89 else 170
    row_height = size + 48
    sheet = Image.new('RGBA', (width, 36 + 2 * row_height), BACKGROUND)
    draw = ImageDraw.Draw(sheet)
    draw.text((22, 12), f'{size}px comparison: original and redrawn vectors', font=font(18), fill=INK)
    for row in range(2):
        y = 45 + row * row_height
        draw.text((22, y + size // 2 - 10), 'Original' if row == 0 else 'Redrawn', font=font(18), fill=INK)
        for i, key in enumerate(KEYS):
            icon = (Image.open(source(key)).convert('RGBA').resize((size, size), Image.Resampling.LANCZOS)
                    if row == 0 else render(key, size))
            sheet.alpha_composite(icon, (first + i * gap, y))
    sheet.convert('RGB').save(OUT / f'comparison-{size}px.png')


def main() -> None:
    (OUT / 'android').mkdir(parents=True, exist_ok=True)
    TEMP.mkdir(parents=True, exist_ok=True)
    report = []
    for key in KEYS:
        svg = OUT / 'svg' / f'vessel_icon_{key}.svg'
        xml = OUT / 'android' / f'vessel_icon_{key}.xml'
        paths = export_android(svg, xml)
        report.append({
            'icon': key, 'paths': len(paths),
            'colors': len({p.attrib['fill'] for p in paths}),
            'source_sha256': digest(source(key)), 'svg_sha256': digest(svg),
            'android_sha256': digest(xml), 'svg_bytes': svg.stat().st_size,
            'android_bytes': xml.stat().st_size,
        })
    enlarged_sheet()
    comparison(89)
    comparison(34)
    (OUT / 'report.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8', newline='\n')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
