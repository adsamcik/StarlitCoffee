# Travel cup refinement

6 October 2026. Refines the travel cup requested by the user, retaining the
existing filled icon family and its tall, tapered reusable-cup silhouette.

The authored SVG has three smooth filled shapes: a closed cap with a connected
sip tab, a curved sealing rim, and the cup body. Wider curved gaps separate the
parts at calculator and settings sizes. No new stroke, color, shadow or body
decoration is introduced.

The original generator image and traced SVG in `../rebuild-20261004/` remain
unchanged. This refinement is hand-authored vector artwork, not a new raster
generation or an automatic trace. `report.json` records both source hashes.

`tools/import_approved_vessel_vectors.py` selects this source for `travel`,
checks the hash, and copies its paths into the native drawable. The other 27
icons retain their reviewed sources. The browser preview uses the same selection
through `rebuild-20261004/build_preview.py`.

```text
python tools/import_approved_vessel_vectors.py --check
python docs/brewing/design/2026-10-02-visual-guides/assets/cup-icons/rebuild-20261004/build_preview.py
```

The [before/after comparison](../../../../../../assets/travel-icon-refinement.png)
shows 24, 28, 32 and 44 dp vector previews at 420 dpi. Reproduce it with this
folder's `render_comparison.py` and ImageMagick.

Validation passed: all 28 native exports match their selected SVG hashes and
paths; the other 27 drawable contents are unchanged; debug and Android test APKs
build. Five existing native checks passed on an isolated API 34 / 420 dpi
emulator, covering all 28 icons at 24 dp, the calculator at 28 dp, and settings
at 32 dp in light, dark and narrow layouts with larger text. The resulting
screenshots were visually reviewed. This establishes emulator rendering.
