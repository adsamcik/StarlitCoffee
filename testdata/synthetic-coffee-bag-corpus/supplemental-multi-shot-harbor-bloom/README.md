# Harbor Bloom Multi-Shot Hard-Case Pack

This folder contains an **experimental multi-photo stress pack** for a single
fictional bag identity: `Harbor Bloom` / `Tide Lantern Coffee`.

It is intentionally **not** wired into the automation-ready sidecar corpus
loader yet, because the committed root fixture schema is still `one front +
one back` per metadata file. The app scan pipeline can already merge OCR
across multiple photos, but the version-controlled root corpus model has not
been expanded to represent grouped fragment shots as first-class fixtures.

## What this pack tests

- heavy bag deformation / folds
- partial visibility only
- no shot with the full bag visible
- dim indoor lighting / night phone capture
- glare, noise, shadow, and mild blur
- multi-shot reconstruction of one bag across several fragments

## Files

- `harbor_bloom_front_upper_center_night_fragment.png`
- `harbor_bloom_front_lower_right_glare_fragment.png`
- `harbor_bloom_back_upper_left_night_fragment.png`
- `harbor_bloom_back_upper_center_shadow_fragment.png`
- `harbor_bloom_back_lower_right_barcode_fragment.png`
- `manifest.json` — sidecar metadata describing the bag identity and each shot

## Intended Future Use

Use this pack when evolving the automated corpus toward grouped multi-photo
fixtures, for example:

- `frontShots[]` / `backShots[]`
- arbitrary `photos[]` with `side` and capture-order metadata
- best-effort OCR aggregation tests where no single image contains all fields
