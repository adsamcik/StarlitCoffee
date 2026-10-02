# Accepted Pulsar icon

The user selected this artwork on 2026-10-03, requesting a correction to the
right leg. The right foot now has a straighter outer edge and a distinct
opening beside the receding rear support. The other contours and the valve
are unchanged from the accepted preview. The exported vector is installed at
`app/src/main/res/drawable/equipment_pulsar.xml`.

The primary construction photographs and mechanism references are recorded
in [v4](../method-icon-concept-v4/README.md). The elevated assembled brewer
photo establishes the recessed cap, clear barrel, coffee bed, continuous
circular base housing and unequal openings between the feet. The front
brewing photo confirms the base is a wide housing with openings in its skirt,
not a narrow platform with three identical legs.

Astra reviewed the rejected simplification and the first corrective render.
Root authored this SVG and corrected the broad base housing and low valve:

- A substantial front collar and recessed dispersion surface replace the
  thin empty ring in v5; three perforations remain as simplified detail.
- The parallel barrel walls terminate inside the wide base housing. An
  18-percent tint distinguishes the clear chamber from an empty frame.
- The coffee bed is a shallow solid ellipse at the chamber floor.
- The base's upper band is wider than the barrel. A broad front opening and
  smaller receding side openings leave unequal feet in the same curved skirt.
- A smaller negative-space circular pivot and straight angled paddle sit on
  the front-right foot. The large outline and exterior spout from earlier
  attempts are removed.

`pulsar.svg` uses seven direct paths and one tint color, with reduced opacity
on the barrel and dispersion surface. `equipment_pulsar.xml` exports the same
paths, fill rules and opacity to an Android vector in this study folder.

`preview.png` compares v4 and this study at 24, 28 and 34 physical pixels,
and shows 48dp badge mockups at 2.625 pixels per dp. `pulsar-1024.png` is an
enlarged SVG render. These are rendered previews, not Android captures.
`report.json` records source hashes and raster bounds.

`native/light.png` and `native/dark.png` capture the production Compose icon
and badge components on Android API 36 at 420dpi, using the real production
resource without overlays. Both existing capture tests passed, including
24/28/34/44dp bounds and visibility checks. Native captures also show the
three unchanged method icons for context. This is component rendering proof,
not a full brewing workflow test. The disposable test packages were removed.
`native/validation.json` links the captures to exact SVG, XML and APK hashes.

The SVG exporter records whether the production resource matches the study
export byte for byte. It exports into this folder; copying that XML to the
production resource is a separate deliberate operation. The historical
`vectorize_equipment_icons.py` raster pipeline predates this approved artwork
and must not be used to regenerate the Pulsar resource from its old raster.

```powershell
uv run --offline --with pillow==12.3.0 python tools/render_pulsar_concept.py --version 6
```
