# Pulsar icon for small sizes

Preview only, following the user's feedback that v4 is closer but still needs
refinement and must work as a smaller icon. The app keeps its original icon.

The construction references remain those recorded in
[v4](../method-icon-concept-v4/README.md). This is an optical simplification of
the same open dispersion cap, transparent barrel, flat bed and rotary valve.

Astra recommended a shallower cap, wider chamber sides, merging the bed and
base, and bringing the paddle onto the exterior silhouette. Root authored
these revised paths for 24dp first:

- Cap depth reduced to 5.55 units, with a broad open well. Individual holes
  are omitted rather than shrunk into indistinct marks.
- Chamber sides increased to 1.8 units (1.35 pixels in a 24px render).
- The bed and base share one filled shape; the hairline crescent is removed.
- Two foot openings are at least 2.4 units wide and 3 units tall.
- The straight paddle joins a small pivot on the right silhouette, avoiding
  an internal outlined symbol with subpixel clearance.

The source is five filled paths in a 32-unit viewport. The 24dp XML export is
kept in this study folder. Previews include 24px/28px/34px samples at 1 pixel
per dp, plus app badge mockups at 2.625 pixels per dp. They are rendered from
the SVG, not captured in Android.

The previous and revised sources are rendered at the same pixel sizes side
by side on the preview. The exported XML passed AAPT2 resource compilation;
this checks syntax rather than visual quality or native rendering.

```powershell
uv run --offline --with pillow==12.3.0 python tools/render_pulsar_concept.py --version 5
```
