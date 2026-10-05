# Active brews belong to the app

3 October 2026 · prototype design revision. Android implementation is a later phase.

An active brew must remain easy to find while someone reads a guide, checks their log, changes an app preference or leaves the application. Learn owns a reading bookmark. The app owns every brew, its physical clock, timed cues and reminder delivery. Leaving a screen or the app never ends or pauses extraction. This applies to every brewing method, recipe and coffee bean; cold brew is one example of the shared capability.

## Every method, inside and outside the app

The existing Start action creates an app-owned session whether the person follows the guide or uses native quick brew. Its persisted recipe plan supplies the clock origin, timed milestones and physical completion rules. Method names and bean selections identify the brew; they do not decide its duration. Learn remains an untimed reader until the person deliberately starts brewing.

| Plan behavior | In the app | While away | Physical completion |
| --- | --- | --- | --- |
| Chemex bloom in the reviewed recipe | Shared elapsed clock and 45-second bloom cue | Quiet countdown, then one bloom reminder with Return to brew | The user confirms the next pour; the brew clock keeps running |
| Espresso extraction | Elapsed pump time and measured 36 g cup target | Elapsed progress and yield cue | The user stops the pump at yield; no timer-based machine control or ready claim |
| Cold-brew steep | Saved deadline and remaining time | Quiet progress, then one ready-to-filter reminder | The user confirms filtration; time alone does not prove safety |
| Any other recipe with timed stages | Same shared session surfaces | Countdown to the current plan's next milestone and a step reminder | Follow the recipe's actual physical cue |
| Any recipe without timed stages | Elapsed clock and current action | Quiet elapsed progress and Return to brew | Manual confirmation; no invented deadline |
| User-chosen timer target | Contextual target and reminder controls | Countdown and one “Timer target reached” cue | Check the recipe; elapsed time does not automatically complete the brew |

**App visibility** in the review controls switches between the unchanged app routes, an Android notification shade example and a lock-screen example. Both outside-app views show each running brew separately, with method, stage, time and an exact-session return action. Opening the app normally retains the current route and scroll. Tapping a brew notification opens that specific stage; Back restores the previous app route. A stale session identity never opens a replacement brew. Countdown updates retain notification buttons rather than rebuilding them every second.

**Any method · custom timer** exposes the same timer sample for all 17 researched methods. Its 1-minute value is explicitly a custom demonstration, not a method recommendation. Its target can be edited in the timer detail without resetting elapsed time. The three completed visual example guides remain Chemex, espresso and cold brew. The browser still models one foreground brew alongside one cold batch; the native session repository, rather than these demonstration slots, should decide supported concurrency.

The standard Android notification is the default across methods and supported devices. Newer promoted Live Updates are an optional enhancement for an eligible active, time-sensitive short brew, subject to platform and user controls. A many-hour passive steep should use quiet standard status and its due reminder, without assuming it deserves a constantly promoted chip. This is a product inference from the [Android Live Updates criteria](https://developer.android.com/develop/ui/views/notifications/live-update), not a platform guarantee that brewing qualifies.

Keep foreground and background delivery separate. An app-owned banner appears only while the app is visible and the emitting timer is elsewhere. The focused timer presents its own reached state. While away, deliver the OS cue once; an acknowledged or dismissed event retains quiet status without repeated vibration. Dismissing a reminder never stops a brew. Permission denial hides notification examples and states that clocks still run; in-app cues remain available. The OS controls lock-screen visibility and privacy. No notification action advances a recipe, stops a machine or marks coffee safe.

The review has no browser notification permission, service worker, Android scheduler or actual notification delivery. Closing the page retains persisted physical start times for restoration; the “away” surfaces simulate background presentation while the page is open. Review fixtures remain isolated from saved sessions.

## Native delivery contract

The read-only audit on 3 October 2026 confirmed that `DurableBrewSessionStatusNotifier` already publishes a session-specific ongoing chronometer outside the focused brew and builds an exact-session `MainActivity.buildBrewSessionIntent`. `DurableBrewSessionStageNotifier` already handles persisted stage effects, notification availability, foreground visibility and session deep links. Extend these owners with method/stage identity and plan-based deadline presentation; do not introduce a timer owned by Learn or a second service for cold brew.

Persist a stable session ID, recipe snapshot, stage instance, physical clock origin, next due event and delivery revision. Compute display time from those values after route changes, process recreation and reopening. Updating duration or start invalidates earlier scheduled effects; ending, replacing or completing the session withdraws its progress and alert notifications. A guide pause suppresses guidance prompts while physical extraction and elapsed time continue. App visibility and the selected session are separate facts for deciding which surface should present an event.

Use system chronometers for elapsed/countdown presentation where supported, rather than waking the application every second. A 45-second brewing cue needs a scheduling design that actually meets that timing requirement under background and idle conditions; the existing durable WorkManager recovery path does not itself establish that guarantee. Evaluate exact-alarm eligibility/access where appropriate, handle denied access honestly, and reconcile durable state after reboot. Android documents distinct precision and permission requirements in [Schedule alarms](https://developer.android.com/develop/background-work/services/alarms). Do not claim exact background reminders until measured on supported devices.

Keep the existing brew-vibration preference and notification settings. Request notification permission contextually when someone enables reminders, and check app/channel availability at delivery. The browser demonstrates this denied state; actual permission prompts and platform restrictions remain native work. See the [Android notification permission guidance](https://developer.android.com/develop/ui/compose/notifications/notification-permission).

## One shared surface

Use the application's existing **Brew / Log / More** navigation. Learn stays inside More. The preview reconstructs the current calculator, More menu, Log card, grouped Learn library, Settings groups, Favorites and Beans empty states. **Compare current UI** shows the matching installed-app captures. Existing screen controls outside guide/navigation/timer interactions are read-only context, not a browser port of the full Android application.

A compact activity strip sits above navigation, outside each screen's scrollable content. It shows a brew name and current status. One brew opens directly; two open the same **Active brews** chooser, with a count on the strip. A reached timer target takes priority; otherwise show the foreground brew, then a passive steep. Inside a brew's focused screen, its own timing is already visible, so the strip shows other active brews rather than duplicating that timer. With no other active brew, it disappears.

This avoids a permanent fourth navigation destination, a notification inbox, and stacked session cards inside Learn. The shared strip remains visible while the current screen scrolls. Longer clock-origin detail belongs in the chooser and actual brew screen. At doubled text, redundant decorative glyphs yield space to the name, status and count.

Keep the existing navigation visibility rule: the bottom bar appears on the calculator, Brew Log and More, while Learn, Settings, Favorites, Beans and guided details retain Back navigation. The shared strip occupies the app dock on both kinds of route; detail screens gain session access without gaining a new bottom bar. Existing native prep, quick-brew routing, settings sections and library catalog remain intact.

Opening a brew from another screen preserves the originating route, reader step and scroll position. **Back** or **Done for now** returns there. Switching between saved brews keeps that original return location. Choosing a main navigation destination is deliberate navigation; it retains the reading bookmark and all physical sessions.

## Readiness, alerts and permission

| Situation | Shared app behavior |
| --- | --- |
| Still steeping | Quiet remaining-time status; no repeated alerts |
| Start unknown | Time unknown; no guessed finish, progress or ready alert |
| Cold deadline reached on another screen | One app-owned ready banner with Open and Dismiss; the screen, scroll position and focused control stay in place |
| Timer already visible at its deadline | Its own screen becomes Ready to filter; suppress a duplicate app banner |
| Banner dismissed or app reopened | Ready status remains in the activity strip until manual filtration; do not replay the same banner |
| OS notifications blocked | In-app ready feedback still works. The timer reports Notifications off and offers contextual recovery |
| Per-batch reminder off | No interrupting app banner or OS reminder; readiness remains visible when inspecting the app |
| Duration or start changed | Retain the physical origin when changing duration, invalidate earlier reminder delivery, and reconcile the new deadline |
| Filtering confirmed or timer ended | Withdraw stale alert surfaces; preserve unrelated brews |

The ready banner is concise because the action is already named: **Cold brew is ready to filter**. Opening it shows the filtration instruction and refrigeration requirement. It does not mark a brew complete, control equipment or infer a collected yield. A paused short guide retains physical elapsed time; an espresso advisory time is not a readiness or pump-stop rule.

Foreground feedback and OS notifications are distinct delivery surfaces for the same session event. OS access must not be a prerequisite for showing status inside the app. Request OS permission in context when someone enables reminders, and check permission plus global/channel availability before background delivery. This follows the [Android notification permission guidance](https://developer.android.com/develop/ui/compose/notifications/notification-permission); the particular strip, prioritization and ready-banner design are product decisions to validate with users.

When the app is visible, the shared app host presents the ready event without navigating automatically. When it is backgrounded, the native session notifier can deliver an OS notification. Its tap opens exactly the emitting session through the app's root navigation. An event already acknowledged on the focused timer should not be duplicated. Native coordination must account for visibility changes and delivery races, using session identity, stage instance and revision rather than screen-local flags.

## Extend the existing native foundation

Read-only source audit refreshed at `990bcd96`, under `app/src/main/java/com/adsamcik/starlitcoffee/`:

- `navigation/StarlitNavHost.kt` already defines Brew, Log and More navigation, observes recoverable sessions at the root, creates the durable runtime and routes `DeepLinkBus.pendingBrewSessionId` to the matching `BrewSession`. Promote session presentation into this shared scaffold. Keep exact-recipe restoration and release gates intact; do not expose invalid or gated plans through the activity strip.
- `ui/screen/MoreScreen.kt` places Learn, Your Favorites, Your Beans and Settings inside More. Keep all four destinations, their labels and native content. The earlier reduced menu has been replaced by their existing card layout.
- The coordinator, persisted plans, Room store, scheduler and worker described in [COLD-BREW-REMINDERS.md](COLD-BREW-REMINDERS.md) remain the owners of physical state and effects. Learn should never instantiate a parallel timer service or own a notification channel.
- Extend foreground visibility coordination to the app shell and selected session. Keep OS delivery, in-app acknowledgements, permission reporting and cancellation revision-aware. Do not keep a foreground service alive merely to render a long countdown.

The native activity strip should observe the shared session repository, with lightweight current-time presentation. Actual deadline transitions still come from the coordinator. Main navigation and scrolling must not recreate clocks, reschedule reminders or mutate a plan snapshot. Root ownership also applies to Settings, bag inventory, favorites and other routes that the prototype does not reproduce.

## Value and verification

The common problem is leaving a long steep or reading ahead and losing the way back. The default needs no setting or setup: show one contextual strip when a brew is active. Multiple brews use progressive disclosure rather than multiple banners. Costs are shared presentation, selection/return routing, accessibility semantics and foreground/background alert coordination. Remove duplicate surface content if it competes with the current action; retain discoverability and truthful readiness.

The browser uses the existing session store plus per-session cue acknowledgements. Forty deterministic checks cover all 17 method samples, background bloom cues, elapsed-only espresso, route isolation, reader return, concurrency, restoration, notification denial/opt-out/deduplication, revisions, cancellation and manual filtration. Evidence is in [REVIEW.md](REVIEW.md).

No real notifications are sent by this prototype. Background delivery, reboot/process recovery, full native navigation/back-stack restoration, TalkBack, real text scaling, all app routes and physical brewing still require native validation. Before integration is complete, test alert arrival on every route, dialog/keyboard visibility, route return after recreation, foreground/background races, multiple-session selection, denied permissions/channels, stale notification taps and exact-session deep links. Announce a new ready event once; do not announce countdown ticks.
