# Clever Dripper: a first water-first brew

Editorially reviewed, 2026-10-02. English research content; no physical brew test.

The Clever steeps coffee, then drains through paper when placed on a compatible cup. This guide selects **Crema Coffee Roasters' published 25 g / 400 g water-first recipe** for a **large Clever**: its clock, stirring and drainage form one coherent starting procedure. Do not interchange its release time with a coffee-first recipe. [Crema guide](https://crema-coffee.com/pages/clever-dripper-brewing-guide)

## Equipment and starting quantities

Use a large Clever, **#4 wedge/Melitta-style paper**, grinder, gram scale, timer, kettle, spoon and stable heat-safe server with room for the batch. Testing its fit with cold water is an app-authored precaution. Ozone identifies nominal **300 ml / #2** and **500 ml / #4** versions. These are volume labels, not beverage yields or safe water-mass limits: coffee needs headroom. Check the packaging. [Ozone guide](https://ozonecoffee.co.uk/pages/brew-guide-clever-dripper)

- Coffee: **25 g**, finer than ordinary drip; Crema's wet-sand comparison describes particle size. Use dry grounds.
- Brew water: **400 g**, freshly boiled and poured promptly. Its temperature depends on local boiling conditions; this recipe does not specify a measured slurry temperature.
- Ratio: **1:16 coffee to input water by mass**; no ice or dilution.
- Clock: **0:00 when coffee enters the already-filled brewer**. Release at **2:30**; expect **45–75 seconds** draining, or approximately **3:15–3:45 total**. [Crema guide](https://crema-coffee.com/pages/clever-dripper-brewing-guide)

## Brew, step by step

1. **Fit and rinse the paper.** Fold its seams so it sits neatly. Rinse with hot water, drain that water into the server and discard it. This also warms the equipment. Move the Clever off the server onto its coaster or a level surface, where the bottom actuator remains unpressed. Tare the scale.
2. **Fill with 400 g water.** Add all the freshly boiled brewing water before any coffee. The brewer should retain it. Water lost through an open valve would change the recipe.
3. **Add 25 g coffee and start the clock.** Stir gently until dry pockets disappear. No separate bloom is needed. Empty-brewer filling is outside extraction time.
4. **At 2:00, break the surface crust.** Gently stir the floating layer of grounds back into the liquid. Let it settle for **30 seconds**; avoid vigorous stirring.
5. **At 2:30, place it securely on the server.** Contact opens the bottom valve. Keep the assembly level while coffee drains through the paper.
6. **Remove when drainage completes.** Look for no standing water above the bed and flow diminishing to occasional drips. The time window is a reference, not a reason to interrupt a brew that is still draining. Lift by the handle onto the coaster; let the drink cool before tasting. Collected coffee is less than 400 g because liquid remains in the grounds and brewer; no exact yield is promised. [Crema guide](https://crema-coffee.com/pages/clever-dripper-brewing-guide)

## Troubleshooting, variants and cleanup

If **nothing drains**, first check that the cup rim actually depresses the actuator and the paper is seated correctly. If **drainage is unusually slow**, check equipment before changing coffee: use the matching filter, a compatible server and gentle agitation. Then try a slightly coarser grind next brew. Do not push the valve from underneath with your hand while it contains hot liquid. These mechanical checks are app-authored; the valve and paper-filter arrangement are manufacturer documented. [E.K./HandyBrew](https://www.handybrew.com/)

If **sour or lacking flavour**, confirm quantities and wetting, then grind slightly finer. If **bitter**, try slightly coarser. Change one variable and taste after cooling. Fast drainage alone does not prove poor extraction. [Subtext's troubleshooting](https://www.subtext.coffee/pages/clever-dripper-brew-guide)

For a **small Clever**, use a complete smaller recipe. Ozone publishes **15 g / 250 g, 95 °C, medium filter grind, coffee first**: start timing with the water pour, stir for 15 seconds, cover, release at **2:15**, and expect drainage around **3:00–3:30**. It is a separate recipe, not a scaled Crema brew. Recheck grind and drainage when changing batch size. [Ozone guide](https://ozonecoffee.co.uk/pages/brew-guide-clever-dripper)

Keep the hot brewer stable and avoid overfilling. After cooling, discard paper and grounds, rinse and clean the brewer according to its model's instructions. Check the valve for residue before reuse. Dishwasher compatibility and seal disassembly are not established here.

## App-audit appendix

- **Already corrected — info:** `domain/brewing/session/P1ImmersionStagePlans.kt` uses 120/150/210-second cues for `clever_water_first_15_250`. `ui/guidance/P1ExactGuidanceFactualErrata.kt` corrects historical release to **2:30**, with about 60 seconds drainage. `ui/guidance/P1ExactGuidanceCatalog.kt` loads localized `R.raw.p1_exact_guidance` and applies the overlay. English/Czech raw records retain historical numbers; they do not establish a current defect.
- **Clock origin — medium:** the typed water-first `ADD_WATER` starts at zero; coffee addition has no explicit clock-start instruction. Clarify the extraction clock and verify session event alignment. Corrected release timing alone leaves this ambiguous.
- **Provenance — medium:** `domain/brewing/BuiltInP1RecipeCatalog.kt` cites Coffee Chronicler/Hario for the coffee-first recipe, although its quantities and timing match the directly verified Ozone guide. Add reviewed Ozone provenance without rewriting immutable historical assets.
- **Equipment/output — medium:** `domain/brewing/BrewingCatalog.kt` has one `clever_style` wedge-paper profile and a 2 g/g retention estimate. Setup already accepts a capacity override. Explain size-specific paper and headroom; label output as estimated, not a measured yield. `data/model/BrewMethod.kt` has no Clever everyday enum entry, and `assets/grinders.json` has no Clever-specific settings: exact-catalog presence does not prove complete everyday support.

Code paths are under `app/src/main/java/com/adsamcik/starlitcoffee/`; assets under `app/src/main/`.

## Evidence limits

Sources accessed **2026-10-02**. Hoffmann's [original video](https://www.youtube.com/watch?v=RpOdennxP24) could not be fetched in this pass; its existing correction was checked in code and corroborated by Crema's separate recipe, not independently rewatched. Manufacturer pages exposed no numerical capacity specification; the size figures above come from Ozone. This English research guide has no physical brew, measured yield, device execution or translation-quality validation.
