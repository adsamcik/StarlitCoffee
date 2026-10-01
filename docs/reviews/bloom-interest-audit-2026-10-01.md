# Bloom visual-interest reassessment — 2026-10-01

The user’s feedback is supported: the revised animations are more consistent, but several finishes became less interesting. The prior review placed too much weight on intact anatomy, stable attachment, distinct images and a readable growth order. Those checks did not consistently protect the original composition’s richness or establish a compelling sequence.

Astra compared all 44 current mature compositions at commit `1c8d3e604f4bdb046b08cc9d3a2a10804b5ee793` with the verified pre-revision originals. Three reviewers independently assessed the tradeoff. The five coffee sequences were additionally reviewed across all 25 poses; the botanical audit inspected the final five poses. Original/current atlas hashes were verified against both the committed resources and the archived original review.

## Findings

Three issues need different remedies:

- **Composition:** some finals lost flower count, secondary buds, asymmetric branches, fine foliage or layered petals. Enlarging a simplified design would retain this loss.
- **Prominence:** some hero flowers became substantially smaller relative to their bean and canvas. This reduced the visual payoff even when their broad identity survived.
- **Choreography:** some sequences spend many poses expanding an established motif. Brew and Starlit remain comparable or larger in footprint, yet their repeated steam/loop shapes still lack development.

Conservative padding explains some size reduction, but it does not explain the largest regressions. Queen of the Night’s occupied final height fell from 226 to 145 source pixels; its current maximum across all 25 poses is only 148 pixels, below the importer’s roughly 208-pixel available height. Bleeding Heart’s final height fell from 245 to 171 pixels and its maximum is 177. These designs also lost supporting anatomy.

Alpha footprint and frame uniqueness are supporting measurements. They cannot establish beauty, narrative interest or successful motion. Small quiet beats can be useful, but several consecutive poses need visible development when viewed at the actual 148px display size.

## Priorities

| Animation | Lost character or weak sequence | Restoration direction |
| --- | --- | --- |
| Bleeding Heart | Two abundant, unequal sprays became a short three-heart arch; divided foliage became broad simple leaves. | Recover both sprays, varied pendant sizes and finely divided foliage; stagger flower groups. |
| Jade Vine | Dense overlapping cascade and twisting woody support became a short regular ladder. | Restore the long irregular raceme, numerous hooked flowers and tendrils; open tiers at different stages. |
| Forget-me-not | An airy branching flower-and-bud cloud became five large front-facing disks. | Retain fine branching, smaller unequal flowers, varied orientations and supporting buds. |
| Queen of the Night | The commanding off-centre starburst shrank greatly and its companion bud disappeared. | Recover the dramatic bloom-to-bean scale, long layered rays, tall cactus segments and separate waiting bud. |
| Snowdrop | Three staggered nodding flowers became two balanced bells. | Restore the third bell, unequal heights and slender crossing stems. |
| Chocolate Cosmos | Four unequal flowers and a bud became three tidy heads on broader foliage. | Recover the fourth head, retained bud, fine foliage and different opening stages. |
| Pincushion Firework | Long fine irregular pins became shorter, thicker, regularly spaced fingers. | Restore filament length, varied curves and airy gaps, with purposeful outward development. |
| Dahlia Kaleidoscope | Dense nested petal spirals became fewer broad tiers around an exposed disk. | Restore narrower staggered tiers, deep overlap and a smaller recessed centre. |
| Blue Himalayan Poppy | The large drooping flower became less prominent and its edge more regular. | Preserve its generous flower-to-bean relationship and delicate crinkled edge; assess framing separately from anatomy. |
| Crema Chrysanthemum | Dense curled petals became fewer uniform thick loops. | Restore varied curl sizes and tighter inner tiers around the coffee-brown centre. |
| Coffee Brew | Many late poses repeat an established heavy steam emblem. | Give successive poses a clear aromatic rise, unfolding filaments and resolving glints. |
| Starlit Coffee | Heavy uniform loops share Brew’s visual grammar for too much of the sequence. | Preserve layered luminous strands and unequal loop tension, with earlier distinctive stellar development. |

Successful changes should carry forward. Fuchsia and the irises now have repaired intact surfaces; the earlier missing alpha was not useful texture. Protea and Morning Glory also retain worthwhile repairs. The Latte-art heart is an approved concept change, and Coffee Plant’s fruit development is a useful payoff. The recent berry follow-ups restore much of their lost abundance. This audit does not call for another uniform redesign of all 44.

## Revised review standard

1. **Establish the mature composition first.** Compare it with the strongest original at actual 148px in both themes and at equal occupied size. Preserve or deliberately improve the distinctive silhouette, abundance, asymmetry, supporting motifs and plant-to-bean proportions.
2. **Lock its anatomy and relative proportions.** Identify flower, fruit and bud sites; branch curves; petal tiers; tendrils; and secondary details. Do not delete those elements to make the growth sequence easier.
3. **Author growth backward from that finish.** Establish the eventual attachments as smaller closed forms, unfold existing surfaces and stagger their maturation. Retained secondary buds may remain buds when they make the final composition stronger.
4. **Plan each late pose’s purpose.** Final petals release, another spray opens, a pendant extends, the last fruit ripens or luminous detail resolves. A changed contour alone does not demonstrate valuable development.
5. **Review interest and continuity separately.** Require both a strong ending and coherent transitions. Keep natural differences in height, orientation, overlap and developmental stage while retaining identity through time.
6. **Retain rendering repairs.** Restore intended richness with complete painted anatomy; do not preserve clipped or missing old surfaces as texture. Review native rendering after the artwork passes both design gates.

This direction uses the strongest original compositions as the minimum target while retaining successful repairs and approved redesigns. An optional preference between preserving those compositions and creating more expressive new finishes was requested; the diagnosis and review standard remain useful for either direction.

## Evidence and limits

The [canonical audit](bloom-interest-audit-2026-10-01.json) retains the three Astra reports, all 44 footprint records, source provenance and exact evidence hashes. The earlier [44-animation revision](bloom-animation-revision-2026-10-01.md) remains an archived checkpoint; its passing continuity checks do not supersede these interest regressions.

The [botanical comparison](../../.qa-screens/bloom-interest-audit-2026-10-01/botanical/comparison-1-light.png) makes the Bleeding Heart and Jade Vine simplification visible. The [second comparison](../../.qa-screens/bloom-interest-audit-2026-10-01/botanical/comparison-2-light.png) shows Queen of the Night and Chocolate Cosmos. Fixed148px and equal-occupied-size contacts for all 44 are retained under `.qa-screens/bloom-interest-audit-2026-10-01/signature` and `core`.

This pass is a visual reassessment and authoring brief. It changes no application artwork, renderer or pipeline and does not constitute a fresh all44 full-transition review, Android playback test or release validation.
