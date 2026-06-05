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

## Current Starter Set

The committed prototype corpus lives in
`testdata/synthetic-coffee-bag-corpus/`.

It currently contains five fictional bags:

- `Q0` English studio-clean baseline
- `Q1` German good real-world capture
- `Q2` Czech ordinary consumer capture
- `Q3` Italian degraded-but-recoverable capture
- `Q4` French failure-mode capture

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

## Important Limitation

This starter corpus is image-generator-native. It is good for visual pipeline
testing, regression coverage, and multilingual prompt work. For any serious
benchmarking milestone, keep a small real-photo holdout set alongside it.
