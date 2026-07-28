# Brewing evidence ledger

Last reviewed: 2026-07-27

This ledger records the evidence boundary for brewing content. It distinguishes
mechanical specifications, manufacturer recipes, professional guidance, product
defaults, and deliberately limited inferences. It does not copy manuals into the
application or turn a named-product recipe into a universal rule.

## Current implementation status

- The shared profile, equipment, ordered-stage, durable-session, and guidance
  foundations are under release validation. Evidence in this ledger still
  limits what individual profiles may claim or prefill.
- P1 curriculum records identify their planned instructional assets as
  `NOT_PRODUCED`; P1 profiles are intentionally release-gated rather than shown
  with incomplete visual teaching.
- A generated or listed asset is not evidence of completion. A profile remains
  gated until its local optimized asset, manifest entry, alternative text, and
  physical accuracy review have all been recorded.
- Locale-key coverage for UI text does not by itself constitute review of every
  instructional translation or culturally specific preparation claim.

## How to use this ledger

- A record supports only the stated profile, claim, and use in the product.
- A manufacturer recipe is a labelled starting point, not an objectively
  correct result for every coffee, grinder, water, or user.
- A product specification may inform compatibility and capacity validation; it
  must not be silently applied to a generic profile.
- “Observed” stages describe a user-visible event, such as first drip or
  drawdown. They are not promises that every setup reaches that event on a
  fixed schedule.
- When no supporting source exists, use neutral, reversible guidance or omit
  the claim. Do not invent numerical grinder clicks, mesh ratings, basket
  ranges, pressure settings, safety claims, or sensory outcomes.

## Confidence labels

| Label | Meaning | Product treatment |
|---|---|---|
| High | Current official product documentation, manual, or recognized public-safety guidance | Ship for the stated product/scope after copy review |
| Medium | Credible manufacturer education, professional education, or a source-specific recipe | Label as a starting point; do not generalize |
| Limited | Narrow vendor recipe, incomplete documentation, or cautious inference | Optional contextual hint only; no default calculation or safety guarantee |
| Unverified | No adequate source captured | Do not expose as factual guidance or a preset |

## Reviewed records

| Profile ID / scope | Supported claim | Source | Source type | Confidence | Affects | Shipping boundary / open question |
|---|---|---|---|---|---|---|
| “pulsar_standard” | The NextLevel product page states a 380 ml liquid volume and identifies 20 g coffee / 340 g water as its optimal batch. It also describes a valve for retaining water during bloom, a dispersion cap, and no-bypass use. | [NextLevel Pulsar product page](https://nextlevelbrewer.com/shop/nextlevel-pulsar-brewer/) | Official product page | High | Capacity validation, stage wording, optional manufacturer recipe | Show the ratio as “NextLevel starting recipe” for Pulsar only. Do not apply its capacity, valve stages, or no-bypass behavior to other drippers. |
| “pulsar_paper” | The product includes a pack of branded filters. | [NextLevel Pulsar product page](https://nextlevelbrewer.com/shop/nextlevel-pulsar-brewer/) | Official product page | High | Named filter compatibility | The source supports use of the supplied branded filters, but not an equivalence claim for other filters. |
| “pulsar_19k_metal”, “pulsar_40k_metal” | No source captured for the identifier meanings, mesh/micron ratings, material specification, or intended recipe differences. | No primary source captured | Missing primary evidence | Unverified | Filter picker, filter-specific copy, grind guidance | Release blocker: do not present these IDs as product facts or give filter-specific recipes until primary documentation verifies their names and behavior. |
| “v60_02” | Hario’s expert V60 guide specifies the 02 dripper, 15 g coffee, 250 ml water, 92–96 °C water, and a bloom/pour recipe. Hario catalog material also documents a 30-second wetting stage. | [Hario UK Expert V60 Brew Guide](https://www.hario.co.uk/pages/brew-guides-v60-expert), [Hario coffee catalog](https://hario.cc/PDF/pdf2020Eng/Coffee.pdf) | Official manufacturer guide and catalog | High | Optional recipe, stage content, localised temperature units | Scope is the described 02 setup. Treat it as a Hario starting recipe, not a default for 01, 03, Switch, or non-Hario cone brewers. |
| “v60_unspecified”, “v60_01”, “v60_03”, “manual_conical_generic”, “manual_wave_155”, “manual_wave_185”, “manual_wedge_generic”, “manual_thick_paper_carafe” | The captured Hario material does not establish a single capacity, filter, dose, timing, or pour pattern for these distinct profiles. | [Hario UK Expert V60 Brew Guide](https://www.hario.co.uk/pages/brew-guides-v60-expert) (02 only) | Negative scope finding | Unverified | Defaults, compatibility, capacity, guidance | Release blocker for exact presets: keep user-entered recipes or clearly labelled brand/model presets. Do not inherit the 02 recipe, dimensions, or filter compatibility. |
| “french_press_generic” | Bodum’s French-press guidance uses coarse-ground coffee and a four-minute brew in its Chambord-oriented preparation. | [Bodum preparation guide](https://www.mynewsdesk.com/bodum/blog_posts/how-to-easily-brew-the-perfect-cup-of-coffee-75146) | Manufacturer education | Medium | Optional stage content and starting recipe | The source is a product-family recipe, not proof of a universal press capacity, filter geometry, or output-retention value. Keep all values editable. |
| “aeropress_standard”, “aeropress_xl” | AeroPress advises against inverted brewing because it is less stable and can expose users to hot-liquid burns. Its FAQ documents that standard and XL paper filters are not interchangeable, and that the Flow Control cap has model-specific compatibility. | [AeroPress FAQ](https://aeropress.com/pages/faq), [AeroPress inverted-method safety notice](https://help.aeropress.com/en-US/i-have-heard-of-people-using-an-%22inverted-method%22-why-do-they-use-it-123217) | Official manufacturer safety and compatibility guidance | High | Default orientation, safety copy, filter/accessory compatibility | Default to the normal orientation. Any inverted method must be an explicitly chosen advanced variation with a safety warning; do not show it as the recommended core flow. Confirm the exact accessory-to-profile matrix before enforcing it in a picker. |
| “espresso_pump_generic”, “espresso_lever_generic”, “espresso_portable_generic” | La Marzocco defines espresso ratio as dry coffee dose relative to liquid yield and presents 18 g in / 36 g out (1:2) as a starting example for its described setup. | [La Marzocco: Using Espresso Brew Ratios](https://home.lamarzoccousa.com/using-espresso-brew-ratios/) | Professional manufacturer education | Medium | Calculation semantics, recipe labels | The app may distinguish dose from beverage yield and offer a 1:2 example. It must not infer a generic basket capacity, pressurization, pressure, temperature, or shot-time target from this source. |
| “moka_generic_unspecified” | Bialetti instructs Moka Express users to fill water just below the safety valve, loosely fill rather than tamp coffee, and use low-to-medium heat. | [Bialetti: How to use the Moka Express](https://bialetti-cookware.zendesk.com/hc/en-us/articles/5416235346322-How-to-use-the-Moka-Express) | Official manufacturer instructions | High | Safety copy and setup stages | Scope is Moka Express-style hardware. Use observed flow/sputter completion cues instead of a universal brew timer, capacity, or coffee dose for generic moka pots. |
| “cold_immersion_generic” | Toddy’s own Cold Brew System guide specifies an 8–24 hour room-temperature steep for that system, then refrigeration of the resulting concentrate; its storage guidance is product-specific. FDA guidance supports keeping refrigerated food at or below 4 °C / 40 °F. | [Toddy Cold Brew System guide](https://toddycafe.com/cold-brew/instruction-manual), [FDA refrigerator guidance](https://www.fda.gov/food/buy-store-serve-safe-food/refrigerator-thermometers-cold-facts-about-food-safety) | Official manufacturer guide and public-safety guidance | High for stated sources; Medium for generic adaptation | Long-duration timer, storage safety copy | Do not promise a universal shelf life, concentrate ratio, or safe room-temperature duration for every vessel/recipe. Show a brand/system-specific recipe only when selected; otherwise use neutral refrigeration guidance. |
| “clever_style” | A Clever-style device is a steep-and-release category, but no current primary Clever manual was captured for the precise release mechanism, paper size, capacity, or timing. | No primary source captured | Missing primary evidence | Unverified | Equipment copy, release action, filter compatibility | Release blocker for exact guidance. Do not equate its interaction with Hario Switch or present a generic “open valve” instruction without a selected product profile. |
| “hario_switch” | Hario describes the Switch as a hybrid V60/immersion brewer: use a standard 02 or 03 V60 filter and flip the switch to begin drawdown. A Hario recipe separately demonstrates a close/open valve stage. | [Hario V60 Switch product page](https://www.hario-usa.com/products/switch-immersion-dripper), [Hario V60 Switch recipe](https://www.hario-usa.com/blogs/recipes-and-more-from-friends/v60-switch-recipe-with-partners-coffee) | Official manufacturer product page and recipe | High | Release-stage copy, filter compatibility, optional recipe | Keep 02 and 03 hardware distinct if capacity/filter geometry is surfaced. A selected recipe may use valve stages; do not apply its timing to other immersion drippers. |
| “valve_release_generic” | No single source establishes the physical release action, filter geometry, capacity, or thermal behavior of all valve-release brewers. | Product-specific Hario evidence only | Negative scope finding | Unverified | Defaults and stage copy | Do not use “flip the switch” or a fixed drawdown plan for this generic profile. Require a selected named profile or use editable, neutral stages. |
| “cezve_generic” | No regional, primary preparation source has been reviewed sufficiently to support one authoritative cultural recipe, timing sequence, foam instruction, or heat level for all cezves/ibriks. | No approved primary procedural source captured | Missing and culturally sensitive evidence | Unverified | Learn content, recipe/stage defaults, localisation | Release blocker for prescriptive cultural copy. Permit user-authored recipes and functional hot-metal/open-flame warnings only until a regionally reviewed source and localisation review are completed. |
| “automatic_batch_generic”, “automatic_single_cup_generic” | Moccamaster manuals show that automatic brewer setup, filter size, volume controls, heat exposure, and automatic shut-off are model-specific. | [Moccamaster KBGV Select manual](https://support.moccamaster.com/hc/en-us/article_attachments/1500014339002) | Official manufacturer manual | High | Setup checklist, completion state, safety copy | Only guide user-controlled preparation and visible completion. Do not simulate machine-internal stages, universal timings, filter sizes, heat-plate behavior, or auto-off for generic machines. |
| “vietnamese_phin” | Nguyen Coffee Supply presents a phin recipe with first- and last-drip observations, while explicitly allowing users to vary water/coffee ratio to preference. | [Nguyen Coffee Supply phin guide](https://nguyencoffeesupply.com/blogs/vietnamese-coffee-brew-guide/traditional-vietnamese-drip-phin) | Specialist vendor guide | Limited | Observed-stage language and optional recipe | Use first/last drip as observable troubleshooting cues, not guaranteed timer events. Do not generalize its screw-insert geometry, dose, or regional serving choices to every phin. |

## Content rules derived from the evidence

1. Show exact values only alongside the named product/source or a saved
   user recipe. Label externally sourced values as a starting point.
2. Preserve separate calculation semantics:
   - espresso uses dry dose and liquid yield;
   - brewed-filter recipes may use brew water and an estimated retained-water
     model;
   - moka, cold concentrate, and unfiltered preparation must not pretend that
     their output is a direct filter-brew calculation.
3. Use stage labels that state the action or observable outcome:
   “close the valve,” “begin drawdown,” “first drip observed,” and “coffee
   begins to flow” are preferable to unexplained technical controls.
4. Never attach a grinder-setting number to a profile unless its grinder,
   source, and applicable recipe are also known. A qualitative direction is
   still not a safety guarantee.
5. Attach safety warnings to the relevant moment in the workflow, not as a
   generic disclaimer: unstable AeroPress orientation, Moka safety valve and
   hot metal, hot glass on the Switch, and refrigerated storage for cold brew.

## Open release blockers

| Blocker | Affected content | Required resolution before factual UI copy/preset release |
|---|---|---|
| Pulsar metal-filter nomenclature | “pulsar_19k_metal”, “pulsar_40k_metal” | Capture primary NextLevel documentation for names, material/opening specification, compatibility, and any recipe distinction; otherwise remove or keep internal/unselectable. |
| Generic manual-dripper assumptions | All generic/manual gravity profiles | Obtain per-product filter/capacity sources, or ship only an editable generic recipe without precise filter, capacity, or timing assertions. |
| Generic espresso basket assumptions | All generic espresso profiles | Require an equipment/basket profile or user-entered dose before offering a numerical default. No unsupported basket range, pressure, temperature, or target time. |
| Cezve cultural and regional copy | “cezve_generic” | Obtain an appropriate primary/regional source and have the localised copy reviewed by a knowledgeable editor. Do not create a single “authentic” universal recipe. |
| Clever-style release behavior | “clever_style” | Verify product/manual details; keep it distinct from Switch and do not use a Switch-specific physical instruction. |
| Phin geometry and timing | “vietnamese_phin” | Verify a selected phin’s press/insert geometry if exposed. Keep vendor timing as optional, observed guidance rather than a contractual timer. |
| Cold-brew storage duration | “cold_immersion_generic” | Tie any duration to the documented system/recipe and present refrigeration as a safety requirement, not an app-calculated expiry date. |
| Automatic-machine internals | Automatic generic profiles | Keep app stages to setup, user start, and observed completion; collect model-specific documentation before claiming heater, flow, or auto-off behavior. |

## Review protocol

- Re-check linked product pages when their corresponding profile changes and
  before a release that introduces a new preset, safety message, or filter
  claim.
- Record the source version/access date and localise units in a way that
  preserves the source’s scope.
- A content reviewer must reject a claim when the evidence is only for a
  different geometry, capacity, accessory, or regional tradition.
- If a source disappears or materially changes, downgrade the record to
  **Unverified** until a replacement is reviewed.
