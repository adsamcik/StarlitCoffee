import fitz
from pathlib import Path
p=Path(__file__).parent
d=fitz.open(p/'hario-manufacturer-reference.pdf')
print(len(d))
for i,page in enumerate(d):
 page.get_pixmap(matrix=fitz.Matrix(1.5,1.5)).save(p/('hario-manual-page-'+str(i)+'.png'))
