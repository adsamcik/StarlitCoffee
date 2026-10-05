"""Record preserved generator outputs and verify the selected trace inputs."""
from pathlib import Path
import hashlib
import json

BASE = Path(__file__).resolve().parent
ROOT = next(parent for parent in BASE.parents if (parent / 'app/src/main').is_dir())


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    catalog = json.loads((BASE / 'catalog.json').read_text())['entries']
    first = json.loads((BASE / 'generation-provenance.json').read_text())
    edits = json.loads((BASE / 'refinement-provenance.json').read_text())['entries']
    calls = first['initial'] + first['lid_refinements'] + edits
    assert len(calls) == 41
    report = json.loads((BASE / 'trace-report.json').read_text())['icons']
    keys = [entry['key'] for entry in catalog]
    assert len(keys) == 28 and set(keys) == set(report)
    originals = []
    references = []
    vectors = []
    for entry in catalog:
        key = entry['key']
        reference = BASE / entry['anatomy_reference']
        assert sha(reference) == sha(ROOT / entry['native_resource']), key
        references.append({'file':entry['anatomy_reference'], 'sha256':sha(reference),
                           'role':'Unchanged native vessel anatomy'})
        history = [call for call in calls if call['key'] == key]
        assert history
        for index, call in enumerate(history, 1):
            selected = index == len(history)
            file = f'generated/{key}.png' if selected else f'discarded/{key}-v{index}.png'
            originals.append({'key':key, 'revision':index, 'selected':selected,
                              'generator_file':call['generator_file'], 'file':file,
                              'sha256':sha(BASE / file)})
        assert report[key]['source_sha256'] == sha(BASE / f'generated/{key}.png')
        assert report[key]['svg_sha256'] == sha(BASE / f'svg/{key}.svg')
        assert report[key]['selected']['passes']
        vectors.append({'key':key, 'file':f'svg/{key}.svg',
                        'sha256':sha(BASE / f'svg/{key}.svg'), 'source':f'generated/{key}.png'})
    assert {item['file'] for item in originals} == {
        path.relative_to(BASE).as_posix() for folder in ['generated','discarded']
        for path in (BASE / folder).glob('*.png')}
    references.append({'file':'references/calculator-style.png',
                       'sha256':sha(BASE / 'references/calculator-style.png'),
                       'role':'Unchanged user-supplied accepted calculator style'})
    manifest = {
        'date':'2026-10-04', 'scope':'All 28 native vessel designs; visual prototype only',
        'mode':'Built-in image_gen; one call per concept or refinement', 'generation_calls':len(calls),
        'path_authoring':'Mechanical repository spline tracing; no hand-authored coordinates',
        'prompt_files':['prompts.json','refinement-prompts.json','refinement-opaque-prompts.json',
                        'refinement-structure-prompts.json','refinement-takeaway-prompt.json',
                        'refinement-espresso-prompt.json'],
        'references':references, 'generator_outputs':originals, 'selected_vectors':vectors,
        'trace_report':'trace-report.json', 'gallery':'gallery.html',
        'review':'Astra: complete family and subsequent Espresso/Cappuccino distinction pass saved light/dark 24/28/34px review; not user acceptance',
        'limits':'Fine seams soften at 24px. Related vessels require labels. Native rendering and measured recognition remain unverified.'
    }
    (BASE / 'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8',newline='\n')
    print(f'Verified 28 unchanged native references, {len(calls)} preserved outputs and 28 selected source/SVG hashes.')


if __name__ == '__main__':
    main()
