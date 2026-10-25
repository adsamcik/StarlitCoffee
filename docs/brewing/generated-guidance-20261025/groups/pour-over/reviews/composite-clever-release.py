from PIL import Image
from pathlib import Path
p=Path(__file__).resolve().parents[1]/'candidates/clever_large_release_v1.webp'
im=Image.open(p).convert('RGBA')
for name,color in [('light',(244,225,210,255)),('dark',(79,64,55,255))]:
    bg=Image.new('RGBA',im.size,color); bg.alpha_composite(im); bg.convert('RGB').save(Path(__file__).parent/f'clever-release-full-{name}.png')
