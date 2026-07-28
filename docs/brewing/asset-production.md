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

## Delivery gate

Assets must be local optimized WebP files in `drawable-nodpi` where appropriate.
No asset is release-complete until the manifest and automated validation pass and
the physical review is signed off.
