#!/usr/bin/env python3
"""Reproducible, source-derived vector study for the five calculator vessels.

uv run --with vtracer==1.0.0a3 --with pillow==12.3.0 --with numpy==2.3.5 \
    python tools/vectorize_vessel_icons.py

The PNG originals are never modified. Broad color regions are retained while
subpixel grain is removed before fitting genuine cubic Bezier contours.
"""
from __future__ import annotations

import hashlib
import json
import re
import shutil
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path

import vtracer
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'docs/assets/vessel-vector-study'
TEMP = ROOT / 'build/vessel-vector-study/tracing'
KEYS = ('espresso', 'cortado', 'cappuccino', 'mug', 'travel')
NS = '{http://www.w3.org/2000/svg}'


def source(key):
    return ROOT / f'app/src/main/res/drawable-nodpi/vessel_icon_{key}.png'


def prepare(key):
    rgba = Image.open(source(key)).convert('RGBA')
    rgb = rgba.convert('RGB')
    smooth = rgb.filter(ImageFilter.MedianFilter(7)).filter(ImageFilter.GaussianBlur(1.2))
    # Keep original colors at the silhouette; smoothing only occurs safely inside.
    inner = rgba.getchannel('A').filter(ImageFilter.MinFilter(7))
    rgb = Image.composite(smooth, rgb, inner)
    rgb.putalpha(rgba.getchannel('A').point(lambda a: 255 if a >= 128 else 0))
    path = TEMP / f'{key}-simplified.png'
    rgb.save(path)
    return path


def android(svg, output):
    tree = ET.parse(svg)
    lines = ['<vector xmlns:android="http://schemas.android.com/apk/res/android"',
             '    android:width="34dp" android:height="34dp"',
             '    android:viewportWidth="256" android:viewportHeight="256">']
    for p in tree.iter(NS + 'path'):
        transform = p.get('transform', '')
        translate = re.fullmatch(r'translate\(([-\d.]+)[, ]+([-\d.]+)\)', transform)
        if transform and not translate:
            raise ValueError(f'Unsupported SVG transform: {transform}')
        if translate:
            lines.append(f'    <group android:translateX="{translate[1]}" android:translateY="{translate[2]}">')
        lines.append(f'        <path android:fillColor="{p.get("fill")}" android:fillType="nonZero" android:pathData="{p.get("d")}" />')
        if translate:
            lines.append('    </group>')
    lines.append('</vector>')
    output.write_text('\n'.join(lines) + '\n', encoding='utf-8', newline='\n')


def retain_readable_details(key, svg):
    """Restore small source features that color clustering merges away.

    These few smooth paths are intentionally authored against the original
    256px pixels, not claimed as automatic traces. They retain the cap's
    elliptical stacked lid and the bowl/glass left reflections.
    """
    details = {
        'cortado': [
            ('#FFE7AE', 'M61 69 C56 69 55 72 56 78 L65 174 C66 181 68 185 72 185 C77 185 77 180 76 174 L68 77 C68 72 65 69 61 69 Z'),
        ],
        'cappuccino': [
            ('#FFD18F', 'M47 144 C42 145 41 149 42 157 C44 176 48 191 55 201 C57 204 60 206 63 203 C65 201 61 196 59 190 C54 177 54 164 54 151 C54 146 51 143 47 144 Z'),
        ],
        'travel': [
            ('#A26636', 'M81 36 C82 28 103 24 128 24 C151 24 171 28 172 35 C173 43 153 48 128 48 C102 48 81 44 81 36 Z'),
            ('#7E4020', 'M89 35 C90 31 108 28 128 28 C148 28 165 31 165 35 C165 40 147 44 127 44 C108 44 89 40 89 35 Z'),
            ('#894926', 'M95 36 C102 31 149 31 159 35 C163 38 146 41 128 41 C111 41 97 39 95 36 Z'),
            ('#A26636', 'M79 44 C88 51 108 54 128 54 C148 54 165 51 176 44 L179 50 C168 58 149 61 128 61 C106 61 87 58 76 51 Z'),
            ('#7E4020', 'M76 55 C88 64 112 67 137 66 C155 66 171 62 180 57 L179 64 C167 72 145 75 125 75 C103 75 84 71 76 65 Z'),
            ('#66351E', 'M160 47 C166 46 173 43 175 40 L177 47 C173 51 167 54 160 55 Z'),
        ],
    }
    contents = svg.read_text(encoding='utf-8')
    contents = contents.replace('width="256" height="256"', 'width="256" height="256" viewBox="0 0 256 256"')
    if key == 'espresso':
        contents = contents.replace('fill="#683D05"', 'fill="#593306"')
    extra = '\n'.join(f'<path fill="{color}" d="{path}"/>' for color, path in details.get(key, []))
    contents = contents.replace('</svg>', '<!-- Source-shaped detail reconstruction by Astra. -->\n' + extra + '\n</svg>')
    svg.write_text(contents, encoding='utf-8', newline='\n')


def render(svg, size):
    path = TEMP / f'{svg.stem}-{size}.png'
    subprocess.run([shutil.which('magick'), '-background', 'none', '-density', '384', str(svg), '-resize', f'{size}x{size}', str(path)], check=True)
    return Image.open(path).convert('RGBA')


def make_sheet():
    sizes = (34, 89, 256)
    sheet = Image.new('RGBA', (1340, 1230), '#F5F3FA')
    draw = ImageDraw.Draw(sheet)
    font = ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf', 19)
    small = ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf', 16)
    draw.text((25, 18), 'Original raster / simplified vector — identical 256 × 256 source canvas', font=font, fill='#44485A')
    for index, key in enumerate(KEYS):
        x = 25 + index * 262
        draw.text((x, 55), key.title(), font=font, fill='#44485A')
        y = 96
        for size in sizes:
            draw.text((x, y), f'{size}px' + ('  (34dp @ 420dpi)' if size == 89 else ''), font=small, fill='#66697A')
            y += 29
            for kind, icon in [('Raster', Image.open(source(key)).convert('RGBA').resize((size, size), Image.Resampling.LANCZOS)),
                               ('Vector', render(OUT / 'svg' / f'vessel_icon_{key}.svg', size))]:
                draw.text((x, y), kind, font=small, fill='#66697A')
                y += 23
                sheet.alpha_composite(icon, (x, y))
                y += size + 11
            y += 13
    sheet.convert('RGB').save(OUT / 'comparison.png')
    # A short, unlabelled toolbar-scale comparison for immediate visual judgement.
    strip = Image.new('RGBA', (960, 256), '#F5F3FA')
    d = ImageDraw.Draw(strip)
    for row, kind in enumerate(('Original', 'Vector')):
        d.text((18, row * 125 + 42), kind, font=font, fill='#55586A')
        for i, key in enumerate(KEYS):
            icon = (Image.open(source(key)).convert('RGBA').resize((89, 89), Image.Resampling.LANCZOS) if row == 0
                    else render(OUT / 'svg' / f'vessel_icon_{key}.svg', 89))
            strip.alpha_composite(icon, (160 + i * 156, 14 + row * 125))
    strip.convert('RGB').save(OUT / 'toolbar-comparison.png')


def main():
    for path in (OUT / 'svg', OUT / 'android', TEMP):
        path.mkdir(parents=True, exist_ok=True)
    report = []
    for key in KEYS:
        svg = OUT / 'svg' / f'vessel_icon_{key}.svg'
        xml = OUT / 'android' / f'vessel_icon_{key}.xml'
        config = vtracer.Config(clustering='color-cluster', hierarchical='stacked', mode='spline',
                               filter_speckle=28, max_colors=12, color_precision=6,
                               layer_difference=8, simplify=1.1, path_precision=2, optimize=2)
        config.convert_file(str(prepare(key)), str(svg))
        retain_readable_details(key, svg)
        android(svg, xml)
        paths = list(ET.parse(svg).iter(NS + 'path'))
        report.append(dict(icon=key, source_sha256=hashlib.sha256(source(key).read_bytes()).hexdigest(),
                           raster_bytes=source(key).stat().st_size, svg_bytes=svg.stat().st_size,
                           android_bytes=xml.stat().st_size, paths=len(paths),
                           colors=len({p.get('fill') for p in paths}),
                           svg_sha256=hashlib.sha256(svg.read_bytes()).hexdigest(),
                           android_sha256=hashlib.sha256(xml.read_bytes()).hexdigest(),
                           authored_detail_paths={'cortado': 1, 'cappuccino': 1, 'travel': 6}.get(key, 0),
                           commands=sum(len(re.findall('[A-Za-z]', p.get('d', ''))) for p in paths)))
    make_sheet()
    (OUT / 'report.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8', newline='\n')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
