from pathlib import Path
import json,hashlib
from PIL import Image
p=Path(__file__).parent
rows=json.loads((p/'manifest.json').read_text())
report=[]
for row in rows:
 d=p/row['sceneId']; f=d/'candidate.webp'; im=Image.open(f).convert('RGBA'); a=im.getchannel('A')
 report.append({'sceneId':row['sceneId'],'width':im.width,'height':im.height,'bytes':f.stat().st_size,'alphaExtrema':a.getextrema(),'perimeterTransparent':all(a.getpixel(xy)==0 for xy in [(0,0),(1023,0),(0,767),(1023,767),(512,0),(512,767),(0,384),(1023,384)]),'rawSha256':hashlib.sha256((d/'original.png').read_bytes()).hexdigest(),'candidateSha256':hashlib.sha256(f.read_bytes()).hexdigest()})
assert all(x['width']==1024 and x['height']==768 and x['bytes']<=300*1024 and x['perimeterTransparent'] for x in report)
(p/'mechanical-qc.json').write_text(json.dumps(report,indent=2)+'\n')
print(len(report),'candidates; bytes',sum(x['bytes'] for x in report),'maximum',max(x['bytes'] for x in report))
