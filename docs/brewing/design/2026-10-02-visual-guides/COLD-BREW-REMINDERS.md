# Saved cold-brew timers and reminders

2 October 2026. Prototype extension requested by the user; Android implementation remains a later phase.

Cold brew needs a saved finish time because the steep lasts longer than a normal visit to the app. Both the guided recipe and a batch made independently enter the same timer view. The user can leave, reopen the timer, change its duration, and make another coffee while it steeps.

The app owns this timer, not Learn or the currently visible guide. A [shared activity strip and ready-banner host](APP-WIDE-BREWS.md) sit outside route content and remain available across Brew, Log, More, Learn and Settings. Learn retains only its reading bookmark. The prototype's menu/log screens are focused examples of this shared access.

## Two entries, one timer

| Entry | Default | What is recorded |
| --- | --- | --- |
| Guided cold brew | The reviewed 100 g / 800 g refrigerated recipe, 14 h | Confirmed preparation, refrigeration start, this brew's duration, reminder choice and guide position |
| Start a timer | Hours/minutes, initially 14 h; start now or enter the actual refrigeration time | A timer for the user's own batch. No assumed dose, ratio, preparation confirmations, dilution or final yield |

The quick entry is contextual on the cold-brew Brew overview. Learn stays a reader: opening it, changing a reading position or viewing the filtration step cannot start a timer or confirm a physical action.

Fourteen hours is the selected [reviewed recipe](../../research/2026-10-02-method-guides/methods/cold-brew.md), not a universal optimum. A custom duration is labelled as a change for this batch; it does not rewrite the source guide. The prototype's 1 minute to 7 days input bound is an engineering limit, not a brewing or storage recommendation.

## Waiting and returning

The live screen emphasizes a large time remaining, a calendar finish time, coffee-bean progress and the existing cold-brew silhouette. A quiet track and bean marker show elapsed deadline progress; an unknown start has no determinate progress. The redundant batch eyebrow is omitted, with recipe provenance inside Timer details. The screen follows the app's open brewing layout, type scale, tonal surfaces and expressive shapes. Duration, reminder availability and details form one connected group. The default action is **Done for now**. The timer stays visible in the Brew context as a compact saved-batch entry. The header's **Steps** opens the guide without repeating another browsing action in the guided wait view.

The **Steep duration** and **Ready reminder** rows open the shared hours/minutes editor and per-batch reminder switch. Blocked notification access instead offers contextual permission recovery. An edit computes the new finish from the original refrigeration start. It never resets elapsed extraction. A shorter duration can make the batch ready immediately; extending it invalidates the earlier reminder. Unknown start time produces no guessed finish time or alert. Entering a known start restores both.

One cold-brew batch can coexist with one foreground brew in this prototype. Starting an espresso or Chemex does not replace the cold brew. Starting a second cold batch offers the saved timer first and makes replacement explicit. Multiple simultaneous cold batches are not designed in this pass.

The calculator retains its existing **Start** key. With cold brew selected and no saved batch, Start offers **Follow the guide** or **Just set a timer**. The latter opens hours/minutes and actual refrigeration-time input without assuming preparation or recipe quantities. The calculator and guide overview have no separate quick-timer shortcut. External review controls provide isolated no-timer, steeping, ready and two-brew fixtures; they do not change saved preview sessions.

The passive steep has no Pause action. Leaving the guide, reading ahead or turning off its reminder does not pause extraction. **End timer**, inside Timer details, removes the reminder and saved timer without claiming that the coffee has stopped steeping.

At the deadline, the status becomes **Ready to filter**. The guided route offers **Continue to filtering**; filtration remains manual and untimed. The quick route offers **I've filtered this batch** plus optional filtration help. It cannot claim that the full guide recipe was performed. A deadline never marks the brew complete, proves safety or supplies an inferred concentrate yield.

Keep refrigeration at 4 °C or colder visible. The timer does not validate refrigerator temperature, establish a safe storage life or certify a batch's safety.

## Reminder contract

The reminder says **Cold brew is ready to filter**, followed in an OS notification by the action to separate the grounds and refrigerate the concentrate. Its tap opens the saved batch. Inside the app, a concise shared banner offers Open and Dismiss without navigating automatically or rebuilding the current screen. A denied OS permission still permits this foreground feedback; per-batch reminder opt-out suppresses both interrupting surfaces. Ready status stays visible in the shared activity strip. Opening the already visible timer does not create a duplicate banner. Enabling OS reminders is contextual rather than a required onboarding step.

The review harness simulates allowed/blocked access and renders an OS alert preview when the target is reached. The app-owned banner demonstrates foreground delivery on any reviewed route, with an independent persisted delivery revision so dismissal/reload does not replay it. Neither is a browser notification, an Android notification or an actual OS permission prompt. Local browser storage restores the session after reload; closing the page stops alert simulation. The prototype lacks multi-tab ownership, a transactional store, monotonic time, reboot recovery and wall-clock correction handling.

## Native implementation audit and next work

This worktree already has a durable session foundation. The audit is source evidence at baseline `79d5d86e`; no native build or runtime validation was performed in this pass.

- `ActiveBrewSessionDao` stores recipe/plan/runtime snapshots, deadlines, schedule tokens, notification state and revisions in Room. Recoverable sessions are a list; concurrent saved sessions are possible in storage, while the user-facing selection and navigation still need review.
- `BrewSessionCoordinator` owns transitions, persistence and idempotent effect delivery. `BrewSessionRuntime` reconciles recoverable sessions and re-enqueues persisted deadlines.
- `WorkManagerLongSessionScheduler` creates uniquely named delayed work per session/token. `LongBrewCompletionWorker` checks the current persisted stage, token and due time before reconciling. This supports rejecting stale work after a duration change.
- `LegacyStagePlanFactory` currently compiles a cold steep from `BrewMethod.timeTargetLow`: 12 h in this checkout, followed by a manual FILTER stage. `LegacyBrewSessionStartFactory` has no custom cold-duration input. The reviewed prototype's 14 h identity is distinct from that legacy recipe; existing saved plans must not be relabelled or rewritten.
- `DurableBrewSessionStageNotifier` currently posts generic stage copy, checks notification permission and suppresses stage alerts while the session is visible. Cold-specific ready-to-filter copy and accurate permission/global/channel availability reporting need integration.
- Existing native Pause freezes session time. The new passive steep must preserve physical elapsed time; renaming that event or keeping a foreground service alive for 14 h would not implement this behavior.

Implement this by extending the existing coordinator and plan contracts. Snapshot the selected duration at start; revise only the running timer through a validated domain event. Persist the updated deadline and new schedule token before scheduling effects; invalidate old work/notifications and preserve idempotence. Keep guided and timer-only provenance distinct. Model a timed target as readiness for manual filtration rather than full completion.

WorkManager is designed to persist across app restarts and device reboots. Its initial delay is a minimum and execution may be delayed by system optimizations, so the app should not promise an exact alarm at the displayed minute. Cold brew can use an inexact reminder, with an authoritative deadline reconciled whenever the app opens. Forced stop, revoked notification access, disabled channels and a powered-off device need honest behavior and their own native tests. See [persistent work](https://developer.android.com/develop/background-work/background-tasks/persistent), [delayed work timing](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work#delayed_work) and [notification permission and availability](https://developer.android.com/develop/ui/compose/notifications/notification-permission).

Before native completion, test process death, reboot, background lateness, notification tap to the correct batch, time-zone/display changes, wall-clock adjustments, denied/disabled notifications, edits after delivery, cancellation races, foreground brewing alongside cold brew, old-plan migration and manual filtration. Reuse the existing reducer and worker tests; browser checks cannot establish these guarantees.

## Prototype checks

Run `node docs/brewing/design/2026-10-02-visual-guides/cold-timer.test.cjs` for the deterministic clock, persistence, migration, alert and actual prototype transition checks. Browser findings and screenshots are recorded in [REVIEW.md](REVIEW.md).
