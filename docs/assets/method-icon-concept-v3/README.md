# Pulsar vector redraw

Rejected by the user on 2026-10-02 as not resembling the real brewer. This
study still treats the cap as a solid lid and oversimplifies the base and
rotary handle. It was created after the user challenged the previous
raster concept's resemblance to their supplied Pulsar photograph. The app
continues to use its original icon.

Astra authored `pulsar.svg` directly as monochrome vector paths. Root supplied
measurements from the user's photograph, compared the render to that photo,
and refined the valve clearance to preserve the foot below it and keep its
surrounding gap even. The chamber sidewalls overlap the cap to avoid fine
seams at their join. Astra inspected
the earlier manufacturer's photo and original Brewing sets screenshot for
part construction and style. The central relationships are a shallow cylindrical cap,
a taller straight glass chamber, a raised base with an open space between its
feet, and a front-right round valve pivot with an angled lever. Texture, coffee
grounds and measurement marks are omitted for small-size readability.

`preview.png` contains a direct enlarged vector render, 48dp badge mockups at
2.625 pixels per dp, and 24px/34px renders. These are SVG renders, not native
Android captures. `pulsar-1024.png` is rendered directly from the vector.
`equipment_pulsar.xml` exports those same paths as a 24dp Android vector into
this study folder, not the app. `report.json` records hashes and bounds.

The final exported XML passed AAPT2 resource compilation. This validates the
resource syntax; the preview has not been captured in the Android app.

Regenerate with:

```powershell
uv run --offline --with pillow==12.3.0 python tools/render_pulsar_concept.py
```

No image generator was used for this redraw. The vector and Android export
remain editable; neither technical rendering checks nor agent review establish
that the user accepts the design.
