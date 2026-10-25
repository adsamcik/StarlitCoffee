from pathlib import Path
from PIL import Image,ImageOps,ImageDraw
import json,shutil,hashlib
p=Path(__file__).resolve().parent;root=p.parents[4]
records=json.loads((p/'generation-records.json').read_text())
entries=[];mechanical=[]
sheets=[Image.new('RGB',(680,1120),(246,239,229)) for _ in range(2)]
def rel(path):return path.relative_to(root).as_posix()
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
for i,r in enumerate(records):
    scene=p/'scenes'/f"{r['sceneId']}-v{r['version']}";scene.mkdir(parents=True,exist_ok=True)
    raw=scene/'raw.png';shutil.copyfile(r['generatedPath'],raw)
    prompt=scene/'prompt.txt';prompt.write_text(r['prompt'],encoding='utf-8')
    if r.get('status')=='rejected':continue
    im=Image.open(raw).convert('RGBA');rawsize=im.size
    im=ImageOps.contain(im,(960,720),Image.Resampling.LANCZOS)
    delivery=Image.new('RGBA',(1024,768));delivery.alpha_composite(im,((1024-im.width)//2,(768-im.height)//2))
    candidate=scene/'candidate.webp';delivery.save(candidate,'WEBP',quality=86,method=4)
    actual=Image.open(candidate).convert('RGBA')
    for col,(name,color) in enumerate([('light',(244,225,210,255)),('dark',(79,64,55,255))]):
        full=Image.new('RGBA',actual.size,color);full.alpha_composite(actual);full.convert('RGB').save(scene/f'full-{name}.png')
        phone=full.resize((320,240),Image.Resampling.LANCZOS).convert('RGB');phone.save(scene/f'phone-{name}.png')
        sheet=sheets[i//4];sheet.paste(phone,(10+col*340,(i%4)*280+30));ImageDraw.Draw(sheet).text((10,(i%4)*280+8),r['sceneId'],fill=(40,25,15))
    entry={k:r[k] for k in ['sceneId','methodId','stepIds','learnStageNumbers','altText','sources']}|{'rawPath':rel(raw),'candidatePath':rel(candidate),'promptPath':rel(prompt),'rawSha256':sha(raw),'candidateSha256':sha(candidate),'promptSha256':sha(prompt),'qc':{'status':'candidate','notes':['Producer inspected full raw, delivered and phone light/dark before handoff. Independent admission pending.','Image depicts action/state only; exact quantities, timings and completion remain in authored guidance.']}}
    entries.append(entry)
    corners=[actual.getpixel(c)[3] for c in [(0,0),(1023,0),(0,767),(1023,767)]]
    assert candidate.stat().st_size<=300000 and actual.size==(1024,768) and corners==[0]*4
    mechanical.append({'sceneId':r['sceneId'],'rawSize':rawsize,'deliverySize':actual.size,'bytes':candidate.stat().st_size,'cornerAlpha':corners,'rawSha256':sha(raw),'candidateSha256':sha(candidate)})
(p/'manifest.json').write_text(json.dumps(entries,indent=2)+'\n',encoding='utf-8')
(p/'mechanical-qc.json').write_text(json.dumps(mechanical,indent=2)+'\n',encoding='utf-8')
for n,sheet in enumerate(sheets):sheet.save(p/f'phone-review-{n+1}.png')
print(json.dumps(mechanical,indent=2))
