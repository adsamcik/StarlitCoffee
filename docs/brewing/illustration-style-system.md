# In-app brewing illustration system

Status: draft replacement system; no asset in this document is approved or
registered for release.

## Product direction

Illustrations are the first element of an instructional stage card. A short
imperative appears directly below the image in Compose; timing, quantity,
temperature, safety, and optional detail stay outside the bitmap. The image
therefore teaches one visible action, state, or mechanism without embedded
text, labels, numbers, arrows, or UI.

The old semi-real candidates are superseded by this product direction. Their
mechanical research remains useful, but their rendering, composition, and
visual language are not reusable as a house style.

## Shared visual system

- **Medium:** clean 2D vector-like editorial raster illustration. Never a
  photoreal product scene, 3D render, clinical CAD drawing, or decorative
  poster.
- **Composition:** one centred instructional idea; 8–10% safe margin on every
  edge; generous empty field; no countertop scene or decorative props.
- **Outline:** crisp, continuous deep-espresso outline, approximately 4–6 px
  at a 1024 px source width. Use rounded joins where they do not alter device
  geometry.
- **Volume:** at most one or two restrained flat shading planes per material.
  No grain, watercolor, photographic reflection, heavy gradients, or texture.
- **Palette:** warm cream background (`#FAF3E7`), paper (`#FFFDF6`), outline
  (`#3B2518`), wood/amber (`#C78A45`), pale water/glass (`#C7DDE3`), brewed
  coffee (`#6D4028`), wet slurry (`#8B5A3C`). Use color to distinguish states,
  never as the only safety or mechanism signal.
- **Grammar:** clear water is pale/translucent; brewed coffee is darker;
  grounds are loose and granular; wet beds are denser; paper remains visibly
  thin; gas or droplets appear only when mechanics justify them.
- **Technical delivery:** retain the current opaque RGB 1024 × 768 WebP
  contract while the app supports dynamic and dark themes. Every candidate uses
  the same deliberately neutral cream artboard, so it reads as an intentional
  illustration tile rather than simulated app chrome.

## Review rule

Each illustration stays a versioned candidate outside `drawable-nodpi` until
the exact brewer, filter, state, visible flow path, mobile-size readability,
alt text, and image-first card placement have been reviewed. A styling revision
does not create a new stable asset ID or substitute for a source/equipment
review.

## Visual master 01 — Chemex bonded-filter air channel

### 1. Visual ID / title

`instruction_p1_chemex_42_700_stage_01_instruction_default` — bonded-filter
air-channel setup, `vector_v2`. The earlier `vector_v1` is retained only as
superseded provenance.

### 2. User learning objective

Understand that the thick three-leaf side of the bonded filter belongs over the
spout while leaving the spout’s air path open.

### 3. Exact moment

The dry pre-brew setup for `chemex_42_700` / `stage_01`; no coffee, water, or
pour has started.

### 4. Sources consulted

- Canonical `SRC-CHEMEX-FAQ` record and exact-stage matrix.
- Chemex [Classic Series support](https://chemexcoffeemaker.com/pages/classic-series-product-support),
  [filter support](https://chemexcoffeemaker.com/pages/filter-series-support-page),
  and [FAQ](https://chemexcoffeemaker.com/pages/faq), rechecked 1 August 2026.

### 5. Reference basis

The sources establish the six-cup hourglass silhouette, wood collar, bonded
filter cone, three leaves at the spout, one leaf opposite, and the need to
avoid sealing the spout. They are geometry/mechanism references only; no source
artwork is copied, traced, or used as a style reference.

### 6. Confirmed mechanics

- Six-cup hourglass Chemex-style carafe and bonded paper filter.
- Three paper leaves sit at the spout side; one leaf sits opposite.
- The thick side stabilises the filter and preserves an air path rather than
  sealing the spout.
- The state is dry and stable before brewing.

### 7. Assumptions / uncertainty

The candidate uses a neutral unbranded bonded-filter silhouette rather than a
specific pre-folded square or circle product. That is permissible only because
the exact stage specifies the layer relationship, not a paper SKU. Expert
review must confirm that the visible spout opening reads as an unsealed air
path at phone size rather than as a fabricated component.

### 8. Mechanical specification

Prominent left-front pouring-spout notch; exactly three broad, stepped,
closely nested folded paper edges directly over that notch; one visibly thinner
single paper layer opposite; a small real negative opening at the spout rim
remains unsealed; empty carafe; dry filter; no receiver or liquid.

### 9. Viewpoint

Elevated three-quarter close view of the upper carafe, widened enough to show
the spout, filter asymmetry, and wood collar at phone scale.

### 10. Style notes

First house-style master: warm cream artboard, espresso outline, solid pale
glass/paper/amber fills, and only restrained flat shade planes.

### 11. Superseded v1 image-generation prompt

Reference images: none were passed to the image model. The official Chemex
sources above constrained geometry and mechanism, not visual style.

> Create a polished flat 2D vector editorial coffee-app illustration of an
> empty six-cup Chemex-style hourglass carafe with a bonded-paper filter at the
> dry pre-brew setup state. Use an elevated three-quarter view on a plain warm
> cream artboard. The visible left-front pouring spout is directly under the
> three-layer paper side: draw exactly three thin staggered folded white leaves
> over that spout, with a narrow visible pale-blue glass/air gap so the paper
> cannot seal it. Draw one thin paper layer only on the opposite right side.
> Use crisp deep-espresso outlines, simple solid pale-glass/paper/amber fills,
> and only restrained flat shadows. Keep the whole upper carafe, spout, paper
> asymmetry, collar, and stable base legible in a centred 4:3 frame. Include no
> hand, water, coffee, kettle, cup, clip, text, letters, numbers, logos,
> arrows, labels, callouts, panels, clutter, photorealism, 3D rendering,
> watercolor, texture, gradient, glossy product reflection, generic cone, or
> sealed spout.

### 11a. Current v2 image-generation prompt

Prompt record: original wide 4:3 flat 2D editorial Chemex setup; cream artboard, crisp espresso outlines, three bold stepped paper leaves directly over the left-front spout, one thin leaf opposite, a small real unsealed spout opening, and no realism, text, arrows, hands, liquid, or props.

### 12. Generated image

`illustration-candidates/instruction_p1_chemex_42_700_stage_01_instruction_default/vector_v2.webp`

- Generated master: `C:\Users\adam-\.codex\generated_images\019fa48e-1d5d-7560-b0de-f39fa2e8b914\exec-d25f9a7c-8f81-48aa-88fe-0040113ce011.png`
- Delivery: opaque static RGB WebP; 1024 by 768; 78,248 bytes.
- SHA-256: `59575e1a4ee6b169bf893f6b202c48ab4eea6f01110aba5cfe60c788921c4cb9`.

Superseded v1 provenance:


`illustration-candidates/instruction_p1_chemex_42_700_stage_01_instruction_default/vector_v1.webp`
- Generated master: `C:\Users\adam-\.codex\generated_images\019fa48e-1d5d-7560-b0de-f39fa2e8b914\exec-aa7b58db-bbed-483b-b3cf-f542b6d60632.png`
- Delivery: opaque static RGB WebP, 1024 × 768, 44,316 bytes.
- SHA-256: `d386a1df39a64ebf18396c14c6b99c18cfec36d5b40ab0d40bc278e109c5d1e6`.

### 13. Superseded v1 accuracy review

The candidate is text-free, opaque RGB, 1024 × 768, and visually abandons the
prior semi-real rendering. It centres the carafe and makes the layer asymmetry
and visible spout the primary reading. The three folded edges and spout-side
relationship are visually plausible but have not been treated as mechanically
approved.

### 13a. Current v2 accuracy review

The v2 candidate is text-free, opaque RGB, 1024 by 768, and uses the intended flat editorial treatment. It makes the left-front spout notch and the three bold stepped folded edges its primary reading, removing v1's broad pale-blue air gap. The source-faithful relationship is clearer but has not been mechanically approved.

Framing exception: v2 intentionally crops the lower carafe/base so the filter
orientation and spout fill the mobile card. All stage-critical geometry retains
its own safe margin. Product review must explicitly accept this close-up
exception or require a wider replacement before any approval.

### 14. Current v2 revision targets

At 300 dp, confirm that the three folded edges remain unmistakable, the actual
spout notch and stack are unequivocally aligned, the opening reads as a real
unsealed channel, and the one-layer side remains visibly thinner.

### 15. Final state

**Candidate only — pending product-direction, brewer-mechanics, accessibility,
and placement review.** It is deliberately outside shipping resources and does
not count as exact-stage coverage.

## Visual master 02 — Hario Switch open-valve drawdown

### 1. Visual ID / title

`instruction_p1_switch_official_20_240_stage_04_instruction_default` — Hario
Switch open-valve drawdown, `vector_v4`. Versions `v1` through `v3` remain
rejected provenance only.

### 2. User learning objective

Show that moving the Switch lever opens its valve and starts drawdown into the
server; it must not read as a generic pour-over or a front-facing tap.

### 3. Exact moment

`p1_switch_official_20_240_stage_04`: the brew has steeped, the user moves the
cool control, the ball lifts from its seat, and the first coffee begins to
leave the brewer.

### 4. Sources consulted

- Canonical P1 exact-stage matrix and source register.
- Hario [V60 Immersion Dripper Switch](https://www.hario-usa.com/products/switch-immersion-dripper)
  and [European product page](https://www.hario-europe.com/collections/03-size/products/v60-immersion-dripper-switch),
  rechecked 1 August 2026.

### 5. Reference basis

The official sources establish the V60/immersion hybrid, glass bowl, silicone
base/switch, stainless-steel ball, compatible V60 02 filter, and a switch
action that starts drawdown. They constrain mechanics and equipment only; no
source artwork is copied, traced, or used as a visual-style reference.

### 6. Confirmed mechanics

- The 02-size V60 paper cone sits inside a ribbed heatproof-glass dripper.
- Moving the side switch opens the bottom valve.
- The opening state is a lifted stainless ball above its circular seat, not a
  faucet or side spout.
- Coffee drains vertically into a stable server after the valve opens.
- The user contact shown is only with the cool silicone control.

### 7. Assumptions / uncertainty

The partial base cutaway is an explanatory abstraction, not a manufacturer
cross-section. Its linkage arrangement is used solely to make the documented
switch-to-ball opening state visible. The subtle paper seam is not a required
mechanical cue and has not been independently verified.

### 8. Mechanical specification

Show a pale-blue ribbed outer glass dripper around a distinct smooth pale V60
paper cone containing wet slurry. In the base cutaway, a translucent side
trigger connects to a central lifting fork/shaft and a stainless ball held
above a circular seat; a visible annular gap feeds one central coffee stream
to a server with only a shallow initial pool.

### 9. Viewpoint

Elevated three-quarter view, with a restrained partial cross-section through
the silicone base. The filter, trigger, valve opening, and receiving server
remain readable at 300 dp without labels or arrows.

### 10. Style notes

Use the shared warm-cream, flat 2D editorial system: crisp espresso outlines,
pale glass and paper, restrained amber/brown slurry, and no texture,
photorealism, text, logos, decorative props, or app chrome.

### 11. Current v4 image-generation prompt

Create a clean flat 2D vector editorial in-app coffee illustration of the
exact moment a Hario Switch-style V60 immersion dripper opens for drawdown.
Show the smooth pale 02 V60 paper cone holding brown wet slurry inside a
separate pale-blue ribbed glass bowl. Use a clear partial cutaway of the
silicone base: a fingertip touches only the cool side trigger, the trigger
links to a central lifting shaft, and a stainless ball is visibly raised above
its circular seat so a single dark stream begins to fall into a stable server
with a shallow pool. Plain warm cream 4:3 artboard, crisp dark espresso
outlines, sparse flat shading. No text, arrows, labels, logos, kettle, steam,
countertop, generic cone, faucet, photorealism, 3D rendering, gradients, or
texture.

### 12. Candidate artifacts

All files are opaque static RGB WebP at 1024 by 768 and remain outside
`drawable-nodpi`.

- `vector_v1.webp` — 44,214 bytes; SHA-256
  `b439f3ef53514d63921c1d1364060d87133ae90dec157314857f4a055743de28`.
- `vector_v2.webp` — 68,086 bytes; SHA-256
  `62670eaa9d9b31b6cd47880254c834db8993e8c087dd7b3b21aae971c81c5386`.
- `vector_v3.webp` — 75,876 bytes; SHA-256
  `ca853652d48e6a7b06cebc61ace3a01439d28cf738b30ee8859ff174e9494380`.
- `vector_v4.webp` — 72,560 bytes; SHA-256
  `d803d1d6202356094133d99050a4c775882eac332d11e3d5e2a665678dbff265`.

### 13. Rejected versions

`v1` used misleading straight ribs, a scalloped paper shape, and a
front-tap-like valve. `v2` and `v3` failed to show a recognisable paper filter.
They are retained to make the final candidate's iterative review traceable.

### 14. Current v4 accuracy review

At mobile scale, `v4` clearly separates the smooth paper cone from the ribbed
glass, shows the trigger-to-lifting linkage, a ball visibly above its seat, an
open annular gap, and a central drawdown stream. It is text-free, quiet, and
does not suggest hot-glass contact. The filter seam is deliberately subtle;
that is non-blocking because the paper itself is unmistakable.

### 15. Final state

**Candidate may advance to formal product, brewer-mechanics, accessibility,
and placement review only.** It is not approved, registered, rendered by the
app, or counted as exact-stage coverage.

## Visual master 03 - Gravity Phin stable setup

### 1. Visual ID / title

`instruction_p1_phin_gravity_14_118_stage_01_instruction_default` - gravity
Phin stable setup. Current review candidate: `vector_v3.webp`.

### 2. User learning objective

Teach that the traditional gravity Phin must sit fully and securely on a wide,
heat-safe cup before brewing starts, with a level bed of dry coffee.

### 3. Exact brewing moment

`p1_phin_gravity_14_118_stage_01`: the empty gravity Phin is on the cup and
14 g of level coffee has been added, before water, the loose press disc, or lid.

### 4. Sources consulted

- Canonical exact-stage record:
  `app/src/main/assets/p1_exact_guidance_2026_07_27.json`.
- `SRC-NGUYEN-PHIN`, `SRC-TRUNGNGUYEN-PHIN`, and `SRC-GOURMETKAVA-PHIN` in
  the supplied source register.
- [Trung Nguyen Coffee brewing information](https://trung-nguyen-coffee.co.uk/page_brewing.php).
- [GourmetKava's Vietnamese coffee preparation guide](https://www.gourmetkava.cz/en/blog/making-coffee/preparation-of--vietnamese-coffee).

The web sources and supplied record were rechecked on 2026-08-01.

### 5. Reference images selected

Product photos and assembly views on the cited guides were used for geometry,
base-to-cup support, and the gravity-only setup. They are research references,
not copied image material.

### 6. Confirmed mechanics

- This is the loose-drop-in press-disc gravity profile, not a screw-insert one.
- The chamber rests directly on the cup; gravity will later move water through
  the perforated base.
- The coffee is level and dry. The press disc and lid are absent; no water,
  brewed coffee, or drainage exists at this stage.
- The cup fully supports the base; stable Phin and level bed are the cue.

### 7. Assumptions / uncertainty

The object is neutral and unbranded. Its small base perforations are not
directly visible in this exterior three-quarter view, though the canonical stage
requires them to be clear. Review must decide if that cue needs a later detail
view without making this setup image noisy.

### 8. Mechanical specification

Show a compact upright metal chamber with a shallow continuous flange seated
symmetrically on a broad cup rim and a visible level dry-coffee bed. Hide the
uninstalled disc and lid. Do not show a screw, linkage, cutaway, water, slurry,
bubbles, stream, or filled receiving cup.

### 9. Chosen visual approach and viewpoint

A centered elevated three-quarter exterior view makes the support relationship
and level bed legible at phone size in one simple state.

### 10. Style notes for this image

Use the house 2D editorial-vector language: warm cream background, charcoal
outlines, restrained warm-metal planes, friendly credible contours, sparse
ground texture, no text, and generous negative space. It sits above concise
stage copy in the app.

### 11. Image-generation prompt

"Clean, text-free 2D coffee-app instructional illustration on a warm cream
background: compact traditional Vietnamese gravity Phin in elevated
three-quarter view, shallow metal support fully seated on the broad rim of a
plain heat-safe cup, visible level bed of dry brown coffee in the open chamber.
Use crisp charcoal outlines, warm muted metal shading, rounded friendly vector
forms, and generous breathing room. No press disc, lid, screw insert, cutaway,
water, brewed coffee, flow, arrows, labels, branding, countertop, photorealism,
3D rendering, noisy texture, or generic cone dripper."

### 12. Candidate artifacts

All are opaque static RGB WebP at 1024 by 768 and remain outside
`drawable-nodpi`.

- `vector_v1.webp` - 80,290 bytes; SHA-256
  `83bdbd362a499638f93ae4d7bdb0d5b9649ecc735f246fe31619efc11720957d`.
- `vector_v2.webp` - 59,518 bytes; SHA-256
  `eaa7533e817de87ef2a9ab3a18a3d3b77889cf7971e8c1edfd5d450e9aec748f`.
- `vector_v3.webp` - 48,372 bytes; SHA-256
  `5f8653a9e3479f7bd70a68731e51e567bfbe21515cceb73bbb448adddd118684`.

### 13. Rejected versions

`v1` was too tall and noisy, with overemphasized outer-hole texture. `v2`
invented a large cutaway-like bridge/linkage that a gravity Phin does not have.
Both are retained for an auditable iteration trail.

### 14. Current v3 accuracy review

`v3` plainly shows the compact open chamber, level dry grounds, and stable
flange-to-cup-rim relationship. It is quiet, text-free, and appropriate for an
image-above-copy layout. Its exterior view intentionally does not expose the
perforations, so it is a review candidate rather than a completed asset.

### 15. Final state

**Candidate may advance to formal product, brewer-mechanics, accessibility, and
placement review only.** It is not approved, registered, rendered by the app,
or counted as exact-stage coverage.

## Visual master 04 - Clever water-first release

### 1. Visual ID / title

`instruction_p1_clever_water_first_15_250_stage_05_instruction_default` -
Clever water-first release. Current review candidate: `vector_v3.webp`.

### 2. User learning objective

Teach that a Clever-style steep-and-release brewer starts draining only after it is
placed level on a stable, sufficiently large server.

### 3. Exact brewing moment

`p1_clever_water_first_15_250_stage_05`: after the water-first steep and
gentle stir, the brewer is seated on the server. The bottom actuator is open and
the first flow begins. The stage copy below the image is "Place the Clever on
the server to release."

### 4. Sources consulted

- Canonical exact-stage record:
  `app/src/main/assets/p1_exact_guidance_2026_07_27.json`.
- `SRC-CLEVER-HOFFMANN` and `SRC-CLEVER-COFFEECHRONICLER` in the supplied
  source register.
- [Clever Brewers product and brew guide](https://cleverbrewing.coffee/collections/clever-manual-brewers/products/clever-dripper).
- [James Hoffmann's Clever technique](https://www.youtube.com/watch?v=RpOdennxP24).
- [Coffee Chronicler's Clever guide](https://coffeechronicler.com/clever-dripper-recipe/).

The source record and product guide were rechecked on 2026-08-01.

### 5. Reference images selected

Product and workflow views on the cited Clever guide informed the tapered,
handled plastic body, #4 paper compatibility, and server-actuated release. They
are research references only and are not copied into this asset.

### 6. Confirmed mechanics

- This recipe uses the `clever_style_bottom_actuated_dripper` profile and a
  wedge paper filter, not a V60 or Hario Switch.
- The Brewer is placed on the server to depress its bottom actuator; the stage
  state is open and flow begins immediately.
- The server must be stable and large enough. This is safety-critical guidance.
- The illustration shows a wet filter and slurry after immersion, with one
  initial coffee stream; it does not imply an exact drawdown volume or time.

### 7. Assumptions / uncertainty

Clever body and lower-valve details vary by revision. The illustration therefore
uses an unbranded, simplified transparent silhouette and only the externally
observable rim-to-actuator contact. It does not invent a cutaway, linkage, or
internal ball mechanism.

### 8. Mechanical specification

Show a tapered handled Clever-style chamber with a visibly seated wet white #4
flat-bottom wedge filter around a calm brown slurry. Its broad base rests evenly
on a broad stable glass server rim. Show a small centered underside actuator at
that contact and one narrow vertical coffee stream entering an almost-empty
server. Do not show a hand, overflow, tilt, secondary stream, or a closed valve.

### 9. Chosen visual approach and viewpoint

A centered elevated three-quarter view, biased enough toward the base to retain
the support overlap and first stream, teaches actuation and safe placement in
one quiet mobile-readable state.

### 10. Style notes for this image

Use the house clean 2D editorial-vector language: warm cream background,
uniform deep-espresso outlines, flat pale translucent-plastic and paper fills,
restrained broad shading, no text, and generous negative space. It appears
above the concise stage copy rather than duplicating it in the image.

### 11. Image-generation prompt

"Flat, text-free 2D mobile coffee-app instruction illustration: unbranded
Clever-style steep-and-release dripper, fully level on a wide stable glass
server. Show a smooth tapered handled chamber, a wet white #4 flat-bottom
trapezoid wedge filter, calm dark slurry, broad even support, visible underside
actuator contact, and one first vertical coffee stream into an almost-empty
server. Centered elevated three-quarter view, warm cream background, crisp
espresso outlines, flat warm neutral fills. No V60 ribs or cone paper, Switch
lever or ball, generic tap, cutaway, labels, arrows, photorealism, texture,
countertop, kettle, hand, scale, narrow mug, tilt, overflow, or no-flow state."

### 12. Candidate artifacts

All are opaque static RGB WebP at 1024 by 768 and remain outside
`drawable-nodpi`.

- `vector_v1.webp` - 96,080 bytes; SHA-256
  `164deeea05ee392f9798d583bdb7a271ab57325eb449b1f74d3f409539ad92ce`.
- `vector_v2.webp` - 44,306 bytes; SHA-256
  `8005660b07d9bea4135a49ece26fe9a200eaf61e77c06988a6ec4340a6932bc7`.
- `vector_v3.webp` - 50,940 bytes; SHA-256
  `3f21f63beb587fa5969d98a1968ed38df58f08cd6d18454945c50c8621c293c7`.

### 13. Rejected versions

`v1` was too rendered and visually generic, with realistic glass/coffee
texture and weak filter clarity. `v2` corrected the flat in-app style but
omitted the visible #4 wedge paper. Both remain for an auditable iteration trail.

### 14. Current v3 accuracy review

`v3` preserves the flat 2D style, shows a visible wedge paper, broad stable
support, exterior actuator contact, and exactly one beginning flow stream. The
lower contact is intentionally diagrammatic rather than an invented internal
mechanism. It remains a review candidate until product, brewer-mechanics,
accessibility, and placement review accept it.

### 15. Final state

**Candidate may advance to formal product, brewer-mechanics, accessibility, and
placement review only.** It is not approved, registered, rendered by the app,
or counted as exact-stage coverage.
