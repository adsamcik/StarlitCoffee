# Verification — minimal brewing UI, 2026-10-25

The live body now consumes normalized `LiveBrewContent`. It gives the clock, current action, target and relevant admitted visual the main hierarchy; the bloom is a smaller companion. The live dose line is removed, with full recipe quantities in Help. Reader targets, progress, navigation and disclosures use flatter sections. Session events, source/asset admission, physical clocks and saved-reader state retain their existing owners.

The supplied content determines which slots appear. Missing teaching content does not hide the authoritative target, safety or primary action, and omitted imagery reserves no empty panel. Existing generated, QC-admitted artwork is reused; no assets, frozen source recipes or admission records changed.

## Build and automated checks

The repository wrapper and existing Android Studio JDK21 passed `testDebugUnitTest`, `detekt`, `assembleDebug` and `assembleDebugAndroidTest`. All 18 focused JVM cases passed with zero failures/errors/skips: content-source selection 3, primary action 3 and live-guidance projection 12. The unchanged architecture/source/asset gates retain their prior verification at 7f359754.

## Native rendering

Used only the existing stable API37 emulator, `emulator-5554`, preview SDK0. The `.minimalrefine` debug/test packages isolate these checks from production data. No AVD was created/cloned, no older API ran and no app data was reset. Large-text tests use fixture-local density and 200% scaling, with a 320dp narrow viewport; normal captures are 411.43 dp. The emulator settings remain unchanged.

The six matched before cases passed in 156.287s; the six after cases passed in 125.87s. Full, partial and missing guidance were captured at both sizes using the same frozen 2:10 Pulsar stage with 340 g target and completed bloom. The after checks retain timer/target/bloom/primary action, absent optional prose/images, uncut text layouts and exactly one physical confirmation event.

The additional 18 affected native cases passed in 428.928s, giving 24 passing after scenarios in total:

| Group | Cases | Observed result |
| --- | --- | --- |
| MinimalBrewScreenTest | 6 | Basic/guided essentials at normal/200%; Help/Recipe retain stage/time and emit no completion event; completed Recipe access and rejected-pause recovery work. |
| MinimalBrewAccessibilityTest | 5 | Long clocks retain units; source/custom timers remain distinct; unknown origins remain unknown; named 48dp controls and checked text layouts fit at 200%. |
| VisualGuideReaderTest | 2 | Bookmarks survive restoration; essential controls and warnings remain available with Why collapsed. |
| AdaptiveBrewContentTest | 3 | Target-only 200%, admitted image-only and omitted teaching content remain usable without empty modules. |
| ReviewedGuideNativeUiTest | 2 | Reviewed quantities/Brew entry remain available; the volume reader retains post-fill clock origins and reachable navigation. |

Static light/dark palette checks for main text, secondary text and primary-action foreground/background pairs range from 7.44:1 to 17.57:1. Dynamic system palettes were not numerically audited.

## Visual evidence and limits

The [before/after gallery](screenshots.md) contains 12 matched pairs, including the six availability cases, Basic Timer normal/200%, reviewed overview, volume reader 200%, target-only 200% and admitted image-only content. Dimensions match within every pair, every after capture differs from its before, and all 24 image links resolve. Screenshots preserve actual native pixels. Some reader captures intentionally show the scrolled position selected by the existing interaction test.

The emulator remains 1080×2424, density 420 and font_scale1.0. Popup sheets retain the emulator window font configuration; navigation from a 200% live view does not prove 200% popup typography. No physical-device test, full TalkBack session, exhaustive recipe/localization traversal or release certification is claimed.

Raw build/JUnit/native logs and full capture outputs remain in the task checkout's ignored build directories. No remote publication or deployment occurred.

One independent review-only agent reviewed the final diff, actual verification results and all selected before/after renders plus raw Help/Recipe captures. The review approved completion with no findings or blockers.
