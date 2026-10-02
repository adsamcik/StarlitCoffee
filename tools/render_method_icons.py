"""Export the rejected brewing-method study and render review sheets from paths.

Run: uv run --offline --with pillow==12.3.0 python tools/render_method_icons.py
ImageMagick must be available. The four SVGs are editable study sources only;
the before/ directory holds immutable snapshots of the previous traced artwork.
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
OUT = ROOT / 'docs/assets/method-icon-refinement'
TEMP = ROOT / 'build/method-icon-refinement'
DRAWABLES = OUT / 'android'
KEYS = ('pulsar', 'aeropress', 'espresso', 'chemex')
ALL_METHODS = ('pulsar', 'v60', 'french_press', 'aeropress', 'espresso', 'moka_pot', 'cold_brew', 'chemex')
LABELS = {'pulsar': 'Pulsar', 'v60': 'V60', 'french_press': 'French press', 'aeropress': 'AeroPress',
          'espresso': 'Espresso', 'moka_pot': 'Moka pot', 'cold_brew': 'Cold brew', 'chemex': 'Chemex'}
NS = '{http://www.w3.org/2000/svg}'
BACKGROUND = '#F5F3FA'
INK = '#484D60'
MAGICK = shutil.which('magick') or 'C:/Program Files/ImageMagick-7.1.2-Q16-HDRI/magick.exe'


def authored_keys() -> tuple[str, ...]:
    return KEYS


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def source(key: str, before: bool = False) -> Path:
    if before:
        return OUT / 'before' / f'equipment_{key}.svg'
    if key in KEYS:
        return OUT / 'svg' / f'equipment_{key}.svg'
    return ROOT / f'docs/assets/equipment-{key}-traced.svg'


def export_android(key: str) -> Path:
    svg = source(key)
    root = ET.parse(svg).getroot()
    if root.tag != NS + 'svg' or root.attrib != {'width': '32', 'height': '32', 'viewBox': '0 0 32 32'}:
        raise ValueError(f'Unexpected SVG viewport/attributes: {svg}')
    if not list(root) or any(path.tag != NS + 'path' for path in root):
        raise ValueError(f'Only direct filled paths are supported: {svg}')
    vector = ET.Element('vector', {
        'xmlns:android': 'http://schemas.android.com/apk/res/android',
        'android:width': '24dp', 'android:height': '24dp',
        'android:viewportWidth': '32', 'android:viewportHeight': '32',
    })
    for path in root:
        if (set(path.attrib) - {'fill', 'd', 'fill-rule'} or path.get('fill') != '#000000'
                or not path.get('d') or path.get('fill-rule', 'nonzero') not in ('nonzero', 'evenodd')):
            raise ValueError(f'Unsupported path attributes: {svg}: {path.attrib}')
        ET.SubElement(vector, 'path', {
            'android:fillColor': '#FF000000',
            'android:fillType': 'evenOdd' if path.get('fill-rule') == 'evenodd' else 'nonZero',
            'android:pathData': path.attrib['d'],
        })
    ET.indent(vector, space='    ')
    output = DRAWABLES / f'equipment_{key}.xml'
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(ET.tostring(vector, encoding='unicode') + '\n', encoding='utf-8', newline='\n')
    return output


def render(key: str, size: int, before: bool = False, ink: str = INK) -> Image.Image:
    svg = source(key, before)
    viewport = float(ET.parse(svg).getroot().get('width', '32'))
    target = TEMP / f'{key}-{size}-{before}.png'
    # Supersample vectors, never upscale a bitmap for the enlarged previews.
    density = max(96, int(size * 4 * 96 / viewport))
    subprocess.run([MAGICK, '-background', 'none', '-density', str(density), str(svg),
                    '-resize', f'{size}x{size}', str(target)], check=True, capture_output=True)
    with Image.open(target) as raster:
        mask = raster.convert('RGBA').getchannel('A')
    icon = Image.new('RGBA', mask.size, ink)
    icon.putalpha(mask)
    return icon


def font(size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf', size)


def comparison() -> None:
    sheet = Image.new('RGBA', (1120, 780), BACKGROUND)
    draw = ImageDraw.Draw(sheet)
    draw.text((32, 18), 'Rejected method icon study', font=font(30), fill=INK)
    draw.text((32, 63), 'Design history only — original icons restored in the app', font=font(20), fill='#73778A')
    draw.text((32, 152), 'Before', font=font(19), fill='#73778A')
    draw.text((32, 328), 'Rejected', font=font(19), fill=INK)
    draw.text((32, 540), '48px badges', font=font(16), fill='#73778A')
    for i, key in enumerate(KEYS):
        x = 196 + i * 228
        draw.text((x + 16, 110), LABELS[key], font=font(22), fill=INK)
        sheet.alpha_composite(render(key, 128, before=True), (x, 147))
        sheet.alpha_composite(render(key, 176), (x - 24, 309))
        for selected in (False, True):
            bx = x + (72 if selected else 0)
            color, tint, radius = ('#4F5F90', '#FAF8FF', 16) if selected else ('#E2E1ED', '#595C69', 24)
            draw.rounded_rectangle((bx, 516, bx + 47, 563), radius, fill=color)
            sheet.alpha_composite(render(key, 34, ink=tint), (bx + 7, 523))
    draw.line((32, 606, 1088, 606), fill='#DBD9E6', width=1)
    draw.text((32, 622), 'Full method family', font=font(19), fill=INK)
    for i, key in enumerate(ALL_METHODS):
        x = 42 + i * 132
        sheet.alpha_composite(render(key, 64), (x + 22, 660))
        draw.text((x + 54, 738), LABELS[key], anchor='mm', font=font(17), fill=INK)
    sheet.convert('RGB').save(OUT / 'comparison.png')


def small_sizes() -> None:
    sheet = Image.new('RGBA', (780, 354), BACKGROUND)
    draw = ImageDraw.Draw(sheet)
    draw.text((24, 16), 'Actual pixel sizes', font=font(24), fill=INK)
    draw.text((24, 51), '24 / 28 / 34 / 44px — no enlargement', font=font(17), fill='#73778A')
    for col, size in enumerate((24, 28, 34, 44)):
        draw.text((248 + col * 134, 85), str(size), font=font(18), fill=INK)
    for row, key in enumerate(KEYS):
        y = 123 + row * 54
        draw.text((24, y + 12), LABELS[key], font=font(19), fill=INK)
        for col, size in enumerate((24, 28, 34, 44)):
            sheet.alpha_composite(render(key, size), (248 + col * 134, y + (44 - size) // 2))
    sheet.convert('RGB').save(OUT / 'small-sizes.png')


def main() -> None:
    TEMP.mkdir(parents=True, exist_ok=True)
    report = {'status': 'rejected by user; study only; original production icons restored',
              'source': 'authored monochrome SVG paths; manufacturer references in README.md',
              'viewport': '32 × 32; 24dp intrinsic size; about 81% maximum glyph dimension',
              'validation': 'deterministic SVG-to-Android path export and direct SVG raster previews',
              'icons': {}}
    for key in KEYS:
        output = export_android(key)
        report['icons'][key] = {
            'source': str(source(key).relative_to(ROOT)).replace('\\', '/'),
            'svg_sha256': digest(source(key)), 'android_sha256': digest(output),
            'before_sha256': digest(source(key, before=True)),
            'alpha_bounds_at_target_pixels': {str(size): render(key, size).getchannel('A').getbbox()
                                               for size in (24, 28, 34, 44)},
        }
        render(key, 1024).save(OUT / f'{key}-1024.png')
    comparison()
    small_sizes()
    (OUT / 'report.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8', newline='\n')
    print('Exported four study icons and review sheets; app resources untouched.', flush=True)


if __name__ == '__main__':
    main()
