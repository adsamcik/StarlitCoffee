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

## Delivery gate

Assets must be local optimized WebP files in `drawable-nodpi` where appropriate.
No asset is release-complete until the manifest and automated validation pass and
the physical review is signed off.
