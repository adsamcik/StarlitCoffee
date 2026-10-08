# Visual guide prototype review

**2 October 2026 · design phase · original source baseline `38639e48`, app-wide activity revision.** Astra created the
[design](DESIGN.md) and [interactive prototype](prototype.html); the parent agent
reviewed the browser behavior and requested corrections. This delivers the
user-selected visual prototype first. Android implementation is a later phase.

## Espresso distinguished from Cappuccino

**4 October 2026, current follow-up:** the user found the rebuilt family much better but flagged Espresso and Cappuccino as very similar. One targeted built-in imagegen edit gives Espresso a compact demitasse, modestly straighter sides and a shallow detached saucer. The other 27 designs, including Cappuccino, have unchanged source images and SVGs. The new source is mechanically traced; no paths are redrawn by hand. Its prior image is retained in `discarded/espresso-v1.png`, with the exact edit prompt and preserved input paths recorded in the [prompt](assets/cup-icons/rebuild-20261004/refinement-espresso-prompt.json). The manifest now accounts for 41 original outputs.

Astra reviewed the actual-size light/dark proof and found no material defect. The saucer stays separate at 24 px, providing a cue absent from Cappuccino; the cup remains naturally rounded and balanced without the previously rejected narrow proportions. Source/SVG hash checks and contour gates pass at 24, 28, 34 and 48 px. Browser review confirms the same shape in the existing calculator slot. This is prototype visual assessment; user acceptance and native rendering remain unverified.

- [Interactive pair comparison](espresso-cappuccino.html)
- [Actual-size proof](preview-espresso-cappuccino-saucer.png)
- [Existing calculator integration](preview-espresso-saucer-calculator.png)

## All 28 vessels rebuilt through image generation and tracing

**4 October 2026, initial rebuild at `4547a411`:** rebuilt every design in native `availablePresetIcons`, rather than stopping at the five default calculator slots. The [catalog](assets/cup-icons/rebuild-20261004/catalog.json) covers all 28 keys in native order; `custom` still maps to Bowl and unknown keys fall back to Mug. Shared In cup and Brew symbols use the new Cappuccino shape. Existing calculator slots, preset labels, volumes, Settings layout, navigation and brewing flows retain their structure. Android resources are unchanged.

Each vessel received a separate built-in imagegen call with its unchanged native artwork for anatomy and the user's accepted calculator screenshot for style. Astra reviewed the references and initial candidates. Twelve further calls corrected transparency artifacts, fragile press frames, the bowl opening and lid/sleeve seams. All forty original generator outputs are retained unchanged, including rejected iterations, with exact prompts and hashes in the [rebuild record](assets/cup-icons/rebuild-20261004/README.md).

The repository tracer converts selected alpha silhouettes into smooth spline SVGs. The generated PNGs are not painted over; normalization and masks are separate mechanical artifacts. Every SVG uses `currentColor`, and the preview importer copies its paths unchanged with nonzero winding. All 28 selected candidates pass full-resolution topology preservation and contour fidelity checks at 24, 28, 34 and 48 px. Small-size topology counts are reported, but are not an equality gate; antialiasing can close fine seams at particular pixel sizes. These checks verify the trace against its source, not user recognition.

Astra inspected the saved final light and dark sheets at actual 24, 28 and 34 px. No material visual defect remained. It noted that fine press, faceted-glass and lid details soften at 24 px, and that Espresso/Cappuccino, Bowl/Ceramic latte bowl and Double-wall espresso/Double-wall tumbler remain related forms. Labels carry their variant identity. Agent review does not establish user acceptance or native rendering quality.

Browser review confirms the existing calculator presets are still 34 px, Settings glyphs 28 px, and the shared quantity/navigation symbols use this family. All 40 existing prototype checks pass; SVG coverage, source hashes, syntax and Git whitespace checks pass. Browser warnings/errors are empty. The source/SVG gallery exposes every design, light/dark colors and downloadable standalone vectors without expanding the app's current calculator UI.

- [All 28 designs, original sources and SVGs](assets/cup-icons/rebuild-20261004/gallery.html)
- [Complete family in light colors](preview-all-cup-icons-light.png)
- [Complete family in dark colors](preview-all-cup-icons-dark.png)
- [New symbols in the existing calculator](preview-rebuilt-cup-calculator.png)
- [New symbols in Settings](preview-rebuilt-cup-settings.png)
- [Exact prompts, provenance and reproduction](assets/cup-icons/rebuild-20261004/README.md)

## Rounded set restored after user rejection

**Historical revision, superseded by the complete rebuild above.**

**4 October 2026:** the user rejected the most recent Espresso and Cappuccino shapes. Restored `vessel-icons.js` byte for byte from `cb272bd5`, the preceding rounded set which the user had described as otherwise looking good. The third generator variants, prompts and review screenshots remain as rejected exploration; earlier Astra approval does not establish user acceptance. The comparison and manifest select the second candidates again. Calculator, Settings, other icons, presets and brewing behavior are unchanged.

Vector syntax and Git whitespace checks pass. Browser review confirms the restored pair in the current preview. This is a focused prototype restoration, not a new design approval or native validation. The similarity concern remains open for a later focused revision.

- [Restored rounded icons in the calculator](preview-restored-rounded-cups.jpg)

## Stronger Espresso and Cappuccino distinction

**Superseded by the user's 4 October rejection above.**

**3 October 2026, follow-up:** the user accepted the style but found some shapes still too close. Astra identified Espresso and Cappuccino's near-equal body heights. Two further generator edits made Espresso a narrow upright demitasse and Cappuccino a broad shallow curved bowl; the selected contours were adapted into normalized vectors. Espresso's body is 10 × 12 viewBox units with a compact handle and 2.5-unit saucer gap. Cappuccino's body is 18 × 9.5 units, with immediately curving sides and a horizontal handle opening. Cortado, Mug and Travel remain unchanged.

Astra reviewed the generated contours and final browser proofs at 24, 28 and 34 px in light and dark colors. The pair passed, including Espresso's distinction from the larger Mug; no further correction was requested. Existing 40 prototype checks, vector and inline-script syntax, local references, JSON, asset hashes and whitespace validation pass. Browser warnings/errors are empty. Native rendering and measured recognition remain unverified.

- [Current generation comparison](cup-icon-iterations.html)
- [Saved final comparison](preview-separated-cup-iterations.jpg)
- [Updated calculator](preview-separated-cup-calculator.jpg)
- [Targeted generator prompts](assets/cup-icons/prompts-v3.json)

## Image-generated cup exploration and Astra review

**3 October 2026:** generated each of the five cup presets separately with the built-in image generator, using its existing vessel artwork for anatomy and the accepted calculator/size sheet for style. Astra reviewed the first images and requested four targeted edits: open Espresso's handle and cup–saucer gap, shorten and broaden Cortado, strengthen Cappuccino's upper rim, and close Travel's hollow lid and sleeve. Mug's first candidate was retained. Nine generated PNGs are preserved unchanged with hashes, exact prompts and selection notes in the [manifest](assets/cup-icons/manifest.json).

The preview uses smooth, authored `currentColor` SVG adaptations of the selected shapes. This removes raster grain and stray alpha specks while retaining the generated silhouettes, rounded treatment and clear negative spaces. Astra reviewed the vectors and actual calculator at 24, 28 and 34 px in light and dark colors, then approved the complete family after moving Cortado upward by two viewBox units. No further shape corrections were requested.

Browser review confirmed all five calculator glyphs at 34 px and the shared Settings glyphs at 28 px, with inherited theme colors and the existing accessible labels and volumes. The final proofs below show the generated candidates, feedback revisions and live vectors. All 40 existing prototype checks pass; JavaScript syntax, JSON manifests, local asset references and Git whitespace checks pass. The browser warning/error log is empty. This changes only the prototype and design evidence; Android rendering and measured user recognition remain unverified.

- [Generation, refinement and final vectors](cup-icon-iterations.html)
- [Saved comparison](preview-cup-generation-iterations.jpg)
- [Final icons in the existing calculator](preview-generated-cup-calculator.jpg)
- [Final icons at actual sizes](preview-generated-cup-sizes.jpg)

## Filled cup presets

**3 October 2026, cup-preset revision:** the five colored vessel illustrations now use filled monochrome vectors in the same family as the accepted calculator and brewer symbols. `vessel-icons.js` shares the Espresso, Cortado, Cappuccino, Mug and Travel shapes between the calculator and Settings. Their different heights, bowl widths, handles and lid preserve the existing vessel distinctions. The calculator slots, preset order, labels, Settings volumes and Backspace stay unchanged; native artwork and comparison captures are retained as reference.

**Silhouette correction:** the user requested stronger differences after reviewing the first monochrome pass. Espresso now has a smaller demitasse with a broad saucer; Cortado is a squat glass with pronounced taper and a heavy base; Cappuccino has a low curved bowl; Mug has straight tall sides and a large vertical handle; Travel has a projecting lid and narrow body. Browser review checked the revised calculator row at 34 px and the size sheet at 24, 28 and 34 px in both colors. Updated screenshots below show this revision. The 40 existing prototype checks and vector syntax pass. This visual review does not establish user recognition rates.

Browser review checked the calculator in blue light and coffee dark appearances, and the five Settings presets in coffee dark. The calculator glyphs measure 34 × 34 CSS px inside the existing 48 px-high controls; Settings uses the same geometry at 28 px. SVGs are decorative beside the existing accessible labels and use `currentColor`. The [actual-size sheet](icon-sizes.html) also shows all five at 24, 28 and 34 px in light and dark colors. The 40 existing prototype transition checks, JavaScript syntax, local asset references and whitespace checks pass. The browser warning/error log is empty. Native rendering and recognition testing remain for the Android phase.

- [Filled presets in the existing calculator](preview-filled-cup-presets.jpg)
- [Cup presets at actual sizes](preview-filled-cup-sizes.jpg)

## Filled icon family throughout the preview

**3 October 2026, complete-family revision:** applied the accepted calculator style to every brewer symbol and every preview size. `method-icons.js` covers all 17 researched methods plus the existing wedge profile. The preceding size-dependent fallback to older equipment vectors is removed. The same symbol now appears in small controls, Learn and guide headings, large timer badges, the shared app dock, session selection and simulated notification titles. Duration controls keep their clock symbol, and notifications keep the Starlit Coffee app identity. Pulsar retains its authored outer silhouette with a narrower glass highlight to match the filled bodies.

New silhouettes follow the previously reviewed equipment photographs and anatomy briefs. The [complete family](icon-sizes.html) includes all 18 symbols at actual 16, 20, 24, 28, 44 and 56 px in light and dark colors. Browser review confirmed the six existing Learn headings use the new 44 px glyphs, and an AeroPress custom timer carries its four-path symbol into the shared dock, a 32 px notification badge and a 56 px timer hero. Opening the notification returned to the same sample timer. The browser error/warning log was empty. The 40 existing transition checks pass, and a separate coverage check confirms every timer method has a glyph.

Instruction scenes, recipe content, app navigation, current-UI captures and Android resources remain unchanged. This is visual prototype validation; native icon rendering, OS notification layout and user recognition still require the Android phase.

- [Complete filled icon family](preview-all-method-icons.jpg)
- [All methods at small and large sizes](preview-compact-icon-sizes.jpg)
- [Existing Learn library with the new badges](preview-filled-icons-learn.jpg)
- [Method recognition in notifications](preview-filled-icons-notifications.jpg)

## Previous compact-only icon revision

**3 October 2026, calculator-style correction:** the user rejected the thin, hollow appearance of the first compact pass and supplied the Coffee, Water in and In cup symbols as the style reference. Six compact glyphs now have solid bodies, rounded edges and small cutouts for their rims, handles or glass highlights. The approved authored Pulsar geometry is retained. All compact glyphs follow `currentColor`; no fixed raster color or extra label was added. Larger equipment references, instruction scenes, comparison captures, calculator layout and touch targets remain unchanged.

Browser review checked seven symbols at actual 16, 20, 24 and 28 px in light and dark colors using the [size sheet](icon-sizes.html), which includes the unchanged calculator symbols for comparison, then checked Chemex in the calculator. The calculator glyph measured 28 × 28 CSS px and used the 24-unit compact viewBox. Existing 40 timer/navigation checks pass. Native rendering and user recognition remain to be validated in the later Android phase.

- [Compact symbols in the existing calculator](preview-compact-icons-calculator.jpg)
- [All seven symbols at actual sizes](preview-compact-icon-sizes.jpg)

## Every method and leaving the app

**3 October 2026:** timers and timed-step cues now use one app-owned presentation model. The existing calculator, navigation and More destinations stay in place. The shared dock supports the three reviewed guides and custom timer samples for all 17 researched methods. A custom sample is explicitly 1 minute for demonstration; it establishes no method recipe or completion rule.

The new **App visibility** review control shows an Android notification-shade example or lock-screen example while the underlying route and clocks remain intact. Each physical session has its own progress card and exact-session return action. Chemex's 45-second bloom emits one reminder; espresso retains elapsed pump time and its 36 g yield cue without a deadline; cold brew keeps quiet steep status and its ready-to-filter reminder. Custom targets use “Timer target reached” and require checking the actual recipe. Opening, dismissing or receiving a notification never advances a physical step.

Browser review verified a Chemex bloom reminder alongside a continuing cold steep, return to the exact bloom stage, Back to the unchanged More screen, an AeroPress custom timer in the shared dock, app-contained duration editing, rejected zero input with the dialog retained, a valid 2-minute target retaining the original start, and lock-screen continuation. Denied notification access hides OS cards and states that timers still run. Espresso remains elapsed and yield-led after the app is left. The browser error/warning log was empty during the final review.

At 320 × 800 CSS px with doubled text, notification actions remained at least 48 px tall. The document, notification card and outside-app container had no horizontal overflow. A lock-clock overflow discovered in that check was corrected with responsive display sizing; the clock and its container both measured 257 px after correction. Temporary viewport/text overrides were reset. The final open preview shows the simulated Chemex reminder beside quiet cold-brew progress; saved sessions remain isolated from these fixtures.

All **40 deterministic checks pass**, including all 17 method samples, generic plan-stage wording, background bloom delivery and deduplication, elapsed-only espresso, exact notification return and reader/scroll restoration, denied notifications with in-app recovery, every preview route, original-start preservation during custom target edits, cancellation, stale session taps, restored clocks and delivery acknowledgements, and paused/unknown-start suppression. Clock ticks update existing notification text rather than replacing action buttons. JavaScript syntax, local asset references and Git whitespace validation pass.

The [native integration contract](APP-WIDE-BREWS.md) extends the existing durable status/stage notifiers and root session repository. Official Android permission, Live Updates and alarm guidance informs the proposal. This revision changes only the visual prototype and its documentation. Real background/idle reminder precision, process/reboot recovery, notification permission/channel behavior, lock-screen privacy, TalkBack, native routes and brewing remain unverified.

- [Concurrent ongoing progress and a timed bloom reminder](preview-all-brew-notifications.jpg)
- [Notification view within the app-sized preview](preview-notifications-detail.jpg)
- [Another method in the existing More screen](preview-any-method-app-dock.jpg)
- [Espresso elapsed progress and yield cue](preview-espresso-notification.jpg)
- [Lock-screen example with doubled text](preview-lock-screen-large-text.jpg)

## Cold-brew Start and explicit timer states

**3 October 2026:** the user approved folding the timer shortcut into the existing **Start** flow. The calculator retains its keypad and Start position; the extra link below it is removed. Cold brew Start offers **Follow the guide** or **Just set a timer**. The duplicate quick-timer card in the guide overview is also removed. Existing cold batches still offer return or explicit replacement.

The external **Timer state** selector adds no-active, steeping, ready-to-filter and two-brew fixtures alongside **Saved preview**. Samples never write sessions, reading positions or notification access to browser storage. Manual sample actions select **Interactive sample**, so a fixture can be selected again. Returning to Saved preview restores the saved sessions, original route and scroll position; reload discards a sample. Switching away from readiness clears its notification preview, app reminder and announcement.

Browser review verified both Start paths: the guide opens untimed preparation; the timer path accepts 13 h 30 min for an independently prepared batch. The no-timer calculator has no activity strip. Steeping exposes one cold timer; readiness displays shared reminders on Brew, Log and More; two brews open the shared chooser. At 320 × 800 CSS px with doubled text, More's ready reminder, activity strip and navigation controls had no horizontal overflow, and the no-timer state removed both reminder surfaces. Temporary text/viewport overrides were reset and Saved preview restored the existing foreground brew and cold batch.

All 30 deterministic checks pass, including sample-storage isolation/reload, saved route/reading/scroll restoration, accurate fixture readiness and manual filtration, Start branching, foreground-brew preservation and fixture reselection after interaction. Git whitespace validation passes. This is browser-prototype work; no Android source, production assets or actual OS notification delivery changed.

- [Calculator with no active timers](preview-timers-none.jpg)
- [Cold-brew options through Start](preview-cold-brew-start-options.jpg)
- [Calculator with a steeping timer](preview-timers-steeping.jpg)
- [Ready reminder on the existing More screen](preview-timers-ready-more.jpg)

## Cohesive step explanations

**3 October 2026:** each **Why this step?** disclosure now expands within one tonal card. Header and explanation share a surface, rounded outer shape and 20 px horizontal padding. The separate colored pill, gap and nested explanation shape are removed. Keyboard focus outlines the whole card; the source link retains its own focus indication. Recipe text, source links and native disclosure semantics are unchanged.

Browser review verified the collapsed and expanded Chemex bloom card in current-blue and coffee-light appearances, Enter/Space toggling and keyboard access to the source link. At 320 × 800 CSS px with doubled product text, the header reflows and no card descendant overflows horizontally. Temporary appearance, text and viewport overrides were reset. No active brew was reset, replaced or advanced. This is a prototype styling change; Android rendering and assistive-technology behavior remain unverified.

- [Unified step explanation in context](preview-cohesive-step-details.jpg)

## Popups contained within the app preview

**3 October 2026:** browser-wide `dialog.showModal()` placed the popup and its backdrop across the entire review page. All prototype dialogs now share an overlay inside the simulated app frame. The app underneath becomes inert while the external review controls and native screenshot stay usable and undimmed. Closing restores the opening control; Escape and forward/reverse keyboard focus wrapping work within the dialog. Opening a popup brings the app frame into view, and long content scrolls inside the popup. Updated stylesheet/script URLs prevent reuse of the preceding cached dialog implementation.

Browser review verified the existing-Chemex confirmation and brewing-set chooser. The overlay matched the app frame's 440 × 640 CSS px bounds; the confirmation stayed inside it at 408 px wide. At a 320 × 800 viewport with doubled product text, the dialog's 768 px visible height contained 912 px of scrollable content, and keyboard navigation reached the final action. The comparison toggle remained usable without closing the chooser. Escape restored focus to Start. Temporary viewport and text overrides were reset; existing brews were never reset, replaced or advanced.

All 26 deterministic timer/prototype checks pass, including app-only interaction blocking, external review controls remaining usable, dismissal and method selection without session changes. Git whitespace validation passes. This is browser-only evidence; no Android code or native dialog behavior changed.

- [Popup contained within the app beside its native reference](preview-app-dialog-contained.jpg)
- [Scrollable popup at 320 px with doubled text](preview-app-dialog-320px-large-text.jpg)

## Reference image recovery

**3 October 2026:** the user reported a broken More screenshot in the current-UI comparison. All seven original captures decode as 1080 × 2400 PNGs, and the local server returns the More file with its recorded SHA-256. The failed request was not retained in the review tab, so its original cause is unconfirmed.

The preview now preloads all seven references, changes the image source only when its route changes, and provides a visible retry action after a load failure. Retry bypasses the failed URL's cache entry and preserves the selected screen, reader position and active brews. An explicit image aspect ratio reserves space during loading. Original screenshots are unchanged.

Browser navigation verified all seven reference images loaded and visible: Brew, Log, More, Learn, Settings, Favorites and Beans. The focused failure/retry regression and the preceding 24 transition checks pass; Git whitespace validation passes. This is browser-artifact validation, with no Android changes.

- [Restored More reference beside the preview](preview-reference-restored.jpg)

## Monochrome method icons

**3 October 2026:** replaced the colored method recognition artwork with filled, single-color SVG glyphs throughout the proposed UI. Chemex, V60, Pulsar, espresso and cold brew retain the native equipment vectors' exact paths; Wave 185 and wedge are matching prototype glyphs. All seven inherit their container's content color. Calculator geometry, existing vessel preset images and detailed instructional scenes are retained.

Browser review covered the 28 px Chemex calculator glyph, the three-method chooser, six Learn headings and Pulsar/espresso saved sets. Computed SVG fills confirm one color per method in the coffee-light library, with current-blue saved sets and coffee-dark guide badges also checked. Decorative glyphs are hidden from accessibility alongside their visible labels. The Chemex overview's pouring scene and all three instructional thumbnails loaded unchanged. Active sessions remained present while browsing.

The 24 existing timer/prototype transition checks pass. Source comparison confirms five native vector path sets match exactly and both instructional image maps remain identical to the preceding commit. Git whitespace validation passes. No Android source or production assets changed; native rendering and final approval of the two new glyphs remain outside this browser pass.

- [Calculator with monochrome Chemex](preview-monochrome-calculator.jpg)
- [Method chooser](preview-monochrome-chooser.jpg)
- [V60, Wave and wedge library headings](preview-monochrome-library.jpg)
- [Coffee dark glyphs](review-monochrome-dark.jpg)

## Try it

Open `prototype.html` in a browser alongside `prototype.js`, `app-shell.js`, `cold-timer.js`, `expressive.css` and its local `assets/` folder. The artifact has local styles and recipe data, local JavaScript,
and retained shipping artwork plus generated review scenes. Recipe source links point into the existing
research pack. To serve the prototype and its linked documents locally, run
this command from the repository root:

```text
python -m http.server 8787 --bind 127.0.0.1 --directory docs/brewing
```

Then visit
<http://127.0.0.1:8787/design/2026-10-02-visual-guides/prototype.html>.

The controls outside the app preview select **Brew** or **Learn**, one of three
recipes, simulated time advancement, notification access, and **Large text · 200%**. Reset preview
clears this prototype's simulated session while retaining reading positions.
The prototype stores its state in this browser; it does not operate brewing
equipment.

## Current UI integration

3 October 2026. This revision supersedes the reduced More/Log navigation examples below. The user asked to see additive integration with the current application, keeping its existing UI.

The preview retains the native calculator hierarchy and Start position, all four More destinations and their descriptions, the Log card, grouped library cards, Settings section hierarchy, and Favorites/Beans empty states. It follows `StarlitNavHost.bottomBarRoutes`: only calculator, Brew Log and More have the bottom navigation. Detail routes keep Back. Shared activity and ready alerts live outside each route; no notification-settings replacement or fourth tab is introduced. A cold-method-only timer link supports the user's own recipe.

**Compare current UI** reveals real, route-matched emulator captures. They were read from the already installed `com.adsamcik.starlitcoffee.debug` 1.5.0 (versionCode 6) on `Medium_Phone`, Android 16, 1080 × 2400 px. No app was built, installed or reset; navigation and the initially open brewing-set sheet were restored before releasing the device claim. The installed APK's source commit is unknown. [Capture/resource provenance](assets/current-ui/PROVENANCE.json) records source paths and screenshot hashes. Native source was inspected at `990bcd96`: `CalculatorBrewScreen`, `MoreScreen`, `BrewLogScreen`, `LearningLibraryScreen`, `SettingsScreen`, `SavedRecipesScreen`, `BagInventoryScreen` and `StarlitNavHost`. Reused quantity icons and vessel/brewer artwork come from those existing resources.

Browser review verified the calculator landing screen, More-to-Learn library entry, the current Settings groups, app-level timer access and return. Opening cold brew from Settings at scroll position 1664.67 px restored that same position on Back, without a detail-screen bottom bar. At 320 × 800 px and doubled text, calculator, More and Settings had no horizontal product overflow; their scrollable route regions measured 578, 490 and 626 px respectively. The calculator content remains scrollable when enlarged. Temporary text/viewport overrides were reset.

An isolated `localhost` review origin created a 13 h 30 min own-recipe timer with OS access blocked. After advancing only that test clock, More kept its four cards while the shared ready banner and strip appeared. The OS preview remained hidden. Start then opened espresso's guide branch while retaining the cold batch, and Back returned to the calculator. The test tab was closed. The user's `127.0.0.1` sessions were never reset, replaced or advanced in this pass.

Twenty-four deterministic checks pass. Four new checks cover existing landing surfaces/destinations, library and set selection without session changes, Settings group/scroll restoration, and comparison isolation. Earlier recipe, clock, alert, revision, manual-filtration, reader-return and artwork checks remain passing. Native math, saved preference editing, rating, scanning and unreviewed guide links are read-only layout context in the browser. Library context is a sample of existing cards plus the reviewed examples; native catalog entries are not removed. Native setup/quick-brew routing is represented by a browser shortcut into the fixed-recipe guide branch, not an implemented replacement for setup.

This remains a visual prototype. Real notifications, complete native route/back-stack restoration, actual calculator-to-exact-recipe handoff, Android font scaling, TalkBack, adaptive rail layouts and native lifecycle recovery are unverified. No Android source, preferences, production artwork or release gates changed.

- [Calculator beside its current native reference](preview-current-ui-calculator.jpg)
- [Preserved More menu with shared activity](preview-current-ui-more.jpg)
- [Existing Learn library with shared activity](preview-current-ui-learn.jpg)
- [Ready reminder on the preserved More screen](preview-current-ui-ready-reminder.jpg)
- [More at 320 px and doubled text](review-current-ui-more-320px-200percent.jpg)
- [Calculator at 320 px and doubled text](review-current-ui-calculator-320px-200percent.jpg)

## App-wide active brews and reminders

The user identified that session banners inside Learn made timers appear owned by that feature. This revision adds a shared app shell using the native Brew / Log / More destinations and keeps Learn inside More. Active sessions move out of route content into one compact strip above navigation. One opens directly; multiple open a shared chooser. Ready cold batches take priority. A focused session suppresses its own duplicate strip while preserving access to other brews. Log and More provide focused navigation examples; their complete native screens are not recreated.

The app-owned ready banner sits outside every route's scrollable content. It offers Open and Dismiss without changing the user's screen, focus or reading position. The deadline updates shared chrome without rebuilding a reader. OS notification denial still allows foreground feedback; reminder opt-out suppresses interrupting feedback while retaining ready status. A persisted delivery revision prevents replay after dismissal/reload. Duration edits invalidate stale readiness, and filtration/end remove stale alert surfaces. Opening a timer from Learn, Log or More saves an exact return route; switching sessions retains the original return destination.

Browser review covered shared access from Learn, Log and More, a two-session chooser, opening cold brew from Log and returning there, and a fresh isolated review origin for deadline/reminder checks. A 14-hour timer reached readiness on More with OS access blocked; an app banner and ready strip appeared while the OS preview stayed hidden. After changing its duration to 15 hours and advancing the review clock, the new revision delivered another appropriate ready event. Dismissal retained readiness on Log; reload did not replay the banner. Returning from the user's cold timer restored Chemex's reader at step 9. Escape closed the chooser and returned focus to its opening strip. These time advances affected only the isolated test origin; the user's existing simulated sessions were preserved.

At 320 × 800 CSS px with doubled text and a ready banner, no product element overflowed horizontally. The first layout left too little space for content; concise activity status and enlarged-text grid layouts recovered a 302 px scrollable route region in the final measured view. Main navigation buttons measured 96.22 × 84.03 px. The two-session chooser also had no horizontal overflow and measured 651.65 px high within the 800 px viewport. Temporary viewport and text overrides were reset. These are browser measurements, not Android font-scale or accessibility proof.

Twenty deterministic checks pass, including six new checks for route isolation, exact reader return across session switching, denied OS access, persisted foreground deduplication, opt-out/unknown/visible-timer suppression, revision/cancellation and deadline delivery without rebuilding the current reader. The original fourteen clock, persistence, migration, manual action and full-illustration checks still pass. Source inspection confirmed root session observation and notification deep links in `StarlitNavHost`, and the existing Learn placement in `MoreScreen`; no native files changed. The [ownership and integration contract](APP-WIDE-BREWS.md) records the remaining native work.

- [Shared activity outside Learn](preview-app-wide-brews.jpg)
- [Shared two-session chooser](review-app-wide-chooser.jpg)
- [Ready reminder on More with OS access blocked](preview-app-wide-ready-reminder.jpg)
- [Ready reminder at 320 px and doubled text](review-app-wide-reminder-320px-200percent.jpg)
- [Two-session chooser at 320 px and doubled text](review-app-wide-chooser-320px-200percent.jpg)
- [Reader restored after opening a timer](review-app-wide-reader-return.jpg)
- [App-wide ownership, routing and delivery contract](APP-WIDE-BREWS.md)

This remains the selected prototype-first phase. Actual OS delivery, native lifecycle/back-stack restoration, foreground/background delivery races, all native destinations, hardware and assistive-technology behavior remain unverified. The older sections below retain the preceding layouts' evidence.

## Instruction artwork and coffee progress

The user identified a recognition icon being used where a full instructional scene was needed, requested the existing Chemex pouring scene's style, removed the redundant **Your batch** label, and requested coffee-themed progress. This revision retains seven shipping Chemex instructional images unchanged and adds fifteen full scenes: two missing Chemex actions, six espresso actions and seven cold-brew actions. All 22 example steps now render an instructional scene rather than an equipment-icon fallback. M3 navigation glyphs and the waiting screen's equipment badge keep their existing roles.

The built-in image generator received the shipping Chemex image as the style reference and existing equipment photographs as anatomy references. The new scenes use large supported objects, warm material rendering, a charcoal organic backdrop and transparent outer margins. Visual review checked the resting bloom's shallow collected liquid, open jar while stirring, separate lid, supported paper filtration, clear serving water, and distinct espresso start/stop actions. The selected machine's controls were checked against the [Breville BES500 manual](https://assets.breville.com/BES500/BES500_USCM_IB_T23_LR.pdf); the refrigerated preparation remains based on the [reviewed cold-brew guide](../../research/2026-10-02-method-guides/methods/cold-brew.md). Images contain no target numbers or scale readouts; the existing UI retains recipe quantities and completion criteria. These are visual checks, not proof with physical equipment or production approval.

The timer now uses a coffee-bean marker on a quiet coffee-toned track. The marker and fill show actual elapsed deadline progress; neither loops or claims to measure extraction. Unknown start time keeps determinate progress hidden. The redundant waiting-context line is removed; recipe provenance remains in Timer details and guide access stays in Steps. Saved sessions, original starts, duration edits, reminder guards and manual filtering remain unchanged.

All fourteen deterministic checks pass. The existing all-stage check now requires a full instructional image for every example step and rejects recognition-icon substitution. All fifteen PNGs are 4:3 RGBA with transparent outer corners; dimensions, hashes, exact final prompts and references are recorded in [ILLUSTRATIONS.json](ILLUSTRATIONS.json). The [artwork gallery](artwork-review.html) loaded every scene. The cold-brew filtration image kept its 4:3 ratio at 320 × 800 CSS px with doubled text (228.67 × 171.5 px in a 305 px document), with no product element overflowing horizontally. Coffee progress was also checked in dark appearance and at that narrow width. Temporary text/viewport overrides were reset, and existing simulated sessions were preserved.

- [Matched Chemex bloom in the guide](preview-matched-chemex-bloom.jpg)
- [Full illustration set beside its style reference](preview-matched-artwork.jpg)
- [Coffee progress and simplified waiting view](preview-coffee-progress.jpg)
- [Coffee progress in dark appearance](review-coffee-progress-dark.jpg)
- [Coffee progress at 320 px with doubled text](review-coffee-progress-320px-200percent.jpg)
- [Instructional image at 320 px with doubled text](review-matched-artwork-320px-200percent.jpg)
- [Saved asset paths, exact prompts and provenance](ILLUSTRATIONS.json)

JavaScript syntax, local HTML/CSS dependencies, Markdown links and Git whitespace checks pass. Hash comparisons confirm that all seven retained shipping instructional files match their Android sources. Only this prototype and its review artifacts changed. The fifteen lossless transparent PNGs remain design candidates; Android assets and approval manifests are untouched. Real notifications, native accessibility, physical technique and usability with people still require their own validation. The sections below retain the earlier revisions' evidence, including the superseded waveform and icon placeholders.

## Expressive timer revision

The user requested an M3 Expressive redesign consistent with the rest of the app. This revision uses the native brewing/preparation screens' open numeric hierarchy, the app bar's type scale, existing shape and palette tokens, 64 px primary actions and a local Roboto font. Cold-brew setup and editing use filled tonal time fields and a semantic reminder switch. The saved timer has connected duration/reminder/detail rows and static wave progress. A tertiary badge with a check mark signals readiness; filtration still requires confirmation. The header and primary-action styling also apply to the other example guides.

Source was inspected in this worktree at `5acdb5fe`. The previously captured [native app reference](../2026-10-02-method-icons/style-references/native-app-2026-10-02.png) was reviewed again. This pass did not capture or run a new Android build. The canonical checkout contains concurrent Android work and was left untouched.

Browser review covered quick timer creation, editing, the waiting and ready states, coffee dark appearance, and doubled text at 320 × 800 CSS px. The Chemex reader was also inspected with the shared app bar and actions; its exploration controls leave the saved cold timer intact. In the final narrow waiting view, the time numerals render at 114 px, no reviewed product element overflows horizontally, and the primary action measures 72 px high after text reflow. The waveform is intentionally clipped within its track. The editor's numeric fields stack at doubled text and remain inside the viewport. Space toggles the reminder switch; Escape dismisses the editor and returns focus to its trigger. The readout exposes its full time label without a ticking live announcement. These are focused browser checks, not TalkBack, Android font scaling or native touch-target proof.

The fourteen existing deterministic timer/controller checks pass. Local HTML/CSS/font dependencies resolve, JavaScript syntax checks and Git whitespace checks pass. Recipe durations, original-start preservation, saved sessions, reminder guards and manual filtration behavior remain intact. Reduced-motion rules were inspected; an OS motion preference change was not exercised.

- [Expressive saved timer](preview-expressive-cold-brew.jpg)
- [Ready to filter](review-expressive-ready.jpg)
- [Coffee dark](review-expressive-dark.jpg)
- [Timer editor](review-expressive-editor.jpg)
- [320 px with doubled text](review-expressive-320px-200percent.jpg)
- [Shared styling on the Chemex reader](review-expressive-chemex-reader.jpg)
- [Roboto source, checksum and license](assets/Roboto-SOURCE.md)

Alerts and permission prompts remain simulated. Browser storage establishes reload behavior only; background delivery, process death, reboot recovery, physical brewing and native accessibility remain unverified. The older screenshots below retain the preceding revisions' evidence.

## Saved cold-brew timer extension

The user chose to extend the prototype first. This pass adds a contextual **Start a timer** route for a batch made independently, and a shared saved timer view for that route and guided cold brew. Hours/minutes and reminder choice are per batch. The reviewed source guide stays at 14 hours; a changed guided duration is explicitly identified in Timer details. **Done for now** replaces the disabled long-wait action. A passive cold batch can coexist with an espresso or Chemex; one timer does not replace the other brew.

Browser review exercised a quick 13 h 30 min timer, a guided 15 h timer, leave/reload/return, foreground espresso preparation while the cold batch remains saved, and duration edits after an hour of simulated steeping. The changed finish retains the original start. The deadline displays **Ready to filter** and waits for physical confirmation. Quick completion records only a filtered-batch confirmation, without claiming the guide's recipe quantities or preparation.

Blocked simulated notification access preserves the saved timer and displays Notifications off. Enabling access exposes the ready-to-filter alert preview. Reminder opt-out suppresses that preview. An unknown start exposes no deadline or determinate progress; a future start is rejected; entering a known start recovers the finish time. Filtration help opens the reader without moving the live stage. These are browser simulation checks, not real notification delivery.

At a 320 × 800 CSS px viewport (305 px document width with the scrollbar) and 200% product text, the waiting view and timer editor had no horizontal overflow. Instructions measured 32 px; reviewed product buttons were at least 48 px high. The editor stacks hours/minutes. Escape dismisses the editor; temporary viewport and text-scale overrides were reset.

Fourteen deterministic checks pass for clock boundaries, duration validation, elapsed preservation, unchanged-edit deduplication, alert opt-out/denial, unknown time, v1 migration, two-session reload, manual filtering, timer cancellation and the existing short-brew pause/read-ahead behavior. The tests execute both the clock rules and actual prototype transition functions with a fixed clock and storage, and render all 22 reader stages with verified artwork/source paths. Local HTML/JS dependencies resolve and Node syntax/Git whitespace checks pass.

- [Saved timer view](preview-cold-brew-timer.jpg)
- [Ready-to-filter reminder preview](review-cold-brew-ready.jpg)
- [320 px / 200 percent waiting view](review-cold-brew-320px-200percent.jpg)
- [Native implementation audit and reminder contract](COLD-BREW-REMINDERS.md)

No Android files were changed. Browser wall time/localStorage prove reload behavior only; real alerts with the app closed, reboot/process recovery, time corrections, multi-tab coordination and actual permission/channel delivery remain unimplemented and unverified. Alert and permission previews are clearly identified by the external review harness. The native foundation already exists, but adding custom duration and the new UX still requires the domain/worker/UI work recorded in the linked audit.

## Existing visual language correction

The user rejected the first pass's visual direction. A source audit and a read-only capture of the running Android development app confirmed the mismatch: the app uses Material 3 Expressive, Android typography, 8/12/20/28/36 dp shape tokens, tonal surfaces and dynamic colors. The prototype had introduced a beige editorial palette, tightly tracked headlines, a central wordmark, custom status colors and an unrelated SVG illustration language.

Revision two reuses the app's hierarchy and components. The review default approximates the current emulator's blue light palette; sampled flat colors are measured, while their semantic-role assignment is inferred. Coffee fallback palettes are taken from `Theme.kt`. Browser controls expose these for design review only. Android dynamic color is not emulated.

Artwork roles stay distinct: Brew equipment silhouettes, Learn recognition icons and instructional action images. Compatible Chemex action assets are copied exactly from Android resources; they illustrate an action rather than certify the prototype's separate recipe quantities. Missing action artwork uses a plainly labelled reference fallback. Espresso and cold-brew instructional illustration production remains outstanding.

## Revision-two validation

The corrected Chemex preparation, physical clock, pause/resume and read-ahead paths were exercised again in the browser. Reading a later pour exposed no physical confirmation control; returning restored the live bloom. Pausing left physical elapsed time running. The live pour still separates 240 g total from +150 g added this pour.

At a 320 px viewport with doubled text, instructions render at 32 px, product buttons remain at least 48 px high, and reviewed product elements have no horizontal overflow. The full instructional image preserves its 4:3 ratio. Browser review found and fixed a missing height:auto rule that had allowed the intrinsic HTML height to stretch the image card. Coffee dark and current blue views were inspected.

All eight local images are exact copies of shipping resources. Embedded JavaScript syntax passes. Recipe quantities, instructions and timings match the preceding version; the final-pour artwork key now selects its matching approved image. Click state handlers remain unchanged. Static rendering also covered all 22 reader/live recipe stages; this is source-level evidence rather than a native run.

- [Corrected live guide](preview-app-language.jpg)
- [Corrected 320 px / 200 percent text](review-app-language-320px-200percent.jpg)
- [Coffee dark fallback](review-app-language-dark.jpg)

## Completed browser checks from the functional first pass

| Scenario | Observed result |
| --- | --- |
| Self-paced learning | Direct step access, Previous/Next and contextual explanation work. No extraction clock starts. Chemex's reading position at step 5 survives reload independently of live progress. |
| Chemex full brew | Preparation stays untimed. First water starts the shared clock. The 90 g first pour, 240 g cumulative target with +150 g addition, and 480 g final input stay distinct. Drainage requires an observed confirmation; no arbitrary total finish time is imposed. |
| Reading ahead during a brew | Viewing a later step and advancing simulated time preserve the physical stage. Returning restores the live action. The reader cannot confirm a pour by browsing to it. |
| Pause/resume | One tap suspends guidance. Known physical elapsed time continues, including simulated time advancement. Resume retains the stage. Unknown time remains explicitly unknown. |
| Known late start | Entering elapsed seconds preserves preparation and sets the intended physical origin. A ready bloom still requires Continue; elapsed time does not mark a pour done. |
| Unknown late start | Espresso permits yield-based manual completion with Time unknown. Chemex's timed bloom offers correction of a known start or deliberate ending, with no invented elapsed duration. |
| Espresso full brew | Basket/model scope, 18 g dose and 36 g beverage target are explicit. Reweighing after trimming is visible before starting. Pump-start includes pre-infusion; 25–35 s is a reference. Early preset completion stops the clock and visibly says not to restart the shot. No measured final yield is invented. |
| Cold brew full brew | Preparation and wetting remain untimed. Refrigerated placement starts the 14-hour steep; reload preserves it. Reaching 14 hours does not advance filtration automatically. Filtering is untimed. A 100 g concentrate + 100 g water serving stays separate from the 800 g extraction input. |
| Narrow layout | At a 320 × 800 CSS px viewport, the reviewed Chemex reading/live and completed-brew surfaces reflow without horizontal overflow. Visible product buttons and disclosures have at least 48 CSS px height; primary controls have at least 58 px. These are browser dimensions, not native dp proof. |
| Large text | At 320 CSS px, the review toggle doubles the instruction to 32 px. Quantities stack, warnings and confirmation stay readable, and the footer moves into normal flow. The reviewed live Chemex card has no overflowing product elements. Quantity and unit remain together in the pour headings and target cards. |
| Keyboard and focus | Enter activates Resume; Space opens the step explanation. Escape closes the late-start dialog and returns focus to Already started. Stage changes focus the new heading. This is focused keyboard evidence, not a complete assistive-technology audit. |
| Illustration corrections | Cold-brew stirring shows an open vessel with the lid beside it. Completion criteria are labelled Done when, so an illustrated desired state does not claim an action already occurred. Production illustration approval remains required. |

Review corrections also removed routine pause confirmation, restored independent
reading bookmarks, exposed the espresso dose check and early-stop instruction,
added unknown-start recovery, and consolidated duplicate live-return banners.

## First-pass visual evidence (superseded style)

- [Chemex live pour preview](preview-chemex-brew.jpg)
- [320 px with 200% product text](review-320px-200percent.jpg)

Screenshots show a simulated recipe state. Elapsed values are review-session
values, not measured brewing results. The full-page large-text capture includes
the external review controls and vertically scrolling content.

## Validation and limits

The embedded recipe data and local JavaScript pass Node syntax checking. Local artifact and
research links resolve to existing repository files; Git whitespace validation
passes. The design maps all 17 researched methods, while the interactive
prototype implements only the three representative examples above.

No application code, production assets, persisted Android session contracts,
translations, or release files changed. No Gradle build was needed for this
documentation and browser artifact. Browser review does not establish Android
layout, TalkBack, Switch Access, OS font scaling, process-death recovery,
background notifications, physical brewing, taste, or usability with people.
Reduced-motion CSS was reviewed; changing the OS motion preference was not
exercised in this pass.

The prototype uses browser wall time and localStorage. Native implementation
must preserve the existing session coordinator and add physical clock semantics
for prompt suspension; renaming the existing frozen-time Pause event would not
implement this design correctly. See the design's staged plan and acceptance
criteria before integration. The concurrent main checkout was left untouched.
