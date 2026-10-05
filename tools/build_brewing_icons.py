#!/usr/bin/env python3
"""Regenerate, validate and optionally install the preparation icon family."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import shutil
import sys

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / 'docs/assets/brewing-icons'
DRAWABLE = ROOT / 'app/src/main/res/drawable'
ICONS = {
    # Retained source/provenance, without unused application resources.
    'coffee-dose': None,
    'grinder': 'brewing_icon_grinder',
    'water-input': None,
    'cup-output': None,
    'grinder-c40': 'brewing_icon_grinder_c40',
    'grinder-ode': 'brewing_icon_grinder_ode',
    'grinder-encore': 'brewing_icon_grinder_encore',
    'grinder-niche': 'brewing_icon_grinder_niche',
    'grinder-df64': 'brewing_icon_grinder_df64',
    'grinder-electric': 'brewing_icon_grinder_electric',
}
SOURCE_REVISIONS = {
    'grinder-ode': 'grinder-ode-v2.png',
    'grinder-df64': 'grinder-df64-v2.png',
    'grinder-electric': 'grinder-electric-v2.png',
}


def source_path(name: str) -> Path:
    return ART / 'source' / SOURCE_REVISIONS.get(name, name + '.png')


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def verify(name: str, installed: bool) -> dict:
    vector = ART / 'vector'
    report = json.loads((vector / (name + '.json')).read_text(encoding='utf-8'))
    if not report['quality']['passed']:
        raise ValueError(f'{name}: vector quality rejected')
    for path, expected in [
        (source_path(name), report['source_sha256']),
        (vector / (name + '.svg'), report['svg_sha256']),
        (vector / (name + '.xml'), report['android_sha256']),
    ]:
        if digest(path) != expected:
            raise ValueError(f'{name}: hash mismatch for {path.name}; regenerate and review')
    resource = ICONS[name]
    if installed and resource is not None and digest(DRAWABLE / (resource + '.xml')) != report['android_sha256']:
        raise ValueError(f'{name}: installed drawable differs from reviewed trace')
    return report


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    action = parser.add_mutually_exclusive_group()
    action.add_argument('--check', action='store_true', help='Check current source/vector/drawable hashes without changing files')
    action.add_argument('--install', action='store_true', help='Regenerate all icons and install only if all pass')
    parser.add_argument('--error', type=float, default=.055, help='Fit tolerance in a 24-unit viewport')
    args = parser.parse_args()
    try:
        if args.check:
            reports = {name: verify(name, installed=True) for name in ICONS}
        else:
            from monochrome_vectorizer import vectorize
            # Never install a partially validated family. A failed run leaves reviewable rejected
            # candidates and any old installed artwork unchanged.
            reports = {name: vectorize(source_path(name), ART / 'vector', name, args.error) for name in ICONS}
            failed = [name for name, report in reports.items() if not report['quality']['passed']]
            if failed:
                raise ValueError('No drawables installed; rejected: ' + ', '.join(failed))
            for name in ICONS:
                verify(name, installed=False)
            if args.install:
                for name, resource in ICONS.items():
                    if resource is not None:
                        shutil.copyfile(ART / 'vector' / (name + '.xml'), DRAWABLE / (resource + '.xml'))
                for name in ICONS:
                    verify(name, installed=True)
        print(json.dumps({name: {'segments': r['total_segments'], 'iou': r['quality']['silhouette_iou'], 'passed': r['quality']['passed']} for name, r in reports.items()}, indent=2))
    except (OSError, ValueError, KeyError) as exc:
        print(f'Brewing icon validation failed: {exc}', file=sys.stderr)
        return 2
    return 0


if __name__ == '__main__':
    sys.exit(main())
