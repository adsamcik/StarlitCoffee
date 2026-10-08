# Turkish coffee in a cezve

Editorially reviewed, 2026-10-02. English research content; no physical brew test.

Turkish coffee is brewed with exceptionally fine grounds in a small handled pot and served unfiltered. The grounds settle in the cup before drinking. This guide covers a stovetop cezve; electric machines need their own instructions.

The starting recipe below is StarlitCoffee's **app-authored conservative single-rise adaptation**, not an official Mehmet Efendi recipe or a universal traditional optimum. It provides one small serving with a single, observable stopping point.

## Equipment and starting recipe

Use a food-safe cezve with a secure handle, compatible heat source, stable support, and space for expanding foam. Advertised capacity is not permission to fill to the rim; no headroom percentage is verified for every shape. Prepare a scale, serving cup, and wooden spoon.

Measure **6 g coffee and 65 g cold drinking water**: approximately **1:10.83 coffee to input water by mass**. The water mass is app-chosen. Mehmet Efendi specifies 6 g coffee per cup but no water volume. Cups and teaspoons vary; neither establishes a universal mass/volume conversion. Finished beverage mass is unspecified.

Use coffee ground to a fine powder for Turkish brewing, or coffee sold specifically for that purpose. Ordinary espresso-fine grounds are not automatically fine enough. No model-independent grinder setting is supplied. Start unsweetened; optional sugar goes into the pot before heating.

## Steps

1. **Check the pot and burner while cold.** Position the handle within reach without crossing heat. The mixture must leave visible expansion space below the neck or rim; choose a suitable pot before proceeding.
2. **Add the measured cold water, coffee, and any sugar off the heat.** Keep the full 65 g water input in the pot. Do not fill the serving cup with an additional unmeasured amount.
3. **Mix gently until no dry clumps remain.** For this adaptation, stop stirring afterward so you can watch the surface develop undisturbed.
4. **Apply low, controllable heat and stay beside the pot.** Keep gas flame within the base, away from the handle. Watch foam expand upward. Remove at its first controlled rise, before a rolling boil or overflow; do not wait for the rim. Pot material, burner, batch size, and starting temperature affect elapsed time. A stopwatch records your brew; it cannot decide when to stop.
5. **Pour slowly into the stable cup.** Preserve the foam without splashing. Do not return this single-rise recipe to heat, and do not strain it through a paper filter.
6. **Rest the cup undisturbed.** About two minutes is an app-chosen first check, not a settling or safe-temperature guarantee. Wait longer if necessary, then try a small sip. Leave the sediment at the bottom.

## If the cup needs adjustment

- **Foam races upward:** check fill level, stability, and heat control first. Use less heat on the next brew; move the pot away promptly during this one.
- **Little foam:** confirm you have suitable Turkish ground coffee and have avoided vigorous boiling or repeated stirring. Try fresher coffee before extending heating simply to create foam.
- **Gritty mouthfuls:** give the cup longer to settle, avoid disturbing it, and stop before the sediment reaches your mouth. Some suspended solids are normal in an unfiltered drink.
- **Harsh or bitter:** check excessive heat or rolling boiling first. Then try another coffee or change one measured quantity next time. Recheck headroom when the batch changes.

These are practical starting checks, not measured guarantees of taste improvement.

## Other coherent approaches

[Turgay Yildizli's STC guide](https://www.specialtyturkishcoffee.com/how-to-make/stc-preparation/) uses 7 g coffee with 70 g water, preferably around 60 °C, and a grind finer than espresso but slightly coarser than traditional powder. It mixes before heating, reduces heat around midway, targets roughly 120–150 seconds on heat, removes before boiling, then allows 120–180 seconds of settling. Its heating clock starts when the pot goes onto heat; settling is separate. Do not transfer that warm-start timing target to the cold-start adaptation.

[Mehmet Efendi's procedure](https://www.mehmetefendi.com/eng/brew-guide/turkish-coffee/with-cezve) stirs during slow heating, distributes some foam into the cups, then returns the pot to heat before filling them. Its wording says another boil; it does not define a universally optimal rise count or justify prolonged rolling boiling.

Let the pot cool before cleaning. Follow its material-specific care: [Soy's C1 instructions](https://soy.com.tr/products/cezve-turkish-coffee-pots-soy-c1-serve-1) prohibit metal utensils, abrasive sponges, dishwashing, and microwaving, and require contacting Soy about induction use. These restrictions concern that lined copper model. They do not define every cezve's compatibility.

## App-audit appendix

Research accessed 2026-10-02; no physical brew test was performed. The [UNESCO Courier, printed page 16](https://www.unesco.at/fileadmin/user_upload/385026eng.pdf#page=16), independently describes fine grinding, a cold start, slow heating, foam, and small cups. The UNESCO heritage-record page returned a browser challenge; it was not used to verify numerical recipes.

- `BuiltInP1RecipeCatalog.kt:473` and `:491` correctly store 6/65 single-rise and 12/130 two-rise recipes as adaptations, with observation-dependent heating. `P1ExactGuidanceFactualErrata.kt:6` already corrects their displayed provenance. Attribution is resolved.
- `P1CezveAndAutomaticStagePlans.kt:29` and live final stages use approximately 60–120 seconds of settling. Label this an app choice; the verified UNESCO source supplies no interval. Two rises remain a bounded variant, not an authentic optimum.
- `P1BuiltInGuidanceCatalog.kt:509` says finely ground coffee. Expand contextual guidance to explain powder texture and the difference from espresso grind.
- `BrewMethod.kt` and `grinders.json` contain no Turkish everyday-method mapping. Catalog presence alone does not establish that support. Generic cezve defaults already use observation completion and no numerical water temperature.
- `P1ExactRecipeReleaseGate.kt:86` retains `BLOCK-CEZVE-HARDWARE` for both recipes. Specific vessel/headroom/heat-source approval remains unresolved; this research does not clear it.
