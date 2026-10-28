# Original brewing UI on modular content

Restored the centered BrewTimerScreen treatment from b44781a2^, before this thread's minimal redesign. Task baseline is f4801d32. The stated assumption was the centered bloom/target original rather than the earlier durable-session timing-card layout; no contrary clarification arrived.

The bloom sits above a light target number with a smaller unit, elapsed time is compact, and Basic Timer retains Finish plus a centered Pause/Play control. Supplied instruction copy uses the original guidance-card treatment. Time remains primary outside pouring stages; other targets remain subordinate. Help/Recipe, modular full/partial/missing content, approved art, durable timing, explicit confirmation, warnings and recovery remain intact. Source-derived bloom time is independent of sprite availability. No persistence, dependency, SDK, ML or artwork-pixel changes.

## Verification

| Check | Actual result |
| --- | --- |
| Focused JVM tests | 28 passed: 14 live projection, 8 bloom/quantities, 3 action mapping, 3 Help preservation. Passed again in the affected batch; unchanged helper results reused after typography-only edits. |
| Detekt and both APK builds | Passed in restore-original-final-build.log, 11m52s. |
| Final app typography / paragraph widths | Detekt and app APK passed in restore-original-typography-build.log, 9m48s. |
| Final corrected instrumentation APK | Detekt and corrected test APK passed in restore-original-final-test-build.log, 10m41s. Capture-helper bytecode includes the new window-idle wait. |
| Native initial run | 19 scenarios; 11 passed. Six strict centered-text bounds failures led to production fillMaxWidth fixes. Two stale a11y assertions were corrected to separate countdown wording and unmerged glyph bounds. |
| Final normal/large matrix | 8/8 passed in 70.378s, restore-original-final-matrix.log: Basic x2 plus full/partial/missing x2. Includes Pause/Resume, permitted paused Finish and press/cancel without confirmation. |
| Final targeted follow-up | 3/3 passed in 18.295s, restore-original-final-followup.log: non-pour glyph hierarchy, separate countdown/reminder, partial/200. An additional direct-window partial case passed (11.393s). All 19 unique scenarios have passing results across the focused runs; this is not one 19-pass batch. |

Logs and raw captures remain in the task worktree's ignored build directory. Native fixtures use frozen real recipe snapshots, a mock dispatch boundary and in-memory state; they do not touch user databases, stock or alarms. They establish rendering and event wiring, not a physical-device or persisted end-to-end brew.

Only the existing stable API 37 Android_17_Google_Play AVD was used, at its existing 4096 MB. Shared access was coordinated with Tracker. No new/cloned/older AVD, restart, reset, data wipe or settings changes. Font scale remained 1.0; large live fixtures use local 200% density, with 320 dp width. This does not establish system-scaled popup typography.

## Screenshot QC and review

Before frames reuse verified 54b88926 renders (same presentation at f4801d32). All eight pairs have matching 1080 x 2424 dimensions and changed pixels. All 19 committed PNGs decode successfully at that size. Captures are unmodified. All final pairs, paused controls and pressed state were visually inspected. Root initially misread the resized partial/200 preview as missing its header/footer. Reinspection at original resolution and Astra's independent check confirmed the exact same file is complete; no rendering defect or production fix was warranted. The final test fixture settles the Android window before capture. Direct-window verification agrees with the root capture.

Astra independently reviewed original requirements, historical reference, code/diff, actual checks and seven admitted pairs plus paused/pressed frames. Its source findings were fixed: constrain target promotion to pouring intent, preserve circular Play and permitted Finish when paused, and decouple bloom timing from artwork. No new required design fixes were found in valid frames. Final verdict before commit: **Approved for commit and local integration. No remaining blocking findings.** Astra inspected all eight comparisons, both paused layouts and the pressed action; all three source findings are resolved. It found readable values, complete controls and no visible clipping at normal/200% text, and required no further refinement or testing.

Full TalkBack traversal, physical brewing, motion feel, exhaustive localization and a separate zero-motion run remain unverified. No remote publication or deployment.
