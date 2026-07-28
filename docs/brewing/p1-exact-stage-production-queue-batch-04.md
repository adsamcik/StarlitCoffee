# P1 exact-stage illustration production queue — batch 04

Status: research-only, prompt-ready documentation; no images generated, no
assets registered, and no manifest or app-code changes

Prepared: 28 July 2026

## Scope and source authority

This queue selects the next six highest-value exact-stage illustrations after
batches 01–03. It contains one remaining unblocked `safety-critical` state
whose mechanic is not already reserved exactly, followed by five `mandatory`
stages that prevent common novice errors through distinct equipment or action
geometry.

Every selected row is canonically `NOT_PRODUCED`, has executable exact-stage
guidance, and has no unresolved hard equipment blocker. Proposed bitmap names
are deterministic identifiers only. Nothing in this document means that an
image exists, has passed review, is registered, or is approved.

Canonical source:

- File: `coffee_brewing_library_2026-07-27.json`
- Local source-register copy:
  `/tmp/starlit-coffee-library.JTlCjL/coffee_brewing_library_complete_2026-07-27/coffee_brewing_library_2026-07-27.json`
- SHA-256:
  `aa006a366297d659332986f8971b5442d77bf168eba30e520708742b3f76506d`
- Schema: `1.0.0`
- Canonical verification date: `2026-07-27`
- Current primary-source re-verification date: `2026-07-28`

The canonical JSON was reconciled against:

- `docs/brewing/p1-exact-stage-matrix.md`
- `BuiltInP1ExactStagePlanCatalog`
- `P1ExactGuidanceCatalog`
- `app/src/main/assets/p1_exact_guidance_2026_07_27.json`
- `docs/brewing/asset-production.md`
- current exact-stage files in `drawable-nodpi`
- batch-01, batch-02, and batch-03 production queues

Every exact asset ID reserved by batches 01–03 was excluded, regardless of
whether concurrent production has created a file since this research pass
started. Existing produced batch artwork remains `PENDING_REVIEW` unless and
until its own expert review and manifest-registration work explicitly changes
that state. File presence and a production-log entry are not release coverage.

## Selection boundary

The queue first takes
`switch_ole_boen_hybrid_16_5_240/stage_01`. It is the highest-value remaining
unblocked safety-critical row whose exact retained-bloom state is not already
reserved. Although earlier Switch artwork also uses a closed valve, this stage
requires a visibly shallow, early bloom retained for the first 40 seconds—not
the high-volume retained final pour from batch 02.

Two other unblocked safety-critical rows remain deferred:

- `clever_coffee_first_15_250/stage_04` repeats the bottom-actuated,
  stable-server release already reserved for the water-first Clever.
- `switch_ole_boen_hybrid_16_5_240/stage_04` repeats the open Switch release
  already reserved for the official Switch baseline.

Safety-critical cezve and generic automatic-batch rows remain excluded while
their canonical `BLOCK-CEZVE-HARDWARE` or `BLOCK-AUTO-BATCH-HARDWARE` variant
blocker is unresolved.

The other five slots target distinct, frequent novice errors:

- pouring a standard V60 against the paper wall rather than over the bed;
- making Scott Rao's shallow bed nest into a deep crater;
- tamping a loose gravity-phin disc instead of resting it level;
- removing a Cup-One mug while residual hot coffee is still dripping; and
- adding coffee before the retained water in the water-first Clever sequence.

## Placement verification

Only an approved `InstructionAssetRecord` can render. Draft, queued,
`PENDING_REVIEW`, rejected, retired, and unregistered artwork fails closed
while localized instruction and safety text remain available.
`ApprovedInstructionAssetImage` renders a full-width 4:3 viewport, clips it to
the Material medium shape, and uses `ContentScale.Fit`.

Renderer inspection on 28 July 2026 found the current order:

- **Learn, safety-critical content with a warning:** step heading →
  instruction → critical warning → image → target/completion/supporting copy.
- **Learn, safety-critical content without separate warning text:** step
  heading → instruction → image → target/completion/supporting copy.
- **Learn, routine mandatory content:** step heading → instruction → image →
  any routine warning → target/completion/supporting copy.
- **Live Brew:** guidance heading and presentation status → all visible
  critical/routine instruction and warning content → image.

For this batch:

- The Switch bloom is safety-critical but has no separate canonical warning;
  its exact instruction is the required pre-image context.
- The official V60 pour, gravity-phin disc, and Cup-One wait are mandatory
  stages with routine warnings. In Learn, their image currently precedes the
  routine warning. Their prompts therefore depict the safe default directly:
  bed-focused pouring, no tamping pressure, and hands-off waiting. The bitmap
  must not rely on later warning text to correct an ambiguous action.
- The Rao nest and water-first Clever stage have no separate warning.
- In Live Brew, every available warning precedes the image.

Artwork must remain self-explanatory in the shared 4:3 frame at approximately
300–400 dp wide. It supplements the exact text and must not become the only
carrier of a quantity, temperature, timing cue, completion criterion, valve
state, or safety warning.

## Shared production contract

- Create one original opaque 1024 × 768 master per stage, exactly 4:3.
- Use a clean semi-flat, softly dimensional educational style with a restrained
  warm-neutral palette, clear silhouettes, subtle shadows, and an uncluttered
  neutral counter/background.
- Show one coherent action or completion state, not a before/after comparison.
  Prefer an elevated three-quarter or elevated near-top close view that exposes
  the governing mechanism.
- Keep critical geometry away from rounded-corner clipping: Switch
  lever/ball/outlet and shallow bloom, V60 bed/paper boundary and circular
  stream, Rao nest depth, phin support/disc relationship, Cup-One
  holder/outlet/mug, and Clever bottom actuator.
- Put no words, letters, numbers, measurement ticks, labels, logos, brand
  marks, UI, captions, arrows, callouts, warning icons, comparison panels, or
  decorative kitchen clutter in the bitmap.
- Keep dose, temperature, time, cumulative amount, and completion language in
  Compose text. A scale may establish stable placement only if its display is
  blank, turned away, or outside the frame.
- Use physical geometry and observable state—not color alone—to communicate
  closed versus actuated valves, retained versus draining water, shallow
  versus deep bed shape, light placement versus compression, bed-focused pour,
  and hands-off hot-part safety.
- Do not copy, trace, download, or imitate a photograph or illustration from
  any evidence page. Linked sources were used only to verify equipment and
  procedural facts.
- After later generation, optimize an opaque lossless or visually lossless
  WebP for `drawable-nodpi`, target no more than 300 KB, and verify exact
  dimensions, 4:3 ratio, mobile-size legibility, absence of embedded text, and
  canonical accessibility metadata before manifest review.
- Any later generated bitmap starts and remains `PENDING_REVIEW`. Generation,
  optimization, or file placement does not authorize registration.

## Current source re-verification

All links in this table were checked on 28 July 2026; automated-retrieval
failures are disclosed below. Current retrievable pages corroborate the
canonical evidence register; they do not replace the canonical records or
silently widen a recipe's supported hardware.

| Evidence ID | Current official, original, or canonical page | Facts reverified for this batch |
| --- | --- | --- |
| `SRC-KURASU-SWITCH` | Canonical Kurasu [Hario Switch recipe](https://kurasu.kyoto/blogs/recipe/hario-switch-recipe), current first-party HARIO Europe [Ole Kristian Bøen Switch recipe](https://www.hario-europe.com/blogs/hario-community/ole-kristian-boens-switch-recipe), and HARIO Europe [Switch product page](https://www.hario-europe.com/products/v60-immersion-dripper-switch) | The current first-party recipe specifies Switch 02, a closed dripper, 50 g bloom, 40-second retention, and 96 °C water. The product page corroborates the V60-paper, heatproof-glass, silicone-base, stainless-steel-ball, and button-release mechanism. |
| `SRC-HARIO-V60-OFFICIAL` | Hario UK, [Intermediate V60 Brew Guide](https://www.hario.co.uk/pages/brew-guides-v60-intermediate) | V60 02 and 02 paper, 15 g coffee, 92–96 °C water, a slow small-circle pour, and a cumulative scale reading of 250 g. |
| `SRC-HARIO-RAO-V60` | Hario UK and Scott Rao, [V60 recipe and interview](https://www.hario.co.uk/blogs/hario-ambassadors/hario-v60-recipe-interview-with-hario-ambassador-scott-rao) | Rao specifies a plastic V60, 20 g coffee, 330 ml water, and making a bird's-nest depression in the grounds. The canonical normalized completion cue bounds that depression as shallow rather than a deep crater. |
| `SRC-NGUYEN-PHIN` | Nguyen Coffee Supply, current [traditional phin guide](https://nguyencoffeesupply.com/blogs/vietnamese-coffee-brew-guide/traditional-vietnamese-drip-phin) and [phin anatomy guide](https://nguyencoffeesupply.com/blogs/news/what-is-the-vietnamese-phin-filter) | A gravity press is a loose insert that sits on top of level coffee without a latch or screw; the current brew guide says to drop the gravity press on the leveled bed. This is mechanically distinct from the screw-insert phin. |
| `SRC-CUPONE-MANUAL` | Technivorm Moccamaster, canonical [Cup-One user manual](https://www.moccamaster.eu/pub/media/handleidingen/talen/User_Manual_Cup-One.pdf), current [Cup-One support page](https://support.moccamaster.com/hc/en-us/articles/1500009438902-Cup-One), and current [Cup-One quick brew guide](https://support.moccamaster.com/hc/en-us/article_attachments/51888677928211) | The mug stays below the seated brew basket, the current guide shows the model-specific outlet arm and No. 1 paper, the cycle is approximately four minutes, and the outlet arm is hot during brewing. The current guide calls the outlet arm single-hole, while an older official quick guide called it nine-hole; the prompt follows the current guide without turning hole count into a reusable identity claim. |
| `SRC-CLEVER-HOFFMANN` | James Hoffmann, canonical [Ultimate Clever Dripper Technique](https://www.youtube.com/watch?v=RpOdennxP24), plus current manufacturer/distributor [Clever Dripper instructions](https://cleverbrewing.coffee/collections/clever-manual-brewers/products/clever-dripper) | The exact canonical technique is water-first. Current product instructions independently specify a folded flush wedge/#4 paper, hot-water rinse, water added to full weight before coffee, and a drain valve activated by placing the dripper on a cup or carafe. |

The Kurasu canonical URL and Hoffmann video were not reliably retrievable by
the automated browser during this pass. Their canonical records remain
unchanged; the exact Switch facts were independently matched on HARIO's
current first-party Ole Bøen page, and the Clever water-first/valve facts were
independently matched on the current product instructions.

## Batch order

| Rank | Recipe / source stage | Visual priority | Novice error prevented | Research disposition |
| ---: | --- | --- | --- | --- |
| 1 | `switch_ole_boen_hybrid_16_5_240` / `stage_01` | `safety-critical` | Opening the valve or draining the low first bloom instead of retaining it | Prompt-ready; not generated; `NONE-BOUNDED` |
| 2 | `v60_official_15_250` / `stage_04` | `mandatory` | Pouring down the paper wall, flooding the cone, or losing the intended gentle bed-focused path | Prompt-ready; not generated; `NONE-BOUNDED` |
| 3 | `v60_rao_20_330` / `stage_01` | `mandatory` | Digging a deep crater instead of a shallow central nest in a plastic V60 | Prompt-ready; not generated; `NONE-BOUNDED` |
| 4 | `phin_gravity_14_118` / `stage_02` | `mandatory` | Tamping or forcefully compressing a loose gravity disc and stalling flow | Prompt-ready; not generated; `NONE-PHIN-MECHANISM` |
| 5 | `auto_cupone_20_300` / `stage_05` | `mandatory` | Pulling the mug early or reaching toward a hot outlet while residual coffee still drips | Prompt-ready; not generated; `NONE-BOUNDED` |
| 6 | `clever_water_first_15_250` / `stage_02` | `mandatory` | Adding coffee first, actuating the valve, or letting the initial water drain | Prompt-ready; not generated; `NONE-BOUNDED` |

## 1. Switch retained first bloom

### Exact identity and source

- Recipe: `switch_ole_boen_hybrid_16_5_240` — “Ole Bøen Switch hybrid
  sequence”
- Method/profile: `steep_release` / `hario_switch_02`
- Filter configuration: one rinsed V60 02 paper; functioning lever and ball
- Source stage/order: `stage_01` / 1
- `StageId`: `p1_switch_ole_boen_hybrid_16_5_240_stage_01`
- `StageContentId`:
  `p1_switch_ole_boen_hybrid_16_5_240_stage_01_instruction`
- Proposed exact asset ID:
  `instruction_p1_switch_ole_boen_hybrid_16_5_240_stage_01_instruction_default`
- Canonical action/type: “Close the Switch and bloom with 50 g” / `BLOOM`
- Exact equipment state: “Valve closed”
- Completion mode/cue: `Countdown(40000 ms)` / “Bloom retained until 0:40”
- Exact typed references: brew start 0:00; stage duration 40 seconds; added and
  cumulative water 50 g; water temperature 96 °C
- Warning/severity: no separate warning text / normalized `CRITICAL`
- Visual priority: `safety-critical`
- Evidence: `SRC-KURASU-SWITCH`; current HARIO first-party recipe and product
  mechanism reverified 28 July 2026

### Renderer placement and visual goal

Learn renders the exact critical instruction before the image. Live Brew
renders all visible critical content before the image. There is no separate
warning string to duplicate in the bitmap.

The visual should show a single low-volume first-bloom state: the Switch is
closed, its ball is seated, the newly wetted coffee is swollen but remains a
shallow retained bloom, and nothing drains into the server. It must be
immediately distinguishable from batch 02's high-volume final retained pour.
The exact 50 g, 40 seconds, 0:00 start, and 96 °C remain text-only.

### Clean text-free generation prompt

> Create one original, clean, text-free 4:3 instructional illustration for a
> mobile coffee-brewing guide. Use a close elevated three-quarter view of an
> accurate unbranded Hario Switch 02-style brewer centered on a stable empty
> heat-safe glass server. Show its handleless ribbed glass V60 02 cone, exactly
> one correctly seated rinsed V60 02 paper, dark silicone base, real side
> lever, and stainless-steel ball valve. Depict the very first retained bloom:
> the lever is in its physically correct closed position, the steel ball is
> seated on the outlet, all coffee grounds are freshly wet and gently swollen,
> and the bloom remains shallow and low in the cone rather than becoming a
> high liquid-filled slurry. A gooseneck spout may be just withdrawing after
> the final small bloom pour, with no ongoing stream obscuring the bed. Show
> no drop or stream beneath the closed outlet and keep the server visibly
> empty. Make the closed lever, seated ball, evenly wet swollen bed, shallow
> retained bloom, and complete lack of drainage the only visual story. Use a
> warm-neutral uncluttered counter/background, semi-flat softly dimensional
> educational rendering, precise believable mechanism geometry, clear
> silhouettes, restrained materials, subtle shadows, and crop-safe
> mobile-readable framing. Include no text, letters, numbers, clock, timer,
> temperature display, scale display, measurement marks, labels, logos, brand
> marks, arrows, callouts, panels, or decorative objects.

### Strong negative constraints

- Do not show the lever open, button depressed, ball lifted, outlet released,
  dripping, a stream into the server, or coffee already collected below.
- Do not show a deep flooded slurry, a nearly full cone, a high retained final
  pour, dry islands, a deep crater, or a completely drained bed.
- Do not depict a Clever bottom actuator, standard non-Switch V60 base, glass
  handle, tap, clamp, or another release mechanism.
- Do not use wedge, Wave, basket, cloth, reusable metal, wrong-size, doubled,
  collapsed, or missing paper.
- Do not encode 50 g, 0:00, 40 seconds, or 96 °C with digits, a scale, clock,
  thermometer, dial, fill line, droplet count, or exact liquid height.
- Do not add stirring, agitation, serving, a hand touching hot glass or the
  lever, multiple brewers, text, logos, arrows, comparison panels, warning
  symbols, insets, or clutter.

### Accessibility and geometry review

- Canonical alt text: “Instructional view of close the switch and bloom with
  50 g using the exact brewer profile and filter configuration stated in this
  recipe; bloom retained until 0:40.”
- Invariants: Switch 02 geometry; one rinsed V60 02 paper; real lever/ball
  relationship; lever closed; ball seated; all grounds wet and gently swollen;
  shallow first bloom; no flow; empty stable server.
- Variant blocker: `NONE-BOUNDED`. This exact stage cannot reuse the batch-02
  hybrid final-pour image: the first bloom must have a much lower retained
  volume and no active circular final-pour cue.
- Reviewer focus: correct closed-lever direction, ball-to-seat contact,
  uniformly wet but low bloom, empty server, no drainage, plausible paper fit,
  and immediate distinction from every other Switch state at 300 dp.

## 2. Official V60 gentle small-circle final pour

### Exact identity and source

- Recipe: `v60_official_15_250` — “Hario V60 official intermediate baseline”
- Method/profile: `manual_gravity` / `hario_v60_02`
- Filter configuration: one folded, seated, rinsed V60 02 paper; rinse
  discarded
- Source stage/order: `stage_04` / 4
- `StageId`: `p1_v60_official_15_250_stage_04`
- `StageContentId`: `p1_v60_official_15_250_stage_04_instruction`
- Proposed exact asset ID:
  `instruction_p1_v60_official_15_250_stage_04_instruction_default`
- Canonical action/type: “Pour slowly in small circles to 250 g” / `POUR`
- Exact equipment state: “Continuous or near-continuous gentle pour”
- Completion mode/cue: `CumulativeAmount(250 g)` / “Scale reads 250 g”
- Exact typed references: cumulative water 250 g; water-temperature range
  92–96 °C
- Warning/severity: “Keep water primarily over the coffee bed, not directly
  down the paper wall” / `WARNING`
- Visual priority: `mandatory`
- Evidence: `SRC-HARIO-V60-OFFICIAL`; current official Hario UK guide
  reverified 28 July 2026

### Renderer placement and visual goal

In Learn, the exact instruction precedes the image and the routine wall-pour
warning currently follows it. In Live Brew, both instruction and warning
precede the image. The artwork must therefore make the safe path unambiguous
without depending on an arrow or later copy.

Show one low, gentle gooseneck stream landing on the center-to-mid coffee bed,
with a compact circular path implied by the wet surface and kettle position.
The stream must stay clearly inside the bed boundary and away from the exposed
paper wall. The numeric target and scale reading remain in text.

### Clean text-free generation prompt

> Create one original, clean, text-free 4:3 instructional illustration for a
> mobile coffee-brewing guide. Use an elevated near-top three-quarter close
> view of an accurate unbranded standard V60 02-style cone dripper with one
> correctly folded and seated V60 02 paper, centered on a stable clear
> heat-safe server and coffee scale. Show a calm slurry and a clearly visible
> dark coffee-bed boundary inside the pale paper. A hand safely holds a
> gooseneck kettle by its handle and produces one low, thin, steady vertical
> stream onto the center-to-mid region of the coffee bed. Imply a compact,
> controlled small-circle motion through the stream position and a gentle
> circular wetting pattern on the bed, while leaving a visible clean band of
> paper wall around it. Keep the stream entirely over coffee—not directly on
> the paper—and show no flooding or aggressive agitation. Turn the scale
> display away or leave it blank. Use a warm-neutral uncluttered counter and
> background, semi-flat softly dimensional educational rendering, precise
> believable V60 geometry, clear silhouettes, restrained materials, subtle
> shadows, and crop-safe mobile-readable framing. Include no text, letters,
> numbers, scale reading, temperature display, measurement marks, labels,
> logos, brand marks, arrows, spiral graphics, callouts, panels, or decorative
> objects.

### Strong negative constraints

- Do not let the water stream touch or run directly down the paper wall,
  outside the coffee-bed boundary, or around the extreme cone perimeter.
- Do not show a wide sweeping circle, high turbulent stream, splash, flood,
  overflow, dry grounds, violently spinning slurry, or multiple simultaneous
  streams.
- Do not use a Switch silicone base/lever/ball, Clever actuator, Wave, wedge,
  basket, cloth, reusable metal, wrong-size, doubled, buckled, or collapsed
  paper.
- Do not show an unstable server, tilted dripper, hand touching hot glass, or
  kettle spout buried in the slurry.
- Do not encode 250 g or 92–96 °C with a readable scale, fill line, digits,
  thermometer, dial, or exact liquid level.
- Do not add text, logos, arrows, drawn circular paths, multiple panels,
  magnified insets, warning symbols, or visual clutter.

### Accessibility and geometry review

- Canonical alt text: “Instructional view of pour slowly in small circles to
  250 g using the exact brewer profile and filter configuration stated in this
  recipe; scale reads 250 g.”
- Invariants: standard V60 02; one folded/seated/rinsed V60 02 paper; stable
  server and scale; low gentle stream; compact center-to-mid-bed circle; water
  stays off the paper wall; no flooding; blank or hidden scale display.
- Variant blocker: `NONE-BOUNDED`. This exact stage cannot reuse a generic
  pouring image whose stream position, paper boundary, V60 size, or final
  cumulative target is unspecified.
- Reviewer focus: visible bed/paper boundary, stream wholly over coffee,
  genuinely small circle, low gentle pour, stable setup, no graphic arrows,
  and safe-path readability before the Learn warning at 300 dp.

## 3. Scott Rao plastic-V60 shallow bed nest

### Exact identity and source

- Recipe: `v60_rao_20_330` — “Scott Rao V60 two-main-pour method”
- Method/profile: `manual_gravity` / `plastic_hario_v60_02`
- Filter configuration: one rinsed V60 02 paper
- Source stage/order: `stage_01` / 1
- `StageId`: `p1_v60_rao_20_330_stage_01`
- `StageContentId`: `p1_v60_rao_20_330_stage_01_instruction`
- Proposed exact asset ID:
  `instruction_p1_v60_rao_20_330_stage_01_instruction_default`
- Canonical action/type: “Rinse the paper and prepare a shallow nest in the
  coffee bed” / `PREPARE`
- Exact equipment state: “Plastic V60 02; bed level except central depression”
- Completion mode/cue: `Manual` / “Paper is seated and nest is shallow, not a
  deep crater”
- Exact typed references: none at this stage; the recipe-level 97 °C source
  temperature is not applied to this no-water preparation state
- Warning/severity: none / `NONE`
- Visual priority: `mandatory`
- Evidence: `SRC-HARIO-RAO-V60`; current original practitioner recipe on the
  Hario manufacturer site reverified 28 July 2026

### Renderer placement and visual goal

Learn renders the instruction before the image. Live Brew renders all visible
routine content before it.

The visual should prioritize the completion geometry: an unmistakably plastic
V60 02, properly seated rinsed paper, a broadly level dry coffee bed, and only
a shallow, smooth central depression. A near-top view makes depth readable
without a before/after comparison or an arrow. The source's “bird's nest”
language must not produce a deep funnel or exposed paper.

### Clean text-free generation prompt

> Create one original, clean, text-free 4:3 instructional illustration for a
> mobile coffee-brewing guide. Use a close elevated near-top three-quarter view
> of an accurate unbranded plastic V60 02-style cone dripper centered on a
> stable server. Make the dripper visibly lightweight molded plastic with
> standard spiral ribs, not ceramic, metal, or heavy glass. Show exactly one
> correctly sized rinsed V60 02 paper seated smoothly against the ribs, with
> its exposed upper edge carrying only a subtle wet sheen and no retained
> rinse water. Inside, show a dry, even coffee bed that is broadly level from
> edge to edge except for one wide, shallow, smooth central dimple. The dimple
> should read as a gentle nest with sloped shoulders and ample coffee still
> covering the cone point—not a hole, tunnel, or deep crater. Show no hand or
> tool obscuring the final shape. Make plastic material, correct paper fit,
> level outer bed, and shallow central depression the only visual story. Use a
> warm-neutral uncluttered counter/background, semi-flat softly dimensional
> educational rendering, precise believable V60 geometry, clear silhouettes,
> restrained materials, subtle shadows, and crop-safe mobile-readable framing.
> Include no text, letters, numbers, measurement marks, labels, logos, brand
> marks, arrows, callouts, panels, or decorative objects.

### Strong negative constraints

- Do not make a deep crater, narrow bore, funnel, tunnel, volcano rim, exposed
  paper at the bottom, sharply excavated hole, or multiple depressions.
- Do not leave the whole bed mounded, tilted, clumped, compressed, wet,
  flooded, or uneven outside the shallow center.
- Do not use a ceramic, glass, metal, Switch, NEO, Suiren, Wave, wedge, basket,
  or wrong-size dripper/filter.
- Do not show an active rinse, retained rinse water, kettle, slurry, bloom,
  pouring, spinning, stirring, or a finger pressing deeply into the bed.
- Do not encode 20 g, 330 ml, 97 °C, or brew time with digits, a scale,
  thermometer, fill marks, or exact pile size.
- Do not add text, logos, arrows, multiple panels, cutaways, contour lines,
  magnified insets, or visual clutter.

### Accessibility and geometry review

- Canonical alt text: “Instructional view of rinse the paper and prepare a
  shallow nest in the coffee bed using the exact brewer profile and filter
  configuration stated in this recipe; paper is seated and nest is shallow,
  not a deep crater.”
- Invariants: plastic V60 02; one rinsed seated V60 02 paper; no retained rinse
  water; dry bed; level outer bed; one broad shallow central dimple; coffee
  still covers the cone point; no active brew action.
- Variant blocker: `NONE-BOUNDED`. Never reuse a standard-material-agnostic V60
  image or a bloom crater; the plastic body and shallow pre-pour nest are exact
  identity.
- Reviewer focus: unmistakable plastic material, standard V60 02 proportions,
  paper/rib contact, visible shallow depth, level outer bed, covered cone
  point, and nest readability at 300 dp.

## 4. Gravity-phin loose disc resting level

### Exact identity and source

- Recipe: `phin_gravity_14_118` — “Gravity-insert phin baseline”
- Method/profile: `phin` /
  `single_serving_gravity_phin_with_loose_drop_in_press_disc`
- Filter configuration: integrated base plus one loose gravity press; metal
  filtration only
- Source stage/order: `stage_02` / 2
- `StageId`: `p1_phin_gravity_14_118_stage_02`
- `StageContentId`: `p1_phin_gravity_14_118_stage_02_instruction`
- Proposed exact asset ID:
  `instruction_p1_phin_gravity_14_118_stage_02_instruction_default`
- Canonical action/type: “Set the gravity press disc gently on the bed” /
  `PREPARE`
- Exact equipment state: “Loose insert resting level; no forceful compression”
- Completion mode/cue: `Manual` / “Insert lies flat”
- Exact typed references: none at this stage; recipe-level 91–93 °C is not
  applied to this dry preparation state
- Warning/severity: “Do not press hard enough to stall flow.” / `WARNING`
- Visual priority: `mandatory`
- Evidence: `SRC-NGUYEN-PHIN`; current regional expert/producer guidance
  reverified 28 July 2026

### Renderer placement and visual goal

In Learn, the instruction precedes the image and the routine no-force warning
currently follows it. In Live Brew, both precede the image. The bitmap must
therefore communicate a loose, flat rest state—not tamping—before the Learn
warning appears.

Continue the exact batch-02 gravity-phin setup geometry: broad supported cup,
level dry bed, metal-only chamber, and a loose unthreaded perforated disc. Show
the disc already lying flat with no hand applying pressure. The absence of a
central threaded post or screw mechanism is part of the instructional value.

### Clean text-free generation prompt

> Create one original, clean, text-free 4:3 instructional illustration for a
> mobile coffee-brewing guide. Use a close elevated three-quarter view of an
> accurate unbranded single-serving Vietnamese gravity phin centered securely
> on a broad stable heat-safe cup. Show a cylindrical metal chamber with a
> solid sidewall, integrated perforated bottom, and wide filter plate fully
> supported by the cup rim. Inside the chamber, show a level dry coffee bed
> with a loose unthreaded perforated gravity-press disc resting flat and
> lightly on its surface. Preserve a little visible headroom above the disc.
> If the disc has a small physically plausible lifting tab, keep it low and
> centered; show no threaded post, screw engagement, spring, plunger, or
> tamper. Leave all hands outside the frame so no downward force is implied.
> Make full cup support, level chamber, loose flat disc, uncompressed bed, and
> clear gravity-only mechanism the only visual story. Use a warm-neutral
> uncluttered counter/background, semi-flat softly dimensional educational
> rendering, precise believable metal geometry, clear silhouettes, restrained
> materials, subtle shadows, and crop-safe mobile-readable framing. Include no
> text, letters, numbers, measurement marks, labels, logos, brand marks,
> arrows, callouts, panels, or decorative objects.

### Strong negative constraints

- Do not show a hand, finger, tool, or weight pressing, tamping, twisting,
  tightening, squeezing, or forcefully compressing the disc or coffee.
- Do not add a central threaded post, screw-in insert, latch, spring, plunger,
  piston, espresso tamper, paper filter, moka-pot funnel, or pressure seal.
- Do not show the disc tilted, wedged, floating, buried, buckled, too small,
  too large, or separated far above the bed.
- Do not show a mounded, deeply compressed, wet, flooded, or spilled bed,
  water, kettle, bloom, steam, active drip, blocked sidewall, or overflow.
- Do not use a narrow or unstable cup, partial rim support, off-center phin,
  long unsupported drop, or phin resting directly on the counter.
- Do not encode 14 g, 118 g, or 91–93 °C with digits, a scale, thermometer,
  fill marks, or exact pile depth.
- Do not add text, logos, arrows, multiple panels, warning symbols, cutaways,
  magnified insets, or visual clutter.

### Accessibility and geometry review

- Canonical alt text: “Instructional view of set the gravity press disc gently
  on the bed using the exact brewer profile and filter configuration stated in
  this recipe; insert lies flat.”
- Invariants: single-serving gravity phin; integrated metal filter base; broad
  cup and full rim support; level dry bed; loose unthreaded perforated disc
  lying flat; visible headroom; no hand or applied pressure; no screw
  mechanism.
- Variant blocker: `NONE-PHIN-MECHANISM`. This is a loose gravity insert and
  must never reuse or visually drift toward the screw-phin stage-02 asset.
- Reviewer focus: unmistakably loose unthreaded disc, flat contact, zero
  compression cue, solid chamber sidewall, stable support, mechanism
  continuity with batch-02 gravity stage 01, and legibility at 300 dp.

## 5. Cup-One residual-drip wait after auto-off

### Exact identity and source

- Recipe: `auto_cupone_20_300` — “Podless automatic single-cup Cup-One
  procedure”
- Method/profile: `automatic_batch` /
  `technivorm_moccamaster_cup_one_with_1_paper_and_full_marked_reservoir`
- Filter configuration: model-specific holder with one No. 1 paper and a clear
  tiny outlet
- Source stage/order: `stage_05` / 5
- `StageId`: `p1_auto_cupone_20_300_stage_05`
- `StageContentId`: `p1_auto_cupone_20_300_stage_05_instruction`
- Proposed exact asset ID:
  `instruction_p1_auto_cupone_20_300_stage_05_instruction_default`
- Canonical action/type: “Wait for residual dripping, then remove and serve” /
  `OBSERVE`
- Exact equipment state: “Mug remains under holder”
- Completion mode/cue:
  `ObservedEvent(p1_obs_auto_cupone_20_300_stage_05)` / “No significant
  dripping remains”
- Source-only timing: starts after auto-off and continues until liquid flow
  into the mug has ended; no typed duration is invented
- Warning/severity: “Outlet pipe and coffee are hot.” / `WARNING`
- Visual priority: `mandatory`
- Evidence: `SRC-CUPONE-MANUAL`; canonical long manual and current official
  support/quick-guide material reverified 28 July 2026

### Renderer placement and visual goal

In Learn, the exact wait instruction precedes the image and the routine hot
outlet/coffee warning currently follows it. In Live Brew, both instruction and
warning precede the image. Because Learn shows the image first, the illustration
must depict patient hands-off waiting and must not suggest touching the mug,
holder, or outlet.

Show the post-transfer near-completion state: the machine is no longer actively
moving water, the mug remains centered below the installed holder, there is no
coffee stream, and at most one tiny residual bead clings beneath the outlet.
No hand begins removal. The observed completion cue stays in text and is
reinforced by the absence of significant flow.

### Clean text-free generation prompt

> Create one original, clean, text-free 4:3 instructional illustration for a
> mobile coffee-brewing guide. Show an accurate unbranded Moccamaster
> Cup-One-style single-cup brewer immediately after its automatic water
> transfer, viewed from an elevated front three-quarter angle. Keep the
> current model-specific outlet arm fully installed and centered over the
> correctly seated Cup-One brew basket/filter holder, with one No. 1 paper
> contained inside. Keep the reservoir lid in place and a broad heat-safe mug
> centered and stable beneath the holder. Show no active water transfer and no
> coffee stream; at most, include one tiny residual bead clinging beneath the
> holder outlet to communicate that the user is waiting for the last drip to
> cease. Keep any non-text power indicator dark and do not emphasize a rocker
> position. Show no hands anywhere near the machine or mug. Make the installed
> outlet/holder, centered waiting mug, lack of significant flow, and
> hands-off pause the only visual story. Use a warm-neutral uncluttered counter
> and background, semi-flat softly dimensional educational rendering, precise
> believable Cup-One geometry, clear silhouettes, restrained materials,
> subtle shadows, and crop-safe mobile-readable framing. Include no text,
> letters, numbers, timer, progress display, measurement marks, labels, logos,
> brand marks, arrows, callouts, panels, or decorative objects.

### Strong negative constraints

- Do not show a hand touching, lifting, pulling, tilting, or reaching for the
  mug, brew basket, holder, outlet arm, reservoir lid, power control, hot
  coffee, or underside.
- Do not show an active continuous stream, repeated falling droplets, water
  still transferring from the arm, overflow, splash, violent steam, grounds
  escaping, or coffee missing the mug.
- Do not omit, detach, misalign, or move the outlet arm, holder, No. 1 paper,
  reservoir lid, or mug.
- Do not show the machine actively on, a glowing power indicator, simultaneous
  on/off states, or an unplugged/disassembled cleaning setup.
- Do not show a carafe, pod, capsule, generic showerhead, batch basket, wrong
  paper size, narrow cup, unstable mug, second brewer, or hands serving.
- Do not encode four minutes, auto-off timing, temperature, or completion with
  digits, a clock, progress ring, thermometer, fill label, or exact beverage
  level.
- Do not add text, logos, arrows, multiple panels, warning symbols, insets, or
  visual clutter.

### Accessibility and geometry review

- Canonical alt text: “Instructional view of wait for residual dripping, then
  remove and serve using the exact brewer profile and filter configuration
  stated in this recipe; no significant dripping remains.”
- Invariants: Cup-One silhouette; current model-specific installed outlet arm;
  seated model-specific holder and one No. 1 paper; reservoir lid in place;
  broad centered mug remains under holder; no active transfer or stream; no
  hands; at most one clinging residual bead.
- Variant blocker: `NONE-BOUNDED`. This stage is exact to Cup-One after
  automatic transfer and cannot represent a pod brewer, carafe brewer, generic
  automatic brewer, active-cycle stage, or another outlet/basket arrangement.
- Reviewer focus: correct current Cup-One arm/holder/mug relationship, no
  significant dripping, no hot-part handling, no premature removal cue, dark
  inactive indicator, and safe-state readability before the Learn warning at
  300 dp.

## 6. Clever water-first retained fill

### Exact identity and source

- Recipe: `clever_water_first_15_250` — “Clever water-first low-clog baseline”
- Method/profile: `steep_release` /
  `clever_style_bottom_actuated_dripper`
- Filter configuration: one rinsed correct wedge paper; bottom valve closed
  off the server
- Source stage/order: `stage_02` / 2
- `StageId`: `p1_clever_water_first_15_250_stage_02`
- `StageContentId`: `p1_clever_water_first_15_250_stage_02_instruction`
- Proposed exact asset ID:
  `instruction_p1_clever_water_first_15_250_stage_02_instruction_default`
- Canonical action/type: “Add 250 g water before adding coffee” / `ADD_WATER`
- Exact equipment state: “Valve closed”
- Completion mode/cue: `CumulativeAmount(250 g)` / “Water is retained”
- Exact typed references: brew start 0:00; added and cumulative water 250 g;
  approximate water temperature 95–100 °C
- Warning/severity: none / `NONE`
- Visual priority: `mandatory`
- Evidence: `SRC-CLEVER-HOFFMANN`; canonical technique and current
  manufacturer/distributor instructions reverified 28 July 2026

### Renderer placement and visual goal

Learn renders the instruction before the image. Live Brew renders all visible
routine content before it.

The image must make the unusual sequence unmistakable: clear hot water enters
the correctly paper-lined Clever before any coffee grounds, the brewer remains
off a cup/server, its bottom actuator is unpressed, and water accumulates
without draining. The active pour can communicate ordering, while the visible
retained pool and dry space below the outlet communicate completion. Exact
mass, start time, and temperature remain text-only.

### Clean text-free generation prompt

> Create one original, clean, text-free 4:3 instructional illustration for a
> mobile coffee-brewing guide. Use a close elevated three-quarter view of an
> accurate unbranded translucent Clever-style bottom-actuated
> steep-and-release dripper standing level on its own outer support feet over
> a flat coffee scale. Show exactly one correctly folded wedge/#4-style paper
> seated flush against the sloped walls and already rinsed. The paper contains
> clear hot water only—absolutely no coffee grounds. A hand safely holds a
> gooseneck kettle by its handle and pours one calm stream into the paper-lined
> brewer while a clear pool visibly accumulates. Keep the brewer off every cup
> and carafe; show the recessed bottom actuator unpressed, the outlet closed,
> a dry air gap below it, and no drop or stream leaving the brewer. Turn the
> scale display away or leave it blank. Make water-before-coffee ordering,
> correct wedge paper, retained clear water, unpressed actuator, and complete
> absence of drainage the only visual story. Use a warm-neutral uncluttered
> counter/background, semi-flat softly dimensional educational rendering,
> precise believable Clever geometry, clear silhouettes, restrained
> materials, subtle shadows, and crop-safe mobile-readable framing. Include no
> text, letters, numbers, scale reading, timer, temperature display,
> measurement marks, labels, logos, brand marks, arrows, callouts, panels, or
> decorative objects.

### Strong negative constraints

- Do not include coffee grounds, brown slurry, bloom, coffee-colored liquid,
  spoon, stirring, crust, or any sign that coffee was added before the water.
- Do not place the brewer on, against, or partly touching a cup, mug, carafe,
  server, or stand that depresses the actuator.
- Do not show the actuator pressed, valve open, outlet open, a drip, stream,
  drainage into a vessel, empty dry paper during the pour, or water escaping.
- Do not depict a Hario Switch lever/ball, standard V60 cone, handleless glass
  cone, tap, manual drain control, or another valve mechanism.
- Do not use V60, Wave, basket, No. 1, cloth, reusable metal, wrong-size, torn,
  doubled, buckled, or floating paper.
- Do not encode 250 g, 0:00, or 95–100 °C with digits, a readable scale,
  thermometer, fill line, dial, or exact liquid height.
- Do not show hot-water contact with skin, a hand under the outlet, overflow,
  splashing, unstable support, text, logos, arrows, multiple panels, warning
  symbols, cutaways, or clutter.

### Accessibility and geometry review

- Canonical alt text: “Instructional view of add 250 g water before adding
  coffee using the exact brewer profile and filter configuration stated in
  this recipe; water is retained.”
- Invariants: translucent handled Clever-style body; one rinsed folded
  wedge paper; clear water only; no coffee; brewer off cup/server; outer feet
  supported; bottom actuator recessed and unpressed; valve closed; water
  retained; no drainage; blank/hidden scale display.
- Variant blocker: `NONE-BOUNDED`. This exact water-first stage cannot reuse
  the batch-01 release image or any coffee-first Clever scene; those show
  opposite valve or ingredient states.
- Reviewer focus: unmistakable absence of coffee, correct wedge-paper fit,
  plausible retained water, credible recessed actuator, no receiving vessel or
  flow, safe kettle grip, and water-first readability at 300 dp.

## Canonical evidence register for this batch

| Evidence ID | Canonical source-register title | Scope used here |
| --- | --- | --- |
| `SRC-KURASU-SWITCH` | Kurasu, “Hario Switch Recipe and Brewing Modes” | Ole Bøen hybrid identity, closed first bloom, exact bloom mass/time, and Switch mechanism |
| `SRC-HARIO-V60-OFFICIAL` | Hario UK, “Intermediate V60 Brew Guide” | Standard V60 02, small-circle pour, 250 g cumulative target, temperature range, and bed-focused path |
| `SRC-HARIO-RAO-V60` | Hario UK and Scott Rao, “Hario V60 Recipe & Interview with Hario Ambassador Scott Rao” | Plastic V60 identity and the shallow pre-pour central nest |
| `SRC-NGUYEN-PHIN` | Nguyen Coffee Supply, “How to Brew Vietnamese Coffee with a Phin” | Loose gravity-press identity, level bed, light placement, and non-screw mechanism |
| `SRC-CUPONE-MANUAL` | Technivorm Moccamaster, “Moccamaster Cup-One User Manual” | Cup-One component placement, automatic-cycle completion, residual-drip wait, and hot outlet/coffee warning |
| `SRC-CLEVER-HOFFMANN` | James Hoffmann, “The Ultimate Clever Dripper Technique” | Water-first ordering, retained initial fill, correct paper, and normally closed off-server valve |

## Canonical identity and collision gates

This queue is valid only while all of these remain true:

- All six recipe/source-stage pairs exist in the canonical JSON, exact-stage
  matrix, exact executable plan catalog, packaged exact-guidance JSON, and
  runtime exact-guidance catalog.
- The six `StageId` values, six `StageContentId` values, and six proposed asset
  IDs are internally unique and follow the deterministic exact-stage naming
  rule.
- Each selected matrix row remains `NOT_PRODUCED`, `P1-STAGE-CARD`,
  evidence-backed, and either `safety-critical` or `mandatory`.
- Each blocker remains `NONE-BOUNDED` or `NONE-PHIN-MECHANISM`; no selection
  silently crosses a generic-cone, V60-variant, wedge-variant, phin-mechanism,
  cezve-hardware, or automatic-batch-hardware blocker.
- None of the six proposed IDs collides with an existing
  `drawable-nodpi` filename, instruction-asset record, production-log entry,
  or asset ID reserved by batch 01, 02, or 03.
- The Switch first bloom remains visually distinct from the batch-02 retained
  final pour; closed-valve similarity alone is not reusable exact coverage.
- The Rao plastic-V60 nest and official V60 pour remain distinct from the
  batch-03 V60 paper-rinse completion.
- The gravity-disc frame remains mechanically continuous with batch-02
  gravity-phin stage 01 and mechanically incompatible with screw-phin artwork.
- The Cup-One prompt follows the current official outlet-arm geometry; a
  production reviewer must reject an obsolete or generic outlet/basket shape.
- The Clever frame shows clear water before coffee with the valve retained
  closed; it cannot be relabeled from a coffee-first or open-release scene.
- No prompt substitutes another paper size, machine, outlet, valve/actuator,
  phin retaining mechanism, brewer material, cup support, or normal/exceptional
  flow state.
- Quantities, time windows, temperature, completion timing, and model-gap
  language remain in text. Generated art must not invent readable values.
- Canonical alt text remains exactly as recorded above, including its original
  casing and phrasing, unless the canonical accessibility record changes and
  the queue is revalidated.
- No evidence-page image is copied, downloaded into the repository, traced, or
  used as a style reference.
- No bitmap is generated, registered, or described as approved by this
  documentation step. Any later produced asset remains `PENDING_REVIEW` until
  expert geometry, safety, accessibility, localization, and placement review
  all pass.
- Learn and Live Brew placement must be rechecked at asset-review time,
  especially the three mandatory stages whose routine warning follows the
  image in Learn.
