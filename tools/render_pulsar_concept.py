"""Render and export the preview-only Pulsar vector; app resources are untouched.

Run: uv run --offline --with pillow==12.3.0 python tools/render_pulsar_concept.py
"""

from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--version', type=int, choices=(3, 4, 5, 6, 7), default=3)
VERSION = parser.parse_args().version
OUT = ROOT / f'docs/assets/method-icon-concept-v{VERSION}'
TEMP = ROOT / f'build/method-icon-concept-v{VERSION}'
SVG = OUT / 'pulsar.svg'
NS = '{http://www.w3.org/2000/svg}'
MAGICK = shutil.which('magick') or 'C:/Program Files/ImageMagick-7.1.2-Q16-HDRI/magick.exe'
INK = '#595C69'
BACKGROUND = '#F5F3FA'


def render(size: int, ink: str = INK, source: Path = SVG) -> Image.Image:
    output = TEMP / f'{source.parent.name}-{size}.png'
    viewport = float(ET.parse(source).getroot().get('width', '32'))
    density = max(96, round(size * 4 * 96 / viewport))
    subprocess.run([MAGICK, '-background', 'none', '-density', str(density), str(source),
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
        if (set(path.attrib) - {'fill', 'd', 'fill-rule', 'fill-opacity'} or path.get('fill') != '#000000'
                or not path.get('d') or path.get('fill-rule', 'nonzero') not in ('nonzero', 'evenodd')):
            raise ValueError(f'Unsupported vector path attributes: {path.attrib}')
        attributes = {
            'android:fillColor': '#FF000000',
            'android:fillType': 'evenOdd' if path.get('fill-rule') == 'evenodd' else 'nonZero',
            'android:pathData': path.attrib['d'],
        }
        if 'fill-opacity' in path.attrib:
            opacity = float(path.attrib['fill-opacity'])
            if not 0 <= opacity <= 1:
                raise ValueError('Path opacity must be between zero and one')
            attributes['android:fillAlpha'] = path.attrib['fill-opacity']
        ET.SubElement(vector, 'path', attributes)
    ET.indent(vector, space='    ')
    output = OUT / 'equipment_pulsar.xml'
    output.write_text(ET.tostring(vector, encoding='unicode') + '\n', encoding='utf-8', newline='\n')
    return output


def font(size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf', size)


def family_comparison() -> None:
    sheet = Image.new('RGBA', (800, 432), BACKGROUND)
    draw = ImageDraw.Draw(sheet)
    draw.text((28, 18), 'Original style · Pulsar redrawn', fill=INK, font=font(25))
    draw.text((28, 56), 'AeroPress, Espresso and Chemex are unchanged', fill='#73778A', font=font(16))
    draw.text((24, 137), 'Before', fill='#73778A', font=font(16))
    draw.text((24, 302), 'Now', fill=INK, font=font(16))
    before = ROOT / 'docs/assets/method-icon-refinement/before'
    for key, label, x in (('pulsar', 'Pulsar', 102), ('aeropress', 'AeroPress', 280),
                          ('espresso', 'Espresso', 458), ('chemex', 'Chemex', 636)):
        original = before / f'equipment_{key}.svg'
        draw.text((x + 60, 87), label, anchor='mt', fill=INK, font=font(19))
        sheet.alpha_composite(render(120, source=original), (x, 116))
        sheet.alpha_composite(render(120, source=SVG if key == 'pulsar' else original), (x, 278))
    sheet.convert('RGB').save(OUT / 'family-comparison.png')


def main() -> None:
    TEMP.mkdir(parents=True, exist_ok=True)
    vector = export_android()
    enlarged = Image.new('RGBA', (1024, 1024), BACKGROUND)
    enlarged.alpha_composite(render(1024))
    enlarged.convert('RGB').save(OUT / 'pulsar-1024.png')
    sheet = Image.new('RGBA', (640, 480), BACKGROUND)
    draw = ImageDraw.Draw(sheet)
    draw.text((32, 24), 'Pulsar', fill=INK, font=font(26))
    subtitle = ('Original style · revised brewer proportions' if VERSION == 7 else
                'Accepted artwork · corrected right foot' if VERSION == 6 else
                'Designed for small icons' if VERSION == 5 else
                'Vector redraw from your photo' if VERSION == 3 else
                'Dispersion cap · flat bed · flow valve')
    draw.text((32, 63), subtitle, fill='#73778A', font=font(17))
    sheet.alpha_composite(render(288), (16, 112))
    draw.text((370, 122), '48dp app badges', fill=INK, font=font(17))
    draw.text((370, 148), 'Rendered at 2.625 px/dp', fill='#73778A', font=font(14))
    for selected, x in ((False, 346), (True, 488)):
        color, ink, radius = ('#4F5F90', '#FAF8FF', 42) if selected else ('#E2E1ED', '#595C69', 63)
        draw.rounded_rectangle((x, 188, x + 125, 313), radius=radius, fill=color)
        sheet.alpha_composite(render(88, ink), (x + 19, 207))
    if VERSION == 7:
        draw.text((336, 339), 'Actual pixel sizes', fill='#73778A', font=font(14))
        for size, x in ((24, 350), (28, 414), (34, 478), (44, 548)):
            draw.text((x, 369), f'{size}px', fill='#73778A', font=font(14))
            sheet.alpha_composite(render(size), (x, 397 + (44 - size) // 2))
        family_comparison()
    elif VERSION in (5, 6):
        previous = ROOT / 'docs/assets/method-icon-concept-v4/pulsar.svg'
        draw.text((336, 369), 'Previous', fill='#73778A', font=font(14))
        draw.text((336, 413), 'Revised', fill=INK, font=font(14))
        for size, x in ((24, 444), (28, 508), (34, 572)):
            draw.text((x - 2, 339), f'{size}px', fill='#73778A', font=font(14))
            sheet.alpha_composite(render(size, source=previous), (x, 362 + (34 - size) // 2))
            sheet.alpha_composite(render(size), (x, 406 + (34 - size) // 2))
    else:
        for size, x in ((24, 348), (28, 444), (34, 548)):
            draw.text((x, 344), f'{size}px', fill='#73778A', font=font(14))
            sheet.alpha_composite(render(size), (x + 8, 376 + (34 - size) // 2))
    sheet.convert('RGB').save(OUT / 'preview.png')
    production = ROOT / 'app/src/main/res/drawable/equipment_pulsar.xml'
    production_matches = production.read_bytes() == vector.read_bytes()
    report = {
        'status': ('rejected by user; app retains its original icon' if VERSION in (3, 5) else
                   'production resource matches this export' if VERSION in (6, 7) and production_matches else
                   'preview only; production resource differs from this export'),
        'source': ('root-authored solid pictogram in the original family style after Astra construction review'
                   if VERSION == 7 else
                   'root-authored photo-based redraw after Astra reviewed the rejected simplification'
                   if VERSION == 6 else
                   'root-authored simplification with Astra small-size review' if VERSION == 5 else
                   'authored paths by Astra; root supplied user-photo measurements and refined valve clearance'
                   if VERSION == 3 else 'authored paths by Astra from primary photos; root refined joins and valve clearance'),
        'svg_sha256': hashlib.sha256(SVG.read_bytes()).hexdigest(),
        'android_sha256': hashlib.sha256(vector.read_bytes()).hexdigest(),
        'production_resource_matches': production_matches,
        'alpha_bounds_px': {str(size): render(size).getchannel('A').getbbox() for size in (24, 28, 34, 88, 1024)},
        'validation': 'direct SVG rendering and lossless Android path export; no native app capture',
    }
    native_evidence = OUT / 'native/validation.json'
    if native_evidence.is_file():
        native = json.loads(native_evidence.read_text(encoding='utf-8'))
        if native.get('android_sha256') == report['android_sha256']:
            report['native_validation'] = 'native/validation.json'
            report['validation'] = ('direct SVG rendering and lossless Android path export; '
                                    'matching native Compose fixture captures in light and dark themes')
    (OUT / 'report.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8', newline='\n')
    print('Rendered Pulsar vector preview and study XML; production resources untouched.')


if __name__ == '__main__':
    main()
