# Brewing icons in the existing app language

The user rejected the first pass's glossy treatment. The current [overview](sheet.html) and [review gallery](index.html) reuse **ten shipping Learn icons exactly**, with seven restrained family-extension candidates. Android resources are unchanged.

The app has three artwork roles: tintable equipment silhouettes in Brew/setup, illustrated recognition icons in Learn, and reviewed images that teach physical actions. This pack extends **Learn recognition only**.

## Current selection

[selection-v2.json](selection-v2.json) identifies the ten exact copies and seven generated candidates in [selected-v2/](selected-v2/). Generic existing profile art remains generic; a photographed reference model is not a claim that reused art depicts that precise model.

Equipment photographs and anatomical briefs for all seventeen methods remain in [manifest.json](manifest.json) and [references/](references/). They establish geometry, not recipe quantities, capacity or safe operation. Photographs remain attributed to their publishers and are reference inputs only.

Each new request receives four actual images, in order: the equipment photograph, shipping Learn contact sheet, shipping Clever icon and shipping batch-machine icon. The photo establishes anatomy; the three existing references establish family, glass, outline weight, compact forms and shading. The French press was calibrated beside shipping neighbors before extending the other six methods.

[Exact prompts](prompts-v2/) request restrained ivory/navy/periwinkle materials and modest highlights. One targeted AeroPress edit corrected its hollow-looking piston top to the closed press surface. That edit received the candidate first, then its photograph and the three family references. The built-in image_gen tool generated eight outputs. [generation-history-v2.json](generation-history-v2.json) preserves exact prompts and ordered input/output hashes. No raster was resized, cropped or cleaned with Python.

## Review and export boundary

[REVIEW.md](REVIEW.md) records current checks. [validation-v2.json](validation-v2.json) checks hashes, exact reuse, provenance, real alpha and visible margins, and separately exposes export requirements. Passing design integrity does not mean production exports are ready.

The ten reused WebPs are 256 x 256. Six new selections are 1254 x 1254; AeroPress is 1241 x 1267 and fits its square preview through object-fit: contain. A square resource export is pending for that candidate. Five new files retain alpha 1/255 residue in clear margins. Final sizing and pixel-clean exports belong to Android integration.

Light surfaces approximate flat colors sampled from the current emulator screen; semantic roles are inferred. Dark surfaces use app fallback colors. This does not emulate Android dynamic color or prove native rendering. Method names remain visible.

## Retained first pass

[candidates/](candidates/), [prompts/](prompts/), [generation-history.json](generation-history.json) and [validation.json](validation.json) retain the original twenty-call pass and anatomical corrections. Those candidates are **superseded for visual style**. Mechanical checks did not establish consistency with the app. Gallery disclosures compare the glossy first treatment with the current selection.

## Reproduce

Run python style_revision.py build here to rebuild current HTML, and python style_revision.py validate for read-only raster/provenance checks. prepare_style_revision.py prepares exact copies and prompts while preserving an existing selection manifest. Scripts leave pixels untouched. The older build_review.py reproduces the superseded first-pass gallery.

Related: [guide prototype](../2026-10-02-visual-guides/prototype.html), [workflow design](../2026-10-02-visual-guides/DESIGN.md), [reviewed guides](../../research/2026-10-02-method-guides/README.md).
