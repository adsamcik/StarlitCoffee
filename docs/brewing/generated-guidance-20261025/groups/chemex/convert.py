from pathlib import Path
from PIL import Image,ImageOps,ImageDraw
import json,shutil,hashlib
p=Path(__file__).resolve().parent
root=p.parents[4]
records=json.loads((p/'generation-records.json').read_text())
entries=[]
sheet=Image.new('RGB',(680,5*280),(246,239,229)); d=ImageDraw.Draw(sheet)
def rel(path): return path.relative_to(root).as_posix()
def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()
for i,r in enumerate(records):
    scene=p/'scenes'/f"{r['sceneId']}-v{r['version']}";scene.mkdir(parents=True,exist_ok=True)
    raw=scene/'raw.png';shutil.copyfile(r['generatedPath'],raw)
    prompt=scene/'prompt.txt';prompt.write_text(r['prompt'],encoding='utf-8')
    if r.get('status')=='rejected':continue
    im=Image.open(raw).convert('RGBA')
    im=ImageOps.contain(im,(960,720),Image.Resampling.LANCZOS)
    delivery=Image.new('RGBA',(1024,768));delivery.alpha_composite(im,((1024-im.width)//2,(768-im.height)//2))
    candidate=scene/'candidate.webp';delivery.save(candidate,'WEBP',quality=86,method=4)
    actual=Image.open(candidate).convert('RGBA')
    for col,(name,color) in enumerate([('light',(244,225,210,255)),('dark',(79,64,55,255))]):
        full=Image.new('RGBA',actual.size,color);full.alpha_composite(actual);full.convert('RGB').save(scene/f'full-{name}.png')
        phone=full.resize((320,240),Image.Resampling.LANCZOS).convert('RGB');phone.save(scene/f'phone-{name}.png')
        sheet.paste(phone,(10+col*340,i*280+30));d.text((10,i*280+8),r['sceneId'],fill=(40,25,15))
    entries.append({k:r[k] for k in ['sceneId','methodId','stepIds','bindingSourceIds','targetContentIds','altText','sources']}|{'rawPath':rel(raw),'candidatePath':rel(candidate),'promptPath':rel(prompt),'rawSha256':sha(raw),'candidateSha256':sha(candidate),'promptSha256':sha(prompt),'qc':{'status':'candidate','notes':['Producer inspected full raw; delivery and 320px light/dark inspection recorded after conversion. Independent admission pending.','Conceptual closeup does not assert overall dimensions or capacity.' if 'unspecified' in r['sceneId'] else 'Six-cup Classic geometry; recipe numbers remain outside illustration.']}})
    assert candidate.stat().st_size<=300000
    assert actual.size==(1024,768)
    assert all(actual.getpixel(c)[3]==0 for c in [(0,0),(1023,0),(0,767),(1023,767)])
(p/'manifest.json').write_text(json.dumps(entries,indent=2)+'\n',encoding='utf-8')
sheet.save(p/'phone-review.png')
print(json.dumps([{'sceneId':e['sceneId'],'candidateSha256':e['candidateSha256'],'bytes':(root/e['candidatePath']).stat().st_size} for e in entries],indent=2))
