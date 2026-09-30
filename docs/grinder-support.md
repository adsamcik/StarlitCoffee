# Supported grinder recommendations

Reviewed against primary manufacturer documentation and an authored Pulsar recipe
on 2026-09-30. These are starting recommendations, not guarantees of extraction or
physical tasting tests.

The Brew picker offers a grinder only when the selected method and filter have
directly published guidance. Every entry identifies its hardware, actual setting
notation, adjustment increment, and sources. Published single starting points
stay single points; the app does not invent a surrounding range. Where a source
publishes a band, the app selects a practical start within that band.

| Exact grinder | Supported methods and published guidance | Readout and adjustment |
| --- | --- | --- |
| 1Zpresso ZP6 Special | Pulsar paper: 5–6; V60: 4–5; French press: 5–6 | Number plus clicks, e.g. `5.2`; 90 clicks per rotation, 10 per number; recommended adjustment 2 clicks |
| Comandante C40, standard axle | Espresso: 7–13; Moka: 14–20; V60: 18–35; French press: 25–35 | Clicks from zero; 1-click adjustments. Red Clix is excluded. |
| Fellow Ode Gen 2, Gen 2 Brew Burrs | Pulsar paper: `5.2`; AeroPress: 3–6; V60: 4–8; French press: 8–10; cold brew: 9–11 | Printed number and subdivision; `5.2` means 5 plus two clicks, followed by 6. Three clicks per numbered interval, 31 positions from 1 to 11. SSP and other burrs are excluded. |
| Baratza Encore ESP, original model | Espresso: 15; AeroPress: 22; V60: 25; French press: 32 | Integer positions from 1 to 40; 1-click adjustments. The original Encore and ESP Pro are excluded. |
| Niche Zero | Espresso: 10–20, starting at 15; V60: 35–50, starting at 40 | Stepless numbered dial. Suggested adjustment is one dial mark, not a click. |

## Sources and applicability

- **ZP6:** [1Zpresso grind-setting guide](https://1zpresso.coffee/grind-setting/)
  and [external-adjustment manual](https://1zpresso.coffee/manual-k-en/) establish
  the scale and pour-over/French press bands. The pour-over band supplies V60's
  starting guidance; it is not a bean-specific V60 recipe.
- **C40:** [Comandante FAQ](https://comandantegrinder.com/pages/faq) publishes the
  standard-axle method bands. The
  [C40 manual](https://cdn.shopify.com/s/files/1/0875/2057/5818/files/C40-Manual-E-01-23-web.pdf?v=1744881570)
  explains counting clicks from zero.
- **Ode:** [Fellow's Gen 2 Brew Burrs guide](https://help.fellowproducts.com/hc/en-us/articles/9962302561819-What-are-the-recommended-grind-settings-for-Ode-Gen-2-Brew-Burrs)
  supplies the method bands. The
  [Gen 2 product specification](https://fellowproducts.com/products/ode-brew-grinder-gen-2)
  identifies 31 positions and the intended brew-grinder hardware.
- **Pulsar paper:** [Kaldi's authored 20 g / 320 g recipe](https://kaldiscoffee.com/blogs/recipes/nextlevel-pulsar-dripper-brew-recipe)
  publishes ZP6 5–6 and Ode Gen 2 `5.2`. These are paper-filter starting anchors,
  not independently verified settings for every Pulsar recipe, including the
  separate 20 g / 340 g learning guide. Other recipes still require adjustment
  for taste and flow.
- **Encore ESP:** [Baratza's ESP manual](https://assets.breville.com/ZCG495/manual-encoreesp-v1-0-en-010923.pdf)
  publishes four single starting settings. The fine adjustment region is 1–20;
  the coarser region is 21–40. The previous V60 15 / espresso 4 entries did not
  follow this model's manual.
- **Niche:** [Niche Zero dial-in guidance](https://www.nichecoffee.co.uk/blogs/exploring-coffee/how-to-dial-in-your-niche-zero)
  publishes espresso and V60 guidance; its
  [calibration article](https://www.nichecoffee.co.uk/blogs/exploring-coffee/how-to-calibrate-your-grinder-for-optimum-performance)
  explains why dial reference matters. Half marks remain continuous dial values.

## Unsupported combinations and existing data

DF64 was removed because the old entry did not specify its generation or burrs.
Pulsar metal-filter predictions and the remaining uncited method entries were
removed. A grinder's mechanical ability to make coffee does not establish a
verified numeric recommendation for every method.

When no compatible recommendation exists, preparation shows the method's generic
texture guidance and the Brew screen hides the grinder picker. A retired or
incompatible selection resolves to None for the active setup. Existing favorites,
brew logs, and manually recorded bag settings are retained; historical values are
not rewritten or guessed.

## Implementation and verification

The version 4 asset stores source URLs per grinder and recommendation. Loading
rejects duplicate or dangling records, missing sources, illegal positions, invalid
adjustment increments, and ambiguous filter entries. Metadata uses physical linear
positions: Ode's printed `5.2` is stored as `5 + 2/3`. One shared formatter renders
preparation, summaries, favorite/log values, and bag setting updates in native
notation.

Decaf adjustments and the existing uncertain-calibration range widening snap to
real positions and respect physical limits. A published single point is never
expanded into an invented range. Adjustment increments express usable physical
steps; they are app tuning suggestions, not claimed manufacturer taste guarantees.

Catalog, formatter, derivation, and session-snapshot unit tests check the data and
saved notation. Android UI tests check compatible choices, hidden unsupported
controls, whole-setup restoration, and preparation readouts. Hardware calibration
and cup quality still depend on the user's grinder, beans, and recipe.
