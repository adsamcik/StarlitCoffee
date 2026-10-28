# Centered brewing UI

The selected Centered design replaces the normal presentation of BrewSessionScreen, BrewTimerScreen and BloomTimerScreen. All instruction copy and instructional illustrations are on demand in Help. The live surface retains the bloom, authoritative values, essential controls, required safety and recovery, and inputs needed to complete a physical stage.

Before images are unchanged captures from b2624181. After images are unchanged native Android captures of the final implementation. Normal uses the device density; large text uses a 320 dp viewport and 200% text inside the test composition, without changing system font settings.

| Screen | Before | Centered |
| --- | --- | --- |
| Basic timer | ![Before basic](evidence/before/basic-timer.png) | ![Centered basic](evidence/after/basic-timer.png) |
| Basic timer, large text | ![Before basic large](evidence/before/basic-timer-200.png) | ![Centered basic large](evidence/after/basic-timer-200.png) |
| Full guidance | ![Before full](evidence/before/minimal-ui-full.png) | ![Centered full](evidence/after/minimal-ui-full.png) |
| Full guidance, large text | ![Before full large](evidence/before/minimal-ui-full-200.png) | ![Centered full large](evidence/after/minimal-ui-full-200.png) |
| Partial guidance | ![Before partial](evidence/before/minimal-ui-partial.png) | ![Centered partial](evidence/after/minimal-ui-partial.png) |
| Partial guidance, large text | ![Before partial large](evidence/before/minimal-ui-partial-200.png) | ![Centered partial large](evidence/after/minimal-ui-partial-200.png) |
| No guidance | ![Before missing](evidence/before/minimal-ui-missing.png) | ![Centered missing](evidence/after/minimal-ui-missing.png) |
| No guidance, large text | ![Before missing large](evidence/before/minimal-ui-missing-200.png) | ![Centered missing large](evidence/after/minimal-ui-missing-200.png) |

## Retained routes and completed state

| Screen | Normal | Large text |
| --- | --- | --- |
| Legacy timer route, paused | ![Legacy timer](evidence/after/centered-legacy-timer.png) | ![Legacy timer large](evidence/after/centered-legacy-timer-200.png) |
| Legacy bloom route, paused | ![Legacy bloom](evidence/after/centered-legacy-bloom.png) | ![Legacy bloom large](evidence/after/centered-legacy-bloom-200.png) |
| Completed brew | ![Completed](evidence/after/centered-completed.png) | ![Completed large](evidence/after/centered-completed-200.png) |
| Durable timer, paused | ![Paused](evidence/after/centered-paused.png) | ![Paused large](evidence/after/centered-paused-200.png) |

The legacy routes use their real BrewViewModel with isolated transient state. Their captures retain the paused source clock; timings are not edited. The completed fixture represents an already persisted log and enables Done without a database write.

## Additional interaction states

![Pressed primary action](evidence/after/primary-pressed.png)

![Guided physical pause, large text](evidence/after/centered-guided-paused-200.png)

## Selected interactive design reference

These are browser captures of the accepted code-built mockup, not native screenshots or generated images.

| State | Normal | Large text |
| --- | --- | --- |
| Timer | ![Reference timer](reference/centered-timer.png) | ![Reference timer large](reference/centered-timer-large.png) |
| Target | ![Reference target](reference/centered-pour.png) | ![Reference target large](reference/centered-pour-large.png) |

[Verification](verification.md)
