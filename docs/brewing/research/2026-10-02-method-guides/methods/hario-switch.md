# Hario Switch: a first steep-and-release brew

Editorially reviewed, 2026-10-02. English research content; no physical brew test.

The glass Switch holds water above a valve, then drains coffee through paper. This guide uses Hario's **Switch 02 manual procedure**, with one fill and one release. The steep is immersion; drainage takes liquid through the bed. Keeping the valve open makes a gravity pour-over; alternating retained and flowing phases makes a hybrid. These are distinct procedures. [Hario product description](https://www.hario-usa.com/products/switch-immersion-dripper)

## Equipment and starting recipe

Use a glass Switch 02, Hario V60 **02 cone paper**, a stable server, kettle, coffee scale, timer and heat-safe measuring vessel. Wash before first use. Start with **20 g medium-ground coffee, approximately 240 mL hot water and about two minutes of steeping**. The manual supplies no numeric temperature or total-time target. 240 mL is not an exact 240 g prescription. [Hario SSD-200 manual](https://www.hario.cc/Items/manual_pdf/SSD.pdf)

For repeatability, this guide starts the steep timer **when the fill is complete**, following the manual's pour-then-steep order. This clock convention is an explicit editorial clarification. Record total time separately from first water until drainage ends. The example uses 12 mL water per gram of coffee, not a mass-based 1:12 ratio. Beverage mass remains unknown.

## Brew step by step

1. **Seat and rinse the paper.** Fold the seam and fit the cone. Rinse with hot water while the valve is open; discard the drained rinse. This wets the paper and warms the server. Rinse water is additional to recipe water. [Hario V60 intermediate guide](https://www.hario.co.uk/pages/brew-guides-v60-intermediate)
2. **Close the valve and add coffee.** Raise the lever: **up means closed**. Add the measured coffee.
3. **Fill gently.** Wet the grounds with the recipe water, keeping liquid below the paper's top. Leave the brewer on its server; do not chase a brim-full appearance.
4. **Steep, then release.** Start the steep timer after filling. At approximately two minutes, press gently until the lever is flat with the base: **flat/down means open**. It stays open without holding it.
5. **Observe drainage.** Finish when standing water disappears and continuous flow diminishes to occasional drops. Leave the hot brewer in place until safe to handle, then remove carefully and serve. [Switch manual](https://www.hario.cc/Items/manual_pdf/SSD.pdf)

## If the result needs adjustment

If coffee leaks while closed or will not drain when open, first check lever position, paper seating, the stainless-steel ball and assembly. Inspect after cooling. A missing ball prevents retention; an incorrectly seated glass bowl can leak. Do not force the lever or poke a hot valve. [Switch manual](https://www.hario.cc/Items/manual_pdf/SSD.pdf)

After those checks, unusually slow drainage is a reason to try a slightly coarser grind, keeping the rest unchanged. Taste adjustments are app-authored suggestions: a sharp, thin result may improve with a little more steep time or a finer grind; a harsh, drying result may improve with less steep time or a coarser grind. Change one variable and compare. Taste alone does not prove under- or over-extraction.

## Other sizes and recipes

The 02 rating is **200 mL finished capacity**; the larger 03 is listed at **360 mL capacity**. Neither supplies a universal brim-fill allowance or an exact output prediction. Match 03 glass with **V60 03 paper** and recheck larger batches rather than doubling this closed fill. [Hario size specifications](https://www.hario-usa.com/products/switch-immersion-dripper), [included paper sizes](https://globalhario.com/products/hario-immersion-dripper-switchglass)

Bøen's distinct hybrid uses 16.5 g coffee, medium-fine grind and 96°C water: closed 50 g bloom from first water to 0:40; open and add 100 g centrally; at 1:30 close and add 90 g in circles; open at 2:10; expected finish 3:00–3:15. The additions total 240 g, although the source heading says 240 mL. [Bøen via Hario Europe](https://www.hario-europe.com/blogs/hario-community/ole-kristian-boens-switch-recipe)

For the open-switch variant, keep the valve open and follow the separate V60 15 g/250 g, 92–96°C procedure; its approximately 30-second bloom precedes a gentle circular pour. [Hario V60 procedure](https://www.hario.co.uk/pages/brew-guides-v60-intermediate)

## Care

Let everything cool. Clean with neutral detergent and a soft, nonabrasive sponge; protect the loose ball from loss. Wear gloves when separating glass and base, following the manual diagram; reseat fully and check lever movement. Avoid damaged glass, metal-spoon scraping and sudden cooling of hot glass. The manual allows dishwasher cleaning according to the dishwasher's instructions. [Switch manual](https://www.hario.cc/Items/manual_pdf/SSD.pdf)

## App-audit appendix

- **Medium — catalog/projection:** `BuiltInP1RecipeCatalog.kt:416` and `assets/p1_exact_guidance_2026_07_27.json:2983` present the manual's volume as exact 240 g and add a 150–180-second total window. Label a weighed adaptation separately; retain unspecified temperature and unresolved output.
- **Medium — typed timing:** `session/P1ImmersionStagePlans.kt:79` puts a 120-second steep countdown after filling but release at brew-elapsed 120 seconds. Use one stated clock and approximate steep duration. Preserve observation-based drainage.
- **Info — verified variants:** the Bøen and open-switch catalog, typed stages and projected instructions preserve their distinct valve sequences. Preserve immutable provenance; add current Hario attribution at the live guidance boundary. `P1ExactGuidanceFactualErrata.kt` currently has no Switch correction.
- **Low — shared copy:** `ui/guidance/P1BuiltInGuidanceCatalog.kt:369` says close/open without physical positions. Add up/flat cues, gentle pressing and cooled assembly checks.
- **Info — support boundary:** Switch is absent from `BrewMethod.kt` and `grinders.json`; generic profile defaults use 1:16 and an estimated retention model. Catalog presence does not establish everyday-method, grinder or localization support.

## Unresolved evidence

Hario.com's manual link returned 403; Hario.cc's original was readable. Kurasu's historical URL and the large 2025 catalog were inaccessible. [Hario UK's September 2026 article](https://www.hario.co.uk/blogs/news/how-to-brew-with-the-hario-v60-switch) publishes a separate 15 g/250 g recipe but mixes clock descriptions and broad capacity/extraction claims; it does not replace this baseline. No physical brew, device or translation validation is claimed.
