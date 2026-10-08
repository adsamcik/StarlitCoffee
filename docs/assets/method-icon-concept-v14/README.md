# Pulsar: five additional iterations

The user subsequently rejected the valve depiction. The corrected upright
assembly, based on their new photograph, is in [v15](../method-icon-concept-v15/README.md),
which supersedes this production source. The captures here remain evidence for v14.

The user requested five more iterations after v9, prioritizing resemblance to
the actual brewer while retaining the earlier bold filled icon family. Root
authored each pass after a separate Astra review of the preceding rendered
pass and the manufacturer's front brewing photo. References are recorded in
[v4](../method-icon-concept-v4/README.md).

| Pass | Source | Change |
| --- | --- | --- |
| 1 | [v10](../method-icon-concept-v10/pulsar.svg) | Flatter collar underside and matching barrel seam |
| 2 | [v11](../method-icon-concept-v11/pulsar.svg) | Wider barrel and chamber, retaining filled side thickness |
| 3 | [v12](../method-icon-concept-v12/pulsar.svg) | Deeper continuous base band above the openings |
| 4 | [v13](../method-icon-concept-v13/pulsar.svg) | Valve centred vertically within the opening |
| 5 | [v14](pulsar.svg) | Flatter, mirrored contacts on the narrow outer feet |

The fifth pass reduces the two outer feet's bottom corner radii to about
0.42 units and increases each flat contact from 0.63 to 1.18 units. It retains
the previous pass's silhouette dimensions, central foot, chamber and valve.
`revision.json` in each folder records its predecessor and geometry changes.

`five-iterations.png` shows the starting point and all five passes, including
24/34/44 physical-pixel samples. `iteration-comparison.png` compares passes
4 and 5; `before-after.png` compares the starting point with the fifth pass.
`family-comparison.png` compares v9 and v14 beside the unchanged
original AeroPress, Espresso and Chemex assets. `preview.png` includes 48 dp
badge simulations at 2.625 px/dp. These sheets are direct SVG renders.

The four opaque filled paths in `pulsar.svg` export to `equipment_pulsar.xml`.
Production uses that XML. The equipment manifest points to it, preserving the
authored paths during batch generation. Only the Pulsar production asset changes.

In Astra's final review, the 34 px render retains distinct foot openings and a
short horizontal valve. At 24 physical pixels, the valve reads mainly as an
asymmetry in the right opening. Small pictograms omit the mechanism's finer
details. Technical validation does not establish the user's aesthetic acceptance.

```powershell
uv run --offline --with pillow==12.3.0 python tools/render_pulsar_concept.py --version 14
```

## Android validation

The production-resource fixture APK build passed in 1 minute 55 seconds using
`tools/method_icon_refinement/production.init.gradle`, without resource overlays.
On Medium Phone, API 36, 420 dpi, the first instrumentation invocation exited
before tests with "Process crashed"; its cause was not established. The same
installed APKs passed both light and dark tests on retry in 16.965 seconds.
Both invocation outputs are retained in `native/`.

`native/light.png` and `native/dark.png` show the production `EquipmentIcon`
and `EquipmentVisualBadge` components at 24/28/34/44 dp and a 48 dp badge.
`native/validation.json` records source and APK hashes and the validation scope.
The disposable test packages were removed after captures. The read-only emulator
was retained because concurrent installation of the normal debug app was observed.

These captures verify rendering in a Compose fixture; they do not establish
full application workflow or aesthetic acceptance.
The ordinary debug and Android test APK build then passed in 1 minute 16 seconds,
restoring the normal debug application IDs in the build outputs.
