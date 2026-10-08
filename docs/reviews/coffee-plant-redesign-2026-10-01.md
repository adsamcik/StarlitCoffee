# Coffee Plant Redesign — 2026-10-01

Coffee plant now grows from a compact bean into a fuller coffee bush with three
pairs of glossy leaves, two visible fruiting branches and four cherries that
ripen through the final frames. This follows the user's request to redesign it
after Latte bloom. The earlier seedling kept two mature leaf pairs for most of
its second half, with repeated tip changes and little final payoff.

The new bean occupies less of the mature silhouette. Larger curved leaves,
air between tiers and a visible central stem make the growth readable at 148px.
Existing leaves stay in place as new upper pairs open. Two fruit buds appear
at F14–15, four persist from F16, and staggered green-to-gold-to-ruby ripening gives
the last two rows a clear coffee finish.

The existing `coffee_plant` ID, label, selection and countdown behavior remain
compatible. Only this bloom's artwork is replaced; its description refers to
glossy leaves and ripening cherries in all 23 explicitly defined locales.

## Artwork and review

The built-in image generator supplied all artwork. The first full atlas was
rejected for whole-plant scale resets and reconstructed leaf tiers. A fresh
generation had a rectangular source canvas and could not pass the existing
importer. Its square repair fixed the scale and leaf continuity but introduced
one early cherry at F12 that vanished at F13. An atlas-wide edit failed to remove
it. Editing the isolated F12 sprite removed that cherry successfully.

The selected source combines 24 complete generated poses from the square
repair with that one complete native-generated replacement. Assembly only
converts the isolated sprite's square canvas to the established 256px coordinate
space and registers its bean base. It does not paint anatomy, erase fragments,
morph leaves or interpolate poses. The other 24 source poses are byte-identical.
The existing importer subsequently moves eight poses right by one source pixel
during anchor rounding; their artwork pixels are otherwise unchanged.

Two Astra agents reviewed all 25 final poses in light and dark 148px composites.
Both accepted the exact prepared resource hash and found no remaining blocker.
Their reviews record all 24 adjacent transitions, leaf and fruit retention,
the isolated repair and the small normalization translations.

Minor artistic residuals remain: the repaired F12 bean is slightly warmer and
broader, with stronger leaf veins; F16–18 have restrained green-fruit changes;
F24→25 is a final settling beat. The earlier whole-subject shrinkage and the
premature fruit disappearance are resolved. This is a stylized compressed
growth sequence, rather than a scientifically timed cultivation diagram.

## Reproduction and provenance

The selected source is tracked at
[the curated source atlas](../assets/bloom-coffee-plant-native-source.png).
The application consumes
[the prepared resource](../../app/src/main/res/drawable-nodpi/bloom_coffee_plant_spritesheet.png).

```powershell
python tools\prepare_bloom_alpha_atlas.py docs\assets\bloom-coffee-plant-native-source.png output.png
```

The existing importer applies one common scale, recovers transparent separators,
registers the base and clears alpha below 24. This selected source uses a common
scale of 1.0. The output contains 25 distinct complete RGBA frames in the existing
1280×1280, 5×5, 256px cell atlas with safe insets.

The [manifest](coffee-plant-redesign-2026-10-01.json) records native attempt paths
and hashes, the whole-pose curation, exact normalization, all frame hashes,
locale edits, both Astra reviews and completed validation.
The [prompt index](../../prompts/bloom-spritesheet-native-alpha-prompts.md#coffee-plant)
links every exact generation/edit request, including the isolated repair.

## Validation

The source reproduces the prepared resource byte-for-byte. The focused Android
build uses committed baseline `7c2f8595` plus only this resource and the 23
description changes. `assembleDebug`, `assembleDebugAndroidTest` and Detekt
passed in 2m 45s with 75 executed tasks and no Detekt findings. Concurrent AI,
calculator and notification changes are excluded from that snapshot and
preserved in the shared checkout; the shared checkout was not built for this
follow-up.

The existing native rendering tests use `bloomOnly=coffee_plant`,
`bloomAllFrames=true` and `bloomCapture=true` to check all 25 paused poses in
both themes, all 48 adjacent pairs and two exact chooser-preview/final-frame
matches. Both tests passed on the second invocation in 11.813s on an Android 16,
API 36 emulator at 420dpi. The run produced 50 native 389×389 captures; all 48
adjacent pairs were distinct, with at least 11,404 changed pixels per pair.
An Astra visual review of all native poses in both themes accepted the result
without blockers.

The first invocation failed before its first growth capture with
`No compose hierarchies found in the app`; its preview comparison passed.
The rerun used the same APKs and test code. Both invocation logs are retained,
and the manifest records this failure and the successful rerun separately.
Native still captures establish actual Compose rendering and anchoring.
The source/browser contacts approximate its filtering and halo.

Physical-device playback, live tween timing, dynamic system palettes and the
full unit suite are outside this artwork follow-up's validation. The local
[native pose preview](../../.qa-screens/coffee-plant-redesign-2026-10-01/native-plant-preview.png)
and [inspection GIF](../../.qa-screens/coffee-plant-redesign-2026-10-01/native-plant-playback.gif)
are assembled from whole native captures, rather than a live
recording of the running animation.

Local QA evidence is retained in `.qa-screens/coffee-plant-redesign-2026-10-01`.
The existing comparison viewer now shows the previous Coffee plant seedling
beside this coffee bush. The other 43 bloom resources are unchanged by this
follow-up.
