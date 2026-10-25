from pathlib import Path
from PIL import Image
p=Path(__file__).parent
im=Image.open(p/'hario-manual-page-6.png')
im.crop((38,54,264,349)).save(p/'hario-filter-install-reference.png')
