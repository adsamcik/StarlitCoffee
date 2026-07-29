# P1 instructional illustration research brief

Status: superseded legacy generic/default concept research, preserved for
provenance; not the current exact-stage release gate or artwork approval

Catalog inspected: `P1BuiltInGuidanceCatalog` and
`BuiltinBrewerStagePlanFactory` on 28 July 2026

Canonical brewing evidence inspected:
`coffee_brewing_library_2026-07-27.json`, Volumes 1–4,
`source_register.csv`, `coverage_matrix.csv`, and `validation_report.md`

## Supersession notice

This document preserves the earlier profile-level generic/default asset
inventory and its equipment, safety, cultural, and visual-geometry research. Its
68-slot table is historical concept-planning provenance, not the current P1
release contract.

The current release authority is the recipe-and-source-stage-scoped
[P1 exact-stage implementation matrix](p1-exact-stage-matrix.md) together with
`BuiltInP1ExactStagePlanCatalog`. That contract contains 114 ordered stages, of
which **81 require exact illustrations**: 53 `mandatory` and 28
`safety-critical`. Current asset production and approval must use those exact
stage/content/asset identities. Nothing in the preserved 68-slot inventory may
be counted as exact-stage release coverage merely because its generic action or
brewer profile appears similar.

## Evidence precedence and identity rules

The supplied **Comprehensive Evidence-Driven Coffee Brewing Guide Library**
(schema 1.0.0, verified 27 July 2026) is the primary project evidence for this
brief. It validates 64 recipes, 369 ordered stages, 81 source records, and 18
method families. The linked manufacturer, standards-body, cultural, and
specialist sources below are provenance and visual-geometry corroboration; they
do not override a narrower recipe/equipment record in the canonical JSON.

The library requires the hierarchy **method family → brewer profile → equipment
configuration → recipe → ordered stage**. Illustration generation must preserve
that hierarchy. In particular:

- Do not transfer an action across different filter geometry, valve mechanism,
  chamber capacity, heat source, or accessory configuration merely because the
  broad method family is similar.
- Preserve unresolved quantities and observations in text. Do not invent a
  timer, fill line, screw torque, machine indicator, heat setting, or capacity
  in the bitmap.
- Treat `mandatory`, `optional`, `no_meaningful_value`, and
  `safety_critical_expert_review` as recipe-stage classifications. They are not
  interchangeable with the app's P1 release-gate requirement that a default
  asset ID exist.
- Retain the current app IDs. The library family IDs differ in two places:
  app `steep_and_release` maps to library `steep_release`, and app
  `restricted_flow_gravity_concentrate` maps to library `phin`.
- An illustration is approvable only for the exact recipe/profile/equipment
  scope named in its review record. A visually plausible generic composition
  is not evidence of universal compatibility.

### Canonical recipe and equipment crosswalk

| App profile | Canonical recipe ID | Exact canonical brewer/filter/accessory scope | Evidence |
| --- | --- | --- | --- |
| `clever_style` | `clever_water_first_15_250` | `clever_style_bottom_actuated_dripper`; correct rinsed wedge paper; brewer off server while closed; actuating server | Original creator; high |
| `clever_style` | `clever_coffee_first_15_250` | `clever_style_bottom_actuated_dripper`; rinsed wedge paper; brewer off server while closed | Battle-tested; medium |
| `hario_switch` | `switch_official_20_240` | `hario_switch_02`; V60 02 paper; intact steel-ball/lever valve; server at least 300 ml | Official; high |
| `hario_switch` | `switch_ole_boen_hybrid_16_5_240` | `hario_switch_02`; rinsed V60 02 paper; functioning lever/ball; gooseneck kettle | Competition-proven; medium-high |
| `hario_switch` | `switch_gravity_15_250` | `hario_switch_02_used_open`; V60 02 paper; valve held open throughout | Official transfer within compatible V60 geometry; high |
| `valve_release_generic` | **No canonical recipe** | No universal filter, actuator, release motion, capacity, or safe handling point exists in the library | **Blocked** |
| `cezve_generic` | `cezve_turkish_single_rise_6_65` | `small_one_cup_cezve_ibrik_with_secure_handle_and_adequate_foam_headroom`; intentionally unfiltered; small heat source; demitasse above prepared volume | Historically documented; medium-high |
| `cezve_generic` | `cezve_bounded_repeated_rise_12_130` | `two_cup_cezve_with_ample_neck_headroom`; intentionally unfiltered; stable low heat; two cups | Historically documented variant; medium-low |
| `automatic_batch_generic` | `auto_batch_500_30` | one-button 500 g brewer; **recorded exact** cone or flat basket and matching paper; compatible carafe | Official; high |
| `automatic_batch_generic` | `auto_batch_1000_60` | 1 L-capable brewer; **recorded exact** cone or flat basket and matching paper; thermal or glass carafe | Official; high |
| `automatic_single_cup_generic` | `auto_cupone_20_300` | `technivorm_moccamaster_cup_one_with_1_paper_and_full_marked_reservoir`; standard #1 paper; clear tiny outlet; supplied outlet brush; at least 300 ml mug | Official; high |
| `vietnamese_phin` | `phin_gravity_14_118` | `single_serving_gravity_phin_with_loose_drop_in_press_disc`; integrated perforated base; loose gravity disc; stable heat-safe cup; lid | Original creator; high |
| `vietnamese_phin` | `phin_screw_18_120` | `single_serving_screw_insert_phin_of_approximately_120_150_ml_chamber_capacity`; threaded insert engaged lightly; stable heat-safe cup; lid | Battle-tested; medium |

## Legacy generic/default scope and historical count

The superseded profile-level inventory defined **68 mandatory default
illustrations**:

| Method family | Brewer profile | Assets |
| --- | --- | ---: |
| `steep_and_release` | `clever_style` | 9 |
| `steep_and_release` | `hario_switch` (shared by two workflows) | 11 |
| `steep_and_release` | `valve_release_generic` | 9 |
| `heated_unfiltered` | `cezve_generic` | 10 |
| `automatic_batch` | `automatic_batch_generic` | 9 |
| `automatic_batch` | `automatic_single_cup_generic` | 8 |
| `restricted_flow_gravity_concentrate` | `vietnamese_phin` | 12 |
|  | **Total** | **68** |

Within this preserved legacy inventory, every row below maps one scoped default
`InstructionAssetId` to a source stage. Its identity language reflects the
pre-exact model and does not define the current exact-stage release IDs. Safety
cards, global-safety cards, and utility cards have text but no separate planned
visual slot.

The original master brief contains 129 minimum-curriculum action bullets across
Pulsar, legacy methods, and P1 methods. Those bullets are a broader product
minimum. At the time of this research, the 68 P1 slots below were treated as
the profile-level drawable contracts. They are retained for provenance and
concept-risk research but no longer participate in the current exact-stage
release gate.

## In-app placement and delivery contract

Each approved image is reused in both contexts:

1. **Learn this brewer:** inside its ordered stage card, immediately after the
   stage instruction.
2. **Brew now:** once in the live Guidance card for the exact current stage,
   after the currently visible instruction and safety copy.

The shared renderer fills the card width, reserves a 4:3 viewport, clips to the
Material medium shape, and uses `ContentScale.Fit`. The Learn layout leaves
20 dp screen margins and 16 dp card padding; the live layout also renders the
image full-width inside a 16 dp-padded card. Because the production master and
viewport are both exactly 4:3, the image should fill the viewport without
cropping or letterboxing. Keep every critical action inside the full frame and
clear of the rounded-corner clip; it must still read at roughly 300–400 dp wide.

Production assumptions:

- Opaque 1024 × 768 master, 4:3 exactly.
- Optimized lossless or visually lossless WebP in `drawable-nodpi`.
- Aim for at most 300 KB per final illustration.
- No words, letters, numbers, logos, brand marks, UI, captions, recipe cards,
  measurement labels, decorative clutter, or multiple panels.
- One action or state per image. A comparison is acceptable only when the
  lesson itself is explicitly “correct versus avoid.”
- Semi-flat, softly dimensional, warm-neutral palette; clear silhouettes;
  subtle shadows; neutral counter/background.
- Dynamic dose, water, temperature, timing, and capacity values stay in Compose
  text, never in pixels.
- Do not use colour alone to identify an open/closed valve, completion, heat, or
  danger. Geometry, liquid flow, or hand action must carry the meaning.

Evidence labels in the inventory:

- **Direct:** the cited primary/specialist source demonstrates the physical
  action or state.
- **Bounded:** multiple product manuals support the class of action, but the
  exact hardware remains model-specific.
- **Inference:** the composition is a conservative visualization of current app
  copy, not a universal external claim.
- **Blocked:** a single default bitmap cannot accurately cover the variants
  collapsed by the current profile/stage. It must not be marked approved until
  the profile or asset-selection model is narrowed.

## Exact canonical stage-fidelity crosswalk

This crosswalk joins each stable asset suffix to canonical ordered stages. The
full, unchanged asset IDs and current app instructions remain in the inventory
below. `M` means mandatory, `O` optional, and `S` safety-critical expert review
in the canonical recipe. “Cue” means the state is useful art direction but is
not a separate ordered stage. `—` means the library contains no equivalent
ordered stage; it must not be presented as canonical for that recipe.

Recipe abbreviations: `CW` = `clever_water_first_15_250`; `CC` =
`clever_coffee_first_15_250`; `SO` = `switch_official_20_240`; `SH` =
`switch_ole_boen_hybrid_16_5_240`; `SG` = `switch_gravity_15_250`; `CS` =
`cezve_turkish_single_rise_6_65`; `CR` =
`cezve_bounded_repeated_rise_12_130`; `B5` = `auto_batch_500_30`; `B10` =
`auto_batch_1000_60`; `CO` = `auto_cupone_20_300`; `PG` =
`phin_gravity_14_118`; `PS` = `phin_screw_18_120`.

### Clever asset-to-stage fidelity

| Stable asset suffix | `CW` canonical stage | `CC` canonical stage | Fidelity note |
| --- | --- | --- | --- |
| `insert_and_rinse_filter` | 01 M | 01 M | Exact only with a wedge paper and brewer off the actuating server. |
| `close_valve` | 01 M | 01 M | A retained state, not a hand-operated control: bottom actuator is not depressed. |
| `add_coffee` | 03 M | 01 M | Coffee follows water in `CW` but precedes water in `CC`. |
| `add_water` | 02 M | 02 M | Water precedes coffee in `CW`; the current fixed P1 asset order does not. |
| `agitate` | 03 M / 04 O | 02 M | Gentle mix/stir only; `CC` warns that over-agitation moves fines to paper. |
| `steep` | 04 O | 03 O | Optional illustration in both canonical recipes. |
| `place_on_server` | 05 M/S | 04 M/S | Exact server must support the brewer, hold the batch, and actuate the bottom valve. |
| `observe_drawdown` | 05 cue | 04 cue | Flow and completion cue belong to the release stage, not a distinct canonical stage. |
| `remove_and_serve` | 06 M | 04 completion | `CW` says lift vertically so the valve closes; `CC` has no separate removal stage. |

The nine bitmaps can be recipe-scoped, but the current coffee-before-water
sequence cannot represent `CW`. Recipe selection must drive stage order before
the water-first recipe is exposed as supported.

### Hario Switch asset-to-stage fidelity

| Stable asset suffix | `SO` | `SH` | `SG` | Fidelity note |
| --- | --- | --- | --- | --- |
| `insert_and_rinse_filter` | 01 M/S | Preparation only | 01 M/S | `SO` closes after rinse; `SG` must stay open. One valve-visible image cannot serve both. |
| `close_valve` | 01 M/S | 01 M/S and 03 M/S | **Incompatible** | Never show a closed valve in the gravity recipe. |
| `add_coffee` | 02 M | Preparation prerequisite | Preparation prerequisite | Shared art must omit valve state; V60 02 paper remains visible. |
| `add_water` | 02 M, closed | 01/02/03 M, alternating | 02/03 M, open | One closed-state image cannot represent hybrid or gravity pours. |
| `agitate` | — | — | — | No canonical Switch recipe contains this as an ordered stage. |
| `steep` | 03 O | Retention within 01/03 | **Incompatible** | Exact only for official full immersion when presented as a standalone stage. |
| `open_valve` | 04 M/S | 02 M and 04 M/S | Already open | Lever direction and ball state require real Switch 02 geometry. |
| `observe_drawdown` | 05 M | 05 O | 04 O | Shared state art may show open drawdown, but recipe classification differs. |
| `remove_and_serve` | 05 M | — | — | Only the official recipe names removal in its ordered stage. |
| `open_valve_for_manual_gravity` | **Incompatible** | Phase-dependent | 01 M/S | Exact only for `SG`, with valve held open throughout. |
| `pour_water` | **Incompatible** | 02 M only while open | 02/03 M | Pour geometry, phase, and cumulative target stay recipe-specific in text. |

The current 11 slots can cover official full immersion and gravity only with
recipe-aware valve variants. They do not faithfully encode the hybrid's
closed-bloom → open-percolation → closed-retention → open-release sequence.

### Generic valve-release fidelity

All nine `valve_release_generic` asset IDs have **no canonical recipe or exact
brewer profile**. Insert/rinse, coffee, water, agitation, steep, drawdown, and
removal may be category concept art, but none is approvable as exact equipment
instruction. `close_valve` and `open_valve` are hard blockers because actuation
can be vessel-triggered, lever/ball operated, or another mechanism; filter
geometry and safe handling points are also unresolved. Variant selection or a
named profile is required before any of the nine becomes release evidence.

### Cezve asset-to-stage fidelity

| Stable asset suffix | `CS` | `CR` | Fidelity note |
| --- | --- | --- | --- |
| `select_pot_capacity` | Equipment prerequisite | Equipment prerequisite | One-cup and two-cup pot/headroom geometries differ; no ordered stage exists. |
| `add_water` | 01 M/S | 01 M/S | Canonical stage combines cold water, very-fine coffee, and optional sugar off heat. |
| `add_finely_ground_coffee` | 01 M/S | 01 M/S | Intentionally unfiltered; preserve safe headroom. |
| `add_sugar_before_heating` | 01 M/S, optional ingredient | 01 M/S, optional ingredient | Never imply sugar is required. |
| `mix_before_heating` | 02 M | 02 M | Pot is off heat; no dry clumps remain. |
| `apply_gentle_heat` | 03 M/S | 03 M/S | Stable low heat, continuous attendance, handle away from heat. |
| `observe_foam_rising` | 03 cue | 03 cue | Show controlled foam ring/rise, never rolling boil. |
| `reduce_or_remove_heat` | 04 M/S | 04 M/S | `CR` additionally requires return to low heat for stage 05 M/S. |
| `pour_with_foam` | 05 M | 06 M | `CR` divides into two stable cups; do not reuse one-cup art. |
| `allow_grounds_to_settle` | 06 O | 06 M | Grounds are intentionally present; hot-liquid warning remains in text. |

The repeated-rise recipe cannot be represented completely by the current ten
assets because there is no return-to-heat/final-bounded-rise asset. A single
one-cup, flat-hob archetype also cannot be approved for the two-cup profile or
for a selected open-flame configuration.

### Automatic-batch asset-to-stage fidelity

| Stable asset suffix | `B5` | `B10` | Fidelity note |
| --- | --- | --- | --- |
| `select_filter_and_basket` | 01 M/S | 01 M/S | Cone and flat baskets are separate recorded configurations, never interchangeable visuals. |
| `rinse_filter` | — | — | Not a canonical ordered stage for either recipe; keep conditional on the exact manual. |
| `add_coffee` | 01 M/S | 01 M/S | Exact basket, paper, rim, outlet, and swollen-bed capacity must match selected configuration. |
| `level_coffee_bed` | 01 M/S | 01 M/S | Level without tamping; no grounds on rim. |
| `add_reservoir_water` | 02 M | 02 M | 500 g versus 1,000 g remains text; selected machine limit geometry must match. |
| `start_machine` | 03 M/S | 03 M | `B5` may require carafe lid/valve actuation; never generalize removal rules. |
| `observe_machine_completion` | 04 M | 03/04 cue | Visible transfer end and basket drainage; no invented internal phase or indicator. |
| `stir_or_swirl_carafe` | 05 M | 04 M | Exact carafe and lid handling; keep low and stable. |
| `serve` | 05 M | 04 M | Glass hotplate and thermal-carafe hazards differ. |

Even the canonical batch profiles require the actual cone/flat basket and
glass/thermal carafe configuration to be recorded. Generate variants for every
basket-visible, carafe-handling, control, fill-limit, and hot-surface image.

### Cup-One versus generic single-cup fidelity

| Stable asset suffix | `CO` canonical stage | Fidelity note |
| --- | --- | --- |
| `select_and_insert_filter` | 01 M/S | Exact view must show a standard #1 paper **and a visibly clear tiny outlet** while power is off. |
| `rinse_filter` | — | No canonical ordered stage; do not substitute it for outlet inspection/cleaning. |
| `add_coffee` | 02 M | Official range remains text; no grounds on rim. |
| `level_coffee_bed` | 02 cue | Model-specific filter holder, outlet, and #1 paper remain exact. |
| `add_reservoir_water` | 03 M/S | Must also show the outlet pipe centered over the holder and a stable at-least-300 ml mug. |
| `start_machine` | 04 M/S | Do not remove basket or mug during the cycle. |
| `observe_machine_completion` | 05 M | Wait for residual dripping, not merely machine switch-off. |
| `serve` | 05 M | Outlet pipe and coffee remain hot. |

The generic eight-stage plan omits `CO` stage 06 (unplug, discard grounds, and
brush the tiny outlet) and does not make outlet clearance/pipe geometry explicit
at stages 01 and 03. It is not an exact Cup-One guide until a named profile and
those safety/maintenance actions are represented.

### Phin asset-to-stage fidelity

| Stable asset suffix | `PG` | `PS` | Fidelity note |
| --- | --- | --- | --- |
| `place_on_stable_cup` | 01 M/S | 01 M/S | Cup rim must fully support the exact base plate. |
| `add_coffee` | 01 M/S | 01 M/S | Gravity uses 14 g; screw uses 18 g; quantities stay in text. |
| `level_coffee` | 01 cue | 01 cue | Keep perforations clear and bed level; no universal packing force. |
| `place_gravity_or_screw_insert` | 02 M | 02 M/S | **Split required:** loose drop-in disc versus lightly engaged threaded insert. |
| `pre_wet` | 03 M | 03 M | Exact insert remains visible; no force or squeeze. |
| `fill_chamber` | 04 M | 04 M | Different total/capacity/headroom; keep quantity in text. |
| `cover` | 04 M | 04 M | Exact lid and chamber geometry. |
| `observe_first_drip` | 05 M | 05 M/S | Observation replaces invented universal timing in art. |
| `check_drip_rate` | 05/06 cue | 05 M/S | Only screw recipe permits a tiny safe loosening if fully stalled; never show pressurising. |
| `observe_drip_completion` | 06 M | 05 cue | Five-minute gravity and five-to-eight-minute screw windows remain text. |
| `remove_hot_filter` | 07 M/S | 06 M | Set the exact hot phin on its inverted lid or heat-safe tray. |
| `collect_concentrate` | 07 service state | 06 service state | Milk, ice, dilution, and sugar are separate service choices. |

Gravity and screw assets must be separately selected from the equipment
configuration. A single blended insert illustration is both mechanically
misleading and unsafe.

## Canonical source register and visual corroboration

The `SRC-*` identifiers below come from the supplied source register and are
the provenance used by canonical recipe stages. `C*`, `H*`, `Z*`, `A*`, and
`P*` are additional references used to verify visible equipment geometry and
safe composition. Where the two differ, the canonical recipe record governs
the action; the manufacturer/manual image governs only the exact hardware it
actually depicts.

### Clever-style

Canonical recipe provenance: [SRC-CLEVER-HOFFMANN](https://www.youtube.com/watch?v=RpOdennxP24)
and [SRC-CLEVER-COFFEECHRONICLER](https://coffeechronicler.com/clever-dripper-recipe/).

- **C1 — official Clever product and brew protocol:**
  [CLEVER Dripper](https://cleverbrewing.coffee/collections/clever-manual-brewers/products/clever-dripper).
  It directly supports a cone paper filter, filter rinse, coffee/water
  saturation, steeping, placement on a cup/carafe to activate drainage, waiting
  for full drain, and removal.
- **C2 — official Clever replacement release ring:**
  [CLEVER Dripper Replacement Release Ring](https://cleverbrewing.coffee/products/clever-dripper-replacement-release-ring-clear).
  It directly confirms that placing the brewer on a vessel triggers the
  release ring.

The current P1 fixed order stages coffee before water. That can map to `CC`, but
it cannot map to canonical water-first `CW`; selected-recipe fidelity requires
recipe-driven ordering.

### Hario Switch

Canonical provenance: [SRC-HARIO-SWITCH](https://global.hario.com/product/coffee/dripper/SSD.html),
[SRC-HARIO-V60-OFFICIAL](https://www.hario.co.uk/pages/brew-guides-v60-intermediate),
and [SRC-KURASU-SWITCH](https://kurasu.kyoto/blogs/recipe/hario-switch-recipe).

- **H1 — official Hario product page:**
  [V60 Immersion Dripper Switch](https://www.hario-europe.com/products/v60-immersion-dripper-switch).
  It documents the heatproof-glass V60 cone, paper filter, stainless ball that
  blocks flow, and button/switch action that releases coffee.
- **H2 — official Hario manual:**
  [SSD user manual](https://www.hario.com/manual_pdf/SSD.pdf).
- **H3 — official Hario recipe:**
  [V60 Switch recipe with Partners Coffee](https://www.hario-usa.com/blogs/recipes-and-more-from-friends/v60-switch-recipe-with-partners-coffee).
  It directly demonstrates valve-open pours, valve-closed immersion, reopening
  to drain, and removal.

No exact valve direction or hand pose should be approved from generated memory.
The reviewer must compare the switch, silicone base, and ball/lever state
against H1/H2 product geometry.

### Generic valve-release

There is no primary source that establishes one filter, valve actuator,
capacity, or release motion for every valve-release brewer. C1/C2 and H1–H3
prove that the category contains materially different mechanisms: vessel-
triggered release and a manual ball valve. They support the category model but
not a universal physical illustration.

### Cezve / ibrik

Canonical provenance: [SRC-MEHMET-EFENDI](https://www.mehmetefendi.com/eng/turkish-coffee/preparation)
and [SRC-UNESCO-TURKISH](https://ich.unesco.org/en/RL/turkish-coffee-culture-and-tradition-00645).
The repeated-rise record remains a documented variant, not a universal default.

- **Z1 — regional specialist organization:**
  [Turkish Coffee Culture and Research Association, “7 Steps to Perfect Coffee”](https://www.turkkahvesidernegi.org/en/index.php?gastronomi=menuactive&icerik=7-adimda-mukemmel-turk-kahvesi-kilavuzu).
  It directly supports selecting a pot with room for foam, powder-fine coffee,
  optional sugar, water, mixing before heat, low flame, observing the rise,
  removing before the brim, optional repeated rise, and distributing foam.
- **Z2 — coffee producer’s traditional preparation:**
  [Kurukahveci Mehmet Efendi, “With Cezve”](https://www.mehmetefendi.com/eng/brew-guide/turkish-coffee/with-cezve).
  It supports measured water, coffee and optional sugar, slow heat, foam, and
  gentle pouring.
- **Z3 — primary pot-maker capacity examples:**
  [Soy Cezve C2](https://shop.soy.com.tr/products/cezve-turkish-coffee-pot-soy-c2-serves-2).
  It demonstrates that usable capacity and cup count vary by pot and serving
  convention.

These sources describe one Turkish preparation tradition. The generic profile
must not label it as the sole authentic regional method. Heat source and pot
material remain selected-equipment variables.

### Automatic batch and podless single-cup

Canonical provenance: [SRC-SCA-CERTIFIED-HOME](https://sca.coffee/certified-home-brewer),
[SRC-ECBC-STANDARD](https://ecbc.info/),
[SRC-MOCCAMASTER-BREW](https://us.moccamaster.com/blogs/blog/how-to-brew-coffee-with-moccamaster),
and [SRC-CUPONE-MANUAL](https://www.moccamaster.eu/pub/media/handleidingen/talen/User_Manual_Cup-One.pdf).

- **A1 — official Breville manual:**
  [Precision Brewer Glass instruction manual](https://www.breville.com/content/dam/breville/us/assets/miscellaneous/instruction-manual/coffee/BDC400-instruction-manual.pdf).
  It directly shows that basket/filter choice, reservoir limit, carafe
  placement, controls, completion, hot surfaces, and overflow precautions are
  model-specific.
- **A2 — official Technivorm manual:**
  [Moccamaster KBGV Select brew guide](https://support.moccamaster.com/hc/en-us/article_attachments/1500014339002).
  It supports reservoir fill, No. 4 filter and rinse, coffee addition, carafe
  placement, start, and machine completion.
- **A3 — official Technivorm single-cup manual:**
  [Moccamaster Cup-One brew guide](https://support.moccamaster.com/hc/en-us/article_attachments/1500014620701).
  It supports a machine-specific No. 1 filter, rinse, coffee, reservoir, cup
  placement, start, and hot-outlet warning.
- **A4 — official OXO manual:**
  [OXO 8-Cup Coffee Maker manual](https://www.oxo.com/media/wysiwyg/Brew_8718800_8CupCoffeeMaker_Manual_M03.pdf).
  It supports correct mug/carafe mode, basket/filter, water between model
  limits, start, a non-colour-only completion cue, stable placement, and hot
  surface/scald safety.

These sources justify only user-controlled setup and externally observable
completion. They do not justify a universal basket, button, indicator, auto-off
behavior, rinse requirement, or internal machine stage.

### Vietnamese phin

Canonical provenance: [SRC-NGUYEN-PHIN](https://nguyencoffeesupply.com/blogs/news/vietnamese-coffee-phin-brew-guide),
[SRC-TRUNGNGUYEN-PHIN](https://trung-nguyen-coffee.co.uk/page_brewing.php), and
[SRC-GOURMETKAVA-PHIN](https://www.gourmetkava.cz/en/blog/making-coffee/preparation-of--vietnamese-coffee).

- **P1 — specialist vendor brew guide:**
  [Nguyen Coffee Supply phin guide](https://nguyencoffeesupply.com/blogs/vietnamese-coffee-brew-guide/traditional-vietnamese-drip-phin).
  It directly supports plate/chamber on a glass, coffee addition and leveling,
  gravity press, a small pre-wet, chamber fill, first drip, and final drip.
- **P2 — specialist vendor anatomy and variant guide:**
  [Nguyen Coffee Supply, “What Is a Vietnamese Phin Filter?”](https://nguyencoffeesupply.com/blogs/news/what-is-the-vietnamese-phin-filter).
  It identifies chamber, plate, lid, gravity press, and screw-on press, and
  explains that the two insert mechanisms differ.
- **P3 — specialist vendor flow troubleshooting:**
  [Nguyen Coffee Supply, “Why Won’t My Phin Filter Drip?”](https://nguyencoffeesupply.com/blogs/news/why-wont-my-phin-filter-drip).
  It supports first/final drip as observational cues and confirms that grind,
  blocked holes, freshness, and insert pressure affect flow.
- **P4 — Vietnamese-founded coffee company guide:**
  [Nam Coffee phin guide](https://www.nam.coffee/blogs/news/how-to-use-a-vietnamese-phin-filter).
  It supports the four-part assembly, stable cup placement, leveling, insert,
  pre-wet/fill, completion, careful removal, and optional serving additions.

The sources do not support one universal target drip interval for every phin,
coffee, dose, or insert. The bitmap must therefore teach first/final drip as an
observable state; exact canonical time windows, where present, stay in recipe
text.

## Asset inventory and generation compositions

All rows use the common placement and 1024 × 768 delivery contract above.
`⚠` marks a source stage with structured safety messages in the current plan.

### Clever-style brewer — `steep_and_release` / `clever_style` (9)

Exact generation scope is `clever_style_bottom_actuated_dripper`, wedge paper,
and an actuating server. Prompt/review metadata must name either `CW` or `CC`;
the two recipes have opposite water/coffee order and cannot share one ordered
instruction sequence.

| Exact asset ID | Exact stage instruction | Text-free composition and accuracy notes | Evidence |
| --- | --- | --- | --- |
| `instruction_steep_and_release_clever_style_clever_style_insert_and_rinse_filter_default` | Fit the filter selected for this Clever-style brewer, then rinse it with hot water. | Unbranded translucent bottom-actuated brewer held safely over a waste vessel; correct wedge paper seated flush; narrow kettle stream visibly wets all paper and drains. No filter number, logo, server actuation claim, or floating paper. | `CW` 01 M; `CC` 01 M; C1 geometry. |
| `instruction_steep_and_release_clever_style_clever_style_close_valve_default` | Set the brewer to its retained-water position according to its own instructions. | Clever-style brewer **off** the cup/server on a level scale or trivet, outlet unobstructed and no flow. Do not invent a hand-operated lever: Clever closes when not vessel-triggered. | `CW` 01 M; `CC` 01 M; C2 mechanism. |
| `instruction_steep_and_release_clever_style_clever_style_add_coffee_default` | Add the measured coffee from your selected recipe and level the bed. | Dose cup pouring grounds into the fitted wedge paper; shallow top view makes the even bed plane legible; brewer remains off server. No depicted quantity. | `CW` 03 M after water; `CC` 01 M before water. |
| `instruction_steep_and_release_clever_style_clever_style_add_water_default` | Pour the selected water amount over the coffee without filling beyond a stable working level. | Kettle stream entering coffee bed; liquid clearly below rim with ample headspace; brewer stable off server, no drain stream. | `CC` 02 M exact; **incompatible with `CW` 02**, which adds water before coffee. |
| `instruction_steep_and_release_clever_style_clever_style_agitate_default` | Agitate only as your selected recipe calls for. | One spoon making a small, gentle circular slurry movement; other hand steadies handle/base; no splashing, aggressive whisking, or implied count. | `CW` 03 M/04 O; `CC` 02 M; over-agitation warning for `CC`. |
| `instruction_steep_and_release_clever_style_clever_style_steep_default` | Let the coffee steep until you are ready to continue with your selected recipe. | Brewer off server, still dark slurry below rim, no hand, no flow, no unsupported lid, and no clock. | `CW` 04 O; `CC` 03 O. |
| `instruction_steep_and_release_clever_style_clever_style_place_on_server_default` ⚠ | Place the brewer on a stable server to begin the release described by its manufacturer. | Hand on brewer handle lowering it vertically onto a wide, level server; bases aligned and all support points visible; first safe drain stream just beginning. No tilting or hand under hot outlet. | Direct C1/C2; safety-critical stability and burn review. |
| `instruction_steep_and_release_clever_style_clever_style_observe_drawdown_default` | Watch for drawdown to finish before removing the brewer. | Brewer fully seated on server; continuous centered coffee stream; slurry level visibly receded; hands absent. | Direct C1. |
| `instruction_steep_and_release_clever_style_clever_style_remove_and_serve_default` | Remove the brewer carefully and serve the coffee. | Focus on the first action: lift drained brewer straight up by handle from stable server; no stream and bed visibly drained. Do not also depict pouring a cup, which would make a two-action image. | Direct C1; serving remains in companion text. |

### Hario Switch — `steep_and_release` / `hario_switch` (11)

Use exact Switch 02, V60 02 paper, steel ball/lever, and stable server geometry.
The add-coffee, drawdown, and removal images are shared across current workflows,
so they must not assert the wrong valve state. Valve-visible images require
recipe-specific full-immersion or gravity variants. The 11 slots do not encode
the canonical hybrid's alternating four-phase valve sequence.

| Exact asset ID | Exact stage instruction | Text-free composition and accuracy notes | Evidence |
| --- | --- | --- | --- |
| `instruction_steep_and_release_hario_switch_hario_switch_insert_and_rinse_filter_default` | Fit the selected V60-compatible filter in the Hario Switch and rinse it with hot water. | Logo-free but geometrically faithful glass V60 cone, black silicone Switch base, and selected V60 paper; open/draining state while kettle wets paper over stable server. | Direct H1/H2; reviewer must verify real Switch proportions. |
| `instruction_steep_and_release_hario_switch_hario_switch_close_valve_default` | Close the Hario Switch valve before the immersion brew begins. | Close framing on real Switch lever/base as a hand moves it to the H2-documented closed state; ball seated and no outlet flow provide a non-colour cue. | Direct H1/H2; reject a guessed lever direction. |
| `instruction_steep_and_release_hario_switch_hario_switch_add_coffee_default` | Add the measured coffee from your selected recipe and level the bed. | Dose cup over fitted cone paper; framing emphasizes glass cone and level bed while omitting the base/lever so the art is valid for both open gravity and closed immersion workflows. | Bounded H2/H3; no amount. |
| `instruction_steep_and_release_hario_switch_hario_switch_add_water_default` | Pour the selected water amount over the coffee while the valve remains closed. | Glass cone and silicone base both visible; kettle stream, slurry below rim, ball seated/lever closed, and no drip below. | Direct H1/H3. |
| `instruction_steep_and_release_hario_switch_hario_switch_agitate_default` | Agitate only as your selected recipe calls for while the valve remains closed. | Gentle spoon movement in slurry; Switch remains closed with no lower stream; server stable and centered. | **No matching canonical ordered stage** in `SO`, `SH`, or `SG`; concept art only. |
| `instruction_steep_and_release_hario_switch_hario_switch_steep_default` | Let the coffee steep according to your selected recipe before opening the valve. | Still slurry retained in glass cone, closed ball/lever state, no hand, no stream, no timer. | `SO` 03 O only; hybrid retention is phase-bound and gravity is incompatible. |
| `instruction_steep_and_release_hario_switch_hario_switch_open_valve_default` ⚠ | Open the Hario Switch valve to begin drawdown into the server. | Hand operates the real H2-documented lever; ball visibly lifted and first centered stream enters broad stable server. Keep hand clear of glass and hot outlet. | Direct H1/H2/H3; critical hot-glass/stability review. |
| `instruction_steep_and_release_hario_switch_hario_switch_observe_drawdown_default` | Watch for drawdown to finish before removing the Switch. | Stable Switch/server stack, open valve, centered stream, bed receding; no hand. The fitted frame must show both glass body and support footprint. | Direct H1/H3. |
| `instruction_steep_and_release_hario_switch_hario_switch_remove_and_serve_default` | Remove the Hario Switch carefully and serve the coffee. | Lift drained Switch vertically by the cool silicone/base handling area or documented safe point; server remains stable; no second serving action. | Direct H3; hot-glass review. |
| `instruction_steep_and_release_hario_switch_hario_switch_open_valve_for_manual_gravity_default` | Keep the Hario Switch valve open before brewing it as a gravity dripper. | Empty prepared Switch on stable server with real valve visibly open/ball lifted; a tiny clear-water drip may confirm open state without colour or symbols. | Direct H3. |
| `instruction_steep_and_release_hario_switch_hario_switch_pour_water_default` ⚠ | Pour water in the pattern and amount your selected recipe calls for while the valve stays open. | Controlled gooseneck stream onto coffee bed, open valve and simultaneous coffee flow into server; liquid safely below rim; stable full footprint. No universal circles, pulses, or target amount. | Direct H3; hot-liquid/hot-glass/stability review. |

### Generic valve-release brewer — `steep_and_release` /
`valve_release_generic` (9)

**Approval blocker:** a “generic” actuator cannot be physically accurate to the
Clever vessel trigger, Hario ball lever, and other release mechanisms at once.
The safe long-term fix is to split named/geometry-specific profiles or select a
reviewed visual variant from the user’s equipment configuration. The following
archetype briefs are useful concept art only. Because the canonical library has
no `valve_release_generic` recipe/profile at all, **none of the nine assets is
canonical release evidence**; close/open are the most acute blockers.

| Exact asset ID | Exact stage instruction | Text-free composition and accuracy notes | Evidence |
| --- | --- | --- | --- |
| `instruction_steep_and_release_valve_release_generic_valve_release_generic_insert_and_rinse_filter_default` | Fit the filter selected for this valve-release brewer, then rinse it with hot water. | Neutral unbranded cone body; selected filter fitted and wetted over a stable server; actuator deliberately outside the frame. | Inference from category examples C1/H1; filter geometry remains selected-equipment-specific. |
| `instruction_steep_and_release_valve_release_generic_valve_release_generic_close_valve_default` | Set the brewer to the retained-water position described by its manufacturer. | Show retained liquid with no outlet flow, not a guessed finger motion. Even this state cannot teach how to operate the selected device. | **Blocked**: no universal release control. |
| `instruction_steep_and_release_valve_release_generic_valve_release_generic_add_coffee_default` | Add the measured coffee from your selected recipe and level the bed. | Tight framing of dose cup, selected filter, and level bed; hide actuator and brand-specific body details. | Inference from C1/H3; no universal filter. |
| `instruction_steep_and_release_valve_release_generic_valve_release_generic_add_water_default` | Pour the selected water amount over the coffee without exceeding a stable working level. | Kettle stream, retained slurry with generous headspace, stable base, no outlet flow; no capacity marks. | Inference from category mechanics. |
| `instruction_steep_and_release_valve_release_generic_valve_release_generic_agitate_default` | Agitate only as your selected recipe calls for. | Small spoon movement in retained slurry; no actuator shown, no aggressive motion or count. | Inference; recipe-specific. |
| `instruction_steep_and_release_valve_release_generic_valve_release_generic_steep_default` | Let the coffee steep according to your selected recipe. | Still retained slurry, no hand, no flow, no clock; neutral silhouette. | Inference from steep-and-release category. |
| `instruction_steep_and_release_valve_release_generic_valve_release_generic_open_valve_default` ⚠ | Use the selected brewer's documented release control to begin drawdown. | The only safe generic state is first flow into a stable server; omit a fabricated control gesture. This cannot by itself teach the required operation. | **Blocked**: no universal control; hot-liquid/overflow/stability risk. |
| `instruction_steep_and_release_valve_release_generic_valve_release_generic_observe_drawdown_default` | Watch for drawdown to finish before removing the brewer. | Stable brewer/server stack, coffee stream and visibly receding bed; actuator out of view. | Bounded category inference. |
| `instruction_steep_and_release_valve_release_generic_valve_release_generic_remove_and_serve_default` | Remove the brewer carefully and serve the coffee. | Lift drained brewer vertically from stable server using an intentionally neutral cool handling point; no second serving action. | Bounded category inference; safe point is device-specific. |

### Cezve / ibrik — `heated_unfiltered` / `cezve_generic` (10)

Do not use one pot/heat-source bitmap set for both canonical recipes. `CS`
requires a one-cup pot; `CR` requires a two-cup pot and a second controlled rise.
Generate logo-free pot-capacity and selected-heat-source variants. A flat hob is
valid only for a selected flat-hob configuration, not a universal default.
The current ten IDs also lack `CR`'s return-to-low-heat/final-rise action, so
that recipe remains illustration-incomplete.

| Exact asset ID | Exact stage instruction | Text-free composition and accuracy notes | Evidence |
| --- | --- | --- | --- |
| `instruction_heated_unfiltered_cezve_generic_cezve_generic_select_pot_capacity_default` | Choose a cezve or ibrik and confirm that your selected recipe fits it safely. | Selected pot beside the intended small serving vessel; pot cross-section visibly leaves generous empty neck/headspace for foam. No amount marks or “too small” second panel. | Direct Z1/Z3; exact capacity stays in text. |
| `instruction_heated_unfiltered_cezve_generic_cezve_generic_add_water_default` | Add the water amount from your selected recipe to the vessel. | Small pitcher pouring clear water into pot while pot is off heat; visible headspace and stable base. | Direct Z1/Z2. |
| `instruction_heated_unfiltered_cezve_generic_cezve_generic_add_finely_ground_coffee_default` | Add the coffee amount and grind specified by your selected recipe. | Spoon tips visibly powder-fine coffee into the water-filled pot; pot off heat; do not depict coarse granules or filter. | Direct Z1/Z2. |
| `instruction_heated_unfiltered_cezve_generic_cezve_generic_add_sugar_before_heating_default` | If your own selected recipe includes sugar, add it before heating. | Small spoon or plain sugar cube entering pot; water/coffee already present, hob visibly off/background. No sweetness scale. | Direct Z1/Z2; optional recipe branch. |
| `instruction_heated_unfiltered_cezve_generic_cezve_generic_mix_before_heating_default` | Stir only enough to combine the selected ingredients before heating. | One gentle spoon turn with pot clearly off heat; no splashing or repeated whisking. | Direct Z1; do not show stirring after heat starts. |
| `instruction_heated_unfiltered_cezve_generic_cezve_generic_apply_gentle_heat_default` ⚠ | Apply heat appropriate for your heat source and watch the vessel continuously. | Exact selected pot centered on its selected low-output heat source; handle away from heat; continuous attendance; modest foam-ring onset, ample headspace, no unsupported hand-on-hot-handle assumption. | `CS` 03 M/S; `CR` 03 M/S; selected pot/heat-source/handle variant required. |
| `instruction_heated_unfiltered_cezve_generic_cezve_generic_observe_foam_rising_default` | Watch for foam to rise and record the observation when it happens. | Close view of continuous tan foam swelling upward but still clearly below rim; hand on handle, eyes not required, no vigorous bubbles. | Direct Z1/Z2. |
| `instruction_heated_unfiltered_cezve_generic_cezve_generic_reduce_or_remove_heat_default` ⚠ | Reduce or remove heat when foam approaches the rim. | Universal “remove” branch: hand lifts pot straight away from heat by long handle while foam is near but below rim; stable trajectory, no spill. | Direct Z1; critical hot-metal/boil-over review. |
| `instruction_heated_unfiltered_cezve_generic_cezve_generic_pour_with_foam_default` ⚠ | Pour carefully into the selected serving vessel when it is safe to do so. | Hand on long handle pours a controlled narrow stream into a stable small cup; foam layer remains visibly carried with coffee; hand never touches pot body. | Direct Z1/Z2; burn/stability review. |
| `instruction_heated_unfiltered_cezve_generic_cezve_generic_allow_grounds_to_settle_default` | Allow the grounds to settle according to your own selected recipe or preference. | Small transparent or subtly cutaway serving cup resting untouched; dark fine grounds layer at bottom, calm surface above; no clock. | Direct Z2 and Mehmet Efendi’s preparation background; transparent cup is explanatory inference. |

### Automatic batch brewer — `automatic_batch` /
`automatic_batch_generic` (9)

Use one internally consistent, unbranded home batch archetype for this set:
removable basket, separate reservoir, and lidded carafe. It is representative,
not a universal machine diagram. Never depict access to internal spray,
pre-infusion, heater, or pump stages.

| Exact asset ID | Exact stage instruction | Text-free composition and accuracy notes | Evidence |
| --- | --- | --- | --- |
| `instruction_automatic_batch_automatic_batch_generic_automatic_batch_generic_select_filter_and_basket_default` | Use the filter and basket specified for your particular batch brewer. | Machine basket removed and centered with its one selected, correctly seated filter; machine softly behind it. Do not show competing filter choices or imply interchangeability. | Bounded A1/A2; exact basket/filter is model-specific. |
| `instruction_automatic_batch_automatic_batch_generic_automatic_batch_generic_rinse_filter_default` | If your machine documentation calls for it, rinse the selected filter before brewing. | Selected filter seated in removable basket over sink or waste vessel while clean water wets it; machine inactive in background. | Bounded A2; explicitly optional because manuals differ. |
| `instruction_automatic_batch_automatic_batch_generic_automatic_batch_generic_add_coffee_default` | Add the measured coffee from your selected recipe to the basket. | Dose cup pouring grounds into installed selected filter; basket fully supported, no quantity or machine display. | Direct A1/A2. |
| `instruction_automatic_batch_automatic_batch_generic_automatic_batch_generic_level_coffee_bed_default` | Level the coffee bed gently before starting the machine. | Top-down close view of basket and flat grounds; fingertips give the basket one gentle side-to-side settling motion, never tamp. | Inference from app preparation; no cited generic manual mandates this step. |
| `instruction_automatic_batch_automatic_batch_generic_automatic_batch_generic_add_reservoir_water_default` | Add the selected reservoir water amount without exceeding your machine's marked limit. | Pitcher pours into open reservoir; water visibly below a simple molded limit line and top edge; basket/carafe remain correctly seated. No numbers or labels. | Direct A1/A2; molded line is model-archetype only. |
| `instruction_automatic_batch_automatic_batch_generic_automatic_batch_generic_start_machine_default` ⚠ | Start the machine using its own controls and keep clear of hot coffee. | Finger presses one unlabeled physical control; lidded carafe and basket are fully seated, hand and face clear of outlet, machine on stable counter. | Bounded A1/A2/A4; hot-liquid review, never imply one universal control. |
| `instruction_automatic_batch_automatic_batch_generic_automatic_batch_generic_observe_machine_completion_default` | Wait for your machine's own completion indicator or visible end of flow. | Full carafe under idle outlet, no stream, basket closed; a non-text status lamp may supplement but not replace the visible end-of-flow cue. | Direct A2/A4; no universal timer, beep, colour, or auto-off. |
| `instruction_automatic_batch_automatic_batch_generic_automatic_batch_generic_stir_or_swirl_carafe_default` | If it is safe for your carafe and machine, gently swirl before serving. | Lidded carafe stays low on level counter while a hand uses its handle for one small controlled swirl; no open lid, slosh, or hot-plate contact. | Inference/optional; A1 requires secure lid and handle use but does not universally prescribe swirling. |
| `instruction_automatic_batch_automatic_batch_generic_automatic_batch_generic_serve_default` ⚠ | Serve carefully from the carafe and avoid contact with hot surfaces. | Hand uses carafe handle, lid locked, and pours into a stable cup away from machine/hot plate; controlled stream and clear hand-to-hot-surface separation. | Direct A1/A4; hot-liquid/surface review. |

### Podless single-cup brewer — `automatic_batch` /
`automatic_single_cup_generic` (8)

Use one consistent unbranded single-cup archetype with a removable paper-filter
basket and separate water reservoir. Do not let it resemble a capsule/pod
machine: no pod, puncture head, capsule drawer, or branded cup.

| Exact asset ID | Exact stage instruction | Text-free composition and accuracy notes | Evidence |
| --- | --- | --- | --- |
| `instruction_automatic_batch_automatic_single_cup_generic_automatic_single_cup_generic_select_and_insert_filter_default` | Use the filter configuration specified for your particular single-cup brewer. | Removable small basket centered with one selected paper seated correctly; podless machine behind it. | Bounded A3/A4; exact filter is model-specific. |
| `instruction_automatic_batch_automatic_single_cup_generic_automatic_single_cup_generic_rinse_filter_default` | If your machine documentation calls for it, rinse the selected filter before brewing. | Selected filter and basket over sink/waste vessel as clean water wets paper; machine inactive. | Direct A3, but conditional across generic machines. |
| `instruction_automatic_batch_automatic_single_cup_generic_automatic_single_cup_generic_add_coffee_default` | Add the measured coffee from your selected recipe. | Dose cup pours grounds into selected filter; basket stable and supported; no pod or quantity marks. | Direct A3/A4. |
| `instruction_automatic_batch_automatic_single_cup_generic_automatic_single_cup_generic_level_coffee_bed_default` | Level the coffee bed gently before starting the machine. | Top-down small basket with flat bed; gentle settling motion, never tamp. | Inference from app preparation. |
| `instruction_automatic_batch_automatic_single_cup_generic_automatic_single_cup_generic_add_reservoir_water_default` | Add the selected reservoir water amount without exceeding your machine's marked limit. | User’s stable cup is already under outlet and pitcher fills reservoir below plain molded limit; visual relation makes cup capacity salient without numbers. | Direct A3/A4; limit geometry is model-specific. |
| `instruction_automatic_batch_automatic_single_cup_generic_automatic_single_cup_generic_start_machine_default` ⚠ | Start the machine using its own controls and keep clear of hot coffee. | Finger presses unlabeled control; basket and stable cup centered beneath outlet; hands clear of flow path. | Bounded A3/A4; hot-liquid review. |
| `instruction_automatic_batch_automatic_single_cup_generic_automatic_single_cup_generic_observe_machine_completion_default` | Wait for your machine's own completion indicator or visible end of flow. | Filled stable cup under idle outlet, no stream; small status lamp optional but completion must also read from stopped flow. | Direct A3/A4; no universal timer or indicator. |
| `instruction_automatic_batch_automatic_single_cup_generic_automatic_single_cup_generic_serve_default` ⚠ | Serve carefully and avoid contact with hot coffee or machine surfaces. | Hand takes cup by cool handle after flow has stopped; cup remains upright and moves away from outlet; no touch near basket or hot arm. | Direct A3/A4; hot-liquid/surface review. |

### Vietnamese phin — `restricted_flow_gravity_concentrate` /
`vietnamese_phin` (12)

Use one consistent, logo-free four-part stainless phin and broad stable cup.
The default set can accurately depict a gravity insert. A screw insert has
different geometry and use and needs a separately selected variant.

| Exact asset ID | Exact stage instruction | Text-free composition and accuracy notes | Evidence |
| --- | --- | --- | --- |
| `instruction_restricted_flow_gravity_concentrate_vietnamese_phin_vietnamese_phin_place_on_stable_cup_default` ⚠ | Place the phin on a stable cup or server before adding anything. | Empty perforated plate and chamber centered on a broad, level cup; full contact flange visible; one hand steadies cup by handle/base, not phin. | Direct P1/P4; critical stability/overflow review. |
| `instruction_restricted_flow_gravity_concentrate_vietnamese_phin_vietnamese_phin_add_coffee_default` | Add the measured coffee from your selected recipe to the phin. | Dose cup pours grounds into chamber already supported on cup; plate remains centered; no amount. | Direct P1/P4. |
| `instruction_restricted_flow_gravity_concentrate_vietnamese_phin_vietnamese_phin_level_coffee_default` | Level the coffee gently without assuming a universal packing method. | Top-down chamber with an even loose bed; hand gives a slight side-to-side shake; no tamper, hard press, or screw. | Direct P1/P4; avoids universal packing pressure. |
| `instruction_restricted_flow_gravity_concentrate_vietnamese_phin_vietnamese_phin_place_gravity_or_screw_insert_default` | Fit the gravity or screw insert exactly as documented for your selected phin. | **Split required:** gravity variant shows a loose perforated disk lowered flat; screw variant shows matching threaded post and controlled engagement. One default image cannot teach both without being wrong for one. | **Blocked** by P2’s documented mechanism difference. |
| `instruction_restricted_flow_gravity_concentrate_vietnamese_phin_vietnamese_phin_pre_wet_default` | Add the small initial pour from your selected recipe and let the coffee wet evenly. | Fine kettle stream adds a shallow layer over the selected insert; chamber stable on cup, all bed wet, large empty headspace. No fixed depth/amount. | Direct P1/P2/P4. |
| `instruction_restricted_flow_gravity_concentrate_vietnamese_phin_vietnamese_phin_fill_chamber_default` ⚠ | Add the remaining water without overfilling the phin or destabilising the cup. | Controlled kettle stream, visible water line safely below metal rim, centered plate/cup stack; hand and kettle clear of hot metal. | Direct P1/P4; overflow/burn review. |
| `instruction_restricted_flow_gravity_concentrate_vietnamese_phin_vietnamese_phin_cover_default` | Cover the phin while the coffee begins to drip. | Hand lowers plain phin lid squarely onto filled chamber by its cool outer edge; cup remains level. No decorative lid or knob not present on selected phin. | Direct P2/P4. |
| `instruction_restricted_flow_gravity_concentrate_vietnamese_phin_vietnamese_phin_observe_first_drip_default` | Observe the first drip rather than waiting for a universal time. | Tight but context-rich view of the **first single drop** suspended below phin plate over nearly empty cup; full stable stack still identifiable. No clock. | Direct P1/P3. |
| `instruction_restricted_flow_gravity_concentrate_vietnamese_phin_vietnamese_phin_check_drip_rate_default` | Check the drip rate and adjust only if your selected phin guidance supports an adjustment. | Several evenly separated descending drops and partially filled cup communicate steady flow; no hand on insert, no generic adjustment, no “fast/slow” labels. Static art cannot quantify rate, so text remains essential. | Direct P3 for variable flow; adjustment is equipment-specific. |
| `instruction_restricted_flow_gravity_concentrate_vietnamese_phin_vietnamese_phin_observe_drip_completion_default` | Observe when dripping has finished before removing the phin. | Drained bed/chamber and fuller cup; outlet is still with one final small drop or clearly no stream. Use visibly depleted water state to distinguish this from first-drip art. | Direct P1/P3/P4. |
| `instruction_restricted_flow_gravity_concentrate_vietnamese_phin_vietnamese_phin_remove_hot_filter_default` ⚠ | Remove the phin carefully by a safe handling point after dripping finishes. | Folded dry heat-resistant cloth grips broad plate edges while lifting phin vertically; other hand steadies cup by handle/base; no bare fingers on chamber. | Bounded P2/P4 plus stainless/aluminum material fact; critical hot-metal/stability review. |
| `instruction_restricted_flow_gravity_concentrate_vietnamese_phin_vietnamese_phin_collect_concentrate_default` | Collect the brewed concentrate; any serving additions remain separate from extraction. | Completed small cup of dark concentrate centered; removed phin rests safely on its inverted lid/coaster nearby; no milk, ice, sugar, or dilution. | Inference from P1/P2/P4; “concentrate” is the app output model. |

## Approval blockers and default-slot disposition

Disposition labels are release decisions, not image-quality judgments:

- **BLOCKED_DEFAULT:** no default bitmap may be approved for this slot because
  it would encode an unsupported or incompatible instruction.
- **VARIANT_REQUIRED:** generate/select only after the exact recipe and equipment
  variant is known; no universal default is release evidence.
- **CONCEPT_ONLY:** useful for design exploration, but not canonical guidance.

| Stable slot or group | Disposition | Required resolution |
| --- | --- | --- |
| All 9 `valve_release_generic` defaults | **BLOCKED_DEFAULT** | Add a named, canonical brewer profile with filter, actuator, capacity, and safe handling point; close/open are especially unsafe to generalize. |
| Clever `add_water` default | **BLOCKED_DEFAULT for `CW`**; valid only for `CC` | Make stage order recipe-driven and generate a water-first composition for `CW`. |
| Clever fixed nine-stage sequence | **VARIANT_REQUIRED** | Select `CW` or `CC`; the recipes have opposite water/coffee order and different stage grouping. |
| Hario Switch `agitate` default | **BLOCKED_DEFAULT** | No canonical `SO`, `SH`, or `SG` ordered stage supports it; omit unless a new sourced recipe adds it. |
| Hario Switch valve-visible defaults | **VARIANT_REQUIRED** | Select full immersion, hybrid phase, or gravity; a shared open/closed image is incompatible. The current slots do not cover the complete hybrid sequence. |
| Phin `place_gravity_or_screw_insert` default | **BLOCKED_DEFAULT** | Split loose gravity disc and lightly engaged threaded insert. Never generate a blended mechanism. |
| Other insert-visible phin defaults | **VARIANT_REQUIRED** | Carry the selected gravity/screw geometry, compression warning, capacity, and safe adjustment rule through the set. |
| Cezve default set | **VARIANT_REQUIRED; `CR` incomplete** | Split one-cup/single-rise and two-cup/repeated-rise profiles plus selected heat source; add the missing return-to-heat/final-rise stage for `CR`. |
| Automatic batch generic defaults | **VARIANT_REQUIRED** | Record exact cone/flat basket, paper, machine limits/control, glass/thermal carafe, and hot surfaces. `rinse_filter` is conditional, not canonical for `B5`/`B10`. |
| Automatic single-cup generic defaults | **VARIANT_REQUIRED; `CO` incomplete** | Use a named Cup-One profile with #1 paper, clear tiny outlet, centered pipe, and mug; add the missing unplug/brush-outlet maintenance stage. |
| Any `remove_and_serve` default | **CONCEPT_ONLY unless reduced to one action** | Depict removal only; serving remains in companion text or a separate stage. |
| Exact rate/capacity/timing in pixels | **BLOCKED_DEFAULT** | Keep values and observational windows in Compose text; art shows only physical state. |

A blocked default may remain registered for stable-ID compatibility, but its
review status must stay draft/unapproved and it must not satisfy release gates
until the required profile, recipe, stage, or variant selection exists.

## Human review checklist

For every generated candidate:

1. Compare brewer silhouette, filter/insert, valve state, hand placement,
   liquid level, and flow direction with the cited product/manual.
2. Inspect at 1024 × 768 and at a 300–400 dp-wide 4:3 viewport.
3. Confirm `ContentScale.Fit` keeps the entire composition visible at exact 4:3,
   with no ratio-induced letterboxing. Keep every active hand, valve, outlet,
   rim, heat source, and support footprint clear of the rounded-corner clip.
4. Reject any text-like marks, logo fragments, numbers, measurement ticks,
   impossible transparency, extra fingers, fused equipment, unsupported
   accessories, multiple actions, or decorative noise.
5. For safety-marked stages, independently verify stable support, headspace,
   hot-liquid clearance, hot-metal handling, and heat state.
6. Record exact prompt revision and generation date; keep review status
   `DRAFT`/`PENDING_REVIEW` until a human signs physical and cultural accuracy.
7. Verify the optimized WebP is 4:3, its encoded size is recorded, its
   drawable stem exactly matches the asset ID, and localized companion
   instruction/alt text resources exist.

Generated artwork remains a production input. This document is not approval to
mark any asset or P1 profile release-ready.
