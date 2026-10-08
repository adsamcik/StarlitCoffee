# Preparation icon family

Historical source artwork and vectorizer evidence from the completed
preparation worktree, retained on 6 October 2026. Current app resources use the
later equipment and calculation icon families; the resource mappings below
describe the original preparation design. Regeneration does not select this
family for the current app.

Ten custom silhouettes generated with the built-in OpenAI image generator on
2026-09-30, then traced into tintable cubic Bézier paths with the reusable
[monochrome vectorizer](../../monochrome-vectorizer.md). The generated
PNGs are retained unchanged in `source/`. `generation-prompts.json` contains the
core prompts; `grinder-generation-prompts.json` and `grinder-edit-prompts.json`
retain Astra's exact model prompts and simplification edits. The model sources
use an opaque white background; negative space becomes transparent in the trace.
No hand-authored replacement paths were used. Astra owns icon design and screen
composition; the same fitting and quality rules apply to every source.

| Source | Android drawable | Intended use | Curve segments / anchors |
| --- | --- | --- | ---: |
| `coffee-dose.png` | Reference trace only | Dry coffee | 17 |
| `grinder.png` | `brewing_icon_grinder.xml` | ZP6 / manual fallback | 29 |
| `water-input.png` | Reference trace only | Water to pour | 5 |
| `cup-output.png` | Reference trace only | Beverage yield | 16 |
| `grinder-c40.png` | `brewing_icon_grinder_c40.xml` | Comandante C40 | 31 |
| `grinder-ode-v2.png` | `brewing_icon_grinder_ode.xml` | Fellow Ode Gen 2 | 49 |
| `grinder-encore.png` | `brewing_icon_grinder_encore.xml` | Baratza Encore ESP | 48 |
| `grinder-niche.png` | `brewing_icon_grinder_niche.xml` | Niche Zero | 65 |
| `grinder-df64-v2.png` | `brewing_icon_grinder_df64.xml` | DF64 | 63 |
| `grinder-electric-v2.png` | `brewing_icon_grinder_electric.xml` | Electric fallback | 39 |

The preparation screen ships seven grinder resources. The bean, water and cup
source images and accepted traces remain in this directory for provenance and
design comparison, without unused application resources. Each vector uses one
tintable colour; negative space remains transparent. Normalization preserves
aspect ratio and centers the complete foreground with a consistent 2-unit
viewport margin.

Grind shows one recommendation using the same label and value hierarchy as
Coffee. The model name appears in a separate disclosure below it; expanding
that row reveals the scale explanation, a 64 dp grinder silhouette, suggested
range and adjustment guidance. Pure-click grinders retain their localized
click unit in the visible recommendation. Astra removed the competing model
and setting arrangements after reviewing their reading order. The original
manual grinder's crank and catch-cup seam are delicate at 24 px; inspect the
comparison row before reusing it smaller. Astra reviewed all model sources and
the comparison sheets at 24/36/48 px. Precision is against the generated
silhouette, rather than an exact industrial drawing of the physical product.

The model icon helps recognize the selected equipment during the existing
preparation task. Exact stable catalogue IDs select it automatically; names are
never guessed. An unknown grinder uses its manual/electric form factor; generic
grind guidance has no equipment illustration. Icons are decorative
and excluded from accessibility announcements; the model name, setting, units
and disclosure remain the meaningful content. The existing range disclosure
reveals the artwork; the brewing task requires no additional interaction.
If a model symbol ceases to be recognizable at its intended size, simplify its
source and repeat review, or use the form-factor fallback.

Shape references used by Astra: [Comandante C40](https://comandantegrinder.com/products/c40-mk4-nitro-blade-american-cherry),
[Fellow Ode](https://fellowproducts.com/collections/all-products/products/ode-brew-grinder-gen-2),
[Baratza Encore ESP](https://www.baratza.com/en-us/product/ZCG495),
[Niche](https://www.nichecoffee.co.uk/), and [DF64](https://df64coffee.com/en-au/collections/models).

The first Ode source had tiny contour islands; the first DF64 and electric
sources had unstable hairline boundaries. Astra requested broad, flat negative
space instead. The unchanged quality gates accepted their `-v2` edits. The
builder explicitly maps those versioned inputs to canonical resource names.

## Regenerate and verify

From the repository root, in the pinned Python tool environment:

```powershell
python -m pip install -r tools/monochrome-vectorizer-requirements.txt
python tools/build_brewing_icons.py --install
python tools/build_brewing_icons.py --check
python -m unittest discover -s tools -p test_monochrome_vectorizer.py -v
```

The default `.055` fitting tolerance is in the normalized 24-unit viewport.
`--error .03` can retain more detail at the cost of more nodes. The build command
regenerates reports and review images for all ten sources, and installs the
seven active grinder resources only after every icon passes its quality checks.
A rejected candidate is saved with `.rejected`
in its name; installed drawables remain untouched on a fitting/QC failure.
`--check` verifies all ten source, SVG and Android trace hashes, plus the seven
installed resource hashes, so stale accepted art cannot pass merely because a
file exists.

Each `vector/NAME.json` records the source/output hashes, exact dependency
versions, normalization, fitting tolerance, node counts, tangent checks,
bidirectional sampled edge errors, raster component/hole counts, and coverage
error at 24/36/48 px. `NAME-comparison.png` shows source, fitted geometry, error
overlay and actual-size pairs. The generic CLI also accepts other flat icons:

```powershell
python tools/monochrome_vectorizer.py source.png --output-dir review --name custom_icon
```

Regenerate instead of independently editing SVG and Android path data. For a
design revision, preserve a versioned source and its prompt, run the same
pipeline, inspect the result, and then update the application resource. Precision
is measured against the generated shape; the algorithm cannot recover an
artist's intended geometry from a noisy bitmap. See the tool documentation for
the limits of sampled geometry and raster topology checks.

## Validation on 2026-09-30

- All 17 vectorizer fixtures passed, including curved/straight geometry,
  corners, holes, nested islands, thin features, rejected inputs, deterministic
  output, accidental sharp joins, retention of accepted art on QC failure,
  lossless component grouping, exact relative-coordinate reconstruction,
  orphan-hole rejection and the Android path-length budget. An independent
  Astra review also checked grouping, cursor handling and corner metadata.
- The full ten-icon tracing pipeline completed for export 1.1. The composition
  refinement retained those accepted traces; `--check` verifies all ten source,
  SVG and XML hashes plus seven active application resources. Each trace
  replaces thousands of original contour samples. The complete family
  uses 5–65 anchors. Largest sampled edge error is `.0553568` viewport units,
  approximately `.1108` px at 48 px. Silhouette IoU is 98.01–99.49%; maximum
  actual-size mean absolute coverage error is `.009021`.
- Export 1.1 groups each disconnected component with its holes. All ten
  production icons retain byte-identical six-decimal contour commands and
  unchanged node counts and quality metrics. DF64's longest path is 2,508
  characters and Niche's is 2,866, below Android's 3,000-character lint budget.
  Larger generic inputs may use exact fixed-point relative commands; inputs
  still over budget fail without replacing accepted art.
- Android Detekt, debug build and instrumentation APK compilation passed. The
  unit suite had 1,384 passed tests and four skipped tests, with no failures.
  Lint still reports four existing findings in the Gradle wrapper, calculator,
  unused learning-intro string and Settings screen; none is in the refined
  preparation code or generated drawables.
- The interactive design preview rendered the exact final SVGs and passed
  coffee selection, both disclosures, local button feedback and a 320 px
  viewport check with no horizontal overflow or JavaScript errors. It is a
  mockup. The Android 16 emulator also passed all 13 preparation UI tests,
  covering all six model resources, dose precision, disclosures, callbacks,
  warnings, all methods, cold-brew guidance, instructions off, enlarged text,
  and a 320 dp frame at 1.6x text scale. The selected-coffee fixture checks that
  its full label has no visual overflow and its clear action still works.
  Screenshots were captured in light and
  dark themes for Astra's native composition review. Tests used a separately
  installed review package; the original app's process was preserved.
  The focused Grind refinement also captured all six model sections closed and
  expanded, Espresso range guidance, and scrolled large-text guidance for
  native review. The revised disclosure also verifies that the setting remains
  visible, its scale explanation appears on demand, and pure-click units remain
  visible before expansion.
