# Image-generated vessel refinement

6 October 2026. This replaces the rejected hand-authored family with 28
separate edits made through the built-in image generator. Each edit uses its
preserved original generated vessel as the anatomy reference. The refined
Cappuccino supplies the shared style reference; its own edit uses the original
Mug for style. No manual family artwork is used as an input.

[Compare the generator pixels and traced contours](gallery.html), including
small-size samples and a light/dark switch.

The prompts ask for restrained refinements: preserve each vessel's natural
profile, smooth rounded filled contours, balanced rims and handles, and clear
but modest seams. Espresso keeps its detached saucer; the travel lid keeps
narrow seams. Glass walls, spouts, lids and plungers retain their vessel-specific
anatomy. The [exact prompts, generator filenames and hashed references](generation-records.json)
are saved for all 28 calls. Built-in tool mode was used, without an API/CLI fallback.

## Required artwork flow

1. Make every visual edit in the image generator, using preserved generated
   references. Inspect the output and iterate there when needed.
2. Preserve the selected transparent PNG unchanged in `generated/`, with the
   exact prompt, generator filename and source/reference hashes.
3. Run `build_vectors.py` to extract alpha and mechanically trace the contours.
   It uses the existing spline tracer; no path coordinates are authored by hand.
4. Review small-size light/dark previews, then import the traced paths unchanged
   through `tools/import_approved_vessel_vectors.py` and the browser importer.
5. Build and check native light/dark rendering, including the calculator and
   Settings. Visual corrections return to step 1.

The generator PNGs keep their original pixels. Only the conversion artifacts
use alpha threshold 128, bounding-box cropping and proportional fitting to
832 pixels inside a centered 1024-pixel canvas. RGB shading is discarded for
the tintable one-color vector. `masks/` and `renders/` are mechanical diagnostics;
`svg/` is the traced production source. The [trace report](trace-report.json)
records source/vector hashes, topology and fidelity checks at 24, 28, 34 and
48 pixels. These checks measure tracing accuracy, not measured recognition.

The native importer verifies generator provenance, original and style reference
hashes, unchanged selected PNG hashes, passing trace records and SVG hashes.
It imports 28 vessel vectors plus the generic quantity cup, which reuses the
same generated Cappuccino geometry as the Brew navigation and actions.
Preset names, saved amounts and fallback aliases remain compatible.

```text
uv run --no-project --with vtracer==1.0.0a3 --with pillow==12.3.0 --with numpy==2.3.5 python docs/brewing/design/2026-10-02-visual-guides/assets/cup-icons/generated-consistency-20261006/build_vectors.py
python tools/import_approved_vessel_vectors.py
python tools/import_approved_vessel_vectors.py --check
python docs/brewing/design/2026-10-02-visual-guides/assets/cup-icons/rebuild-20261004/build_preview.py
python docs/brewing/design/2026-10-02-visual-guides/assets/cup-icons/generated-consistency-20261006/render_family.py
python docs/brewing/design/2026-10-02-visual-guides/assets/cup-icons/generated-consistency-20261006/build_gallery.py
```

The original generated family, original traces, and rejected manual experiments
remain preserved in their separate historical folders. Active sources use only
this generated family.

## Validation

All 28 traces pass contour and full-resolution component/hole preservation
checks, with soft IoU at least 0.94, binary IoU at least 0.95 at 24 pixels and
0.94 at other target sizes, and 95th-percentile boundary error at most 1 pixel.
All 29 native exports match the selected traced SVGs. Debug and Android test
APK builds and Detekt passed. Five native checks passed on an isolated API 34
x86_64 emulator at 420 dpi: all 28 vessels at 24 and 32 dp in both light/dark
colors (112 individual renders), plus the calculator toolbar and cup Settings
card in both themes. All eight native captures were visually reviewed.

The [light](../../../../../../assets/vessel-family-light.png) and
[dark](../../../../../../assets/vessel-family-dark.png) sheets show the traced
family at large, 24 and 32 pixel sizes. Native captures are saved for
[24 dp light](native/vessel_family_light_24.png),
[24 dp dark](native/vessel_family_dark_24.png),
[32 dp light](../../../../../../assets/vessel-family-native-light.png),
[32 dp dark](../../../../../../assets/vessel-family-native-dark.png),
the [calculator](../../../../../../assets/calculator-vessel-toolbar-light.png),
and [Settings](../../../../../../assets/cup-presets-settings-light.png).
The [validation receipt](validation.json) records source, native vector and
capture hashes. This is emulator evidence; physical-device rendering and
human recognition testing have not been performed.
