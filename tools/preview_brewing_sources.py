"""Inspect untouched generated sources on a light canvas at actual UI sizes."""
from pathlib import Path
import json
from PIL import Image, ImageDraw
import numpy as np

root = Path(__file__).resolve().parents[1] / 'docs/assets/brewing-icons'
names = ['coffee-dose', 'grinder', 'water-input', 'cup-output']
sheet = Image.new('RGB', (1200, 480), '#f5f4f9')
draw = ImageDraw.Draw(sheet)
stats = {}
for i, name in enumerate(names):
    icon = Image.open(root / 'source' / (name + '.png')).convert('RGBA')
    alpha = np.asarray(icon)[:, :, 3]
    visible = np.asarray(icon)[:, :, :3][alpha > 127]
    stats[name] = {'size': icon.size, 'alpha_min': int(alpha.min()), 'alpha_max': int(alpha.max()), 'bbox': icon.getbbox(), 'foreground_rgb_range': [int(visible.min()), int(visible.max())]}
    preview = Image.new('RGBA', (280, 300), 'white')
    preview.alpha_composite(icon.resize((280, 280), Image.Resampling.LANCZOS), (0, 10))
    sheet.paste(preview.convert('RGB'), (i * 300 + 10, 35))
    draw.text((i * 300 + 15, 10), name, fill='#292b38')
    for j, size in enumerate([24, 36, 48]):
        tiny = icon.resize((size, size), Image.Resampling.LANCZOS)
        sheet.paste(tiny, (i * 300 + 25 + j * 85, 355), tiny)
        draw.text((i * 300 + 25 + j * 85, 420), str(size) + ' px', fill='#292b38')
sheet.save(root / 'generated-source-review.png')
print(json.dumps(stats, indent=2))
