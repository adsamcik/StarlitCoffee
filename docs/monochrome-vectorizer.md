# Monochrome icon vectorizer

`tools/monochrome_vectorizer.py` turns an image-generated flat silhouette into
editable SVG and Android VectorDrawable paths. Source images remain the design
authority. The algorithm produces cubic Bézier curves and true straight edges,
then chooses fewer segments subject to a geometric fitting tolerance. It never
substitutes manually drawn icon paths or silently drops small components.

## Run and review

Use Python 3.11+ in a project virtual environment with the exact development
dependencies in `tools/monochrome-vectorizer-requirements.txt`:

```powershell
python -m pip install -r tools/monochrome-vectorizer-requirements.txt
python tools/monochrome_vectorizer.py source.png --output-dir output --name coffee-dose --error .055
python -m unittest discover -s tools -p test_monochrome_vectorizer.py -v
```

`--error` is in a 24-unit viewport, not source pixels. The default `.055` is
approximately 0.11 pixel at 48 px. Supported values are `.015` through `.15`.
Lower values preserve more raster detail and normally require more segments.
Start with the default; inspect the difference panel and actual-size row before
changing it. Maximum error is a hard constraint, and segment count is the first
optimization objective; there is no user-facing application setting.

The CLI accepts one 32–4096 px PNG-like raster with transparent background and a
flat foreground color, or opaque black ink on white. Transparent coverage comes
from alpha, including antialiasing. White-backed coverage comes from inverse
grayscale. Clipped, empty, multicolor or excessively noisy inputs are rejected.
Opaque colored ink should be supplied with alpha instead. Inputs with a colored
background, shadows, texture or translucent interior are outside the contract.

Each successful run writes `NAME.svg`, `NAME.xml`, `NAME.json` and
`NAME-comparison.png`. SVG uses `currentColor`; Android uses tintable black
paths. Both contain identical six-decimal path data and nonzero fill. Failed
quality checks exit with code 2 and write only `.rejected` candidates, reports
and comparisons, leaving any previously accepted artwork intact. An input or
fitting rejection exits 2 without producing new vector files. Consumers must
check process success and report `quality.passed`; existence of an older accepted
file does not mean the current source passed.

The output basename is a review asset label. When importing XML into Android
`res/drawable`, use a valid lowercase resource name with underscores, for example
`coffee-dose.xml` becomes `ic_coffee_dose.xml`.

## Algorithm and precision contract

1. Extract the antialiased 50% contour using interpolated marching squares.
   Saddle cells use foreground 4-connectivity; this is intentional and
   deterministic. Directed edges keep the foreground on the right. Outer rings
   and holes consequently have opposite winding, including nested islands.
2. Normalize the foreground bounding box to 20 units, centered in a 24-unit
   viewport. All contours share the same transform. No feature is independently
   recentered or scaled. Preserve every component; tiny noise requires fixing
   the source rather than guessing what the artist meant.
3. Resample contours uniformly at approximately `.035` units. Detect persistent
   direction changes at a wider neighborhood and retain those corners. At smooth
   joins use a common tangent; at corners use distinct one-sided tangents.
4. Fit cubic handles by constrained linear least squares, requiring positive
   lengths and bounded handles. Newton projection refines the parameterization,
   with monotonicity checks. Invalid or over-tolerance spans are discarded.
   Straight spans become lines only when their endpoint tangents agree.
5. Build a graph of candidate knots at regular arclength and mandatory corners.
   Adaptively refine infeasible intervals. Dynamic programming chooses the
   smallest number of feasible segments, breaking ties by summed squared span
   error. Search is bounded to 32 predecessor knots for predictable runtime.
   This is an optimum **over the bounded candidate graph**, not a claim of a
   universal minimum-node solution. Corners may not be bypassed.
6. Serialize and parse the actual output paths, then independently rasterize
   their cubic commands with PDFium via a small SVG-subset-to-PDF adapter.
   Rendering uses real production cubic rasterization and nonzero winding, not
   the fitter's sampled polylines. It is not a general SVG renderer or proof of
   Android device rendering.
7. Compare against the normalized source: silhouette IoU at 384 px; bidirectional
   boundary maximum, 99th percentile and mean in viewport units; components,
   holes and signed winding; smooth tangent mismatch after decimal rounding;
   actual-size coverage error at 24, 36 and 48 px. The comparison PNG shows
   source, vector, error overlay, and unscaled icon rows.

The project keeps every `pathData` within Android's 3000-character VectorPath
lint budget; this is not an intrinsic vector format limit. Export version 1.1 keeps a single compound path when
it fits. Larger artwork is partitioned by foreground component, preserving each
hole in the same path as its smallest containing outer contour. Nested islands
form separate components with their own holes. All coordinates, six-decimal
precision, segments, winding and fitting tolerances remain unchanged; this is
a lossless grouping change, not geometric simplification. SVG and Android use
the same groups, and the independent renderer paints each group separately so
QC covers the exported fill behavior. The report records groups and per-path
character counts. If a single compound component still exceeds the budget, the
exporter tries equivalent relative commands. These deltas are calculated on the
same six-decimal integer grid, preserving every rounded absolute coordinate
exactly; they are not a lower-precision fit. An individual component that still
exceeds the budget after this lossless encoding, or a hole whose parent cannot
be safely assigned, is rejected instead of dropping precision, filling the hole
or suppressing lint.

Acceptance requires IoU >= `.97`, boundary maximum <= `1.65 * error + .025`,
99th percentile <= `1.3 * error + .02`, unchanged topology/winding, and <= `.1`
degree mismatch at smooth joins. Mean absolute coverage error must also be <=
`.015` at every actual output size. Alpha sources must contain an opaque ink
interior; uniform translucency is rejected before fitting, and coverage gating
catches materially soft or partially translucent interiors. Boundary comparisons sample both directions at
approximately `.015` units. These are reproducible sampled quality measurements,
**not certified analytic Hausdorff bounds**. Source topology uses the original
isocontours; output component/hole checks use the independently rendered 384 px
mask, so a subpixel feature may deliberately fail acceptance. Smooth-join checks
use preserved source-corner metadata, never an exemption inferred from a sharp
angle in the output. Source corner detection uses 53 degrees at its measurement
neighborhood. Six decimal places preserve tangent agreement even on short
handles where coarser coordinate rounding creates visible angular error.

The JSON records source and output SHA-256 hashes, normalization, all parameters,
dependency versions, per-contour source-point/segment/corner counts, anchor and
control-point counts, acceptance thresholds and failures. No timestamps or
absolute machine paths enter reproducible output. Re-run the source rather than
editing generated SVG or XML independently. Parameter tuning must retain the
source, report and visual comparison so fidelity/complexity tradeoffs stay
reviewable. Handcrafted appearance still needs visual judgment; a noisy source
cannot reveal the artist's intended geometry through metrics alone.

Topology checks establish component/hole counts at the stated rendering scale
and contour winding. They are not an analytic no-self-intersection proof or a
certification of the complete nesting/containment tree at arbitrarily small
scales. Review narrow gaps and deeply nested or touching shapes explicitly.

## Provenance and licensing

The fitter is an independent NumPy implementation based on the mathematical
method in Philip J. Schneider, *An Algorithm for Automatically Fitting Digitized
Curves*, Graphics Gems, 1990, pp. 612–626. The
[original Graphics Gems reference implementation](https://github.com/erich666/GraphicsGems/blob/master/gems/FitCurves.c)
and [publisher's chapter listing](https://www.sciencedirect.com/book/edited-volume/9780080507538/graphics-gems)
were reviewed. No third-party fitting implementation was copied or vendored.
The [Graphics Gems code permission](https://github.com/erich666/GraphicsGems/blob/master/LICENSE.md)
permits commercial and noncommercial use while retaining author ownership.

Peter Selinger's [Potrace paper](https://potrace.sourceforge.net/potrace.pdf)
informs the separation of contour extraction, corner handling and segment
optimization. This tool does not implement or bundle Potrace, use its executable,
or claim identical output. It uses direct cubic least squares rather than
Potrace's optimal-polygon stage.

Development dependencies are Pillow (HPND/PIL permission), NumPy (BSD-3-Clause),
ReportLab (BSD) and pypdfium2 (Apache-2.0/BSD-3-Clause, with PDFium's bundled
third-party notices). They are installed tools; the Android app receives only
generated vector path data. When redistributing the tool environment, retain
the installed packages' license and PDFium third-party notice files. The
requirements pin the versions used for the initial acceptance runs; report
versions and regenerate/review all outputs when updating the toolchain.
