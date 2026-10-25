# Brewing UI — before and after

Actual API37 native renders; screenshots are not mockups. Before is the unchanged layout at 7f359754 (with only shared renderer extraction for the six availability cases). After is this visual redesign. No screenshot pixels are redrawn.

The live matrix uses one frozen reviewed Pulsar stage: 2:10 elapsed, 340 g cumulative target and a completed bloom. Full includes its admitted water-height visual; partial keeps its supplied operation text and target without artwork; missing omits teaching content while retaining the authoritative action, target and controls. Normal is approximately 411 dp at 100%; large text is 320 dp at 200%, using fixture-local density without changing emulator settings.

| Guidance / text | Before | After |
| --- | --- | --- |
| Full / normal | ![Before Full / normal](evidence/before/minimal-ui-full.png) | ![After Full / normal](evidence/after/minimal-ui-full.png) |
| Partial / normal | ![Before Partial / normal](evidence/before/minimal-ui-partial.png) | ![After Partial / normal](evidence/after/minimal-ui-partial.png) |
| Missing / normal | ![Before Missing / normal](evidence/before/minimal-ui-missing.png) | ![After Missing / normal](evidence/after/minimal-ui-missing.png) |
| Full / 200% | ![Before Full / 200%](evidence/before/minimal-ui-full-200.png) | ![After Full / 200%](evidence/after/minimal-ui-full-200.png) |
| Partial / 200% | ![Before Partial / 200%](evidence/before/minimal-ui-partial-200.png) | ![After Partial / 200%](evidence/after/minimal-ui-partial-200.png) |
| Missing / 200% | ![Before Missing / 200%](evidence/before/minimal-ui-missing-200.png) | ![After Missing / 200%](evidence/after/minimal-ui-missing-200.png) |

The following before captures reuse the matching verified 7f359754 renders already committed with the architecture work. The after captures use the same existing native fixtures. Popup sheets keep the emulator window font configuration; 200% live content does not establish 200% popup typography.

| Flow | Before | After |
| --- | --- | --- |
| Basic Timer / normal | ![Before Basic Timer / normal](evidence/before/basic-timer.png) | ![After Basic Timer / normal](evidence/after/basic-timer.png) |
| Basic Timer / 200% | ![Before Basic Timer / 200%](evidence/before/basic-timer-200.png) | ![After Basic Timer / 200%](evidence/after/basic-timer-200.png) |
| Reviewed overview / normal | ![Before Reviewed overview / normal](evidence/before/chemex.png) | ![After Reviewed overview / normal](evidence/after/chemex.png) |
| Volume reader / 200% | ![Before Volume reader / 200%](evidence/before/switch-200.png) | ![After Volume reader / 200%](evidence/after/switch-200.png) |
| Targets only / 200% | ![Before Targets only / 200%](evidence/before/targets-only-200.png) | ![After Targets only / 200%](evidence/after/targets-only-200.png) |
| Image only / normal | ![Before Image only / normal](evidence/before/image-only.png) | ![After Image only / normal](evidence/after/image-only.png) |
