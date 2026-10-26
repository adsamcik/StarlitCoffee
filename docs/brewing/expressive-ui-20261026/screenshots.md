# Expressive brewing UI — before and after

Actual native renders on the existing stable API37 emulator. Before captures are the verified 4019dcac layout, preserved from the preceding pass. After captures use the matching fixtures with this change. Screenshot pixels are unmodified.

Normal live captures are 411.43 dp at 100% text. Large live captures use 320 dp at 200% text through fixture-local density; device settings are unchanged. The six availability cases share a frozen 2:10 elapsed, 340g target and completed bloom.

| View | Before | After |
| --- | --- | --- |
| Basic Timer / normal | ![Before Basic Timer / normal](evidence/before/basic-timer.png) | ![After Basic Timer / normal](evidence/after/basic-timer.png) |
| Basic Timer / 200% | ![Before Basic Timer / 200%](evidence/before/basic-timer-200.png) | ![After Basic Timer / 200%](evidence/after/basic-timer-200.png) |
| Full guidance / normal | ![Before Full guidance / normal](evidence/before/minimal-ui-full.png) | ![After Full guidance / normal](evidence/after/minimal-ui-full.png) |
| Full guidance / 200% | ![Before Full guidance / 200%](evidence/before/minimal-ui-full-200.png) | ![After Full guidance / 200%](evidence/after/minimal-ui-full-200.png) |
| Partial guidance / normal | ![Before Partial guidance / normal](evidence/before/minimal-ui-partial.png) | ![After Partial guidance / normal](evidence/after/minimal-ui-partial.png) |
| Partial guidance / 200% | ![Before Partial guidance / 200%](evidence/before/minimal-ui-partial-200.png) | ![After Partial guidance / 200%](evidence/after/minimal-ui-partial-200.png) |
| Missing guidance / normal | ![Before Missing guidance / normal](evidence/before/minimal-ui-missing.png) | ![After Missing guidance / normal](evidence/after/minimal-ui-missing.png) |
| Missing guidance / 200% | ![Before Missing guidance / 200%](evidence/before/minimal-ui-missing-200.png) | ![After Missing guidance / 200%](evidence/after/minimal-ui-missing-200.png) |
| Reviewed reader / normal | ![Before Reviewed reader / normal](evidence/before/chemex.png) | ![After Reviewed reader / normal](evidence/after/chemex.png) |
| Volume reader / 200% | ![Before Volume reader / 200%](evidence/before/switch-200.png) | ![After Volume reader / 200%](evidence/after/switch-200.png) |
| Targets only / 200% | ![Before Targets only / 200%](evidence/before/targets-only-200.png) | ![After Targets only / 200%](evidence/after/targets-only-200.png) |
| Image only / normal | ![Before Image only / normal](evidence/before/image-only.png) | ![After Image only / normal](evidence/after/image-only.png) |

Basic Timer and missing guidance retain only the bloom as non-icon imagery. Full or partially supplied guidance keeps relevant admitted art when it exists. Help remains explicit access to source guidance.

[Native primary action while pressed](evidence/after/primary-pressed.png) demonstrates the Expressive shape state. The gesture is canceled, and the test verifies that pressing alone never confirms a physical brewing step. Spatial motion is additionally checked with controlled-clock intermediate bounds and interrupted disclosure reversal; static screenshots alone do not prove motion.

Some reader captures intentionally show the existing test scroll position. Popup sheets keep the emulator window font configuration; 200% live content does not establish 200% popup typography.

The normal reviewed-reader and image-only idle captures intentionally match the baseline. Their changes are press feedback and transition motion; the other ten idle comparison pairs show visible layout changes.
