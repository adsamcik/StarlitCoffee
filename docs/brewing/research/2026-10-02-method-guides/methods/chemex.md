# Chemex brewing guide

Editorially reviewed, 2026-10-02. English research content; no physical brew or device test.

Chemex is a glass pour-over brewer: water passes through coffee and bonded paper into the carafe. This guide covers a **six-cup Classic Chemex** with matching paper. The selected **30 g coffee / 480 g water** recipe is an **app-authored starting point**, chosen for a manageable batch and two main pours. Its taste and total time have not been independently validated. It follows manufacturer handling and the app's everyday defaults; it is not an exact Chemex, Stumptown, or Blue Bottle recipe.

## Equipment and starting recipe

Use the brewer, compatible bonded paper, a gram scale, timer, kettle with a controllable stream, grinder, spoon, and mugs. Six-cup paper options are FP-1, FC-100, FS-100, and FSU-100. [Chemex six-cup instructions](https://chemexcoffeemaker.com/products/six-cup-classic-chemex).

- Coffee: **30 g**, medium-coarse, visibly granular rather than powder.
- Brew water: **480 g**, plus separate water for rinsing.
- Ratio: **1:16 by mass**, based on water added, not coffee collected.
- Temperature: **93–96 °C** as our starting range.
- Bloom: **90 g**; first main pour adds **150 g** to **240 g total**; last adds **240 g** to **480 g total**.
- Finish: observe drainage. This batch has no independently verified total-time target.

The temperature and ratio are selected defaults, not universal optima. Blue Bottle publishes a different 600 g batch with 36–46 g coffee and 200–210 °F water (about 93–99 °C). [Blue Bottle guide](https://bluebottlecoffee.com/us/eng/brew-guides/chemex).

## Brew step by step

1. **Fit the paper.** Open the cone with three layers toward the pouring spout and one opposite. Keep the spout groove open: escaping air needs that route while coffee enters the carafe. [Chemex folding instructions](https://chemexcoffeemaker.com/pages/how-to-brew-with-chemex).
2. **Rinse and empty.** Wet the paper with hot water, then discard every bit of rinse water before brewing. Return the brewer to a stable scale. Rinsing is our chosen preparation step; Chemex makes it optional. [Chemex FAQ](https://chemexcoffeemaker.com/pages/faq).
3. **Add the coffee.** Grind and weigh 30 g, gently level the bed, and zero the scale with brewer, filter, and grounds in place.
4. **Wet the bed.** Start the timer at the first contact of brewing water with coffee. Pour 90 g evenly. If dry pockets remain, gently stir once to wet them; avoid striking the paper. Wait until **0:45 elapsed**, counting the pouring time within those 45 seconds. Bubbling may be vigorous or slight; complete wetting matters more than visible swelling. These quantities are our choices.
5. **Pour to 240 g total.** Add 150 g with a gentle circular stream over the coffee. Pause whenever liquid rises faster than it drains. Keep generous room below both paper and glass rims. This is cumulative water, not another 240 g addition.
6. **Pour to 480 g total.** Once the level has visibly fallen, add the last 240 g gradually. Split it into smaller additions if needed for safe headroom. Leave the scale running; never zero it between pours.
7. **Let it drain and serve.** Wait until standing water above the bed disappears and the continuous flow becomes occasional drips. Record the time, remove the wet filter carefully, then gently swirl and serve using the collar or handle. Do not wait for every last drip or treat a timer alert as drainage completion. This observable endpoint is our baseline choice, consistent with Blue Bottle's full-drawdown approach.

## Adjustments, sizes, and cleanup

For slow flow, stop adding water and check filter fit and the spout first. Once airflow is clear, try a slightly coarser grind next brew. If drainage is fast and coffee tastes thin or sharply sour, first confirm even wetting and the weighed water, then try slightly finer. Change one thing at a time. Chemex identifies blocked spouts and fine grounds as separate causes of flooding. [Manufacturer troubleshooting](https://chemexcoffeemaker.com/pages/faq). For balanced but weak coffee, our adjustment is a little more coffee at the same water amount, informed by [Chemex's stronger-coffee suggestion](https://chemexcoffeemaker.com/products/six-cup-classic-chemex).

The **three-cup** brewer uses **FP-2 or FP-2N half-moon paper**, not the larger folded sheets. A smaller app-authored example is 20 g / 320 g, with a 60 g bloom; recheck pour sizes and drainage. The brand's five-ounce “cups” are not ordinary mugs. The glass bump marks full capacity on three-cup models but halfway on five- to ten-cup models. Mass targets cannot be replaced with those marks. [Three-cup specification](https://chemexcoffeemaker.com/products/three-cup-classic-chemex), [Chemex FAQ](https://chemexcoffeemaker.com/pages/faq).

Keep the brewer stable, fingers clear of hot glass, and liquid below the rim. Let it cool; discard the filter and wash the glass with warm soapy water. Remove the wooden collar and tie before washing; secure bare glass if using a dishwasher. Chemex prohibits microwave reheating. [Classic Series care](https://chemexcoffeemaker.com/pages/classic-series-product-support).

## App-audit appendix

Paths below are relative to the repository; findings reflect current code, not just the August audit.

| Severity | Current evidence | Recommendation |
| --- | --- | --- |
| Info | `data/model/BrewMethod.kt:235` under the Java package: CHEMEX already has 1:16, 93–96 °C, 45-second default bloom, 3× bloom water, two main pours, 240–330-second reference, unknown capacity, and no fixed decaf reduction. | Keep these as app choices. Do not attribute the combined recipe or its finish range to one source. |
| Info | `domain/brewing/session/LegacyStagePlanFactory.kt:116` and `ui/guidance/LegacyBuiltInGuidanceCatalog.kt:245,448` already use manual drainage confirmation, compatible paper, spout checks, and hot-glass warnings. | Preserve these correct behaviors. |
| Addressed in native content, 2026-10-04 | `domain/brewing/BuiltInP1RecipeCatalog.kt` labels `chemex_42_700` app-authored. Its six-cup equipment, 100–125 g bloom, 400/700 g cumulative pours, 94–96 °C and 300–390-second reference remain intact. | Runtime provenance states that Chemex FAQ supports handling rather than these recipe numbers. Equal dose and water do not establish creator attribution. The current Stumptown page no longer exposed its numerical recipe to the text browser on 4 October; the earlier comparison remains research history, not refreshed evidence. |
| Addressed in native content, 2026-10-04 | `ui/guidance/P1ExactGuidanceFactualErrata.kt` supplies app-authored provenance, explicitly prepares the 42 g coffee on a zeroed scale, and replaces stage 06's bitter-tail cue with absent standing water and occasional drips. | Historical manifest/images and the seven-stage executable plan remain intact. Filtration still requires the user's physical observation; its time reference is not a completion signal. [Chemex preparation](https://chemexcoffeemaker.com/pages/faq), [Blue Bottle drawdown](https://bluebottlecoffee.com/us/eng/brew-guides/chemex). |
| Medium | `domain/brewing/BuiltinBrewerProfileRecipeDefaults.kt:170,197` and `domain/brewing/BrewingCatalog.kt:240` assign Chemex a 2.0 g/g retention model. `domain/BeverageOutputEstimator.kt:56` correctly returns null for Chemex. | Resolve the inconsistent output contracts; keep actual beverage yield unknown unless measured. No reviewed source validates the coefficient. |
| Info | `app/src/main/assets/grinders.json:332` stores Encore ESP setting 30 alone. | Correct model-specific manufacturer starting point; retain tuning language and do not extend it to the original Encore or other grinders. [Baratza manual, p. 5](https://assets.breville.com/ZCG495/manual-encoreesp-v1-0-en-010923.pdf). |

Java package prefix: `app/src/main/java/com/adsamcik/starlitcoffee/`. `docs/brewing/learning-guide-factual-audit-2026-08-19.md:37` correctly calls 42/700 synthesized; runtime evidence wording is less explicit. Catalog presence alone does not verify localization or UI execution.

## Unresolved evidence

No critical hardware blocker remains for the stated six-cup setup. Recipe timing, taste, and scaling remain untested. The three-cup product inconsistently prints “15 Ounce / 1 Pint”; use its marked capacity, not an exact SI conversion. George Howell's older six-cup-for-one URL returned site navigation without recipe content, so it was not used. The app's `SRC-SCHMIEDER-FLOW` identifier was not resolved to a primary document in the inspected guide records; it does not establish a named Chemex recipe here.
