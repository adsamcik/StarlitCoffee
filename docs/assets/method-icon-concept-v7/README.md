# Pulsar in the original icon style

On 2026-10-03 the user asked to keep the earlier bold filled icon family and
recreate only Pulsar with a more accurate design. This replaces the detailed
v6 design, which had been accepted earlier and then superseded by this request.

The style reference is the immutable `method-icon-refinement/before` artwork
and the user's attached Before row. The production AeroPress, Espresso and
Chemex resources match `08619ebd` byte for byte and have not been edited.

Astra reviewed the style reference, the manufacturer's front brewing photo
and the designer's assembled brewer photo, then reviewed the first v7 render.
Those primary references are documented in [v4](../method-icon-concept-v4/README.md).
Root authored this solid pictogram and softened the base after that review.

- Keep one opaque fill, broad shapes and a rounded rectangular chamber cutout.
- Use a 27-unit-high by about 18-unit-wide silhouette in the 32-unit viewport.
  The cap, barrel and base occupy approximately 5, 14 and 8 vertical units.
- Show a substantial collar in a near-front view, without dispersion holes,
  reflections, gray fills or fine inner construction lines.
- Replace the original flat pedestal with a rounded cylindrical skirt, with
  two lower openings around the central valve-bearing foot.
- Show a short filled paddle in the horizontal closed position, extending
  from the central foot into the right opening. The valve has no outlined dial.

`pulsar.svg` is four filled paths. `equipment_pulsar.xml` is the matching
24dp Android vector export in a 32-unit viewport. `family-comparison.png`
shows the original row and the replacement Pulsar with the three unchanged
icons. `preview.png` includes larger artwork, badge simulations and actual
24/28/34/44px samples. These are SVG renders, not Android captures.

The source hashes and exact production match are recorded in `report.json`.
The reproduction command exports into this folder; installing the XML into
the production drawable is a separate copy operation.

The equipment manifest points Pulsar at this authored XML. The batch equipment
generator copies it directly instead of tracing the obsolete Pulsar raster;
its tracing fidelity comparisons still cover the other raster-derived icons.
The normal app `:app:processDebugResources` build passed for this resource.
The v7 design has SVG previews and resource compilation proof, but no new
native captures; v6's captures apply only to the superseded detailed design.

```powershell
uv run --offline --with pillow==12.3.0 python tools/render_pulsar_concept.py --version 7
```
