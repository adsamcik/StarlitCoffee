# Brewing-method icon refinement

Four production method icons have authored SVG sources in `svg/`: Pulsar,
AeroPress, Espresso and Chemex. The remaining equipment artwork retains its
existing traced source. Astra reviewed both the original silhouettes and the
new enlarged and small-size previews.

- Pulsar: wider clear chamber, shallow dispersion cap, raised valve base with
  feet, and a small front valve recess. The old tall narrow chamber and flat
  plinth resembled a lantern.
- AeroPress: narrower upper plunger, wider chamber, pressing top and broad
  bottom flange/filter cap. The previous solid tube hid the telescoping parts.
- Espresso: compact housing with a group head, shorter portafilter, separate
  cup/drip tray and offset bent steam wand. The wand and handle have a broad gap.
- Chemex: open funnel with an asymmetric pouring lip, pronounced collar/tie,
  and hollow, rounded lower glass carafe. The previous silhouette was filled in.

These are simplified equipment pictograms, not scale drawings. Front/side
views omit logos, markings and fine details that disappear at small sizes.
Sources for the defining equipment features:

- [NextLevel Pulsar product photos](https://nextlevelbrewer.com/shop/nextlevel-pulsar-brewer/)
- [AeroPress Original product photos](https://aeropress.com/products/aeropress-coffee-maker)
- [Breville Bambino components guide](https://assets.breville.com/BES450/BES450_USCM_IB_M26_LR.pdf)
- [Chemex Classic six-cup product photos](https://chemexcoffeemaker.com/collections/classic-series-nav/products/six-cup-classic-chemex)

## Source and reproduction

The SVGs use only solid black paths and transparent cutouts, without strokes,
transforms or raster data. Their 32-unit square viewport keeps the maximum
silhouette dimension near the original 81% optical scale. Intrinsic Android
size remains 24dp. Existing `EquipmentVisual` mappings, labels, semantics and
theme tint apply everywhere these resources are used.

```powershell
uv run --offline --with pillow==12.3.0 python tools/render_method_icons.py
```

The renderer requires ImageMagick and Segoe UI, exports the four production
VectorDrawables, records hashes in `report.json`, and renders PNGs directly from
the SVG paths. `comparison.png` includes immutable previous-art snapshots from
`before/`, enlarged artwork and 48px badge simulations. `small-sizes.png` shows
actual 24/28/34/44px samples. The `*-1024.png` files are rendered from vectors,
not enlarged bitmaps.

The legacy `tools/vectorize_equipment_icons.py` runner preserves these four
authored production resources during tracing and then runs the authored
exporter. Its old comparison/report files describe the original raster traces;
pixel overlap with those discarded shapes is not a quality gate for this redraw.

## Android capture

An opt-in capture fixture uses the production `EquipmentVisual.method`,
`EquipmentIcon` and 48dp `EquipmentVisualBadge` components:

```powershell
.\gradlew.bat -I tools/method_icon_refinement/native.init.gradle :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --console=plain
```

It uses the disposable `.methodiconcheck` application ID and adds no fixtures
or source sets to ordinary app builds. Install its app/test APKs and run
`com.adsamcik.starlitcoffee.ui.component.MethodIconCaptureTest` with the Android
instrumentation runner. Outputs are in the disposable app's external files
directory under `method-icon-refinement/`. The two checks capture light/dark
artwork and verify all four icons are displayed at 24/28/34/44dp without bounds
drift. Visual judgment still requires inspecting the captures.

Validated on 2026-10-02 using an Android 16/API 36 x86_64 emulator at 420dpi
(2.625 pixels/dp). Both artwork capture checks and the existing
`EquipmentLayoutTest.narrowLayoutAndDoubleTextSizeUseOneReadableColumn` passed:
**3 tests, 0 failures**. Inspected the actual light/dark captures to confirm the
glass openings, cup handle, front valve recess and wand separation render
correctly with theme tint. Dynamic color was disabled for repeatable captures.

- [Native light theme](native/light.png)
- [Native dark theme](native/dark.png)
- [Before/after and family comparison](comparison.png)
- [Actual small pixel sizes](small-sizes.png)

The final drawable revision passes the debug app and Android-test APK builds.
The disposable preview app/test package is removed after capture and ordinary
debug outputs are restored. This is focused artwork/layout validation, not a
full regression suite, physical-device check or release validation.
