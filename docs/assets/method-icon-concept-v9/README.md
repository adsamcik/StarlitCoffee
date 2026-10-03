# Pulsar front-view refinement

The user asked to keep iterating, with closer resemblance to the brewer as
the priority. This 2026-10-03 continuation retains the earlier bold filled
icon family and changes only Pulsar.

Root and Astra compared v8 to the manufacturer's front brewing photo recorded
in [v4](../method-icon-concept-v4/README.md). The v8 skirt rose toward the centre
and the outer feet were too broad. Root authored the following refinements:

- Make the skirt's upper edge almost level, with a shallow downward centre
  curve, instead of the previous raised central arch.
- Widen the two foot openings outward. The central valve-bearing foot is
  broader than either outer foot, matching the photo's construction.
- Widen the chamber cutout by 0.9 viewport units, retaining substantial filled
  sides. Compare curved and straight chamber edges; retain straight edges
  with rounded corners to avoid a padded appearance.
- Retain v8's aligned feet, cap proportions and short horizontal valve.

The drawing uses four opaque filled paths. `pulsar.svg` is the authored source;
`equipment_pulsar.xml` is its Android export. Production uses that XML, and the
equipment manifest points to it to preserve the source during batch generation.
Other method icons are unchanged.

`iteration-comparison.png` compares v7 with this iteration at an enlarged size
and at 24/28/34/44 physical pixels. `family-comparison.png` places it alongside
the original AeroPress, Espresso and Chemex assets. `chamber-comparison.png`
records the two chamber treatments; its curved source is in `alternatives/`.
`preview.png` shows badge simulations at 2.625 px/dp. These are SVG renders.
`report.json` records source hashes, bounds and production resource equality.

```powershell
uv run --offline --with pillow==12.3.0 python tools/render_pulsar_concept.py --version 9
```

## Android validation

The production-resource fixture build passed using
`tools/method_icon_refinement/production.init.gradle`, without resource overlays.
Both `MethodIconCaptureTest` tests passed on a disposable read-only boot of
Medium Phone, API 36, 420 dpi, in 10.284 seconds. `native/light.png` and
`native/dark.png` capture the actual `EquipmentIcon` and `EquipmentVisualBadge`
components at 24/28/34/44 dp and a 48 dp badge. `native/validation.json` records
the exact source and APK hashes; `native/instrumentation.txt` records test output.
The cap seam, outer feet and short valve remain distinguishable in those captures.

This validates rendering in a Compose fixture, rather than a full application
workflow or aesthetic acceptance. The disposable packages were removed and the
emulator started for this capture was closed.
The ordinary debug and Android test APK build then passed in 1 minute 18 seconds,
restoring the normal application IDs in the build outputs.
