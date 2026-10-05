# NextLevel Pulsar: a first paper-filter brew

Editorially reviewed, 2026-10-02. This full-size, paper-filter guide selects Jonathan Gagné's published steeped-bloom recipe at 20 g. The valve retains the bloom, then opens for fresh water to flow through. StarlitCoffee's everyday defaults are separate. No physical brewing test was performed.

## Equipment and starting recipe

Use a complete Pulsar, matching paper, stable heat-safe server, gram scale, timer, burr grinder and kettle; gooseneck optional. Support it level with room to turn the valve.

Use **20 g coffee / 340 g brew water: 1:17 by input mass**, a relatively coarse, even grind, and **99 °C water** for Gagné's light-roast example. Rinse water is additional and discarded; beverage yield is not fixed. Aim for **3:30–4:30 from first bloom pour through final drainage**; drainage and taste govern completion. [Original recipe](https://coffeeadastra.com/2023/09/13/the-pulsar-dripper/)

## Brew

1. **Prime the paper.** Remove the barrel, close the valve and cover the base ridges with water. Float dry paper on it, then open over the sink to seat it with little trapped air. Close again. Fit the barrel firmly, threaded end downward. [Gagné](https://coffeeadastra.com/2023/09/13/the-pulsar-dripper/), [Rao's assembly instructions](https://www.scottrao.com/blog/pulsar-recipe)
2. **Add and level the coffee.** Shake gently without compacting it, fit the dispersion cap, and tare the complete brewer/server on the scale. Practice the valve beforehand: horizontal at 3 o'clock is closed; upright at 12 o'clock is open. Use that quarter-turn range and stabilize the base while turning. [Manufacturer guide](https://nextlevelbrewer.com/wp-content/uploads/2023/06/PulsarUserGuide.pdf), [Kaldi's valve explanation](https://kaldiscoffee.com/blogs/recipes/nextlevel-pulsar-dripper-brew-recipe)
3. **Start the timer; pour 60 g with the valve open.** Close at first dripping, even while finishing the pour. This helps water displace air between coffee particles. Hold only the base and swirl gently to wet dry pockets. [Gagné](https://coffeeadastra.com/2023/09/13/the-pulsar-dripper/)
4. **Wait until 1:00, then open fully.** Add the remaining **280 g** through the cap in small pulses, maintaining roughly 1 cm above the bed. With a gooseneck, Gagné recommends relatively fast circular pours to avoid digging holes. Pulse timing follows water level; there is no fixed count or universal rate. Keep the same tare: finish at **340 g cumulative**, including bloom. [Gagné](https://coffeeadastra.com/2023/09/13/the-pulsar-dripper/)
5. **Let it drain.** After the last pour, allow standing water to disappear and the flow to subside to final drips. Slowing near the end is normal. Close, lift the assembled brewer only by its base, set it down safely, then serve. [Manufacturer guide](https://nextlevelbrewer.com/wp-content/uploads/2023/06/PulsarUserGuide.pdf)

## Taste and drainage

If flow stalls, first check that the valve opens, the paper is flat, and the cap and server support are correct. Next brew, reduce agitation; if necessary, grind coarser. For a fast, thin or sour result with correct setup, try slightly finer. For drying harshness, try coarser or less agitation; developed roasts can benefit from cooler water. Change one variable at a time. A long brew can still taste good. [Manufacturer guidance](https://nextlevelbrewer.com/wp-content/uploads/2023/06/PulsarUserGuide.pdf)

## Variants, limits and cleanup

Rao uses **25 g / 425 g**, **75 g** bloom, near-boiling water, roughly **45–60-second** release, **1–2 cm** pool and final swirl. Kaldi's uses **20 g / 320 mL**, closed-start bloom and different pulses; keep its grinder suggestions and units in context. [Rao](https://www.scottrao.com/blog/pulsar-recipe), [Kaldi's](https://kaldiscoffee.com/blogs/recipes/nextlevel-pulsar-dripper-brew-recipe)

The chamber's **380 mL volume is not a maximum cumulative water dose**. Scaling changes bed depth and drainage. The Mini needs separate preparation and dose/grind choices. [Specifications](https://nextlevelbrewer.com/shop/nextlevel-pulsar-brewer/), [Mini instructions](https://nextlevelbrewer.com/pulsar-mini-recipes/)

For paper, let the brewer cool, remove cap/barrel, then invert the base over a bin and open the valve. Rinse and wipe oils from the barrel; store disassembled. Keep parts off hot surfaces; avoid bleach. Metal discs may tolerate less agitation before clogging: follow their separate procedure, empty grounds with the barrel still fitted, then disassemble and remove the delicate disc carefully. Clean after each use and protect it in its case. Do not dispose of it like paper. [Paper care](https://nextlevelbrewer.com/wp-content/uploads/2023/06/PulsarUserGuide.pdf), [Metal care](https://nextlevelbrewer.com/shop/pulsar-stainless-steel-filter-discs/)

## App-audit appendix

Paths below are repository-relative; detailed findings and recommendations are in `pulsar.json`.

- `data/model/BrewMethod.kt:86` (under `app/src/main/java/com/adsamcik/starlitcoffee/`): everyday defaults are 1:17, 3× bloom, 45 seconds, five post-bloom pulses, 93–96 °C and 210–270 seconds. At 20/340, these calculate as 60 + five × 56 g, cumulative 60/116/172/228/284/340. Label this app-authored recipe; it is not Gagné's fixed 60-second/99 °C workflow.
- **High:** `ui/guidance/PulsarBuiltInGuidanceCatalog.kt:292` says “remove the brewer carefully”; line 311 says “Keep hands clear of the hot liquid and use a stable surface.” Add base-only lifting to shared safety and completion.
- **Medium:** `data/model/BrewMethod.kt:98` sets `capacityMaxG = 380`; `domain/BrewCalculator.kt:75` calculates `ceil(waterG.toDouble() / method.capacityMaxG)`. Separate chamber volume from cumulative input. Its inherited 2.0 g/g loss estimate and `domain/BeverageOutputEstimator.kt:33`'s 2.2 are predictions, not yield targets.
- **Medium:** `viewmodel/BrewDerivation.kt:158` creates 55/45/35-second freshness blooms, while durable `LegacyBrewSessionStartFactory.kt:124` keeps Pulsar at 45 seconds. Reconcile recipe identity/timing. The inherited decaf rule subtracts 30 seconds without Pulsar-specific evidence.
- `app/src/main/res/raw/pulsar_learn_guidance.json` matches Gagné's quantities. Clarify stage 3/4 closure during pouring, air-between-particles explanation and circular gooseneck pouring advice. All 23 locales match numeric targets; language quality is unreviewed. Learn is educational, outside exact P1 session contracts.
- **Medium:** `data/model/FilterType.kt:15,20` asserts exact hole counts and comparative clarity without sufficient primary evidence. Generic paper timing/agitation must not silently become a metal recipe. `app/src/main/assets/grinders.json` already removes metal predictions and keeps Kaldi's paper-only suggestions; preserve that boundary. Generic 2–4/3–5-click troubleshooting and a universal seven-day rest claim in `values/strings.xml` need replacement with observable adjustments.
- **Low:** `BrewMethod.kt:264` calls 1:16 “Bright” and 1:18 “Rich”; use strength language. `values/strings.xml:87`'s “Most users” is not a measured population claim.

## Unresolved evidence

Metal evidence remains partial: care text was read in the browser after web timeouts, but exact 19K/40K specifications remain unresolved. No fixed yield, universal decaf time or grinder conversion was established. Manufacturer 4–7-minute timing covers broader recipes than Gagné/Rao; it is not an interchangeable finish rule.
