# Moka pot brewing guide

Editorially reviewed, 2026-10-02. English research content; no physical brew test.

A moka pot pushes heated water through grounds into an upper chamber. This
procedure covers a **classic aluminum Bialetti Moka Express**, such as the
3-cup size. It is an app-authored adaptation of Bialetti's cold fill, using
illy's first-gurgle stop cue. Brikka and electric moka need their own manuals.
Follow the manual supplied with your pot. [Bialetti manual][manual]
[illy procedure][illy]

## Equipment and starting procedure

Use the complete pot, compatible gas/electric/ceramic hob, moka-ground coffee
or grinder, cold drinking water, spoon, cup, heat protection, and trivet. A scale
can record your pot's quantities.

**Water:** fill the lower chamber just below the safety valve's lower edge.
**Coffee:** completely fill the basket loosely and level it; never tamp.
**Grind:** coarser than machine espresso, finer than ordinary drip; granulated
sugar is a rough texture comparison. **Heat:** low gas flame or controlled
low-to-medium hob setting. These are equipment-fill quantities, not a 1:10 mass
ratio. Grams, milliliters, and beverage yield remain unspecified until measured
for the actual pot. Its advertised “cups” are small espresso servings.
[Bialetti use][use] [Blue Bottle][blue]

Time from placing the assembled pot on active heat until removing it at the
completion cue. Bialetti's broad 3–6-minute reference depends on size and heat;
this guide assigns no deadline. Reservoir fill is not beverage yield. [Bialetti use][use]
[Valve guidance][valve]

## Brew step by step

1. **Check the cold pot.** Confirm that the gasket, filter plate, funnel, and
   valve are present, clean, intact, and correctly seated. Do not use damaged
   parts. For a brand-new pot, follow its first-use instructions: Bialetti's
   cleaning page specifies a water-filled commissioning cycle followed by at
   least three coffee brews to discard. Never heat an empty, dry pot.
   [Manual][manual] [First-use and care][clean]
2. **Fill with cold water.** Stop just below the valve; leave it uncovered.
   Insert the funnel. If water rises into the coffee basket, empty the excess.
   The valve landmark overrides a guessed volume or calculator target.
   [Valve guidance][valve] [Blue Bottle][blue]
3. **Fill the basket gently.** Use moka-ground coffee, level without compressing,
   and remove grounds from the sealing rim. A clean rim helps the gasket seal.
   Do not substitute powder-fine espresso grounds. [Bialetti use][use]
   [illy grind guidance][illy-faq]
4. **Assemble before heating.** Tighten the upper and lower metal sections
   securely without excessive force; do not use the handle as a tightening
   lever. Close the lid and orient the safety valve away from yourself and
   others, as this model's manual requires. [Manual][manual]
5. **Heat and stay nearby.** Keep the pot stable and the handle away from heat;
   gas flames must stay under the base. Use controlled heat, never maximum.
   Listen for coffee delivery. Remove from heat at the first gurgling change;
   do not leave the coffee boiling or chase the last reservoir water.
   [Bialetti use][use] [illy procedure][illy]
6. **Let discharge settle, then serve.** Keep hands and face clear of steam.
   Once active flow settles, lift the lid using its knob from a safe position:
   coffee should be collected in the upper chamber. Stir gently, then pour
   using the handle and heat protection. Leave the pot on a trivet until
   completely cool before unscrewing it. [Manual][manual] [Blue Bottle][blue]

For another model whose manual permits viewing the outlet, pale, bubbly or
sputtering flow is an additional conservative stopping cue. That is app-authored
visual guidance, not permission to override a closed-lid requirement.

## Troubleshooting and care

**No coffee, leaking joint, or valve venting:** turn off heat and allow complete
cooling. Check fill level, filter passages, gasket, rim cleanliness, and assembly
before changing grind. Never unscrew a hot pot, block a venting valve, or increase
heat to force a blockage. Check that grounds are loose and not too fine; if valve
venting persists, stop using the pot and contact its maker. [Manual][manual]
[Valve guidance][valve]

**Burnt or harsh:** first reduce heat and stop promptly at the gurgle; then, if
needed, try slightly coarser grounds. **Thin or sharp:** verify a complete loose
basket and sound seal; then try a small finer adjustment within moka texture.
These taste adjustments are app-authored starting suggestions, not guaranteed
diagnoses. Change one variable at a time. [Comandante FAQ][comandante]

After cooling, discard grounds without banging the funnel. For this aluminum
pot, rinse parts with warm water, use no detergent or abrasives, and keep it out
of the dishwasher. Dry completely and store unclosed; periodically inspect the
filter and maintain the valve according to its manual. [Bialetti care][clean]

## Variants and evidence limits

Blue Bottle's separate 6-cup example uses about 20–22 g coffee and approximately
345 g reservoir water, with optional hot water and medium heat. Its fill level
must still respect the actual valve. Hot filling requires safe handling of an
already hot base and changes warm-up timing; it is not the cold baseline above.
Do not import its Cafiza suggestion into aluminum care without confirmed product
compatibility. [Blue Bottle][blue]

Stainless steel is not automatically dishwasher-safe: a mirrored Venus manual
permits it but recommends hand washing, while Bialetti Philippines' current
Venus page forbids it. Use the supplied model/edition instructions; hand washing
with water is the conservative interim choice. [Venus manual][venus-manual]
[Venus current page][venus-page]

This guide has not been physically brewed. Basket mass, safe reservoir volume,
yield, and timing require the actual model and measurement. The Bialetti manuals
landing page could not be fetched; its original manual hosted by Amazon was read.

## App-audit appendix

Repository baseline: `490fc2d4`.

- **Medium — `data/model/BrewMethod.kt:189`:** generic 1:10, 1:8–1:12 warnings,
  `FINE`, and 240–300 s remain. Recommend pot-fill quantities, a distinct moka
  texture description, and observational timing. `ui/screen/GrindPrepScreen.kt`
  and `ui/screen/BrewTimerScreen.kt` display positive time targets. Their
  temperature guards hide 0/0; no displayed 0 °C bug was found.
- **Medium — `ui/screen/BrewTimerScreen.kt:136`:** Moka inherits `ACTIVE_TIMER`;
  its no-bloom timer auto-starts at screen entry, potentially before heat.
  Start any reference clock at heat application and retain physical completion.
- **High — `app/src/main/res/values/strings.xml:426`:**
  `instruction_moka_start` says lid open; the Moka Express manual says closed.
  `prep_tip_moka` also says “to the valve”; make “below its lower edge” explicit.
  `BrewTimerScreen.methodTimerGuidanceRes` consumes these legacy resources.
- **Preserve — `domain/brewing/session/LegacyStagePlanFactory.kt:70` and
  `ui/guidance/LegacyBuiltInGuidanceCatalog.kt:336`:** manual flow completion,
  below-valve fill, gentle heat, loose basket, and cooldown safety already exist.
  Align lid and model-specific cleaning guidance without replacing these
  safeguards. The P1 exact catalog/errata and August factual audit contain no
  Moka exact recipe correction.
- **Preserve/qualify — `domain/BeverageOutputEstimator.kt:53`:** correctly returns
  no generic Moka output model. `domain/BrewCalculator.kt` still contains the
  legacy ratio/loss calculation; do not expose its result as measured yield.
  `app/src/main/assets/grinders.json:187` correctly records Comandante's
  14–20-click stovetop range; 17 is an app-selected midpoint, restricted to the
  stated C40 standard-axle/zero convention. No transferable grinder number or
  fixed decaf timing shortcut was verified. [Comandante FAQ][comandante]

[manual]: https://m.media-amazon.com/images/I/913VOwvygKL.pdf
[use]: https://bialetti-cookware.zendesk.com/hc/en-us/articles/5416235346322-How-to-use-the-Moka-Express
[valve]: https://www.bialetti.com/us_en/learn-and-brew/post/the-moka-valve-a-tiny-component-of-remarkable-importance
[clean]: https://www.bialetti.com/it_en/inspiration/post/how-to-clean-the-coffee-pot-at-home-natural-and-effective-remedies
[illy]: https://illy.com.au/pages/what-is-a-moka-pot
[illy-faq]: https://illy.com.au/pages/faqs
[blue]: https://cdn.bluebottlecoffee.com/us/eng/brew-guides/moka-pot
[comandante]: https://comandantegrinder.com/pages/faq
[venus-manual]: https://manuals.plus/m/add151ed4a29e1de3f3161b2b1ea38229e9326e5ec6b4555975b169f29c946fe_optim.pdf
[venus-page]: https://bialetti.ph/product/venus/
