from pathlib import Path
import json
from PIL import Image,ImageDraw
p=Path(__file__).parent
rows=json.loads((p/'manifest.json').read_text())
for start in range(0,len(rows),8):
 group=rows[start:start+8]; sheet=Image.new('RGB',(640,len(group)*264),'#ddd')
 draw=ImageDraw.Draw(sheet)
 for i,row in enumerate(group):
  d=p/row['sceneId']; draw.text((4,i*264+2),row['sceneId'],fill='#111')
  for j,s in enumerate(['light','dark']):
   sheet.paste(Image.open(d/('phone-'+s+'.png')),(320*j,i*264+24))
 sheet.save(p/('phone-review-'+str(start//8+1)+'.png'))
print('Contact sheets ready')
