# P1 exact-stage illustration production queue — batch 01

Status: all six candidate images generated and production-logged as
`PENDING_REVIEW`; no assets registered or approved, and no manifest or app-code
changes

Prepared: 28 July 2026
Production completed: 28 July 2026

## Scope and source authority

This batch selects six high-value, mechanically distinct visuals from the
20-recipe, 114-stage exact matrix in
`docs/brewing/p1-exact-stage-matrix.md`. Five are canonically
`safety-critical`; the Kalita Wave stage is `mandatory` because filter-pleat
geometry is difficult to communicate reliably with text alone.

Every selected row was canonically `NOT_PRODUCED` when queued. Each now has a
local candidate drawable and production-log record, but remains canonically
uncovered until expert review, localization, manifest registration, and approval
all pass.

Canonical source:

- File: `coffee_brewing_library_2026-07-27.json`
- SHA-256:
  `aa006a366297d659332986f8971b5442d77bf168eba30e520708742b3f76506d`
- Schema: `1.0.0`
- Verification date: `2026-07-27`

The batch excludes three legacy assets that were then `PENDING_REVIEW` and are
now retired:

- Clever filter rinse
  (`instruction_steep_and_release_clever_style_clever_style_insert_and_rinse_filter_default`)
- Hario Switch coffee addition
  (`instruction_steep_and_release_hario_switch_hario_switch_add_coffee_default`)
- Phin stable-cup setup
  (`instruction_restricted_flow_gravity_concentrate_vietnamese_phin_vietnamese_phin_place_on_stable_cup_default`)

The selected Clever, Switch, and phin stages teach different mechanisms:
bottom-actuated release, lever/ball release, and light threaded-insert
engagement respectively. Those generic assets never counted as coverage for an
exact recipe stage.

## Placement verification

Only an approved `InstructionAssetRecord` can render; queued or
`PENDING_REVIEW` art fails closed while localized text remains available.
`ApprovedInstructionAssetImage` renders a full-width 4:3 viewport, clips it to
the Material medium shape, and uses `ContentScale.Fit`.

Renderer inspection on 28 July 2026 found a placement-order mismatch that must
remain visible during production review:

- **Current Learn order:** step heading → image → instruction → warning →
  explanation/tip, inside a card with 16 dp padding and 12 dp vertical spacing.
  The list uses 20 dp horizontal screen padding.
- **Current Live Brew order:** Guidance heading → image → presentation-level
  controls/status → critical and routine guidance content, inside a card with
  16 dp padding and 12 dp vertical spacing.
- **Documented target order:** Learn image immediately after the stage
  instruction; Live image after instruction and safety copy.

Artwork should be composed for the common full-width 4:3 viewport and remain
readable at roughly 300–400 dp wide, but no asset in this queue should be
approved until product review resolves whether code or the documented target
order is authoritative. For safety-critical rows, the preferred target keeps
the warning before the image so the bitmap cannot visually outrank the safety
copy.

## Shared production contract

- Create one original opaque 1024 × 768 master, exactly 4:3.
- Use a clean semi-flat, softly dimensional educational style with a restrained
  warm-neutral palette, clear silhouettes, subtle shadows, and an uncluttered
  neutral counter/background.
- Keep the one critical action or state inside the full frame and clear of
  rounded-corner clipping.
- Put no words, letters, numbers, measurement marks, labels, logos, brand
  marks, UI, recipe cards, captions, arrows, comparison panels, or decorative
  kitchen clutter in the bitmap.
- Keep dose, water amount, temperature, timing, capacity, and warning language
  in Compose text. Do not encode them with ticks, labels, or implied exact fill
  quantities.
- Use geometry, hand position, valve state, liquid flow, and stable support—not
  color alone—to explain the action.
- After generation, optimize an opaque lossless or visually lossless WebP for
  `drawable-nodpi`, target no more than 300 KB, and verify exact dimensions,
  4:3 ratio, mobile-size legibility, absence of embedded text, and accessibility
  metadata before manifest review.

## Batch order

| Rank | Recipe / source stage | Visual priority | User error prevented | Generation disposition |
| ---: | --- | --- | --- | --- |
| 1 | `chemex_42_700` / `stage_01` | `safety-critical` | Sealing the carafe air channel with the filter | Prompt-ready; `NONE-BOUNDED` |
| 2 | `clever_water_first_15_250` / `stage_05` | `safety-critical` | Unstable or undersized release support | Prompt-ready; `NONE-BOUNDED` |
| 3 | `switch_official_20_240` / `stage_04` | `safety-critical` | Wrong valve state or touching hot parts | Prompt-ready; `NONE-BOUNDED` |
| 4 | `phin_screw_18_120` / `stage_02` | `safety-critical` | Overtightening a gravity-driven screw insert | Prompt-ready; `NONE-PHIN-MECHANISM` |
| 5 | `auto_cupone_20_300` / `stage_03` | `safety-critical` | Missing/misaligned outlet pipe or unstable mug | Prompt-ready; `NONE-BOUNDED` |
| 6 | `wave185_ozone_25_400` / `stage_01` | `mandatory` | Collapsing Wave 185 paper pleats during rinse | Prompt-ready; `NONE-BOUNDED` |

## 1. Chemex bonded-filter air channel

### Exact identity and source

- Recipe: `chemex_42_700`
- Method/profile: `manual_gravity` /
  `chemex_six_cup_carafe`
- Source stage/order: `stage_01` / 1
- `StageId`: `p1_chemex_42_700_stage_01`
- `StageContentId`: `p1_chemex_42_700_stage_01_instruction`
- Proposed asset:
  `instruction_p1_chemex_42_700_stage_01_instruction_default`
- Canonical action: Place the folded filter with three layers over the spout.
- Exact equipment state: Air channel remains open; one paper layer opposite.
- Completion cue: Filter cannot collapse into and seal the spout.
- Source warning: A sealed air channel can stall or burp hot coffee.
- Visual priority: `safety-critical`
- Evidence: `SRC-CHEMEX-FAQ` (official Chemex compatibility and brewing
  guidance; accessed 27 July 2026)

### Display and visual goal

Placement: in Learn, the current renderer puts the 4:3 image below the step
heading and above this instruction/warning; in Live Brew, it puts the image
below the Guidance heading and above controls/content. Acceptance target:
full-width `ContentScale.Fit` immediately after the instruction and, because
this stage is safety-critical, after its warning.

The image must make the asymmetric bonded-filter fold and open spout air
channel obvious at phone size. The viewer should understand the correct
orientation without a label, arrow, inset, or wrong-versus-right comparison.

### Clean text-free generation prompt

> Create one original, clean, text-free 4:3 instructional illustration for a
> mobile coffee-brewing guide. Use an elevated three-quarter close view of the
> upper half of an unbranded six-cup Chemex-style hourglass borosilicate
> carafe. Make the pouring spout and its air channel clearly visible. Show one
> correctly folded thick bonded paper filter seated in the carafe: the visibly
> thicker three-layer side lies over the spout, while one paper layer lies on
> the opposite side. Preserve a clear open passage between the folded paper
> and the spout so the filter cannot seal the air channel. A hand may gently
> steady the dry filter at the rim without hiding the fold or spout. Keep the
> carafe empty and stable and focus on this single setup state. Use a
> warm-neutral background and counter, semi-flat softly dimensional
> educational rendering, clear silhouettes, subtle shadows, and a crop-safe
> composition readable at mobile size. Include no text, numbers, labels,
> logos, brand marks, arrows, inset, or decorative objects.

### Negative constraints

- Do not place the single paper layer over the spout.
- Do not collapse paper into or seal the spout/air channel.
- Do not show a V60 cone, separate dripper, metal filter, basket paper, or
  generic thin cone paper.
- Do not add coffee, water, kettle, active pour, steam, serving action, or a
  second filter.
- Do not obscure the folded-layer asymmetry or imply that all sides have equal
  thickness.
- Do not show an unstable carafe, thermal shock, overflow, text, measurement
  graphics, multiple panels, or visual clutter.

### Accessibility and geometry review

- Alt text: “A folded bonded filter sits in a six-cup Chemex with its
  three-layer side over the spout and the air channel visibly open.”
- Invariants: six-cup hourglass carafe; correct bonded square/circle filter;
  three layers over spout; one layer opposite; open air channel; dry
  pre-brew state; stable base.
- Variant blocker: `NONE-BOUNDED` for this exact six-cup bonded-filter scope.
  Do not reuse for another Chemex size, unfolded paper, or metal cone without
  separate geometry review.
- Reviewer focus: folded-layer count, spout visibility, open air path, paper
  seating, and whether the state remains self-explanatory at 300 dp.

## 2. Clever bottom-actuated release

### Exact identity and source

- Recipe: `clever_water_first_15_250`
- Method/profile: `steep_release` /
  `clever_style_bottom_actuated_dripper`
- Source stage/order: `stage_05` / 5
- `StageId`: `p1_clever_water_first_15_250_stage_05`
- `StageContentId`:
  `p1_clever_water_first_15_250_stage_05_instruction`
- Proposed asset:
  `instruction_p1_clever_water_first_15_250_stage_05_instruction_default`
- Canonical action: Place the Clever on the server to release.
- Exact equipment state: Bottom actuator open.
- Completion cue: Flow begins immediately and completes around 3:30.
- Source warning: Server must be stable and large enough.
- Visual priority: `safety-critical`
- Evidence: `SRC-CLEVER-HOFFMANN` (original professional technique) and
  `SRC-CLEVER-COFFEECHRONICLER` (corroborating practitioner guide), both
  accessed 27 July 2026

### Display and visual goal

Placement: in Learn, the current renderer puts the 4:3 image below the step
heading and above this instruction/warning; in Live Brew, it puts the image
below the Guidance heading and above controls/content. Acceptance target:
full-width `ContentScale.Fit` after the instruction and stable-server warning.

The image must explain that setting the brewer squarely on a compatible server
opens the bottom actuator and starts gravity flow. It must not resemble a
hand-operated Switch lever or an abstract generic valve.

### Clean text-free generation prompt

> Create one original, clean, text-free 4:3 instructional illustration for a
> mobile coffee-brewing guide. Show an unbranded translucent Clever-style
> bottom-actuated steep-and-release dripper being placed vertically and
> squarely onto a broad, stable, heat-safe server. The brewer contains a
> correctly seated wet wedge paper and brewed coffee slurry, with generous
> headroom and no implied exact quantity. Make full, even server-rim support
> visibly depress the brewer's bottom actuator so the outlet is open and one
> steady coffee stream has just begun flowing into the server. One hand may
> hold the brewer by its cool handle while staying away from the hot underside;
> the other hand should not be needed. Frame the actuator contact, stable
> support, and vertical flow clearly in one coherent view. Use a warm-neutral
> uncluttered counter/background, semi-flat softly dimensional educational
> style, clear silhouettes, subtle shadows, and crop-safe mobile-readable
> composition. Include no text, numbers, labels, logos, arrows, panels, or
> decorative objects.

### Negative constraints

- Do not show the brewer suspended above, off-center on, or too wide for the
  server rim.
- Do not use an undersized cup, narrow glass, unstable vessel, tilted brewer,
  overflowing server, or hand touching the hot actuator.
- Do not show a lever, steel ball, manual tap, Switch base, or another
  valve-release mechanism.
- Do not use V60 cone paper; keep the correct seated wedge paper.
- Do not show a closed/no-flow state, extra kettle pour, stirring, serving, a
  numeric quantity, text, multiple panels, or clutter.

### Accessibility and geometry review

- Alt text: “A Clever-style dripper sits squarely on a wide stable server,
  opening its bottom actuator as coffee begins to drain.”
- Invariants: bottom-actuated Clever geometry; correct wedge paper; brewer
  supported evenly by a compatible server; actuator visibly open through
  contact; vertical flow; stable capacity/headroom; hand on cool handle only.
- Variant blocker: `NONE-BOUNDED` for the exact bottom-actuated recipe scope.
  The same physical actuation may be evaluated separately for
  `clever_coffee_first_15_250/stage_04` only if it receives that stage's
  distinct content/asset ID and independent review. Never reuse for Hario
  Switch or an unnamed valve-release brewer.
- Reviewer focus: real actuator/server contact, support overlap, wedge-paper
  fit, hot-part hand clearance, and immediate flow readability.

## 3. Hario Switch lever/ball release

### Exact identity and source

- Recipe: `switch_official_20_240`
- Method/profile: `steep_release` / `hario_switch_02`
- Source stage/order: `stage_04` / 4
- `StageId`: `p1_switch_official_20_240_stage_04`
- `StageContentId`: `p1_switch_official_20_240_stage_04_instruction`
- Proposed asset:
  `instruction_p1_switch_official_20_240_stage_04_instruction_default`
- Canonical action: Move the lever to open the valve.
- Exact equipment state: Valve open; brewer over server.
- Completion cue: Drawdown starts immediately.
- Source warning: Do not touch hot glass or metal.
- Visual priority: `safety-critical`
- Evidence: `SRC-HARIO-SWITCH` (official Hario Switch instructions; accessed
  27 July 2026)

### Display and visual goal

Placement: in Learn, the current renderer puts the 4:3 image below the step
heading and above this instruction/warning; in Live Brew, it puts the image
below the Guidance heading and above controls/content. Acceptance target:
full-width `ContentScale.Fit` after the instruction and hot-parts warning.

The image must distinguish the Switch 02 lever-and-steel-ball mechanism from a
Clever bottom actuator. The lever reaches its documented open state, the ball
releases the outlet, and drawdown begins without a hand touching hot glass or
metal.

### Clean text-free generation prompt

> Create one original, clean, text-free 4:3 instructional illustration for a
> mobile coffee-brewing guide. Use a close elevated three-quarter view of an
> unbranded Hario Switch 02-style brewer: a handleless ribbed glass V60 02 cone,
> correctly seated white V60 02 paper, intact dark silicone base, documented
> lever, and steel-ball valve, all centered over a stable heat-safe server.
> Show one fingertip contacting only the cool lever tab and moving it into the
> documented open position. Make the open mechanism physically coherent: the
> steel ball is lifted from the outlet and a single vertical coffee stream has
> just begun draining into the server. Keep the hand clearly away from hot
> glass, liquid, and metal. Focus on this one release action with the lever,
> ball/outlet state, and initial flow legible at mobile size. Use a
> warm-neutral uncluttered counter/background, semi-flat softly dimensional
> educational style, clear silhouettes, subtle shadows, and crop-safe 4:3
> framing. Include no text, numbers, labels, logos, arrows, panels, or
> decorative objects.

### Negative constraints

- Do not add a glass handle, Clever body, bottom server actuator, tap, or
  generic valve.
- Do not show the ball sealing the outlet, a closed lever state, or no flow.
- Do not omit or misalign the steel ball, silicone base, lever, V60 02 cone,
  or correctly seated 02 paper.
- Do not place fingers on hot glass, steel, liquid, or beneath the brewer.
- Do not show an unstable/undersized server, overflow, kettle pour, stirring,
  serving, text, measurement marks, multiple panels, or clutter.

### Accessibility and geometry review

- Alt text: “A fingertip moves the Hario Switch lever open while the steel-ball
  valve releases and coffee starts draining into a stable server.”
- Invariants: handleless ribbed glass V60 02 cone; V60 02 paper; intact
  silicone base; real lever/steel-ball relationship; documented open state;
  centered stable server; initial vertical drawdown; hand on cool lever only.
- Variant blocker: `NONE-BOUNDED` for the official Switch 02 recipe. Hybrid
  stages use the same hardware but different retained/percolating states and
  must receive separate stage IDs and review. Do not reuse for the
  always-open gravity recipe or Clever.
- Reviewer focus: actual lever direction, steel-ball lift, outlet/flow
  geometry, hot-part clearance, and exact Switch 02 proportions.

## 4. Screw-insert phin light engagement

### Exact identity and source

- Recipe: `phin_screw_18_120`
- Method/profile: `phin` /
  `single_serving_screw_insert_phin_of_approximately_120_150_ml_chamber_capacity`
- Source stage/order: `stage_02` / 2
- `StageId`: `p1_phin_screw_18_120_stage_02`
- `StageContentId`: `p1_phin_screw_18_120_stage_02_instruction`
- Proposed asset:
  `instruction_p1_phin_screw_18_120_stage_02_instruction_default`
- Canonical action: Engage the screw insert lightly.
- Exact equipment state: Insert level; threads engaged without hard
  compression.
- Completion cue: Disc rests on the bed but can still allow swelling.
- Source warning: Do not overtighten; the phin is gravity-driven and must not
  be converted into a sealed pressure vessel.
- Visual priority: `safety-critical`
- Evidence: `SRC-GOURMETKAVA-PHIN` (narrow screw-insert corroboration) and
  `SRC-TRUNGNGUYEN-PHIN` (regional producer/distributor instruction), both
  accessed 27 July 2026

### Display and visual goal

Placement: in Learn, the current renderer puts the 4:3 image below the step
heading and above this instruction/warning; in Live Brew, it puts the image
below the Guidance heading and above controls/content. Acceptance target:
full-width `ContentScale.Fit` after the instruction and overtightening warning.

The visual must communicate correct screw-insert identity, level alignment, and
light engagement through relaxed fingertips and uncompressed coffee geometry.
It must never imply tamping, pressure brewing, or interchangeability with a
loose gravity disc.

### Clean text-free generation prompt

> Create one original, clean, text-free 4:3 instructional illustration for a
> mobile coffee-brewing guide. Use an elevated close three-quarter view into
> an unbranded single-serving screw-insert Vietnamese phin of approximately
> 120–150 ml chamber capacity, centered on a broad stable heat-safe cup. Show a
> level bed of ground coffee above the integrated perforated base. Accurately
> depict the threaded central post and matching perforated screw insert aligned
> level inside the chamber. Relaxed fingertips lightly turn only the insert's
> cool upper control just enough for the threads to engage and for the disc to
> rest gently on the bed, leaving the bed visibly uncompressed and able to
> swell. Show no water or heat yet. Keep the threaded mechanism, level disc,
> gentle hand posture, chamber headroom, and stable support legible in one
> crop-safe mobile view. Use a warm-neutral uncluttered background/counter,
> semi-flat softly dimensional educational rendering, clear silhouettes, and
> subtle shadows. Include no text, numbers, labels, logos, arrows, panels, or
> decorative objects.

### Negative constraints

- Do not show a loose drop-in gravity disc, unthreaded insert, spring, plunger,
  tamper, paper filter, sealed pressure chamber, or espresso mechanism.
- Do not show forceful palm pressure, white knuckles, wrench/tool, hard
  compression, compacted puck, or the disc driven deeply into the bed.
- Do not add water, kettle, steam, lid sealing the chamber, drips, milk, ice,
  serving action, or a second phin.
- Do not show a narrow/unstable cup, off-center base, tilted chamber, impossible
  thread geometry, hot-metal contact, text, multiple panels, or clutter.

### Accessibility and geometry review

- Alt text: “Relaxed fingertips lightly engage the level threaded insert of a
  screw-insert phin without compressing the coffee bed.”
- Invariants: screw-insert mechanism, not gravity disc; integrated perforated
  base; matching threaded post/insert; level bed and disc; gentle contact;
  visible swelling headroom; stable heat-safe cup; dry pre-wet state.
- Variant blocker: `NONE-PHIN-MECHANISM`; the recipe fixes screw rather than
  gravity mechanics. Manufacturer styling may vary, so expert review must
  confirm credible 120–150 ml proportions and thread geometry. Never reuse for
  `phin_gravity_14_118`.
- Reviewer focus: thread engagement, no implied pressure seal, no bed
  compression, disc level, cup support, and distinction from the pending
  stable-cup asset.

## 5. Cup-One reservoir and outlet-pipe setup

### Exact identity and source

- Recipe: `auto_cupone_20_300`
- Method/profile: `automatic_batch` /
  `technivorm_moccamaster_cup_one_with_1_paper_and_full_marked_reservoir`
- Source stage/order: `stage_03` / 3
- `StageId`: `p1_auto_cupone_20_300_stage_03`
- `StageContentId`: `p1_auto_cupone_20_300_stage_03_instruction`
- Proposed asset:
  `instruction_p1_auto_cupone_20_300_stage_03_instruction_default`
- Canonical action: Fill to the marked level with fresh cold water and position
  outlet pipe.
- Exact equipment state: Reservoir within mark; pipe centred over holder.
- Completion cue: Mug and holder stable.
- Source warning: Do not operate without the outlet pipe or remove hot parts
  during a cycle.
- Visual priority: `safety-critical`
- Evidence: `SRC-CUPONE-MANUAL` (official Moccamaster Cup-One manual; accessed
  27 July 2026)

### Display and visual goal

Placement: in Learn, the current renderer puts the 4:3 image below the step
heading and above this instruction/warning; in Live Brew, it puts the image
below the Guidance heading and above controls/content. Acceptance target:
full-width `ContentScale.Fit` after the instruction and outlet-pipe/hot-parts
warning.

The image must make the model-specific pre-cycle arrangement understandable:
fresh cold water goes into the reservoir, the outlet pipe is installed and
centered over the #1 paper holder, and a sufficiently broad mug is stable
below. The image must not imply an active hot cycle.

### Clean text-free generation prompt

> Create one original, clean, text-free 4:3 instructional illustration for a
> mobile coffee-brewing guide. Show an accurate unbranded
> Moccamaster Cup-One-style single-cup brewer in a stable pre-cycle setup,
> viewed from an elevated front three-quarter angle. One hand pours fresh cold water from a plain
> pitcher into the open reservoir until the water meets the machine's subtle
> molded fill line, with no printed mark or number. The model-specific outlet
> pipe is already installed and centered over the correctly seated #1 paper in
> its filter holder, and a broad heat-safe mug sits centered and stable below.
> The machine is off: show no hot flow, steam, illuminated control, or moving
> parts. Frame the reservoir opening, water level, installed outlet pipe,
> paper-lined holder, and mug support clearly while keeping this a single setup
> action. Use a warm-neutral uncluttered background/counter, semi-flat softly
> dimensional educational rendering, clear silhouettes, subtle shadows, and
> crop-safe mobile-readable composition. Include no text, numbers, labels,
> logos, arrows, panels, or decorative objects.

### Negative constraints

- Do not omit, remove, tilt, or point the outlet pipe away from the filter
  holder.
- Do not pour water into the basket, paper, mug, or outlet pipe.
- Do not show an overfilled reservoir, printed measurement, active brew flow,
  steam, hot-part handling, removed basket, or cup being moved.
- Do not use a pod/capsule machine, carafe batch brewer, generic showerhead,
  #2/cone/basket paper, narrow cup, or unstable mug.
- Do not add grounds spilling on the rim, extra tools, text, numbers, logos,
  multiple panels, or clutter.

### Accessibility and geometry review

- Alt text: “Fresh cold water is poured to the Cup-One reservoir mark while
  the outlet pipe is centered over the #1 paper holder and a stable mug.”
- Invariants: Cup-One model silhouette; open reservoir; qualitative molded
  fill line without a printed value; fresh cold input; installed centered
  outlet pipe; model-specific holder; one #1 paper; stable mug of appropriate
  capacity; machine visibly off.
- Variant blocker: `NONE-BOUNDED` for this exact Cup-One configuration. It is
  not reusable for a generic automatic single-cup brewer, pod machine, another
  filter size, or a model without the same outlet-pipe geometry.
- Reviewer focus: actual Cup-One reservoir/pipe/holder relationship, #1 paper
  fit, machine-off state, mug stability, and absence of implied numeric fill
  information.

## 6. Kalita Wave 185 pleat-preserving rinse

### Exact identity and source

- Recipe: `wave185_ozone_25_400`
- Method/profile: `manual_gravity` / `kalita_wave_185`
- Source stage/order: `stage_01` / 1
- `StageId`: `p1_wave185_ozone_25_400_stage_01`
- `StageContentId`: `p1_wave185_ozone_25_400_stage_01_instruction`
- Proposed asset:
  `instruction_p1_wave185_ozone_25_400_stage_01_instruction_default`
- Canonical action: Rinse the Wave 185 paper without flattening its pleats.
- Exact equipment state: Correct 185 crimped filter; flat bottom centered.
- Completion cue: Paper is wet and pleats remain open.
- Source warning: none.
- Visual priority: `mandatory`
- Evidence: `SRC-KALITA-OZONE` (professional Kalita Wave 185 recipe; accessed
  27 July 2026)

### Display and visual goal

Placement: in Learn, the current renderer puts the 4:3 image below the step
heading and above this instruction; in Live Brew, it puts the image below the
Guidance heading and above controls/content. Acceptance target: full-width
`ContentScale.Fit` immediately after the instruction.

The image must let a novice recognize the flat-bottom Wave 185 filter, its
open crimped pleats, and a gentle rinse that wets rather than crushes them. This
is intentionally distinct from the pending Clever wedge-paper rinse.

### Clean text-free generation prompt

> Create one original, clean, text-free 4:3 instructional illustration for a
> mobile coffee-brewing guide. Use an elevated three-quarter close view of an
> unbranded Kalita Wave 185 flat-bottom dripper centered on a stable heat-safe
> server. Show the correct white Wave 185 crimped paper centered in the brewer,
> with its flat bottom level and every accordion pleat evenly open rather than
> pressed against the metal walls. A narrow gentle stream from a plain
> gooseneck kettle wets the inner paper evenly; show light drainage into the
> server while the pleats retain their shape. Keep hands and kettle clear of
> the paper and focus on this single rinse action. Use a warm-neutral
> uncluttered counter/background, semi-flat softly dimensional educational
> rendering, clear silhouettes, subtle shadows, and crop-safe mobile-readable
> framing. Include no text, numbers, labels, logos, arrows, panels, or
> decorative objects.

### Negative constraints

- Do not show V60 cone paper, wedge paper, generic basket paper, Wave 155
  geometry, metal reusable filter, or grounds.
- Do not flatten, fold over, crush, close, or glue the paper pleats against the
  dripper walls.
- Do not press the paper with fingers or the kettle spout.
- Do not show an off-center/tilted flat bottom, floating paper, unstable server,
  overflow, excessive spray, a second dripper, text, multiple panels, or
  clutter.

### Accessibility and geometry review

- Alt text: “A gentle kettle stream rinses a centered Wave 185 paper while its
  flat bottom stays level and the wet pleats remain open.”
- Invariants: Kalita Wave 185 geometry; correct 185 crimped paper; centered
  level flat bottom; open evenly spaced pleats; gentle rinse; drainage; stable
  server; no coffee.
- Variant blocker: `NONE-BOUNDED` for Wave 185. Never reuse for Wave 155,
  cone, wedge, basket, or metal filtration.
- Reviewer focus: 185 proportions, crimp count/shape plausibility, level paper
  bottom, open wet pleats, and visual distinction from Clever/V60 rinsing.

## Canonical evidence register for this batch

| Evidence ID | Canonical source-register title | Scope used here |
| --- | --- | --- |
| `SRC-CHEMEX-FAQ` | Chemex, “FAQ — Chemex Coffeemakers & Filters” | Bonded-filter orientation and spout/air-channel compatibility |
| `SRC-CLEVER-HOFFMANN` | James Hoffmann, “The Ultimate Clever Dripper Technique” | Water-first Clever method and release action |
| `SRC-CLEVER-COFFEECHRONICLER` | Coffee Chronicler, “Clever Dripper Recipe” | Corroboration of Clever profile/release mechanics |
| `SRC-HARIO-SWITCH` | Hario, “Immersion Dripper Switch Instructions” | Switch 02 lever/steel-ball valve and release |
| `SRC-GOURMETKAVA-PHIN` | GourmetKava, “Vietnamese Coffee Preparation: Traditional Phin Method” | Narrow corroboration of screw-insert behaviour |
| `SRC-TRUNGNGUYEN-PHIN` | Trung Nguyen Coffee UK, “Vietnamese Coffee Brewing Information” | Traditional phin retaining-press procedure |
| `SRC-CUPONE-MANUAL` | Technivorm Moccamaster, “Moccamaster Cup-One User Manual” | Cup-One reservoir, outlet pipe, #1 holder, and safe setup |
| `SRC-KALITA-OZONE` | Ozone Coffee, “Kalita Wave Brew Guide” | Wave 185 paper and rinse procedure |

## Validation and release gates

The batch is valid only while all of these remain true:

- Six recipe/source-stage pairs, `StageId` values, `StageContentId` values, and
  proposed asset IDs are unique and match the exact-stage matrix.
- Each selected stage was canonically `mandatory` or `safety-critical` when
  queued, has at least one evidence ID, and has no hard variant/equipment
  blocker.
- Each of the six local drawables uses its matching stable ID and has one
  production-log record. None collides with the three pre-existing generic
  `PENDING_REVIEW` IDs or a registered `InstructionAssetRecord`.
- No prompt substitutes a different filter, valve, actuator, brewer size,
  machine model, or phin mechanism.
- Cross-recipe reuse requires mechanics that truly match, a distinct exact
  content/asset ID, and an independent evidence and geometry review.
- All six generated bitmaps have a production-log record and validated local
  WebP payload. They remain intentionally unregistered and are not described as
  approved. Each stays `PENDING_REVIEW` until expert geometry, safety,
  accessibility, localization, and placement review all pass.
- The historical Learn/Live placement-order mismatch recorded above was
  subsequently resolved. The final order must still be reverified on both
  surfaces at asset-review time before any candidate is registered or approved.
