# Kalita Wave: your first cup

Editorially reviewed, 2026-10-02.

The Wave is a paper-filter pour-over with a flat coffee bed and three drainage
holes. Its pleated paper limits contact with the brewer walls. This guide uses
Ozone's **currently published Wave 155 single-cup recipe**, with three water
additions and an explicit clock. It is a starting point. This English research
draft has not been physically brewed. [Ozone](https://ozonecoffee.co.uk/pages/kalita-wave-brew-guide)

## Equipment and starting recipe

Use a **Wave 155 with Wave 155 paper**, a stable heat-safe mug or server with
room for the drink, a scale, timer, grinder, and controlled-pour kettle. For the
larger 185, buy 185 Wave papers. Cone
papers and Kalita 102/103 wedge papers are different shapes.

Weigh **15 g coffee and 250 g brew water**, at **93 °C**, with a **medium filter
grind**. The input ratio is 1:16.67. Heat extra water for rinsing; that rinse is
not part of the 250 g. The collected drink weighs less than the input because
some water remains in the grounds and paper; no fixed beverage yield is
promised. Use visible small coffee grains as an initial texture cue, not an
invented grinder number. [Ozone recipe](https://ozonecoffee.co.uk/pages/kalita-wave-brew-guide)

## Brew step by step

1. **Set up.** Confirm the brewer sits securely on
   the vessel. Inspect its underside: the three holes must be clear. Put the
   matching paper centrally in the brewer without crushing its pleats. These
   checks and overflow guard are app-authored guidance.
2. **Rinse and preheat.** Wet the paper with hot water, then discard the rinse.
   Handle the hot vessel carefully. Add the coffee and gently level the bed.
   Tare the assembled brewer, coffee, and vessel; do not tare between pours.
3. **At 0:00, add 50 g.** Start the clock when water first touches coffee. Wet
   the entire bed gently and pause until 0:30. This first wetting is the bloom.
4. **At 0:30, add 100 g to reach 150 g total.** Distribute about half over the
   bed in small circles, then pour the other half centrally.
5. **At 1:10, add 100 g to reach 250 g total**, with the same circular then
   central pattern. Keep pouring gentle. Some water may remain above the bed
   between additions. If it approaches the paper's rim, pause for space; do not
   overflow to keep a timestamp.
6. **Wait for drainage.** Remove the brewer when the bed has drained and
   dripping has essentially stopped. Ozone suggests 2:30–3:00 from first wetting;
   record the time, but let the physical cue determine completion. Put the hot
   dripper on a suitable surface before serving. [Ozone recipe](https://ozonecoffee.co.uk/pages/kalita-wave-brew-guide)

## Adjust after checking the setup

If drainage is unexpectedly slow, first confirm the correct paper size, open
pleats, clean holes, stable vessel, and unblocked exit path. During brewing,
pause a pour that threatens overflow; do not press the hot paper down or poke
the outlets. After cooling, inspect the equipment. These checks follow from
the Wave's paper and outlet arrangement, rather than a published fault diagnosis.
[Kalita stainless 185](https://kalitausa.com/products/kalita-wave-185-stainless-steel-coffee-dripper)

With setup consistent, change one variable next time. Try finer for a sour,
thin cup; coarser for a dry, bitter cup or persistent slow drainage. Compare
taste; a longer time alone does not prove a bad brew.
[Ozone adjustments](https://ozonecoffee.co.uk/pages/kalita-wave-brew-guide)

## Sizes, materials, and care

For a Wave **185**, Ozone currently publishes a separate 30 g/500 g example:
100 g at 0:00, then 200 g at 0:30 and 200 g at 1:30, finishing around
3:30–4:00. Recheck headroom and grind when changing batch size. These are input
masses, not capacity specifications. Do not put that batch in a 155.
[Ozone size variants](https://ozonecoffee.co.uk/pages/kalita-wave-brew-guide)

Stainless, glass, and ceramic models share the Wave concept but differ in
construction; the reviewed sources provide no reliable ranking of their
drainage speed. Preheat consistently and tune to your actual model. Kalita's
glass 185 has a polypropylene collar: keep it installed for stable support.
After cooling, discard paper and grounds, wash the dripper, rinse, and dry.
Kalita lists the reviewed stainless 185, glass 185, and MINO ceramic 185 as
dishwasher safe; check your own model's care instructions. Protect glass and
ceramic from knocks and sudden temperature changes.
[Stainless care](https://kalitausa.com/products/kalita-wave-185-stainless-steel-coffee-dripper),
[glass construction and care](https://kalitausa.com/products/kalita-wave-185-glass-dripper),
[MINO ceramic care](https://kalitausa.com/products/kalita-mino-ceramic-dripper-185-sand-beige)

## App-audit appendix

- `BuiltInP1RecipeCatalog.kt:303` and `P1ManualGravityStagePlans.kt:197`
  contain the historical Ozone 25/400 example: 50/110/60/60/60/60 g additions
  at 0/30/45/60/75/105 seconds, reaching 400 g, about 180 seconds total.
  Arithmetic and typed targets agree. Current Ozone no longer publishes it:
  preserve provenance, seek an archived primary snapshot, and version any
  replacement separately.
- Live English `app/src/main/res/raw/p1_exact_guidance.json:1194` still presents
  that recipe as high-confidence expert guidance. The loader uses raw resources;
  `P1ExactGuidanceFactualErrata.kt` has no Wave correction. Explanations mostly
  repeat targets; add setup, clock, headroom, and useful cues in a future app
  change. The August audit's source-match claim is now stale.
- `BuiltinBrewerProfileRecipeDefaults.kt:186` shares 1:16, 93–96 °C and
  150–210 seconds across both sizes. Label these as app defaults, not Ozone's
  current recipes. `BrewingCatalog.kt:220` uses one `wave_paper` ID for both
  sizes and a 2.0 retention estimate; expose size compatibility and keep output
  explicitly estimated. `BrewMethod.kt` has no Kalita enum entry;
  `grinders.json` has no Wave-specific settings. Catalog presence alone does
  not establish full everyday-method or localization support.

## Remaining evidence limits

The previous Ozone schedule's primary page was not recovered. No
comparative material-flow measurements, universal capacity, exact grinder
calibration, or fixed retention factor was verified. The retailer-hosted Wave
instruction PDF was readable but authorship was not established;
it is not used for this baseline. No app execution or translation review was
performed.
