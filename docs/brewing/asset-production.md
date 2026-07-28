# Instruction asset production workflow

## Prompt template

> A clean text-free instructional illustration showing [one exact brewing
> action] using [brewer profile and equipment]. The equipment geometry, filter
> placement, liquid level, hand placement and flow direction must be physically
> accurate. Show only the objects necessary to understand this action on a
> neutral uncluttered countertop. Consistent semi-flat educational illustration
> style, softly dimensional, clear silhouettes, subtle shadows, high readability
> at mobile size, restrained warm-neutral palette, 4:3 composition, crop-safe
> center. No words, no letters, no numbers, no labels, no logos, no recipe card,
> no infographic, no multiple panels, no decorative kitchen clutter.

Add profile-specific negative constraints, for example “do not tamp moka coffee”
or “do not show an unstable inverted AeroPress.”

## Production record

For every asset record:

- Stable asset ID, method family, brewer profile, stage/content ID, and variant.
- Exact prompt, source document, revision, generation date, and review status.
- Drawable path, pixel dimensions, encoded size, and 4:3 validation result.
- Alt-text resource and reviewer notes about equipment/safety accuracy.

### `clever-rinse-imagegen-v1`

- Stable asset ID:
  `instruction_steep_and_release_clever_style_clever_style_insert_and_rinse_filter_default`
- Scope: Clever-style bottom-actuated dripper, correctly seated wedge paper,
  rinse stage shared by `clever_water_first_15_250` and
  `clever_coffee_first_15_250`.
- Generation mode: new bitmap generation with the built-in image generator.
- Generated: 28 July 2026.
- Exact prompt:

  > Create one clean, text-free 4:3 instructional illustration for a mobile
  > coffee-brewing guide. Show a translucent, unbranded Clever-style
  > bottom-actuated steep-and-release dripper with a correctly seated wedge
  > paper filter being rinsed by a narrow stream of hot water from a simple
  > gooseneck kettle. The brewer is held securely over a stable waste server so
  > rinse water drains safely; show the wedge paper seated flush and visibly
  > wetted, with accurate brewer, paper, server, hand, stream, and liquid
  > geometry. Focus on this single action. Neutral uncluttered countertop and
  > warm-neutral background, consistent semi-flat educational illustration,
  > softly dimensional, clear silhouettes, restrained palette, subtle shadows,
  > readable at mobile size, all important action inside the full frame. No
  > words, letters, numbers, labels, logos, brand marks, recipe card, UI,
  > infographic, arrows, multiple panels, decorative kitchen clutter, floating
  > paper, cone-shaped paper, unsafe grip, unstable vessel, or implied recipe
  > quantities.
- Final drawable:
  `app/src/main/res/drawable-nodpi/instruction_steep_and_release_clever_style_clever_style_insert_and_rinse_filter_default.webp`
- Delivery: opaque RGB WebP, 1024 × 768 px, 79,850 bytes, exact 4:3.
- Accessibility: uses the existing `prep_tip_pour_over_paper` resource, which
  is present in all 23 supported locales, for alt and companion copy pending
  recipe-specific localization.
- Inspection: no embedded text, numbers, labels, logos, extra panels, or visual
  noise; action remains legible at phone size. The dripper, wedge-like paper,
  supported waste server, and rinse flow are visually coherent.
- Review status: **pending brewer-expert review**. Do not mark approved until a
  reviewer confirms the exact paper seating, bottom-actuator/server contact,
  hot-water handling, and canonical stage fit.

### `hario-switch-add-coffee-imagegen-v2`

- Stable asset ID:
  `instruction_steep_and_release_hario_switch_hario_switch_add_coffee_default`
- Scope: measured coffee entering a V60 02 paper in a handleless Switch 02
  glass cone. The valve assembly is outside the frame, so this physical action
  remains valid across the official immersion, hybrid, and open-gravity plans.
- Generation mode: new bitmap generation followed by one corrective image edit.
- Generated: 28 July 2026.
- Base prompt:

  > Create one clean, original, text-free 4:3 instructional illustration for a
  > mobile coffee-brewing guide in a consistent semi-flat, softly dimensional
  > educational style. Show measured medium-fine ground coffee being poured
  > from a small plain ceramic dosing cup into a correctly fitted white V60 02
  > conical paper filter inside an unbranded Hario Switch 02 glass cone, forming
  > a visibly level coffee bed. Frame closely around the upper glass cone,
  > paper, dosing cup, grounds, and stable clear server support. Deliberately
  > keep the Switch base, steel ball, lever, and valve state outside the frame.
  > Do not show water, a kettle, agitation, fingers touching grounds, or an
  > uneven bed. Use a neutral warm-cream counter and background, clear
  > silhouettes, restrained warm-neutral palette, subtle shadows, diffuse
  > upper-left light, and mobile-readable composition. No words, letters,
  > numbers, labels, logos, marks, UI, infographic, arrows, multiple panels,
  > clutter, extra brewer, Clever-style body, or floating parts.
- Corrective edit prompt:

  > Preserve the single action, 4:3 composition, hand, dosing cup, falling
  > grounds, seated white conical paper, warm background, and level coffee bed.
  > Remove the entire glass handle. Use an unbranded Hario Switch 02-style
  > handleless glass V60 cone with accurate diagonal ribs and a smooth circular
  > rim, not a handled V60 dripper or glass cup. End the framing before the
  > silicone base, steel ball, lever, and valve state become visible; a stable
  > clear server may remain below. Make the rendering semi-flat and softly
  > dimensional rather than photorealistic. Keep paper flush to glass. Do not
  > add water, kettle, valve, lever, logo, text, numbers, measurement marks,
  > arrows, extra panels, clutter, extra hands, or a handle.
- Rejected draft: the initial render added a handled glass cone, so it was not
  copied into the repository or manifest.
- Final drawable:
  `app/src/main/res/drawable-nodpi/instruction_steep_and_release_hario_switch_hario_switch_add_coffee_default.webp`
- Delivery: opaque RGB WebP, 1024 × 768 px, 84,364 bytes, exact 4:3.
- Accessibility: uses the existing `action_brew_add_coffee` resource, present
  in all 23 supported locales, pending recipe-specific descriptive alt text.
- Inspection: no handle, valve, lever, text, logo, water, or extra action is
  shown. The seated cone paper, falling grounds, level bed, stable support, and
  hand placement remain clear at phone size.
- Review status: **pending brewer-expert review**. Confirm the handleless glass
  cone/rib geometry, paper seating, server support, and cross-recipe framing
  before approval.

### `phin-stable-cup-imagegen-v2`

- Stable asset ID:
  `instruction_restricted_flow_gravity_concentrate_vietnamese_phin_vietnamese_phin_place_on_stable_cup_default`
- Scope: empty single-serving phin chamber and integrated perforated base on a
  broad heat-safe cup, before either gravity-disc or threaded-insert workflow.
- Generation mode: new bitmap generation followed by one corrective image edit.
- Generated: 28 July 2026.
- Base prompt:

  > Create one clean, original, text-free 4:3 mobile instructional illustration
  > in a semi-flat, softly dimensional warm-neutral style. Show an empty,
  > unbranded single-serving Vietnamese phin centered securely on a broad,
  > level, heat-safe ceramic cup. Accurately show the perforated support
  > plate/flange resting with full even contact on the cup rim and the empty
  > cylindrical metal chamber upright above it. The cup must support the plate
  > with generous overlap. One hand may steady the cup by its handle or lower
  > body, not touch the phin. Show no coffee, insert, screw, lid, water, kettle,
  > steam, milk, ice, drips, pressure, or brewing action. Use an elevated
  > three-quarter view so the flange, centered chamber, level cup, and stable
  > footprint are obvious. No words, letters, numbers, labels, logos, marks,
  > arrows, panels, ticks, clutter, narrow glass, tilted cup, off-center phin,
  > floating parts, extra hands, or hot-metal contact.
- Corrective edit prompt:

  > Preserve the centered chamber, broad support flange, cup-rim contact, hand
  > position, 4:3 framing, and warm educational style. Remove every decorative
  > object, including plant leaves, vase, tray, and terracotta pot; use a plain
  > warm-cream wall and uncluttered counter. Make the empty phin's integrated
  > perforated metal floor clearly visible through the open chamber as a flat
  > circular base with a regular field of small round perforations. Keep it
  > empty and upright. Do not add insert, screw, lid, coffee, water, kettle,
  > steam, drips, milk, text, logo, marks, arrows, panels, extra hands, clutter,
  > floating parts, or impossible fused geometry.
- Rejected draft: the initial render hid the perforated floor and added plants,
  pottery, and a tray, so it was not copied into the repository or manifest.
- Final drawable:
  `app/src/main/res/drawable-nodpi/instruction_restricted_flow_gravity_concentrate_vietnamese_phin_vietnamese_phin_place_on_stable_cup_default.webp`
- Delivery: opaque RGB WebP, 1024 × 768 px, 86,592 bytes, exact 4:3.
- Accessibility: uses the existing `warning_brew_safety_stability` resource,
  present in all 23 supported locales, pending recipe-specific descriptive alt
  text.
- Inspection: the empty perforated floor, upright chamber, full flange overlap,
  wide stable cup, and hand kept off metal are legible at phone size; the frame
  contains no decorative objects or second action.
- Review status: **pending brewer-expert safety review**. Confirm the phin's
  integrated base/perforation geometry, flange overlap, cup support, and absence
  of implied pressure before approval.

### `p1-chemex-filter-air-channel-imagegen-v2`

- Stable asset ID:
  `instruction_p1_chemex_42_700_stage_01_instruction_default`
- Exact scope: `chemex_42_700` / source `stage_01` /
  `p1_chemex_42_700_stage_01_instruction`; six-cup hourglass carafe and
  bonded filter, before coffee or water is added.
- Evidence: `SRC-CHEMEX-FAQ`. Reverified on 28 July 2026 against the current
  official Chemex FAQ at <https://chemexcoffeemaker.com/pages/faq>, which
  still identifies three filter leaves on the spout as the protection against
  collapse into the air channel.
- Generation mode: new bitmap generation followed by one corrective image
  edit with the built-in image generator.
- Generated: 28 July 2026.
- Base prompt:

  > Create one original, clean, text-free 4:3 scientific-educational
  > instructional illustration for a mobile coffee-brewing guide. Use an
  > elevated three-quarter close view of the upper half of an unbranded
  > six-cup Chemex-style hourglass borosilicate glass carafe, stable on a
  > simple warm-neutral counter. Make the pouring spout and its open air
  > channel unmistakably visible. Show one correctly folded thick bonded paper
  > filter seated in the carafe: the visibly thicker three-layer side lies
  > over the pouring spout, while exactly one paper layer lies on the opposite
  > side. Preserve a clearly visible open passage between the folded paper and
  > the spout so the filter cannot collapse into and seal the air channel. A
  > relaxed hand may gently steady the dry filter at the rim without hiding
  > the fold, layer asymmetry, or spout. Keep the carafe empty and focus on
  > this single correct setup state. Semi-flat softly dimensional educational
  > rendering, restrained warm-neutral palette, clear silhouettes, subtle
  > shadows, uncluttered neutral background, crop-safe composition readable at
  > 300–400 dp, all critical geometry away from rounded corners. No text,
  > letters, numbers, measurement marks, labels, captions, logos, brand marks,
  > UI, arrows, inset, split panel, comparison, kettle, coffee grounds, water,
  > active pour, steam, serving action, second filter, metal filter, V60 cone,
  > decorative objects, clutter, sealed spout, or single-layer side over the
  > spout.
- Corrective edit prompt:

  > Edit the immediately previous Chemex instructional illustration while
  > preserving its clean 4:3 warm-neutral semi-flat style, elevated close
  > camera, empty stable six-cup hourglass carafe, and absence of text or
  > clutter. Correct the critical filter geometry so it is unambiguous: rotate
  > and reseat the folded bonded paper filter around the vertical axis so the
  > visibly thick three-layer bundle is directly against and centered over the
  > inside wall of the pouring spout, with three distinct stacked paper edges
  > readable immediately behind the spout. Put exactly one paper layer on the
  > opposite rear side. Show a clear narrow open air passage running between
  > the three-layer bundle and the spout groove, so neither paper nor fold
  > seals the channel. Do not let the broad single sheet cross or cover the
  > spout. Keep the relaxed hand only at the far rim and do not hide the three
  > paper edges, spout, or open channel. No other objects, coffee, water,
  > kettle, steam, text, numbers, labels, logos, arrows, inset, split panel,
  > comparison, or decoration.
- Rejected draft: the first render offset the visible three-layer edge from
  the spout enough that the broad single sheet could be read as covering the
  air channel. It was not copied into the repository or manifest.
- Corrected generated master:
  `C:\Users\adam-\.codex\generated_images\019fa48e-1d5d-7560-b0de-f39fa2e8b914\call_AjIWZRF6721moi7VoFiKR7OI.png`
  (opaque RGB PNG, 1448 × 1086 px, exact 4:3).
- Final drawable:
  `app/src/main/res/drawable-nodpi/instruction_p1_chemex_42_700_stage_01_instruction_default.webp`
- Delivery: opaque RGB WebP, 1024 × 768 px, 71,828 bytes, exact 4:3.
- Accessibility target: “A folded bonded filter sits in a six-cup Chemex with
  its three-layer side over the spout and the air channel visibly open.” The
  exact-stage alt-text resource and asset record remain intentionally
  unregistered until localization and geometry review are complete.
- Inspection: no text, numbers, logos, extra panels, liquid, coffee, kettle,
  or secondary action. The corrected frame puts three distinct paper edges at
  the spout, keeps the single layer opposite, and remains readable at phone
  size.
- Review status: **pending brewer-expert safety and geometry review**. Confirm
  the layer count, fold orientation, visible air passage, correct six-cup
  bonded-filter fit, and post-warning Learn/Live placement before manifest
  registration or approval.

### `p1-clever-release-imagegen-v2`

- Stable asset ID:
  `instruction_p1_clever_water_first_15_250_stage_05_instruction_default`
- Exact scope: `clever_water_first_15_250` / source `stage_05` /
  `p1_clever_water_first_15_250_stage_05_instruction`; the bottom-actuated
  release onto a broad stable server after immersion.
- Evidence: `SRC-CLEVER-HOFFMANN` and `SRC-CLEVER-COFFEECHRONICLER`.
  Reverified on 28 July 2026 against the current Clever distributor product
  guide at
  <https://cleverbrewing.coffee/collections/clever-manual-brewers/products/clever-dripper>,
  which still specifies a standard #4 filter and placing the dripper on a
  carafe or cup to activate the drain valve.
- Generation mode: new bitmap generation followed by one corrective image
  edit with the built-in image generator.
- Generated: 28 July 2026.
- Base prompt:

  > Create one original, clean, text-free 4:3 scientific-educational
  > instructional illustration for a mobile coffee-brewing guide. Show an
  > unbranded translucent Clever-style bottom-actuated steep-and-release
  > dripper being lowered vertically and seated squarely onto a broad, stable,
  > heat-safe glass server on a simple warm-neutral counter. The brewer
  > contains a correctly seated wet wedge-shaped paper filter and brewed coffee
  > slurry with generous headroom, without implying an exact quantity. Make
  > the full even server-rim support visibly press the brewer’s small bottom
  > actuator upward so its outlet is open and one steady vertical coffee stream
  > has just begun flowing into the server. Use an elevated close three-quarter
  > view that clearly shows the real actuator-to-server contact beneath the
  > brewer, the centered support overlap, and the initial flow. One relaxed
  > hand holds only the brewer’s cool side handle, away from the hot underside;
  > no second hand. Semi-flat softly dimensional educational rendering,
  > restrained warm-neutral palette, clear silhouettes, subtle shadows,
  > uncluttered neutral background, crop-safe mobile-readable composition with
  > critical geometry away from rounded corners. No text, letters, numbers,
  > measurement marks, labels, captions, logos, brand marks, UI, arrows, inset,
  > comparison, split panel, decoration, narrow cup, undersized server,
  > off-center support, tilted brewer, overflow, fingers under the brewer,
  > lever, steel ball, tap, Hario Switch base, V60 cone paper, kettle, active
  > pour, stirring, serving, closed valve, no-flow state, or clutter.
- Corrective edit prompt:

  > Edit the immediately previous Clever release illustration while
  > preserving the 4:3 warm-neutral educational style, stable broad server,
  > one hand on the cool handle, centered brewer, and initial vertical coffee
  > flow. Correct the brewer identity and release mechanism. Replace the
  > V60-like ribbed conical body with a smooth translucent Clever-style 500 ml
  > steep-and-release body: a familiar #4 wedge-dripper silhouette with gently
  > sloped broad walls, flat handle side, and a correctly seated standard #4
  > wedge paper whose folded seams sit flush. Keep the brewed slurry lower with
  > generous headroom. At the underside, clearly show the real small bottom
  > drain-valve actuator/tab being pushed upward by direct contact with the
  > server rim; the actuator must visibly compress/open the adjacent outlet as
  > the single coffee stream begins. Make the rim-to-actuator contact readable
  > at phone size without a diagram, arrow, label, cutaway panel, or extra hand.
  > The wide support flange must sit fully and evenly on the server, not float.
  > Do not show V60 ribs, a cone paper, outlet pipe, long nozzle, Switch lever,
  > steel ball, tap, undersized cup, off-center support, tilted brewer, fingers
  > under hot parts, overflow, kettle, stirring, text, numbers, logos, arrows,
  > inset, split panel, comparison, decoration, or clutter.
- Rejected draft: the first render used a V60-like vertically ribbed cone and
  left the actuator hidden behind an outlet-pipe-like shape. It was not copied
  into the repository or manifest.
- Corrected generated master:
  `C:\Users\adam-\.codex\generated_images\019fa48e-1d5d-7560-b0de-f39fa2e8b914\call_odoQwDbXpF0OELrwPlAiZgXs.png`
  (opaque RGB PNG, 1448 × 1086 px, 1,956,836 bytes, exact 4:3).
- Final drawable:
  `app/src/main/res/drawable-nodpi/instruction_p1_clever_water_first_15_250_stage_05_instruction_default.webp`
- Delivery: opaque RGB WebP, 1024 × 768 px, 81,288 bytes, exact 4:3.
- Accessibility target: “A Clever-style dripper sits squarely on a wide
  stable server, opening its bottom actuator as coffee begins to drain.” The
  exact-stage alt-text resource and asset record remain intentionally
  unregistered until localization and geometry review are complete.
- Inspection: no text, numbers, logos, extra panel, kettle, second hand, or
  secondary action. The correction replaces the V60-like body with a smooth
  #4 wedge-filter silhouette, gives the server generous support overlap, and
  shows immediate vertical drawdown.
- Review status: **pending brewer-expert safety and geometry review**. Confirm
  the exact #4 paper seating, bottom-valve/release-plate relationship,
  server-rim actuation, stable capacity, hand clearance, and post-warning
  Learn/Live placement before manifest registration or approval.

### `p1-switch-release-imagegen-v3`

- Stable asset ID:
  `instruction_p1_switch_official_20_240_stage_04_instruction_default`
- Exact scope: `switch_official_20_240` / source `stage_04` /
  `p1_switch_official_20_240_stage_04_instruction`; opening the Switch 02
  trigger and steel-ball valve over a stable server.
- Evidence: `SRC-HARIO-SWITCH`. Reverified on 28 July 2026 against Hario’s
  current official product guidance at
  <https://www.hario-usa.com/products/switch-immersion-dripper>, which still
  identifies the glass 02 cone, standard 02 paper, silicone base, trigger,
  stainless-steel ball, and trigger-initiated drawdown.
- Generation mode: new bitmap generation followed by two targeted corrective
  image edits with the built-in image generator.
- Generated: 28 July 2026.
- Base prompt:

  > Create one original, clean, text-free 4:3 scientific-educational
  > instructional illustration for a mobile coffee-brewing guide. Use a close
  > elevated three-quarter view of an unbranded Hario Switch 02-style brewer
  > centered over a broad stable heat-safe glass server: a handleless ribbed
  > borosilicate V60 02 glass cone, correctly seated white V60 02 paper, intact
  > dark silicone base, clear plastic trigger lever, and stainless-steel
  > stopper ball. The brewer contains brewed coffee slurry with generous
  > headroom and no implied exact quantity. Show one relaxed fingertip
  > contacting only the cool outer trigger tab and flipping it into the
  > documented open position. Make the mechanism physically coherent and
  > readable at phone size: use a subtle educationally translucent view
  > through the small front portion of the silicone base so the stainless-steel
  > ball is visibly lifted from the outlet, while one vertical coffee stream
  > has just begun draining into the centered server. Keep the hand clearly
  > away from hot glass, liquid, metal ball, and underside. Semi-flat softly
  > dimensional educational rendering, restrained warm-neutral palette, clear
  > silhouettes, subtle shadows, uncluttered counter/background, crop-safe
  > framing with all critical geometry away from rounded corners. No text,
  > letters, numbers, measurement marks, labels, captions, logos, brand marks,
  > UI, arrows, inset, split panel, comparison, glass handle, Clever body,
  > server-rim actuator, tap, generic valve, closed ball, closed lever, no-flow
  > state, fingers on hot parts, undersized or unstable server, kettle, active
  > pour, stirring, serving, overflow, decoration, or clutter.
- First corrective edit prompt:

  > Edit the immediately previous Hario Switch 02 release illustration while
  > preserving its clean 4:3 warm-neutral educational style, stable centered
  > server, fingertip on only the cool clear trigger, and single initial coffee
  > stream. Correct three exact mechanical details. First, show the
  > stainless-steel stopper ball unmistakably open: the trigger’s inner lifting
  > fork must physically raise the ball several millimetres above the circular
  > outlet seat, with a clear visible air gap all around between ball and seat
  > and coffee passing through that open gap into the central outlet. Do not
  > leave the ball touching or sealing the seat. Second, replace the vertical
  > straight ribs with the characteristic curved diagonal spiral ribs of a
  > handleless glass V60 02 cone. Third, replace the scalloped/crimped paper rim
  > with one correctly seated smooth white V60 02 conical paper, its seam/tab
  > plausible and its rim not wavy. Keep the intact dark silicone Switch base
  > and transparent educational window around only the lever-ball-seat
  > relationship. Keep the hand away from glass, coffee, metal, and underside.
  > No Clever actuator, handle, tap, closed valve, impossible floating ball,
  > extra mechanism, text, numbers, logos, arrows, labels, inset, split panel,
  > comparison, kettle, stirring, overflow, decoration, or clutter.
- Final corrective edit prompt:

  > Make one final precise mechanical edit to the immediately previous Switch
  > illustration; preserve every other corrected detail, 4:3 framing,
  > warm-neutral semi-flat style, diagonal V60 glass ribs, smooth seated V60 02
  > paper, fingertip on the cool clear trigger, stable server, and coffee
  > stream. Remove the invented vertical stem above the stainless-steel ball.
  > Move the ball visibly upward so its lowest point is clearly separated from
  > the black circular outlet seat below by a large clean gap approximately one
  > quarter of the ball diameter. Show the inner end of the clear trigger as a
  > physically connected lifting fork/ramp directly under one side of the ball,
  > supporting it in this raised open position. The circular outlet seat must
  > remain centered below the raised ball, and coffee must pass visibly through
  > the open gap around the seat before exiting as the single stream. The ball
  > must not touch the seat, float without support, attach to an upper rod, or
  > block the outlet. Do not change or add anything else. No text, numbers,
  > logos, arrows, labels, inset, extra panel, or clutter.
- Rejected drafts: the initial render left the steel ball visually seated while
  coffee flowed and used straight glass ribs plus a crimped paper rim. The
  first edit corrected the cone and paper but retained an invented upper stem
  and an ambiguous ball/seat gap. Neither draft was copied into the repository
  or manifest.
- Corrected generated master:
  `C:\Users\adam-\.codex\generated_images\019fa48e-1d5d-7560-b0de-f39fa2e8b914\call_PVSSv7fepwMHE5DIHSSXtm3T.png`
  (opaque RGB PNG, 1448 × 1086 px, 2,164,880 bytes, exact 4:3).
- Final drawable:
  `app/src/main/res/drawable-nodpi/instruction_p1_switch_official_20_240_stage_04_instruction_default.webp`
- Delivery: opaque RGB WebP, 1024 × 768 px, 90,364 bytes, exact 4:3.
- Accessibility target: “A fingertip moves the Switch lever open while the
  steel-ball valve lifts and coffee starts draining into a stable server.” The
  exact-stage alt-text resource and asset record remain intentionally
  unregistered until localization and geometry review are complete.
- Inspection: no text, numbers, logos, extra panel, kettle, hot-part contact,
  or secondary action. The final pass shows the handleless spiral-ribbed 02
  glass cone, smooth 02 paper, fingertip on the external trigger, a visible
  gap between the raised ball and centered outlet seat, and immediate vertical
  drawdown.
- Review status: **pending brewer-expert safety and mechanism review**. Confirm
  the exact trigger/lifting-fork relationship, steel-ball travel and seat,
  silicone-base proportions, paper fit, hand clearance, and post-warning
  Learn/Live placement before manifest registration or approval.

### `p1-phin-screw-insert-imagegen-v1`

- Stable asset ID:
  `instruction_p1_phin_screw_18_120_stage_02_instruction_default`
- Exact scope: `phin_screw_18_120` / source `stage_02` /
  `p1_phin_screw_18_120_stage_02_instruction`; lightly engaging the level
  threaded insert without compressing the dry coffee bed.
- Evidence: `SRC-GOURMETKAVA-PHIN` and `SRC-TRUNGNGUYEN-PHIN`. Reverified on
  28 July 2026 against GourmetKava's current preparation guide at
  <https://www.gourmetkava.cz/en/blog/making-coffee/preparation-of--vietnamese-coffee>,
  which continues to distinguish a gently screwed internal plate, room for
  the coffee to swell after pre-wetting, and gravity rather than pressure.
- Generation mode: new bitmap generation with the built-in image generator;
  no corrective edit was needed before pending-review delivery.
- Generated: 28 July 2026.
- Final prompt:

  > Create one original, clean, text-free 4:3 instructional illustration for
  > a mobile coffee-brewing guide, composed to remain clear at roughly 320 dp
  > wide. Show an elevated close three-quarter view looking into an unbranded
  > single-serving Vietnamese screw-insert phin with a realistic approximately
  > 120–150 ml chamber, centered securely on a broad stable heat-safe ceramic
  > cup. Inside the dry metal chamber, show a level bed of ground coffee above
  > the integrated perforated base. Accurately show a slender threaded central
  > post and a matching circular perforated screw insert aligned perfectly
  > level. Relaxed fingertips lightly turn only the insert's small cool upper
  > control just enough for the threads to engage and for the disc to rest
  > gently on the coffee bed. The coffee remains loose and visibly
  > uncompressed, with generous empty headroom above it for swelling. Make the
  > gentle fingertip posture, level perforated disc, visible central thread
  > engagement, dry chamber, and stable cup support unmistakable in one
  > crop-safe view. Use a warm-neutral uncluttered background and counter,
  > semi-flat softly dimensional educational rendering, precise believable
  > geometry, clear silhouettes, restrained materials, soft natural light,
  > and subtle shadows. No water, kettle, heat, steam, lid, drips, milk, ice,
  > serving action, or second brewer. Do not depict a loose gravity disc,
  > unthreaded plate, spring, plunger, tamper, paper filter, sealed pressure
  > chamber, espresso mechanism, forceful palm pressure, white knuckles,
  > tools, hard compression, compacted puck, deeply driven disc, narrow or
  > unstable cup, off-center base, tilted chamber, impossible thread geometry,
  > or touching hot metal. Absolutely no words, letters, numbers, labels,
  > logos, arrows, callouts, diagrams, multiple panels, borders, or decorative
  > clutter.
- Generated master:
  `C:\Users\adam-\.codex\generated_images\019fa48e-1d5d-7560-b0de-f39fa2e8b914\call_4zFPSw44cwFgOu4sYwzeIyJ7.png`
  (opaque RGB PNG, 1448 × 1086 px, 2,059,862 bytes, exact 4:3).
- Final drawable:
  `app/src/main/res/drawable-nodpi/instruction_p1_phin_screw_18_120_stage_02_instruction_default.webp`
- Delivery: opaque RGB WebP, 1024 × 768 px, 74,788 bytes, exact 4:3.
- Accessibility target: “Relaxed fingertips lightly engage the level threaded
  insert of a screw-insert phin without compressing the coffee bed.” The
  exact-stage alt-text resource and asset record remain intentionally
  unregistered until localization and mechanism review are complete.
- Inspection: no text, numbers, logos, extra panel, water, heat, lid, tamper,
  or secondary action. The dry frame clearly separates this screw-insert
  recipe from a loose gravity-disc phin, keeps the disc level, shows gentle
  fingertip-only engagement, leaves the bed visibly loose, and preserves
  swelling headroom above it.
- Review status: **pending brewer-expert safety and mechanism review**. Confirm
  credible 120–150 ml proportions, fixed-post/threaded-insert geometry, light
  engagement without implied pressure, dry-bed headroom, stable cup support,
  and post-warning Learn/Live placement before manifest registration or
  approval.

### `p1-cup-one-setup-imagegen-v3`

- Stable asset ID:
  `instruction_p1_auto_cupone_20_300_stage_03_instruction_default`
- Exact scope: `auto_cupone_20_300` / source `stage_03` /
  `p1_auto_cupone_20_300_stage_03_instruction`; fresh cold reservoir input,
  installed outlet pipe, #1 paper holder, and stable mug before power-on.
- Evidence: `SRC-CUPONE-MANUAL`. Reverified on 28 July 2026 against the
  current official Cup-One manual at
  <https://www.moccamaster.eu/pub/media/handleidingen/talen/User_Manual_Cup-One.pdf>,
  Moccamaster USA's May 2026 Cup-One brew-guide listing at
  <https://support.moccamaster.com/hc/en-us/articles/1500009438902-Cup-One>,
  and the current official product page at
  <https://us.moccamaster.com/products/cup-one>. The current product guidance
  continues to identify the 0.33 L single-cup body, #1 paper, outlet arm over
  the brew basket, removable cup support, and fresh tap, filtered, or bottled
  reservoir water.
- Generation mode: new bitmap generation followed by two targeted corrective
  image edits with the built-in image generator.
- Generated: 28 July 2026.
- Base prompt:

  > Create one original, clean, text-free 4:3 instructional illustration for
  > a mobile coffee-brewing guide, readable at roughly 320 dp wide. Show an
  > accurate unbranded Moccamaster Cup-One-style single-cup drip brewer in one
  > stable pre-cycle setup, viewed from an elevated front three-quarter angle.
  > Preserve the distinctive compact machine geometry: a tall rectangular
  > metal heating column, a clear single-cup water reservoir beside it with its
  > lid open, a correctly installed polished metal outlet arm/pipe extending
  > from the column and centered directly over the small black Cup-One filter
  > holder, and a broad stable heat-safe ceramic mug centered below on the
  > machine's support. One relaxed hand pours fresh cold clear water from a
  > plain small pitcher into only the open reservoir, stopping exactly at a
  > subtle molded fill ridge with no printed marking or number. The outlet arm
  > is fully seated in its socket and its end is directly above the center of
  > one correctly seated small white #1 wedge paper filter inside the holder.
  > Keep the holder level and attached, the mug broad and fully supported, and
  > the power switch visibly unlit so the machine is clearly off. Show no
  > coffee grounds in this setup stage. Compose the reservoir opening,
  > qualitative water level, installed outlet arm, paper-lined holder, and mug
  > support clearly in a single crop-safe frame, with no secondary action. Use
  > a warm-neutral uncluttered background and counter, semi-flat softly
  > dimensional educational rendering, precise believable product geometry,
  > clear silhouettes, restrained materials, soft natural light, and subtle
  > shadows. Do not omit, remove, tilt, disconnect, or point the outlet pipe
  > away from the filter holder. Do not pour into the basket, paper, mug, or
  > outlet pipe. No overfilled reservoir, printed measurements, active brew
  > flow, drips, steam, glowing switch, moving parts, hot-part handling,
  > removed basket, moving cup, pod or capsule machine, carafe batch brewer,
  > generic showerhead, #2 cone or basket paper, narrow or unstable mug,
  > spilled grounds, extra tools, text, letters, numbers, labels, logos, brand
  > marks, arrows, callouts, diagrams, multiple panels, borders, or decorative
  > clutter.
- First corrective edit prompt:

  > Edit the immediately previous Cup-One setup illustration while preserving
  > its 4:3 framing, cold-water pour into the clear reservoir, installed
  > centered metal outlet arm, stable broad mug, machine-off state,
  > warm-neutral semi-flat educational rendering, and clear pre-cycle
  > composition. Replace the oversized round handled cone basket with a
  > compact model-specific Cup-One #1 filter holder: a small narrow black
  > wedge/trapezoidal holder with no side handle, seated directly below the
  > outlet arm, containing exactly one small white folded #1 wedge paper whose
  > two flat sides and folded seam fit closely without flaring like a V60 cone
  > or round basket. Keep the polished outlet pipe fully seated in the machine
  > and center its downturned end directly over the small paper holder. Remove
  > the printed O or any symbol from the power rocker; it must be a plain dark
  > unlit control with no mark. Remove the plant, pots, jars, chair, and all
  > decorative background objects so only the brewer, pitcher, hand, and
  > stable mug remain on a simple uncluttered counter. Make the reservoir's
  > current water surface meet a subtle molded horizontal fill ridge with no
  > writing, number, tick marks, or printed graphic; the pouring stream may be
  > just ending at that level, with no overflow. Do not alter the broad fully
  > supported mug or add active coffee flow, steam, grounds, glow, hot-part
  > contact, labels, text, numbers, logos, arrows, inset, extra panel, or
  > clutter.
- Final corrective edit prompt:

  > Make one final precise mechanical correction to the immediately previous
  > Cup-One pre-cycle illustration while preserving the 4:3 framing, compact
  > #1 wedge paper and holder, cold-water pour, stable broad mug, unmarked
  > unlit switch, clean background, warm-neutral educational style, and every
  > other corrected detail. The small black filter holder must not appear to
  > float or hang from the metal outlet pipe. Add a short rigid model-integrated
  > black support bracket or slide rail from the machine column to the back of
  > the holder, clearly holding the holder level and stable. Keep the holder
  > separate from the polished outlet arm. Raise or shorten the outlet arm's
  > downturned end so it stops just above the center of the open #1 paper with
  > a small visible air gap; it must not touch, pierce, or carry the paper or
  > holder. Add one subtle transparent molded horizontal fill ridge on the
  > reservoir exactly at the current water surface, with no text, number,
  > ticks, icon, or printing. Do not change the machine-off state or add flow,
  > steam, grounds, labels, logo, arrow, inset, extra panel, decorative object,
  > or clutter.
- Rejected drafts: the initial render used an oversized round handled holder,
  a printed power-control symbol, and decorative background objects. The first
  edit corrected the compact #1 paper/holder and noise but left the holder
  visually unsupported and the outlet pipe touching the paper. Neither draft
  was copied into the repository or manifest.
- Corrected generated master:
  `C:\Users\adam-\.codex\generated_images\019fa48e-1d5d-7560-b0de-f39fa2e8b914\call_IVaYQbahuIN4Hh617nDU9C3N.png`
  (opaque RGB PNG, 1448 × 1086 px, 1,998,431 bytes, exact 4:3).
- Final drawable:
  `app/src/main/res/drawable-nodpi/instruction_p1_auto_cupone_20_300_stage_03_instruction_default.webp`
- Delivery: opaque RGB WebP, 1024 × 768 px, 59,040 bytes, exact 4:3.
- Accessibility target: “Fresh cold water is poured to the Cup-One reservoir
  mark while the outlet pipe is centered over the #1 paper holder and a stable
  mug.” The exact-stage alt-text resource and asset record remain intentionally
  unregistered until localization and equipment review are complete.
- Inspection: no text, numbers, logos, extra panel, grounds, hot flow, steam,
  glow, hot-part contact, or decorative object. The final frame shows the
  reservoir water meeting a qualitative molded ridge, the installed outlet
  pipe ending above rather than supporting the paper, the compact #1 holder
  fixed on a separate machine bracket, and the mug centered on a broad base.
- Review status: **pending brewer-expert safety and equipment review**. Confirm
  the exact Cup-One reservoir/column proportions, outlet-arm socket and
  clearance, holder bracket, #1 paper fit, water-level interpretation,
  machine-off state, mug clearance, and post-warning Learn/Live placement
  before manifest registration or approval.

### `p1-wave-185-rinse-imagegen-v1`

- Stable asset ID:
  `instruction_p1_wave185_ozone_25_400_stage_01_instruction_default`
- Exact scope: `wave185_ozone_25_400` / source `stage_01` /
  `p1_wave185_ozone_25_400_stage_01_instruction`; gently rinsing the correct
  185 paper while its flat bottom stays level and its pleats remain open.
- Evidence: `SRC-KALITA-OZONE`. Reverified on 28 July 2026 against Ozone
  Coffee's current Kalita Wave guide at
  <https://ozonecoffee.co.uk/pages/kalita-wave-brew-guide>, which continues to
  specify the Wave 185, its flat-bottom dripper, its required crimped paper,
  and a thorough hot-water rinse before coffee is added.
- Generation mode: new bitmap generation with the built-in image generator;
  no corrective edit was needed before pending-review delivery.
- Generated: 28 July 2026.
- Final prompt:

  > Create one original, clean, text-free 4:3 instructional illustration for
  > a mobile coffee-brewing guide, composed to remain legible at roughly 320
  > dp wide. Use an elevated close three-quarter view of an unbranded
  > stainless-steel Kalita Wave 185-style flat-bottom dripper centered securely
  > on a broad stable heat-safe glass server. Show the correct large white
  > Wave 185 paper centered inside: a visibly level circular flat bottom, tall
  > evenly spaced accordion/crimp pleats all around, and every pleat remaining
  > open with a small air space from the metal wall rather than flattened
  > against it. A plain gooseneck kettle enters only from an upper corner and
  > releases one narrow gentle stream onto the inner paper wall, moving no
  > paper. The paper is visibly wet where rinsed, while a light clear drainage
  > stream leaves the flat three-hole base into the server. Keep the kettle
  > spout and any hand well clear of the paper. Make the Wave 185's broad
  > flat-bottom geometry, open crimped pleats, centered level paper, gentle
  > wetting, and drainage unmistakable in one crop-safe frame. Use a
  > warm-neutral uncluttered counter and background, semi-flat softly
  > dimensional educational rendering, precise believable geometry, clear
  > silhouettes, restrained materials, soft natural light, and subtle shadows.
  > Show no coffee grounds. Do not depict a V60 cone paper, wedge paper,
  > generic basket paper, Wave 155 proportions, metal reusable filter,
  > flattened or crushed pleats, folded-over rim, pleats glued to the walls,
  > finger pressure, kettle touching the filter, tilted or floating paper,
  > off-center flat bottom, unstable server, overflow, excessive spray, second
  > dripper, text, letters, numbers, labels, logos, arrows, callouts, diagrams,
  > multiple panels, borders, or decorative clutter.
- Generated master:
  `C:\Users\adam-\.codex\generated_images\019fa48e-1d5d-7560-b0de-f39fa2e8b914\call_TD1v5gzxfWFh902wMOgXZTIt.png`
  (opaque RGB PNG, 1448 × 1086 px, 2,184,148 bytes, exact 4:3).
- Final drawable:
  `app/src/main/res/drawable-nodpi/instruction_p1_wave185_ozone_25_400_stage_01_instruction_default.webp`
- Delivery: opaque RGB WebP, 1024 × 768 px, 85,670 bytes, exact 4:3.
- Accessibility target: “A gentle kettle stream rinses a centered Wave 185
  paper while its flat bottom stays level and the wet pleats remain open.” The
  exact-stage alt-text resource and asset record remain intentionally
  unregistered until localization and filter review are complete.
- Inspection: no text, numbers, logos, extra panel, grounds, touching, or
  secondary action. The close crop makes the level flat paper bottom and open
  wet accordion pleats dominant, preserves clear kettle clearance, and shows
  a light rinse-water drain into a broad stable server.
- Review status: **pending brewer-expert filter and geometry review**. Confirm
  the Wave 185 proportions, crimp count and spacing, paper-to-wall clearance,
  level flat-bottom seating, rinse-stream placement, drainage, and
  post-instruction Learn/Live placement before manifest registration or
  approval.

### `p1-cup-one-paper-and-outlet-imagegen-v2`

- Stable asset ID:
  `instruction_p1_auto_cupone_20_300_stage_01_instruction_default`
- Exact scope: `auto_cupone_20_300` / source `stage_01` /
  `p1_auto_cupone_20_300_stage_01_instruction`; one correctly seated No. 1
  cone paper and a visibly unobstructed model-specific drip hole while the
  brewer remains off.
- Evidence: `SRC-CUPONE-MANUAL`. Reverified on 28 July 2026 against the
  current official Cup-One quick guide at
  <https://support.moccamaster.com/hc/en-us/article_attachments/1500014620701>,
  the current Cup-One user manual at
  <https://www.moccamaster.eu/pub/media/handleidingen/talen/User_Manual_Cup-One.pdf>,
  and Moccamaster USA's current product page at
  <https://us.moccamaster.com/products/cup-one>. These sources continue to
  require one No. 1 cone paper and identify the small drip hole as a cleaning
  and overflow-prevention point.
- Generation mode: new bitmap generation followed by one targeted corrective
  image edit with the built-in image generator.
- Generated: 28 July 2026.
- Base prompt:

  > Create one original, clean, text-free 4:3 instructional illustration for
  > a mobile coffee-brewing guide. Use a close elevated three-quarter view of
  > the detached brew basket/filter holder from an accurate unbranded
  > Moccamaster Cup-One-style single-cup brewer. Show exactly one correctly
  > shaped No. 1 cone paper fully opened and seated smoothly inside the dry
  > holder. Angle the supported holder just enough that its model-specific
  > single tiny bottom drip hole is clearly visible and completely
  > unobstructed, while the paper seating remains easy to read. Keep the
  > switched-off brewer in the soft background with no illuminated control,
  > hot liquid, or steam. Show one calm hand supporting only the cool holder
  > without covering the paper rim or outlet. Make the seated paper and clear
  > tiny hole the only visual story. Use a warm-neutral uncluttered counter and
  > background, semi-flat softly dimensional educational rendering, clear
  > silhouettes, subtle shadows, and crop-safe mobile-readable framing with
  > generous breathing room. Include no text, letters, numbers, labels, logos,
  > brand marks, arrows, callouts, panels, measurement marks, decorative
  > objects, grounds, water, active brewing, steam, overflow, glowing controls,
  > extra paper filters, pods, reusable filters, sink, carafe, second holder,
  > cutaway, magnified inset, or exploded view. Do not block, fill, cover, omit,
  > or enlarge the tiny bottom hole and do not invent multiple holes. Do not
  > place the paper outside the holder or hide the outlet with the hand. The
  > final image must be exactly one coherent scene, opaque, 1024 x 768, and
  > readable at mobile size.
- Corrective edit prompt:

  > Correct the existing illustration while preserving its clean warm-neutral
  > 4:3 educational style and single coherent scene. The current white paper
  > is wrong: it looks like a round fluted basket filter and exposes an
  > impossible black floor through its bottom. Replace the holder with the
  > accurate compact Cup-One-style cone brew basket geometry and replace the
  > paper with exactly one small No. 1 cone paper: smooth folded paper with a
  > clear conical/wedge profile and seam, fully opened and seated against the
  > basket walls, not round, not scalloped, not pleated, not a flat-bottom
  > basket filter. Keep the paper completely intact with no cutout or opening
  > through its bottom. Tilt the detached holder in the supporting hand so both
  > the seated paper above and the exterior underside of the holder below are
  > legible; show the model-specific single tiny drip hole on the exterior
  > bottom outlet, visibly clear and unobstructed. The hole must be outside and
  > below the intact paper, not drawn as a large round plate inside the paper.
  > Keep the supporting fingers away from both the paper rim and the outlet.
  > Keep the unbranded Cup-One-style brewer switched off in the soft
  > background, with no lit control, water, grounds, steam, pod, carafe, extra
  > filter, text, letters, numbers, logo, arrow, inset, split view, or clutter.
  > Make the small No. 1 cone paper and one clear exterior drip hole the only
  > visual story. Preserve opaque 1024 x 768 output and mobile-readable
  > crop-safe framing.
- Rejected draft: the initial render substituted a round pleated basket paper
  and exposed a large black inner floor through an impossible opening in the
  paper. It therefore taught the wrong filter type and geometry. That draft
  was not copied into the repository or manifest.
- Corrected generated master:
  `C:\Users\adam-\.codex\generated_images\019fa48e-1d5d-7560-b0de-f39fa2e8b914\call_UuGUUXQWn2hWJ6HIXEyPb8aD.png`
  (opaque RGB PNG, 1448 x 1086 px, exact 4:3).
- Final drawable:
  `app/src/main/res/drawable-nodpi/instruction_p1_auto_cupone_20_300_stage_01_instruction_default.webp`
- Delivery: opaque RGB WebP, 1024 x 768 px, 42,348 bytes, exact 4:3.
- Accessibility target: “A single No. 1 cone paper is seated in the dry
  Cup-One holder while its tiny exterior drip hole remains visibly clear.”
  The canonical exact-stage alt text and asset record remain intentionally
  unregistered until localization and equipment review are complete.
- Inspection: no text, numbers, logos, extra panel, grounds, liquid, steam,
  glow, active brewing, or decorative object. The final frame shows one intact
  smooth folded cone paper, one clear exterior bottom outlet, an unobstructed
  hand position, and a switched-off brewer in the background.
- Review status: **pending brewer-expert safety and equipment review**. Confirm
  compact Cup-One holder proportions, exact No. 1 paper fit and seam, exterior
  outlet location and size, machine-off context, hand clearance, mobile-size
  readability, and post-warning Learn/Live placement before manifest
  registration or approval.

### `p1-cup-one-unplugged-outlet-cleaning-imagegen-v2`

- Stable asset ID:
  `instruction_p1_auto_cupone_20_300_stage_06_instruction_default`
- Exact scope: `auto_cupone_20_300` / source `stage_06` /
  `p1_auto_cupone_20_300_stage_06_instruction`; the cooled detached holder is
  empty and brushed through its tiny outlet while the brewer is unplugged and
  kept completely dry.
- Evidence: `SRC-CUPONE-MANUAL`. Reverified on 28 July 2026 against the
  current official Cup-One quick guide at
  <https://support.moccamaster.com/hc/en-us/article_attachments/1500014620701>
  and user manual at
  <https://www.moccamaster.eu/pub/media/handleidingen/talen/User_Manual_Cup-One.pdf>.
  The current guidance requires cleaning the brew basket with a mild detergent
  and a supplied-style tool through the drip hole to prevent overflow; its
  electrical safeguards require unplugging and cooling before cleaning and
  prohibit immersing the cord, plug, or brewer.
- Generation mode: new bitmap generation followed by one targeted corrective
  image edit with the built-in image generator.
- Generated: 28 July 2026.
- Base prompt:

  > Create one original, clean, text-free 4:3 instructional illustration for
  > a mobile coffee-brewing guide. Show an accurate unbranded Moccamaster
  > Cup-One-style single-cup brewer switched off on a completely dry counter,
  > with its power cord and plug clearly disconnected from the wall and the
  > loose plug resting visibly beside the machine, far from water. In the
  > foreground, show the cooled detached compact Cup-One cone brew
  > basket/filter holder completely empty of paper, coffee grounds, and
  > residue. One calm hand supports only the cool holder while the other gently
  > passes a slim plain supplied-style cleaning tool straight through the
  > model-specific single tiny bottom drip hole so the open tool path is
  > unmistakable. Use the same credible compact holder geometry: narrow
  > cone/wedge interior, small exterior bottom outlet, no large round batch
  > basket. Keep the holder, slim tool path, disconnected plug, and entirely
  > dry cleaning context legible in one coherent elevated three-quarter view.
  > Use a warm-neutral uncluttered counter and background, semi-flat softly
  > dimensional educational rendering, clear silhouettes, restrained
  > materials, subtle shadows, generous crop-safe breathing room, and
  > mobile-readable framing. Include no text, letters, numbers, labels, logos,
  > brand marks, arrows, callouts, panels, sink, basin, bucket, water, rinse,
  > spray, immersion, dripping, wet counter, detergent foam, paper, grounds,
  > active brewing, steam, glowing control, fingers near live electrical parts,
  > bottle brush, knife, drill, carafe, second machine, exploded view, or
  > decorative objects. Do not show the plug in an outlet. Do not show the tool
  > touching any electrical component. The final image must be one opaque
  > 1024 x 768 scene whose only story is safe unplugged dry outlet cleaning.
- Corrective edit prompt:

  > Correct the immediately previous Cup-One dry-cleaning illustration while
  > preserving its 4:3 composition, dry counter, clearly disconnected loose
  > plug, switched-off unlit machine, two calm hands, slim cleaning tool,
  > warm-neutral educational style, and absence of water or residue. Remove the
  > second black brew basket/filter holder currently attached beneath the
  > machine outlet; the model must have only one holder in the entire scene,
  > the detached compact holder in the hands. Leave only an empty
  > model-integrated support bracket and the separate metal outlet arm on the
  > machine, with no duplicate cone, basket, or chamber. Correct the detached
  > holder's bottom geometry: replace the oversized round nozzle and gaping
  > opening with one small model-specific exterior bottom drip outlet
  > containing a single tiny hole only slightly wider than the slim cleaning
  > tool. The tool must pass straight through that tiny hole and emerge just a
  > short visible distance below it, proving the path is clear; no large
  > collar, pipe, funnel, or invented second hole. Keep the compact No. 1
  > cone/wedge holder completely empty and dry, with no paper, grounds, liquid,
  > or residue. Keep the unplugged cord and plug clearly visible and separated
  > from any wall socket or water. Do not add text, numbers, logos, arrows,
  > panels, sink, basin, spray, immersion, wet surface, foam, active brewing,
  > steam, glow, carafe, second holder, or decorative clutter. The only story
  > must be safe unplugged dry cleaning of one detached holder's single tiny
  > drip hole. Preserve opaque 1024 x 768 output and mobile-readable crop-safe
  > framing.
- Rejected draft: the initial render correctly showed a loose plug and dry
  tool path, but retained a second holder on the machine and enlarged the
  detached holder's drip hole into a broad nozzle. Those contradictions made
  the model-specific maintenance state ambiguous. The draft was not copied
  into the repository or manifest.
- Corrected generated master:
  `C:\Users\adam-\.codex\generated_images\019fa48e-1d5d-7560-b0de-f39fa2e8b914\call_4IeUwQLJ1CINNXJQvWnk8YZy.png`
  (opaque RGB PNG, 1448 x 1086 px, exact 4:3).
- Final drawable:
  `app/src/main/res/drawable-nodpi/instruction_p1_auto_cupone_20_300_stage_06_instruction_default.webp`
- Delivery: opaque RGB WebP, 1024 x 768 px, 49,834 bytes, exact 4:3.
- Accessibility target: “A slim tool clears the empty Cup-One holder's tiny
  drip hole on a dry counter beside the visibly unplugged brewer.” The
  canonical exact-stage alt text and asset record remain intentionally
  unregistered until localization and equipment review are complete.
- Inspection: no text, numbers, logos, paper, grounds, liquid, wet surface,
  steam, glow, plugged-in connection, immersion, duplicate holder, or
  decorative object. The final frame shows the loose plug, one detached empty
  holder, and a slim tool traversing one small exterior outlet.
- Review status: **pending brewer-expert electrical-safety and equipment
  review**. Confirm Cup-One silhouette and empty bracket, holder proportions,
  outlet size and tool path, credible cooled dry state, unmistakably unplugged
  context, mobile-size readability, and post-warning Learn/Live placement
  before manifest registration or approval.

### `p1-switch-ole-boen-retained-pour-imagegen-v1`

- Stable asset ID:
  `instruction_p1_switch_ole_boen_hybrid_16_5_240_stage_03_instruction_default`
- Exact scope: `switch_ole_boen_hybrid_16_5_240` / source `stage_03` /
  `p1_switch_ole_boen_hybrid_16_5_240_stage_03_instruction`; the Switch 02
  valve is closed before the final pour and the added water remains retained
  with no drawdown.
- Evidence: canonical `SRC-KURASU-SWITCH` plus `SRC-HARIO-SWITCH` for hardware
  geometry. Reverified on 28 July 2026 against HARIO Europe's current Switch
  product page at
  <https://www.hario-europe.com/products/v60-immersion-dripper-switch> and its
  current Ole Kristian Bøen recipe at
  <https://www.hario-europe.com/blogs/hario-community/ole-kristian-boens-switch-recipe>.
  The current pages corroborate the V60 02 paper, silicone base,
  stainless-steel ball valve, closing before the final circular pour, and a
  retained phase before later release.
- Generation mode: new bitmap generation with the built-in image generator;
  no corrective edit was needed before pending-review delivery.
- Generated: 28 July 2026.
- Final prompt:

  > Create one original, clean, text-free 4:3 instructional illustration for
  > a mobile coffee-brewing guide. Use a close elevated three-quarter view of
  > an accurate unbranded Hario Switch 02-style brewer centered over a stable
  > heat-safe glass server on a coffee scale whose display is blank or fully
  > out of frame. Show the handleless ribbed clear-glass V60 02 cone, one
  > correctly seated V60 02 paper, a dark silicone base, the real side lever,
  > and the stainless-steel ball valve visible through the central lower glass
  > and silicone opening. Put the lever in its physically credible closed
  > position and show the steel ball seated firmly over the outlet. A plain
  > gooseneck kettle pours one controlled narrow circular stream into the dark
  > coffee slurry while liquid is visibly retained above the closed valve.
  > Show absolutely no stream, drip, or liquid below the brewer and keep the
  > server beneath empty or nearly empty from this retained phase. Keep one
  > safe kettle hand away from hot glass, metal, and slurry. Make the closed
  > lever-and-ball mechanism, retained liquid, circular pour, and absence of
  > drawdown the only visual story. Use a warm-neutral uncluttered counter and
  > background, semi-flat softly dimensional educational rendering, precise
  > believable mechanism geometry, clear silhouettes, restrained materials,
  > subtle shadows, generous crop-safe breathing room, and mobile-readable
  > framing. Include no text, letters, numbers, scale reading, measurement
  > ticks, thermometer, labels, logos, brand marks, arrows, callouts, panels,
  > cutaway, inset, decorative objects, Clever actuator, tap, clamp, glass
  > handle, generic V60-only base, stirring, serving, overflow, splash, or
  > hands touching hot parts. Do not omit or misalign the lever, steel ball,
  > silicone base, V60 02 cone, or seated paper. Do not show the ball lifted,
  > valve open, outlet open, drainage, or stream below. The final output must
  > be one opaque 1024 x 768 scene.
- Generated master:
  `C:\Users\adam-\.codex\generated_images\019fa48e-1d5d-7560-b0de-f39fa2e8b914\call_iF8m72QHN1Dn8OiaLeVsrGKK.png`
  (opaque RGB PNG, 1448 x 1086 px, exact 4:3).
- Final drawable:
  `app/src/main/res/drawable-nodpi/instruction_p1_switch_ole_boen_hybrid_16_5_240_stage_03_instruction_default.webp`
- Delivery: opaque RGB WebP, 1024 x 768 px, 75,956 bytes, exact 4:3.
- Accessibility target: “A controlled final pour enters the Switch while its
  steel ball stays seated, the liquid remains retained, and nothing drains
  into the server.” The canonical exact-stage alt text and asset record remain
  intentionally unregistered until localization and mechanism review are
  complete.
- Inspection: no text, numbers, logos, scale reading, thermometer, arrows,
  secondary panel, drain stream, hot-part contact, or decorative object. The
  final frame makes the full retained slurry and empty server dominant while
  exposing the seated ball and adjacent lever in one view.
- Review status: **pending brewer-expert mechanism review**. Confirm exact
  Switch 02 proportions, V60 02 paper fit, credible closed lever direction,
  seated ball and sealed outlet, absence of drawdown, safe kettle clearance,
  and mobile-size legibility before manifest registration or approval.

### `p1-switch-gravity-open-rinse-imagegen-v2`

- Stable asset ID:
  `instruction_p1_switch_gravity_15_250_stage_01_instruction_default`
- Exact scope: `switch_gravity_15_250` / source `stage_01` /
  `p1_switch_gravity_15_250_stage_01_instruction`; clean V60 02 paper is
  rinsed while the Switch 02 valve remains open and rinse water drains freely.
- Evidence: `SRC-HARIO-SWITCH` and `SRC-HARIO-V60-OFFICIAL`. Reverified on
  28 July 2026 against HARIO Europe's current Switch product page at
  <https://www.hario-europe.com/products/v60-immersion-dripper-switch> and
  Hario UK's current V60 guide at
  <https://www.hario.co.uk/pages/brew-guides-v60-intermediate>. The current
  sources corroborate the V60 02 paper, silicone base, ball-valve release
  mechanism, full-paper rinse, and complete drainage before adding coffee.
- Generation mode: new bitmap generation followed by one targeted corrective
  image edit with the built-in image generator.
- Generated: 28 July 2026.
- Base prompt:

  > Create one original, clean, text-free 4:3 instructional illustration for
  > a mobile coffee-brewing guide. Use a close elevated three-quarter view of
  > an accurate unbranded Hario Switch 02-style brewer centered over a stable
  > heat-safe glass waste server. Show the handleless ribbed clear-glass V60
  > 02 cone, exactly one correctly seated clean white V60 02 paper, a dark
  > silicone base, the real side lever, and the stainless-steel ball valve
  > visible through the central lower mechanism. Keep the lever in its
  > physically credible open position throughout and show the steel ball
  > lifted clearly away from the outlet. A plain gooseneck kettle sends one
  > gentle narrow rinse stream around the empty paper wall while clear rinse
  > water drains freely in one visible vertical stream into the server. Show no
  > retained pool above the valve. Keep the kettle hand safely away from hot
  > glass and metal. Make the open lever-and-ball geometry, wet clean paper,
  > and free clear drainage the only visual story. Use a warm-neutral
  > uncluttered counter and background, semi-flat softly dimensional
  > educational rendering, precise believable mechanism geometry, clear
  > silhouettes, restrained materials, subtle shadows, generous crop-safe
  > breathing room, and mobile-readable framing. Include no text, letters,
  > numbers, measurement marks, labels, logos, brand marks, arrows, callouts,
  > panels, cutaway, inset, decorative objects, coffee grounds, coffee-colored
  > liquid, slurry, bloom, closed lever, seated or blocking ball, retained
  > water, absent flow, Clever actuator, tap, generic V60-only base, glass
  > handle, other filter type, hand moving the lever, hot-part contact,
  > overflow, splash, or serving action. Do not omit or misalign the Switch
  > lever, steel ball, silicone base, V60 02 cone, or seated paper. The final
  > output must be one opaque 1024 x 768 scene with free drainage plainly
  > visible.
- Corrective edit prompt:

  > Correct the immediately previous Switch 02 rinse illustration while
  > preserving its clean white V60 02 paper, clear rinse stream from the
  > kettle, continuous clear drain stream into the stable glass waste server,
  > warm-neutral 4:3 educational style, text-free composition, and every other
  > clean detail. The valve state is currently ambiguous because the steel ball
  > reads as seated. Make the open mechanism physically explicit: move the
  > outer end of the real side switch/lever into its depressed release position
  > and show its linkage lifting the stainless-steel ball upward, with a clearly
  > visible air gap between the bottom of the ball and the central outlet seat.
  > The ball must float above rather than touch or plug the hole. Align the
  > uninterrupted clear drainage stream directly beneath that now-open central
  > outlet. Keep no retained water pool above the valve. Preserve the
  > handleless ribbed glass V60 02 cone, dark silicone base, single clean paper,
  > no grounds, no coffee-colored liquid, and no hand touching the switch. Do
  > not add arrows, cutaway panels, labels, text, numbers, logos, extra
  > mechanisms, Clever actuator, closed lever, seated ball, blocked outlet,
  > absent flow, clutter, or decorative objects. The only story must be the
  > Switch held open throughout a clean-paper rinse, with ball visibly raised
  > and water draining freely. Preserve opaque 1024 x 768 output and crop-safe
  > mobile legibility.
- Rejected draft: the initial render clearly showed clean-paper rinsing and
  free drainage, but the steel ball still appeared seated against the outlet,
  contradicting the visible drain stream. It was not copied into the
  repository or manifest.
- Corrected generated master:
  `C:\Users\adam-\.codex\generated_images\019fa48e-1d5d-7560-b0de-f39fa2e8b914\call_fMYjfQjFxI2YUgkIteyM7zj2.png`
  (opaque RGB PNG, 1448 x 1086 px, exact 4:3).
- Final drawable:
  `app/src/main/res/drawable-nodpi/instruction_p1_switch_gravity_15_250_stage_01_instruction_default.webp`
- Delivery: opaque RGB WebP, 1024 x 768 px, 66,124 bytes, exact 4:3.
- Accessibility target: “Clear water rinses a clean V60 02 paper while the
  depressed Switch lever holds the steel ball above the outlet and water
  drains freely.” The canonical exact-stage alt text and asset record remain
  intentionally unregistered until localization and mechanism review are
  complete.
- Inspection: no text, numbers, logos, grounds, coffee-colored liquid,
  retained pool, secondary panel, hot-part contact, or clutter. The final
  frame shows the lever angled into release, the ball raised relative to the
  outlet, and a continuous clear stream entering the waste server.
- Review status: **pending brewer-expert mechanism review**. Confirm exact
  Switch 02 proportions, V60 02 paper fit, depressed/open lever direction,
  ball-to-seat gap, free-drain alignment, safe kettle clearance, and immediate
  distinction from the retained-phase asset before registration or approval.

### `p1-gravity-phin-stable-dry-bed-imagegen-v2`

- Stable asset ID:
  `instruction_p1_phin_gravity_14_118_stage_01_instruction_default`
- Exact scope: `phin_gravity_14_118` / source `stage_01` /
  `p1_phin_gravity_14_118_stage_01_instruction`; a loose-disc gravity phin is
  fully supported on a broad cup with a level dry bed, while its unthreaded
  press remains separate for the next stage.
- Evidence: `SRC-NGUYEN-PHIN`. Reverified on 28 July 2026 against Nguyen
  Coffee Supply's current traditional phin guide at
  <https://nguyencoffeesupply.com/blogs/vietnamese-coffee-brew-guide/traditional-vietnamese-drip-phin>
  and current construction guide at
  <https://nguyencoffeesupply.com/blogs/news/what-is-the-vietnamese-phin-filter>.
  The current guidance corroborates placing the filter plate and chamber on a
  stable glass, adding and leveling the dry coffee, and using a loose gravity
  press rather than a screw mechanism.
- Generation mode: new bitmap generation followed by one targeted corrective
  image edit with the built-in image generator.
- Generated: 28 July 2026.
- Base prompt:

  > Create one original, clean, text-free 4:3 instructional illustration for
  > a mobile coffee-brewing guide. Use an elevated three-quarter close view of
  > an accurate unbranded single-serving Vietnamese gravity-insert phin with a
  > perforated cylindrical metal chamber and wide integrated filter plate.
  > Center the phin securely on a broad, stable, heat-safe ceramic cup whose
  > rim fully supports the filter plate continuously all the way around; make
  > the cup base, centered overlap, and level chamber unmistakable. Show a
  > level dry coffee bed inside the chamber after grounds have just been added,
  > with a flat even surface, no compression, no loose grounds on the rim, and
  > the lower perforations unobstructed. Place the matching loose perforated
  > gravity press disc flat and clearly separate on the phin's metal lid beside
  > the cup, ready for the next stage, with no screw post, threads, spring, or
  > plunger. Keep the cup, full rim support, level chamber, level dry bed, clear
  > perforated gravity mechanism, separate loose disc, and lid legible in one
  > coherent view. Use a warm-neutral uncluttered counter and background,
  > semi-flat softly dimensional educational rendering, precise believable
  > metal geometry, clear silhouettes, restrained materials, subtle shadows,
  > generous crop-safe breathing room, and mobile-readable framing. Include no
  > text, letters, numbers, readable scale, measurement marks, labels, logos,
  > brand marks, arrows, callouts, panels, decorative objects, water, kettle,
  > steam, drips, milk, condensed milk, ice, serving action, paper filter,
  > espresso basket, moka pot, screw post, threaded insert, spring, plunger,
  > installed press, uneven mound, compressed bed, wet bed, overflow, spilled
  > grounds, undersized cup, tilted cup, unstable support, or blocked
  > perforations. Do not leave any part of the filter plate unsupported or
  > off-center. The final output must be one opaque 1024 x 768 scene whose only
  > story is a stable gravity-phin setup with a level dry bed and separate loose
  > press.
- Corrective edit prompt:

  > Correct the immediately previous gravity-insert phin setup illustration
  > while preserving its 4:3 framing, broad stable ceramic cup, wide metal
  > filter plate fully centered on and supported by the cup rim, separate loose
  > perforated press disc resting on the lid, warm-neutral educational style,
  > text-free simplicity, and every other clean detail. Remove every invented
  > perforation from the vertical cylindrical chamber wall; the chamber sides
  > must be continuous solid brushed metal with no holes, slots, mesh, or
  > window. The phin's brewing perforations belong only to its flat internal
  > bottom and to the separate loose press disc. Because the dry grounds cover
  > the internal bottom, do not fake visible side holes or a transparent
  > cutaway; a subtle glimpse of the flat perforated bottom may appear only at
  > the inner edge if physically plausible. Lower the dry coffee bed
  > substantially so it sits level around the lower third to half of the
  > chamber, leaving obvious empty headroom above it for the loose press and
  > later bloom. Keep the bed flat, dry, loose, and uncompressed, with no mound
  > or grounds on the rim. Keep the loose press disc clearly unthreaded and
  > separate on the matching lid; no screw post, spring, handle, or installed
  > press. Preserve continuous full cup-rim support, centered level geometry,
  > no water, kettle, steam, milk, numbers, text, logo, arrow, panel, or
  > clutter. The only visual story must be a stable gravity phin with a level
  > dry bed and a separate loose press. Preserve opaque 1024 x 768 output and
  > crop-safe mobile legibility.
- Rejected draft: the initial render invented rows of perforations through the
  chamber's vertical wall and filled the dry bed nearly to the rim. Those
  details contradicted loose-disc phin geometry and removed necessary
  headroom. The draft was not copied into the repository or manifest.
- Corrected generated master:
  `C:\Users\adam-\.codex\generated_images\019fa48e-1d5d-7560-b0de-f39fa2e8b914\call_q5h48fQffPtFe3MCFKkLa0cX.png`
  (opaque RGB PNG, 1448 x 1086 px, exact 4:3).
- Final drawable:
  `app/src/main/res/drawable-nodpi/instruction_p1_phin_gravity_14_118_stage_01_instruction_default.webp`
- Delivery: opaque RGB WebP, 1024 x 768 px, 63,106 bytes, exact 4:3.
- Accessibility target: “A centered gravity phin rests fully on a broad cup
  with a level dry bed and its loose perforated press separate on the lid.”
  The canonical exact-stage alt text and asset record remain intentionally
  unregistered until localization and phin-mechanism review are complete.
- Inspection: no text, numbers, logos, water, steam, service additions, screw
  mechanism, installed press, sidewall perforations, unstable support, or
  clutter. The final frame shows a smooth solid chamber, a lower level dry bed
  with clear headroom, continuous filter-plate support, and an unthreaded loose
  disc on the matching lid.
- Review status: **pending brewer-expert phin-mechanism and stability review**.
  Confirm gravity-insert proportions, solid chamber wall and bottom-perforation
  interpretation, loose-disc identity, cup-rim overlap, level dry-bed depth,
  mobile-size stability cues, and warning-before-image placement before
  registration or approval.

### `p1-gravity-phin-hot-removal-imagegen-v1`

- Stable asset ID:
  `instruction_p1_phin_gravity_14_118_stage_07_instruction_default`
- Exact scope: `phin_gravity_14_118` / source `stage_07` /
  `p1_phin_gravity_14_118_stage_07_instruction`; a dry insulated grip moves
  the drained hot gravity phin from the finished cup onto its matching inverted
  lid used as a stable heat-safe coaster.
- Evidence: `SRC-NGUYEN-PHIN`. Reverified on 28 July 2026 against Nguyen
  Coffee Supply's current traditional phin guide at
  <https://nguyencoffeesupply.com/blogs/vietnamese-coffee-brew-guide/traditional-vietnamese-drip-phin>
  and current construction guide at
  <https://nguyencoffeesupply.com/blogs/news/what-is-the-vietnamese-phin-filter>.
  The current sources continue to identify the loose gravity press, hot metal
  brewer, and use of the lid as a post-brew coaster while leaving black, iced,
  milk, and condensed-milk service as separate choices.
- Generation mode: new bitmap generation with the built-in image generator;
  no corrective edit was needed before pending-review delivery.
- Generated: 28 July 2026.
- Final prompt:

  > Create one original, clean, text-free 4:3 instructional illustration for
  > a mobile coffee-brewing guide. Show an accurate unbranded single-serving
  > Vietnamese gravity-insert phin immediately after drainage beside a broad
  > stable heat-safe ceramic cup containing finished black coffee concentrate.
  > Use a close elevated three-quarter view. A calm hand protected by a small
  > dry folded heat-resistant cloth grips the hot solid-walled metal chamber
  > securely, with no bare skin touching the chamber, loose insert, wide filter
  > plate, underside, or lid. Show the drained phin moving only a very short
  > controlled distance away from the cup and just above its own matching metal
  > lid, which is inverted with its shallow concave drip-catching side facing
  > upward and resting flat and stable on the counter as a heat-safe coaster.
  > Keep the loose perforated gravity press safely contained inside the drained
  > chamber, with no screw post or threaded mechanism. Make the dry insulated
  > grip, hot phin, stable cup, short movement, and correctly oriented resting
  > lid immediately legible. Keep the black concentrate untouched so the image
  > does not privilege milk, ice, or dilution. Use a warm-neutral uncluttered
  > counter and background, semi-flat softly dimensional educational
  > rendering, precise believable metal geometry, clear silhouettes,
  > restrained materials, subtle shadows, generous crop-safe breathing room,
  > and mobile-readable framing. Include no text, letters, numbers,
  > measurement marks, labels, logos, brand marks, arrows, callouts, panels,
  > warning icon, symbolic steam, decorative objects, bare fingers on hot
  > metal, wet cloth, tongs, implausible handle, long carry, tipping, spill,
  > phin remaining on the cup as the final state, phin directly on bare
  > counter, screw post, threaded insert, pressure mechanism, paper filter,
  > moka pot, espresso basket, multiple serving options, milk, condensed-milk
  > jar, ice, dilution water, or tasting scene. The final output must be one
  > opaque 1024 x 768 scene whose only story is safe hot-phin removal onto its
  > lid-coaster.
- Generated master:
  `C:\Users\adam-\.codex\generated_images\019fa48e-1d5d-7560-b0de-f39fa2e8b914\call_MUwtpKjWLJarU57AH8yLolOb.png`
  (opaque RGB PNG, 1448 x 1086 px, exact 4:3).
- Final drawable:
  `app/src/main/res/drawable-nodpi/instruction_p1_phin_gravity_14_118_stage_07_instruction_default.webp`
- Delivery: opaque RGB WebP, 1024 x 768 px, 110,596 bytes, exact 4:3.
- Accessibility target: “A dry folded cloth protects the hand while the
  drained hot gravity phin is moved from the black coffee cup onto its inverted
  lid-coaster.” The canonical exact-stage alt text and asset record remain
  intentionally unregistered until localization and phin-mechanism review are
  complete.
- Inspection: no text, numbers, logos, arrows, service additions, spill,
  long carry, bare hot-metal contact, wet cloth, screw mechanism, or clutter.
  The final frame shows an insulated side grip, contained loose press, stable
  black-coffee cup, short placement path, and concave-side-up matching lid.
- Review status: **pending brewer-expert hot-handling and phin-mechanism
  review**. Confirm gravity-insert proportions, safe cloth grip, containment of
  the loose press, filter-plate clearance, lid orientation and stability,
  neutral black service, mobile-size legibility, and warning-before-image
  placement before registration or approval.

### `p1-cup-one-hands-off-cycle-imagegen-v3`

- Stable asset ID:
  `instruction_p1_auto_cupone_20_300_stage_04_instruction_default`
- Exact scope: `auto_cupone_20_300` / source `stage_04` /
  `p1_auto_cupone_20_300_stage_04_instruction`; a correctly assembled
  Cup-One-style brewer runs its automatic transfer hands-free, with clear hot
  water entering the compact paper holder and dark brewed coffee draining into
  a stable mug.
- Evidence: `SRC-CUPONE-MANUAL`. Reverified on 28 July 2026 against the current
  Cup-One quick guide at
  <https://support.moccamaster.com/hc/en-us/article_attachments/1500014620701>
  and user manual at
  <https://www.moccamaster.eu/pub/media/handleidingen/talen/User_Manual_Cup-One.pdf>.
  The sources corroborate the installed outlet arm, supported No. 1 paper
  holder, closed reservoir, hands-off automatic transfer, hot-water caution,
  and keeping the holder and mug seated during brewing. Timing and completion
  language remain in Compose text rather than being encoded in the image.
- Generation mode: new bitmap generation followed by two targeted corrective
  image edits with the built-in image generator.
- Generated: 28 July 2026.
- Base prompt:

  > Create one original, clean, text-free 4:3 instructional illustration for a
  > mobile coffee-brewing guide. Show an accurate unbranded Moccamaster
  > Cup-One-style single-cup brewer during a normal active automatic cycle,
  > viewed from an elevated front three-quarter angle. Preserve the distinctive
  > compact geometry: tall rectangular heating column and clear single-cup
  > reservoir with its lid closed, a polished model-specific metal outlet arm
  > fully installed, supported independently by the machine and centered with
  > a small air gap above the compact Cup-One No. 1 cone paper holder. Show
  > exactly one correctly seated small No. 1 paper with its coffee safely
  > contained inside the holder. Keep a broad heat-safe ceramic mug centered
  > and fully supported beneath the holder. Show the plain unmarked power
  > control in its physically credible on position with only a subtle non-text
  > indicator glow, and show one calm narrow coffee stream or a few aligned
  > drops entering the mug with no overflow, splash, or steam cloud. Leave
  > generous empty space around the machine and show no hands at all: the only
  > action is the machine completing its automatic transfer while every
  > component stays seated. Use a warm-neutral uncluttered counter and
  > background, semi-flat softly dimensional educational rendering, precise
  > believable Cup-One geometry, clear silhouettes, restrained materials,
  > soft light, subtle shadows, and crop-safe mobile-readable framing. Include
  > no text, letters, numbers, clock, time display, temperature display,
  > measurement ticks, labels, logos, brand marks, arrows, callouts, panels,
  > warning icon, decorative objects, detached or misaligned pipe, removed
  > holder, wrong paper, pod, capsule, carafe, generic showerhead, batch basket,
  > narrow cup, unstable mug, second machine, overflow, grounds escaping,
  > blocked outlet, coffee missing the mug, hands, simultaneous on/off states,
  > or exact liquid level. The final output must be one opaque 1024 x 768 scene
  > whose only story is a stable hands-off automatic cycle.
- First corrective edit prompt:

  > Correct the immediately previous active Cup-One cycle illustration while
  > preserving its 4:3 framing, closed reservoir lid, stable broad mug, calm
  > coffee flow into the mug, subtle unmarked on-state indicator, no hands,
  > warm-neutral educational style, and every other safe hands-off detail.
  > Replace the oversized round V60-like holder and pleated basket paper with
  > the accurate compact Cup-One-specific brew basket assembly: a small narrow
  > black wedge/trapezoidal No. 1 cone paper holder with no side handle, mounted
  > level on a short rigid model-integrated black support bracket from the
  > machine column. Inside it show exactly one small smooth folded white No. 1
  > cone paper with two flat sides and a visible folded seam, fitted closely
  > without round flutes, scallops, accordion pleats, or V60 flare. Keep the
  > holder mechanically separate from the polished metal outlet arm. Shorten
  > and raise the outlet arm's downturned end so it stops just above the center
  > of the open No. 1 paper with a small visible air gap; the arm must not touch,
  > pierce, carry, or support the paper or holder. A restrained hot-water stream
  > may enter the paper from that outlet while one narrow coffee stream drains
  > from the compact holder into the centered mug, with no overflow or splash.
  > Keep the power indicator on and show no hands or component movement. Do not
  > add text, numbers, logos, arrows, panels, pod, capsule, carafe, round basket,
  > large cone, wrong filter, extra holder, removed lid, steam cloud, clutter,
  > or decorative objects. The only story must remain a stable hands-off
  > automatic Cup-One cycle with exact compact holder geometry. Preserve opaque
  > 1024 x 768 output and crop-safe mobile legibility.
- Final corrective edit prompt:

  > Make one final fluid-state correction to the immediately previous active
  > Cup-One illustration while preserving its exact compact supported No. 1
  > wedge holder, folded white paper, separate outlet arm with visible air gap,
  > closed reservoir, broad centered mug, subtle unmarked on indicator, no
  > hands, clean background, 4:3 framing, and every other corrected detail.
  > Change only the two-stage liquid path so it is physically unmistakable:
  > the narrow stream leaving the metal outlet arm and entering the paper must
  > be clear translucent hot water, not brown coffee. Inside the No. 1 paper,
  > show a modest dark wet coffee bed or shallow slurry safely below the paper
  > rim, with no overflow and no grounds escaping. The narrow stream leaving
  > the bottom of the holder and entering the mug must remain dark brewed
  > coffee. Keep the clear upper stream visually distinct from the dark lower
  > stream through material and context, without labels, arrows, color swatches,
  > split panels, or text. Do not change the support bracket, paper shape,
  > holder size, outlet gap, mug position, on state, or hands-off composition.
  > Add no steam cloud, numbers, logo, clutter, or extra objects. Preserve
  > opaque 1024 x 768 output and crop-safe mobile legibility.
- Rejected drafts: the initial render used an oversized round V60-like holder
  and pleated paper. The first correction fixed the compact No. 1 assembly but
  showed brown liquid leaving the hot-water outlet and made the paper appear
  empty. Neither draft was copied into the repository or manifest.
- Corrected generated master:
  `C:\Users\adam-\.codex\generated_images\019fa48e-1d5d-7560-b0de-f39fa2e8b914\call_03Sz71oZM2TNizqklM08OkFw.png`
  (opaque RGB PNG, 1448 x 1086 px, exact 4:3).
- Final drawable:
  `app/src/main/res/drawable-nodpi/instruction_p1_auto_cupone_20_300_stage_04_instruction_default.webp`
- Delivery: opaque RGB WebP, 1024 x 768 px, 42,120 bytes, exact 4:3.
- Accessibility target: “The Cup-One runs hands-free with clear water entering
  its supported No. 1 paper holder and dark coffee draining into a centered
  mug.” The canonical exact-stage alt text remains “Instructional view of
  switch on and let the automatic cycle run using the exact brewer profile and
  filter configuration stated in this recipe; machine switches off after
  water transfer.” The asset record remains intentionally unregistered until
  localization and brewer review are complete.
- Inspection: no text, numbers, logos, arrows, hands, removed components,
  unstable mug, overflow, pod, carafe, or clutter. The final frame shows the
  compact supported wedge holder, closely fitted folded paper, independent
  outlet arm and air gap, closed reservoir, on-state indicator, clear upper
  water stream, shallow contained slurry, and dark lower coffee stream.
- Review status: **pending brewer-expert Cup-One geometry and active-cycle
  review**. Confirm machine silhouette, integrated holder support, No. 1 paper
  fit, outlet clearance, clear-water/dark-coffee path, on-state legibility,
  stable mug, mobile-size readability, and warning-before-image placement
  before registration or approval.

### `p1-screw-phin-stable-dry-bed-imagegen-v1`

- Stable asset ID:
  `instruction_p1_phin_screw_18_120_stage_01_instruction_default`
- Exact scope: `phin_screw_18_120` / source `stage_01` /
  `p1_phin_screw_18_120_stage_01_instruction`; a screw-insert phin is centered
  on a broad stable cup with a low level dry bed around its central threaded
  post, while the matching threaded insert remains separate on the lid.
- Evidence: `SRC-TRUNGNGUYEN-PHIN` and `SRC-GOURMETKAVA-PHIN`. Reverified on
  28 July 2026 against the current Trung Nguyen brewing information at
  <https://trung-nguyen-coffee.co.uk/page_brewing.php> and GourmetKava's
  current traditional-phin guide at
  <https://www.gourmetkava.cz/en/blog/making-coffee/preparation-of--vietnamese-coffee>.
  These sources corroborate stable cup support, a level coffee bed, and the
  screw/press mechanism used gently rather than as pressure brewing. The exact
  source dose and later adjustment guidance remain in Compose text.
- Generation mode: new bitmap generation with the built-in image generator;
  no corrective edit was needed before pending-review delivery.
- Generated: 28 July 2026.
- Final prompt:

  > Create one original, clean, text-free 4:3 instructional illustration for a
  > mobile coffee-brewing guide. Show one coherent setup state from an elevated
  > front three-quarter close view: an accurate unbranded single-serving
  > Vietnamese screw-insert phin with a compact cylindrical brushed-metal
  > chamber, a smooth solid vertical sidewall, an integrated flat perforated
  > brewing bottom, a wide horizontal support plate, and one unmistakable
  > central threaded post rising vertically from the center. Center the phin
  > securely and level on a broad, low, stable heat-safe ceramic cup; the cup
  > rim must continuously support the phin plate all around with generous
  > overlap, and the cup base must rest fully on the counter. Inside the
  > chamber, show a modest level dry coffee bed around the central threaded
  > post, sitting in the lower third to half with ample empty headroom, no
  > mound, compression, moisture, or grounds on the rim. Place the matching
  > perforated screw-insert component separately on the phin's matching metal
  > lid beside the cup, ready for the next stage: it must be a flat round
  > perforated metal plate with a clearly visible raised central internally
  > threaded hub that visibly corresponds to the chamber's threaded post, yet
  > is not installed, not tightened, and not touching the coffee. Arrange the
  > separate insert at a slight readable angle on the lid so its threaded hub
  > and perforations are evident while the assembly remains physically
  > credible and uncluttered. Make screw-phin identity, broad cup stability,
  > continuous rim support, level chamber, low level dry bed, central threaded
  > post, and separate matching threaded insert instantly understandable at
  > mobile size without relying on text. Use a warm-neutral empty counter and
  > soft plain background, semi-flat softly dimensional educational rendering,
  > precise believable metal geometry, restrained warm gray and coffee-brown
  > palette, clear silhouettes, soft light, subtle shadows, generous crop-safe
  > breathing room, and no decorative objects. Include no text, letters,
  > numbers, readable scale, measurement marks, labels, logos, brand marks,
  > arrows, callouts, panels, warning symbols, cutaways, exploded diagram,
  > second brewer, narrow or unstable cup, unsupported plate, tilted chamber,
  > loose unthreaded gravity disc, spring, plunger, tamper, paper filter, moka
  > pot, espresso basket, sidewall holes, transparent wall, installed insert,
  > hand, force, water, kettle, steam, liquid, drips, wet bed, exact dose,
  > overflow, spilled grounds, or clutter. The final output must be one opaque
  > 1024 x 768 scene whose only story is a stable screw-insert phin with a level
  > dry bed and its matching threaded insert clearly separate for the next
  > step.
- Generated master:
  `C:\Users\adam-\.codex\generated_images\019fa48e-1d5d-7560-b0de-f39fa2e8b914\call_jTHuRAThIK0k1U0GEHNKVyJC.png`
  (opaque RGB PNG, 1448 x 1086 px, exact 4:3).
- Final drawable:
  `app/src/main/res/drawable-nodpi/instruction_p1_phin_screw_18_120_stage_01_instruction_default.webp`
- Delivery: opaque RGB WebP, 1024 x 768 px, 90,410 bytes, exact 4:3.
- Accessibility target: “A screw-insert phin rests level across a broad cup,
  with a low even dry bed around its threaded post and the matching threaded
  insert separate on the lid.” The canonical exact-stage alt text remains
  “Instructional view of stabilise the phin on the cup and add 18 g level
  coffee using the exact brewer profile and filter configuration stated in
  this recipe; bed level.” The asset record remains intentionally unregistered
  until localization and phin-mechanism review are complete.
- Inspection: no text, numbers, logos, arrows, water, steam, installed press,
  sidewall perforations, loose gravity disc, narrow cup, unsupported plate,
  unstable stance, exact quantity, or clutter. The final frame clearly shows a
  smooth solid chamber, external threads on the central post, a low level dry
  bed with headroom, broad continuous cup-rim support, and a separate
  perforated insert whose internally threaded hub visibly matches the post.
- Review status: **pending brewer-expert screw-phin mechanism and stability
  review**. Confirm 120–150 ml chamber proportions, integrated support plate,
  credible post/hub thread relationship, bottom-perforation interpretation,
  insert identity, cup-rim overlap, dry-bed depth, mobile-size legibility, and
  warning-before-image placement before registration or approval.

### `p1-screw-phin-undisturbed-drip-imagegen-v3`

- Stable asset ID:
  `instruction_p1_phin_screw_18_120_stage_05_instruction_default`
- Exact scope: `phin_screw_18_120` / source `stage_05` /
  `p1_phin_screw_18_120_stage_05_instruction`; a covered screw-insert phin
  stays fully supported and untouched while separated slow drops enter a broad
  transparent heat-safe cup.
- Evidence: `SRC-NGUYEN-PHIN` and `SRC-GOURMETKAVA-PHIN`. Reverified on
  28 July 2026 against Nguyen Coffee Supply's current stalled-flow guidance at
  <https://nguyencoffeesupply.com/blogs/news/why-wont-my-phin-filter-drip>
  and GourmetKava's current traditional-phin guide at
  <https://www.gourmetkava.cz/en/blog/making-coffee/preparation-of--vietnamese-coffee>.
  The sources corroborate slow gravity dripping, gentle screw/press handling,
  clear perforations, and avoiding force. Source-only timing windows and the
  exceptional stalled-flow decision remain in Compose text.
- Generation mode: new bitmap generation followed by two targeted corrective
  image edits with the built-in image generator.
- Generated: 28 July 2026.
- Base prompt:

  > Create one original, clean, text-free 4:3 instructional illustration for a
  > mobile coffee-brewing guide. Show one coherent safe observation state: an
  > accurate unbranded single-serving Vietnamese screw-insert phin midway
  > through normal gravity drainage, viewed from a close elevated front
  > three-quarter angle. Use a compact cylindrical brushed-metal chamber with a
  > smooth solid sidewall, an integrated wide horizontal support plate,
  > proportions matching a small 120–150 ml screw phin, and a matching metal
  > lid correctly seated flat on top. The threaded retaining insert is already
  > lightly engaged inside beneath the lid and remains entirely contained and
  > undisturbed; do not expose or manipulate it, and include no loose
  > gravity-disc cues. Center the wide phin plate level across a broad, stable,
  > heat-safe ceramic cup whose rim supports the plate continuously with
  > generous overlap; the cup base rests fully on an uncluttered counter. Keep
  > the phin outlet close above the cup interior with a safe short drop
  > distance. Show a short vertical sequence of exactly three visually
  > well-separated dark brewed-coffee droplets between the phin outlet and the
  > cup, making a slow active drip cadence unmistakable: discrete drops, not a
  > continuous stream and not a complete stall. The coffee in the cup should
  > be calm, with only a tiny restrained ripple below the falling drops, no
  > splash and no exact level emphasis. Leave all hands completely outside the
  > frame so the hot lidded phin is visibly untouched and patient observation
  > is the entire action. Make stable support, full cup-rim overlap, seated lid,
  > separated slow drops, heat-safe distance, and hands-off patience
  > immediately readable at mobile size. Use a warm-neutral empty counter and
  > plain soft background, semi-flat softly dimensional educational rendering,
  > precise believable metal geometry, restrained warm gray and dark coffee
  > palette, clear silhouettes, soft light, subtle shadows, generous crop-safe
  > breathing room, and no decorative objects. Include no text, letters,
  > numbers, clock, timer, measurement marks, labels, logos, brand marks,
  > arrows, callouts, panels, warning symbols, comparison, cutaway, inset, hand,
  > finger, tool, squeeze, press, twist, loosening, lifting, shaking, tapping,
  > tilting, removed lid, exposed insert, loose unthreaded disc, spring, plunger,
  > tamper, moka pot, espresso basket, paper filter, plastic dripper, sidewall
  > holes, narrow or unstable cup, unsupported or off-center plate, long drop
  > distance, continuous stream, spray, overflow, spill, no-flow stall, sealed
  > pressure, bulging lid, steam jet, exact beverage level, second brewer,
  > serving additions, or clutter. The final output must be one opaque
  > 1024 x 768 scene whose only story is a stable covered screw phin draining in
  > slow separated drops without intervention.
- First corrective edit prompt:

  > Correct the immediately previous screw-phin slow-drip illustration while
  > preserving its single lidded solid-wall metal chamber, broad stable ceramic
  > cup, hands-free composition, slow separated dark droplets, warm-neutral
  > clean educational style, 4:3 framing, and every other safe detail. Fix the
  > support geometry so the brewer cannot read as floating: raise and widen the
  > cup mouth so the entire top rim visibly meets and physically contacts the
  > underside of the phin's wide horizontal support plate, with clear contact
  > points on both left and right sides and generous continuous overlap around
  > the cup. There must be no air gap between the cup rim and support plate, no
  > hovering plate, no hidden stand, and no contact only at the central outlet.
  > Keep the phin level and centered, and keep the cup base fully planted on the
  > counter. Preserve a short open space only inside the cup, between the small
  > central drain outlet beneath the supported plate and the coffee surface,
  > where several clearly separated dark droplets fall vertically; do not make
  > a continuous stream and do not arrange the drops as a symbolic count. Lower
  > and de-emphasize the finished coffee surface so it is only a shallow calm
  > pool visible inside the cup, without suggesting an exact beverage level,
  > and retain only a tiny restrained ripple beneath the drip path. Keep the
  > seated metal lid, untouched hot chamber, no hands, no intervention, no
  > exposed insert, no sidewall holes, no steam, no splash, no text, no numbers,
  > no clock, no logo, no arrows, no panels, and no clutter. Do not change to a
  > narrow cup, add a handle, remove the lid, expose or manipulate the screw, or
  > introduce a loose gravity disc. The only visual story must be a fully
  > supported, centered, covered screw phin draining slowly into a stable cup
  > without intervention. Preserve opaque 1024 x 768 output and crop-safe
  > mobile legibility.
- Final corrective edit prompt:

  > Correct the immediately previous screw-phin slow-drip illustration again,
  > preserving the centered level lidded metal phin, solid sidewall, broad
  > support plate, no hands, slow natural dark droplets, warm-neutral text-free
  > educational style, 4:3 framing, and every other safe detail. The current
  > opaque ceramic cup incorrectly looks cut open or transparent only in one
  > patch. Replace it with one physically coherent broad, stable, heat-safe
  > clear borosilicate glass cup whose entire wall is transparently rendered,
  > with a thick continuous circular rim and a wide planted base. The cup must
  > not have a cutout, window, missing wall, opaque front panel, or floating
  > liquid. Seat the phin's wide support plate directly on that glass rim: the
  > underside of the plate must visibly touch the rim at both left and right
  > edges with generous overlap and no air gap, so the full brewer weight is
  > clearly supported. Through the naturally transparent glass wall, show a
  > shallow calm pool of dark coffee at the bottom and only a few irregularly
  > spaced individual coffee droplets falling from the small central outlet
  > into it. Keep the droplets naturally varied and separated in a short active
  > sequence, not a perfectly regular bead chain, not a continuous stream, not
  > a symbolic count, and not a stall. Keep the outlet-to-liquid distance
  > modest, the glass stable, and the coffee surface understated with only a
  > tiny ripple. The covered phin remains untouched; do not expose or manipulate
  > its internal screw insert. Add no handle if it would weaken the support
  > silhouette. Include no text, digits, clock, logo, arrow, panel, warning
  > icon, opaque-wall cutaway, impossible floating pool, narrow glass,
  > unsupported plate, steam, splash, spill, exact level marking, sidewall
  > holes, removed lid, hand, loose disc, intervention, or clutter. The only
  > story must be a fully supported covered screw phin draining slowly through
  > a transparent stable cup without intervention. Preserve opaque overall
  > canvas output at 1024 x 768 and crop-safe mobile legibility.
- Rejected drafts: the initial render left an apparent air gap between the cup
  rim and support plate, so the hot brewer read as floating. The first edit
  closed that gap but created an impossible opaque cup with a window-like
  cutaway and visible floating liquid. Neither draft was copied into the
  repository or manifest.
- Corrected generated master:
  `C:\Users\adam-\.codex\generated_images\019fa48e-1d5d-7560-b0de-f39fa2e8b914\call_kyMUvUElzoiU17pxOgsSf27F.png`
  (opaque RGB PNG, 1448 x 1086 px, exact 4:3).
- Final drawable:
  `app/src/main/res/drawable-nodpi/instruction_p1_phin_screw_18_120_stage_05_instruction_default.webp`
- Delivery: opaque RGB WebP, 1024 x 768 px, 74,962 bytes, exact 4:3.
- Accessibility target: “A covered screw-insert phin rests fully on a broad
  clear glass while separated slow coffee drops fall without anyone touching
  the hot brewer.” The canonical exact-stage alt text remains “Instructional
  view of monitor first drip and total window using the exact brewer profile
  and filter configuration stated in this recipe; steady slow drips complete.”
  The asset record remains intentionally unregistered until localization and
  phin-mechanism review are complete.
- Inspection: no text, numbers, logos, arrows, hands, intervention, exposed
  insert, loose gravity disc, sidewall holes, steam, continuous stream, stall,
  splash, spill, narrow support, cutaway, or clutter. The final frame shows a
  coherent transparent cup, direct rim-to-plate contact, a planted wide base,
  shallow calm coffee, a short sequence of separated drops, a seated lid, and
  an undisturbed solid-wall chamber.
- Review status: **pending brewer-expert screw-phin mechanism, support, and
  drip-state review**. Confirm 120–150 ml proportions, concealed screw-insert
  continuity with stage 01, plate/rim contact, heat-safe glass stability,
  natural slow-drip readability, beverage-level neutrality, mobile-size
  legibility, and warning-before-image placement before registration or
  approval.

### `p1-switch-official-post-rinse-closed-imagegen-v3`

- Stable asset ID:
  `instruction_p1_switch_official_20_240_stage_01_instruction_default`
- Exact scope: `switch_official_20_240` / source `stage_01` /
  `p1_switch_official_20_240_stage_01_instruction`; a rinsed V60 02 paper
  remains seated in an empty official Switch while its lever is closed, its
  steel ball blocks the outlet, and the emptied server receives no new flow.
- Evidence: `SRC-HARIO-SWITCH`. Reverified on 28 July 2026 against HARIO
  Europe's current Switch 02/03 product page at
  <https://www.hario-europe.com/products/v60-immersion-dripper-switch>.
  The current first-party page corroborates the heatproof-glass cone, silicone
  base, resin switch, stainless-steel blocking ball, V60 paper, and button-led
  release relationship. The canonical global page currently rejects automated
  retrieval; the canonical source record remains authoritative for the exact
  post-rinse order and completion cue.
- Generation mode: new bitmap generation followed by two targeted corrective
  image edits with the built-in image generator.
- Generated: 28 July 2026.
- Base prompt:

  > Create one original, clean, text-free 4:3 instructional illustration for a
  > mobile coffee-brewing guide. Show one coherent completed preparation state
  > from a close elevated front three-quarter angle: an accurate unbranded
  > Hario Switch 02-style steep-and-release brewer centered securely on a
  > stable empty heat-safe glass server. Preserve the distinctive mechanism and
  > silhouette: a handleless ribbed clear-glass V60 02 cone, exactly one
  > correctly folded and fully seated clean white V60 02 paper following the
  > cone ribs with a subtle wet sheen, a thick dark silicone base, the real
  > short side switch lever, a small stainless-steel ball, the circular outlet
  > seat, and the central drain opening. Depict the state after rinse water has
  > completely drained and been discarded and the valve has then been closed.
  > Show the side switch in the physically credible closed position and the
  > steel ball resting firmly and concentrically on its outlet seat, blocking
  > the central opening by direct contact. Make the ball-to-seat relationship
  > visible through the clear lower glass and open viewing angle without a
  > cutaway, diagram, or exploded parts. The wet paper must contain no pool, no
  > grounds, and no coffee. The glass server below must be visibly empty and dry
  > enough to read as having been emptied after preheating; show no retained
  > liquid, droplet, stream, splash, or condensation trail below the outlet.
  > Show no kettle and no hands. Make correctly seated wet paper, folded seam,
  > closed lever, seated blocking ball, empty server, and complete absence of
  > new flow immediately legible at mobile size. Use a warm-neutral empty
  > counter and soft plain background, semi-flat softly dimensional educational
  > rendering, precise believable Switch geometry, restrained
  > glass/charcoal/silver palette, clear silhouettes, soft light, subtle
  > shadows, generous crop-safe breathing room, and no decorative objects.
  > Include no text, letters, numbers, measurement marks, labels, logos, brand
  > marks, arrows, callouts, panels, warning symbols, cutaways, magnified insets,
  > comparison, coffee grounds, brown liquid, slurry, bloom, retained rinse
  > pool, kettle, pouring, stirring, serving, hand, finger, open lever, depressed
  > release control, lifted ball, open outlet, droplet, stream, liquid in the
  > server, Clever actuator, generic V60 base, glass handle, tap, clamp, bottom
  > server actuator, wedge paper, Wave paper, basket paper, cloth, reusable
  > metal filter, wrong-size cone, misaligned paper, second brewer, or clutter.
  > The final output must be one opaque 1024 x 768 scene whose only story is an
  > accurately closed, post-rinse Switch 02 above an empty server with no new
  > flow.
- First corrective edit prompt:

  > Refine the immediately previous post-rinse Switch 02 illustration while
  > preserving its exact 4:3 framing, handleless ribbed clear-glass cone, dark
  > silicone base, side switch, seated stainless ball, empty glass server, no
  > flow, no coffee, no kettle, no hands, and clean warm-neutral educational
  > style. Correct only the paper and mechanism legibility. Make the single
  > white V60 02 paper visibly wet from the completed rinse through a restrained
  > translucent sheen and slightly darker damp fibers along the lower cone,
  > while keeping it empty with no retained water pool. Show one physically
  > credible folded factory seam as a narrow doubled strip running down one
  > side of the conical paper, tucked flush against the glass ribs; the paper
  > must remain smooth, correctly sized, centered, and fully seated, not
  > floating, pleated like a basket, buckled, doubled, or extending implausibly
  > above the cone. Keep the stainless-steel ball visibly centered in direct
  > contact with its circular outlet seat, and keep the real short side switch
  > in the closed state; do not lift the ball, depress the release, add a drip,
  > or open the outlet. Preserve the visibly empty server and completely dry air
  > gap below the drain. Do not add labels, text, digits, arrows, cutaways,
  > comparison panels, water, grounds, brown liquid, steam, splash, hand, tool,
  > logo, generic lever, Clever mechanism, or clutter. The only story must
  > remain a rinsed, correctly folded V60 paper in a closed post-rinse Switch
  > above an empty server with no new flow. Preserve opaque 1024 x 768 output and
  > crop-safe mobile legibility.
- Final corrective edit prompt:

  > Make one final, tightly scoped paper-seam correction to the immediately
  > previous closed post-rinse Switch illustration. Preserve the entire brewer,
  > ball seated on its outlet seat, closed side switch, dark silicone base,
  > empty server, no flow, no coffee, no hands, lighting, 4:3 framing, and all
  > other geometry exactly as shown. Change only the single wet white V60 02
  > paper: add one unmistakable but subtle factory seam fold on the viewer-left
  > front side, rendered as a narrow doubled-paper strip that begins at the
  > upper paper rim and runs diagonally downward along the cone wall toward the
  > bottom point, flush against the glass ribs. The seam must be a physical
  > overlap in the paper silhouette with a faint doubled edge, not a printed
  > line, decorative pattern, arrow, crack, label, second filter, or horizontal
  > band. Remove the broad horizontal doubled band currently reading around the
  > paper's upper rim; keep only a normal thin paper edge at the top plus the one
  > diagonal folded seam. Keep the paper wet-looking, smooth, correctly sized,
  > centered, fully seated, empty, and free of retained water. Do not change or
  > add any lever, ball, outlet, liquid, server content, text, number, logo,
  > panel, callout, hand, kettle, grounds, or clutter. Preserve opaque
  > 1024 x 768 output and crop-safe mobile legibility.
- Rejected drafts: the initial render did not make the folded paper seam or wet
  post-rinse state legible enough at mobile size. The first edit strengthened
  the damp paper but produced a broad doubled band around the upper rim rather
  than one folded factory seam. Neither draft was copied into the repository
  or manifest.
- Corrected generated master:
  `C:\Users\adam-\.codex\generated_images\019fa48e-1d5d-7560-b0de-f39fa2e8b914\call_Gww98DkmNdeSAijFZiLeL663.png`
  (opaque RGB PNG, 1448 x 1086 px, exact 4:3).
- Final drawable:
  `app/src/main/res/drawable-nodpi/instruction_p1_switch_official_20_240_stage_01_instruction_default.webp`
- Delivery: opaque RGB WebP, 1024 x 768 px, 96,844 bytes, exact 4:3.
- Accessibility target: “A wet folded V60 02 paper sits in the closed Switch,
  with the steel ball seated and the empty server receiving no new drip.” The
  canonical exact-stage alt text remains “Instructional view of insert and
  rinse the v60 02 paper, then close the switch using the exact brewer profile
  and filter configuration stated in this recipe; rinse drained before closure
  and no new dripping occurs.” The asset record remains intentionally
  unregistered until localization and Switch-mechanism review are complete.
- Inspection: no text, numbers, logos, arrows, hands, kettle, coffee, retained
  rinse pool, drip, stream, server liquid, open valve, lifted ball, generic
  valve, wrong filter, cutaway, or clutter. The final frame shows one seated
  damp V60 paper with a visible doubled seam at viewer-left, a handleless
  ribbed-glass cone, the steel ball centered on its seat, a short closed-state
  side switch, and a stable visibly empty server.
- Review status: **pending brewer-expert Switch mechanism and post-rinse
  review**. Confirm Switch 02 proportions, closed-lever direction, direct
  ball-to-seat contact, outlet sealing, seam fold and wet-paper fit, empty
  server, no-flow readability, mobile-size distinction from all open/retained
  Switch assets, and instruction-before-image placement before registration or
  approval.

### `p1-v60-official-rinse-complete-imagegen-v1`

- Stable asset ID:
  `instruction_p1_v60_official_15_250_stage_01_instruction_default`
- Exact scope: `v60_official_15_250` / source `stage_01` /
  `p1_v60_official_15_250_stage_01_instruction`; one folded V60 02 paper is
  fully wet and seated against standard spiral ribs while the preheated server
  below is empty after discarded rinse water.
- Evidence: `SRC-HARIO-V60-OFFICIAL`. Reverified on 28 July 2026 against Hario
  UK's current intermediate guide at
  <https://www.hario.co.uk/pages/brew-guides-v60-intermediate> and beginner
  guide at
  <https://www.hario.co.uk/pages/how-to-brew-coffee-with-hario-v60-coffee-dripper>.
  The current first-party guidance corroborates folding the V60 paper seam,
  seating the paper in the cone, rinsing with hot water, preheating the server,
  and discarding the rinse water before coffee is added.
- Generation mode: new bitmap generation with the built-in image generator;
  no corrective edit was needed before pending-review delivery.
- Generated: 28 July 2026.
- Final prompt:

  > Create one original, clean, text-free 4:3 instructional illustration for a
  > mobile coffee-brewing guide. Show one coherent completed rinse state from a
  > close elevated front three-quarter angle: an accurate unbranded standard
  > transparent V60 02-style conical dripper centered level and securely
  > supported on a stable clear heat-safe glass server. Preserve standard V60
  > 02 geometry only: a clear handle-bearing cone with one large central outlet,
  > a broad circular support flange, and continuous curved spiral ribs running
  > up the inner wall. Show exactly one correctly sized smooth white conical V60
  > 02 paper fully seated to the bottom point and lying flush against the spiral
  > ribs. Make one factory-crimped side seam physically unmistakable on the
  > viewer-left front side: it has been folded neatly over itself into a narrow
  > doubled-paper strip that follows the conical wall from upper rim toward the
  > bottom point, without adding a printed line or second filter. The paper has
  > already been rinsed and is uniformly wet, shown by a restrained translucent
  > sheen and subtly darker damp fibers, yet contains no retained water pool,
  > coffee, grounds, or slurry. The empty server below has no rinse water; only
  > a very faint natural haze at the upper interior may suggest preheating
  > without becoming a steam plume. Show a dry outlet with no droplet or active
  > flow. Show no kettle and no hands. Make standard spiral-rib geometry, the
  > single folded seam, smooth rib contact, uniformly wet paper, empty preheated
  > server, stable support, and completed no-flow state immediately legible at
  > mobile size. Use a warm-neutral empty counter and soft plain background,
  > semi-flat softly dimensional educational rendering, precise believable
  > glass and paper geometry, restrained clear-glass/white/warm-gray palette,
  > clear silhouettes, soft light, subtle shadows, generous crop-safe breathing
  > room, and no decorative objects. Include no text, letters, numbers,
  > measurement marks, labels, logos, brand marks, arrows, callouts, panels,
  > warning symbols, comparison, magnified inset, cutaway, unfolded seam,
  > horizontal folded band, reversed, doubled, torn, buckled, floating,
  > collapsed, oversized, undersized, off-center or pleated basket paper, Wave,
  > wedge, No. 1, cloth, reusable metal, NEO, Suiren, Switch silicone base,
  > steel ball, lever, Clever actuator, valve, retained rinse water, active
  > dripping, kettle pour, coffee grounds, brown liquid, bloom, serving beverage,
  > overflow, splash, steam plume, unstable server, second brewer, or clutter.
  > The final output must be one opaque 1024 x 768 scene whose only story is a
  > standard V60 02 with one neatly folded, fully wet paper above an emptied
  > preheated server after rinsing is complete.
- Generated master:
  `C:\Users\adam-\.codex\generated_images\019fa48e-1d5d-7560-b0de-f39fa2e8b914\call_u8lWPcaWE9MsPA5n9un4adAh.png`
  (opaque RGB PNG, 1448 x 1086 px, exact 4:3).
- Final drawable:
  `app/src/main/res/drawable-nodpi/instruction_p1_v60_official_15_250_stage_01_instruction_default.webp`
- Delivery: opaque RGB WebP, 1024 x 768 px, 95,574 bytes, exact 4:3.
- Accessibility target: “A wet folded V60 02 paper lies smoothly against the
  spiral ribs above an empty preheated server after the rinse water is
  discarded.” The canonical exact-stage alt text remains “Instructional view
  of insert and rinse the v60 02 paper using the exact brewer profile and
  filter configuration stated in this recipe; paper is fully wet and server
  preheated.” The asset record remains intentionally unregistered until
  localization and V60-geometry review are complete.
- Inspection: no text, numbers, logos, arrows, hands, kettle, coffee, retained
  water, active flow, Switch/Clever mechanism, wrong paper, unstable support,
  cutaway, or clutter. The final frame shows standard V60 02 spiral ribs, one
  smooth damp conical paper with a doubled folded seam at viewer-left, a level
  clear handle-bearing dripper and support flange, and a stable visibly empty
  glass server.
- Review status: **pending brewer-expert standard-V60 paper, support, and rinse
  review**. Confirm V60 02 proportions, spiral ribs and central outlet,
  dripper/server interface, seam fold, wet-paper fit, lack of retained water,
  empty-server readability, material neutrality for the selected profile,
  mobile-size legibility, and instruction-before-image placement before
  registration or approval.

### `p1-clever-closed-off-server-rinse-imagegen-v2`

- Stable asset ID:
  `instruction_p1_clever_water_first_15_250_stage_01_instruction_default`
- Exact scope: `clever_water_first_15_250` / source `stage_01` /
  `p1_clever_water_first_15_250_stage_01_instruction`; a rinsed wedge paper
  remains seated in a Clever-style brewer held safely by its handle, fully
  clear of every server while the recessed actuator stays relaxed and dry.
- Evidence: `SRC-CLEVER-HOFFMANN`. Reverified on 28 July 2026 against James
  Hoffmann's canonical technique video at
  <https://www.youtube.com/watch?v=RpOdennxP24> and the current Clever product
  instructions at
  <https://cleverbrewing.coffee/collections/clever-manual-brewers/products/clever-dripper>.
  The manufacturer/distributor guidance corroborates the folded flush wedge
  paper, rinse and discarded water, and bottom valve that opens only when the
  brewer is placed on a cup or carafe.
- Generation mode: new bitmap generation followed by one targeted corrective
  image edit with the built-in image generator.
- Generated: 28 July 2026.
- Base prompt:

  > Create one original, clean, text-free 4:3 instructional illustration for a
  > mobile coffee-brewing guide. Show one coherent completed rinse state from a
  > close elevated front three-quarter angle: an accurate unbranded translucent
  > Clever-style bottom-actuated steep-and-release dripper held level a short
  > distance above a dry neutral counter. Preserve distinctive Clever geometry:
  > a tall translucent trapezoidal/wedge-shaped brewer body with sloped walls,
  > one sturdy cool side handle, outer support feet around the base, a recessed
  > central drain outlet, and the real springless bottom actuator that opens
  > only when the brewer is placed on a suitable cup or server. Show exactly one
  > correctly folded wedge/#4-style white paper seated flush against both
  > sloped walls, with its crimped seam folded neatly and a restrained uniformly
  > wet translucent sheen from rinsing. The paper is empty: no coffee grounds,
  > retained rinse pool, or brown liquid. One calm adult hand grips only the
  > outer side handle, with fingers and thumb entirely away from the brewer
  > body, underside, actuator, outlet, and paper. Hold the dripper level several
  > centimeters above the counter so a clear dry air gap is visible beneath the
  > entire base; it must be unmistakably off every cup, mug, carafe, server,
  > stand, and scale. Use the elevated viewing angle to expose the underside
  > edge and show the recessed bottom actuator fully relaxed and unpressed in
  > its normally closed position, with the outlet shut and absolutely no
  > droplet or stream beneath it. Show no kettle and no receiving vessel. Make
  > the wet seated wedge paper, handle-only grip, off-server clearance, visible
  > outer feet, unpressed recessed actuator, dry closed outlet, and complete
  > absence of flow immediately legible at mobile size. Use a warm-neutral empty
  > counter and plain soft background, semi-flat softly dimensional educational
  > rendering, precise believable translucent plastic and valve geometry,
  > restrained clear/charcoal/white palette, clear silhouettes, soft light,
  > subtle shadows, generous crop-safe breathing room, and no decorative
  > objects. Include no text, letters, numbers, measurement marks, labels,
  > logos, brand marks, arrows, callouts, panels, warning symbols, cutaways,
  > magnified inset, comparison, cup, mug, carafe, server, stand, scale, surface
  > touching the actuator, pressed actuator, open valve, open outlet, drip,
  > stream, retained water, active rinse pour, kettle, coffee grounds, slurry,
  > bloom, serving liquid, overflow, hot-part contact, hand under the outlet,
  > second hand, second brewer, Hario Switch lever or steel ball, tap, generic
  > valve, standard V60 cone, handleless glass cone, manual drain control, V60
  > paper, Wave paper, basket paper, No. 1 paper, cloth, reusable metal filter,
  > wrong-size, torn, doubled, buckled or floating paper, or clutter. The final
  > output must be one opaque 1024 x 768 scene whose only story is a rinsed
  > Clever-style dripper held safely by its handle, fully off-server with its
  > normally closed bottom actuator unpressed and no new drip.
- Corrective edit prompt:

  > Correct the immediately previous off-server Clever rinse illustration while
  > preserving its safe handle-only hand grip, level hovering position, visible
  > dry air gap over the counter, no receiving vessel, no flow, translucent
  > material, unpressed central bottom mechanism, warm-neutral text-free style,
  > 4:3 framing, and every other clean safety detail. Replace the incorrect
  > round V60-like cone and conical paper with accurate Clever-style wedge
  > geometry. The brewer body must have a rounded-rectangular or elongated oval
  > top opening, two broad nearly planar sloped front/back walls, two narrower
  > side walls, and a tapered trapezoidal wedge profile leading to the bottom
  > outlet—not a rotationally symmetric circular cone. Keep one sturdy
  > integrated side handle. Replace the conical filter with exactly one folded
  > #4/Melitta-style wedge paper: two broad flat paper faces meeting along narrow
  > side gussets, one neatly folded crimped side seam, a straight-to-gently-
  > curved upper edge matching the elongated opening, and a flat narrow lower
  > fold seated close to the wedge bottom. The wet white paper must lie flush to
  > the broad walls, remain empty, and show no water pool or coffee. Keep the
  > distinct outer support base and feet, but make the normally closed actuator
  > mechanically credible: a small central recessed contact button and outlet
  > contained above the lowest plane of the outer feet, visibly relaxed and not
  > touching the counter or the hand. Show no separate V60 flange, spiral ribs,
  > circular cone, Switch ball/lever, or generic tap. Preserve the hand entirely
  > on the cool handle with fingers away from the body and underside. Keep the
  > whole base several centimeters above the counter, outlet dry, no drop, no
  > kettle, no cup/server/stand/scale, no text, logo, arrow, panel, cutaway, or
  > clutter. The only story must be an accurate wedge-bodied Clever dripper with
  > one wet folded #4 paper held safely off-server while its recessed bottom
  > actuator remains unpressed and closed. Preserve opaque 1024 x 768 output and
  > crop-safe mobile legibility.
- Rejected draft: the initial render correctly showed the handle-only grip,
  air gap, feet, and unpressed outlet, but its rotationally symmetric cone and
  conical paper read as a V60 rather than a Clever wedge brewer. That draft was
  not copied into the repository or manifest.
- Corrected generated master:
  `C:\Users\adam-\.codex\generated_images\019fa48e-1d5d-7560-b0de-f39fa2e8b914\call_6Nkw4txTus0gMrlyP6HwyBXr.png`
  (opaque RGB PNG, 1448 x 1086 px, exact 4:3).
- Final drawable:
  `app/src/main/res/drawable-nodpi/instruction_p1_clever_water_first_15_250_stage_01_instruction_default.webp`
- Delivery: opaque RGB WebP, 1024 x 768 px, 113,646 bytes, exact 4:3.
- Accessibility target: “A hand holds the rinsed wedge-bodied Clever only by
  its handle above the counter, with the recessed actuator unpressed and no
  drip.” The canonical exact-stage alt text remains “Instructional view of
  insert and rinse the wedge paper, then confirm the valve is closed using the
  exact brewer profile and filter configuration stated in this recipe; no
  dripping after rinse is discarded.” The asset record remains intentionally
  unregistered until localization and Clever-mechanism review are complete.
- Inspection: no text, numbers, logos, arrows, cup, server, scale, kettle,
  coffee, retained water, flow, contact under the brewer, Switch/V60 mechanism,
  second hand, cutaway, or clutter. The final frame shows a rounded-rectangular
  wedge body, broad planar walls, one wet folded #4 paper and visible seam, a
  safe handle-only grip, clear air beneath the outer feet, and a small recessed
  actuator above the foot plane with a dry outlet.
- Review status: **pending brewer-expert Clever body, paper, and actuator
  review**. Confirm wedge profile, #4 paper fold and wet fit, safe handle grip,
  outer-foot geometry, recessed normally closed actuator, outlet clearance,
  no-flow readability, mobile-size distinction from V60 and Switch assets,
  and instruction-before-image placement before registration or approval.

### `p1-switch-ole-boen-first-bloom-imagegen-v2`

- Stable asset ID:
  `instruction_p1_switch_ole_boen_hybrid_16_5_240_stage_01_instruction_default`
- Exact scope: `switch_ole_boen_hybrid_16_5_240` / source `stage_01` /
  `p1_switch_ole_boen_hybrid_16_5_240_stage_01_instruction`; the first low,
  granular coffee bloom remains retained above a centrally seated Switch ball
  while the server below stays empty.
- Evidence: `SRC-KURASU-SWITCH`. Reverified on 28 July 2026 against the current
  first-party HARIO Europe Ole Kristian Bøen recipe at
  <https://www.hario-europe.com/blogs/hario-community/ole-kristian-boens-switch-recipe>
  and Switch product page at
  <https://www.hario-europe.com/products/v60-immersion-dripper-switch>.
  The first-party pages corroborate Switch 02, the closed first bloom, V60
  paper, glass cone, silicone base, stainless ball, and button-release
  mechanism. Exact mass, time, and temperature remain in Compose text rather
  than the bitmap.
- Generation mode: new bitmap generation followed by one targeted corrective
  image edit with the built-in image generator.
- Generated: 28 July 2026.
- Base prompt:

  > Create one original, clean, text-free 4:3 instructional illustration for a
  > mobile coffee-brewing guide. Show one coherent very-early retained-bloom
  > state from a close elevated front three-quarter angle: an accurate
  > unbranded Hario Switch 02-style brewer centered securely on a stable empty
  > heat-safe clear-glass server. Preserve the distinctive mechanism and
  > silhouette: a handleless ribbed clear-glass V60 02 cone, exactly one
  > correctly folded rinsed white V60 02 paper seated flush against the ribs, a
  > thick dark silicone base, the real short side switch lever, a small
  > stainless-steel ball, its circular outlet seat, and the central drain
  > opening. The valve is closed: show the side switch in the physically
  > credible closed position and the steel ball resting firmly and
  > concentrically on its outlet seat by direct contact. Inside the paper, show
  > a modest low bed of freshly wetted coffee that is uniformly dark and gently
  > swollen from the first small bloom, with a soft domed texture and fine
  > trapped-gas sheen but no dry islands, deep crater, active stirring, or high
  > standing pool. Keep the retained bloom shallow in the lower portion of the
  > cone with abundant empty headroom, clearly much lower than a final retained
  > pour. Show no kettle, stream, or hand; the small pour has just finished.
  > Through the lower glass and open angle, keep the seated ball and closed
  > mechanism readable without a cutaway. Beneath the outlet show a completely
  > dry air gap: no drop, stream, splash, or coffee in the server, which remains
  > visibly empty. Make the low evenly wet swollen bloom, closed lever, seated
  > ball, abundant headroom, empty server, and complete absence of drainage
  > immediately legible at mobile size. Use a warm-neutral empty counter and
  > soft plain background, semi-flat softly dimensional educational rendering,
  > precise believable Switch/glass/paper geometry, restrained
  > glass/charcoal/silver/coffee-brown palette, clear silhouettes, soft light,
  > subtle shadows, generous crop-safe breathing room, and no decorative
  > objects. Include no text, letters, numbers, clock, timer, temperature
  > display, scale display, measurement marks, labels, logos, brand marks,
  > arrows, callouts, panels, warning symbols, comparison, inset, cutaway, open
  > lever, depressed release, lifted ball, open outlet, dripping, liquid or
  > coffee in the server, deep flooded slurry, nearly full cone, high retained
  > volume, dry grounds, deep crater, drained bed, kettle, ongoing pour,
  > stirrer, agitation, hand, glass handle, Clever actuator, standard non-Switch
  > base, tap, clamp, wedge paper, Wave paper, basket paper, cloth, reusable
  > metal filter, wrong-size, doubled, collapsed or missing paper, second
  > brewer, or clutter. The final output must be one opaque 1024 x 768 scene
  > whose only story is the first shallow coffee bloom retained safely in a
  > closed Switch above an empty server.
- Corrective edit prompt:

  > Correct the immediately previous shallow retained-bloom Switch illustration
  > while preserving its accurate handleless ribbed glass V60 cone, one white
  > V60 02 paper, dark silicone base, empty stable glass server, no flow, no
  > hands, no kettle, warm-neutral educational style, 4:3 framing, and abundant
  > empty headroom. Fix exactly two physical errors. First, replace the smooth
  > oversized cake-like coffee dome with a plausible very-early bloom: a much
  > lower, modest bed in the bottom of the paper with visible individual wet
  > coffee granules, an irregular gently rounded surface, a few tiny natural
  > gas bubbles, and only a restrained moist sheen. It must be uniformly wetted
  > with no dry islands but must not resemble a muffin, sponge, solid molded
  > puck, deep slurry, standing water pool, high liquid level, or final retained
  > pour. Keep the wet bed low enough that most of the paper-lined cone remains
  > empty and visible above it. Second, remove the invented stainless ball and
  > socket from the front exterior of the silicone base. Place exactly one small
  > stainless-steel ball on the brewer's central vertical axis inside the clear
  > lower glass throat directly beneath the paper tip, seated concentrically in
  > direct contact with the circular outlet seat. The ball must block the true
  > central drain opening; it must not sit in front of the cone, outside the
  > liquid path, in a decorative recess, or beside the lever. Keep the real
  > short dark switch lever on the viewer-right side in the closed position;
  > its internal cam may remain hidden inside the silicone base. Through the
  > clear lower glass, make central ball-to-seat contact visible without a
  > cutaway or exploded diagram. Keep the server completely empty and the air
  > below the outlet dry, with no drop or stream. Add no text, digits, clock,
  > scale, arrows, panels, kettle, hand, stirring, open lever, lifted ball,
  > second ball, coffee in the server, wrong filter, generic valve, or clutter.
  > The only story must be a low granular first bloom retained by one centrally
  > seated ball in a closed Switch. Preserve opaque 1024 x 768 output and
  > crop-safe mobile legibility.
- Rejected draft: the initial render made the bloom look like a smooth molded
  cake and made the central ball/seat relationship read as a decorative front
  socket. That draft was not copied into the repository or manifest.
- Corrected generated master:
  `C:\Users\adam-\.codex\generated_images\019fa48e-1d5d-7560-b0de-f39fa2e8b914\call_xEWHIklZj3WcSQAl13jhERk1.png`
  (opaque RGB PNG, 1448 x 1086 px, exact 4:3).
- Final drawable:
  `app/src/main/res/drawable-nodpi/instruction_p1_switch_ole_boen_hybrid_16_5_240_stage_01_instruction_default.webp`
- Delivery: opaque RGB WebP, 1024 x 768 px, 95,746 bytes, exact 4:3.
- Accessibility target: “A low granular first bloom is retained above the
  closed Switch ball while the clear server below remains empty.” The
  canonical exact-stage alt text remains “Instructional view of close the
  switch and bloom with 50 g using the exact brewer profile and filter
  configuration stated in this recipe; bloom retained until 0:40.” The asset
  record remains intentionally unregistered until localization and
  Switch-mechanism review are complete.
- Inspection: no text, numbers, clock, scale, logos, arrows, hands, kettle,
  active pour, high slurry, dry islands, drainage, server liquid, second ball,
  wrong filter, cutaway, or clutter. The final frame shows one wet granular
  shallow bed with broad headroom, one rinsed V60 02 paper, a central seated
  stainless ball, a short closed-state side lever, a dry outlet, and a stable
  empty server.
- Review status: **pending brewer-expert Switch mechanism and first-bloom
  review**. Confirm Switch 02 proportions, closed-lever direction, true
  central ball/seat relationship, granular uniformly wet bloom depth, absence
  of drainage, empty server, distinction from the high retained final pour,
  mobile-size legibility, and instruction-before-image placement before
  registration or approval.

### `p1-v60-official-small-circle-pour-imagegen-v1`

- Stable asset ID:
  `instruction_p1_v60_official_15_250_stage_04_instruction_default`
- Exact scope: `v60_official_15_250` / source `stage_04` /
  `p1_v60_official_15_250_stage_04_instruction`; a single low gooseneck stream
  stays within the center-to-mid coffee bed while a broad clean paper band
  remains untouched around the standard V60 02.
- Evidence: `SRC-HARIO-V60-OFFICIAL`. Reverified on 28 July 2026 against Hario
  UK's current intermediate V60 guide at
  <https://www.hario.co.uk/pages/brew-guides-v60-intermediate>.
  The current first-party guide corroborates V60 02 and 02 paper, a slow
  small-circle pour, the cumulative target, and the water-temperature range.
  Numeric quantity, temperature, and scale completion remain in Compose text.
- Generation mode: new bitmap generation with the built-in image generator;
  no corrective edit was needed before pending-review delivery.
- Generated: 28 July 2026.
- Final prompt:

  > Create one original, clean, text-free 4:3 instructional illustration for a
  > mobile coffee-brewing guide. Show one coherent gentle final-pour action from
  > an elevated near-top front three-quarter angle: an accurate unbranded
  > standard transparent V60 02-style conical dripper with continuous curved
  > spiral ribs, one correctly folded and fully seated rinsed white V60 02
  > paper, centered level on a stable clear heat-safe glass server and a
  > low-profile coffee scale whose display is turned away and unreadable. Make
  > the inside boundary unmistakable: a pale clean paper wall forms a broad
  > visible ring around a smaller dark coffee bed and calm brown slurry. One
  > adult hand safely grips only the handle of a matte gooseneck kettle outside
  > the hot-water path. The narrow gooseneck tip hovers low but safely above the
  > cone and produces one thin, calm, nearly vertical stream onto the
  > center-to-mid region of the dark coffee bed, well inside its boundary and
  > visibly far from every exposed paper wall. Imply a compact controlled
  > small-circle pour only through natural physical cues: position the stream
  > slightly off center within the middle of the bed and show a subtle small
  > circular wetting/ripple pattern contained entirely on the coffee surface,
  > without any drawn path or arrow. Keep the slurry level modest and calm, the
  > clean paper ring exposed, the stream low-energy, and the
  > server/dripper/scale stable. Make the bed-versus-paper boundary, stream
  > wholly over coffee, compact circle, gentle pour, safe kettle grip, and lack
  > of flooding immediately legible at mobile size. Use a warm-neutral empty
  > counter and soft plain background, semi-flat softly dimensional educational
  > rendering, precise believable V60/glass/paper/kettle geometry, restrained
  > clear-glass/white/charcoal/coffee-brown palette, clear silhouettes, soft
  > light, subtle shadows, generous crop-safe breathing room, and no decorative
  > objects. Include no text, letters, numbers, readable scale, temperature
  > display, measurement marks, labels, logos, brand marks, arrows, drawn
  > spiral, dotted path, callouts, panels, warning symbols, comparison, inset,
  > cutaway, stream touching or running down the paper wall, extreme-perimeter
  > pour, wide sweeping circle, high turbulent stream, splash, flood, overflow,
  > dry grounds, violently spinning slurry, multiple streams, kettle spout
  > buried in slurry, hand touching glass or paper, tilted dripper, unstable
  > server, exact liquid level, Switch base/lever/ball, Clever actuator, Wave,
  > wedge, basket, cloth, reusable metal, wrong-size, doubled, buckled or
  > collapsed paper, second brewer, or clutter. The final output must be one
  > opaque 1024 x 768 scene whose only story is a low gentle small-circle pour
  > kept entirely over the coffee bed and away from the V60 paper wall.
- Generated master:
  `C:\Users\adam-\.codex\generated_images\019fa48e-1d5d-7560-b0de-f39fa2e8b914\call_4aH3DzA1KvmbdujlJJrPWxPn.png`
  (opaque RGB PNG, 1448 x 1086 px, exact 4:3).
- Final drawable:
  `app/src/main/res/drawable-nodpi/instruction_p1_v60_official_15_250_stage_04_instruction_default.webp`
- Delivery: opaque RGB WebP, 1024 x 768 px, 84,150 bytes, exact 4:3.
- Accessibility target: “A thin gooseneck stream lands inside the V60 coffee
  bed while a clean paper band remains visible and untouched around it.” The
  canonical exact-stage alt text remains “Instructional view of pour slowly in
  small circles to 250 g using the exact brewer profile and filter
  configuration stated in this recipe; scale reads 250 g.” The asset record
  remains intentionally unregistered until localization and V60 pour-path
  review are complete.
- Inspection: no text, numbers, readable scale, logos, arrows, drawn path,
  paper-wall pour, flooding, splash, dry grounds, unstable setup, hot-glass
  contact, wrong filter, Switch/Clever mechanism, cutaway, or clutter. The
  final frame shows one low thin stream just off center, a restrained circular
  surface ripple fully within a dark bed, a broad pale paper boundary, standard
  spiral ribs, a safe kettle grip, a stable server, and an unreadable scale.
- Review status: **pending brewer-expert standard-V60 pour-path and fluid-state
  review**. Confirm V60 02 proportions, folded paper fit, clear bed/paper
  boundary, compact circle cue, stream position and energy, lack of wall
  contact or flooding, server/scale stability, natural non-quantified beverage
  level, mobile-size readability before the Learn warning, and
  instruction-before-image placement before registration or approval.

### `p1-v60-rao-shallow-nest-imagegen-v2`

- Stable asset ID:
  `instruction_p1_v60_rao_20_330_stage_01_instruction_default`
- Exact scope: `v60_rao_20_330` / source `stage_01` /
  `p1_v60_rao_20_330_stage_01_instruction`; a rinsed paper in a lightweight
  plastic V60 02 holds a broadly level dry bed with one wide, shallow central
  nest and coffee still covering the cone point.
- Evidence: `SRC-HARIO-RAO-V60`. Reverified on 28 July 2026 against Hario UK's
  current Scott Rao recipe and interview at
  <https://www.hario.co.uk/blogs/hario-ambassadors/hario-v60-recipe-interview-with-hario-ambassador-scott-rao>.
  The original practitioner guidance on the manufacturer site corroborates a
  plastic V60, rinsed paper, dry coffee, and the central bird's-nest
  preparation. Recipe quantities and temperature remain in Compose text.
- Generation mode: new bitmap generation followed by one targeted corrective
  image edit with the built-in image generator.
- Generated: 28 July 2026.
- Base prompt:

  > Create one original, clean, text-free 4:3 instructional illustration for a
  > mobile coffee-brewing guide. Show one coherent completed dry-bed preparation
  > state from a close elevated near-top front three-quarter angle: an accurate
  > unbranded plastic V60 02-style conical dripper centered level and securely
  > supported on a stable clear heat-safe glass server. Make the brewer
  > unmistakably lightweight molded plastic rather than glass, ceramic, or
  > metal: use a thin translucent smoke-clear plastic wall, molded handle and
  > support flange, slightly softened injection-molded edges, one large central
  > outlet, and standard continuous curved spiral ribs visible through the
  > body. Show exactly one correctly sized rinsed white V60 02 paper seated
  > smoothly to the cone point and flush against the ribs; only its exposed
  > upper paper band has a subtle damp translucent sheen, with no retained rinse
  > water, droplet, or pool. Inside the paper show a dry, loose, evenly
  > distributed coffee bed whose broad outer surface is level from edge to edge.
  > At the precise center, form one wide, shallow, smooth nest: a gentle
  > saucer-like dimple with gradual low shoulders and a softly rounded bottom,
  > only a small fraction below the surrounding bed. Keep a continuous visible
  > layer of coffee covering the cone point at the dimple bottom. Make the
  > depression broad and understated, never a hole or funnel. Use a near-top
  > angle and gentle side lighting so the shallow depth is readable through
  > soft natural shading rather than contour lines or arrows. Show no hand,
  > finger, spoon, tool, kettle, active brewing, or scale. Make thin
  > molded-plastic identity, standard spiral ribs, correctly seated rinsed
  > paper, level dry outer bed, and one broad shallow central nest immediately
  > legible at mobile size. Use a warm-neutral empty counter and soft plain
  > background, semi-flat softly dimensional educational rendering, precise
  > believable V60/paper/coffee geometry, restrained
  > smoke-clear/white/coffee-brown palette, clear silhouettes, soft light,
  > subtle shadows, generous crop-safe breathing room, and no decorative
  > objects. Include no text, letters, numbers, measurement marks, labels,
  > logos, brand marks, arrows, callouts, panels, warning symbols, comparison,
  > cutaway, contour lines, magnified inset, deep crater, narrow bore, funnel,
  > tunnel, volcano rim, exposed paper at the bottom, sharply excavated hole,
  > multiple depressions, mounded or tilted outer bed, clumps, compression, wet
  > grounds, slurry, bloom, retained water, kettle, pouring, spinning, stirring,
  > finger, tool, ceramic, heavy glass, metal, Switch base/lever/ball, NEO,
  > Suiren, Wave, wedge, basket, wrong-size paper, second brewer, or clutter.
  > The final output must be one opaque 1024 x 768 scene whose only story is a
  > rinsed paper in a plastic V60 02 holding a broadly level dry bed with one
  > deliberately shallow central nest.
- Corrective edit prompt:

  > Correct the immediately previous plastic-V60 dry-bed illustration while
  > preserving its clear lightweight molded-plastic V60 02 body, handle, spiral
  > ribs, one white rinsed paper, empty stable glass server, elevated near-top
  > framing, no hand or tool, warm-neutral text-free educational style, and
  > every other clean detail. Fix only the coffee-bed geometry. The current
  > grounds form a high donut mound with a deep crater; replace that with a much
  > lower, broadly horizontal dry bed sitting in the lower portion of the paper
  > so abundant clean paper wall remains visible above it. The entire outer bed
  > from the paper boundary toward the center must be flat, loose, evenly
  > distributed, and level, with no raised rim, ring mound, volcano shoulder,
  > tilted slope, clump, or compressed wall. At the exact center make only one
  > very shallow, wide saucer-like nest: a subtle smooth indentation with
  > gradual shoulders that merge almost imperceptibly into the level plane,
  > only slightly lower than the surrounding surface. Keep loose coffee visibly
  > covering the entire dimple bottom and cone point; show no hole, bore, dark
  > tunnel, exposed paper, sharp edge, or deep shadow. Use fine natural shading
  > and granular texture to make the slight depression readable without
  > contour lines. Do not change the plastic material, paper fit, server
  > support, perspective, or clean environment. Add no text, digits, arrow,
  > cutaway, hand, spoon, water, wet grounds, scale, kettle, or clutter. The only
  > story must be a low level dry bed in a plastic V60 with one deliberately
  > shallow central nest, not a crater. Preserve opaque 1024 x 768 output and
  > crop-safe mobile legibility.
- Rejected draft: the initial render made the dry bed a high ring-shaped mound
  around a deep dark crater. That contradicted the bounded shallow completion
  cue and would teach the exact novice error this stage prevents. The draft was
  not copied into the repository or manifest.
- Corrected generated master:
  `C:\Users\adam-\.codex\generated_images\019fa48e-1d5d-7560-b0de-f39fa2e8b914\call_Co9IpysuaMR3G0IZBqDE5HOn.png`
  (opaque RGB PNG, 1448 x 1086 px, exact 4:3).
- Final drawable:
  `app/src/main/res/drawable-nodpi/instruction_p1_v60_rao_20_330_stage_01_instruction_default.webp`
- Delivery: opaque RGB WebP, 1024 x 768 px, 140,864 bytes, exact 4:3.
- Accessibility target: “A rinsed paper in a thin molded-plastic V60 holds a
  level dry bed with one wide, shallow central nest.” The canonical exact-stage
  alt text remains “Instructional view of rinse the paper and prepare a shallow
  nest in the coffee bed using the exact brewer profile and filter
  configuration stated in this recipe; paper is seated and nest is shallow,
  not a deep crater.” The asset record remains intentionally unregistered until
  localization and plastic-V60 geometry review are complete.
- Inspection: no text, numbers, logos, arrows, hand, tool, water, wet grounds,
  exposed paper, deep bore, volcano ring, non-plastic brewer, wrong filter,
  Switch mechanism, cutaway, or clutter. The final frame shows a thin clear
  molded-plastic cone and handle, standard spiral ribs, one seated damp paper,
  a lower dry granular bed with broadly level outer surface, one gentle central
  saucer, and a stable empty server.
- Review status: **pending brewer-expert plastic-V60 material and shallow-nest
  review**. Confirm plastic identity, V60 02 proportions, rinsed-paper fit,
  level outer bed, dimple depth and width, covered cone point, lack of retained
  water, server stability, mobile-size distinction from a crater or bloom, and
  instruction-before-image placement before registration or approval.

## Delivery gate

Assets must be local optimized WebP files in `drawable-nodpi` where appropriate.
Run `python tools/verify_instruction_assets.py` after every asset batch. It
fails on a malformed Android resource name, non-WebP content, a size other than
1024 × 768, a non-RGB or animated payload, or an encoded size above 300,000
bytes.

No asset is release-complete until the manifest and automated validation pass and
the physical review is signed off.
