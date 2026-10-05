# Siphon: Hario Technica with a cloth filter

Editorially reviewed, 2026-10-02. English research content; no physical brew test.

A siphon lifts heated water into coffee, then filters the drink back as the
lower chamber cools. This guide covers the **Hario Technica TCAR-3, supplied
alcohol lamp, and F-103S assembly with FS-103 cloth**, following Hario's
coffee-first procedure. It is English research content, without a physical test.

## Equipment and starting recipe

Have the brewer, stand, lamp, snuffer, suitable liquid fuel alcohol, grinder,
scale, timer, measuring jug, Hario spoon or bamboo paddle, dry heat protection,
and cups. Follow the matching lamp manual and actual fuel-container instructions,
including ventilation requirements.

Use **30 g medium-ground coffee and 360 mL water**: our selected three-cup
instance of Hario's approximate 10 g/120 mL standard, or **12 mL per gram**,
without assuming a mass ratio. Our editorial clock convention is **60 seconds
starting immediately after the initial gentle stir following completed water
transfer**. This selects Hario's 40–60-second range; the manual gives the
sequence without defining stopwatch zero. This starting recipe is an
app-authored adaptation of Hario's procedure. Heating/return have no fixed deadline
or numerical slurry-temperature target. [Hario manual, pp. 7–9](https://www.manualslib.com/manual/4267319/Hario-Coffee-Syphon.html)

TCAR-3 practical capacity is 360 mL; 440 mL brimful capacity is not a brew target.
The “3” mark means three 120 mL water portions. Yield varies; recheck model,
marks and recipe when changing batch size. [Hario specifications](https://shop.hariocorp.co.jp/products/tca-2)

## Brew step by step

1. **Prepare.** Inspect glass/gasket, secure the stand fastener, and use a level,
   dry, heat-safe surface away from combustibles. Rinse the brewer; dip new cloth
   in boiling water. Center the filter, draw its chain through the upper stem,
   and hook it to the stem end. Rest the upper chamber in its support.
   [Hario manual, pp. 3, 7, 10](https://www.manualslib.com/manual/4267319/Hario-Coffee-Syphon.html)

2. **Add coffee before transfer.** Put 30 g grounds above the filter. Fill the
   lower chamber to its three-cup mark with 360 mL water; dry the outside. Hot
   water can shorten heating. [Hario manual, p. 7](https://www.manualslib.com/manual/4267319/Hario-Coffee-Syphon.html)

3. **Heat and transfer.** Set the wick while unlit: about 3 mm exposed. Center
   the lamp under the bowl, then light it; keep the flame under 4 cm.
   Rest the upper chamber angled until water boils; gently seat it upright.
   Watch water rise, with a little remaining below. Keep this phase separate
   from the steep clock. [Hario manual, p. 7](https://www.manualslib.com/manual/4267319/Hario-Coffee-Syphon.html), [Stumptown transfer cue](https://www.stumptowncoffee.com/pages/brew-guide-vacuum-pot)

4. **Steep.** After completed transfer, gently stir to wet the grounds; start
   our 60-second clock immediately when that stir ends. Avoid striking glass.
   Keep heat centered; avoid overheating the nearly empty lower bowl.
   [Hario manual, pp. 4–5, 8](https://www.manualslib.com/manual/4267319/Hario-Coffee-Syphon.html)

5. **Remove heat and let coffee return.** At 60 seconds, use dry heat protection
   to move the stand clear of the stationary lamp; snuff it and verify extinction.
   Observe return separately. After liquid returns and flow stops, hold the stand
   and gently rock the upper chamber free into its support. Serve carefully.
   [Hario manual, p. 8](https://www.manualslib.com/manual/4267319/Hario-Coffee-Syphon.html)

## Adjustments, variants and care

Our app-authored troubleshooting starts with equipment: stop heat and cool
before checking gasket, lamp and filter for poor transfer/premature return.
For slow return, confirm heat removal and filter cleanliness before trying a
coarser grind next brew. Never force hot glass apart. For harsh/thin taste during
normal operation, change grind one small step, holding other variables fixed.

**Cloth:** wash after use, brush gently, and refrigerate submerged in fresh water,
changing water regularly. Replace worn cloth. [Hario cloth care](https://www.hario-canada.ca/products/hario-syphon-cloth-filter-5-pk)

**Paper:** F-103MN supports TCAR-3 but excludes TCAR-5. Use matching paper and
supplied assembly instructions; installation/rinsing were not verified here.
Discard spent paper; cloth storage does not apply.
[Hario holder compatibility](https://shop.hariocorp.co.jp/products/f-103mn)

**Metal:** the NXA/SCA assembly is not a Technica substitute. Fit its marked mesh
face upward in the silicone ring; disassemble, brush gently and dry for storage.
Avoid bending mesh or touching sharp edges. [Hario NEXT manual, p. 4](https://www.manualslib.com/manual/4390380/Hario-Next-Nxar-5.html?page=4#manual)

**Other recipes:** Stumptown's separate 40 g/five-mark recipe adds coffee after
transfer and starts timing then: stir at 40 seconds, heat off at 90. Its shown
butane burner requires its own manual.
[Stumptown original recipe](https://www.stumptowncoffee.com/pages/brew-guide-vacuum-pot)

Never heat dry/damaged glass or move/refill a lit lamp. Prevent thermal shock:
cool glass before wet contact. Wash cooled glass with mild detergent and a
nonabrasive sponge. [Hario care](https://www.manualslib.com/manual/2836953/Hario-Coffee-Syphon-Tca-50a.html)

## App-audit appendix

- **Info — absent support:** `data/model/BrewMethod.kt:64`,
  `domain/brewing/BrewingCatalog.kt:57` and `BuiltInP1RecipeCatalog.kt:220` contain
  no siphon enum/family/profile/exact recipe. A later approved implementation
  needs these plus localization/routing.
- **Medium — units:** `Recipe.kt:59` and `Equipment.kt` need explicit water
  volume, practical volume capacity and volume-per-mass ratios. Keep output
  measured, without invented retention.
- **Medium — stages/equipment:** `session/StagePlan.kt` supports `HEAT`,
  `OBSERVE`, stage durations and observed completion. Reuse these; add volume
  targets and mount/filter/seal/heat-removal states. `Equipment.kt` has filter
  media but lacks a burner/fuel compatibility contract.
- **Info — reviewed scope:** `grinders.json`, the August factual audit and
  `P1ExactGuidanceFactualErrata.kt` have no siphon entry. Use texture guidance;
  do not borrow numeric grinder settings.

## Evidence limits

The official [TCAR PDF](https://global.hario.com/product/TCAR.pdf) returned 403;
Hario-authored pages/diagrams 7–8 were inspected through ManualsLib. The older
TCA/50A manual supports care. Prefer the specific Japanese paper-holder exclusion
over broader regional advertising. Temperature, return duration, paper
preparation, fuel-label ventilation, physical brewing, app execution and
translations remain unvalidated.
