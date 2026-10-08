# French press: a conventional first brew

Editorially reviewed, 2026-10-02. English research content; no physical brew test.

French press coffee steeps in one vessel before a mesh screen holds back most grounds. Expect a full-bodied drink with some fine sediment. This guide selects an **app-authored conventional baseline**, informed by Bodum's procedure and roaster recipes: coarse coffee, four minutes before a gentle plunge, then decant. Its quantities and temperature are starting choices, not a universal recipe or an exact Stumptown, Counter Culture, or Hoffmann recipe.

## Equipment and starting recipe

Use a clean **1 L French press**, its matching assembled plunger, a burr grinder, gram scale, timer, kettle, heat-safe plastic stirring spoon, and cups or a separate serving carafe. A smaller press is suitable only if the batch fits its safe fill instructions. “Cups” on brewer packaging are not mug servings. For the Bodum models covered by [its manual](https://d9pl0lig74xnv.cloudfront.net/product_manuals/z/p/zp2019-000001_frenchpress_coffeemakers_v1-20190308_pages.pdf), leave at least **2.5 cm below the rim**; follow other makers' limits for their equipment.

Start with **30 g coffee and 450 g brew water**, a **1:15 ratio by input mass**, at **95 °C**. Grind coarse, with visibly distinct particles rather than powder. These 450 g are water added to the grounds, excluding preheating water. Poured beverage will differ: liquid remains with the grounds, and leaving sediment behind changes recovery. There is no promised cup weight; weigh the collected coffee if you need one.

## Brew it

1. **Check and warm the press.** Inspect the beaker and assemble the screen according to its model's diagram, with all retaining parts secure. Rinse with hot water, then empty. Add the coffee and tare the scale. Preparation makes the water measurement repeatable.
2. **Start the timer with the first water.** Add all 450 g, wetting the grounds. Stop if the safe fill limit would be exceeded; resize the whole recipe next time. Stir gently just enough to wet dry pockets. Fit the lid with the plunger raised; use the maker's spout-cover or locking arrangement. Do not press yet.
3. **Wait until 4:00 from first water contact.** Leave the press undisturbed. A floating layer of grounds can form. Four minutes is the selected steep reference, not a deadline for finishing the drink.
4. **Plunge gently.** Move the press off the scale onto a stable, heat-safe counter. For the cited Bodum models, point the spout away from you. Hold the handle and lower the screen straight down with light hand pressure, stopping at the grounds rather than compressing them. Smooth movement is the cue. If it binds or requires force, stop and follow the maker's recovery instructions; never push harder to meet the clock.
5. **Pour off the coffee.** Open the spout as instructed and decant into cups or a carafe immediately after plunging. Pour steadily and leave the last sediment-heavy liquid if desired. The brew ends when you have safely decanted it; pressing and pouring add time beyond 4:00. [Stumptown](https://www.stumptowncoffee.com/pages/brew-guide-french-press) also advises moving off the scale and transferring unserved coffee to a carafe.

## Troubleshooting and other techniques

For resistance or excessive grounds in the cup, first check a clean, correctly assembled, undamaged screen and a straight, gentle plunge. A finer grind is not the remedy for a blocked screen. For an unpleasantly silty cup, pour more gently and leave more residue. Some sediment is normal for mesh filtering.

If the coffee is weak but otherwise pleasant, increase dose slightly while keeping water constant. If it is thin and sour, confirm water temperature and full wetting, then try a small grind adjustment within the brewer's allowed range. For harsh bitterness, check cleanliness and prompt decanting, then try slightly coarser coffee or a lower temperature. These are app-authored diagnostic suggestions: change one variable and compare cooled samples.

[Counter Culture's recipe](https://counterculturecoffee.com/pages/quick-easy-french-press) instead uses 45 g/800 g, medium-coarse coffee, a stir at 0:30 and a plunge at 6:00. Its stated 1:18 is rounded: 800/45 is approximately 17.78.

**Hoffmann's settling technique is a separate approach.** His [original interview](https://imbibemagazine.com/tools-and-tips-for-better-french-press-coffee/) describes medium-coarse coffee, four minutes before stirring the surface crust and skimming floating material, then another five minutes of settling. The mesh rests at the liquid surface instead of plunging through the bed; pour gently. Do not graft this sequence onto the four-minute conventional plunge or let it override equipment restrictions.

## Care and safety

For the cited Bodum glass models: avoid damaged glass, metal stirring utensils, and stovetop heating. Keep hot equipment stable and away from children. Once safe to handle, empty the grounds, dismantle and wash the screen, and reassemble correctly. Dishwasher compatibility depends on the particular parts and finish; check the matching manual.

## App-audit appendix

Current code findings; no app changes made.

- **Info — `data/model/BrewMethod.kt:122`:** 1:15, coarse, 93–96 °C, 240 seconds, no bloom/pulses are coherent conventional defaults; label them as a starting recipe. Capacity is unspecified, so add equipment-specific fill/headroom advice rather than inventing a universal capacity.
- **Medium — `data/model/BrewMethod.kt:18` and `domain/BrewCalculator.kt:64`:** inherited decaf policy changes 240 to 210 seconds. These sources do not justify an automatic 30-second decaf shortcut; retain recipe timing unless a particular recipe supports the change.
- **Info — `domain/brewing/session/LegacyStagePlanFactory.kt:35`:** both typed legacy stages are manual. `ui/guidance/LegacyBuiltInGuidanceCatalog.kt:291–306,386–391` already defers to selected timing and warns about forcing. Preserve that behavior. Add headroom, assembly and decant detail to preparation/completion copy at 504–527 and legacy `res/values/strings.xml:119,417–419`.
- **Medium — `domain/BeverageOutputEstimator.kt:38`:** 2.2 g/g apparent loss excludes decant residue; `domain/brewing/BrewingCatalog.kt:257` and the calculator's enum fallback use 2.0. Harmonize estimate semantics and calibration endpoints; neither is a verified universal retention constant.
- **Low — `assets/grinders.json:19,141,165,238,298`:** separate texture from model settings. [Comandante](https://comandantegrinder.com/pages/faq) supports 25–35 clicks, [Encore ESP's manual](https://assets.breville.com/ZCG495/manual-encoreesp-v1-0-en-010923.pdf) supports 32, and [ZP6's chart](https://1zpresso.coffee/grind-setting/) supports 5–6 as starting choices. [Fellow's chart](https://help.fellowproducts.com/hc/en-us/articles/9962302561819-What-are-the-recommended-grind-settings-for-Ode-Gen-2-Brew-Burrs) was visually inspected, but unlabelled arc endpoints do not certify the app's exact 8–10 bounds. Qualify the immersion-fines claim for sediment and screen resistance.
- **Info:** `BuiltInP1RecipeCatalog.kt`, its exact typed plans, `P1ExactGuidanceFactualErrata.kt`, and the August factual audit contain no French press exact recipe; they do not supply a Hoffmann alternative to the current legacy guide.
- **Medium — clock:** `ui/screen/BrewTimerScreen.kt:136` auto-starts this active, no-bloom timer on screen entry. Preparation delay can precede first water contact; tie the clock to the selected brew event while preserving manual completion.

## Evidence limits

Sources were read on 2026-10-02. The [original Hoffmann video](https://www.youtube.com/watch?v=st571DYYTR8) page was reached, but neither the research reader nor browser transcript export supplied a transcript; audio was not reviewed. The variant relies on the original interview. No grinder calibration, beverage yield, additional glass-model compatibility, or physical brew has been validated.
