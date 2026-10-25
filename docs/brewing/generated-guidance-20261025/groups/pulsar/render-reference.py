from pathlib import Path
import fitz
p=Path(__file__).resolve().parent/'references'
d=fitz.open(p/'PulsarUserGuide.pdf')
for n,page in enumerate(d):page.get_pixmap(matrix=fitz.Matrix(1.5,1.5)).save(p/f'manual-page-{n+1}.png')
