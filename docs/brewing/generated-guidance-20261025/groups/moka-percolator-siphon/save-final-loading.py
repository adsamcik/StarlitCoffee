from pathlib import Path
from PIL import Image,ImageOps,ImageDraw
import shutil,json,hashlib
p=Path(__file__).parent
row=json.loads((p/'correction-records-v3.json').read_text())[0];sid=row['sceneId'];d=p/'corrections'/(sid+'-v3');d.mkdir(parents=True,exist_ok=True)
raw=d/'original.png';shutil.copy2(row['originalPath'],raw);(d/'prompt.txt').write_text(row['prompt']+'\n')
im=Image.open(raw).convert('RGBA');fit=ImageOps.contain(im,(960,704),Image.Resampling.LANCZOS);c=Image.new('RGBA',(1024,768));c.alpha_composite(fit,((1024-fit.width)//2,(768-fit.height)//2));f=d/'candidate.webp';c.save(f,'WEBP',quality=86,method=4)
for surface,color in [('light','#FAF7F1'),('dark','#17191C')]:
 phone=Image.new('RGBA',(320,240),color);phone.alpha_composite(c.resize((320,240),Image.Resampling.LANCZOS));phone.convert('RGB').save(d/('phone-'+surface+'.png'))
manifest=json.loads((p/'manifest.json').read_text());m=next(x for x in manifest if x['sceneId']==sid);m['priorSelections'].append({k:m[k] for k in ['rawPath','candidatePath','promptPath','rawSha256','candidateSha256']})
rel='docs/brewing/generated-guidance-20261025/groups/moka-percolator-siphon/corrections/'+sid+'-v3/'
m.update(rawPath=rel+'original.png',candidatePath=rel+'candidate.webp',promptPath=rel+'prompt.txt',rawSha256=hashlib.sha256(raw.read_bytes()).hexdigest(),candidateSha256=hashlib.sha256(f.read_bytes()).hexdigest())
(p/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
ids=[x['sceneId'] for x in json.loads((p/'corrected-subset-v2.json').read_text())]
sheet=Image.new('RGB',(640,len(ids)*264),'#ddd');draw=ImageDraw.Draw(sheet);report=[]
for i,sid in enumerate(ids):
 m=next(x for x in manifest if x['sceneId']==sid);repo=p.parents[4];folder=repo/Path(m['candidatePath']).parent
 draw.text((4,i*264+2),sid,fill='#111')
 for j,surface in enumerate(['light','dark']):sheet.paste(Image.open(folder/('phone-'+surface+'.png')),(320*j,i*264+24))
 report.append({k:m[k] for k in ['sceneId','rawPath','candidatePath','rawSha256','candidateSha256']})
sheet.save(p/'corrected-phone-review-final.png');(p/'corrected-subset-final.json').write_text(json.dumps(report,indent=2)+'\n')
print('Final six-scene subset saved')
