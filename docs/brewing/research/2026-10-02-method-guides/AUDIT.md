# Brewing guide accuracy audit

This review supplies guide content and proposed corrections. It does not change
the app. Findings describe baseline `490fc2d4`; the implementation phase must
recheck the affected paths against its current commit. Each method's JSON record
contains the detailed code evidence, source IDs, and uncertainty notes.

## How to use the findings

Prioritize handling instructions and contradictory execution cues, then units
and recipe attribution. Different published recipes are legitimate alternatives.
A default is not incorrect simply because a creator uses another temperature,
grind, or timing. It becomes misleading when attribution, quantities, equipment,
or the instructions actually shown to users disagree.

Keep one coherent beginner recipe per equipment setup. Show the action and
completion cue in the main flow; put the explanation, variants, and evidence in
the guide. Exact numeric values should come from that recipe. Unknown capacity,
yield, or timing belongs in an explicit unknown state rather than a plausible
number.

## Corrections to prioritize

| Area | Current issue | Proposed correction | Detailed review |
| --- | --- | --- | --- |
| Pulsar handling | Shared hot-liquid and finish guidance omits the manufacturer's base-only lifting instruction, although the separate Learn guide already includes it. | Carry the handling instruction into shared safety, completion, and matching illustrations. | [Pulsar](methods/pulsar.md#app-audit-appendix) |
| Pulsar timing | Freshness-adjusted bloom times can be displayed while the durable session still compiles the static 45-second bloom. | Select and persist one effective timing procedure; make the displayed target and session agree. | [Pulsar](methods/pulsar.json) |
| Pulsar capacity | `capacityMaxG=380` drives refill arithmetic from total input, confusing chamber volume with a draining batch. | Model instantaneous fill separately from cumulative recipe water; include coffee displacement and safe headroom. | [Pulsar](methods/pulsar.json) |
| Automatic drip | Exact KBGV Select recipes omit its half/full selector and refer to a nonexistent gram-labelled reservoir mark. | Show the appropriate model control, retain weighed input, and distinguish it from volume marks. | [Automatic drip](methods/automatic-drip.md#app-audit-appendix) |
| Cup-One completion | Exact guidance can use power-off as a brew boundary; current regional versions delay power-off. | Observe delivery ending and basket drainage separately from the switch and from safe cleaning. | [Automatic drip](methods/automatic-drip.json) |
| Chemex provenance | The 42/700 contract differs from Stumptown's full procedure; runtime evidence wording and the subjective "bitter tail" cue need clarification. | Label the app-authored sequence explicitly and provide an observable completion cue. | [Chemex](methods/chemex.md#app-audit-appendix) |
| Chemex output | Profile defaults use a retention coefficient while the method-specific output estimator returns unknown. | Make quantity contracts agree and distinguish a prediction from measured beverage yield. | [Chemex](methods/chemex.json) |
| V60 completion | The legacy plan has a bloom and manual pour without a distinct drainage step; an exact recipe's typical finish range can become a minimum-time constraint. | Make drainage explicit and keep finish references advisory rather than delaying a completed brew. | [V60](methods/v60.json) |
| V60 source units | Rao and Kasuya source sequences specify milliliters, while the exact app records normalize them to grams. | Disclose a weighed-water adaptation or obtain explicit mass evidence. Keep flash-brew water and ice separate. | [V60](methods/v60.md#app-audit-appendix) |
| Clever clock and provenance | Water-first timing can start before coffee is added, and the coffee-first source attribution does not match the directly verified Ozone procedure. | State the extraction clock at coffee addition for water-first; attach reviewed provenance to the correct recipe. Preserve the existing 2:30 release correction. | [Clever](methods/clever.md#app-audit-appendix) |
| Switch steep | A post-fill 120-second countdown is combined with release at 120 seconds from brew start. The manual's approximate volume is also stored as an exact mass. | Use one explicit steep clock and distinguish the manual procedure from a weighed-water adaptation and an advisory total-time window. | [Switch](methods/hario-switch.md#app-audit-appendix) |
| Kalita source drift | The internally consistent historical Ozone 25/400 schedule is absent from the current Ozone page. | Preserve recipe identity, qualify current-source verification, and version a replacement independently. Seek an archived primary snapshot before confirming the older attribution. | [Kalita Wave](methods/kalita-wave.md#app-audit-appendix) |
| Melitta interpretation | Voltage's source has inconsistent bloom and total-time descriptions; the app substitutes observation-based pulses while retaining strong evidence wording. | Identify the interpreted procedure as an adaptation. Keep matching regional hardware and paper explicit rather than adopting inaccurate generic source claims. | [Melitta](methods/melitta.md#app-audit-appendix) |
| Phin source and hardware | Nguyen's recipe mixes units and prints an inconsistent ratio; the screw profile's supplier source describes different locking hardware. | Label the metric adaptation, use the source temperature range, and obtain actual threaded-model evidence before clearing its existing hardware gate. | [Phin](methods/phin.md#app-audit-appendix) |
| Uncalibrated output | Phin's zero retention and automatic drip's zero internal-retention defaults can make predicted output equal input when a target or calibration is missing. | Keep actual output unknown or measured; require a justified protocol-specific prediction model. | [Phin](methods/phin.json), [automatic drip](methods/automatic-drip.json) |
| French press preparation | Shared guidance already protects against forced plunging but lacks explicit headroom, screen assembly and decanting details. Its output estimates also disagree. | Preserve gentle manual operation; add the matching maker's preparation and spout precautions, prompt decanting, and consistent qualified output semantics. | [French press](methods/french-press.md#app-audit-appendix) |
| AeroPress equipment | Generic filters lack size constraints, and XL shares a pressure-actuated accessory profile with Standard despite its different adjustable cap. | Distinguish sizes and cap behavior; require XL's tab to be Open before pressing. Preserve upright, stable, gentle-press guidance. | [AeroPress](methods/aeropress.md#app-audit-appendix) |
| AeroPress decaf | The shared decaf clamps transform 90–150 seconds into 120–150 seconds, increasing the lower target. | Use the selected protocol's explicit steep/press sequence without an unsupported generic timing transformation. | [AeroPress](methods/aeropress.json) |
| Active timer origin | No-bloom active timers start on screen entry, which can precede first water, a sealed steep, pump start, or heat application. | Start at the selected physical event and persist that origin; keep manual completion. | [French press](methods/french-press.json), [AeroPress](methods/aeropress.json), [espresso](methods/espresso.json) |
| Espresso presentation | The calculator correctly uses beverage yield, but preparation calls it Water; timer wording also permits blonding as the stop rule. | Carry beverage-mass semantics into preparation and completion; show temperature in the actual machine's control context. | [Espresso](methods/espresso.md#app-audit-appendix) |
| Moka handling | Legacy timer copy says lid open, conflicting with the reviewed Moka Express manual; generic ratio and time defaults ignore pot geometry. | Use the actual pot's fill landmark, loose basket, lid/valve precautions, and physical stop cue. Preserve existing manual flow and cooldown guidance; the zero temperature sentinel is already hidden. | [Moka pot](methods/moka-pot.md#app-audit-appendix) |
| Cold brew temperature | Defaults display 20–25 °C while existing preparation and safety instructions require refrigeration at 4 °C or colder. | Align temperature presentation with the selected refrigerated procedure; separate steep, filtering, measured concentrate and serving dilution. Do not promise a universal safe storage period. | [Cold brew](methods/cold-brew.md#app-audit-appendix) |
| Siphon integration | No dedicated method exists. The reviewed Technica procedure uses volume input, a heated steep after transfer and stirring, and natural return; burner and filter requirements depend on the model. | Represent volume explicitly, retain the chosen clock convention, and scope fuel, assembly, handling and cloth care to the verified equipment. | [Siphon](methods/siphon.md#app-audit-appendix) |
| Percolator integration | No dedicated method exists. The reviewed electric procedure uses reservoir marks and tablespoons; its ready light begins a keep-warm state. | Support native measures or measured calibration and indicator-based completion. Keep electric and stovetop controls, fill limits and handling separate. | [Percolator](methods/percolator.md#app-audit-appendix) |

## Shared accuracy rules

- **Quantity basis:** reservoir water, brew water, collected beverage,
  concentrate, dilution, and ice are separate. Grams and milliliters may form a
  useful approximate adaptation, but that adaptation must be declared. An exact
  recipe cannot silently inherit a manufacturer volume as a weighed mass.
- **Time basis:** record the clock origin and the event being timed. A steep
  duration, elapsed release point, drawdown window, and appliance shutoff are
  different. A timer target must not override a physical completion cue.
- **Grinders:** publish numbers only for the actual grinder, burrs, calibration,
  filter, and source recipe. Generic click changes and inferred cross-grinder
  conversions should become small, observable adjustments.
- **Decaf and roast age:** the reviewed sources do not establish a universal
  subtraction of 30 seconds or exact freshness offsets for every method. Treat
  these as unsupported heuristics unless independently validated and scoped.
- **Yield:** apparent-loss coefficients are predictions that depend on the
  protocol and endpoint. They cannot establish an exact cup volume or a safe
  batch capacity. Unknown or measured output is preferable where no justified
  model exists.
- **Recipe identity:** do not combine one creator's dose, another's temperature,
  and a third's pulse schedule under a named attribution. Preserve intact recipes
  or label a synthesis app-authored.
- **Model and filter boundaries:** paper, cloth, and metal filters can have
  different preparation and cleaning requirements. Shared family guidance must
  not erase those differences or infer unverified technical specifications.

## Preserve existing corrections

Current runtime errata already correct Clever's water-first release to 2:30 and
Cup-One's unplugging/cooling warnings. Cezve variants already identify their
single-rise or bounded repeated-rise adaptation. Do not report the historical
projection as the only live instruction or rewrite it to erase provenance.

## Before app integration

Some evidence remains specific to an unresolved recipe or component. The
beginner guides qualify these gaps; editorial review does not clear an existing
exact-recipe release gate.

| Evidence follow-up | Boundary to retain |
| --- | --- |
| Exact V60 variants | Kasuya 4:6 and Kurasu flash recipes retain `BLOCK-V60-VARIANT`. Source-unit adaptations and complete variant-specific execution/visual review still need validation in the implementation phase. |
| Threaded phin hardware | The supplier's locking insert is not proof of the catalog's threaded model. Keep `BLOCK-PHIN-PRIMARY-HARDWARE-EVIDENCE` until the correct hardware is verified. |
| Exact cezve hardware | Preserve the existing primary-hardware gate for the exact recipe. A clearly labelled general starting procedure does not certify its specific vessel contract. |
| Historical Kalita recipe | Current Ozone instructions differ from the stored 25/400 sequence. Obtain a primary archived version or keep the older attribution qualified. |
| Pulsar metal-filter specifications | Verified care instructions do not establish the asserted 19K/40K opening counts. The reviewed beginner procedure uses paper. |
| Grinder chart interpretation | Readable charts can still have ambiguous endpoints. Do not certify an exact range or transfer a setting to another burr or model without evidence. |

Corrections must reach each relevant presentation density, accessibility text,
localized copy, and typed execution plan. Preserve stored recipe identity and
method ordinals. New everyday methods also need quantity semantics, equipment
constraints, preparation, timing, persistence, and truthful grinder fallbacks;
adding an enum value alone does not establish full support.

Source review and the pack checker validate evidence structure and arithmetic.
They do not establish physical brewing, translation quality, illustration
correctness, device behavior, or a release. Those remain separate validation
steps in the implementation phase.
