# Pulsar Learn guide image prompts

Generated on 2026-08-19 with the built-in OpenAI ImageGen tool. User-supplied Pulsar
product photos were treated as visual references only. Existing Starlit Coffee Learn assets
were used as the style reference.

## Shared production prompt

> Create one text-free instructional asset for a mobile coffee brewing guide. Match the
> Starlit Coffee visual language: premium photorealistic 3D product render, black NextLevel
> Pulsar brewer, warm charcoal-brown organic blob backdrop, fully transparent outside the
> blob, soft studio lighting, centered readable silhouette, generous safe margins, no labels,
> no arrows, no numbers, no UI, and no watermark. Use a 4:3 landscape composition. Preserve
> the real Pulsar cylindrical chamber, broad black dispersion cap with shower-screen underside,
> four-footed black base, clear side valve, and product proportions from the references.

Each final prompt combined the shared production prompt with one action block below.

## Stage prompts and feedback

### 01 — Prime the paper

> Show the barrel removed and a black Pulsar base on a stable surface. A natural adult hand
> lowers a correctly sized round white Pulsar paper into the full circular recess so its edge
> fits the base with no large gap. A shallow glint of hot water reveals the radial base ridges.
> The clear valve lever is horizontal and closed. Keep the action large and immediately legible.

- Rejected `starlit_tactile_v1_rejected.png`: the paper was visibly undersized.
- Accepted `starlit_tactile_v3_alpha_fixed.png`: the approved v2 subject pixels are preserved;
  the paper fills the recess, the valve state is readable, and all disconnected outer
  background regions use genuine alpha.

### 02 — Add coffee and tare

> Show the assembled Pulsar on a clear server and black scale with the top open. A hand pours
> ground coffee into the transparent chamber onto the seated paper. The bed is beginning to
> form and the clear valve lever is horizontal and closed. Place the real dispersion cap beside
> the brewer with its shower-screen underside visible; it must not resemble a second brewer base.

- Rejected `starlit_tactile_v1_rejected.png`: the detached cap resembled another base and the
  valve orientation was ambiguous.
- Accepted `starlit_tactile_v3_alpha_fixed.png`: correct detached dispersion cap, horizontal
  closed valve, and genuine outer alpha.

### 03 — Open and pour the 60 g bloom

> Show the Pulsar assembled over a clear server on a scale. The clear valve is upright and open.
> A matte-black gooseneck kettle pours onto the fitted dispersion cap, which distributes visible
> droplets over a shallow coffee bloom. Show the first drops leaving the base. The slurry must
> remain low rather than filling the chamber.

- Rejected `starlit_tactile_v1_rejected.png`: the chamber looked too full for a 60 g bloom.
- Accepted `starlit_tactile_v3_alpha_fixed.png`: shallow bloom, open valve, cap shower, first
  drops, and genuine outer alpha agree.

### 04 — Close at the first drips

> The Pulsar is assembled on a clear server and scale after a shallow bloom. Show exactly two
> natural adult hands: one securely stabilizes the black base while the other gently moves the
> small clear valve lever into its closed horizontal position. The cap remains fitted. There is
> no kettle, pouring stream, or coffee drip after the valve closes.

- Accepted `starlit_tactile_v1.png`: both hand roles and the horizontal valve state are clear.

### 05 — Hold the retained bloom

> Show a quiet retained bloom at the one-minute wait. The assembled Pulsar sits on a clear empty
> server and black scale. The cap is fitted and the valve is unmistakably horizontal and closed.
> The chamber contains only a shallow fully wet slurry with delicate bloom bubbles. No hands,
> kettle, pouring stream, or liquid may appear below the closed brewer.

- Accepted `starlit_tactile_v2_alpha_fixed.png`: stillness, bloom bubbles, zero drawdown, and
  the clean transparent surround are clear.

### 06 — Open and pulse-pour to 340 g

> Show controlled pulse pouring after the bloom. The valve lever is upright and open. A
> matte-black gooseneck kettle pours a thin stream onto the center of the fitted cap; many small
> droplets shower the bed. Keep the slurry about 1 cm above the flat bed, never full. Show a
> narrow brewed-coffee stream into a server that already contains some dark coffee.

- Accepted `starlit_tactile_v2_alpha_fixed.png`: low slurry, even cap shower, upright valve,
  drawdown, and genuine outer alpha agree.

### 07 — Finish the drawdown

> Show the final natural drawdown with the cap fitted and the valve upright and open. The clear
> chamber is almost completely drained: a flat saturated bed rests at the bottom with no standing
> water. A thin final stream or aligned drops fall into the server of finished coffee. No kettle
> and no hands.

- Accepted `starlit_tactile_v2_alpha_fixed.png`: drained bed and final drops are distinct from
  the pulse pour, with no rasterized checker remaining.

### 08 — Lift only by the base

> Show the drained assembled Pulsar lifted a few centimeters straight upward from a stationary
> server of finished coffee. The valve is horizontal and closed. Exactly two natural adult hands
> grip only the cool black base from opposite sides; neither hand touches the hot clear chamber
> or cap. Keep a visible gap over the server and show no falling drip.

- Accepted `starlit_tactile_v2_alpha_fixed.png`: the safe grip, closed valve, gap, stationary
  server, and genuine outer alpha pass.

## Asset preparation

ImageGen returned 1448×1086 RGB files with a rasterized transparency checker. Production
preparation flood-selected every disconnected neutral checker field reachable from a safe outer
corner, converted those fields to real alpha, resized with Lanczos to 1024×768, and encoded
lossless WebP. Every packaged file was then composited on the actual guide surface and checked
for 4:3 geometry, an alpha channel, transparent outer regions, safe crop, Pulsar anatomy,
correct valve state, and agreement with its instruction.

## Transparency-correction feedback loop

After the app exposed a disconnected checker field in stage 1, two built-in ImageGen
background-extraction edits were attempted. Both prompts required changing only the visible
checkerboard to genuine alpha while preserving the original hand, paper, brewer, valve,
organic backdrop, 4:3 framing, lighting, and textures. Both edits were rejected because they
rasterized another checkerboard and changed the framing or subject pixels.

No generative edit was shipped. The accepted correction instead retains the approved candidate
pixels exactly and changes only the alpha mask of neutral checker fields connected to verified
outer-canvas seed points. A second contact-sheet review found and corrected the same mask defect
in stages 2, 3, and 5–8; all eight final assets now composite cleanly on the guide surface.
