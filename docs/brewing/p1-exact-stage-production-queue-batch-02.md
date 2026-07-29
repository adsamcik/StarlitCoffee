# P1 exact-stage illustration production queue — batch 02

Status: all six candidate images generated and production-logged as
`PENDING_REVIEW`; no assets registered or approved, and no manifest or app-code
changes

Prepared: 28 July 2026
Production completed: 28 July 2026

## Scope and source authority

This queue is the next coherent six-illustration batch after
`p1-exact-stage-production-queue-batch-01.md`. All six stages were canonically
`safety-critical` and `NOT_PRODUCED` when queued, have exact executable
guidance, and have no unresolved equipment-variant blocker. Each now has a local
candidate drawable and production-log record, but remains canonically uncovered
until expert review, localization, manifest registration, and approval all
pass. The batch deliberately covers six different user-visible mechanics:

1. Cup-One filter-holder outlet inspection and paper seating.
2. Cup-One dry, unplugged outlet cleaning.
3. Switch hybrid valve closure and retained final pour.
4. Switch always-open paper rinse and free drainage.
5. Gravity-insert phin support and level dry-bed setup.
6. Safe removal and resting of a hot gravity-insert phin.

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
- current exact-stage drawables in `drawable-nodpi`

The six produced, pending-review batch-01 exact-stage drawables are excluded:

- `instruction_p1_chemex_42_700_stage_01_instruction_default`
- `instruction_p1_clever_water_first_15_250_stage_05_instruction_default`
- `instruction_p1_switch_official_20_240_stage_04_instruction_default`
- `instruction_p1_phin_screw_18_120_stage_02_instruction_default`
- `instruction_p1_auto_cupone_20_300_stage_03_instruction_default`
- `instruction_p1_wave185_ozone_25_400_stage_01_instruction_default`

The pending generic phin stable-cup candidate
`instruction_restricted_flow_gravity_concentrate_vietnamese_phin_vietnamese_phin_place_on_stable_cup_default`
is not exact coverage for the composite gravity-phin stage selected here.

## Placement verification

Only an approved `InstructionAssetRecord` can render. Queued or
`PENDING_REVIEW` artwork fails closed while localized instruction and safety
text remain available. `ApprovedInstructionAssetImage` uses a full-width 4:3
viewport, the Material medium clip shape, and `ContentScale.Fit`.

Renderer inspection on 28 July 2026 established the current order:

- **Learn:** step heading → instruction → image → in-card warning →
  explanation/tip.
- **Live Brew:** Guidance heading and presentation status → visible
  instruction/safety content → image.
- **Safety-critical approval target:** instruction and warning before image in
  both surfaces.

Live Brew currently satisfies the safety-critical target. Learn still places
the warning after the image. That Learn order is an explicit release blocker
for the six safety-critical assets in this queue; the production wiring change
must be committed and reverified before any of them is approved. This document
records the current implementation rather than assuming that pending change
has landed.

Artwork must remain self-explanatory in the shared 4:3 frame at approximately
300–400 dp wide. It supplements the exact text; it must not become the only
carrier of a quantity, timing cue, completion criterion, or safety warning.

## Shared production contract

- Create one original opaque 1024 × 768 master per stage, exactly 4:3.
- Use a clean semi-flat, softly dimensional educational style with a restrained
  warm-neutral palette, clear silhouettes, subtle shadows, and an uncluttered
  neutral counter/background.
- Show one action or final state only. Use a close elevated three-quarter view
  unless a different view is needed to make the mechanism physically legible.
- Preserve breathing room around critical geometry so rounded clipping cannot
  remove an outlet, valve, cup rim, hot-part hand clearance, or resting
  surface.
- Put no words, letters, numbers, measurement ticks, labels, logos, brand
  marks, UI, captions, arrows, callouts, comparison panels, or decorative
  kitchen clutter in the bitmap.
- Keep exact dose, water amount, cumulative amount, temperature, and timing in
  Compose text. A scale may establish stable placement, but its display must
  be blank or out of view.
- Explain state through real geometry: seated paper, an unobstructed hole, an
  unplugged cord, lever/ball relationship, retained versus draining liquid,
  full rim support, insulated hand position, and a heat-safe resting surface.
- Do not copy, trace, or imitate any photograph or illustration from a source
  page. The linked sources below were used only to verify equipment and
  procedure facts.
- After generation, optimize an opaque lossless or visually lossless WebP for
  `drawable-nodpi`, target no more than 300 KB, and verify exact dimensions,
  4:3 ratio, mobile-size legibility, absence of embedded text, and canonical
  accessibility metadata before manifest review.

## Current primary-source re-verification

All links in this table were accessed on 28 July 2026.

| Evidence ID | Current official or primary page | Facts reverified for this batch |
| --- | --- | --- |
| `SRC-CUPONE-MANUAL` | Moccamaster, [Cup-One quick brew guide](https://support.moccamaster.com/hc/en-us/article_attachments/1500014620701), plus the canonical [Cup-One user manual](https://www.moccamaster.eu/pub/media/handleidingen/talen/User_Manual_Cup-One.pdf) | Power off before setup; one No. 1 paper in the brew basket; regular mild-detergent cleaning; the supplied-style tool passes through the drip hole to prevent overflow. |
| `SRC-HARIO-SWITCH` | HARIO Europe, [V60 Immersion Dripper Switch, 02/03 Size](https://www.hario-europe.com/products/v60-immersion-dripper-switch) | The Switch 02 uses V60 paper, a silicone base, a stainless-steel ball that blocks flow, and a switch/button that releases flow. |
| `SRC-KURASU-SWITCH` | HARIO Europe, [Ole Kristian Bøen's Switch Recipe](https://www.hario-europe.com/blogs/hario-community/ole-kristian-boens-switch-recipe) | Independent current primary confirmation of the canonical sequence: Switch 02, valve closed again around 1:30, then a circular 90 g final pour before later release. |
| `SRC-HARIO-V60-OFFICIAL` | Hario UK, [Intermediate V60 Brew Guide](https://www.hario.co.uk/pages/brew-guides-v60-intermediate) | V60 02 dripper and 02 paper; wet the whole paper and let rinse water drain before adding coffee. |
| `SRC-NGUYEN-PHIN` | Nguyen Coffee Supply, [Traditional Vietnamese drip phin guide](https://nguyencoffeesupply.com/blogs/vietnamese-coffee-brew-guide/traditional-vietnamese-drip-phin) and [phin construction guide](https://nguyencoffeesupply.com/blogs/news/what-is-the-vietnamese-phin-filter) | Put the filter plate and chamber on a glass, add 14 g, level the bed, and use a loose gravity press; the lid can serve as a coaster after brewing; black, iced, milk, and condensed-milk service are supported. |

The current links corroborate the canonical register; they do not replace its
recipe identity or evidence IDs. In particular, the canonical
`SRC-KURASU-SWITCH` attribution remains intact even though HARIO's current
first-party recipe page is the stronger live verification for the exact Ole
Bøen sequence.

## Batch order

| Rank | Recipe / source stage | Visual priority | User error prevented | Generation disposition |
| ---: | --- | --- | --- | --- |
| 1 | `auto_cupone_20_300` / `stage_01` | `safety-critical` | A blocked tiny outlet causing overflow and scalding | Prompt-ready; `NONE-BOUNDED` |
| 2 | `auto_cupone_20_300` / `stage_06` | `safety-critical` | Cleaning a powered or submerged brewer, or leaving the outlet blocked | Prompt-ready; `NONE-BOUNDED` |
| 3 | `switch_ole_boen_hybrid_16_5_240` / `stage_03` | `safety-critical` | Leaving the valve open and losing the retained final immersion phase | Prompt-ready; `NONE-BOUNDED` |
| 4 | `switch_gravity_15_250` / `stage_01` | `safety-critical` | Rinsing with the valve closed and retaining hot rinse water | Prompt-ready; `NONE-BOUNDED` |
| 5 | `phin_gravity_14_118` / `stage_01` | `safety-critical` | Using an undersized cup or leaving the dry bed unstable/uneven | Prompt-ready; `NONE-PHIN-MECHANISM` |
| 6 | `phin_gravity_14_118` / `stage_07` | `safety-critical` | Bare-hand contact with hot metal or setting the phin on an unsafe surface | Prompt-ready; `NONE-PHIN-MECHANISM` |

## 1. Cup-One outlet inspection and No. 1 paper

### Exact identity and source

- Recipe: `auto_cupone_20_300` — “Podless automatic single-cup Cup-One
  procedure”
- Method/profile: `automatic_batch` /
  `technivorm_moccamaster_cup_one_with_1_paper_and_full_marked_reservoir`
- Source stage/order: `stage_01` / 1
- `StageId`: `p1_auto_cupone_20_300_stage_01`
- `StageContentId`: `p1_auto_cupone_20_300_stage_01_instruction`
- Proposed exact asset:
  `instruction_p1_auto_cupone_20_300_stage_01_instruction_default`
- Canonical action/type: “Clean the filter holder outlet and insert one #1
  paper” / `PREPARE`
- Exact equipment state: “Power off; tiny bottom hole visibly clear”
- Completion mode/cue: `Manual` / “Paper seated and outlet unobstructed”
- Warning/severity: “A blocked outlet can overflow and cause scalding.” /
  `CRITICAL`
- Visual priority: `safety-critical`
- Evidence: `SRC-CUPONE-MANUAL`; current official Moccamaster quick guide and
  long manual reverified 28 July 2026

### Renderer placement and visual goal

Current Learn placement is after the instruction but before the scalding
warning. Current Live placement is after visible instruction and safety
content. Approval placement is after both instruction and warning in both
surfaces.

The frame must make two facts immediately legible: the model-specific holder's
tiny bottom drip hole is genuinely clear, and exactly one correctly sized No.
1 paper is seated in the holder while the machine is off. A final-state image
is preferred to a tool-in-use image because the completion criterion is
unobstructed seating, not a particular cleaning gesture.

### Clean text-free generation prompt

> Create one original, clean, text-free 4:3 instructional illustration for a
> mobile coffee-brewing guide. Use a close elevated three-quarter view of the
> detached brew basket/filter holder from an accurate unbranded
> Moccamaster Cup-One-style single-cup brewer. Show exactly one correctly
> shaped No. 1 cone paper fully opened and seated smoothly inside the dry
> holder. Angle the supported holder just enough that its model-specific tiny
> bottom drip hole is clearly visible and completely unobstructed, while the
> paper seating remains easy to read. Keep the switched-off brewer in the soft
> background with no illuminated control, hot liquid, or steam. Show one calm
> hand supporting only the cool holder without covering the paper rim or
> outlet. Make the seated paper and clear tiny hole the only visual story. Use
> a warm-neutral uncluttered counter/background, semi-flat softly dimensional
> educational rendering, clear silhouettes, subtle shadows, and crop-safe
> mobile-readable framing. Include no text, letters, numbers, labels, logos,
> brand marks, arrows, callouts, panels, or decorative objects.

### Strong negative constraints

- Do not block, fill, cover, omit, enlarge, or invent multiple bottom holes.
- Do not show grounds, water, rinse flow, active brewing, steam, overflow, or a
  glowing/on power control.
- Do not show a No. 2 or No. 4 cone, basket paper, wedge paper, capsule, pod,
  reusable metal filter, doubled paper, folded paper, or torn paper.
- Do not place paper outside the holder, leave it collapsed, or hide the
  outlet with the hand.
- Do not show a generic batch-brewer basket, showerhead, carafe, sink,
  cleaning bath, second holder, cutaway panel, magnified inset, or exploded
  view.
- Do not add text, symbols, measurements, logos, arrows, multiple panels, or
  visual clutter.

### Accessibility and geometry review

- Canonical alt text: “Instructional view of clean the filter holder outlet
  and insert one #1 paper using the exact brewer profile and filter
  configuration stated in this recipe; paper seated and outlet unobstructed.”
- Invariants: Cup-One holder geometry; one No. 1 paper; dry holder; power-off
  context; paper fully seated; one model-specific tiny drip hole visibly open;
  no grounds or water.
- Variant blocker: `NONE-BOUNDED`. This is exact to the Cup-One holder and No.
  1 paper; never reuse it for a generic automatic brewer, pod machine, other
  basket, or another filter size.
- Reviewer focus: real holder/outlet proportions, unambiguous single clear
  hole, correct paper size and seating, machine-off state, hand clearance, and
  whether both completion facts survive a 300 dp rendering.

## 2. Cup-One unplugged dry outlet cleaning

### Exact identity and source

- Recipe: `auto_cupone_20_300` — “Podless automatic single-cup Cup-One
  procedure”
- Method/profile: `automatic_batch` /
  `technivorm_moccamaster_cup_one_with_1_paper_and_full_marked_reservoir`
- Source stage/order: `stage_06` / 6
- `StageId`: `p1_auto_cupone_20_300_stage_06`
- `StageContentId`: `p1_auto_cupone_20_300_stage_06_instruction`
- Proposed exact asset:
  `instruction_p1_auto_cupone_20_300_stage_06_instruction_default`
- Canonical action/type: “Discard grounds and brush the outlet” / `CLEAN_UP`
- Exact equipment state: “Machine unplugged for cleaning”
- Completion mode/cue: `Manual` / “No grounds remain and outlet is clear”
- Warning/severity: “Do not submerge the brewer.” / `CRITICAL`
- Visual priority: `safety-critical`
- Evidence: `SRC-CUPONE-MANUAL`; current official Moccamaster quick guide and
  long manual reverified 28 July 2026

### Renderer placement and visual goal

Current Learn placement is after the instruction but before the no-submersion
warning. Current Live placement is after visible instruction and safety
content. Approval placement is after both instruction and warning in both
surfaces.

The illustration must communicate dry maintenance rather than washing the
machine: grounds and paper are already discarded, the cooled holder is
detached, the narrow cleaning tool passes through the tiny outlet, and the
machine's loose plug is visibly away from power and water.

### Clean text-free generation prompt

> Create one original, clean, text-free 4:3 instructional illustration for a
> mobile coffee-brewing guide. Show an accurate unbranded Moccamaster
> Cup-One-style single-cup brewer switched off on a dry counter, with its power
> plug clearly disconnected and resting loose beside the machine. In the
> foreground, show the cooled detached brew basket/filter holder completely
> empty of paper and grounds. One hand supports the cool holder while the other
> gently passes a slim, plain supplied-style cleaning tool through the
> model-specific tiny bottom drip hole so the outlet is visibly clear. Keep the
> holder, tool path, disconnected plug, and entirely dry cleaning context
> legible in one coherent elevated three-quarter view. Use a warm-neutral
> uncluttered counter/background, semi-flat softly dimensional educational
> rendering, clear silhouettes, subtle shadows, and crop-safe mobile-readable
> framing. Include no text, letters, numbers, labels, logos, brand marks,
> arrows, callouts, panels, sink, basin, or decorative objects.

### Strong negative constraints

- Do not show the plug in an outlet, illuminated power, active brewing, steam,
  a hot holder, or fingers near live electrical parts.
- Do not place the brewer or any electrical body in a sink, basin, bucket, or
  water; do not show rinsing, spraying, immersion, dripping water, wet
  counters, or detergent foam.
- Do not leave paper, coffee grounds, residue, or a blocked outlet in the
  completion state.
- Do not use a large bottle brush, knife, drill, pin forced into another
  opening, or a tool touching electrical components.
- Do not show a generic batch-brewer basket, capsule machine, outlet arm
  cleaning, carafe, second machine, before/after panels, or exploded view.
- Do not add text, symbols, measurements, logos, arrows, multiple panels, or
  visual clutter.

### Accessibility and geometry review

- Canonical alt text: “Instructional view of discard grounds and brush the
  outlet using the exact brewer profile and filter configuration stated in
  this recipe; no grounds remain and outlet is clear.”
- Invariants: Cup-One silhouette and detached holder; cooled dry state;
  grounds and paper removed; unplugged cord visibly disconnected; slim
  cleaning tool through the tiny drip hole; no water or submersion.
- Variant blocker: `NONE-BOUNDED`. The action is exact to the Cup-One
  holder/outlet and must not be generalized to another automatic brewer or to
  cleaning its electrical body.
- Reviewer focus: unmistakably disconnected plug, correct outlet/tool
  relationship, dry environment, no residual grounds, no implied immersion,
  and clear distinction from stage 01's paper-seating final state.

## 3. Switch hybrid retained final pour

### Exact identity and source

- Recipe: `switch_ole_boen_hybrid_16_5_240` — “Ole Bøen Switch hybrid
  sequence”
- Method/profile: `steep_release` / `hario_switch_02`
- Source stage/order: `stage_03` / 3
- `StageId`: `p1_switch_ole_boen_hybrid_16_5_240_stage_03`
- `StageContentId`:
  `p1_switch_ole_boen_hybrid_16_5_240_stage_03_instruction`
- Proposed exact asset:
  `instruction_p1_switch_ole_boen_hybrid_16_5_240_stage_03_instruction_default`
- Canonical action/type: “Close the valve and pour 90 g to 240 g cumulative” /
  `POUR`
- Exact equipment state: “Valve closed”
- Completion mode/cue: `CumulativeAmount(240 g)` / “Scale reads 240 g and
  final phase is retained”
- Exact typed references: approximately 1:30 at stage start; 90 g added; 240 g
  cumulative; 96 °C
- Warning/severity: no separate source warning / normalized `CRITICAL` because
  the canonical visual priority is safety-critical
- Visual priority: `safety-critical`
- Evidence: `SRC-KURASU-SWITCH`; exact sequence independently reverified on
  HARIO Europe's official Ole Kristian Bøen recipe on 28 July 2026

### Renderer placement and visual goal

Current Learn placement is after the instruction; there is no separate source
warning to precede it. Current Live placement is after visible instruction and
critical content. Approval placement is after the instruction and any critical
copy in both surfaces.

This frame must teach the retained phase, not the quantities: the Switch 02
valve is physically closed, its steel ball seals the outlet, a circular pour
continues into the slurry, and no liquid drains into the server. The exact
90 g, 240 g, 96 °C, and approximately 1:30 references remain text-only.

### Clean text-free generation prompt

> Create one original, clean, text-free 4:3 instructional illustration for a
> mobile coffee-brewing guide. Use a close elevated three-quarter view of an
> accurate unbranded Hario Switch 02-style brewer centered over a stable
> heat-safe server on a coffee scale whose display is blank or out of frame.
> Show the handleless ribbed glass V60 02 cone, correctly seated V60 02 paper,
> dark silicone base, real lever, and stainless-steel ball valve. Put the lever
> in its physically correct closed position and show the steel ball seated over
> the outlet. A plain gooseneck kettle pours one controlled circular stream
> into the coffee slurry while liquid is visibly retained above the closed
> valve; show no stream or drips below the brewer. Keep one safe kettle hand
> away from hot glass and metal. Make the closed mechanism, retained liquid,
> circular pour, and absence of drawdown the only visual story. Use a
> warm-neutral uncluttered counter/background, semi-flat softly dimensional
> educational rendering, clear silhouettes, subtle shadows, and crop-safe
> mobile-readable framing. Include no text, letters, numbers, scale reading,
> measurement marks, labels, logos, brand marks, arrows, callouts, panels, or
> decorative objects.

### Strong negative constraints

- Do not show the lever open, the steel ball lifted, an open outlet, coffee
  draining, a stream below, or an empty retained chamber.
- Do not depict a Clever bottom actuator, tap, clamp, glass handle, generic
  V60-only base, or another immersion brewer.
- Do not omit or misalign the Switch lever, steel ball, silicone base, V60 02
  cone, or correctly seated 02 paper.
- Do not encode 90 g, 240 g, 96 °C, or time with text, digits, ticks, a visible
  scale display, a thermometer, or an exact fill line.
- Do not touch hot glass, metal, slurry, or the underside; do not show
  overflow, splashing, unstable support, stirring, serving, or release.
- Do not add text, symbols, logos, arrows, multiple panels, cutaways, insets,
  or visual clutter.

### Accessibility and geometry review

- Canonical alt text: “Instructional view of close the valve and pour 90 g to
  240 g cumulative using the exact brewer profile and filter configuration
  stated in this recipe; scale reads 240 g and final phase is retained.”
- Invariants: Switch 02 geometry; V60 02 paper; real lever/steel-ball
  relationship; valve visibly closed; ball seated; controlled circular pour;
  liquid retained; no drawdown; stable server and scale; safe hand clearance.
- Variant blocker: `NONE-BOUNDED`. The hardware is known, but this retained
  hybrid state must not reuse the batch-01 open-release image or the always-open
  gravity-rinse image below.
- Reviewer focus: credible closed lever direction, seated ball and sealed
  outlet, unmistakable retained liquid/no flow, circular pour, exact Switch 02
  proportions, and absence of visualized numeric quantities.

## 4. Switch always-open paper rinse

### Exact identity and source

- Recipe: `switch_gravity_15_250` — “Switch conventional gravity mode”
- Method/profile: `steep_release` / `hario_switch_02_used_open`
- Source stage/order: `stage_01` / 1
- `StageId`: `p1_switch_gravity_15_250_stage_01`
- `StageContentId`: `p1_switch_gravity_15_250_stage_01_instruction`
- Proposed exact asset:
  `instruction_p1_switch_gravity_15_250_stage_01_instruction_default`
- Canonical action/type: “Open the Switch valve and rinse the V60 02 paper” /
  `RINSE`
- Exact equipment state: “Lever open throughout”
- Completion mode/cue: `Manual` / “Rinse drains freely”
- Warning/severity: no separate source warning / normalized `CRITICAL` because
  the canonical visual priority is safety-critical
- Visual priority: `safety-critical`
- Evidence: `SRC-HARIO-SWITCH`, `SRC-HARIO-V60-OFFICIAL`; current official
  HARIO Europe product and Hario UK V60 guide reverified 28 July 2026

### Renderer placement and visual goal

Current Learn placement is after the instruction; there is no separate source
warning to precede it. Current Live placement is after visible instruction and
critical content. Approval placement is after the instruction and any critical
copy in both surfaces.

The image must show that a Switch can operate as a conventional gravity
dripper: its actual lever/ball valve stays open while a clean V60 02 paper is
rinsed, and rinse water drains freely. It must be visually opposite to the
retained hybrid stage above without using a comparison layout.

### Clean text-free generation prompt

> Create one original, clean, text-free 4:3 instructional illustration for a
> mobile coffee-brewing guide. Use a close elevated three-quarter view of an
> accurate unbranded Hario Switch 02-style brewer centered over a stable
> heat-safe waste server. Show the handleless ribbed glass V60 02 cone, one
> correctly seated clean V60 02 paper, dark silicone base, real lever, and
> stainless-steel ball valve. Keep the lever in its physically correct open
> position throughout and show the ball lifted clear of the outlet. A plain
> gooseneck kettle sends one gentle rinse stream around the empty paper while
> clear rinse water drains freely in one visible vertical stream into the
> server, with no retained pool. Keep the kettle hand away from hot glass and
> metal. Make open-valve geometry, wet paper, and free drainage the only visual
> story. Use a warm-neutral uncluttered counter/background, semi-flat softly
> dimensional educational rendering, clear silhouettes, subtle shadows, and
> crop-safe mobile-readable framing. Include no text, letters, numbers,
> measurement marks, labels, logos, brand marks, arrows, callouts, panels, or
> decorative objects.

### Strong negative constraints

- Do not show a closed lever, seated/blocking ball, retained rinse pool, no
  flow, coffee-colored liquid, coffee grounds, slurry, bloom, or brewing dose.
- Do not depict a Clever bottom actuator, tap, generic V60-only base, glass
  handle, paperless cone, or another release mechanism.
- Do not omit or misalign the Switch lever, steel ball, silicone base, V60 02
  cone, or correctly seated 02 paper.
- Do not use wedge, flat-bottom Wave, basket, No. 1, No. 2, reusable metal, or
  cloth filtration.
- Do not show a hand moving the lever during the rinse, touching hot glass or
  metal, an unstable server, overflow, splashing, or serving.
- Do not add text, numbers, logos, arrows, multiple panels, cutaways, insets,
  or visual clutter.

### Accessibility and geometry review

- Canonical alt text: “Instructional view of open the switch valve and rinse
  the v60 02 paper using the exact brewer profile and filter configuration
  stated in this recipe; rinse drains freely.”
- Invariants: Switch 02 geometry; one clean V60 02 paper; real lever/steel-ball
  relationship; valve held open; ball lifted; gentle clear-water rinse; free
  vertical drainage; no grounds; no retained pool; stable server.
- Variant blocker: `NONE-BOUNDED`. The image is exact to Switch 02 used
  continuously open; never reuse it for a standard V60, closed immersion
  rinse, Clever, hybrid retained phase, or active coffee brew.
- Reviewer focus: correct open lever direction and ball lift, genuine free
  drainage, clean paper with no coffee, V60 02 fit, hot-part clearance, and
  immediate distinction from the retained hybrid asset.

## 5. Gravity-insert phin stable dry-bed setup

### Exact identity and source

- Recipe: `phin_gravity_14_118` — “Gravity-insert phin baseline”
- Method/profile: `phin` /
  `single_serving_gravity_phin_with_loose_drop_in_press_disc`
- Source stage/order: `stage_01` / 1
- `StageId`: `p1_phin_gravity_14_118_stage_01`
- `StageContentId`: `p1_phin_gravity_14_118_stage_01_instruction`
- Proposed exact asset:
  `instruction_p1_phin_gravity_14_118_stage_01_instruction_default`
- Canonical action/type: “Place the phin securely on a heat-safe cup and add
  14 g level coffee” / `ADD_COFFEE`
- Exact equipment state: “Base fully supported by cup rim; perforations clear”
- Completion mode/cue: `Manual` / “Phin is stable and bed level”
- Exact typed reference: source-only coffee dose, exactly 14 g
- Warning/severity: “Do not use a cup whose rim cannot support the phin.” /
  `CRITICAL`
- Visual priority: `safety-critical`
- Evidence: `SRC-NGUYEN-PHIN`; current primary Nguyen Coffee Supply recipe and
  equipment guide reverified 28 July 2026

### Renderer placement and visual goal

Current Learn placement is after the instruction but before the unsupported-cup
warning. Current Live placement is after visible instruction and safety
content. Approval placement is after both instruction and warning in both
surfaces.

This composition must make mechanical stability clearer than dose: the phin's
wide filter plate has continuous support on a broad heat-safe cup, its
perforations remain clear, and the dry bed is level. The exact 14 g remains in
text. Because the loose gravity press belongs to the next stage, it may rest
nearby on the lid but must not appear installed or threaded.

### Clean text-free generation prompt

> Create one original, clean, text-free 4:3 instructional illustration for a
> mobile coffee-brewing guide. Use an elevated three-quarter close view of an
> accurate unbranded single-serving Vietnamese gravity-insert phin with a
> perforated chamber and wide integrated filter plate. Center the phin
> securely on a broad, stable, heat-safe cup whose rim fully supports the
> filter plate all the way around. Show a level dry coffee bed inside the
> chamber after grounds have just been added, with no loose grounds on the rim
> and the bottom perforations unobstructed. Place the matching loose
> perforated gravity press flat and clearly separate on the phin lid beside
> the cup, ready for the next stage, with no threads or screw post. Keep the
> cup base, full rim overlap, level chamber, level dry bed, and clear gravity
> mechanism legible in one coherent view. Use a warm-neutral uncluttered
> counter/background, semi-flat softly dimensional educational rendering,
> clear silhouettes, subtle shadows, and crop-safe mobile-readable framing.
> Include no text, letters, numbers, scale display, measurement marks, labels,
> logos, brand marks, arrows, callouts, panels, or decorative objects.

### Strong negative constraints

- Do not use a narrow, undersized, cracked, tilted, disposable, or unstable
  cup; do not leave any part of the phin base unsupported or off-center.
- Do not show a screw post, threaded insert, tightened press, spring, plunger,
  paper filter, espresso basket, moka pot, or pressure seal.
- Do not install the loose gravity press yet, bury it in the grounds, or make
  it look threaded; keep it safely separate for this stage.
- Do not show an uneven, mounded, compressed, wet, overflowing, or spilled
  coffee bed; do not block the perforations.
- Do not encode 14 g with text, digits, a readable scale, scoop markings, or
  an exact pile size.
- Do not add water, kettle, steam, drips, condensed milk, ice, serving action,
  text, logos, arrows, multiple panels, or clutter.

### Accessibility and geometry review

- Canonical alt text: “Instructional view of place the phin securely on a
  heat-safe cup and add 14 g level coffee using the exact brewer profile and
  filter configuration stated in this recipe; phin is stable and bed level.”
- Invariants: gravity-insert phin; wide filter plate continuously supported by
  a broad heat-safe cup; centered level chamber; unobstructed perforations;
  level dry bed; loose unthreaded press clearly separate; no water.
- Variant blocker: `NONE-PHIN-MECHANISM`. The canonical profile fixes a loose
  gravity press. Never reuse the screw-insert batch-01 image or a generic
  stable-cup candidate without exact-stage identity and a fresh review.
- Reviewer focus: continuous cup-rim support, plausible filter-plate overlap,
  level dry bed, unobstructed perforations, clear loose-disc identity, no
  accidental screw geometry, and mobile-size stability readability.

## 6. Gravity phin hot removal to its lid

### Exact identity and source

- Recipe: `phin_gravity_14_118` — “Gravity-insert phin baseline”
- Method/profile: `phin` /
  `single_serving_gravity_phin_with_loose_drop_in_press_disc`
- Source stage/order: `stage_07` / 7
- `StageId`: `p1_phin_gravity_14_118_stage_07`
- `StageContentId`: `p1_phin_gravity_14_118_stage_07_instruction`
- Proposed exact asset:
  `instruction_p1_phin_gravity_14_118_stage_07_instruction_default`
- Canonical action/type: “Remove the hot phin and choose service” / `SERVE`
- Exact equipment state: “Set phin on inverted lid or heat-safe tray”
- Completion mode/cue: `Manual` / “Concentrate is served black, diluted, over
  ice, or combined with measured condensed milk”
- Warning/severity: “The chamber and insert are hot.” / `CRITICAL`
- Visual priority: `safety-critical`
- Evidence: `SRC-NGUYEN-PHIN`; current primary Nguyen Coffee Supply recipe and
  equipment guide reverified 28 July 2026

### Renderer placement and visual goal

Current Learn placement is after the instruction but before the hot-metal
warning. Current Live placement is after visible instruction and safety
content. Approval placement is after both instruction and warning in both
surfaces.

The bitmap should resolve the hazardous transition, not force a serving
preference: an insulated grip lifts the drained metal phin from the cup and
sets it onto its inverted lid used as a heat-safe coaster. Leave the finished
concentrate black in the stable cup. Compose text can then present black,
diluted, iced, or measured-condensed-milk choices without a noisy four-option
image.

### Clean text-free generation prompt

> Create one original, clean, text-free 4:3 instructional illustration for a
> mobile coffee-brewing guide. Show an accurate unbranded single-serving
> Vietnamese gravity-insert phin immediately after drainage, above a broad
> stable heat-safe cup containing finished black coffee concentrate. Use a
> close elevated three-quarter view. A calm hand protected by a small dry
> folded heat-resistant cloth grips the hot metal chamber securely without
> bare skin touching the chamber, insert, or filter plate, and moves it a very
> short distance onto its own inverted metal lid resting flat on the counter
> as a heat-safe coaster. Keep the loose gravity press safely inside the
> drained chamber, with no screw post or threaded mechanism. Make the
> insulated grip, hot phin, stable cup, short controlled movement, and
> correctly oriented resting lid immediately legible. Keep the black
> concentrate untouched so the image does not privilege milk, ice, or
> dilution. Use a warm-neutral uncluttered counter/background, semi-flat softly
> dimensional educational rendering, clear silhouettes, subtle shadows, and
> crop-safe mobile-readable framing. Include no text, letters, numbers,
> measurement marks, labels, logos, brand marks, arrows, callouts, panels, or
> decorative objects.

### Strong negative constraints

- Do not show bare fingers touching the hot chamber, insert, filter plate, lid,
  or underside; do not grip with a wet cloth, loose tongs, or an implausible
  handle.
- Do not tip, spill, swing, or carry the phin over a long distance; do not set
  it directly on wood, plastic, fabric, skin, or an unstable surface.
- Do not leave the phin on the serving cup as the completion state; the
  inverted lid must be flat, stable, correctly sized, and visibly used as the
  coaster.
- Do not show a screw post, threaded insert, pressure mechanism, paper filter,
  moka pot, or espresso basket.
- Do not show multiple serving options, milk pouring, condensed-milk jar, ice,
  dilution water, readable dose, or a tasting scene.
- Do not add text, warning icons, steam shaped like symbols, logos, arrows,
  multiple panels, before/after views, or visual clutter.

### Accessibility and geometry review

- Canonical alt text: “Instructional view of remove the hot phin and choose
  service using the exact brewer profile and filter configuration stated in
  this recipe; concentrate is served black, diluted, over ice, or combined
  with measured condensed milk.”
- Invariants: gravity-insert phin; completed drained state; insulated dry
  grip; no bare hot-metal contact; stable concentrate cup; short controlled
  lift; matching inverted lid used as coaster; black service kept neutral;
  loose press remains safely contained.
- Variant blocker: `NONE-PHIN-MECHANISM`. The scene is exact to a loose
  gravity-insert phin and its matching lid. Separate geometry review is needed
  for a screw-insert phin, a model with a cool handle, or a lid that cannot
  serve as a coaster.
- Reviewer focus: credible safe grip, clear hot-part avoidance, lid orientation
  and stability, contained insert, no spill path, no accidental service-option
  preference, and safe readability at 300 dp.

## Canonical evidence register for this batch

| Evidence ID | Canonical source-register title | Scope used here |
| --- | --- | --- |
| `SRC-CUPONE-MANUAL` | Technivorm Moccamaster, “Moccamaster Cup-One User Manual” | No. 1 paper, power-off setup, drip-hole cleaning, overflow prevention, and no-submersion context |
| `SRC-HARIO-SWITCH` | Hario, “Immersion Dripper Switch Instructions” | Switch 02 geometry, V60 paper, lever/button, silicone base, and steel-ball valve state |
| `SRC-KURASU-SWITCH` | Kurasu, “Hario Switch Recipe” | Ole Bøen hybrid stage sequence and retained final pour |
| `SRC-HARIO-V60-OFFICIAL` | Hario UK, “Intermediate V60 Brew Guide” | V60 02 paper identity and pre-brew rinse |
| `SRC-NGUYEN-PHIN` | Nguyen Coffee Supply, “Vietnamese Coffee Phin Brew Guide” | Gravity-press identity, stable cup setup, 14 g level bed, service options, and lid-as-coaster behaviour |

## Canonical identity and collision validation

The queue is valid only while all of these remain true:

- All six recipe/source-stage pairs exist in the canonical JSON, exact-stage
  matrix, exact executable plan catalog, packaged exact-guidance JSON, and
  runtime exact-guidance catalog.
- The six `StageId` values, six `StageContentId` values, and six proposed asset
  IDs are internally unique and follow the exact deterministic naming rule.
- Each selected matrix row was `safety-critical`, `NOT_PRODUCED`, and
  `P1-STAGE-CARD` when queued, remains evidence-backed, and is free of a hard
  equipment blocker.
- Each of the six local drawables uses its matching stable ID and has one
  production-log record. None collides with a batch-01 exact asset, the
  pre-existing generic phin candidate, or a registered
  `InstructionAssetRecord`.
- The pending generic phin stable-cup candidate remains partial generic
  coverage and is not promoted or renamed as this exact composite stage.
- No prompt substitutes another paper size, machine, outlet, valve/actuator,
  brewer state, phin retaining mechanism, cup support, or service geometry.
- Quantities and time references stay in text; a generated bitmap cannot be
  accepted if it invents a readable number or implies a contradictory exact
  fill level.
- Alt text must remain the canonical stage text above unless the canonical
  accessibility record changes and the queue is revalidated with it.
- No source image is copied, downloaded into the repository, traced, or used
  as a style reference.
- All six generated bitmaps have a production-log record and validated local
  WebP payload. They remain intentionally unregistered and are not described as
  approved. Each stays `PENDING_REVIEW` until expert geometry, safety,
  accessibility, localization, and placement review all pass.
- The safety-critical ordering change described above was subsequently
  resolved. The final order must still be reverified on both Learn and Live Brew
  at asset-review time before any candidate is registered or approved.
