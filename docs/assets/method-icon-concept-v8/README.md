# Pulsar proportion refinement

The user's subsequent "keep iterating" request prioritizes resemblance to the
brewer. [v9](../method-icon-concept-v9/README.md) supersedes this intermediate
iteration after a second front-photo review and a chamber-treatment comparison.

This is the 2026-10-03 iteration requested after v7. It retains the original
family's solid fill, rounded cutouts and restrained detail, refining only Pulsar.
The cap and outer barrel are unchanged from v7.

Root and Astra compared v7 against the manufacturer's front brewing photo
recorded in [v4](../method-icon-concept-v4/README.md). The prior skirt extended
too high and shortened the visible chamber. Root authored these corrections:

- Lower the skirt's upper curve from y20.1 to y21.3.
- Make the glass cutout 0.4 units narrower and extend its lower edge from
  y20.91 to y21.45; the visible chamber is taller above the lowered base.
- Lower the foot-window roofs from y25.25 to y25.7 and round the shoulders.
- Align all three foot ends at y29.4 while retaining a slight outward taper
  on the outer edges of the cylindrical skirt.
- Shorten the valve tip from x19.95 to x19.5 and move it slightly lower,
  leaving more space above it and between its tip and the right foot.

The SVG is four opaque filled paths, with no gray fills or fine construction
details. `equipment_pulsar.xml` is the matching Android export. This intermediate
export was applied during iteration, and `:app:processDebugResources` passed
in 26 seconds. The production drawable and manifest now use v9.

`iteration-comparison.png` compares v7 and v8 at an enlarged size and at
24/28/34/44 physical pixels. `family-comparison.png` places the iteration beside
the three original icons, which have not been changed. `preview.png` shows
badge simulations at 2.625px/dp. These are SVG renders, not Android captures.
`report.json` records source hashes, bounds and production resource equality.

```powershell
uv run --offline --with pillow==12.3.0 python tools/render_pulsar_concept.py --version 8
```
