# Consistent cup and vessel family

**Historical, rejected manual experiment.** The user requires every artwork
edit to go through image generation and mechanical tracing. This pack is no
longer selected by the app or browser. Its source and report remain only as
historical evidence. The replacement is
[`generated-consistency-20261006`](../generated-consistency-20261006/README.md).
The construction and validation below describe the earlier experiment;
the shared preview image links now display the current generated family.

6 October 2026. The user requested a consistent visual language after the
travel-cup refinement. This set covers all 28 preset choices and the generic
quantity cup. It follows the existing filled, monochrome cup family.

The shared construction rules are:

- Rounded filled silhouettes, with elliptical mouth openings and balanced rims.
- Rounded handle loops with comparable weight and clear interiors.
- Narrow lid seams, based on the user's preferred closer-seam travel cup.
- One restrained glass reflection or material seam where it explains the vessel.
- Plungers, spouts, feet and double walls only where they distinguish the object.
- Optical adjustments for compact cups, wide bowls and tall vessels.

`build_family.py` is the authored source. Its shared ellipse, handle, glass,
double-wall and press helpers produce deterministic SVGs on the same 1024-unit
canvas. The travel cup copies the closer-seam artwork unchanged. The shapes are
hand-authored vectors; they are not claimed as mechanical traces or new image
generation. The original generator pixels, traces and preceding refinements
remain preserved in their historical folders.

`report.json` records the selected SVG hashes and original reference hashes.
At the time of this experiment, the native importer and browser preview selected these sources. The generic
quantity cup copies Cappuccino, which also supplies the Brew navigation icon
and cup artwork on Brew actions. Preset keys, labels and fallback mappings stay
compatible with saved presets.

```text
python docs/brewing/design/2026-10-02-visual-guides/assets/cup-icons/consistent-20261006/build_family.py
python tools/import_approved_vessel_vectors.py
python tools/import_approved_vessel_vectors.py --check
python docs/brewing/design/2026-10-02-visual-guides/assets/cup-icons/rebuild-20261004/build_preview.py
python docs/brewing/design/2026-10-02-visual-guides/assets/cup-icons/consistent-20261006/render_family.py
```

The [light](../../../../../../assets/vessel-family-light.png) and
[dark](../../../../../../assets/vessel-family-dark.png) review sheets show all
28 silhouettes, alongside 24 and 32 pixel samples. These are vector previews;
native Android rendering is checked separately.

Validation passed: all 28 SVGs reproduce exactly from the builder; 29 native
exports match their selected source paths, including the shared quantity cup.
Debug and Android test APK builds and Detekt passed. Eight existing emulator
checks passed on API 34 at 420 dpi, including all 28 vessels at both 24 and
32 dp in light and dark colors (112 individual render checks), the calculator,
settings with larger text, and brewing-set persistence and visibility.

The [native light](../../../../../../assets/vessel-family-native-light.png) and
[native dark](../../../../../../assets/vessel-family-native-dark.png) captures
show the 32 dp family in catalog order. Both 24 dp captures and the calculator
and settings captures were also visually reviewed. This is emulator evidence.
