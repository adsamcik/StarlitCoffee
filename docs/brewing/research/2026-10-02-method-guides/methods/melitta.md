# Melitta style wedge pour-over

Editorially reviewed, 2026-10-02.

This guide covers the current **Melitta European plastic 1x4 dripper with two outlets and internal ribs**, using matching wedge paper. Water drains through coffee and paper into a server. The paper has bottom and side seams, rather than a V60 paper’s pointed shape.

The [European model](https://www.melitta-international.com/accessories/Manual-Coffee-Preparation/Coffeefilters-pots/Coffee-Filter-1x4) has two outlets; [Japanese Hasami models](https://www.melitta.co.jp/enjoyment/how_to/brewing_with_hasamicoffeefilter/) have one. Check your actual brewer before applying a recipe.

## Equipment and starting recipe

Use the named dripper, matching paper, a stable heat-safe server with room for the batch, a kettle, scale, timer, and grinder or freshly ground coffee. A gooseneck kettle is optional.

| Item | Starting point |
| --- | --- |
| Coffee | 20 g |
| Brew water | 320 g, weighed separately from rinse water |
| Ratio | 1:16 coffee to input water by mass |
| Temperature | 94 °C at the kettle; a starting choice |
| Grind | Medium, with visible small granules rather than powder |
| Additions | 40 g, then 140 g, then 140 g |
| Scale readings | 40 g, 180 g, 320 g |
| Clock | First water touching coffee to completed drainage |

This **app-authored baseline** uses maker setup instructions and [Sweet Maria’s filtercone guidance](https://library.sweetmarias.com/wp-content/uploads/2020/08/Drip-Brewing-Pourover-Filtercone-Tip-Sheet.pdf). Dose, split pours and bloom are selected starting choices. No universal finish time or beverage yield is verified. Grounds retain water, so the drink weighs less than the input.

## Brew step by step

1. **Check the setup.** Place server and dripper securely on the scale. Confirm clean, unobstructed outlets and a clear server opening. Leave room below the outlets; rising coffee must not submerge them. This checks hardware before grind.
2. **Fit and rinse the paper.** Fold the bottom and side seams, open the paper, and seat it against the wedge walls without crumpling. Rinse with hot water, then discard it. [Melitta’s filter FAQ](https://www.melitta.co.uk/faq/filter-papers) explains that correct size and folded seams help prevent collapse or tearing.
3. **Add the coffee.** Weigh in 20 g and level gently. Tare the scale. Subsequent readings count brew water, including bloom; never tare between pours.
4. **Wet the bed.** Start the timer at first water contact. Pour 40 g gently over all coffee, wetting any dry patches within this allowance. Wait until 0:30 from first contact, including pouring time. Visible foaming is not required.
5. **Pour to 180 g.** Add 140 g with a low, gentle stream over coffee. Avoid bare paper above the bed, where water can bypass coffee. Pause to keep liquid below the paper edge and rim. Wait until standing water has largely drained.
6. **Pour to 320 g and drain.** Add the remaining 140 g similarly. At 320 g, stop adding water. When no standing pool remains and the stream becomes occasional drips, lift the dripper carefully onto a heat-safe dish. Mix the server and serve. Record the observed time for comparison next time; do not force a deadline.

## If the result needs adjustment

For slow drainage, check paper size and seating, outlets, server clearance, and rim flooding. Then try slightly coarser grinding next time. For fast, weak coffee, check complete wetting and pouring onto coffee; then try slightly finer grinding. These are starting diagnostics, not guarantees.

With sound setup, try finer grinding for unpleasant sharpness, or coarser grinding or lower temperature for bitter, drying taste. Change one variable per brew; coffee and roast also matter. Grinder numbers are not transferable.

## Variants, handling and cleanup

The [US 1-Cup plastic cone, SKU 64007](https://shoponline.melitta.com/products/1-cup-pour-over-coffee-brew-cone-black), accepts Melitta #2 or #4 wedge papers. A taller paper does not increase supported liquid capacity. Other regional sizes need their specified paper. Preheat ceramic models thoroughly and follow their care instructions.

Keep hot vessels stable and hands clear of draining coffee. Let grounds cool before discarding paper. Wash the dripper and server, clear both outlets, rinse, and dry. The selected European plastic dripper is dishwasher safe; the US SKU specifies the top rack. Recheck grind, pour sizes and headroom when changing batch size.

## App-audit appendix

- `BrewMethod.kt` has no standalone Melitta method. `BrewingCatalog.kt` correctly distinguishes `manual_wedge_generic` and wedge paper; catalog presence does not establish a complete everyday flow. `grinders.json` has no Melitta-specific entry.
- `BuiltInP1RecipeCatalog.kt:322` stores Voltage’s 23.5/400 example and ratio 17.02 correctly. Its “Battle-tested” evidence label exceeds the evidence checked here.
- [Voltage’s current recipe](https://voltagecoffee.com/melitta/) still adds 50 + 50 + 100 + 100 + 100 = 400 g. Its stage durations total 215 seconds (3:35), while prose says 3:30 and the card says 4:00; separate bloom prose says 30 seconds while instructions say 40. These are unresolved source inconsistencies, not proof the site changed.
- `P1ManualGravityStagePlans.kt:270` and live `res/raw/p1_exact_guidance.json` retain the 40-second bloom and correct cumulative targets, but replace published pulse durations with observation cues and a 210–240-second finish range. Label this interpretation as an adaptation. `P1ExactGuidanceFactualErrata.kt` currently has no Melitta correction.
- `BuiltinBrewerProfileRecipeDefaults.kt` gives the generic wedge the shared 150–210-second window and 2 g/g retention estimate. Neither is a universal hardware fact. The 2026-08-19 factual audit’s unqualified timing match needs a dated follow-up, preserving historical provenance.

## Evidence limits

Sources were read on 2026-10-02. Outlet diameter, maximum slurry capacity, a universal finishing time, and repeatable beverage yield remain unverified. Voltage’s peripheral claims about available sizes, immersion and fines passing paper are not adopted. This English research guide has not been physically brewed, device-tested, or reviewed as a translation.
