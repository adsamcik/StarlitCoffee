# Starlit Tactile style-master generation record

Date: 2026-08-02

This record preserves the exact accepted image-generation requests and the
local transparency conversion used for the preferred instruction-art style.

## Generator and reproducibility limits

- Generator: built-in `image_gen` tool.
- Model/build identifier: not exposed.
- Seed: not exposed.
- Output size from the built-in tool: 1448 by 1086.
- Session cache:
  `C:/Users/adam-/.codex/generated_images/019fa48e-1d5d-7560-b0de-f39fa2e8b914`.
- Prompt replay is stochastic. The preserved PNG must be used when an exact
  visual edit or deterministic downstream conversion is required.

Direct workspace reference paths failed under the Windows split-root sandbox.
The accepted edits therefore used recent conversation images. The raw outputs
were copied into
`docs/brewing/illustration-style-explorations/starlit-tactile-2026-08-02`
so future work can use durable local references.

## Accepted contained master

Input image: `switch_style_pilot_v2_shallow.png`.

Request option: `num_last_images_to_include = 1`.

Output:

- Cache file: `exec-e0a4c4fe-1289-457d-a9a3-16d1ada63be8.png`
- Preserved file: `switch_style_master_v1_chroma.png`
- SHA-256: `85a67d5cfc1823e8cef85b81e8c0b0deb815eafb71cdbe3350fe12dbaf2b5f6f`

Exact prompt:

    Use case: precise-object-edit
    Asset type: transparent-edge 4:3 mobile coffee-brewing instruction illustration
    Input images: Image 1 is the edit target.

    Primary request: Reframe the existing Switch illustration as a self-contained “illustration island” prepared for local background removal. Scale and reposition the complete existing subject cluster so nothing touches or is cropped by any canvas edge. Show the entire receiving server including its rounded bottom and shallow initial coffee pool. Keep the fingertip extending slightly beyond the dark stage shape, but keep the entire hand at least 7 percent away from every canvas edge.

    Backdrop edit: Keep the deep espresso organic rounded stage behind the brewer and machinery, but make its complete silhouette visible and at least 8 percent away from every canvas edge. Add a very thin warm-cream rim around the stage shape so it remains distinguishable in a dark app theme. Replace every area outside that rim with a perfectly flat, solid #00FF00 chroma-key background for local transparency removal. The #00FF00 area must be completely uniform: no shadow, gradient, texture, glow, reflection, floor plane, or lighting variation.

    Invariants: Preserve the corrected shallow server pool, single central stream, smooth paper filter and wet slurry, ribbed glass dripper, silicone base, partial cutaway, side trigger, fingertip contact, linkage, visibly lifted stainless ball, open circular valve seat, Starlit Tactile gouache/cut-paper style, warm palette, grain, geometry, and mechanical meaning. Keep the gold focus glow entirely within the dark stage shape.

    Composition: centered 4:3 landscape; all non-key pixels inside a 7 percent safe area; large enough to read at 300dp; calm asymmetric balance.

    Constraints: Change only framing, containment, stage rim, and outer background. No text, labels, numbers, arrows, logos, watermark, extra objects, extra fingers, extra streams, decorative stars, or green inside the illustration island. Do not crop the server, stage, hand, filter, or brewer.

The user selected this textured visual language over the later flat experiment.

## Accepted larger borderless master

Input images, in order:

1. `switch_style_master_v1_chroma.png` — edit target.
2. `switch_style_flat_experiment_rejected.png` — explicitly ignored.

Request option: `num_last_images_to_include = 2`.

Output:

- Cache file: `exec-eeb8cac9-1b46-4744-b470-d1b0ef571548.png`
- Preserved file: `switch_style_master_v2_chroma.png`
- SHA-256: `f0914400afc865d5cd852ba0a2308ca55fc08b1b0325e790b0659c7d73dd2248`

Exact prompt:

    Use case: precise-object-edit
    Asset type: chroma-keyed 4:3 mobile coffee-brewing instruction illustration
    Input images: Image 1 is the preferred textured Starlit Tactile edit target with the green chroma-key background and thin cream rim. Image 2 is a rejected flatter style experiment; ignore Image 2 completely and do not borrow its rendering.

    Primary request: Make exactly two composition changes to Image 1:
    1. Enlarge the complete illustration island by approximately 12 percent so it fills the 4:3 canvas more confidently at mobile size.
    2. Remove the thin warm-cream border around the dark espresso stage shape completely. The dark stage must meet the green background directly with a clean, softly rounded edge.

    Preserve: Keep the preferred textured dimensional rendering, dark espresso organic stage, brewer, paper filter, slurry, glass, cutaway mechanism, side trigger, fingertip, receiving server, coffee stream, gold focus glow, palette, lighting, texture, geometry, and mechanical meaning unchanged. Scale all non-green content together as one locked group; do not redraw or restyle individual elements.

    Containment: Keep the complete dark stage silhouette visible. Keep every non-green pixel at least 4.5 percent away from every canvas edge. The fingertip may remain outside the dark stage shape but must remain fully inside the safe area. Nothing may be cropped or touch an edge.

    Backdrop: Every area outside the dark stage and the protruding fingertip must be perfectly flat solid #00FF00 chroma-key color, with no shadow, gradient, texture, glow, halo, reflection, border, or lighting variation. Do not use #00FF00 inside the illustration island.

    Constraints: no cream/white rim; no other changes; no new objects, extra fingers, text, labels, numbers, arrows, logos, watermark, decorative elements, or edge contact.

## Transparency conversion

The chroma master was copied to
`C:/tmp/starlit_tactile_switch_scaled_source.png` and processed with the
installed image-generation helper:

    python 'C:\Users\adam-\.codex\skills\.system\imagegen\scripts\remove_chroma_key.py' --input 'C:\tmp\starlit_tactile_switch_scaled_source.png' --out 'C:\tmp\starlit_tactile_switch_scaled_alpha.png' --auto-key border --soft-matte --transparent-threshold 12 --opaque-threshold 220 --despill

Helper result:

- Detected key color: `#07ee1d`
- Transparent pixels: `666007 / 1572528`
- Partially transparent pixels: `3505 / 1572528`
- All four corner alpha values: `0`
- Preserved output: `switch_style_master_v2_alpha.png`
- SHA-256: `1af5536d0402c105d8064e8aa9143f86bbe96b70363fb97ba1d9fd6990427d4e`

## Theme and mobile validation

The RGBA output was resized with Pillow LANCZOS to 384 by 288 and alpha
composited over the app's fallback `surfaceVariant` colors:

- Light: `#F2E0D5`
- Dark: `#52443C`

Both composites retained a readable brewer, valve cutaway, stream, server,
and fingertip. The transparent perimeter had no fixed light border.

## Production prompt contract

For a new exact-stage illustration, pass the preserved borderless alpha or
chroma master as a style reference and add the following non-negotiable
contract to the stage-specific mechanics prompt:

    Create a text-free 4:3 mobile instruction illustration in the Starlit Tactile style. Place the exact equipment and action inside one deep-espresso, softly asymmetric stage shape on a flat #00FF00 chroma-key perimeter. The action element may break the stage boundary, but every non-key pixel must remain at least 4.5 percent from every canvas edge. Nothing may be cropped or terminate at the image edge. Use no outer border. Keep the subject large and readable at 300-384 dp.

    Use texture only to distinguish necessary materials or equipment states. Do not add ambient decoration, stars, floating particles, scenery, labels, arrows, redundant reflections, or extra props. Use one restrained localized gold focus treatment only when it helps locate the taught action. Preserve all stage-specific equipment states, warnings, completion cues, filter geometry, liquid quantities, and flow paths exactly.

After generation, visually inspect the chroma source, remove the key with the
recorded helper command, and inspect the alpha result at phone size on both
light and dark surfaces. Art-direction approval never substitutes for the
existing mechanics, safety, accessibility, placement, and release gates.
