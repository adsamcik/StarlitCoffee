"""Reproduce mechanical delivery only; no visual edits."""
import hashlib,json,shutil
from pathlib import Path
from PIL import Image,ImageOps
ROOT=Path(__file__).resolve().parents[5]
GROUP=Path(__file__).resolve().parent
specs=json.loads((GROUP/'production-inputs.json').read_text(encoding='utf-8'))
attempts=json.loads((GROUP/'generation-attempts.json').read_text(encoding='utf-8'))
draft=json.loads((ROOT/'docs/brewing/generated-guidance-20261025/bindings-draft.json').read_text(encoding='utf-8'))
targets=draft['bindings']+draft['unresolved']
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
rel=lambda p:p.relative_to(ROOT).as_posix()
entries=[]
for a in attempts:
 s=next(s for s in specs if s['id']==a['sceneId'])
 scene=GROUP/'scenes'/s['id'];scene.mkdir(parents=True,exist_ok=True)
 v=a['version']
 raw=scene/f'raw-v{v}.png';prompt=scene/f'prompt-v{v}.txt';candidate=scene/f'candidate-v{v}.webp'
 if not raw.exists():shutil.copyfile(a['path'],raw)
 prompt.write_text(a['prompt'],encoding='utf-8')
 im=Image.open(raw);im.load()
 if im.mode!='RGBA':raise ValueError(f'{s["id"]}: raw is not RGBA')
 contained=ImageOps.contain(im,(896,672),Image.Resampling.LANCZOS)
 canvas=Image.new('RGBA',(1024,768),(0,0,0,0))
 canvas.alpha_composite(contained,((1024-contained.width)//2,(768-contained.height)//2))
 canvas.save(candidate,'WEBP',quality=92,method=4,exact=True)
 if candidate.stat().st_size>300000:raise ValueError(f'{s["id"]}: exceeds300KB')
 phones={}
 for theme,color in [('light','#F3E0D2'),('dark','#514339')]:
  card=Image.new('RGBA',(320,240),color)
  thumb=canvas.resize((320,240),Image.Resampling.LANCZOS)
  card.alpha_composite(thumb)
  out=scene/f'phone-{theme}-v{v}.png';card.convert('RGB').save(out)
  phones[theme]=rel(out)
 selected=not(s['id']=='espresso_generic_extract' and v==1)
 rows=[r for r in targets if r.get('source_id') in s['missing']]
 notes=['Producer inspected full raw, delivered candidate and320px light/dark previews; independent QC still required.',
        'Only visible physical subaction is illustrated; existing instruction owns quantities, controls and completion.']
 if s['id']=='espresso_generic_extract':
  notes+=['No machine controls, dimensions or basket capacity transfer. Clear glass cup in selectedv2 avoids impossible opaque-cup cutaway.']
 if s['id']=='phin_neutral_support':
  notes+=['Low opaque exterior hides floor/insert/post; no visible fluid or grounds. Does not prove holes clean or dry; accompanying source checks remain.']
 if s['id']=='phin_neutral_pour':
  notes+=['Opaque low view deliberately hides chosen insert. Pour quantity, headroom and initial-vs-remaining timing remain outside artwork.']
 if s['id']=='phin_neutral_selected_insert':
  notes+=['Paired alternatives are NOT a sequence or two inserts in one brewer. Loose disc and matching threaded post/hub are distinguished. Selected manufacturer fitting/tightness remains textual.']
 if s['id']=='phin_neutral_remove':
  notes+=['Thick mitts protect grip at opposite outer plate edges; covered chamber stays upright, no drips. Does not claim every phin has cool handles; use actual manufacturer-safe handling point.']
 if s['id']=='cezve_optional_sugar':
  notes+=['Optional ingredient only if selected recipe includes it; cold off-heat preparation, not sugar required for every brew.']
 if not selected:notes=['Rejected by producer: opaque cream cup was rendered as see-through; replaced with real clear-glass cupv2.']
 contexts=[]
 for r in rows:
  contexts.append({'contentId':r['content_id'],'targetFamilyId':r['target_family_id'],
   'targetProfileId':r['target_profile_id'],'targetContentRevision':r['target_content_revision'],
   'targetCatalog':r['target_catalog'],'altText':s['alt'],'reviewStatus':'candidate',
   'scopeNotes':notes[1:]})
 entries.append({'sceneId':s['id'],'methodId':'neutral-details','version':v,
  'conceptual':s['id']=='phin_neutral_selected_insert',
  'sourcePhysicalFamilyId': 'espresso' if s['id'].startswith('espresso') else 'heated_unfiltered' if s['id'].startswith('cezve') else 'restricted_flow_gravity_concentrate',
  'sourcePhysicalProfileId':None,'stepIds':[r['content_id'] for r in rows],
  'targetContentIds':[r['content_id'] for r in rows] if selected else [],
  'altText':s['alt'],'rawPath':rel(raw),'promptPath':rel(prompt),'candidatePath':rel(candidate),
  'phonePreviews':phones,'rawSha256':sha(raw),'promptSha256':sha(prompt),'candidateSha256':sha(candidate),
  'sources':[{'url':u,'purpose':'Primary hardware/action reference; no unrelated quantities or operating controls transfer.'} for u in s['sources']],
  'referenceImages':s.get('references',[]),
  'sourceContracts':[r['target_contract'] for r in rows],
  'bindingContexts':contexts if selected else [],'qc':{'status':'candidate' if selected else 'rejected','notes':notes}})
doc={'schemaVersion':1,'generatedOn':'2026-10-25','generator':'Codex built-in image_gen',
 'conversion':'Original RGBA preserved. Lanczos contain within896x672 centered transparent1024x768; WebPquality92 method4 exactalpha;320x240 light/dark. No visual hand edits.',
 'scope':'Minimal neutral physical details. Unknown phin internals hidden except explicit paired mechanism alternatives. No automatic control selection, capacity or quantity claim.',
 'selectedSceneCount':sum(e['qc']['status']=='candidate' for e in entries),
 'targetContextCount':len({c for e in entries if e['qc']['status']=='candidate' for c in e['targetContentIds']}),
 'entries':entries}
(GROUP/'manifest.json').write_text(json.dumps(doc,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
generic=json.loads((ROOT/'docs/brewing/generated-guidance-20261025/groups/generic-concepts/manifest.json').read_text(encoding='utf-8'))
level=next(r for r in generic['entries'] if r['sceneId']=='concept_coffee_level' and r['qc']['status']=='candidate')
reuse={'schemaVersion':1,'reviewStatus':'candidate','sourceSceneId':'concept_coffee_level','sourcePath':level['candidatePath'],
 'sourceSha256':level['candidateSha256'],'scope':'Conceptual loose level coffee bed only; no phin mechanism or actual loading motion inferred.',
 'bindings':[{'contentId':r['content_id'],'targetFamilyId':r['target_family_id'],'targetProfileId':r['target_profile_id'],
 'targetContentRevision':r['target_content_revision'],'targetCatalog':r['target_catalog'],
 'altText':'Conceptual end state: loose coffee forms an even level bed in an open receptacle; no phin insert mechanism is shown.',
 'notes':'Common loose coffee/level-bed subaction only. Selected dose and actual phin loading/fitting remain in source instruction.'}
 for r in targets if r.get('source_id') in ('phin_neutral_add_coffee','phin_neutral_level')]}
(GROUP/'reuse-proposals.json').write_text(json.dumps(reuse,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(f'{doc["selectedSceneCount"]}candidate scenes;{doc["targetContextCount"]}new-scene target contexts;{len(reuse["bindings"])}reuse contexts.')
for e in entries:print(e['sceneId'],e['version'],e['qc']['status'],(ROOT/e['candidatePath']).stat().st_size)
