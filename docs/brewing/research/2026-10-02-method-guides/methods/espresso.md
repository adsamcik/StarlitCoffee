# Espresso: one measured pump-machine shot

Editorially reviewed, 2026-10-02. English research content; no physical brew test.

Espresso forces hot water through finely ground, compacted coffee. This example uses a **Breville Bambino Plus BES500 with its supplied 54 mm, two-cup single-wall basket**, an unpressurized basket specified for 16–19 g. Other baskets require their own fit checks. [Breville manual, pp. 10–12](https://assets.breville.com/BES500/BES500_USCM_IB_T23_LR.pdf)

## Equipment and starting recipe

Use the machine, basket, compatible tamper, espresso-capable burr grinder, fresh beans, cup, scale reading 0.1 g, timer, dry cloth and knock box. A funnel is optional.

Start with **18 g dry coffee → 36 g collected espresso**, a **1:2 coffee-to-beverage mass ratio**. Reservoir fill and water passed through the puck are separate, unmeasured quantities; 36 g is not 36 ml. Counter Culture publishes this dose/yield with a 25–35-second extraction window. Our machine-specific workflow and explicit clock convention are an **app-authored adaptation**, not its exact published procedure. [Counter Culture recipe](https://counterculturecoffee.com/pages/quick-easy-espresso)

The Bambino controls brew temperature; its milk-temperature buttons do not set brew temperature. No numeric brew setpoint was verified. Leave factory pre-infusion unchanged. Count time **from pump start, including pre-infusion, to pump stop**; first-drip timing is a different measurement. Treat 25–35 seconds as a starting comparison, with yield and taste taking priority. [Breville controls](https://assets.breville.com/BES500/BES500_USCM_IB_T23_LR.pdf), [Barista Hustle timing](https://www.baristahustle.com/espresso-recipes-time/)

## Make the shot

1. **Prepare the machine.** Complete its first-use cycle if necessary, seat the water tank and fill with cold potable water up to MAX. Set it on a stable, dry counter with the drip tray fitted. Wait for ready lights.
2. **Prepare the basket.** Fit the single-wall two-cup basket, warm the portafilter as directed, then dry it.
3. **Grind and weigh 18 g.** Tare the empty portafilter to measure actual grounds. Start espresso-fine; adjust by flow rather than universal dial numbers. Distribute evenly, then tamp level, firmly and consistently. [Counter Culture preparation](https://counterculturecoffee.com/pages/quick-easy-espresso)
4. **Check clearance and seal.** Use the Razor check after tamping; clear the rim. Reweigh after trimming: stay within 16–19 g, otherwise reprepare. Target twice the actual dose. Never force an overfilled portafilter. Briefly flush, then lock in securely. [Breville dose procedure](https://assets.breville.com/BES500/BES500_USCM_IB_T23_LR.pdf)
5. **Tare and start.** Put the cup on the scale and zero it. Press 2 CUP, starting your timer as the pump begins. Initial low-pressure wetting is pre-infusion; do not restart the clock at the first drops.
6. **Stop by mass.** Press the running shot button again at your chosen yield: 36 g for 18 g. Allow for remaining drips; no early-stop offset is universal. Record pump-stop time. If the preset stops first, record the result; do not restart that shot. Use the manual's manual/programmed-shot instructions next brew. [Breville controls](https://assets.breville.com/BES500/BES500_USCM_IB_T23_LR.pdf), [Yield before time](https://www.baristahustle.com/espresso-recipes-time/)
7. **Taste and record.** Taste after it cools enough, noting dose, final yield, time and grind. Keep dose and yield fixed while making the next grind adjustment.

## When the result is wrong

Spraying or irregular, watery flow suggests uneven extraction: check distribution, level tamp, basket fit and cleanliness before grinding finer. Visible flow cannot detect every small channel. [Barista Hustle channelling](https://www.baristahustle.com/lesson/b1-3-02-channelling/)

For a consistently fast, thin or sharply sour shot, try slightly finer grind. For a consistently slow or harsh shot, try slightly coarser grind. Change one variable, reweigh the dose, and retain the chosen comparison yield. With even flow, experiment with yield separately; sourness has several possible causes. If nothing flows, stop and check the tank, basket blockage and overfilling. [Counter Culture adjustments](https://counterculturecoffee.com/pages/quick-easy-espresso), [Barista Hustle flow limits](https://www.baristahustle.com/espresso-recipes-time/)

## Variants and cleanup

Dual-wall baskets provide their own restriction and suit preground coffee; these single-wall grind/flow expectations do not transfer directly. Make two separate shots instead of doubling the dose. Lever machines such as Flair 58 require their own preheat, filling, lever and purge sequence. Portable Picopresso needs externally heated water and manual pumping. Use each model's instructions for pressure release. [Flair guide](https://cdn.shopify.com/s/files/1/0983/8097/2334/files/flair_58_2023_brew_guide_feb_2023_v4_web_0930537d-e313-4f83-a544-33ab56b5983c.pdf), [Wacaco hardware](https://www.wacaco.com/products/picopresso)

Never unlock during extraction. Stop first and let flow settle. If pressure or blockage is suspected, leave it locked, switch off and cool; contact Breville if unresolved. Use handles for hot parts. Remove grounds and rinse as directed; unplug and cool before further cleaning. Follow specified cleaning/descaling cycles. [Breville safety and maintenance](https://assets.breville.com/BES500/BES500_USCM_IB_T23_LR.pdf)

## App-audit appendix

- **Correct:** `data/model/BrewMethod.kt` already defaults to 1:2 beverage yield and 25–35 seconds. `calculator/CalculatorQuantityTarget.kt`, `domain/BeverageOutputEstimator.kt` and `viewmodel/LegacyBrewSessionStartFactory.kt` preserve output mass. Paths here are under `app/src/main/java/com/adsamcik/starlitcoffee/`.
- **Medium:** `ui/screen/GrindPrepScreen.kt:452` labels espresso's yield as “Water”; use a beverage-yield label. Its temperature row also lacks machine-control context.
- **Medium:** `ui/screen/BrewTimerScreen.kt:136` auto-starts non-bloom timers on screen entry, which does not establish pump-start timing. `app/src/main/res/values/strings.xml:425` permits blonding as a stop rule; align it with selected yield. Typed `domain/brewing/session/LegacyStagePlanFactory.kt` and `ui/guidance/LegacyBuiltInGuidanceCatalog.kt` already use manual observation and selected yield.
- **Low:** `data/model/GrindDescriptor.kt` maps the espresso fallback to powdered sugar; prefer flow-based espresso-fine guidance. `app/src/main/assets/grinders.json` contains model-specific entries; the Encore ESP setting 15 is supported only as an 18 g medium-roast starting point, not a universal setting. [Baratza manual, p. 5](https://assets.breville.com/ZCG495/manual-encoreesp-v1-0-en-010923.pdf)

## Evidence limits

Counter Culture does not define its clock origin. Brew setpoint, factory pre-infusion duration and pressure-release wait remain unquantified. C40/Niche settings and decaf modifiers were not revalidated. The old Flair PDF returned 404; the current linked guide was read. Breville's cleanup tutorial failed to fetch; its manual supplied maintenance evidence. This is English research content, without physical brewing or device execution.
