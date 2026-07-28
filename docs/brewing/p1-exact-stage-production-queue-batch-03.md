# P1 exact-stage illustration production queue — batch 03

Status: prompt-ready research documentation; no images generated, no assets
registered, and no manifest or app-code changes

Prepared: 28 July 2026

## Scope and source authority

This queue selects the next six highest-value exact-stage illustrations after
batches 01 and 02. It contains four `safety-critical` stages and two
`mandatory` stages whose geometry prevents common novice errors. Every
selection is canonically `NOT_PRODUCED`, has exact executable guidance, and has
no unresolved hard equipment blocker.

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
- batch-01 and batch-02 production queues

As of this research pass, all 12 exact-stage images from batches 01 and 02
exist as produced WebP files and have production-log records. They remain
**PENDING_REVIEW**, intentionally unregistered, and unapproved. Their file
presence is not release coverage and is not authority to reuse their visual
for a different exact stage.

## Selection boundary

The queue takes the four remaining unblocked safety-critical stages that add a
meaningfully different user-visible state:

- Cup-One active-cycle hands-off safety.
- Screw-phin stable support and dry-bed setup.
- Screw-phin normal slow-drip observation without unsafe intervention.
- Switch post-rinse closed valve and seated ball.

Three other unblocked safety-critical rows are deferred because their primary
mechanic is already represented in batches 01 or 02:

- `clever_coffee_first_15_250/stage_04` repeats the same bottom-actuated,
  stable-server release shown for the water-first Clever.
- `switch_ole_boen_hybrid_16_5_240/stage_01` repeats the closed-valve retained
  pour already queued for the hybrid final pour.
- `switch_ole_boen_hybrid_16_5_240/stage_04` repeats the open Switch release
  already queued for the official baseline.

Safety-critical cezve and generic automatic-batch rows remain excluded because
their canonical `BLOCK-CEZVE-HARDWARE` or `BLOCK-AUTO-BATCH-HARDWARE` variant
blocker is unresolved.

The remaining two slots go to high-frequency novice setup errors with distinct
mechanics: folding/seating/rinsing a standard V60 02 paper, and confirming a
Clever bottom valve is closed while the brewer is off its actuating server.

## Placement verification

Only an approved `InstructionAssetRecord` can render. Draft, queued,
`PENDING_REVIEW`, rejected, retired, or unregistered artwork fails closed while
localized instruction and safety text remain available.
`ApprovedInstructionAssetImage` renders a full-width 4:3 viewport, clips it to
the Material medium shape, and uses `ContentScale.Fit`.

Renderer inspection on 28 July 2026 found the current order:

- **Learn, safety-critical content with a warning:** step heading →
  instruction → safety warning → image → target/completion/supporting copy.
- **Learn, safety-critical content without separate warning text:** step
  heading → instruction → image → target/completion/supporting copy.
- **Learn, routine mandatory content:** step heading → instruction → image →
  any routine warning → target/completion/supporting copy.
- **Live Brew:** Guidance heading and presentation status → all visible
  critical/routine instruction and warning content → image.

This order satisfies the selected stages' placement contract: the instruction
always precedes the image, and every available safety-critical warning precedes
it. The Switch row is canonically critical but has no separate source warning;
its exact instruction therefore remains the required pre-image context.

Artwork must remain self-explanatory in the shared 4:3 frame at approximately
300–400 dp wide. It supplements the exact text and must not become the only
carrier of a quantity, timing cue, completion criterion, valve state, or
safety warning.

## Shared production contract

- Create one original opaque 1024 × 768 master per stage, exactly 4:3.
- Use a clean semi-flat, softly dimensional educational style with a restrained
  warm-neutral palette, clear silhouettes, subtle shadows, and an uncluttered
  neutral counter/background.
- Show one coherent action or completion state, not a before/after comparison.
  Prefer an elevated three-quarter close view that exposes the governing
  mechanism.
- Keep critical geometry away from rounded-corner clipping: Cup-One basket and
  mug, phin support rim and drip gap, Switch lever/ball/outlet, V60 paper seam
  and ribs, and Clever bottom actuator.
- Put no words, letters, numbers, measurement ticks, labels, logos, brand
  marks, UI, captions, arrows, callouts, warning icons, comparison panels, or
  decorative kitchen clutter in the bitmap.
- Keep dose, temperature, time, cumulative amount, and model-gap language in
  Compose text. A scale may establish stable placement only if its display is
  blank or outside the frame.
- Use physical geometry and observable state—not color alone—to communicate
  correct support, valve position, no-flow versus slow-drip state, filter
  seating, and hands-off hot-part safety.
- Do not copy, trace, download, or imitate a photograph or illustration from
  any evidence page. Linked sources were used only to verify equipment and
  procedural facts.
- After later generation, optimize an opaque lossless or visually lossless
  WebP for `drawable-nodpi`, target no more than 300 KB, and verify exact
  dimensions, 4:3 ratio, mobile-size legibility, absence of embedded text, and
  canonical accessibility metadata before manifest review.

## Current source re-verification

All links in this table were accessed on 28 July 2026.

| Evidence ID | Current official, original, or canonical page | Facts reverified for this batch |
| --- | --- | --- |
| `SRC-CUPONE-MANUAL` | Moccamaster, [Cup-One quick brew guide](https://support.moccamaster.com/hc/en-us/article_attachments/1500014620701), plus the canonical [Cup-One user manual](https://www.moccamaster.eu/pub/media/handleidingen/talen/User_Manual_Cup-One.pdf) | With the cup and brew basket positioned, the brewer is plugged into a grounded outlet and switched on; the cycle is approximately four minutes; the outlet arm is hot during brewing. |
| `SRC-HARIO-SWITCH` | Canonical Hario [Switch product page](https://global.hario.com/product/coffee/dripper/SSD.html) and current HARIO Europe [V60 Immersion Dripper Switch, 02/03 Size](https://www.hario-europe.com/products/v60-immersion-dripper-switch) | Switch 02 geometry, V60 paper, heatproof glass, silicone base, stainless-steel ball, and the relationship between a seated blocking ball and button-triggered release. The canonical global page and linked PDF currently reject automated retrieval, so the live HARIO Europe page is the current first-party corroboration. |
| `SRC-HARIO-V60-OFFICIAL` | Hario UK, [Intermediate V60 Brew Guide](https://www.hario.co.uk/pages/brew-guides-v60-intermediate) and [beginner V60 guide](https://www.hario.co.uk/pages/how-to-brew-coffee-with-hario-v60-coffee-dripper) | V60 02 and 02 paper; fold the paper seam, wet the paper with hot water, remove the rinse water, and preheat the serving vessel. |
| `SRC-CLEVER-HOFFMANN` | James Hoffmann, [The Ultimate Clever Dripper Technique](https://www.youtube.com/watch?v=RpOdennxP24), plus current manufacturer/distributor [Clever Dripper instructions](https://cleverbrewing.coffee/collections/clever-manual-brewers/products/clever-dripper) | The canonical video remains the exact water-first technique source. Current product instructions independently corroborate a folded flush wedge/#4 paper, hot-water rinse, discarded rinse, and a drain valve activated only by placement on a cup or carafe. |
| `SRC-TRUNGNGUYEN-PHIN` | Trung Nguyen Coffee UK/Dragon Coffee, [Vietnamese Coffee Brewing Information](https://trung-nguyen-coffee.co.uk/page_brewing.php) | A small metal phin sits on a cup; coffee is settled level; the retaining press is placed lightly; the brewer drips slowly and its parts become hot to touch. |
| `SRC-GOURMETKAVA-PHIN` | GourmetKava, [Vietnamese Coffee Preparation: Traditional Phin Method](https://www.gourmetkava.cz/en/blog/making-coffee/preparation-of--vietnamese-coffee) | A phin is gravity-driven rather than pressure-brewed; press or screw adjustment is gentle; grind and restriction affect stalling; normal flow is slow and dropwise. |
| `SRC-NGUYEN-PHIN` | Nguyen Coffee Supply, [Why Won't My Phin Filter Drip?](https://nguyencoffeesupply.com/blogs/news/why-wont-my-phin-filter-drip) and [traditional phin guide](https://nguyencoffeesupply.com/blogs/vietnamese-coffee-brew-guide/traditional-vietnamese-drip-phin) | First-drip and finish windows are diagnostic rather than reasons to force flow; clean clear perforations, level grounds, grind adjustment, and gentle gravity-press handling are preferred over added pressure. |

The current pages corroborate the canonical evidence register but do not
replace it. In particular, the exact 18 g screw-phin dose, its broad
approximately 5–8 minute source window, and the conditional tiny screw
adjustment remain canonical normalized guidance rather than a universal
manufacturer standard. Prompts therefore show the safe normal state—stable
support and undisturbed slow dripping—and leave exceptional troubleshooting in
text.

## Batch order

| Rank | Recipe / source stage | Visual priority | Novice error prevented | Generation disposition |
| ---: | --- | --- | --- | --- |
| 1 | `auto_cupone_20_300` / `stage_04` | `safety-critical` | Pulling the mug or basket from a live hot automatic cycle | Prompt-ready; `NONE-BOUNDED` |
| 2 | `phin_screw_18_120` / `stage_01` | `safety-critical` | Balancing a metal phin on a narrow cup or starting with an uneven bed | Prompt-ready; `NONE-PHIN-MECHANISM` |
| 3 | `phin_screw_18_120` / `stage_05` | `safety-critical` | Squeezing, forcefully adjusting, or touching a hot phin instead of observing normal slow drips | Prompt-ready; `NONE-PHIN-MECHANISM` |
| 4 | `switch_official_20_240` / `stage_01` | `safety-critical` | Beginning immersion with an open or still-dripping valve | Prompt-ready; `NONE-BOUNDED` |
| 5 | `v60_official_15_250` / `stage_01` | `mandatory` | Wrong-size, unfolded, buckled, or unrinsed V60 paper and retained rinse water | Prompt-ready; `NONE-BOUNDED` |
| 6 | `clever_water_first_15_250` / `stage_01` | `mandatory` | Leaving the bottom actuator open or placing the brewer on an actuating server during the closed setup | Prompt-ready; `NONE-BOUNDED` |

## 1. Cup-One hands-off automatic cycle

### Exact identity and source

- Recipe: `auto_cupone_20_300` — “Podless automatic single-cup Cup-One
  procedure”
- Method/profile: `automatic_batch` /
  `technivorm_moccamaster_cup_one_with_1_paper_and_full_marked_reservoir`
- Source stage/order: `stage_04` / 4
- `StageId`: `p1_auto_cupone_20_300_stage_04`
- `StageContentId`: `p1_auto_cupone_20_300_stage_04_instruction`
- Proposed exact asset:
  `instruction_p1_auto_cupone_20_300_stage_04_instruction_default`
- Canonical action/type: “Switch on and let the automatic cycle run” /
  `CUSTOM`
- Exact equipment state: “Machine transfers water automatically at
  approximately 92–96 °C”
- Completion mode/cue:
  `ObservedEvent(p1_obs_auto_cupone_20_300_stage_04)` / “Machine switches off
  after water transfer”
- Exact typed references: approximately four-minute stage duration;
  approximately 92–96 °C machine-controlled water
- Warning/severity: “Do not remove the brew basket or mug while brewing.” /
  `CRITICAL`
- Visual priority: `safety-critical`
- Evidence: `SRC-CUPONE-MANUAL`; current official quick guide and long manual
  reverified 28 July 2026

### Renderer placement and visual goal

Learn renders the instruction, then the critical warning, then the image. Live
Brew renders all visible instruction/safety content before the image. The
full-width `ContentScale.Fit` frame therefore reinforces a warning the user has
already encountered.

The visual should depict the safe in-progress state, not an attempt to show
both start and completion: the Cup-One is on, the outlet pipe and brew basket
are correctly installed, the broad mug remains centered, hot coffee transfers
without overflow, and no hand approaches any component. The approximately
four-minute duration, temperature range, and later automatic shutoff remain
text-only.

### Clean text-free generation prompt

> Create one original, clean, text-free 4:3 instructional illustration for a
> mobile coffee-brewing guide. Show an accurate unbranded Moccamaster
> Cup-One-style single-cup brewer during a normal active automatic cycle,
> viewed from an elevated front three-quarter angle. Keep the model-specific
> outlet pipe fully installed and centered over the correctly seated brew
> basket/filter holder, with one No. 1 paper and its coffee contained safely
> inside. Keep the reservoir lid in place and a broad heat-safe mug centered
> and stable beneath the holder. Show the power control in its physically
> credible on position with only a subtle non-text indicator glow, and show
> one calm coffee stream or a few aligned coffee drops entering the mug with no
> overflow. Leave generous empty space around the machine and show no hands at
> all: the only action is the machine completing its automatic transfer while
> every component stays seated. Use a warm-neutral uncluttered counter and
> background, semi-flat softly dimensional educational rendering, precise
> believable Cup-One geometry, clear silhouettes, restrained materials,
> subtle shadows, and crop-safe mobile-readable framing. Include no text,
> letters, numbers, time display, temperature display, measurement ticks,
> labels, logos, brand marks, arrows, callouts, panels, or decorative objects.

### Strong negative constraints

- Do not show any hand touching, lifting, pulling, tilting, or reaching for the
  mug, brew basket, holder, outlet pipe, reservoir lid, power switch, hot
  liquid, or underside.
- Do not omit, detach, misalign, or move the outlet pipe, holder, No. 1 paper,
  reservoir lid, or mug during the cycle.
- Do not show the power control off, an unplugged machine, an empty active
  reservoir, a stopped cycle, or simultaneous on/off states.
- Do not show a carafe, pod, capsule, generic showerhead, batch basket, wrong
  paper size, narrow cup, unstable mug, or second machine.
- Do not show overflow, splashing, violent steam, grounds escaping, a blocked
  outlet, coffee missing the mug, or a hand testing hot parts.
- Do not encode four minutes or 92–96 °C with digits, a clock, thermometer,
  dial, fill label, or exact liquid level.
- Do not add text, logos, arrows, multiple panels, warning symbols, insets, or
  visual clutter.

### Accessibility and geometry review

- Canonical alt text: “Instructional view of switch on and let the automatic
  cycle run using the exact brewer profile and filter configuration stated in
  this recipe; machine switches off after water transfer.”
- Invariants: Cup-One silhouette; installed centered outlet pipe; seated
  model-specific holder and one No. 1 paper; reservoir lid in place; broad
  centered mug; active automatic transfer; no overflow; no hands or component
  movement.
- Variant blocker: `NONE-BOUNDED`. This scene is exact to Cup-One and cannot
  represent a pod brewer, carafe brewer, generic automatic brewer, or machine
  with another outlet/basket interlock.
- Reviewer focus: Cup-One pipe/holder/mug relationship, credible active-cycle
  indicator, absence of hot-part handling, safe coffee path, component
  stability, and legibility at 300 dp.

## 2. Screw-phin stable support and level dry bed

### Exact identity and source

- Recipe: `phin_screw_18_120` — “Screw-insert phin controlled-drip profile”
- Method/profile: `phin` /
  `single_serving_screw_insert_phin_of_approximately_120_150_ml_chamber_capacity`
- Source stage/order: `stage_01` / 1
- `StageId`: `p1_phin_screw_18_120_stage_01`
- `StageContentId`: `p1_phin_screw_18_120_stage_01_instruction`
- Proposed exact asset:
  `instruction_p1_phin_screw_18_120_stage_01_instruction_default`
- Canonical action/type: “Stabilise the phin on the cup and add 18 g level
  coffee” / `ADD_COFFEE`
- Exact equipment state: “Clear perforations; stable support”
- Completion mode/cue: `Manual` / “Bed level”
- Exact typed reference: source-only coffee dose, exactly 18 g
- Warning/severity: “Hot metal and a narrow cup are a tipping hazard.” /
  `CRITICAL`
- Visual priority: `safety-critical`
- Evidence: `SRC-TRUNGNGUYEN-PHIN`, `SRC-GOURMETKAVA-PHIN`; both current pages
  reverified 28 July 2026

### Renderer placement and visual goal

Learn renders the instruction and tipping/hot-metal warning before the image.
Live Brew renders all visible instruction/safety content before the image.

The image must teach stable support and screw-phin identity rather than the
numeric dose. The integrated filter plate is fully supported by a broad
heat-safe cup; the dry bed is level around the central threaded post; the
matching screw insert remains separate for the next stage. This distinguishes
the setup from batch 02's loose-disc gravity phin.

### Clean text-free generation prompt

> Create one original, clean, text-free 4:3 instructional illustration for a
> mobile coffee-brewing guide. Use an elevated three-quarter close view of an
> accurate unbranded single-serving Vietnamese screw-insert phin with an
> approximately 120–150 ml cylindrical metal chamber, integrated perforated
> bottom, wide filter plate, and a real central threaded post. Center the phin
> securely on a broad stable heat-safe ceramic cup whose rim continuously and
> evenly supports the filter plate. Show a level dry coffee bed at a plausible
> low depth around the central post, with no grounds on the rim and ample
> headroom for later swelling. Place the matching perforated threaded screw
> insert flat and clearly separate on the metal lid beside the cup, ready for
> the next stage and visibly different from a loose gravity disc. Make the
> broad cup base, full support overlap, level chamber, level dry bed, clear
> central thread, and matching separate insert legible in one coherent view.
> Use a warm-neutral uncluttered counter/background, semi-flat softly
> dimensional educational rendering, precise believable metal geometry, clear
> silhouettes, restrained materials, subtle shadows, and crop-safe
> mobile-readable framing. Include no text, letters, numbers, readable scale,
> measurement marks, labels, logos, brand marks, arrows, callouts, panels, or
> decorative objects.

### Strong negative constraints

- Do not use a narrow, undersized, tilted, cracked, disposable, or unstable cup
  or leave any part of the phin filter plate unsupported.
- Do not show a loose gravity press, unthreaded disc, spring, plunger, tamper,
  paper filter, moka pot, espresso basket, or sealed pressure chamber.
- Do not install, tighten, or press the screw insert yet; keep it separate and
  show the central threaded post without force.
- Do not invent holes in the chamber sidewall; perforations belong to the flat
  internal bottom and matching insert.
- Do not show an uneven, mounded, compressed, wet, overflowing, or spilled
  coffee bed, blocked holes, water, kettle, steam, or active dripping.
- Do not encode 18 g with digits, a readable scale, scoop markings, or an exact
  pile size.
- Do not add text, logos, arrows, multiple panels, warning symbols, cutaways,
  or visual clutter.

### Accessibility and geometry review

- Canonical alt text: “Instructional view of stabilise the phin on the cup and
  add 18 g level coffee using the exact brewer profile and filter configuration
  stated in this recipe; bed level.”
- Invariants: screw-insert phin; approximately 120–150 ml proportions; wide
  filter plate fully supported by a broad heat-safe cup; clear central threaded
  post; level dry bed; separate matching threaded insert; clear perforation
  path; no heat or water yet.
- Variant blocker: `NONE-PHIN-MECHANISM`. The canonical mechanism is fixed as
  screw-insert. Never reuse the gravity-phin stage-01 image or the generic
  stable-cup candidate.
- Reviewer focus: credible central thread and insert match, continuous cup-rim
  support, safe proportions, level dry-bed depth, solid sidewall, no accidental
  loose-disc identity, and stability readability at 300 dp.

## 3. Screw-phin undisturbed slow-drip observation

### Exact identity and source

- Recipe: `phin_screw_18_120` — “Screw-insert phin controlled-drip profile”
- Method/profile: `phin` /
  `single_serving_screw_insert_phin_of_approximately_120_150_ml_chamber_capacity`
- Source stage/order: `stage_05` / 5
- `StageId`: `p1_phin_screw_18_120_stage_05`
- `StageContentId`: `p1_phin_screw_18_120_stage_05_instruction`
- Proposed exact asset:
  `instruction_p1_phin_screw_18_120_stage_05_instruction_default`
- Canonical action/type: “Monitor first drip and total window” / `OBSERVE`
- Exact equipment state: “Phin undisturbed unless clearly stalled”
- Completion mode/cue:
  `ObservedEvent(p1_obs_phin_screw_18_120_stage_05)` / “Steady slow drips
  complete”
- Source-only time references: first drip ideally within roughly 1–2 minutes;
  finish approximately 5–8 minutes. The clock basis remains an explicit model
  gap and is not coerced into a typed timestamp.
- Beverage-yield reference: user-measured
- Warning/severity: “If completely stalled, do not squeeze; only a very small
  safe loosening of the screw may be attempted while avoiding hot-metal
  contact, otherwise wait and redial next brew.” / `CRITICAL`
- Visual priority: `safety-critical`
- Evidence: `SRC-NGUYEN-PHIN`, `SRC-GOURMETKAVA-PHIN`; current guidance
  reverified 28 July 2026

### Renderer placement and visual goal

Learn renders the instruction and complete stalled-flow/hot-metal warning
before the image. Live Brew renders all visible instruction/safety content
before the image.

The image must show the safe normal case: a stable covered screw phin remains
untouched while separated slow drops enter the cup. It must not make the rare
conditional screw adjustment look like the default action. The source-only
timing windows, model gap, and troubleshooting decision stay in text.

### Clean text-free generation prompt

> Create one original, clean, text-free 4:3 instructional illustration for a
> mobile coffee-brewing guide. Show an accurate unbranded single-serving
> Vietnamese screw-insert phin of approximately 120–150 ml capacity midway
> through normal gravity drainage. Center its wide filter plate fully on a
> broad stable heat-safe glass or ceramic cup. Keep the metal lid correctly
> seated on the hot chamber and the lightly engaged threaded insert contained
> inside, with no loose-disc or pressure-brewer cues. Use a close elevated
> three-quarter view that clearly exposes the gap between the phin outlet and
> cup. Show only a short sequence of well-separated dark coffee droplets
> falling vertically into the cup—slow active dripping, neither a continuous
> stream nor a complete stall. Leave both hands entirely outside the frame so
> the hot phin is visibly undisturbed. Make stable support, hands-off patience,
> separated slow drops, and a clean spill-free cup the only visual story. Use
> a warm-neutral uncluttered counter/background, semi-flat softly dimensional
> educational rendering, precise believable metal geometry, clear
> silhouettes, restrained materials, subtle shadows, and crop-safe
> mobile-readable framing. Include no text, letters, numbers, clock, timer,
> measurement marks, labels, logos, brand marks, arrows, callouts, panels, or
> decorative objects.

### Strong negative constraints

- Do not show hands squeezing, pressing, twisting, loosening, lifting, shaking,
  tapping, tilting, or touching the hot chamber, lid, insert, filter plate, or
  underside.
- Do not show a continuous fast stream, spray, overflow, no-flow stall, sealed
  pressure, bulging lid, steam jet, or forced extraction.
- Do not depict a loose gravity disc, unthreaded insert, plunger, tamper, moka
  pot, espresso basket, paper filter, plastic dripper, or visible sidewall
  perforations.
- Do not use a narrow or unstable cup, off-center filter plate, long drop
  distance, spill path, or phin resting directly on the counter.
- Do not encode the first-drip or finish windows with clocks, digits, progress
  rings, droplet counts, labels, or an exact beverage level.
- Do not visualize the exceptional tiny screw loosening; normal undisturbed
  drainage is the selected single state.
- Do not add text, logos, arrows, multiple panels, warning symbols, cutaways,
  insets, or clutter.

### Accessibility and geometry review

- Canonical alt text: “Instructional view of monitor first drip and total
  window using the exact brewer profile and filter configuration stated in
  this recipe; steady slow drips complete.”
- Invariants: screw-insert phin; approximately 120–150 ml proportions; stable
  broad cup and full rim support; lid on; internal screw insert lightly
  engaged; separated slow vertical drips; no hands; no pressure or spill.
- Variant blocker: `NONE-PHIN-MECHANISM`. The hidden retaining mechanism must
  still be reviewed as screw-insert based on exact model proportions and
  continuity with the stage-01/02 assets; never relabel a gravity-phin drip
  scene as this stage.
- Reviewer focus: clearly slow but active drip cadence, exact screw-phin
  silhouette, stable support, no implied intervention, no hot-metal contact,
  no timing graphics, and legibility of separate droplets at 300 dp.

## 4. Switch post-rinse closed-valve state

### Exact identity and source

- Recipe: `switch_official_20_240` — “Hario Switch official full-immersion
  baseline”
- Method/profile: `steep_release` / `hario_switch_02`
- Source stage/order: `stage_01` / 1
- `StageId`: `p1_switch_official_20_240_stage_01`
- `StageContentId`: `p1_switch_official_20_240_stage_01_instruction`
- Proposed exact asset:
  `instruction_p1_switch_official_20_240_stage_01_instruction_default`
- Canonical action/type: “Insert and rinse the V60 02 paper, then close the
  Switch” / `RINSE`
- Exact equipment state: “Lever in closed position; ball seated”
- Completion mode/cue: `Manual` / “Rinse drained before closure and no new
  dripping occurs”
- Warning/severity: no separate source warning / normalized `CRITICAL` because
  the canonical visual priority is safety-critical
- Visual priority: `safety-critical`
- Evidence: `SRC-HARIO-SWITCH`; current HARIO first-party product page and
  linked V60-paper guidance reverified 28 July 2026

### Renderer placement and visual goal

There is no separate warning string. Learn therefore renders the exact
instruction before the image; Live Brew renders all visible critical content
before it.

The frame should show the final preparation state after rinse water has already
drained and been discarded: one clean wet V60 02 paper remains seated, the
Switch lever is now closed, the steel ball seals the outlet, the server is
empty, and neither retained rinse water nor a new drip is present. This is
mechanically opposite to batch 02's always-open Switch rinse.

### Clean text-free generation prompt

> Create one original, clean, text-free 4:3 instructional illustration for a
> mobile coffee-brewing guide. Use a close elevated three-quarter view of an
> accurate unbranded Hario Switch 02-style brewer centered over a stable empty
> heat-safe glass server. Show the handleless ribbed glass V60 02 cone, exactly
> one correctly seated clean V60 02 paper with a subtle wet sheen, dark
> silicone base, real side lever, and stainless-steel ball valve. Depict the
> completed post-rinse state: the rinse water has already drained and been
> discarded, the lever is in its physically correct closed position, and the
> steel ball sits firmly on the outlet seat. Keep the glass server visibly
> empty, with no retained pool above the ball and no droplet or stream below
> the outlet. Show no kettle and no hands; make the wet clean paper, seated
> ball, closed lever, empty server, and complete absence of new flow the only
> visual story. Use a warm-neutral uncluttered counter/background, semi-flat
> softly dimensional educational rendering, precise believable mechanism
> geometry, clear silhouettes, restrained materials, subtle shadows, and
> crop-safe mobile-readable framing. Include no text, letters, numbers,
> measurement marks, labels, logos, brand marks, arrows, callouts, panels, or
> decorative objects.

### Strong negative constraints

- Do not show the lever open, button depressed, steel ball lifted, outlet
  released, dripping, a stream, or liquid collecting in the server.
- Do not retain rinse water above the closed valve; the paper may look wet but
  no pool should remain.
- Do not add coffee grounds, coffee-colored liquid, slurry, bloom, kettle
  pour, stirring, serving, or a hand touching the lever or hot glass.
- Do not depict a Clever actuator, generic V60 base, glass handle, tap, clamp,
  bottom server actuator, or another release mechanism.
- Do not use wedge, Wave, basket, wrong-size cone, cloth, or reusable metal
  filtration.
- Do not omit or misalign the V60 02 paper, glass cone, silicone base, real
  lever, ball, outlet, or stable server.
- Do not add text, logos, arrows, multiple panels, cutaways, magnified insets,
  or clutter.

### Accessibility and geometry review

- Canonical alt text: “Instructional view of insert and rinse the v60 02 paper,
  then close the switch using the exact brewer profile and filter
  configuration stated in this recipe; rinse drained before closure and no new
  dripping occurs.”
- Invariants: Switch 02 geometry; one clean wet V60 02 paper; real
  lever/steel-ball relationship; lever closed; ball seated; no retained rinse
  pool; no new flow; empty stable server; no coffee.
- Variant blocker: `NONE-BOUNDED`. This is the official post-rinse closed state
  and cannot reuse the batch-01 open-release or batch-02 always-open rinse and
  retained-coffee images.
- Reviewer focus: credible closed lever direction, ball-to-seat contact,
  genuinely empty server, wet paper without retained water, no drip, correct
  V60 02 fit, and immediate distinction from every other Switch asset.

## 5. Standard V60 02 folded-paper rinse completion

### Exact identity and source

- Recipe: `v60_official_15_250` — “Hario V60 official intermediate baseline”
- Method/profile: `manual_gravity` / `hario_v60_02`
- Source stage/order: `stage_01` / 1
- `StageId`: `p1_v60_official_15_250_stage_01`
- `StageContentId`: `p1_v60_official_15_250_stage_01_instruction`
- Proposed exact asset:
  `instruction_p1_v60_official_15_250_stage_01_instruction_default`
- Canonical action/type: “Insert and rinse the V60 02 paper” / `RINSE`
- Exact equipment state: “Cone seam folded; paper seated against ribs; server
  empty after rinse”
- Completion mode/cue: `Manual` / “Paper is fully wet and server preheated”
- Warning/severity: none / `NONE`
- Visual priority: `mandatory`
- Evidence: `SRC-HARIO-V60-OFFICIAL`; current official Hario UK intermediate
  and beginner guides reverified 28 July 2026

### Renderer placement and visual goal

Learn renders the instruction before the image. Live Brew renders all visible
routine content before the image.

The image must make the standard V60 02 paper geometry, folded seam, contact
with spiral ribs, uniformly wet surface, and empty preheated server readable in
one completion-state frame. No active pour is needed; removing the kettle
prevents the frame from contradicting the already-discarded rinse water.

### Clean text-free generation prompt

> Create one original, clean, text-free 4:3 instructional illustration for a
> mobile coffee-brewing guide. Use a close elevated three-quarter view of an
> accurate unbranded standard V60 02-style transparent cone dripper centered
> on a stable clear heat-safe server. Show the standard spiral ribs and one
> correctly sized V60 02 paper whose crimped side seam has been folded neatly
> over itself before seating. The paper is uniformly wet with a subtle darker
> translucent sheen and lies smoothly against the cone ribs without buckles,
> gaps, or a collapsed point. Show the rinse already complete: the server
> below is visibly empty of rinse water but may have very light heat
> condensation that implies preheating. Show no kettle, no coffee, and no
> hands. Make the folded seam, correct cone fit, wet paper, rib contact, and
> empty stable server the only visual story. Use a warm-neutral uncluttered
> counter/background, semi-flat softly dimensional educational rendering,
> precise believable V60 geometry, clear silhouettes, restrained materials,
> subtle shadows, and crop-safe mobile-readable framing. Include no text,
> letters, numbers, measurement marks, labels, logos, brand marks, arrows,
> callouts, panels, or decorative objects.

### Strong negative constraints

- Do not show an unfolded, reversed, doubled, torn, buckled, floating,
  collapsed, oversized, undersized, or off-center paper.
- Do not use Wave, wedge, basket, No. 1, cloth, reusable metal, NEO, Suiren, or
  another non-standard filter/dripper geometry.
- Do not show a Switch silicone base, steel ball, lever, Clever actuator, or
  valve mechanism.
- Do not leave rinse water in the server, a pool in the paper, or active
  dripping; do not show an active kettle pour.
- Do not add coffee grounds, slurry, bloom, serving beverage, unstable support,
  overflow, splashing, steam plume, or a hand obscuring the seam.
- Do not add text, logos, arrows, multiple panels, magnified insets, cutaways,
  or visual clutter.

### Accessibility and geometry review

- Canonical alt text: “Instructional view of insert and rinse the v60 02 paper
  using the exact brewer profile and filter configuration stated in this
  recipe; paper is fully wet and server preheated.”
- Invariants: standard V60 02 cone and spiral ribs; one V60 02 paper; seam
  folded; paper fully seated and wet; no buckling or gaps; server preheated and
  empty after discarded rinse; no coffee.
- Variant blocker: `NONE-BOUNDED`. Never reuse this standard V60 02 image for
  Switch, Wave, wedge, generic cone, V60 NEO/Suiren, or a different paper size.
- Reviewer focus: standard V60 02 proportions, visible folded seam, paper/rib
  contact, uniformly wet paper, genuinely empty server, lack of active flow,
  and mobile-size seam legibility.

## 6. Clever closed bottom valve after paper rinse

### Exact identity and source

- Recipe: `clever_water_first_15_250` — “Clever water-first low-clog baseline”
- Method/profile: `steep_release` /
  `clever_style_bottom_actuated_dripper`
- Source stage/order: `stage_01` / 1
- `StageId`: `p1_clever_water_first_15_250_stage_01`
- `StageContentId`: `p1_clever_water_first_15_250_stage_01_instruction`
- Proposed exact asset:
  `instruction_p1_clever_water_first_15_250_stage_01_instruction_default`
- Canonical action/type: “Insert and rinse the wedge paper, then confirm the
  valve is closed” / `RINSE`
- Exact equipment state: “Clever off the server; bottom actuator not
  depressed”
- Completion mode/cue: `Manual` / “No dripping after rinse is discarded”
- Warning/severity: none / `NONE`
- Visual priority: `mandatory`
- Evidence: `SRC-CLEVER-HOFFMANN`; current Clever manufacturer/distributor
  product instructions additionally reverified 28 July 2026

### Renderer placement and visual goal

Learn renders the instruction before the image. Live Brew renders all visible
routine content before the image.

The image must show a completion state that the pending generic rinse asset
does not guarantee: the correct wedge paper is wet and seated, rinse water has
been discarded, the brewer is physically clear of any server, the bottom
actuator is unpressed, and the outlet produces no drip. The geometry should
make the normally closed off-server behavior obvious without arrows or a
wrong-versus-right panel.

### Clean text-free generation prompt

> Create one original, clean, text-free 4:3 instructional illustration for a
> mobile coffee-brewing guide. Use a close elevated three-quarter view of an
> accurate unbranded translucent Clever-style bottom-actuated
> steep-and-release dripper. Show exactly one correct folded wedge/#4-style
> paper seated flush against the sloped walls, uniformly wet after rinsing,
> with no coffee grounds. One calm hand holds only the cool brewer handle a
> short distance above a dry neutral counter, leaving the entire bottom
> mechanism unobstructed. Make the brewer visibly off every cup and server:
> show a clear air gap under the base, the bottom actuator fully unpressed in
> its normally closed state, the outlet shut, and absolutely no drop beneath
> it. The rinse water has already been discarded, so show no retained pool,
> receiving vessel, or active kettle. Make the wet seated wedge paper, clear
> off-server gap, unpressed actuator, and no-drip outlet the only visual story.
> Use a warm-neutral uncluttered counter/background, semi-flat softly
> dimensional educational rendering, precise believable Clever geometry,
> clear silhouettes, restrained materials, subtle shadows, and crop-safe
> mobile-readable framing. Include no text, letters, numbers, measurement
> marks, labels, logos, brand marks, arrows, callouts, panels, or decorative
> objects.

### Strong negative constraints

- Do not place the brewer on, against, or partly touching a cup, mug, carafe,
  server, stand, or surface that could depress the actuator.
- Do not show the bottom actuator pressed, valve open, outlet open, a drip,
  stream, retained rinse pool, active rinse pour, kettle, or receiving vessel.
- Do not depict a Hario Switch lever/ball, tap, generic valve, standard V60
  cone, glass handleless cone, or manual drain control.
- Do not use V60, Wave, basket, No. 1, cloth, reusable metal, wrong-size, torn,
  doubled, or buckled paper.
- Do not add coffee grounds, slurry, bloom, serving liquid, overflow, hot-part
  contact, second brewer, or a hand under the outlet.
- Do not add text, logos, arrows, multiple panels, warning symbols, cutaways,
  magnified insets, or clutter.

### Accessibility and geometry review

- Canonical alt text: “Instructional view of insert and rinse the wedge paper,
  then confirm the valve is closed using the exact brewer profile and filter
  configuration stated in this recipe; no dripping after rinse is discarded.”
- Invariants: Clever-style translucent handled body; one correct wet folded
  wedge paper; no coffee; rinse discarded; brewer off server; visible air gap;
  bottom actuator unpressed; valve closed; no retained water and no drip; hand
  on cool handle only.
- Variant blocker: `NONE-BOUNDED`. The exact stage adds off-server closed-valve
  completion beyond the generic pending rinse candidate. Never reuse the
  batch-01 release image, which shows the opposite actuated/open state.
- Reviewer focus: credible Clever bottom mechanism, unmistakable off-server
  clearance, unpressed actuator, no flow, correct wedge-paper seating,
  handle-only grip, and state readability at 300 dp.

## Canonical evidence register for this batch

| Evidence ID | Canonical source-register title | Scope used here |
| --- | --- | --- |
| `SRC-CUPONE-MANUAL` | Technivorm Moccamaster, “Moccamaster Cup-One User Manual” | Cup-One active automatic cycle, component placement, approximate duration, and hot-part safety |
| `SRC-HARIO-SWITCH` | Hario, “Immersion Dripper Switch Instructions” | Switch 02 paper, lever, silicone base, steel-ball valve, closed state, and release relationship |
| `SRC-HARIO-V60-OFFICIAL` | Hario UK, “Intermediate V60 Brew Guide” | Standard V60 02 and 02 paper, fold/seating, rinse, discarded water, and preheating |
| `SRC-CLEVER-HOFFMANN` | James Hoffmann, “The Ultimate Clever Dripper Technique” | Water-first Clever preparation, correct paper, rinse, and normally closed off-server state |
| `SRC-TRUNGNGUYEN-PHIN` | Trung Nguyen Coffee UK, “Vietnamese Coffee Brewing Information” | Cup-supported metal phin, level grounds, light retaining press, slow drainage, and hot-part handling |
| `SRC-GOURMETKAVA-PHIN` | GourmetKava, “Vietnamese Coffee Preparation: Traditional Phin Method” | Narrow screw-insert corroboration, gentle adjustment, gravity rather than pressure, and slow-drip behavior |
| `SRC-NGUYEN-PHIN` | Nguyen Coffee Supply, “How to Brew Vietnamese Coffee with a Phin” | First/last-drip diagnostic ranges, clear perforations, level grounds, and non-forceful flow troubleshooting |

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
  silently crosses a generic-cone, V60-variant, wedge-variant, cezve-hardware,
  or automatic-batch-hardware blocker.
- None of the six proposed IDs collides with an existing
  `drawable-nodpi` filename, instruction-asset record, production-log entry,
  or batch-01/batch-02 exact asset.
- The pending generic Clever rinse and phin stable-cup assets remain partial
  generic candidates and are not promoted, renamed, or treated as coverage
  for these exact composite stages.
- No prompt substitutes another paper size, machine, outlet, valve/actuator,
  phin retaining mechanism, cup support, or normal/exceptional flow state.
- Quantities, time windows, temperature, completion timing, and model-gap
  language remain in text. Generated art must not invent readable values.
- Canonical alt text remains exactly as recorded above, including its original
  casing and phrasing, unless the canonical accessibility record changes and
  the queue is revalidated.
- No evidence-page image is copied, downloaded into the repository, traced, or
  used as a style reference.
- No bitmap is generated, registered, or described as approved by this
  documentation step. Later produced assets remain `PENDING_REVIEW` until
  expert geometry, safety, accessibility, localization, and placement review
  all pass.
- Learn and Live Brew placement must be rechecked at asset-review time so
  instruction and available critical warning text still precede the image.
