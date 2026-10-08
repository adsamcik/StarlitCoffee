# AeroPress: an upright first cup

Editorially reviewed, 2026-10-02. English research content; no physical brew test.

Use an **Original or Clear, standard
perforated cap, and Standard paper filter**, upright. AeroPress steeps coffee,
then pushes it through a filter. This follows Tim Wendelboe's light-roast recipe
with AeroPress safety and completion cues; other equipment and recipes differ.

## Equipment and starting recipe

Use a burr grinder, gram scale, kettle, timer, stirrer, and a sturdy mug or server
whose opening supports the brewer securely.
Do not press into thin glass. For XL, the maker prohibits **all glass receiving
vessels**, including sturdy ones; use its supplied Tritan carafe or another
suitable non-glass vessel. [AeroPress vessel guidance](https://aeropress.com/pages/faq)

- Coffee: **14 g light roast**, ground fine for filter brewing.
- Extraction water: **200 g, freshly boiling** at your local boiling point.
- Input ratio: **1:14.29**; this compares dry coffee with water poured onto it.
- Steep: **60 seconds after the first stir and plunger insertion**.
- Dilution and ice: none. Collected drink weight and total brew time are not
  specified by the creator. Water retained in grounds prevents a guaranteed
  200 g serving. [Original recipe](https://timwendelboe.no/pages/how-to-brew-with-an-aeropress)

## Brew

1. **Fit the filter.** Briefly rinse a paper disc under the tap, seat it flat in
   the cap, and lock the cap onto the chamber. Discard rinse water. Place the
   chamber upright on the mug and add the coffee. Check that the cap is secure
   and the setup cannot rock.
2. **Add the measured water.** Tare the scale and pour 200 g onto the grounds.
   The scale includes water dripping into the mug; a little is normal.
3. **Stir, then seal.** Stir three times. Insert the plunger seal shallowly,
   about half an inch, to stop draining. A slight lift can help seal it; do not
   pull the seal out. Start a **60-second steep timer now**. This clock excludes
   the preceding pour and stir. [Recipe](https://timwendelboe.no/pages/how-to-brew-with-an-aeropress),
   [maker's seal procedure](https://aeropress.com/pages/how-to-use)
4. **Stir again.** At the end of that minute, carefully remove the plunger and
   stir three times from back to front. Reinsert it without moving the chamber
   off its stable support.
5. **Press gently.** Keep the chamber centered and hands clear of the outlet.
   Apply steady light pressure; do not force it to meet a deadline. Finish as
   liquid flow ends and air passes through with a hiss, without grinding the
   plunger into the puck. Hissing can be faint when little air was trapped.
   The maker's troubleshooting describes a gentle 20–40-second press;
   Wendelboe gives no fixed duration.
   [Pressing guidance](https://help.aeropress.com/en-US/am-i-doing-something-wrong-if-it-is-hard-to-press-123207),
   [hiss explanation](https://help.aeropress.com/en-US/what-is-that-hissing-sound-when-i-press-141582)

Cool the cup before tasting. These troubleshooting adjustments are app-authored
starting trials.

## When something goes wrong

- **It drains before steeping:** check filter seating, locked cap, and prompt
  shallow plunger insertion first. Keep the coffee bed level. If sealing and
  technique are sound but excessive water still escapes, try a slightly finer
  grind next time. A Standard Flow Control cap is a separate option for preventing
  passive dripping. [Maker FAQ](https://aeropress.com/pages/faq)
- **Pressing needs force:** stop increasing pressure. Check the stable support,
  clean chamber and seal, correct filter, and any accessory's flow setting.
  Let gentle pressure work; try a coarser grind on the next brew if needed.
  Fines can obstruct the pressed bed even though this is an immersion brewer.
  [Maker troubleshooting](https://help.aeropress.com/en-US/am-i-doing-something-wrong-if-it-is-hard-to-press-123207)
- **Thin or sharply sour:** verify dose, water, temperature, and the full steep
  first; then try slightly finer grinding. **Harsh or drying:** verify gentle
  pressing, then try slightly coarser grinding. For a dark roast, consider the
  maker's cooler recipe rather than treating this light-roast example as
  mandatory. Change one variable per brew. [Temperature guidance](https://aeropress.com/pages/faq)

## Size, filter, and recipe variants

Original/Clear use Standard filters. Go also uses Standard filters but is
smaller: its advertised 8 US fl oz brewing serving differs from its 15 oz
carrying/drinking mug. Original/Clear are advertised at 10 oz and Original XL at
20 oz. These are volume specifications, not safe chamber-water limits in grams
with coffee inside. Keep each batch within the model's safe fill instructions;
validate any resized recipe. [Go](https://aeropress.com/products/aeropress-go-travel-coffee-press),
[XL](https://aeropress.com/products/aeropress-coffee-maker-xl)

XL requires XL paper or metal filters. The **Standard Flow Control** cap uses a
pressure-actuated valve and fits Original/Clear/Go, not XL. The **XL Variable
Flow Control** cap is a different accessory: use an XL filter and move its tab
to **Open before pressing**. Neither cap replaces the filter.
[Standard cap](https://aeropress.com/products/aeropress-flow-control-filter-cap),
[XL cap](https://aeropress.com/products/aeropress-flow-control-filter-cap-xl)

Correctly sized reusable metal filters allow more oils through; expect a fuller
cup rather than paper's cleaner filtration. They change this paper-filter
recipe's cup profile. [Filter guidance](https://aeropress.com/pages/faq)

The maker's current Original/Clear recipe instead uses 16–18 g, 85 °C water to
mark 4, three seconds of stirring, and a 30-second sealed wait for medium-fine
coffee; its medium pre-ground version waits 60 seconds. Those marks are not
verified gram amounts. [Current instructions](https://aeropress.com/pages/how-to-use)

Concentrate recipes need separate extraction and dilution quantities. For
example, the published 2019 Wendelien van Bunnik competition recipe uses 30 g
coffee and 100 g extraction water, then roughly 60 g concentrate plus water to
taste; the reported 120 g dilution gives roughly 180 g drink, not 220 g. It
uses inversion and is an advanced historical variant, not this beginner
sequence. AeroPress **strongly discourages inversion** because instability can
spill hot liquid and burn the user. [Competition recipe](https://aeropress.com/pages/wac-recipes),
[maker caution](https://help.aeropress.com/en-US/i-have-heard-of-people-using-an-%22inverted-method%22-why-do-they-use-it-123217)

After brewing, safely remove the hot cap, eject grounds and filter over a bin,
and rinse. Keep the seal uncompressed for storage; wash periodically according
to your model's instructions. [Care guidance](https://aeropress.com/products/aeropress-go-travel-coffee-press)

## App-audit appendix

These are recommendations only; app code was not changed.

- **Medium — defaults and clock:** `data/model/BrewMethod.kt:143` currently
  sets 1:15, 80–90 °C, 90–150 s, and 250 g capacity. The ratio is a plausible
  app baseline, not the verified Wendelboe recipe; temperature excludes that
  light-roast boiling-water recipe. `res/values/strings.xml:420` starts a steep
  after adding water/stirring without the seal step or a precise clock origin.
  Store complete named protocols with steep and press kept separate.
- **Medium — equipment specificity:** `domain/brewing/BrewingCatalog.kt:263`
  and `:272` accept the same generic paper/metal IDs and pressure-actuated
  accessory on Standard and XL. The filter IDs lack size constraints;
  they do not establish that physical parts interchange. The validator in
  `domain/brewing/Equipment.kt` checks ID memberships only. The shared accessory's
  pressure-actuated behavior is wrong for the maker's manually adjustable XL
  cap. Specify filter sizes and cap models; XL's adjustable cap requires
  an Open-before-press instruction. Give Go its own capacity configuration.
  `domain/BrewCalculator.kt:77` uses 250 g to compute refills; additional fills
  are a new protocol, not validated linear scaling of an immersion recipe.
- **Medium — decaf:** the inherited `DecafTimeAdjustmentPolicy.STANDARD` in
  `data/model/BrewMethod.kt:18` changes AeroPress's 90–150 s window to
  **120–150 s**, because its minimum clamps dominate the 30-second subtraction.
  `domain/BrewCalculator.kt:63` applies it. Disable generic decaf shortening
  for named protocols; no reviewed source supports this timing transformation.
- **Medium — yield:** `domain/BeverageOutputEstimator.kt:42` uses apparent
  loss 1.8 g/g, while `domain/brewing/BrewingCatalog.kt:266` and `:275` use
  2.0 g/g. Treat both as estimates needing protocol-specific validation, not
  guaranteed yield. Track measured concentrate and dilution separately.
- **Medium / info — guidance coverage:** `domain/brewing/session/LegacyStagePlanFactory.kt:41`
  already separates manual steep and press with critical safety messages.
  `ui/guidance/LegacyBuiltInGuidanceCatalog.kt:308` already specifies upright,
  stable, sturdy support and no forcing. Preserve this; add filter locking,
  leakage control, clock origin, cap-specific actions, and cleanup. AeroPress
  has no exact P1 entry in the inspected recipe/stage catalogs or
  `P1ExactGuidanceFactualErrata.kt`; the August factual audit covers other
  methods and should not be cited as AeroPress recipe verification.
- **Info / medium — grinder evidence:** `assets/grinders.json:226` contains
  Fellow Ode Gen 2 Brew Burrs 3–6 with app-selected 4, and `:274` Encore ESP 22.
  Their manufacturers support the band and point respectively; neither proves
  the correct grind for this recipe. Do not transpose ordinary Encore 11–13
  from the AeroPress instructions to Encore ESP. `viewmodel/BrewDerivation.kt:280`
  gives AeroPress zero baseline decaf grind steps, then adds a dark-roast step
  and subtracts a process-relief step. Its comment about no pressurized bed is
  misleading: AeroPress still pushes liquid through a bed. The reviewed sources
  do not validate those exact decaf process/step offsets.
  [Fellow chart](https://help.fellowproducts.com/hc/en-us/articles/9962302561819-What-are-the-recommended-grind-settings-for-Ode-Gen-2-Brew-Burrs),
  [Baratza manual, printed p. 5](https://assets.breville.com/ZCG495/manual-encoreesp-v1-0-en-010923.pdf)

- **Medium — clock:** `ui/screen/BrewTimerScreen.kt:136` auto-starts this
  active, no-bloom timer on screen entry. It cannot establish the selected recipe's
  after-sealing steep origin. Tie the clock to that physical event while preserving
  manual completion.

## Evidence limits

The linked maker Original/Clear, Go, and XL PDFs had no extractable text and
the Original/Clear PDF did not render in the inspected browser; no numeric
recipe claims rely on them. The readable maker webpage and FAQ disagree in
some quick-start stirring/wait details, so this guide keeps their recipes
separate from Wendelboe's. Press duration, final yield, local boiling-point
temperature, third-party accessories, and physical brewing are unverified.
