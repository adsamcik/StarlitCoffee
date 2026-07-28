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

## Delivery gate

Assets must be local optimized WebP files in `drawable-nodpi` where appropriate.
No asset is release-complete until the manifest and automated validation pass and
the physical review is signed off.
