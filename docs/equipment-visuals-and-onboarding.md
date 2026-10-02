# Equipment visuals and brewing-set setup

The set workflow uses one `BrewingSetForm` in onboarding and the expanded
`BrewingSetEditor` sheet. Names are optional. Method-specific starting ratios,
coffee quantities and supported filter/grinder combinations make the first set
usable immediately; extra sets remain optional. Recipe details expand in place.
The default coffee dose is 18g for espresso, 15g for AeroPress and 20g otherwise.
These are editable starting inputs, not equipment-specific optimum recipes.

Onboarding saves every draft, active identity and completion flag in one
DataStore transaction. Draft IDs and incomplete text survive restoration and
back navigation. Existing stored sets are preserved if setup is reopened.
Failures retain the draft and allow retry; invalid sets offer a direct route
back to the relevant form. Ordinary equipment edits preserve newer calculator
input. Explicit recipe edits replace the intended recipe atomically and reject
an outdated equipment revision.

## Design decisions

Astra reviewed the original screenshots and the implementation. The app already
uses MaterialExpressiveTheme, so the work focuses on hierarchy, spacing and
interaction, following [Google's expressive design guidance](https://design.google/library/expressive-material-design-google-research?pubDate=20250521).

- Method choices use 44dp illustrations, readable typography, distinct selected
  shapes, tonal color and checks. Narrow widths and large text use one column.
- Connected set rows use a 48dp equipment badge, 12dp text gap and one overflow
  menu. The active check and accessible selected state supplement color.
- The compact Brew trigger opens a titled sheet. Long lists scroll. Set and
  ratio controls wrap, while redundant method subtitles are omitted.
- Editor content scrolls independently of its action footer, with keyboard and
  system insets. Android Back closes an inner equipment picker first.
- Saved calculation expressions remain intact until an explicit amount replaces
  them. Compatibility uses the existing sourced grinder recommendations.
- Illustrations are decorative beside localized labels; mesh ratings and exact
  grinder settings always remain readable text. Unsupported controls are hidden.

The concrete value is faster recognition and reusable Home/Work equipment
combinations without a second configuration model. The recurring scenario is
switching brewing location or method. Defaults require no naming or recipe
configuration. Extra choices appear only during setup/editing. No new permanent
toggle is added to the Brew screen. State, accessibility and tracing tests are
the maintenance cost. Further controls should be removed or hidden if they make
the first-set path slower without a demonstrated recurring need.

## Shared visual API

`EquipmentVisual.method`, `.filter`, and `.grinder` centralize drawable mapping
outside domain models. `EquipmentIcon` provides tintable rendering;
`EquipmentVisualBadge` adds optional tonal containment. Consumers include the
shared form, onboarding method grid, Brew set picker, Settings set list,
preparation method choices and brew history.

Eight methods have distinct visuals. Paper and metal filters have separate
artwork; 19K/40K share the same physical disk shape and retain distinct labels.
All five supported grinder models have individual silhouettes. Unknown or
unspecified equipment uses a neutral Material symbol rather than a false model
likeness. The illustrations do not imply extra recommendation support.

Model identity references: [Pulsar](https://nextlevelbrewer.com/shop/nextlevel-pulsar-brewer/),
[ZP6](https://1zpresso.coffee/zp6/),
[C40](https://comandantegrinder.com/products/c40-mk4-nitro-blade-liquid-amber),
[Ode Gen 2](https://fellowproducts.com/collections/grinder/products/ode-brew-grinder-gen-2?variant=40978992496740),
[Encore ESP](https://www.baratza.com/en-us/product/ZCG495),
[Niche Zero](https://www.nichecoffee.co.uk/).

## Authored method refinements

Pulsar, AeroPress, Espresso and Chemex now use authored monochrome SVGs to make
their equipment shapes clearer: a substantial Pulsar valve base, AeroPress's
stepped plunger, a separated Espresso portafilter/steam wand, and Chemex's open
glass body and collar. Resource names and the shared presentation API are the
same. The new source, references, reproduction command and before/after sheets
are in [method-icon-refinement/README.md](assets/method-icon-refinement/README.md).

`tools/render_method_icons.py` exports these four production resources. The
legacy tracing runner preserves these authored overrides and invokes their
exporter after rebuilding the original traces. The earlier raster fidelity
reports below describe the historical concepts, not the new authored shapes.

## Original generated artwork and deterministic conversion

Built-in ImageGen generated one raster per asset in a shared monochrome style.
The exact prompts and immutable source paths are in
`docs/assets/equipment-imagegen-manifest.json`. Initial Pulsar/French press
concepts are retained as design history; the manifest uses their simplified v2
sources because thin details failed the small-size checks.

The workflow reuses the calculation-icon tracing approach, exposing reusable
operations in `tools/monochrome_icons.py` and a manifest-driven equipment runner:

```powershell
uv run --with vtracer==1.0.0a3 --with pillow==12.3.0 --with numpy==2.3.5 python tools/vectorize_equipment_icons.py
```

ImageMagick must be on PATH. Generated dark pixels are thresholded at 128, cropped
and centered in a 1024px mask with an 832px maximum silhouette dimension. VTracer
fits contours at several simplification tolerances. The smallest candidate
passing every gate became the original Android VectorDrawable; those traced
contours did not have hand-authored path coordinates. The four method
refinements above intentionally replace their original contours.

The gates require soft alpha IoU >=0.94, binary IoU >=0.95 at 24px / >=0.94 at
32px and 48px, 95th-percentile boundary displacement <=1px, and identical
full-resolution connected-component/hole topology. The report records source
and mask SHA-256 hashes and every candidate measurement. Visual comparison at
24/32/48px is still required before accepting new artwork.

Outputs: `equipment-*-mask.png`, `equipment-*-traced.svg`,
`equipment-vector-report.json`, `equipment-vector-comparison.png`,
`equipment-small-size-comparison.png`, and `drawable/equipment_*.xml`.

To add equipment, generate a separate concept using the family prompt, add its
source/prompt to the manifest, run the pinned pipeline, review the comparison,
then add its presentation mapping. Support eligibility stays in the existing
equipment/recommendation data. Never add a supported model merely because an
illustration exists.

## Original workflow verification and previews

Detekt, the debug build and Android-test APK build pass. The JVM suite has
1,439 passing tests and four skips. The 22 affected Android checks pass across
the final suite and the corrected Back-routing test rerun. Coverage includes
multiple onboarding sets, recipe persistence, unsupported equipment, quantity
selection, grinder readouts, 320dp layouts and the editor at 200% text with the
keyboard open. All 15 traced assets pass the documented fidelity gates.

The actual app was launched on a disposable API 36.1 emulator. Changing the
first draft from Pulsar to Espresso, then going Back and Next, preserved the
method and its 18g / 1:2 recipe. Home and Work drafts, the selected identity and
recipe survived termination of the background app process and restoration in
a new process. Finishing onboarding opened Brew with the selected Work set.
Native Back returned from the editor's equipment picker to its form. The
automated Back test exercises Android's navigation dispatcher directly so
emulator System UI dialogs cannot consume the injected key. Physical-device
and release validation are separate from these checks.

Actual app previews: [method selection](assets/equipment-ui-onboarding-methods.png),
[first-set setup](assets/equipment-ui-onboarding-set.png),
[set picker](assets/equipment-ui-set-picker.png), and
[set editor](assets/equipment-ui-set-editor.png).
