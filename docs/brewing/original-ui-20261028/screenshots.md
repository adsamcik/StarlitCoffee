# Original brewing UI restored on modular content

Before: verified native renders at 54b88926 (also the presentation at task baseline f4801d32). After: this restoration. Both use the same frozen source recipes. Images are unmodified native pixels.

Visual reference: the centered BrewTimerScreen in b44781a2^, before the minimal brewing-screen rework: bloom above the main target, compact elapsed time, supplied guidance card, and Finish with a circular physical-pause control. Current durable sessions and optional content modules remain authoritative.

Normal captures use the existing emulator viewport (~411 dp). Large live captures use fixture-local 200% text; the six guidance-availability cases also constrain width to 320 dp. This does not claim system-scaled popup typography.

| Flow | Before | After |
| --- | --- | --- |
| Basic Timer / normal | ![Before basic-timer](evidence/before/basic-timer.png) | ![After basic-timer](evidence/after/basic-timer.png) |
| Basic Timer / 200% | ![Before basic-timer-200](evidence/before/basic-timer-200.png) | ![After basic-timer-200](evidence/after/basic-timer-200.png) |
| Guided pour / full / normal | ![Before minimal-ui-full](evidence/before/minimal-ui-full.png) | ![After minimal-ui-full](evidence/after/minimal-ui-full.png) |
| Guided pour / full / 200% | ![Before minimal-ui-full-200](evidence/before/minimal-ui-full-200.png) | ![After minimal-ui-full-200](evidence/after/minimal-ui-full-200.png) |
| Guided pour / partial / normal | ![Before minimal-ui-partial](evidence/before/minimal-ui-partial.png) | ![After minimal-ui-partial](evidence/after/minimal-ui-partial.png) |
| Guided pour / partial / 200% | ![Before minimal-ui-partial-200](evidence/before/minimal-ui-partial-200.png) | ![After minimal-ui-partial-200](evidence/after/minimal-ui-partial-200.png) |
| Guided pour / missing / normal | ![Before minimal-ui-missing](evidence/before/minimal-ui-missing.png) | ![After minimal-ui-missing](evidence/after/minimal-ui-missing.png) |
| Guided pour / missing / 200% | ![Before minimal-ui-missing-200](evidence/before/minimal-ui-missing-200.png) | ![After minimal-ui-missing-200](evidence/after/minimal-ui-missing-200.png) |

Paused controls (no matching pre-restoration fixture exists): [normal](evidence/after/original-basic-paused.png), [200% text](evidence/after/original-basic-paused-200.png). Finish stays available when permitted; the centered Play button resumes the physical clock. [Pressed guided action](evidence/after/primary-pressed.png) is captured before canceling the press without dispatching completion.
