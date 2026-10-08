# Native visual guides and app-wide brews

4 October 2026. Approved prototype: [prototype.html](prototype.html). This is an implementation plan, not a release declaration.

## Product contract

Keep Brew, Log and More, the calculator's quantity cards, set/ratio selectors, vessel shortcuts and keypad. Learn stays inside More. Extend the existing Material 3 theme, including dynamic color, dark mode, typography, adaptive navigation and accessibility. The preview's sampled blue palette is not a new Android theme.

The app owns every brew, its persisted identity, physical clocks and effects. Learn owns only an untimed reading bookmark. Leaving a screen or the app must not stop extraction. One shared activity surface opens the exact brew, with multiple brews disclosed in a chooser. Back returns to the originating route. The current brew's timer is not duplicated below its own detail screen.

Use the existing Room session store, coordinator, typed stage plans, release gates and notifiers. A duration is a recipe cue or an explicitly chosen timer target; it never proves physical completion or food safety. Unsupported/corrupt sessions remain recoverable through the repair UI, without fabricated names, clocks or fallback recipes.

## Implementation order

### 1. Approved assets and shared app chrome

- Import all 28 approved vessel SVGs as Android VectorDrawables, preserving paths, viewport, winding and aliases. Verify source hashes before import; use Material content tint instead of baked colors. Reuse the approved cup for In cup and Brew navigation.
- Project valid, release-eligible persisted sessions into a compact root activity strip. Show elapsed time or the plan's actual timing boundary, paused/ready/completed states and an exact-session action. No active brew means no strip.
- Prioritize reached timing boundaries and unlogged completions. One brew opens directly; multiple brews open a scrollable native chooser. Hide the focused brew only; other brews remain accessible on detail routes.
- Observe clocks only while the app is visible. Presentation cannot dispatch recipe events, reschedule work or mutate snapshots. Keep the existing notification owners and exact-session deep links.
- Remove the duplicate calculator resume chip for sessions represented by the strip; retain repair access for unavailable sessions.

Acceptance: all icons load through the native renderer at small sizes; empty/single/multiple/focused states behave correctly; back navigation retains the prior route; no change to calculator inputs, persisted preset keys or timer semantics.

### 2. Visual Brew and Learn guides

- Build a shared visual action canvas, concise title, dominant quantity/time target and connected Why disclosure using the existing guidance/content resolver. Safety remains outside optional disclosures.
- Add a visual overview, untimed reader, step list and read-ahead. Reading ahead must not advance a live stage, start a timer or consume a physical confirmation. Retain separate reading and brewing positions.
- Adapt existing recipe plans before expanding methods. Preserve the exact `chemex_42_700` recipe and its equipment/filter contracts. The prototype's 30 g / 480 g recipe requires its own reviewed ID and content; never substitute it for arbitrary calculator input.
- Roll out the 17 researched brewing methods from their reviewed guides, with explicit equipment variants and source-specific defaults. Chemex, espresso and refrigerated cold brew are the first visual examples; every additional method needs the same content and physical-completion review.
- Import the approved monochrome method family, preserving the approved native Pulsar geometry. Review generated instruction scenes through the existing asset/hash gate; candidate artwork is not production approval.
- Audit the separate preparation/journey work against the current baseline before integration. Reuse compatible behavior; do not create a second coffee/grind memory model.

Acceptance: one-handed live brewing, reading without a clock, all steps reachable, accurate source-specific quantities and timing origins, progressive disclosure, small screens, 200% text, TalkBack and dark/dynamic themes.

### 3. All-method clocks, custom timers and reminders

- Move remaining legacy quick-brew clocks into the same app-owned runtime. Every calculator method and bean selection must retain its actual setup and physical start across routes, backgrounding and process recreation.
- Distinguish pause guidance from pause physical clock. Suppress discretionary prompts while guidance is paused; keep extraction running. Preserve explicit legacy pause compatibility until migration is reviewed.
- Fold cold brew's Follow guide / Just set timer choice into the existing Start flow. Provide a strong refrigerated default and contextual duration/start edits, not a second permanent calculator action. Unknown start time cannot produce a guessed deadline.
- Support user-chosen timer targets for every method. Persist origin, deadline, stage identity and revision; duration edits retain elapsed time and invalidate old effects. Manual filtration/pour/pump completion remains explicit.
- Extend quiet ongoing notifications with method, current action and truthful elapsed/countdown presentation. Use OS chronometers; tapping opens the exact session. Deliver a due cue once, keep dismissing separate from ending, and withdraw obsolete notifications on cancellation/replacement/completion.
- Audit scheduling precision before short-stage alerts ship: delayed WorkManager recovery alone is not a precise brew alarm. Handle permission/channel denial, reboot, stale intents, clock changes, and foreground/background delivery races through the existing owner.

Acceptance: actual notification delivery and exact-session return for all methods; multiple-session identity; denied permissions/channels; old-revision cancellation; process death/reboot; minute and many-hour timers; physical clocks continue after guide pause; no automatic safety or machine-control claims.

## Cost and removal criteria

The common problem is losing a running brew when checking a log, reading ahead or leaving the app. The default adds no setup: a single contextual strip appears only when useful. Multiple sessions and timer edits use progressive disclosure. Costs are shared state projection, native assets, alert scheduling, localization and lifecycle tests. If duplicate surfaces compete with the current action, remove the duplicate rather than add another mode or setting. Keep all essential physical/safety cues visible.

## Verification and delivery

Commit coherent native slices separately. Run focused pure-state tests, resource/build checks, native Compose interaction checks and visual review. Extend to migration/scheduler tests when those behaviors change. Emulator evidence does not establish real-device notification timing, reboot delivery, TalkBack quality or physical brewing accuracy. No release/version bump or publication is part of this pass.

Android references: [Material icons and tint](https://developer.android.com/develop/ui/compose/graphics/images/material), [notification chronometers](https://developer.android.com/reference/android/app/Notification.Builder#setUsesChronometer(boolean)), [notification permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission). Implementation details should be verified against the project's pinned libraries.

## Progress

All three native implementation phases are complete. [ANDROID-ACCEPTANCE-STATUS.md](ANDROID-ACCEPTANCE-STATUS.md) records delivered behavior separately from physical-device, speech, technical-localization and illustration-approval limits.

- Approved 28-vessel and 18-method monochrome vector families are native, preserving paths, tint, winding, aliases and the newer approved Pulsar. The existing calculator and Brew/Log/More navigation are retained.
- Shared app activity handles empty, single, multiple, attention and focused-session states. A brew continues through navigation, backgrounding and process recreation; Learn is an untimed reader, not a timer owner.
- Brew and Learn share the visual action canvas, targets, visible relevant safety, Done when and connected Why disclosure. Reading and live execution retain separate positions.
- All 17 reviewed source procedures are native, including exact equipment, quantity precision, physical clock boundaries, manual completion, evidence, variants and troubleshooting. Volume stays mL; unknown fill quantities require actual measurements. Existing Chemex 42/700 and reviewed Chemex 30/480 remain separate.
- Room 20→21 and scoped coffee/grind preparation memory are integrated. The reviewed Gagné Pulsar guide uses the shared canvas and coordinator; no second app-level journey runtime is introduced. See [PREPARATION-INTEGRATION-AUDIT.md](PREPARATION-INTEGRATION-AUDIT.md).
- Fresh calculator brews and reviewed presets use the same durable owner. Pause guide suppresses discretionary guidance while physical clocks, quiet OS status and explicit user reminders continue.
- Existing Start includes cold-brew Follow guide / Just set timer, a refrigerated default, actual/earlier/unknown origins and contextual duration edits. Unknown origins do not fabricate a deadline; timer-only ending creates no stock deduction or log.
- Every method supports persisted custom reminders with immutable stage/revision identities. Quiet OS chronometers, exact-session notification taps, obsolete-cue withdrawal, at-most-once attempts, precise AlarmManager access and WorkManager recovery share the same owner.
- Final validation passes both debug APK builds, detekt, 56 focused JVM checks, 28 native UI/Room/OS/alarm checks and one denied-permission check. Both guide-source checkers pass all 17 methods. Earlier preparation integration passes 214 JVM checks, 11 migration checks and coffee-memory/calculator persistence checks.
- Timer and guide interface controls are available in all 23 resource locales. Reviewed technical procedures remain explicitly English outside English locales. Full lint completes with the same 26 documented baseline findings and no feature-owned findings.
- The final emulator install preserves the pre-existing running Pulsar and user stock/history. Owned test sessions are cancelled; temporary notification/exact-alarm privileges are restored. No release, version bump, merge or publication is part of this implementation pass.

Detailed logs, native screenshots, lifecycle evidence and corrected failures are recorded in [NATIVE-INTEGRATION-REVIEW.md](NATIVE-INTEGRATION-REVIEW.md). Real-device notification punctuality/reboot, full TalkBack speech, professional technical localization, physical brewing trials and new instruction-art approval are not implied by the passing development checks.