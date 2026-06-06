# Synthetic Coffee Bag Corpus

This document defines the starter synthetic coffee bag corpus used to exercise
Starlit Coffee's OCR-first bag scan pipeline with controlled multilingual and
capture-quality variation.

## Goals

- Cover English, German, Czech, Italian, and French labels.
- Always provide a front/back pair for each fictional bag.
- Include at least one near-perfect baseline image.
- Include progressively degraded captures that still look like real coffee bag
  photos from consumer devices.
- Keep one explicit decaf example in the set.
- Capture visible back-label barcode digits in metadata so OCR/barcode tests
  can reuse the same corpus.

## Current Starter Set

The committed prototype corpus lives in
`testdata/synthetic-coffee-bag-corpus/`. Images are lossy WebP (~1024×1536,
quality 85) and are version-controlled — the whole 26-image set is ~4.5 MB.
Because every bag is fictional and AI-generated, the images can ship in the
repo; the old copyrighted real-photo set has been fully retired.

It currently contains thirteen fictional bags:

- `Q0` English studio-clean baseline
- `Q1` German good real-world capture
- `Q2` Czech ordinary consumer capture
- `Q3` Italian degraded-but-recoverable capture
- `Q4` French failure-mode capture
- `Q0` English celestial-map graphic design
- `Q0` German Bauhaus geometric design
- `Q1` Czech botanical block-print design
- `Q2` Italian risograph collage design
- `Q0` French topographic sun-map design
- `Q1` English split-front/back nautical design
- `Q2` German split-front/back technical design
- `Q1` French split-front/back art-deco design

The split-front/back bags carry structured extras metadata for automation:
`requiresSideFusion`, `labelLayout`, `frontFields`, and `backFields`.

## Quality Tiers

### `Q0` Studio Perfect

- Straight-on or near-straight-on capture
- Sharp focus across the whole label
- Even lighting
- Minimal glare
- Full bag visible

Expected use:
- Prove the happy path works
- Catch regressions where clean labels suddenly stop parsing

### `Q1` Good Real-World

- Mild perspective
- Light wrinkles
- Small glare patches
- Still comfortably readable by a human

Expected use:
- Best approximation of a careful user capture

### `Q2` Ordinary Consumer

- Stronger perspective
- Home lighting
- Slight label curvature
- Minor softness or crop

Expected use:
- Default day-to-day scan coverage

### `Q3` Degraded but Recoverable

- Stronger glare
- Motion softness
- Partial crop or shadow falloff
- Some fields slower to read but still recoverable

Expected use:
- Stress OCR and field extraction without making every field impossible

### `Q4` Failure Mode

- Multiple real-world issues combined
- Some fields may be unreadable or should correctly return `not_visible`
- Still must look like a plausible real coffee bag photo, not synthetic noise

Expected use:
- Validate abstention behavior and non-hallucination behavior

## Generation Rules

- Use the built-in image generation tool for the final raster images.
- Treat front/back as separate photos, not mirrored duplicates.
- Keep the bag identity stable across the pair: same roaster, same coffee name,
  same material, same overall packaging design.
- Include some bags where the front intentionally carries only shopping-level
  information and the back carries the extraction detail. This exercises
  `BagOcrTextMerger` and multi-side reasoning more realistically.
- If the rendered back label shows barcode digits, store them in
  `extras.barcode`. Treat these as fictional fixture digits, not product
  identity. If two rendered bags collide on the same barcode, keep the raster
  truth and mark the collision with `extras.barcodeUnique=false` plus a
  `barcodeCollisionGroup`.
- Degrade the photo, not the metadata. When a field becomes unreadable because
  of capture quality, keep the ground truth aligned to what is actually visible
  on the rendered image.
- Audit the final raster after generation. Do not trust the prompt text
  blindly; generated labels can drift.
- Store concept-field ground truth in canonical English to match the current
  LLM contract:
  - `origin`
  - `region`
  - `process`
  - `roastLevel`
  - `tastingNotes`
- Keep proper nouns verbatim from the rendered bag when possible:
  - `name`
  - `roaster`
  - `farm`

## Scaling Guidance

When expanding this starter set, vary three axes independently before combining
them heavily:

- Capture quality: glare, blur, crop, lighting, perspective
- Label complexity: dense back label, small text, stickers, handwritten date
- Semantic difficulty: decaf, multi-origin, unusual process, translated notes

Recommended distribution for a larger synthetic corpus:

- `Q0`: 20%
- `Q1`: 25%
- `Q2`: 30%
- `Q3`: 20%
- `Q4`: 5%

## How the Tests Use It

- `CoffeeBagCorpusExtractionTest` (JVM) validates the metadata structure +
  parser contract; it resolves this folder automatically.
- `BagFieldScorerTest` / `QualityReportTest` / `FieldSpecTest` (JVM) pin the
  scoring + metric math deterministically (no model).
- `LlmCorpusBenchmarkTest` (instrumented) runs the real OCR fixtures + LLM and
  writes a per-field/per-tier quality report — numbers, never pass/fail.
- `BagScanBestCaseGateTest` (instrumented) runs the full pipeline on the `Q0`
  bags and must pass 100% on the gate fields
  (`name, roaster, origin, process, roastLevel, weight`).

Field-specific scoring (multilingual origin/process aliases, order-insensitive
tasting notes, gram-normalized weight, format-agnostic dates) lives in
`FieldSpec`, so the gate stays meaningful without being brittle.

## Limitation

This corpus is image-generator-native: excellent for visual pipeline testing,
regression coverage, multilingual prompt work, and a reproducible best-case
gate. It is not a substitute for occasional manual validation against genuinely
novel real-world labels — but per project policy, no real label photos are
committed to this repo.
