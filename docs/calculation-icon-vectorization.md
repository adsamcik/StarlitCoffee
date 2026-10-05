# Calculation icon vectorization

The coffee-dose, water-input, and cup-output VectorDrawables are mechanically
derived from ImageGen output. Their contours were not authored or corrected by
hand.

## Provenance

| Icon | Immutable ImageGen source | Reviewed raster reference | Selected trace |
| --- | --- | --- | --- |
| Coffee dose | `docs/assets/calculation-icon-coffee-dose-imagegen-source-v2.png` | `docs/assets/calculation-icon-coffee-dose-raster-reference-v2.webp` | `docs/assets/calculation-icon-coffee-dose-traced.svg` |
| Cup output | `docs/assets/calculation-icon-cup-output-imagegen-source-v2.png` | `docs/assets/calculation-icon-cup-output-raster-reference-v2.webp` | `docs/assets/calculation-icon-cup-output-traced.svg` |
| Water input | `docs/assets/calculation-icon-water-input-imagegen-source-v2.png` | `docs/assets/calculation-icon-water-input-raster-reference-v2.webp` | `docs/assets/calculation-icon-water-input-traced.svg` |

The same asset directory retains the first-pass sources and raster references
without the `-v2` suffix as design-history evidence. They are not generator
inputs; the reviewed `-v2` files above are the immutable production sources.

The generated concepts are deliberately single-colour and use different
silhouettes: two coffee beans with broad grooves for dry coffee, a zero-hole
upright drop for brew water, and a cup with a substantial filled region for
collected beverage. ImageMagick deterministically extracts the reviewed masks.
VTracer then fits cubic Bézier contours at several tolerances. The smallest
candidate that meets the fidelity and full-resolution topology gates becomes
the Android vector.

## Rebuild

From the repository root:

```powershell
uv run --with vtracer==1.0.0a3 --with pillow==12.3.0 --with numpy==2.3.5 python tools\vectorize_calculation_icons.py
```

The script verifies that the rebuilt masks exactly match the reviewed raster
alphas before tracing. It evaluates the result at 18, 20, 24, and 32 pixels and
requires:

- soft alpha intersection-over-union of at least 0.94;
- binary intersection-over-union of at least 0.95 through 24 px and 0.94 at
  32 px, where a one-pixel shift along the cup's long liquid edge has a larger
  proportional effect;
- 95th-percentile boundary error no greater than one pixel; and
- identical full-resolution connected-component and hole counts.

Target-size topology is recorded as a diagnostic after ignoring features under
four pixels; the pixel previews remain the authority for recognition because
antialiasing can change whether a broad groove is technically open or closed.

The chosen coffee trace uses a 2.0 px simplification tolerance, two paths, and
27 SVG commands. The chosen cup trace uses a 1.0 px tolerance, one path, and 24
SVG commands. The chosen water trace uses a 2.0 px tolerance, one path, and only
7 SVG commands. All three preserve full-resolution topology and pass every
target-size fidelity gate.
Exact candidate measurements are recorded in
`docs/assets/calculation-icon-vector-report.json`; the visual comparison is in
`docs/assets/calculation-icon-vector-comparison.png`. Actual 18, 20, 24, and 32
pixel rasters are enlarged without smoothing in
`docs/assets/calculation-icon-small-size-comparison.png`.

The resulting app resources are:

- `app/src/main/res/drawable/calculation_icon_coffee_dose.xml`
- `app/src/main/res/drawable/calculation_icon_water_input.xml`
- `app/src/main/res/drawable/calculation_icon_cup_output.xml`
