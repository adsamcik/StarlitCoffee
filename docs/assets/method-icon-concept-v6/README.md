# Pulsar silhouette correction

Preview study after the user rejected v5's likeness. This version has not
been accepted as replacement artwork. The app's original icon remains in use.

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

The exported Android XML passed AAPT2 resource compilation (4,108-byte
compiled resource). This validates resource syntax, not native visual quality.

```powershell
uv run --offline --with pillow==12.3.0 python tools/render_pulsar_concept.py --version 6
```
