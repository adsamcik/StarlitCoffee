"""Render and export the preview-only Pulsar vector; app resources are untouched.

Run: uv run --offline --with pillow==12.3.0 python tools/render_pulsar_concept.py
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
OUT = ROOT / 'docs/assets/method-icon-concept-v3'
TEMP = ROOT / 'build/method-icon-concept-v3'
SVG = OUT / 'pulsar.svg'
NS = '{http://www.w3.org/2000/svg}'
MAGICK = shutil.which('magick') or 'C:/Program Files/ImageMagick-7.1.2-Q16-HDRI/magick.exe'
INK = '#595C69'
BACKGROUND = '#F5F3FA'


def render(size: int, ink: str = INK) -> Image.Image:
    output = TEMP / f'pulsar-{size}.png'
    subprocess.run([MAGICK, '-background', 'none', '-density', str(size * 12), str(SVG),
                    '-resize', f'{size}x{size}', str(output)], check=True, capture_output=True)
    with Image.open(output) as raster:
        alpha = raster.convert('RGBA').getchannel('A')
    image = Image.new('RGBA', alpha.size, ink)
    image.putalpha(alpha)
    return image


def export_android() -> Path:
    source = ET.parse(SVG).getroot()
    if source.tag != NS + 'svg' or source.get('viewBox') != '0 0 32 32':
        raise ValueError('Expected a 32 by 32 SVG viewport')
    if not list(source) or any(path.tag != NS + 'path' for path in source):
        raise ValueError('Expected direct filled vector paths')
    vector = ET.Element('vector', {
        'xmlns:android': 'http://schemas.android.com/apk/res/android',
        'android:width': '24dp', 'android:height': '24dp',
        'android:viewportWidth': '32', 'android:viewportHeight': '32',
    })
    for path in source:
        if (set(path.attrib) - {'fill', 'd', 'fill-rule'} or path.get('fill') != '#000000'
                or not path.get('d') or path.get('fill-rule', 'nonzero') not in ('nonzero', 'evenodd')):
            raise ValueError(f'Unsupported vector path attributes: {path.attrib}')
        ET.SubElement(vector, 'path', {
            'android:fillColor': '#FF000000',
            'android:fillType': 'evenOdd' if path.get('fill-rule') == 'evenodd' else 'nonZero',
            'android:pathData': path.attrib['d'],
        })
    ET.indent(vector, space='    ')
    output = OUT / 'equipment_pulsar.xml'
    output.write_text(ET.tostring(vector, encoding='unicode') + '\n', encoding='utf-8', newline='\n')
    return output


def font(size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf', size)


def main() -> None:
    TEMP.mkdir(parents=True, exist_ok=True)
    vector = export_android()
    enlarged = Image.new('RGBA', (1024, 1024), BACKGROUND)
    enlarged.alpha_composite(render(1024))
    enlarged.convert('RGB').save(OUT / 'pulsar-1024.png')
    sheet = Image.new('RGBA', (640, 480), BACKGROUND)
    draw = ImageDraw.Draw(sheet)
    draw.text((32, 24), 'Pulsar', fill=INK, font=font(26))
    draw.text((32, 63), 'Vector redraw from your photo', fill='#73778A', font=font(17))
    sheet.alpha_composite(render(288), (16, 112))
    draw.text((370, 122), '48dp app badges', fill=INK, font=font(17))
    draw.text((370, 148), 'Rendered at 2.625 px/dp', fill='#73778A', font=font(14))
    for selected, x in ((False, 346), (True, 488)):
        color, ink, radius = ('#4F5F90', '#FAF8FF', 42) if selected else ('#E2E1ED', '#595C69', 63)
        draw.rounded_rectangle((x, 188, x + 125, 313), radius=radius, fill=color)
        sheet.alpha_composite(render(88, ink), (x + 19, 207))
    draw.text((348, 354), '24px', fill='#73778A', font=font(14))
    draw.text((466, 354), '34px', fill='#73778A', font=font(14))
    sheet.alpha_composite(render(24), (401, 353))
    sheet.alpha_composite(render(34), (521, 348))
    sheet.convert('RGB').save(OUT / 'preview.png')
    report = {
        'status': 'preview only; app retains its original icon',
        'source': 'authored paths by Astra; root supplied user-photo measurements and refined valve clearance',
        'svg_sha256': hashlib.sha256(SVG.read_bytes()).hexdigest(),
        'android_sha256': hashlib.sha256(vector.read_bytes()).hexdigest(),
        'alpha_bounds_px': {str(size): render(size).getchannel('A').getbbox() for size in (24, 34, 88, 1024)},
        'validation': 'direct SVG rendering and lossless Android path export; no native app capture',
    }
    (OUT / 'report.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8', newline='\n')
    print('Rendered Pulsar vector preview and study XML; production resources untouched.')


if __name__ == '__main__':
    main()
