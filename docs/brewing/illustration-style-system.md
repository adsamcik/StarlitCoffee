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
