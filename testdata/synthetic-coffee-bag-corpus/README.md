# Synthetic Coffee Bag Corpus

This folder is the committed, version-controlled coffee-bag test dataset for
Starlit Coffee scan testing. Every bag is fictional and AI-generated, so the
images can ship in the repo without bringing back copyrighted real-label
photos.

The corpus now uses **per-fixture sidecars** instead of one monolithic
manifest. Each fixture lives as:

- `<fixture>.front.<ext>` and optionally `<fixture>.back.<ext>`
- `<fixture>.metadata.json`

Example:

- `scb-001-en-q0_moonlit_orchard.front.webp`
- `scb-001-en-q0_moonlit_orchard.back.webp`
- `scb-001-en-q0_moonlit_orchard.metadata.json`

## Current Set

- 29 sidecar metadata files
- 28 automation-ready fixtures used by the JVM/instrumented corpus loaders
- 13 original audited core bags
- 15 additional reference-inspired / format-variation fixtures
- 1 back-only unlabeled reverse-side control image

The committed set now spans:

- `en`, `de`, `cs`, `it`, `fr`
- one bilingual `en` + `mr` reference fixture
- one `und` reverse-side control fixture

Image formats remain mixed by source:

- original audited corpus images: WebP
- newer realistic/handheld additions: PNG

## Metadata Contract

Every `.metadata.json` file follows `schema_version = 2` and contains:

- `id`
- `automation_ready`
- `photos.front` and/or `photos.back`
- `language`
- all 14 extraction fields in `fields`
- `extras.captureTier`
- optional bag-specific extras such as `barcode`, `riskTags`, and layout hints

Field values use JSON `null` for `not_visible`. That rule applies to both the
audited core fixtures and the newer messy real-world-inspired additions.

The shared schema definition lives in
`app/src/sharedTest/kotlin/com/adsamcik/starlitcoffee/test/corpus/CorpusModels.kt`.
The loader prefers sidecars (`schema_version = 2`) but still accepts the old
legacy `corpus_metadata.json` format (`schema_version = 1`) for ad hoc local
overrides. The committed repo corpus no longer uses the legacy format.

## Intended Use

- Parser and prompt-contract validation (`CoffeeBagCorpusExtractionTest`)
- Deterministic scoring-harness validation (`BagFieldScorerTest`,
  `QualityReportTest`, `FieldSpecTest`)
- On-device OCR + field-extraction quality reporting (`LlmCorpusBenchmarkTest`)
- The Q0 best-case integration gate (`BagScanBestCaseGateTest`)
- Multilingual and messy-capture regression checks

`automation_ready = true` controls which fixtures are loaded by the benchmark
and gate harnesses. The back-only unlabeled control remains in the folder for
reference, but is intentionally excluded from automation.

## Running

Pure JVM:

```bash
./gradlew :app:testDebugUnitTest --tests '*CoffeeBagCorpusExtractionTest*'
./gradlew :app:testDebugUnitTest --tests 'com.adsamcik.starlitcoffee.test.corpus.*'
```

On device:

```bash
./gradlew pushTestImages
./gradlew connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.adsamcik.starlitcoffee.benchmark.OcrFixtureCaptureTest"
./gradlew connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.adsamcik.starlitcoffee.benchmark.LlmCorpusBenchmarkTest"
./gradlew connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.adsamcik.starlitcoffee.benchmark.BagScanBestCaseGateTest"
```

`pushTestImages` now pushes all root-level `*.metadata.json` sidecars plus the
referenced sibling image files to `/data/local/tmp/coffee-bags/`.

## Experimental Packs

Grouped-shot and fragment experiments still live in their own subfolders and
remain outside the automation-ready corpus loader:

- `supplemental-multi-shot-harbor-bloom/`
- `supplemental-multi-shot-harbor-bloom-handheld/`

Those packs keep their own manifest-style metadata because they model
multi-photo fragment sets rather than one front/back fixture.
