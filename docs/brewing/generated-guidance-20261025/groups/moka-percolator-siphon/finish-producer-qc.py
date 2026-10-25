from pathlib import Path
import json
p=Path(__file__).parent
rows=json.loads((p/'manifest.json').read_text())
for row in rows:
 if row['methodId']=='siphon': row['sources'].insert(0,'https://global.hario.com/product/TCAR.pdf')
 row['qc']['notes']=['Producer inspected selected generated PNG at full size and1024x768 candidate at320px on light and dark surfaces.','Mechanical checks passed: alpha preserved, transparent perimeter,1024x768, below300KB.','Independent factual and visual QC required before admission; no approved status assigned.']
 if row['sceneId']=='siphon-prepare': row['qc']['notes'].append('Supplementary first-use cloth soaking subaction; does not explain recurring chain/hook installation. See siphon-install-filter candidate.')
 if row['sceneId']=='siphon-install-filter': row['qc']['notes'].append('Based on current manufacturer page7 geometry. Open stem rim and external spring hook visible; hook small at320px and requires independent explanatory-adequacy review.')
 if row['sceneId']=='moka-serve': row['qc']['notes'].append('Illustrates serving subaction only; cooldown, stirring and compatible cleaning remain in adjacent instructions.')
 if row['sceneId']=='siphon-clean': row['qc']['notes'].append('Illustrates submerged cloth storage subaction only; cooling and glass washing remain in adjacent instructions.')
(p/'manifest.json').write_text(json.dumps(rows,indent=2)+'\n')
print('Producer QC notes and direct manufacturer provenance saved')
