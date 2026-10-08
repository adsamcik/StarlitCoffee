# Brew timer notification

Approved in the design conversation on 2026-10-08: a clean outlined cup with no bean,
vertically centered beside the timer and a two-line brew/stage title block.
Android provides the surrounding notification frame and expand control.

The compact duration uses smaller unit letters: `12h50m`, `50m`, then `2m59s` below
three minutes. Display updates follow minute boundaries until that threshold,
then second boundaries. Positive final durations never display `0s` early.

The liquid rises against the same target that supplies remaining time. Explicit
user timers take precedence; source-defined stage/brew constraints remain
respected. Untimed and unknown-origin stages use an empty outline rather than
inventing a percentage. Reaching a display target does not complete physical work.

The cup is rendered into a square bitmap with equal top/bottom insets; the 40 dp
collapsed and 48 dp expanded icon views are centered within their respective rows.
The expanded layout provides additional room for long titles and larger text.

## Runtime

`BrewTimerNotificationService` owns display refresh only. It restores existing
Room snapshots, observes revisions, and stops tracking removed, paused, completed,
or foreground-visible brews. It does not write session state, alter alarms, or
advance stages. One brew supplies the foreground-service notification; additional
brews retain individually tagged statuses. Recovery finishes restoring active
sessions before deciding whether the service is idle. Requests and removal are
serialized on the main thread, independently of a departing service's coroutine scope.

The manifest declares the `specialUse` foreground service type for this
user-started timer display. If Android rejects a service start, the adapter posts
the existing standard notification with Android's independent chronometer, rather
than a custom countdown that cannot refresh. This fallback retains seconds.

## Artwork provenance

`approved-notification.png` is the unchanged image-generator output. The exact
prompt and generator filename are in `generation-record.json`; its preceding
reference is preserved as `reference-notification.png`.

`tools/import_timer_notification_cup.py` segments the cream cup outline from that
image and mechanically identifies its largest closed interior as the bowl. It
traces both masks at 8x nearest-neighbor sampling with the existing pinned vtracer
toolchain, then imports the SVG paths through the existing vessel-vector converter.
No SVG or Android contour coordinates are drawn or adjusted by hand.

`trace-report.json` records source/SVG hashes. `contour-review.json` records
source-resolution and small-size render comparisons. Source-resolution outline
binary IoU is 1.0; the actual 40/48 pixel notification sizes have mean alpha errors
of 0.0199/0.0096. The informational 24/32 pixel measurements are disclosed and
are not notification display sizes. Antialias filters affect thin-stroke binary
IoU at small sizes. `cup-preview.png` is a Cairo contour/theme review, not an
Android notification screenshot.

Reproduce imports:

```powershell
uv run --no-project --with vtracer==1.0.0a3 --with numpy==2.3.5 --with pillow==12.3.0 python tools/import_timer_notification_cup.py
python docs/brewing/design/timer-notification-20261008/build_preview.py
```

## Native verification

`BrewTimerNotificationRenderingTest` checks native RemoteViews at 240/328 dp,
light/dark themes, and font scales 1/1.3/2. It checks cup centering, unclipped time,
title-column visibility, and an unfilled handle, and saves render images to the
test app's cache. Existing OS-posting tests use the delivery seam; they do not
claim service-lifetime coverage.

No device or configured AVD was available during implementation. These native
tests, notification-shade appearance, process restart, multi-brew promotion,
screen-off timing, and background-start fallback still require device execution.
Use an isolated emulator/phone with notification access granted.

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.adsamcik.starlitcoffee.notification.BrewTimerNotificationRenderingTest,com.adsamcik.starlitcoffee.notification.DurableBrewStatusNotificationTest
```

## Local validation boundary

The JVM suite passed 1,703 tests (4 skipped, no failures or errors). Detekt,
debug assembly, and Android-test source compilation passed. The independent
review closed recovery, expiry-boundary, native-test-assumption, and delivery-race
findings; no actionable findings remained.

Notification-relevant lint checks passed: `NestedWeights`, `UseKtx`,
`MissingPermission`, `RemoteViewLayout`, `NewApi`, `ContentDescription`, and
`ResourceName`. An ignored, temporary Gradle init script selected those checks;
no project lint policy or baseline was changed. Full repository lint remains
failed: the initial report had 24 errors, including three new notification
findings that were fixed. The remaining 21 findings concern the Gradle update
advisory, existing Compose resource/modifier usage, and unused resources in
untouched files. The original full report is retained in the ignored
`build/timer-notification-full-lint.txt`.
