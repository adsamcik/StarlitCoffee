from PIL import Image,ImageOps,ImageDraw
from pathlib import Path
import json,shutil
d=Path(__file__).parent
rows=json.loads((d/'generation-records.json').read_text(encoding='utf-8'))
sources={'moka-pot':['https://m.media-amazon.com/images/I/913VOwvygKL.pdf','https://bialetti-cookware.zendesk.com/hc/en-us/articles/5416235346322-How-to-use-the-Moka-Express'],'percolator':['https://www.gopresto.com/uploads/02822_72_875J_SCM_Eng_Web_Instructions.pdf'],'siphon':['https://www.manualslib.com/manual/4267319/Hario-Coffee-Syphon.html?page=7','https://www.manualslib.com/manual/4267319/Hario-Coffee-Syphon.html?page=8']}
out=[]
for row in rows:
 sid=row['sceneId']
 folder=d/sid;folder.mkdir(exist_ok=True)
 raw=folder/'original.png';shutil.copy2(row['originalPath'],raw)
 (folder/'prompt.txt').write_text(row['prompt']+'\n',encoding='utf-8')
 im=Image.open(raw).convert('RGBA')
 fitted=ImageOps.contain(im,(960,704),Image.Resampling.LANCZOS)
 canvas=Image.new('RGBA',(1024,768));canvas.alpha_composite(fitted,((1024-fitted.width)//2,(768-fitted.height)//2))
 candidate=folder/'candidate.webp';canvas.save(candidate,'WEBP',quality=86,method=4)
 print('Converted',sid,flush=True)
 for surface,color in [('light','#FAF7F1'),('dark','#17191C')]:
  thumb=Image.new('RGBA',(320,240),color)
  thumb.alpha_composite(canvas.resize((320,240),Image.Resampling.LANCZOS));thumb.convert('RGB').save(folder/('phone-'+surface+'.png'))
 rel='docs/brewing/generated-guidance-20261025/groups/moka-percolator-siphon/'+sid+'/'
 out.append({'sceneId':sid,'methodId':row['methodId'],'stepIds':row['stepIds'],'altText':row['altText'],'rawPath':rel+'original.png','candidatePath':rel+'candidate.webp','promptPath':rel+'prompt.txt','sources':sources[row['methodId']],'qc':{'status':'candidate','notes':['Generated through built-in imagegen; final independent QC pending.']}})
 if candidate.stat().st_size>300*1024: raise ValueError(str(candidate)+' exceeds300KB')
(d/'manifest.json').write_text(json.dumps(out,indent=2)+'\n',encoding='utf-8')
print('Converted',len(out),'scenes')
