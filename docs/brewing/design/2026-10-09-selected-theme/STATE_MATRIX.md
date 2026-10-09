# Selected-theme state coverage

Design proposal, 2026-10-09. Forty-six representative screenshots cover meaningful presentation classes. Numeric values, counts, recipe text and source-specific triggers are variants, not a finite enumeration of every app combination. No behavior is implemented by this gallery.

| Board | States | Source contract | Coverage |
| --- | --- | --- | --- |
| Preparation | G01 Prime filter; G02 Dose and tare; B01 Basic ready | app/src/main/res/raw/pulsar_learn_guidance.json | Rendered |
| Guided bloom | G03 Pour bloom; G04 Close and swirl; G05 Wait for bloom | app/src/main/res/raw/pulsar_learn_guidance.json | Rendered |
| Current target and drawdown | G06 Bloom cue reached; G07 Main pour; G08 Drawdown | app/src/main/res/raw/pulsar_learn_guidance.json | Rendered |
| Serving and completion | G09 Safe serving; G10 Completed | app/src/main/res/raw/pulsar_learn_guidance.json | Rendered |
| Unfinished work remains visible | L01 Bloom pour unfinished; L02 Main pour slower; L03 Drawdown slower | app/src/main/res/raw/pulsar_learn_guidance.json | Rendered |
| Basic recipe timers | B02 Basic during bloom; B03 Basic after bloom; B04 Basic ended | app/src/main/res/raw/pulsar_learn_guidance.json | Rendered |
| Cold timer-only start origins | C01 Start now; C02 Started earlier; C03 Start unknown | viewmodel/ColdBrewStartFactory.kt; ui/guidance/ColdBrewGuidanceCatalog.kt | Rendered |
| Guided cold preparation | C04 Clean and prepare; C05 Combine and cover | viewmodel/ColdBrewStartFactory.kt; ui/guidance/ColdBrewGuidanceCatalog.kt | Rendered |
| Steep, reminder and filtration | C06 Steeping; C07 Reminder reached; C08 Filtering | viewmodel/ColdBrewStartFactory.kt; ui/guidance/ColdBrewGuidanceCatalog.kt | Rendered |
| Cold serving and cleanup | C09 Dilute serving; C10 Clean up; C11 Cold completed | viewmodel/ColdBrewStartFactory.kt; ui/guidance/ColdBrewGuidanceCatalog.kt | Rendered |
| Independent concurrent sessions | X01 One background brew; X02 Choose another brew; X03 Other reminder reached | ui/session/ActiveBrewSessionPresentationMapper.kt; ui/component/BrewActivityHost.kt | Rendered |
| Help, recipe and paused guidance | H01 Contextual help; H02 Read recipe ahead; H03 Guide paused | ui/session/ActiveBrewSessionPresentationMapper.kt; ui/component/BrewActivityHost.kt | Rendered |
| Reminder controls and limitations | R01 Edit reminder; R02 Precise reminder off; R03 Notifications denied | ui/screen/BrewSessionTimerControl.kt | Rendered |
| Coherent recoverable states | E01 Guide unavailable, timer valid; E02 Confirmation pending; E03 Confirmation failed | ui/session/ActiveBrewSessionPresentationMapper.kt; ui/component/BrewActivityHost.kt | Rendered |
| Restoration and cancellation | S01 Restoring; S02 Saved session unavailable; S03 Cancel selected brew | ui/session/ActiveBrewSessionPresentationMapper.kt; ui/component/BrewActivityHost.kt | Rendered |
| Methods without bloom | N01 Immersion; N02 Gentle pressing; N03 Beverage yield | data/model/BrewMethod.kt | Rendered |

## Shared variants and boundaries

- Guided first-water ready (G00) reuses B01's 00:00 / Not started / Start with first water layout with guided setup context. G02 Ready for water only navigates to that ready surface; it does not start the physical clock. This is a shared rendered layout rather than an additional screenshot.
- Unknown-start setup uses C01/C02's origin controls with Not sure selected, then C03; the Add brew start time dialog reuses C02's hours/minutes inputs. A late acknowledgement is not an exact physical timestamp. If physical origin remains unknown, precise elapsed/reminder timing remains unknown.
- A source-relative wait uses its reliably known action origin; first-water-relative references remain first-water-relative. Countdown triggers and hard constraints from a real source still apply. These screenshots do not replace every recipe's valid completion modes with manual completion.
- Cold timer-only active/expiry reuses C06/C07 clock, storage and reminder layout with End timer instead of Begin filtering. Guided mode retains the physical filtering action. A timer-only End timer is not evidence of filtration.
- Reminder removed reuses C06 with Until reminder replaced by No reminder set and Set reminder action. Cold elapsed remains. Saving/removal pending and failure reuse E02/E03 control treatment with appropriate operation wording; changing a reminder never changes physical origin.
- No other brew: omit the whole background row, as illustrated on G01–G04 and L01/L03. Multiple others use X02; the initiating strip says Other brews plus count (for example 2 active), and never implies all timers finish together. Attention sorting preserves independently valid sessions.
- Background reminder reached is X03. Several attention states use the chooser rows with per-session wording. Foreground completion, cancellation, help and switching do not complete other sessions.
- Moka heating/flow follows N01/N02's no-bloom task hierarchy with source text, fill-below-valve cue and source safety; no invented 0 °C temperature or assumed boiler capacity. Other no-bloom and bloom recipes reuse these layouts with their own source quantities, time origins, warnings and actions. Espresso's beverage-yield role must never become water input.
- Source stages driven by added water, cumulative water, beverage yield, observed event or external marker retain their source qualifier and explicit required report. The gallery does not add a sensor or new mass-entry system, or pretend recipe targets are actual measurements.
- Serving/handling warnings remain outside optional guidance density and Pause guide. Finished bloom art stays in any subsequent stage of a bloom-containing brew; methods without bloom omit it entirely. Exact frames remain the existing atlas/renderer contract; generated art is a visual approximation.
- Unsupported recipe/storage/runtime reasons share S02 with accurate reason wording and supported return action; no substitute recipe, reset, deletion or guessed clock. E01 is narrowly missing discretionary guide content with a separately valid timer, not invalid saved execution data. Retry guide, async pending/failure copy and notification-settings entry are proposed presentation affordances; implementation feasibility/approval is still required.
- Cancellation returns to the existing session destination and retains other brews; no new cancellation-success screen. Early finish, pause/resume availability and source-defined limits must use existing actions, not a universal new session engine. A genuinely pausable free stopwatch is out of scope.
- Contextual help can use a sheet or full reading surface with identical clock semantics; H01 renders the readable surface, H02 the recipe. Long text scrolls; footer remains reachable. Rotation, large font, dark/light theme variants, keyboards and animation frames are runtime/layout variants and require implementation testing, not additional invented product states.

## Quantity and timing fixtures

Pulsar uses the released Gagné 20 g / 340 g guide, 60 g bloom, about 99 °C, 1:00 first-water boundary and 3:30–4:30 drawdown reference. Cold fixture is 50 g coffee / 400 g extraction water (default 1:8), 14 h reminder. Its serving dilution is separately 100 g collected concentrate + 100 g cold water. Cold reminder reached does not end physical steeping. The filtering transition is user-reported; the displayed reported steep time is not a claimed exact liquid-separation timestamp. Basic ended time does not establish physical completion.

French press 20/300 (1:15), AeroPress 15/225 (1:15), Espresso 18/36 beverage yield (1:2) are illustrative source-default quantities, not measured inputs. AeroPress pressing copy follows official gentle-press instructions; no universal press duration is invented.

## Evidence

- [Gagné's Pulsar method](https://coffeeadastra.com/2023/09/13/the-pulsar-dripper/) and the released local guide supply valve, quantity and drawdown cues.
- [AeroPress instructions](https://aeropress.com/pages/how-to-use) and [official FAQ](https://aeropress.com/pages/faq) support gentle pressing.
- [OXO cold-brew manual](https://www.oxo.com/media/wysiwyg/PDF_Files/1403_gg_1272880%20Cold%20Brew%20Coffee%20Maker%20instructions.pdf) distinguishes steeping and filtration. App-specific refrigerated guidance and 14 h fixture come from the local catalog/factory, rather than substituting the OXO recipe.
- [Firsthand timer-origin confusion](https://www.reddit.com/r/pourover/comments/1azks3a/) and [guided-timer user reviews](https://play.google.com/store/apps/details?id=com.pareto.coffee) are qualitative input, not representative usability evidence. Display rules are design inferences, not research claims.

Source paths listed above are relative to app/src/main/java/com/adsamcik/starlitcoffee/; the Pulsar JSON is relative to the src checkout. See [earlier process analysis](../2026-10-08-flow-directions/cleaner-directions/PROCESS.md). All new recovery behavior, action wording and UI choices require review/approval before app implementation.
