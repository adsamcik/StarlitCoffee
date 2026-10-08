# Berry Family Redesign — 2026-10-01

Strawberry, Blueberry and Blackberry grow toward richer, distinct final plants. The sequences were developed from each original final composition, preserving identifiable flowers, branching and fruit sites as the plant develops. The completed Raspberry redesign remains unchanged.

## Artwork and review

| Animation | Final composition |
| --- | --- |
| Strawberry | Four seed-textured, conical berries hang from a fuller leafy plant, with a separate white flower and staggered ripening through the finish. |
| Blueberry | Seven dusty-blue berries form uneven clusters beneath two retained pale bell flowers. Pointed oval leaves and visible calyx crowns preserve its identity. |
| Blackberry | Four elongated aggregate berries follow an asymmetric branching cane, with a pale crown flower and a red-to-violet-to-black finish. |

The original final poses were recovered from commit `b7914c30cd7e89829fc9d57cee9f0b07389eb776`. These are native reconstructions of their richer compositions; the selected finals do not retain the original pixels. The manifest records both exact original-reference hashes and new resource hashes.

Astra inspected all 25 poses and all 24 adjacent transitions in light and dark at 148px for each sequence. A different Astra reviewer cross-checked each selection. Native paused Compose captures received a separate visual review. All three exact selected resources were accepted; the manifest retains the complete findings, quieter growth beats and small painted contour variations.

All anatomy comes from the built-in image generator. Whole-pose curation retains native cells, with common bank scaling and integer registration where recorded. The existing importer performs common scaling, base registration and alpha cleanup. No components were mixed, and no intermediate anatomy was drawn or interpolated by code. Pixel differences establish distinct poses, while the visual reviews assess useful growth.

### Strawberry

[Application atlas](../../app/src/main/res/drawable-nodpi/bloom_strawberry_spritesheet.png) · [Selected native source](../assets/bloom-strawberry-native-source.png)

Resource SHA-256: `303273e5833a32012e1507f7dc664779de04af111a70d13ec4889448970e239f`. Source SHA-256: `f83d64f594710c18c441f4fb2c94590f2eabe03441baa1c8a6f1f8df0d6e3fe8`.

Actual native attempts and retained or rejected outcomes:

- `initial`: Retained the first 16 complete poses; the final nine were superseded after review. [Exact prompt](../../prompts/berry-family-redesign-2026-10-01/strawberry/initial.txt).
- `late-bank`: Selected all nine native late poses, with fuller fruit and ripening through the finish. [Exact prompt](../../prompts/berry-family-redesign-2026-10-01/strawberry/late-bank.txt).

Recorded review residuals:

- Small leaf-angle variations remain around F5–8, and minor painted leaf details shift at the F16–17 bank join. The base, canopy and four fruit sites remain coherent.
- Bud swelling at F9–10 is quiet. Later changes emphasize fruit fullness and color, with the final berry finishing red at F25.
- The original final remains slightly more irregular and oversized; the redesigned fruit prominence is materially stronger than the simplified prior version.

### Blueberry

[Application atlas](../../app/src/main/res/drawable-nodpi/bloom_blueberry_spritesheet.png) · [Selected native source](../assets/bloom-blueberry-native-source.png)

Resource SHA-256: `dccee9e7e08816d2ac3a77f489c0ab3b63827987d65cee5f2f2034a3ce42e19b`. Source SHA-256: `8ff06505299a01af4ce6a95e1639dc963dc9434803f848ab9dfacc2f79f85259`.

Actual native attempts and retained or rejected outcomes:

- `initial`: Rejected for missing fruit sites, flower conversion, an early leaf reset and a changed final composition. [Exact prompt](../../prompts/berry-family-redesign-2026-10-01/blueberry/initial.txt).
- `fruiting-bank`: Retained as reference for the complete F19 repair; its direct F19 selection was superseded. [Exact prompt](../../prompts/berry-family-redesign-2026-10-01/blueberry/fruiting-bank.txt).
- `fruiting-bank-repair`: Selected 15 complete fruiting poses; its F19 candidate was replaced to remove a ripening reversal. [Exact prompt](../../prompts/berry-family-redesign-2026-10-01/blueberry/fruiting-bank-repair.txt).
- `early-bank`: Rejected because its final early stages contained only five fruit sites. [Exact prompt](../../prompts/berry-family-redesign-2026-10-01/blueberry/early-bank.txt).
- `early-bank-repair`: Selected all nine complete early poses. [Exact prompt](../../prompts/berry-family-redesign-2026-10-01/blueberry/early-bank-repair.txt).
- `tone-repair`: Selected a complete native F19 replacement, accepted for consistent color and fruit identity. [Exact prompt](../../prompts/berry-family-redesign-2026-10-01/blueberry/tone-repair.txt).

Recorded review residuals:

- Fruit-site introduction at F6–7 and canopy expansion at F9–10 are brisk forward steps.
- Several green-fruit swelling beats at F10–16 are subtle at 148px; enlargement and calyx development remain visible across the sequence.
- Small contour and bean-highlight differences remain around the native F19 replacement. The distracting tone flash is resolved.

### Blackberry

[Application atlas](../../app/src/main/res/drawable-nodpi/bloom_blackberry_spritesheet.png) · [Selected native source](../assets/bloom-blackberry-native-source.png)

Resource SHA-256: `0e485f07a1776432cb22bd8a01c923b112f4aa048fe7563ada2f03a9845d68b7`. Source SHA-256: `362370976187015bdac3ff9628626edb8b39c8227cec3feee7b0b900c502d331`.

Actual native attempts and retained or rejected outcomes:

- `initial`: Rejected for an early growth shrink, a transient fifth berry and quiet late holds. [Exact prompt](../../prompts/berry-family-redesign-2026-10-01/blackberry/initial.txt).
- `repair-01`: Selected as the base, retaining 20 complete poses after the narrow early and late repairs. [Exact prompt](../../prompts/berry-family-redesign-2026-10-01/blackberry/repair-01.txt).
- `early-bridge`: Selected one complete native pose for F5 to bridge the unfolding upper leaves. [Exact prompt](../../prompts/berry-family-redesign-2026-10-01/blackberry/early-bridge.txt).
- `late-ripening`: Selected four complete native poses for F22–25, extending the last two berries’ ripening. [Exact prompt](../../prompts/berry-family-redesign-2026-10-01/blackberry/late-ripening.txt).

Recorded review residuals:

- Bud development before F11 and red-fruit swelling around F16–18 are quieter than the neighboring growth and color changes.
- Minor painted leaf, fruit and brightness differences remain where the late native bank begins, with stable fruit count and overall scale.
- The last purple-to-black change and final settling are restrained. The original tiny serrations and branch curves are somewhat simplified at 148px.

## Reproduction and validation

Each selected source reproduces its application atlas byte for byte using the existing importer:

```powershell
python tools\prepare_bloom_alpha_atlas.py docs\assets\bloom-strawberry-native-source.png output.png
```

Use the corresponding Blueberry or Blackberry source for those sequences. The [canonical manifest](berry-family-redesign-2026-10-01.json) records exact prompt and source hashes, source lineage, normalization, frame hashes, reviews and native validation. The [prompt index](../../prompts/bloom-spritesheet-native-alpha-prompts.md) retains the earlier checkpoint prompts alongside these follow-up requests.

The isolated build uses committed baseline `c1a7c0f2a627fe7e8e3bb2729c8ce4107cdc4d61` plus exactly the three accepted atlas overlays. `assembleDebug`, `assembleDebugAndroidTest` and Detekt passed, with zero Detekt findings. The packaged application ID is `com.adsamcik.starlitcoffee.berryreview`.

Six existing instrumentation tests passed: two per species. The tests produced 150 native captures, 144 distinct adjacent comparisons and six exact preview/finish comparisons. Every sequence contains 25 distinct poses with safe atlas bounds. Source reproduction and the original-reference hashes were independently checked.

Native inspection used the Android 16/API36 emulator at 420dpi. The [native inspection GIF](../../.qa-screens/berry-family-redesign-2026-10-01/native-berry-family-playback.gif) is assembled from paused captures. It does not establish live tween timing. Physical devices, dynamic palettes, release readiness and the full unit suite were outside this follow-up. Concurrent shared-checkout work was not rebuilt.

Only three application atlases change; all other 41 bloom atlases, including Raspberry, remain byte-identical to the baseline. Animation IDs, localized descriptions, countdown mapping and renderer remain compatible. Local evidence is retained under `.qa-screens/berry-family-redesign-2026-10-01`.
