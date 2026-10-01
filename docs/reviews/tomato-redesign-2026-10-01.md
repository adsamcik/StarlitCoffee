# Cherry Tomato Redesign — 2026-10-01

Cherry tomato now grows toward the richer original finish: an asymmetric vine
with serrated compound foliage, three unequal tomato clusters and two yellow
blossoms. The final clusters contain three tomatoes on the left, four on the
right and two above. This restores the abundance and branching character lost
in the earlier four-fruit revision.

The user requested working backward from the original finish. Astra mapped its
foliage landmarks, fruiting trusses and nine eventual fruit sites before planning
the younger poses. The bean sprouts into retained compound leaf sprays; trusses
and blossoms develop before green fruit enlarges and ripens in staggered groups.
Once established, fruit and branches persist through the finish.

The original pose was recovered from commit `b7914c30`, frame 25 of its
1280×1280 atlas. The final artwork is a native reconstruction guided by that
pose, rather than a pixel-identical copy. A direct swap of the old final was
rejected because its branch geometry and paint would jump at the last frame.
The reconstruction keeps the richer composition while matching the surrounding
sequence's materials and anatomy.

## Artwork and consistency

The built-in image generator supplied all new artwork. The first atlas restored
the clusters but lost a lower leaf when fruit appeared, ripened both lower
clusters abruptly and reached its finish too early. A full repair improved the
growth and late ripening but still omitted the lower-left leaf in frames 13–14.
A targeted native edit of a four-pose panel repaired those two complete poses.

The selected source combines 23 complete poses from the repaired atlas with the
two complete edited poses. Their source cells are converted together into the
256px coordinate space and registered at the bean base. Assembly does not draw
anatomy, mix components, interpolate poses or apply individual zoom corrections.
The other 23 selected source poses remain byte-identical to their source bank.

The existing importer applies one common scale of approximately 0.999256,
registers the base and clears alpha below 24. The resulting resource has 25
distinct complete RGBA poses with safe margins. Pixel uniqueness is accompanied
by Astra's visual review of all 24 transitions in both 148px themes.

Minor residuals are recorded in the manifest: native paint varies slightly in
the two repaired poses; frames 22→23 are a quieter orange-fruit stage; 24→25 is a
final settling beat. These do not conceal leaf loss, fruit replacement, backward
ripening or a row-boundary scale reset.

## Reproduction and provenance

The [curated source atlas](../assets/bloom-cherry-tomato-native-source.png)
reproduces the [application resource](../../app/src/main/res/drawable-nodpi/bloom_cherry_tomato_spritesheet.png):

```powershell
python tools\prepare_bloom_alpha_atlas.py docs\assets\bloom-cherry-tomato-native-source.png output.png
```

The [manifest](tomato-redesign-2026-10-01.json) records original-reference
provenance, all native attempts and hashes, complete-pose curation, normalization,
frame hashes, visual reviews and validation evidence. The
[prompt index](../../prompts/bloom-spritesheet-native-alpha-prompts.md#cherry-tomato)
links the exact generation and edit requests.

The existing `cherry_tomato` ID, label, description, selection, countdown mapping
and renderer remain compatible. Only this bloom's application artwork changes.
The other 43 bloom resources remain unchanged by this follow-up.

## Validation

The saved source reproduces the prepared resource byte-for-byte. The focused
build uses committed baseline `e25e8b05` plus exactly one atlas overlay.
`assembleDebug`, `assembleDebugAndroidTest` and Detekt passed in 3m 40s with
75 executed tasks and no Detekt findings. Concurrent shared-checkout work is
preserved; this isolated build does not establish that the shared checkout or
full unit suite passes.

Both native rendering tests passed on the first invocation in 13.121s on a
headless Android 16, API 36 emulator at 420dpi. The tests produced 50 native
389×389 captures: all 25 paused Compose poses in both themes, all 48 adjacent
pairs and two exact chooser-preview/final-frame comparisons. Every adjacent
pair differed by at least 18,372 pixels. The device claim was released after the
captures were retrieved. Astra reviewed all native poses and adjacent transitions
in both themes and accepted the result without blockers. The
[native preview](../../.qa-screens/tomato-redesign-2026-10-01/native-tomato-preview.png)
and [inspection GIF](../../.qa-screens/tomato-redesign-2026-10-01/native-tomato-playback.gif)
are assembled from those captures, rather than a live animation recording.

Physical-device playback, live tween timing, dynamic system palettes and a
release build are outside this artwork follow-up's validation. Local QA evidence
is retained in `.qa-screens/tomato-redesign-2026-10-01`. The comparison viewer
continues to use the original tomato sequence on the left and the new artwork
on the right, so the original final remains available for comparison.
