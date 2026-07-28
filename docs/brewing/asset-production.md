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

## Delivery gate

Assets must be local optimized WebP files in `drawable-nodpi` where appropriate.
No asset is release-complete until the manifest and automated validation pass and
the physical review is signed off.
