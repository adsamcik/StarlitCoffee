from pathlib import Path
from PIL import Image,ImageOps,ImageDraw
import json,shutil,hashlib
p=Path(__file__).parent
rows=json.loads((p/'correction-records-v2.json').read_text())
manifest=json.loads((p/'manifest.json').read_text())
review=[]
for row in rows:
 sid=row['sceneId'];d=p/'corrections'/(sid+'-v2');d.mkdir(parents=True,exist_ok=True)
 raw=d/'original.png';shutil.copy2(row['originalPath'],raw);(d/'prompt.txt').write_text(row['prompt']+'\n')
 im=Image.open(raw).convert('RGBA')
 fitted=ImageOps.contain(im,(960,704),Image.Resampling.LANCZOS);canvas=Image.new('RGBA',(1024,768));canvas.alpha_composite(fitted,((1024-fitted.width)//2,(768-fitted.height)//2))
 f=d/'candidate.webp';canvas.save(f,'WEBP',quality=86,method=4)
 for surface,color in [('light','#FAF7F1'),('dark','#17191C')]:
  phone=Image.new('RGBA',(320,240),color);phone.alpha_composite(canvas.resize((320,240),Image.Resampling.LANCZOS));phone.convert('RGB').save(d/('phone-'+surface+'.png'))
 assert f.stat().st_size<=300*1024
 m=next(x for x in manifest if x['sceneId']==sid);old={k:m[k] for k in ('rawPath','candidatePath','promptPath')};m.setdefault('priorSelections',[]).append(old)
 rel='docs/brewing/generated-guidance-20261025/groups/moka-percolator-siphon/corrections/'+sid+'-v2/'
 m.update(rawPath=rel+'original.png',candidatePath=rel+'candidate.webp',promptPath=rel+'prompt.txt',rawSha256=hashlib.sha256(raw.read_bytes()).hexdigest(),candidateSha256=hashlib.sha256(f.read_bytes()).hexdigest())
 m['qc']={'status':'candidate','notes':['Targeted generator correction after independent QC; previous selected originals/prompts/candidates retained unchanged.','Producer full and phone light/dark inspection; independent corrected-subset recheck required.']}
 if sid.startswith('percolator'):
  m['sources']+=['https://www.gopresto.com/product/basket-for-6-cup-stainless-steel-coffee-maker-94643','https://www.gopresto.com/product/electric-cord-36276']
 if sid=='siphon-install-filter':m['altText']='Close-up of centered siphon cloth filter, tension spring through the open stem and external hook caught on the lower rim.'
 if sid=='siphon-add-coffee':m['altText']='Ground coffee enters the siphon upper chamber held upright in its visible support.'
 if sid=='percolator-load':m['altText']='Coffee loads into square wraparound paper in the solid-sided percolator basket while a fingertip blocks the short center stem.'
 review.append({'sceneId':sid,'rawSha256':m['rawSha256'],'candidateSha256':m['candidateSha256'],'bytes':f.stat().st_size,'path':rel})
(p/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
(p/'corrected-subset-v2.json').write_text(json.dumps(review,indent=2)+'\n')
sheet=Image.new('RGB',(640,len(rows)*264),'#ddd');draw=ImageDraw.Draw(sheet)
for i,row in enumerate(rows):
 d=p/'corrections'/(row['sceneId']+'-v2');draw.text((4,i*264+2),row['sceneId'],fill='#111')
 for j,surface in enumerate(['light','dark']):sheet.paste(Image.open(d/('phone-'+surface+'.png')),(320*j,i*264+24))
sheet.save(p/'corrected-phone-review-v2.png')
print('Corrected',len(rows),'scenes; prior selected files unchanged')
