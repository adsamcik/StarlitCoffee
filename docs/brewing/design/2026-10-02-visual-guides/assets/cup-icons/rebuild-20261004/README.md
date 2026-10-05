# Complete vessel icon rebuild

4 October 2026. [Review all 28 icons](gallery.html), including their selected generator images, traced SVGs and actual 24 / 28 / 34 px appearances. This is a prototype asset family; no Android resources are replaced.

The [catalog](catalog.json) follows all 28 designs in native `availablePresetIcons`, rather than the five default calculator slots. Original native artwork supplies anatomy and the user's unchanged calculator screenshot supplies the rounded, filled, monochrome style. Glasses, bowls, servers, presses and the kettle are included. Historical `custom` uses Bowl and unknown keys use Mug. Shared In cup and Brew symbols reuse Cappuccino. Rating characters and brewing instruction scenes have a separate illustration role.

## Generator workflow

Used the built-in `image_gen` tool with transparent backgrounds: 28 separate initial calls and 13 refinement calls. No API/CLI fallback was used. Astra reviewed the native references, generated candidates and final small-size proofs. The refinements correct patchy transparent interiors, press frames, bowl openings, lid/sleeve seams, and the user-identified Espresso/Cappuccino similarity. Espresso now has a naturally compact demitasse and detached saucer; The other 27 designs, including Cappuccino, keep their accepted contours.

- [Initial exact prompts](prompts.json)
- [Closed-lid refinements](refinement-prompts.json)
- [Opaque-body and glass refinements](refinement-opaque-prompts.json)
- [Bowl and press refinements](refinement-structure-prompts.json)
- [Takeaway seam refinement](refinement-takeaway-prompt.json)
- [Espresso distinction refinement](refinement-espresso-prompt.json)
- [Generator provenance](generation-provenance.json), [later provenance](refinement-provenance.json)
- [Manifest of references, all 41 outputs and selected SVG hashes](manifest.json)

`generated/` holds the 28 selected original PNGs. `discarded/` retains 13 earlier original outputs. These images are preserved unchanged. `references/` retains the unchanged anatomy/style images. `svg/` contains the 28 standalone tintable vectors. Masks and comparison renders are separate mechanical artifacts.

## Mechanical vector conversion

`build_vectors.py` uses the existing `tools/monochrome_icons.py` spline tracer. Alpha threshold 128 selects the generated contour; bounding-box cropping, isotropic fitting to 832 px inside a centered 1024 px canvas, and another alpha threshold normalize it. RGB shading is irrelevant to a one-color symbol. The generator PNGs are not painted over. No path coordinates are redrawn by hand.

All selected traces pass full-resolution component/hole preservation and the repository's contour gates at 24, 28, 34 and 48 px: soft IoU ≥ 0.94, binary IoU ≥ 0.95 at 24 px and ≥ 0.94 elsewhere, and 95th-percentile boundary error ≤ 1 px. Small-size topology is reported but is not an equality gate; antialiasing can close fine gaps. The [trace report](trace-report.json) retains per-icon metrics and source/vector hashes. This measures trace fidelity rather than human recognition.

Reproduce from the repository using Python with `vtracer==1.0.0a3`, `pillow==12.3.0`, `numpy==2.3.5`, and ImageMagick on PATH:

```text
python docs/brewing/design/2026-10-02-visual-guides/assets/cup-icons/rebuild-20261004/build_vectors.py
python docs/brewing/design/2026-10-02-visual-guides/assets/cup-icons/rebuild-20261004/build_preview.py
python docs/brewing/design/2026-10-02-visual-guides/assets/cup-icons/rebuild-20261004/build_gallery.py
python docs/brewing/design/2026-10-02-visual-guides/assets/cup-icons/rebuild-20261004/build_manifest.py
```

The preview importer verifies native keys/order and source/vector hashes, then copies SVG paths unchanged with `currentColor` and nonzero winding. The manifest builder verifies copied native reference hashes and accounts for every saved generator output. Text exports use LF line endings so recorded SVG hashes survive Git checkout on Windows and other platforms.

## Review boundary

Astra found no material defects in the saved light/dark sheets at 24 / 28 / 34 px and reviewed the subsequent [Espresso/Cappuccino pair](../../../espresso-cappuccino.html). The detached saucer stays visible at 24 px without exaggerated cup proportions. Fine press, facet and lid details soften at 24 px. Bowl/Ceramic latte bowl and Double-wall espresso/Double-wall tumbler remain related forms; labels clarify their specific identity. This is agent review, not user acceptance, native rendering validation or measured recognition.

The browser keeps its existing five calculator slots, Settings labels/volumes and navigation structure. All 28 assets are visible in the review gallery, without adding more controls to the calculator. The previous [five-icon exploration](../../../cup-icon-iterations.html) is frozen as historical evidence.
