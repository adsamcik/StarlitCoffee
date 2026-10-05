# Vietnamese phin brewing guide

Editorially reviewed, 2026-10-02. English research content; no physical brew test.

A phin makes concentrated coffee by draining hot water through grounds and
perforated metal parts. This guide covers **Nguyen Coffee
Supply's single-serving 4 oz gravity phin**, whose loose disc rests on the
grounds. Other sizes and insert designs need their own procedure.

## Equipment and starting recipe

Use the complete phin, a stable heat-safe cup supporting its plate, a kettle,
a coffee scale, a graduated measure, a timer, and a grinder or phin-ground
coffee. No paper filter is required. The lid can hold the brewer after use.
[Nguyen's hardware guide](https://nguyencoffeesupply.com/pages/master-phin-filter-faq)
describes the parts.

Start with **14 g coffee and 118 ml total brew water at 91–93 °C**. Grind coffee
fine like sand. The initial pour is **30 ml**; the remaining
pour is **88 ml**, giving **118 ml cumulative**. Start timing when water first
touches coffee. Look for a first drip by about **2:00** and completion around
**5:00**; let a still-draining brewer finish.

These metric quantities are an **app-authored adaptation** of
[Nguyen's traditional recipe](https://nguyencoffeesupply.com/blogs/vietnamese-coffee-brew-guide/traditional-vietnamese-drip-phin):
we interpret its water ounces as US fluid ounces and round its 4 oz total and
1 oz initial pour to milliliters. Its 195–200 °F range is rounded to 91–93 °C.
The result uses 8.43 ml water per gram of coffee. Retention means the finished
drink differs from input; no fixed yield is promised. Serving additions are separate.

## Brew it

1. **Check the dry brewer.** Ensure the holes are clean, fit the plate and chamber
   on the cup, and verify that nothing rocks. Start with dry parts after washing;
   Nguyen recommends dry parts when diagnosing blocked flow.
2. **Add and level the coffee.** Weigh 14 g into the chamber and shake gently to
   level it. Set the gravity disc flat on the bed without compressing it. This
   follows the traditional guide's insert-before-water order.
3. **Wet the bed.** Start the timer with first water contact, pour 30 ml gently,
   and wait until it reads 0:45. The coffee should absorb water and swell. This water
   belongs to the 118 ml extraction total.
4. **Add the remaining water and cover.** At 0:45, add 88 ml gently. Keep the
   level below the rim, letting it fall if needed to finish the measured pour.
   Cover and leave the brewer stable.
5. **Watch the flow.** First drip and completion are different observations.
   Look for a drop reaching the cup by roughly 2:00, then let it drain.
   Around 5:00 is a size-specific reference. Finish when standing water has
   drained and dripping ends.
6. **Remove and serve.** Protect your hand from the hot metal and place the
   phin on its inverted lid or a heat-safe tray. Taste the coffee.
   Drink hot, add water to taste, or stir in condensed milk before pouring over
   ice. Serving additions do not change the extraction-water total.

## If the result needs adjustment

Check clean holes, assembly, a flat disc and dose before changing grind.
For a stalled gravity phin, Nguyen describes briefly lifting
and releasing the disc with a utensil to vent trapped gas. Only attempt this
with stable support and hands clear of hot water; otherwise
let it cool. Do not push down to force flow. Persistent slow flow suggests a
slightly coarser grind next time; a very fast, weak brew suggests slightly finer
grounds after confirming the dose.
[Nguyen's dialing guide](https://nguyencoffeesupply.com/blogs/news/dialing-in-your-phin-how-to-make-the-best-vietnamese-phin-coffee?_pos=1&_psq=dialin&_ss=e&_v=1.0)
supports these checks. If drainage is reasonable but flavor
is harsh or sour, change one variable; time alone cannot
diagnose extraction. Dilute a strong pleasant cup before changing the recipe.

## Variants and care

A threaded screw disc and a slot-lock disc need their own instructions;
do not apply the gravity disc's lift-and-release action to them. Larger phins
need a size-specific recipe. Condensed milk and ice are optional.

Let the parts cool, discard the grounds, wash with soap and a soft sponge,
rinse, and dry. Avoid abrasive pads and steel wool.
[Nguyen's care guide](https://nguyencoffeesupply.com/blogs/news/using-and-maintaining-a-vietnamese-phin-filter?_pos=2&_psq=phin+filter&_ss=e&_v=1.0)
permits a dishwasher for its phins; that permission is not universal.
[Nam Coffee's 8 oz aluminum phin](https://www.nam.coffee/products/phin-filter-black)
recommends hand washing.

## App-audit appendix

- **Medium:** `BuiltInP1RecipeCatalog.kt:572` and `session/P1PhinStagePlans.kt:14`
  store the gravity example as 118 g, including a 30 g bloom, and 91–96 °C.
  The source uses volume and 195–200 °F. Preserve volume or label a mass
  adaptation; correct attribution and temperature.
- **Medium:** `P1ExactGuidanceCatalog.kt:403` applies runtime errata, but
  `P1ExactGuidanceFactualErrata.kt` has no phin correction. The July projection's
  mass-based creator attribution still reaches live guidance.
  Add a reviewed runtime correction without rewriting historical provenance.
- **Medium:** `BuiltinBrewerProfileRecipeDefaults.kt:261`, `BrewingCatalog.kt:389`
  and `Recipe.kt:242` use zero retention to estimate concentrate as input.
  Use measured output or an unresolved estimate instead of promising that yield.
- **Medium:** `BuiltInP1RecipeCatalog.kt:591` calls the screw 18 g/120 g profile
  “battle-tested.” Its [Dragon Coffee source](https://trung-nguyen-coffee.co.uk/page_brewing.php)
  is a supplier's slot-lock, spoon-dose, fill-to-level procedure. It does not
  verify threaded hardware or those numbers. Label the adaptation and seek
  model-specific primary evidence before asserting validation.
  `P1ExactRecipeReleaseGate.kt:88` already retains
  `BLOCK-PHIN-PRIMARY-HARDWARE-EVIDENCE`; this research does not clear it.
- **Info:** Phin has generic profile and exact-catalog guidance, but no everyday
  `BrewMethod` enum entry or dedicated `grinders.json` target. Catalog presence
  does not establish full everyday-method or localization support.

## Unresolved evidence

Nguyen's “1:2” beside 14 g/4 oz has no common-unit definition. Its care article
blooms before inserting the disc; its traditional guide inserts first.
The timing origin is unstated; this guide starts at initial wetting.
A Trung Nguyên manufacturer PDF's recipe page could not be visually verified.
Exact screw-recipe evidence remains unresolved. This is an English research
draft, without physical brewing or localization validation.
