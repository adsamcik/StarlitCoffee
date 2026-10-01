# Bloom Animation Revision — 2026-10-01

This report records the 44-animation revision in commit `8735c18`. Subsequent
replacements are documented in the [latte-art heart redesign](latte-heart-redesign-2026-10-01.md)
and [coffee bush redesign](coffee-plant-redesign-2026-10-01.md), followed by the
[tomato vine reconstruction](tomato-redesign-2026-10-01.md), including their
current source hashes and native-frame evidence.

All 44 registered bloom animations have revised native-alpha artwork. Astra reviewed all 25 poses of each sequence in light and dark small-size composites. Existing animation IDs, selections, weights, countdown mapping and completed-bloom behavior remain compatible.

The new sequences distribute visible unfolding through the middle, preserve introduced anatomy, and develop the finish progressively. The most damaged originals—Magnolia, Morning Glory, Fuchsia, both irises, Protea and Water Lily—now have complete, intact sprites and colored petal surfaces. Whole native-generated poses were curated where a repair retained an isolated reversal; the manifest records every selected source and frame.

## Implementation

- The built-in image generator supplied the artwork. Deterministic preparation recovers transparent cell separators, uses one common scale, aligns the bean base and clears low-alpha matte noise. It does not paint, morph or interpolate anatomy.
- All 44 app resources are 1280 × 1280 RGBA, with 25 distinct complete poses each. The new y=232 baseline and nominal 24-pixel inset allow one pixel of inclusive-coordinate/rounding tolerance.
- Finished previews now use the same frame-25 registration as the played animation. Both retain the existing theme-aware halo.
- The legacy keyed importer removes guide colors only on recovered guide bands, protecting legitimate pink and purple petals.
- The active authoring contract uses native alpha and replaces the old closed-frame-20/late-release direction. The historical keyed prompts remain identified as superseded.

## Verification

- All 44 selected sources reproduce their prepared PNG hashes through the current importer.
- All 1,100 frames are nonempty, meet the inset/baseline contract, have cleared matte noise, and are unique within their own sequence.
- The modern-grid verifier passes; all 9 pipeline regression tests pass.
- Android debug and instrumentation APK builds pass; 1,507 unit tests pass. The refreshed Detekt task passes with an empty findings report.
- Both native rendering tests pass: all 88 finished previews match the played finish pixel for pixel, and all variants render distinct paused F01/F13/F25 stages in both static themes. All 264 captures use the real Compose renderer at 148dp (389px at 420dpi).
- Independent Astra cross-checks accept the final hashes for nine anatomy-sensitive variants; a final read-only code review found no meaningful correctness blocker.

These checks separate source-art review from native rendering proof. The browser comparison approximates registration and halos; it is not Android rendering evidence. Physical-device playback and dynamic system palettes were not tested. Minor painted contour variations and some restrained settling poses remain; no claim is made that pixel uniqueness alone proves a valuable frame.

## Per-animation review

| Animation | Original priority | Revised outcome and residual polish |
| --- | --- | --- |
| Coffee flower | Targeted | Astra accepted the prepared sequence after geometric row recovery. All bean fragments/clipped bottoms are gone. Lower petals persist and progressively unfold through F16-25, removing the old major recupping reset. Parent verified broad white surfaces remain legible at 148px in both themes with the renderer halo. Minor F11-12 bud-height softening and F20-21 upper-petal reshaping remain. |
| Starlit coffee | Rebuild | The left loop survives the right loop's appearance around F12 and both remain through F25. The central steam develops into a third loop at F19-21; established loops and stars are retained as the final motif expands. Light and dark themes preserve the luminous outline. Residual polish: F11-12 has a noticeably stronger broad glow than F13, making that transition read partly as a lighting change. F16-18 steam height undulates while the established side loops remain. F21-22 is near-held, then two lower stars add the final accent at F23. |
| Latte bloom | Rebuild | Cream lobes emerge around the central steam and remain attached to the same split bean. Once the layered rosette is established at F15, its bilateral tiers persist through F25. Cream surfaces stay solid and distinct in both themes, with a readable final silhouette. Residual polish: F11-14 develops slowly before the larger tier addition at F15. F16-22 is dominated by small expansion and contour changes; F23-25 grows the upper curl more visibly than the rosette tiers. |
| Coffee plant | Targeted | The first leaf pair persists while the second pair emerges at F13. Both tiers remain clearly separated through F25; neither tier disappears during later growth. The final plant reads clearly in both themes and finishes larger than the middle poses. Residual polish: F9-10 has a larger first-leaf expansion than nearby steps. F15-16 and several later apical-tip changes vary the terminal bud shape and height; the mature leaf tiers remain stable. The last row primarily expands an already-established two-tier plant rather than introducing another distinct unfurling stage. |
| Cherry tomato | Targeted | Four attached nodes are established by F10 and remain through flowering and fruiting. Yellow flowers around F13-14 recede naturally into green fruit at F15-16. Ripening advances through orange/red without reverting any ripe fruit to green. All four final tomatoes remain visible in both themes. Residual polish: The F8-9 transition introduces the lower leaf tier and additional branch structure in a larger step than adjacent poses. F24-25 adds a modest overall foliage/fruit expansion after ripening is already complete; this is a finishing accent, not a node-count jump. |
| Strawberry | Targeted | Four attached fruit stay in place through green growth and staged ripening. The former lower-left red-to-green reversal and extra final flower are removed. F11-12 has slight foliage contour settling between whole native sources. F24-25 is a modest fullness step; all four red fruit persist. |
| Raspberry | Targeted | Four primary berries plus two small upper buds stay coherent. Lower-right ripens first, then the other three mature across the last row without the former late six-fruit jump. F16-18 andF20-21 are relatively subtle swelling steps. The two upper buds remain green intentionally while the four primary berries finish red. |
| Blueberry | Targeted | Four primary berries retain their attachments through flower, green-fruit and blue stages. Two central/lower berries ripen before the outside pair, with no final count jump. F20-23 has subdued size/ripening changes; both remaining outer berries turn blue atF24. This is readable staged development, though less finely staggered than the prompt requested. |
| Blackberry | Targeted | Whole-pose order removes the purple-to-red reversal. Ripening progresses from the upper-right fruit to lower-left and then the last pair; final four blackberries are clearly separate. Fourth lower-right berry becomes prominent late atF23 from an existing branch attachment; three earlier berries remain. F18-19 has a small warm-red hue/shape settling change between native sources. |
| Coffee brew | Rebuild | Side curls emerge atF9-10 and remain visible throughF25; center steam rises and the final curl breadth increases. The old repeated compact finish is replaced. F10-11 shows a modest native contour/height settling change, with both curls retained. F16-22 has smaller changes than the early reveal. |
| Rose | Targeted | The green shoot, red bud, opening rim and broad layered rose now form clear stages; the late petals retain the rose silhouette instead of recupping. F16-18 has minor outer-petal contour and blossom-height settling. F21-25 mainly extend the petal rim; strong final red silhouette stays readable at148px. |
| Lotus | Targeted | Early green emergence now survives, outer ivory petals unfold before inner tiers and gold center. F20-25 retains the complete bowl and expands the center. F13-16 has small painterly petal reshaping between whole native poses. Light background relies on the warm contour, which remains visible at148px. |
| Sunflower | Polish | Bud cup, exposed disk, emerging rays and broad mature flower are distinct. Late rays remain open, removing the old F20-21 recupping; final yellow silhouette is clear in both themes. F16-17 andF21-22 are subtle ray extension/settling steps, with no lost ray tier. |
| Orchid | Targeted | White petals, central column, lower petals and pink lip are ordered so opening anatomy persists. Native spacing repair separates touching row4/5 art; all complete poses now fit safely. F14-18 has mild warmth/outline differences where compatible native poses were curated. Pink lip grows through the late row; final silhouette stays intact. |
| Jasmine | Targeted | Folded white bud opens into a five-petal star; the lower-left petal begins earlier and persists, avoiding a last-frame petal-count pop. F9-10 has a small bud-height settling change andF14-16 modest petal-angle changes. F21-25 subtly broadens the same five-petal flower. |
| Bleeding heart | Targeted | Repaired F10-13 has exactly three green-to-pink buds, F14-16 retains exactly three hearts, and all three persist through F25. The single right arch remains readable; white pendants develop across F21-25 and pink surfaces remain opaque in both themes. Residual polish: F16-20 is visually near-held before the white pendants emerge. The pale pendant extension is weighted toward F21-24; F25 is a modest settling pose. |
| Passionflower | Targeted | Petal cup starts separating by F13–15, exposes corona through F18–20, and lifts reproductive center late. Tendrils and leaf base provide stable recognizable structure. Residual: F20–21 flower face narrows/tilts as center rises; opening remains visible rather than fully reclosing. |
| Bee orchid | Targeted | Pink sepals separate around a consistent central bee-like lip by F15–18. Golden lip details remain discernible in both small themes. Residual: F15–16 top sepal scale varies slightly; final row mainly settles. |
| Jade vine | Rebuild | Whole arch and two major left leaves fit within each cell. Turquoise claws begin unfolding before final row and continue down the raceme. Residual: several terminal green buds remain; progression is stylized rather than literal botanical growth. |
| Bird of paradise | Targeted | Spathe bends before opening; orange blade appears F13–15 and blue blade before final row. Warm orange/cobalt separation is crisp on both themes. Residual: F19–20 left orange fan spreads in a larger step; F21–25 is subtle fanning. |
| Blue Himalayan poppy | Targeted | Hooked stem and bowed bud connect growth to the final drooping flower. Blue surface unfolds progressively F14–20, gold center becomes readable before final row. Final row gently rotates/lifts flower toward viewer; no abrupt replacement by giant terminal bloom. |
| Rafflesia | Targeted | Spotted bud divides and opens from F10; mouth and center appear F16–20. Broad scarlet silhouette and speckles survive148px. Residual: front-lobe separation F18–19 is more discrete than earlier widening; final row mainly relaxes. |
| Chocolate cosmos | Rebuild | Three persistent flower locations replace flower identity switching. Central flower opens first, right next, left opens over F21–25. Burgundy petals remain readable on dark background; smaller left bloom finishes distinctly. |
| Flame lily | Targeted | Pendant bud develops before red/yellow petal unfolding F15–20. Recurving petals and emerging stamens create distinct late structure. Residual: arch becomes occluded by upright petals near F24; quiet final settling. |
| Queen of the Night | Targeted | Three cactus segments retained after repair; early small bud becomes an open white flower F15–20. Warm white layered petals and center remain clear against dark theme. Residual: final blossom is comparatively compact; final row is gentle face opening/settling. |
| Snowdrop | Targeted | Two persistent stems and bells; removed surprise third terminal flower. Tall bell opens first, shorter left bell follows F19–21; green inner marks remain visible. Residual: F11–13 and final row have restrained development. |
| Myosotis sylvatica | Targeted | Repaired F10-13 now preserves exactly five tips: one top and two on each side. All five persist into five blue flowers. The selected late sequence does not reclose; blue surfaces and yellow centers remain intact in both themes. Residual polish: F22-23 lowers and compacts the crown while the lower pair begin opening. This is visible settling, with all five nodes retained. F10-11 bud size changes slightly as the top tip turns blue; this is a small contour variation rather than loss of anatomy. |
| Fuchsia ballerina | Rebuild | F13-20 progressively unfurl the pink sepals and purple skirt; F21-25 extend visible stamens without reclosing. Opaque purple and pink surfaces remain intact in both themes. Residual polish: F20-21 changes the left leaf pose downward abruptly while the flower remains coherent. F16-18 contains restrained silhouette change before the larger skirt opening at F19. |
| Magnolia dawn | Rebuild | F14-21 exposes the center progressively; the ivory petal surfaces remain opaque and the cup does not close backward in either theme. Residual polish: F21-23 has little visible structural development; F24 exposes a small front lip, then F25 adds a much larger divided front tier. The final beat is noticeably stronger than the preceding three poses. |
| Iris origami | Rebuild | The purple standards and two visible side falls persist across F18-25. Yellow markings remain legible and no transparent gaps replace the petals in either theme. Residual polish: F20-21 expands both the flower and foliage more than neighboring steps. F21-25 reads as a restrained settling sequence, with most of the recognisable bloom already present at F21. |
| King protea sunrise | Rebuild | Localized first-row retry removes top clipping and F5->6 height reversal. Intact pink bracts, crown unfolds in middle frames, cream center stays visible once revealed. Final small opening increments subtle but no former shredded surfaces. |
| Dahlia kaleidoscope | Targeted | Localized final-row edit keeps yellow center visible from F20 and progressively opens the inner ring through F25. Earlier coral petal unfolding retained, no old sudden final-frame center appearance. |
| Pincushion firework | Targeted | Native retry fixes premature orange bud. Whole-frame F4/F5 curation restores green bud growth. Progressive filaments F13-25 and stable orange cushion replace original plateau/cone reversal. |
| Morning glory spiral | Rebuild | Whole plants, opaque blue petals and progressive trumpet unfolding replace broken original. Whole-frame late ordering removes F20->21 width shrink. Earlier curled-shoot to leaf transition is stylized; no split/cropped plants. |
| Foxglove chimes | Targeted | All three persistent buds/bells retained after formation. Lower and middle open first; top progressively opens last after whole-frame F20/F21 ordering. Clean intact pink petals and spot detail. |
| Cherry blossom wish | Targeted | Intact petals and three persistent branches; staggered left F12-15/right F17-20/top F22-25 opening. No disappearing flowers. Small bud/branch size variation remains but clear meaningful middle-to-late progress. |
| Himalayan lantern | Targeted | Seam reveal begins F12 and gold bud appears F14, with flower opening F19 onward. Large old plateau removed and final flower expands across last row. Shell-fold topology varies F16-18, a residual continuity caveat; intact and legible source. |
| Black bat flower | Targeted | Bracts begin separating F12 and both persist; cluster and whiskers emerge F18, then arcs lengthen progressively through F25. Dark violet structure much more legible on dark without losing character. Some early bud near-holds remain. |
| Water lily at twilight | Rebuild | Opaque dark composite confirms intact violet petals, no old transparency holes; layers begin opening F11 and gold appears F17. Final inner petal aperture continues opening through F25. 25 whole plants in regular grid. Minor near-held early bud poses remain but no long old plateau. |
| Moonphase magnolia | Targeted | Localized F21/F22 repair keeps both front petals and both stars visible after F20. Earlier sphere seam release and progressive uncup retained; final petal lowering reaches full face at F25. Subtle late petal differences remain. |
| Crema chrysanthemum | Targeted | Crema ribbons release from F11 onward and center is exposed progressively from F16. The old F21->22 topology leap is much reduced; complete flower and intact cream texture. Some petal redraw variation F20-22 remains, but center persists and bloom expands. |
| Stained-glass iris | Rebuild | Final-flower-only regeneration fixes old holes and frontal petal disappearance. All six major petals persist from F15. Spread develops F10-20, but F21-25 remain similar open poses; the final row remains a restrained settling sequence. Accepted as materially improved source with timing caveat. |
| Aurora anemone | Targeted | Localized F16/F21/F22 retry removes row-start reclosure and keeps dark center fully visible after F20. Opaque purple-to-cyan petals, gradual cup opening then flattening. No original missing color bands. |
| Constellation blossom | Targeted | Intact white petals and five-point final form; side petals release F11, gold appears F17, front pair unfold F21-23 without old F24 reclosure. Gold nodes remain readable; fine constellation lines simplified. |

## Evidence and prompts

- [Original Astra review](bloom-animation-review-2026-10-01.md)
- [Revision manifest and source/frame hashes](bloom-animation-revision-2026-10-01.json)
- [Native generation and repair prompts](../../prompts/bloom-spritesheet-native-alpha-prompts.md)
- [Preparation contract](../bloom-spritesheet-splicing.md)

Local QA evidence is retained in `.qa-screens/bloom-revision-2026-10-01`: original assets, selected source atlases, intermediate attempts, opaque contact sheets, before/after viewer, native captures and build/test logs. Final consuming assets are the tracked `app/src/main/res/drawable-nodpi/bloom_*_spritesheet.png` files.

The initial final-build attempt encountered three Detekt findings in concurrently edited recognition code. A later successful analysis from that work produced an empty findings report; this task refreshed Detekt successfully with the result up to date. The bloom work did not change those files.

## Validation evidence

```json
{
  "date": "2026-10-01",
  "artwork": {
    "animations": 44,
    "frames": 1100,
    "unique_poses_per_sequence": 25,
    "source_reproduction_hashes_matched": 44,
    "astra_reviewed_all_poses_both_themes": true,
    "independent_anatomy_cross_reviews": 9,
    "resource_png_bytes_before": 47765868,
    "resource_png_bytes_after": 42886913
  },
  "python_pipeline": {
    "tests": 9,
    "failures": 0
  },
  "grid_verifier": {
    "spritesheets": 44,
    "separate_final_frames": 0,
    "status": "passed"
  },
  "android_build": {
    "tasks": [
      ":app:assembleDebug",
      ":app:assembleDebugAndroidTest"
    ],
    "status": "passed",
    "log": "final-build-core.log",
    "isolated_application_id": "com.adsamcik.starlitcoffee.bloomreview"
  },
  "unit_suite": {
    "tests": 1507,
    "failures": 0,
    "errors": 0,
    "suites": 191,
    "task": ":app:testDebugUnitTest"
  },
  "detekt": {
    "task": ":app:detekt",
    "status": "passed-up-to-date",
    "findings": 0,
    "log": "final-detekt.log",
    "initial_attempt": "Three findings in concurrent RecognitionUiState.kt/BagPhotoExtractor.kt work; subsequent report is empty. No bloom-scope changes to those files."
  },
  "native_rendering": {
    "tests": 2,
    "failures": 0,
    "seconds": 118.444,
    "device": "Android 14 x86_64 emulator",
    "size_dp": 148,
    "size_px": 389,
    "density_dpi": 420,
    "exact_preview_finish_pairs": 88,
    "growth_stage_captures": 264,
    "log": "instrumentation-final.log",
    "boundary": "Static light/dark palettes and paused F01/F13/F25 snapshots. All transitions visually reviewed in contacts. Live tween timing, dynamic system palettes and physical-device playback untested."
  },
  "apks": {
    "bloom-review-final.apk": "490b6b885608f789a24b3c57465a7f8d7f186ff79a63c5214c9c3f4b0b61005d",
    "bloom-review-final-test.apk": "608b48f06de50e7399ca34845b3c098a55a46718f0899b2db5e2ebacee1f5c58"
  },
  "evidence_directory": ".qa-screens/bloom-revision-2026-10-01"
}

```

<!-- raspberry-redesign-2026-10-01:start -->
## Subsequent Raspberry redesign

The [Raspberry follow-up](raspberry-redesign-2026-10-01.md) restores the richer original branched berry-cluster finish. Its [manifest](raspberry-redesign-2026-10-01.json) contains the current resource/source hashes, original-final provenance and native validation. The preceding 44-animation report and hashes remain an archived checkpoint, including its earlier simplified Raspberry artwork.
<!-- raspberry-redesign-2026-10-01:end -->

<!-- berry-family-redesign-2026-10-01:start -->
## Subsequent berry-family redesign

The [berry-family follow-up](berry-family-redesign-2026-10-01.md) gives Strawberry, Blueberry and Blackberry richer, distinct final compositions and consistent growth. Its [manifest](berry-family-redesign-2026-10-01.json) contains the current resource/source hashes, exact prompt provenance and native validation. The preceding 44-animation report and hashes remain an archived checkpoint. The completed Raspberry follow-up remains unchanged.
<!-- berry-family-redesign-2026-10-01:end -->
