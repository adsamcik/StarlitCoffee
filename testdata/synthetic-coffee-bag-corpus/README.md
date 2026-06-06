# Synthetic Coffee Bag Corpus

This folder is the committed, version-controlled coffee-bag test dataset for
Starlit Coffee scan testing. Every bag is fictional and AI-generated, so the
images can live in the repo (unlike copyrighted real label photos, which have
been fully retired).

The set contains **thirteen** bag identities, each with a front/back pair.

## Contents

- `corpus_metadata.json` — ground truth for every bag (schema v1)
- `prototypes/*.webp` — front/back label images

Images are lossy **WebP** (~1024×1536, quality 85). Text stays crisp enough for
OCR while keeping the whole corpus small (~4.5 MB for 26 images vs ~67 MB as
PNG).

Every bag has:

- one front image, one back image
- one metadata entry with all 14 extraction fields declared (JSON `null` =
  `not_visible`)
- a quality tier `Q0`..`Q4` in `extras.captureTier`
- back-label barcode digits captured in `extras.barcode` for OCR/barcode test
  wiring
- for split-layout bags, explicit `extras.requiresSideFusion`,
  `extras.labelLayout`, `extras.frontFields`, and `extras.backFields`

Three bags are "split front/back" designs: the front carries only
shopping-level info while the back carries the extraction detail, exercising
`BagOcrTextMerger` multi-side reasoning.

There is also one **experimental** grouped-shot pack under
`supplemental-multi-shot-harbor-bloom/` for night, deformed, no-full-bag
captures. It is intentionally outside `corpus_metadata.json` until the schema
can model more than one front/back image per bag.

An additional **experimental** visual-variation pack lives under
`supplemental-handheld-varied-bag-types/` for broader bag-format diversity:
kraft flat-bottom bags, folded-top paper bags, quad-seal espresso bags,
vacuum bricks, minimal white pouches, and tall foil side-gusset bags.

## Intended Use

- Parser and prompt-contract validation (`CoffeeBagCorpusExtractionTest`)
- Deterministic scoring-harness validation (`BagFieldScorerTest`,
  `QualityReportTest`, `FieldSpecTest`)
- On-device OCR + field-extraction quality reporting (`LlmCorpusBenchmarkTest`)
- The Q0 best-case integration gate (`BagScanBestCaseGateTest`)
- Multilingual regression checks (en / de / cs / it / fr)

## Schema Compatibility

`corpus_metadata.json` follows `schema_version = 1`. The single definition of
the model lives in the shared harness
(`app/src/sharedTest/kotlin/.../test/corpus/CorpusModels.kt`), consumed by both
the JVM unit tests and the instrumented tests. Field keys use the LLM-side
names (`process`, `roastLevel`, `isDecaf`); the `process → processType` mapping
to app-internal names is owned by `FieldSpec`.

## Running

Pure JVM (no device):

```bash
./gradlew :app:testDebugUnitTest --tests '*CoffeeBagCorpusExtractionTest*'
./gradlew :app:testDebugUnitTest --tests 'com.adsamcik.starlitcoffee.test.corpus.*'
```

The unit tests resolve this folder automatically from the repo root; an
override is still honoured via `MINDLAYER_COFFEE_BAG_CORPUS` /
`-Dmindlayer.coffee.bag.corpus=<dir>`.

On device:

```bash
./gradlew pushTestImages
./gradlew connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.adsamcik.starlitcoffee.benchmark.OcrFixtureCaptureTest"   # once
./gradlew connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.adsamcik.starlitcoffee.benchmark.LlmCorpusBenchmarkTest"     # quality numbers
./gradlew connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.adsamcik.starlitcoffee.benchmark.BagScanBestCaseGateTest"    # Q0 gate
```

`pushTestImages` pushes `corpus_metadata.json` + `prototypes/` to
`/data/local/tmp/coffee-bags/` (relative paths preserved).

## Quality Lanes

- **Quality report** (`LlmCorpusBenchmarkTest`) is NOT pass/fail — it writes
  `llm-fixture-quality-report.{json,txt}` with per-field/per-tier accuracy.
- **Best-case gate** (`BagScanBestCaseGateTest`) IS pass/fail: every visible
  gate field (`name, roaster, origin, process, roastLevel, weight`) on every
  `Q0` bag must extract exactly. Run the required lane with
  `-Pandroid.testInstrumentationRunnerArguments.starlit.quality.required=true`
  so a missing Mindlayer/corpus hard-fails instead of skipping.

## Audit Note

The metadata reflects the rendered images, not just the prompts that created
them. The `Q0` bags were re-audited against the rendered WebP — the visible
`Producer` on each back label is recorded in `farm`. When degrading a photo,
degrade the photo, not the metadata: keep ground truth aligned to what is
actually visible on the raster.

Barcode metadata follows the printed digits visible on the raster. These are
fictional test digits, not real product identifiers; duplicate rendered
barcodes are flagged with `extras.barcodeUnique=false` plus a
`barcodeCollisionGroup` so barcode-based tests do not assume uniqueness.
