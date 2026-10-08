# Pulsar icon from its construction

Preview only. This study follows the rejected v3 icon. The app retains its
original method icon; the study is not accepted replacement artwork.

## Mechanical references

Primary references checked on 2026-10-02:

- [Jonathan Gagné's design explanation](https://coffeeadastra.com/2023/09/13/the-pulsar-dripper/)
- [Manufacturer's user guide](https://nextlevelbrewer.com/wp-content/uploads/2023/06/PulsarUserGuide.pdf)
- [Dispersion cap](https://nextlevelbrewer.com/shop/pulsar-dispersion-cap/)
- [Rotary valve](https://nextlevelbrewer.com/shop/pulsar-valve/)
- [Base](https://nextlevelbrewer.com/shop/pulsar-base/)

Water enters an open pouring well and passes through its recessed perforated
dispersion plate. This spreads water over the coffee with less agitation. The
clear plastic barrel seats on a flat filter in the base, preventing water
from passing around the bed. The filter rests on support ridges; water below
it drains through the base. A cylindrical rotary valve closes or restricts
that outlet: closing retains water for immersion, opening permits gravity
percolation. The handle is a rectangular paddle on the valve end. The raised
base supports the brewer over a receiving vessel, with openings between feet.
It has no plunger or sealed top lid.

The manufacturer's starter guide uses a closed-valve bloom; Gagné's recipe
briefly starts with the valve open before closing it. Those are recipe
choices using the same mechanism, not different hardware.

## Design interpretation

Astra inspected the maker's and designer's photographs, including the cap
removed, standalone valve and cap, and assembled brewers in use. Those
reference images are temporary build inputs and are not committed here.

`imagegen-construction-study.png` is an additional raster concept made with
the built-in image generator using the original UI as a style reference and
the assembled brewer, dispersion cap and valve photos as shape references.
The exact prompt is in `imagegen-prompt.txt`. It suggests curved planes but
adds shading, reflections and excessive top-ellipse depth; it is not the
final vector or production artwork.

The new vector uses a slightly elevated view to expose the pouring well and
dispersion plate, a broad flat coffee bed, and a valve attached to the raised
base. Markings, plastic reflections, granules and the full hole pattern are
simplified for small-size readability. This interpretation is a proposed icon,
not a diagram of every internal passage.

Root refined the chamber/base joins and valve clearance after rendering
Astra's first complete vector. The coffee bed now meets a thinner smooth rim;
the valve stays inside the footprint of its supporting front foot.

`pulsar.svg` is editable single-color artwork; `equipment_pulsar.xml` exports
the same paths as an Android vector into this study folder. `preview.png`
shows an enlarged render, 48dp badge mockups at 2.625 pixels per dp, and
24px/34px samples. `pulsar-1024.png` is rendered directly from the SVG.
`report.json` records hashes and bounds. These previews are SVG renders,
not captures from the Android app.

The exported XML passed AAPT2 resource compilation. That check establishes
resource syntax, not aesthetic acceptance or native Android rendering.

```powershell
uv run --offline --with pillow==12.3.0 python tools/render_pulsar_concept.py --version 4
```
