"""Import traced SVG paths without changing coordinates or winding."""
from pathlib import Path
import hashlib
import json
import re
import sys
import xml.etree.ElementTree as ET

BASE = Path(__file__).resolve().parent
PREVIEW = BASE.parents[2]
ROOT = next(parent for parent in BASE.parents if (parent / 'app/src/main').is_dir())
NS = '{http://www.w3.org/2000/svg}'
sys.path.insert(0, str(ROOT / 'tools'))
from import_approved_vessel_vectors import source_for

def main():
    catalog = json.loads((BASE / 'catalog.json').read_text(encoding='utf-8-sig'))['entries']
    native = (ROOT / 'app/src/main/java/com/adsamcik/starlitcoffee/ui/util/PresetIcon.kt').read_text()
    native_keys = re.findall(r'"([a-z_]+)"', native.split('val availablePresetIcons = listOf(',1)[1].split(')',1)[0])
    assert [entry['key'] for entry in catalog] == native_keys
    report = json.loads((BASE / 'trace-report.json').read_text())
    vectors = {}
    for entry in catalog:
        key = entry['key']
        assert report['icons'][key]['selected']['passes'], key
        for folder, field, extension in [('generated','source_sha256','png'),('svg','svg_sha256','svg')]:
            actual = hashlib.sha256((BASE / folder / f'{key}.{extension}').read_bytes()).hexdigest()
            assert actual == report['icons'][key][field], f'{key}: stale {field}'
        selected, expected_hash = source_for(key, report)
        assert hashlib.sha256(selected.read_bytes()).hexdigest() == expected_hash, key
        svg = ET.parse(selected).getroot()
        assert svg.attrib['viewBox'] == '0 0 1024 1024'
        assert not any('transform' in node.attrib for node in svg.iter())
        paths = list(svg.iter(NS+'path'))
        fills = [node.attrib['fill'] for node in svg.iter() if 'fill' in node.attrib]
        assert paths and fills and all(fill == 'currentColor' for fill in fills)
        vectors[key] = [path.attrib['d'] for path in paths]
    labels = [[entry['key'],entry['label']] for entry in catalog]
    source = '''/* Generated from reviewed SVGs in assets/cup-icons.
   Shared authored family in consistent-20261006; historical traces preserved.
   All 28 native preset keys; currentColor and nonzero winding preserve holes.
   Run rebuild-20261004/build_preview.py after source changes; do not hand-edit paths. */
'use strict';
const VesselCatalog = '''+json.dumps(labels,indent=2)+''';
const VesselIcons = '''+json.dumps(vectors,indent=2)+''';
function vesselIcon(key) {
  const resolved = key === 'custom' ? 'bowl' : Object.hasOwn(VesselIcons,key) ? key : 'mug';
  return '<svg class="vessel-icon" viewBox="0 0 1024 1024" focusable="false" aria-hidden="true">'+VesselIcons[resolved].map(d=>'<path fill="currentColor" fill-rule="nonzero" d="'+d+'"/>').join('')+'</svg>';
}
'''
    (PREVIEW / 'vessel-icons.js').write_text(source, encoding='utf-8', newline='\n')
    print(f'Imported {len(vectors)} reviewed icons; native keys/order, fill and winding verified.')

if __name__ == '__main__':
    main()
