# Pulsar valve correction from the user's photograph

The user rejected v14's valve and supplied the assembled white-brewer photo
preserved in `user-reference.png`. That reference takes priority over the prior
front photo showing a horizontal valve. The new reference shows a round plug
face with an upright, flat-ended rectangular paddle offset to its right.

The earlier capsule depicted a projection of the foot, omitting the rotary
face and paddle overlap. Root and Astra revisited the standalone valve and
assembled reference photographs, then compared two treatments:

- A single negative cutout joined the round face and paddle into a musical-note
  shape. It was discarded; its SVG is in `alternatives/negative-valve.svg`.
- The final drawing places a solid circular face and upright paddle over
  negative clearance on the front foot. A small right-side connection keeps
  the assembly attached to its supporting foot.

The solid hub centre is (15.25, 27.5), radius 0.75. The upright paddle occupies
x15.9–16.55 and y25–28.3, with flat ends and small corner rounding. Clearance
provides the visible acrylic edge around the assembly. This front-view
interpretation retains the central front bearing; the user's elevated
three-quarter view projects that bearing onto the visible front-right foot.

Only the valve and necessary clearance change. The cap, barrel and exterior
base paths retain v14 geometry. The illustration remains single-colour, with
opaque filled paths and transparent cutouts. The other method icons are unchanged.

`pulsar.svg` is the authored source, and `equipment_pulsar.xml` its Android
export. Production uses that export, and the equipment manifest preserves it
during batch generation. `preview.png` shows enlarged artwork, 48 dp badge
simulations, and physical-pixel size samples. `iteration-comparison.png` compares
v14 and v15; `family-comparison.png` shows the unchanged original icon family.

These sheets are SVG renders, not native Android captures. At 24 physical
pixels the hub and clearance are reduced to a small notch; at 34 pixels their
separation is delicate. Resource validation does not establish aesthetic
acceptance or replace evaluation in the actual UI.

Validation: `:app:processDebugResources` passed in 18 seconds. The production
drawable matches the exported XML byte for byte. Only the Pulsar method
drawable changed; this revision has no native Android capture.

```powershell
uv run --offline --with pillow==12.3.0 python tools/render_pulsar_concept.py --version 15
```
