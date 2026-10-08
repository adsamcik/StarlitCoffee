# Raspberry Redesign — 2026-10-01

Raspberry develops fuller branching, textured berry clusters and a white blossom toward its richer original finish. The final composition retains the asymmetric foliage, seven large berries (three left and four right), crown blossom, split bean and shallow soil ring.

## Reference and artwork

The original frame 25 was recovered from commit `b7914c30cd7e89829fc9d57cee9f0b07389eb776`. Reference-faithful native reconstruction of the richer original finish.

Original reference provenance and final pixel identity are separate checks:

- Original F25 raw RGBA SHA-256: `cef3605c5a64ca8d150a1197dc888b83f8eda8b5f5566e6e1b376ce5ff0fc921`.
- Exact original final pose in selected source: `false`.
- Exact original final pixels in prepared resource: `false`.

All new artwork comes from the built-in image generator. Actual attempts and outcomes:

- `initial`: Complete source poses1–4 and6–7 retained as canonicalF1–F6; source8–10 supplied bridge context before replacement. Fruiting/terminal sequence rejected for missing berries, leaf loss and ripening reversal. [Exact prompt](../../prompts/raspberry-redesign-2026-10-01/initial.txt).
- `repair-01`: Rejected: missing fruit before final and early leaf loss persisted. [Exact prompt](../../prompts/raspberry-redesign-2026-10-01/repair-01.txt).
- `fruiting-bank`: Seven persistent fruiting nodes retained; three complete poses replaced to keep white flower open. [Exact prompt](../../prompts/raspberry-redesign-2026-10-01/fruiting-bank.txt).
- `flower-repair`: Native complete-pose replacements for canonicalF14/F15/F19; no component mixing. [Exact prompt](../../prompts/raspberry-redesign-2026-10-01/flower-repair.txt).
- `growth-bridge`: Native complete-pose replacements for F7–F9 to introduce fuller branching progressively before fruiting. [Exact prompt](../../prompts/raspberry-redesign-2026-10-01/growth-bridge.txt).

The manifest records any complete-pose curation and its source lineage. The existing importer uses one common scale (`1.000000000`), base registration and alpha cleanup. It does not draw, morph or interpolate anatomy. The prepared atlas contains 25 distinct complete poses. Distinct pixels alone do not establish motion quality.

## Review

The primary and independent Astra reviews accepted the selected resource. The native visual review separately accepted its paused Compose captures. Their complete findings and residuals are embedded in the manifest.

Recorded residuals:

- Small crown height, leaf placement and flower placement shifts from native whole-pose repairs are visible at source size and subtly at148px. They do not read as fruit deletion, replanting or meaningful growth reversal. (F13->F14->F16; F18->F19->F20)
- A fuller canopy/bud step and slight painted-material difference remain at the bridge-to-bank join, now supported by preceding branch development. (F9->F10)
- Subtle swelling/maturation changes; late motion is deliberately quieter after the main color payoff. (F10->F11; F23->F25)
- Brisk F8→9 branching/bud development.
- Quiet F10→11 andF16→17 expansion.
- Small native whole-pose paint/contour variation at flower replacement joins.
- One quiet final settling frame after ordered late ripening.

## Reproduction and scope

The [selected native source](../assets/bloom-raspberry-native-source.png) reproduces the [application atlas](../../app/src/main/res/drawable-nodpi/bloom_raspberry_spritesheet.png) with:

```powershell
python tools\prepare_bloom_alpha_atlas.py docs\assets\bloom-raspberry-native-source.png output.png
```

Prepared resource SHA-256: `3562683749a341e9c4f977eab7e19ef68acd9e92931bfc73905ed6fd03207976`. Selected source SHA-256: `729a2420a43e2244a818cb406c4c26ed281fd31446ff02599d57997b2464bbe0`.

The [canonical manifest](raspberry-redesign-2026-10-01.json) records original-reference provenance, exact prompt hashes, generation attempts, curation, normalization, frame hashes and validation evidence. The [prompt index](../../prompts/bloom-spritesheet-native-alpha-prompts.md#raspberry) retains both the earlier checkpoint prompts and these follow-up requests.

Only the Raspberry application atlas changes in this follow-up. Its animation ID, description, countdown mapping and renderer stay compatible. No locale changes are included.

## Validation

The isolated build uses committed baseline `98eb74b8647fa29689e831353a8e7c1059f3b4cb` plus exactly one Raspberry atlas overlay. `assembleDebug`, `assembleDebugAndroidTest` and Detekt passed with no Detekt findings. This does not establish a clean build for concurrent shared-checkout work, and the full unit suite was not rerun.

The selected native run passed 2 tests and produced 50 captures (25 paused poses per theme), 48 distinct adjacent comparisons and 2 exact preview/finish comparisons. The minimum adjacent difference is 16,391 pixels. The recorded emulator reports Android 16, Physical density: 420.

Instrumentation attempts retained in the validation evidence:

- `instrumentation.log`: passed; SHA-256 `502e2a1854dde8bba0c4bb19224ecad224e85dc675902cc5b941585eea2fb352`.

The [native preview](../../.qa-screens/raspberry-redesign-2026-10-01/native-raspberry-preview.png) and [inspection GIF](../../.qa-screens/raspberry-redesign-2026-10-01/native-raspberry-playback.gif) are assembled from native paused captures; the GIF is not a live recording.

Prepared contacts approximate filtering and halo at148px. Paused native Compose captures verify rendering and adjacent poses, not live tween timing, dynamic-color or physical-device playback. Full unit suite not rerun.

Physical-device playback, dynamic system palettes, live tween timing and release readiness are outside this follow-up. Local evidence is retained in `.qa-screens/raspberry-redesign-2026-10-01`.
