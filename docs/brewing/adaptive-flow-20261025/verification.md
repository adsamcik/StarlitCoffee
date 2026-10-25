# Verification — 2026-10-25

The modular presentation is implemented and independently reviewed. Execution plans, reducers, clock origins, persistence, completion commands and recipe release gates remain authoritative.

## Automated checks

- Final Gradle batch passed with the repository wrapper and Android Studio JDK21: focused testDebugUnitTest, Detekt, assembleDebug and assembleDebugAndroidTest.
- 168 focused JVM tests across26 suites passed: zero failures, errors or skips. The set covers guidance policy/projection, source matching, release/illustration gates, normalized presentation and live selection.
- Focused regressions cover independently omitted prose/imagery, target-only and caption-only data, sparse authored densities, valid siblings with missing guidance, common Switch content, unchanged physical target meaning, required warnings and stable reader identity.
- Existing all-default source contracts retain their artwork revisions. Newly authored operational targets/controls change the revision, preventing stale artwork admission.
- Git diff checks passed. Generated artwork, frozen recipe/source data and QC ledgers were not modified.

## Native rendering and interaction

Used only the existing stable API37 emulator emulator-5554 (preview SDK0). Debug/test applications use the isolated .minimalrefine suffix. No production application data was reset, no AVD was created/cloned, no older API ran, and no global font/display setting changed. Final device settings remain1080×2424, density420, font_scale1.0.

Eighteen unique scenarios passed across the native batches:

| Group | Cases | Observed behavior |
| --- | --- | --- |
| MinimalBrewScreenTest | 6 | Normal/200% Basic Timer and guided pour retain bloom, target and primary action; Help/Recipe preserve live stage/time and emit no completion event; completed Recipe access and rejected-pause recovery remain usable. |
| MinimalBrewAccessibilityTest | 5 | Source/custom timers keep distinct labels, long clocks retain units, unknown origins remain unknown, named controls/targets remain reachable and checked text layouts fit at200%. |
| VisualGuideReaderTest | 2 | Safety/physical operations remain available while Why is collapsed; reader step picker/bookmark survive restoration without live timer controls. |
| AdaptiveBrewContentTest | 3 | Target-only320dp/200%, image-only with admitted artwork, and omitted teaching content with an independently authorized Brew action render without empty optional sections. |
| ReviewedGuideNativeUiTest | 2 | Chemex quantities/Brew entry and Switch volume/post-fill clock origins remain correct; approved images and normal/200% navigation remain reachable. |

The initial batch passed17/18; the image-only test fixture used an invalid hyphenated stable ID and was corrected to lower_snake_case. After the final stable-ID selection change, all six live-screen cases, both reviewed-reader cases and the corrected image-only case were rerun against the matching final APKs:9/9 passed in122.346s. The unchanged component/accessibility results were reused, yielding18 unique passing scenarios.

Large text uses fixture-local200% scaling, with320dp cases for the affected narrow layouts. Popup sheets retain the emulator's normal window font configuration; navigation from a200% live view does not establish200% popup typography.

## Review and visual inspection

One independent review-only agent approved the final implementation and actual results. Concrete findings were resolved: unfamiliar warnings on known IDs stay live; reader safety survives omitted copy; new operational fields invalidate old artwork; raw-to-normalized selection follows content IDs instead of shifted indices.

Actual native captures were inspected for normal/200% timer and pour, the narrow Switch reader, target-only200% content and the image-only reader. Timer/target/action hierarchy, readable wrapping, reachable navigation, complete images and absent empty modules were checked. The [before/after gallery](screenshots.md) preserves the unchanged7caf5278 baseline alongside finished captures. No UI pixels were redrawn.

Full-resolution screenshots and execution/JUnit logs remain in the task checkout's ignored build directories; selected captures are committed with this report.

## Limits

This is emulator/native component and interaction evidence for the listed scenarios, not a traversal of every recipe or physical brewing. No physical-device test, full TalkBack session, localization approval or release certification is claimed. Previously reviewed generated artwork is reused; this architecture change did not generate or readmit artwork. No remote publication or deployment occurred.
