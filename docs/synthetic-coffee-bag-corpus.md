# Synthetic Coffee Bag Corpus

This document defines the committed synthetic coffee bag corpus used to
exercise Starlit Coffee's OCR-first bag scan pipeline with controlled language,
packaging, and capture-quality variation.

## Goals

- Cover the original target languages: English, German, Czech, Italian, and French.
- Keep near-perfect baseline fixtures for the happy path.
- Add messier, more realistic retail packaging and phone-capture variation.
- Support both classic front/back pairs and front-only fixtures.
- Keep every fixture self-describing through a per-fixture sidecar file.
- Preserve one explicit back-only unlabeled reverse-side control capture.

## File Layout

The committed corpus lives in `testdata/synthetic-coffee-bag-corpus/`.

Each fixture now uses a sidecar layout:

- `<fixture>.front.<ext>`
- `<fixture>.back.<ext>` when present
- `<fixture>.metadata.json`

Example:

- `scb-020-en-q1_kieni.front.png`
- `scb-020-en-q1_kieni.back.png`
- `scb-020-en-q1_kieni.metadata.json`

The sidecar is the ground-truth contract. It declares:

- `schema_version = 2`
- `id`
- `automation_ready`
- `photos.front` and/or `photos.back`
- `language`
- all 14 extraction fields in `fields`
- `extras.captureTier`
- optional extras such as `barcode`, `riskTags`, and split-layout hints

JSON `null` means `not_visible`.

## Current Composition

The current root corpus contains:

- 13 original audited core fixtures
- 15 additional realistic / format-variation fixtures
- 1 back-only unlabeled reverse-side control

The newer additions include:

- reference-inspired local-roastery sticker pouches
- commercial espresso retail bags
- folded-top paper bags
- vacuum brick grocery packs
- premium specialty boxes
- playful white specialty pouches
- Scandinavian data-heavy pouches
- night handheld foil bags

Not every committed fixture must be two-sided. The benchmark loaders use
`automation_ready = true` to decide what participates in automated runs.

## Quality Tiers

### `Q0` Studio Perfect

- Straight-on or near-straight-on capture
- Sharp focus across the whole label
- Even lighting
- Minimal glare
- Full label visibility

Expected use:
- Prove the happy path works
- Catch regressions where clean labels stop parsing

### `Q1` Good Real-World

- Mild perspective
- Light wrinkles
- Small glare patches
- Mostly complete visibility

Expected use:
- Careful user capture
- Clean webshop-style product photos that are not part of the Q0 gate

### `Q2` Ordinary Consumer

- Stronger perspective
- Home lighting
- Partial hand occlusion
- Mild crop or softness

Expected use:
- Default day-to-day scan coverage

### `Q3` Degraded but Recoverable

- Night phone photos
- Glare, motion softness, or stronger grip deformation
- Some text still recoverable, some fields legitimately not visible

Expected use:
- Stress OCR and field extraction without turning the image into noise

### `Q4` Failure Mode

- Multiple real-world issues combined
- Some fields should correctly remain `not_visible`

Expected use:
- Validate abstention and non-hallucination behavior

## Automation Rules

- The benchmark loaders consume only fixtures with `automation_ready = true`.
- The committed Q0 gate remains anchored in the original audited clean set.
- Barcodes are recorded in `extras.barcode` when digits are visibly rendered.
- Duplicate rendered barcodes are preserved as raster truth and explicitly
  marked with `extras.barcodeUnique=false` plus `barcodeCollisionGroup`.
- Split-layout bags continue to declare `requiresSideFusion`, `labelLayout`,
  `frontFields`, and `backFields`.
- When front/back panels conflict, metadata follows the side chosen in the
  fixture notes. The notes must explain the drift.

## Loader Compatibility

The shared loader lives in
`app/src/sharedTest/kotlin/com/adsamcik/starlitcoffee/test/corpus/CorpusModels.kt`.
It prefers the committed sidecar format (`schema_version = 2`) but still
accepts the old monolithic `corpus_metadata.json` (`schema_version = 1`) for
local override directories.

## How The Tests Use It

- `CoffeeBagCorpusExtractionTest` validates sidecar structure, field coverage,
  and parser contract.
- `CorpusMetadataReadinessTest` validates automation-ready front-photo wiring,
  barcode metadata sanity, and split front/back layout hints.
- `BagFieldScorerTest`, `FieldSpecTest`, and `QualityReportTest` pin
  deterministic scoring behavior.
- `OcrFixtureCaptureTest`, `LlmCorpusBenchmarkTest`, and
  `BagScanBestCaseGateTest` use only `automation_ready = true` fixtures pushed
  by `./gradlew pushTestImages`.

## Limitation

This corpus is still image-generator-native. It is much closer to messy
real-world packaging than the original clean set, but it is not a substitute
for occasional manual validation against truly novel retail labels.
