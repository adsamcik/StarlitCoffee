# Brewing UI after Astra review — before and after

Actual native screenshots. Before is f0636ccd; after is this design pass. Pixels are unmodified. Normal live fixtures use 411.43dp/100% text; large live fixtures use 320dp/200% text. Both sets keep the same source recipe and frozen clock. Some reader captures show the existing fixture scroll position.

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
| Chemex preparation | ![Before Chemex preparation](evidence/before/chemex.png) | ![After Chemex preparation](evidence/after/chemex.png) |
| Volume reader / 200% | ![Before Volume reader / 200%](evidence/before/switch-200.png) | ![After Volume reader / 200%](evidence/after/switch-200.png) |
| Targets only / 200% | ![Before Targets only / 200%](evidence/before/targets-only-200.png) | ![After Targets only / 200%](evidence/after/targets-only-200.png) |
| Image only | ![Before Image only](evidence/before/image-only.png) | ![After Image only](evidence/after/image-only.png) |
| Primary pressed | ![Before Primary pressed](evidence/before/primary-pressed.png) | ![After Primary pressed](evidence/after/primary-pressed.png) |
| Basic Timer Help | ![Before Basic Timer Help](evidence/before/basic-help.png) | ![After Basic Timer Help](evidence/after/basic-help.png) |
| Guided Help | ![Before Guided Help](evidence/before/help.png) | ![After Guided Help](evidence/after/help.png) |
| Recipe | ![Before Recipe](evidence/before/recipe.png) | ![After Recipe](evidence/after/recipe.png) |
| Help / Android 200% | ![Before Help / Android 200%](evidence/before/help-200.png) | ![After Help / Android 200%](evidence/after/help-200.png) |
| Recipe / Android 200% | ![Before Recipe / Android 200%](evidence/before/recipe-200.png) | ![After Recipe / Android 200%](evidence/after/recipe-200.png) |

The normal Help/Recipe rows use the normal Android window font setting. The final two rows use actual Android 200% text, including the separate sheet windows. The emulator font setting was restored after each capture run.

Chemex preparation uses the existing generated, admitted transparent orientation detail after [independent binding review](chemex-binding-review.json). No art pixels were changed. The bloom remains the only non-icon image when live guidance is absent.
