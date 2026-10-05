# Brewing guides and accuracy review

This research pack covers 17 brewing methods, including Chemex. Each method has
its own beginner guide and structured evidence record. The guides select a
repeatable starting procedure, explain what to watch for, and show how to make
one adjustment at a time. A recipe can suit a particular brewer and coffee;
there is no single best recipe for every setup.

Research date: **2 October 2026**. Repository baseline: `490fc2d4`.

The next design phase translates this research into a
[shared visual guide workflow](../../design/2026-10-02-visual-guides/DESIGN.md).
Its [interactive prototype](../../design/2026-10-02-visual-guides/prototype.html)
demonstrates Chemex, espresso, and cold brew in live and self-paced use. See the
[browser review](../../design/2026-10-02-visual-guides/REVIEW.md) for completed checks
and the remaining native and physical validation.

## Find a guide

| Method | App coverage at baseline | Guide | Evidence |
| --- | --- | --- | --- |
| Chemex | Everyday method and catalog | [Chemex](methods/chemex.md) | [Record](methods/chemex.json) |
| Automatic drip | Catalog and recipe guidance | [Automatic drip](methods/automatic-drip.md) | [Record](methods/automatic-drip.json) |
| NextLevel Pulsar | Everyday method and catalog | [Pulsar](methods/pulsar.md) | [Record](methods/pulsar.json) |
| Hario V60 | Everyday method and catalog | [V60](methods/v60.md) | [Record](methods/v60.json) |
| Clever Dripper | Catalog and recipe guidance | [Clever](methods/clever.md) | [Record](methods/clever.json) |
| Hario Switch | Catalog and recipe guidance | [Switch](methods/hario-switch.md) | [Record](methods/hario-switch.json) |
| Kalita Wave | Catalog and recipe guidance | [Kalita Wave](methods/kalita-wave.md) | [Record](methods/kalita-wave.json) |
| Melitta style pour-over | Catalog and recipe guidance | [Melitta](methods/melitta.md) | [Record](methods/melitta.json) |
| Turkish coffee | Catalog and recipe guidance | [Turkish coffee](methods/turkish.md) | [Record](methods/turkish.json) |
| Vietnamese phin | Catalog and recipe guidance | [Phin](methods/phin.md) | [Record](methods/phin.json) |
| French press | Everyday method and catalog | [French press](methods/french-press.md) | [Record](methods/french-press.json) |
| AeroPress | Everyday method and catalog | [AeroPress](methods/aeropress.md) | [Record](methods/aeropress.json) |
| Espresso | Everyday method and catalog | [Espresso](methods/espresso.md) | [Record](methods/espresso.json) |
| Moka pot | Everyday method and catalog | [Moka pot](methods/moka-pot.md) | [Record](methods/moka-pot.json) |
| Cold brew | Everyday method and catalog | [Cold brew](methods/cold-brew.md) | [Record](methods/cold-brew.json) |
| Siphon | Absent | [Siphon](methods/siphon.md) | [Record](methods/siphon.json) |
| Percolator | Absent | [Percolator](methods/percolator.md) | [Record](methods/percolator.json) |

Catalog presence means internal brewer profiles or recipe guidance exist. It
does not establish support throughout the everyday calculator, preparation,
timer, persistence, grinder recommendations, or translated interface. This
pack supplies research and guide content; app behavior remains at the baseline.

## Read the numbers correctly

Each guide specifies the equipment and the basis of its quantities. Water
added to a manual brewer, water put in a machine reservoir, and coffee collected
in a cup are different quantities. Espresso recipes usually specify beverage
mass. A moka pot filled according to its manual is governed by its geometry.
Concentrate, dilution water, and ice must be tracked separately.

Timers also need an origin and an endpoint. A bloom starts with the first water
touching the coffee unless the selected recipe says otherwise. A steep timer
does not include every preparation step. Draining, foam rising, flow ending,
and a machine switching off are distinct events. Follow the guide's completion
cue as well as its reference time.

## Evidence and review boundaries

The records distinguish manufacturer procedures, intact published recipes,
and app-authored starting recipes. Sources include actual manuals, the original
recipe creator, and roasters publishing their own procedures. Each record notes
what a source supports, its access status, uncertainties, and claims to avoid.
Instructions from one model must not silently become rules for another model.

The app-audit appendix in each guide describes current code and recommended
corrections separately from the beginner procedure. Historical source assets
remain provenance records; corrections belong in the reviewed runtime guidance
and its translations.

These are English editorial guides. No recipe in this pack has been physically
brewed as part of this work. Source review and arithmetic checks do not establish
device execution, localization quality, measured beverage yield, or taste.

## Validate the pack

Run from the repository root:

```text
python tools/check_brewing_method_guides.py --require-reviewed
```

The checker verifies all 17 deliverables, required metadata, primary-source
references, ratio bases, pour totals, cumulative additions, time ordering,
temperature ranges, and existing audit paths. Use `--allow-incomplete` only
while research files are still being delivered. The checker cannot prove that a
source supports a claim; editorial review must read the actual source.

See [the assignment brief](RESEARCH_BRIEF.md) for the evidence schema and scope,
[the review findings](AUDIT.md) for the implementation priorities, and
[the validation record](VALIDATION.md) for completed checks and their limits.
