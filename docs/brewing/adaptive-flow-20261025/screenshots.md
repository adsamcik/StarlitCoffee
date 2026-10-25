# Native screenshots

Actual Android API37 renders. Before: unchanged base 7caf5278. After: adaptive presentation implementation. Live layout intentionally retains its established hierarchy.

| Screen | Before | After |
| --- | --- | --- |
| Basic Timer | ![Basic Timer before](evidence/before/basic-timer.png) | ![Basic Timer after](evidence/after/basic-timer.png) |
| Guided pour | ![Guided pour before](evidence/before/guided-pour.png) | ![Guided pour after](evidence/after/guided-pour.png) |
| Basic Timer, 200% at320dp | ![Large-text Basic Timer before](evidence/before/basic-timer-200.png) | ![Large-text Basic Timer after](evidence/after/basic-timer-200.png) |
| Guided pour, 200%, cue scrolled into view | ![Large-text guided pour before](evidence/before/guided-pour-200-cue.png) | ![Large-text guided pour after](evidence/after/guided-pour-200-cue.png) |
| Chemex reader | ![Chemex reader before](evidence/before/chemex.png) | ![Chemex reader after](evidence/after/chemex.png) |
| Switch reader, 200% at320dp | ![Switch reader before](evidence/before/switch-200.png) | ![Switch reader after](evidence/after/switch-200.png) |

## Optional-content examples

Native fixtures feed the same production reader directly. They demonstrate independently supplied content rather than alternate screen implementations.

![Target-only reader at200%](evidence/after/targets-only-200.png)

![Image-only reader with admitted artwork](evidence/after/image-only.png)
