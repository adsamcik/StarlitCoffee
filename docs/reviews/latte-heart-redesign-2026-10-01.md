# Latte-art Heart Redesign — 2026-10-01

Latte bloom now unfolds into a latte-art heart, following the user's chosen
direction. It replaces the cream rosetta in `8735c18`, which read as a cream plant
with a flame tip and reached its finished silhouette too early.

The new sequence starts with an espresso bean and a milk bead, spreads the milk
into a broad oval fan, then rounds the lobes and draws the cleft and lower point
into a clear ivory heart. The final row continues the pull-through gesture.
Warm caramel contours keep the milk readable in both light and dark themes.

The existing `coffee_latte` ID, label, filename and countdown behavior remain
compatible with saved selections. Only this animation's artwork changes. Its
description now refers to a heart in all 23 explicitly defined locales.

## Artwork and review

The built-in image generator supplied the new artwork. Two early attempts were
rejected because they completed the heart too soon and held it through the
final row. A fresh generation using only the final-heart style reference
provided the selected 25-pose sequence. No code draws, morphs or reorders its
anatomy; all poses come directly from the selected native-alpha source.

Two Astra agents independently reviewed all 25 prepared poses in both 148px
themes. Both accepted the selected hash, including every adjacent transition.
The milk fan stays unfinished through the middle, and the final row develops
the heart's cleft, lobe height and lower point.

Minor limitations remain: F5→6 spreads the curl sideways more abruptly than
neighboring steps; F11–14 have subtle oval-fan changes; F20→21 visibly trades
width for the inward pull-through; F21→22 is a quiet step. The later F23–25
continue the finish, rather than repeating a completed heart across the row.

## Reproducible preparation

The selected native source is tracked at
[`docs/assets/bloom-latte-heart-native-source.png`](../assets/bloom-latte-heart-native-source.png).
The app consumes
[`bloom_coffee_latte_spritesheet.png`](../../app/src/main/res/drawable-nodpi/bloom_coffee_latte_spritesheet.png).

```powershell
python tools\prepare_bloom_alpha_atlas.py docs\assets\bloom-latte-heart-native-source.png output.png
```

Preparation only recovers transparent separators, applies one common scale,
registers the bean base and clears alpha below 24. The resulting resource has
25 distinct complete RGBA frames in a 1280 × 1280 atlas, with the existing
inset and baseline contract. No independent frame scaling implies false growth.

The [redesign manifest](latte-heart-redesign-2026-10-01.json) records source,
prompt and frame hashes, exact normalization, both Astra reviews and native
validation. The [prompt index](../../prompts/bloom-spritesheet-native-alpha-prompts.md#latte-bloom)
links the exact generation and repair prompts.

## Validation

The source reproduces the imported resource exactly, and all 25 prepared frames
pass the existing alpha, inset and grid contracts. The other 43 bloom resources
are unchanged by this follow-up.

An isolated snapshot of committed baseline `b44d8a5f` plus only the Latte test
and resource changes passed `assembleDebug`, `assembleDebugAndroidTest` and
Detekt (zero findings). The shared working checkout separately failed on four
Detekt findings and three unresolved `backend` references in concurrent AI
changes outside this task. Its build is not reported as passing. The full unit
suite was not rerun for this artwork follow-up.

Both native rendering tests passed in 15.955 seconds on an Android 16/API 36
emulator at 420dpi. With `bloomOnly=coffee_latte`, `bloomAllFrames=true` and
`bloomCapture=true`, they captured all 25 paused poses in both themes (50
389px-square captures), checked all 48 adjacent pairs for distinct pixels, and
confirmed two exact chooser-preview/final-frame matches. The minimum adjacent
difference was 23,582 pixels; distinctness alone is not a polish assessment.
An independent Astra review of both complete native contact sheets confirmed
the accepted design with no clipping, alpha, registration or heart-legibility
blockers.

The first emulator had an Android system-service crash during a prior package
removal and could not restart successfully. Native validation used the other
available, exclusively claimed emulator, which was released afterward.

The native snapshots establish real Compose rendering and registration. Source
contact sheets and the browser comparison approximate its filtering and halo;
they do not replace native evidence. Physical-device playback, dynamic system
palettes and live tween timing remain outside these checks.

The local [native pose preview](../../.qa-screens/latte-heart-redesign-2026-10-01/native-heart-preview.png)
and [inspection playback](../../.qa-screens/latte-heart-redesign-2026-10-01/native-heart-playback.gif)
are assembled from whole native captures. The 12-second GIF is an inspection
aid, rather than a live recording of the app's motion.

Local QA evidence is retained in `.qa-screens/latte-heart-redesign-2026-10-01`.
The before/after viewer in `.qa-screens/bloom-revision-2026-10-01` now compares
the previous Latte rosetta to this heart while retaining the other 43 variants.
