# Cold brew: refrigerated concentrate

Cold brew soaks coffee before separating the grounds. This guide covers a refrigerated home vessel and paper filtration, excluding nitrogen dispensing, canning, commercial production and room-temperature processes.

**Editorially reviewed · 2 October 2026 · no physical brewing test.** Batch size, clock and weighed serving are app-authored choices based on Counter Culture's refrigerated recipe, without storage-life validation.

## Equipment and starting recipe

Use a clean, food-safe covered vessel of approximately 1.5 L with stirring space, scale, grinder, spoon, timer, refrigerator thermometer, sieve, supported paper filter and clean receiving container. Check its actual fill limit.

Start with **100 g coffee and 800 g cold drinking water**, measured by mass: coffee-to-input-water **1:8**. Use medium-coarse grounds, slightly coarser than ordinary pour-over grounds. Start with water refrigerated at **4 °C or colder**, and keep the covered vessel at that temperature throughout. The selected **14-hour steep is 50,400 seconds**; preparation and filtration are additional time. These ratio, texture and steep choices follow [Counter Culture's guide](https://counterculturecoffee.com/blogs/counter-culture-coffee/guide-to-cold-brew).

This makes **concentrate**. Collected mass varies; weigh it rather than assuming a fixed loss. For one serving, weigh **100 g concentrate plus 100 g cold water**, producing **200 g before ice or milk**. This app-selected mass implementation of equal-parts dilution excludes ice so strength is reproducible.

## Brew it

1. **Prepare the cold, clean setup.** Wash hands and equipment; rinse away detergent and follow each item's cleaning instructions. Check refrigerator temperature and clear a stable shelf. [BCCDC's household advice](https://www.bccdc.ca/health-info/prevention-public-health/food-safety) supports clean utensils and refrigeration at or below 4 °C.
2. **Combine and wet evenly.** Put the grounds in the vessel, add all 800 g water and gently stir through the mixture. Check for dry clumps, including below the surface; the water total remains 800 g. Cover immediately.
3. **Refrigerate and start the steep clock.** Start at zero when the fully wetted, covered vessel enters the refrigerator. Label the preparation time. Leave it for 14 hours. Appearance and elapsed recipe time do not prove safety.
4. **Separate the grounds.** At 14 hours, strain into a clean server, then pass the liquid through paper for less sediment. Support the filter securely and pour in portions below its rim. Stop when the useful liquid has drained; do not force an overloaded filter. Filtration is untimed and can take longer with fine particles. Serve or cover and refrigerate promptly after filtering.
5. **Measure the serving, then taste.** Combine the 100 g concentrate and 100 g cold water. Adjust dilution before changing extraction. Keep unused concentrate cold. Record collected mass separately from serving water.
6. **Clean immediately.** Dispose of the grounds and paper; wash the vessel, lid and receiving equipment and allow reusable parts to dry. Follow a reusable filter's own storage instructions.

## Troubleshooting and variants

Check quantities, full wetting, refrigerator temperature and dilution. Too intense: add measured serving water. Too thin: reduce dilution first. Flat or unpleasant: try another coffee or one small grind/time change in the next refrigerated batch. Slow filtering or sediment: check paper seating, overfilling and accumulated grounds; a sieve-first pass or fresh paper can help. These troubleshooting suggestions are app-authored.

Ready-to-drink cold brew uses a different extraction recipe and should not automatically receive this concentrate's dilution. Dedicated brewers also differ: [OXO Compact](https://www.oxo.com/blog/coffee-and-beverages/how-to-make-cold-brew-coffee) specifies coarse coffee, a stronger concentrate, 20–24 hours in the fridge, and its own automatic drain. Follow that model's instructions rather than transferring this jar procedure.

## Handling boundaries

Plan prompt serving after filtration; refrigerate leftovers in a clean covered container. This review establishes **no maximum safe home storage period**. A manufacturer's 14-day claim does not validate this recipe, dilution or handling. [Health Canada](https://www.canada.ca/en/health-canada/services/general-food-safety-tips/food-safety-you.html) cautions that smell cannot establish safety. [Minnesota's retail guidance](https://www.mda.state.mn.us/food-feed/unique-foods-processed-retail) requires cold brewing/storage and date marking, with assessment for room-temperature or extended storage; this is not universal household shelf-life validation. [BCCDC's 2017 nitro assessment](https://www.bccdc.ca/resource-gallery/Documents/Educational%20Materials/EH/FPS/Food/Nitro_Cold_Brew_Coffee_Food_Safety_Risks.pdf) concerns nitrogen and reduced-oxygen packaging, not this vessel.

## App-audit appendix

- **High:** `app/src/main/java/com/adsamcik/starlitcoffee/data/model/BrewMethod.kt` gives cold brew 20–25 °C, contradicting correct refrigerated safety copy. Align preparation and holding defaults.
- **Info:** Its 12–24-hour range/coarse descriptor are another generic starting profile, not inherently incorrect. `app/src/main/java/com/adsamcik/starlitcoffee/domain/brewing/session/LegacyStagePlanFactory.kt` correctly separates manual filtering and counts down 12 hours. Integrating this selected recipe would instead use 14 hours and medium-coarse texture.
- **Medium:** `app/src/main/java/com/adsamcik/starlitcoffee/domain/BeverageOutputEstimator.kt` already qualifies 2 g/g apparent loss. `app/src/main/java/com/adsamcik/starlitcoffee/domain/brewing/BrewingCatalog.kt` uses `CollectedConcentrate(2.0)`; distinguish measured concentrate from diluted serving mass throughout the calculator.
- **Low:** `app/src/main/java/com/adsamcik/starlitcoffee/domain/BrewCalculator.kt` applies the inherited 30-second decaf subtraction without evidence of universal shortening.
- **Preserve:** `app/src/main/res/values/strings.xml` and `app/src/main/java/com/adsamcik/starlitcoffee/ui/guidance/LegacyBuiltInGuidanceCatalog.kt` already require cleanliness and refrigeration. P1 has no cold brew recipe/erratum. Model-specific grinder bands must remain scoped.

## Unresolved evidence

Hario MCPN manual URLs could not be opened, and the FIC manual returned 403; snippet-only recipe numbers are excluded. Filter duration, batch yield, decaf changes and a safe storage life remain unverified. No source establishes a universal 2 g/g loss, lower acidity, or a fixed caffeine advantage. English source review is not localization, device or kitchen validation.
