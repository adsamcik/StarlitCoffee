# Approved image-generator Pulsar icon

The user accepted the photo-based image-generator design and its subsequent
perspective pass, then requested vectorization, production installation,
native size checks and a commit. This replaces the rejected hand-authored v15.

`source.png` is an unchanged copy of the perspective-polished generated PNG
(`exec-d2e758fb-0aa7-490c-93d0-88aa1fabcf7a.png`). The built-in image generator
used the first generated icon as its edit target and the user's assembled
white Pulsar photograph as its supporting reference. The exact edit prompt is
in `perspective-prompt.txt`; the output requested a transparent background.

`tools/vectorize_pulsar_imagegen.py` extracts the alpha silhouette at 128,
crops it, uniformly scales it to fit 832 pixels in a 1024-pixel square, and
centres it to match the equipment family. This removes faint alpha noise and
colour variation while retaining the generated geometry. No contour coordinates
are authored or adjusted by hand. VTracer fits the curves; candidates must
retain source components/holes and pass the existing target-size fidelity gates.

`pulsar.svg` contains the selected trace; `equipment_pulsar.xml` exports those
paths unchanged into a tintable 24 dp Android vector. Production uses the
identical XML at `app/src/main/res/drawable/equipment_pulsar.xml`. The manifest's
approved-vector override protects it from retracing the rejected raster source.
The other equipment resources are unchanged.

The selected trace uses four filled paths and 103 commands at a 2.0-pixel
fitting tolerance. All four source components and four holes survive at full
resolution. Soft silhouette overlap is 97.6–97.7% at 63, 74, 89 and 116 pixels
(24, 28, 34 and 44 dp on the capture device), with 95% of boundary pixels at
most one pixel from the source mask. SVG and Android path strings are identical,
and production XML is byte-identical to the saved export.

`comparison.png` compares the normalized generated silhouette and trace, with
24, 28, 34 and 44 dp simulations at 420 dpi. `report.json` records normalization,
hashes, topology and raster fidelity. These sheets are SVG renders; native
Android captures and their validation details are recorded separately.

## Native validation

The isolated production capture app built successfully in 2m 41s, without
resource overlays. `MethodIconCaptureTest` passed both light/dark checks in
14.125 seconds on a read-only Medium Phone emulator (API 36, 420 dpi). It
captures the actual `EquipmentIcon` and `EquipmentVisualBadge` components,
checks 24, 28, 34 and 44 dp dimensions, and renders the 48 dp selected badge.
The collar/chamber remain clear at all four sizes; the fine valve outline is
small at 24 dp, as in the generated source. The native captures were inspected
in both themes. This is component validation, not a full app workflow test or
physical-device check.

The initial emulator stopped before installation. After restarting it, the
first instrumentation invocation exited before tests with `Process crashed`;
its crash buffer was empty and no cause was established. The second invocation
passed both tests. The successful screenshots, first-run output, final test
output and exact asset/APK hashes are saved in `native/`.

Ordinary debug/app-test outputs were subsequently rebuilt successfully in
2m 8s and retain `.debug` / `.debug.test` application IDs. Only the disposable
icon-check packages were used for validation; both were removed and the
dedicated read-only emulator was shut down after capturing the evidence.

Regenerate review assets (production installation is explicit):

```powershell
uv run --offline --with vtracer==1.0.0a3 --with pillow==12.3.0 --with numpy==2.3.5 python tools/vectorize_pulsar_imagegen.py
```
