# Starlit Tactile style-master generation record

Date: 2026-08-02

This record preserves the exact accepted image-generation requests and the
local transparency conversion used for the preferred instruction-art style.

## Locked production master

The selected production reference is
`docs/brewing/illustration-style-explorations/starlit-tactile-2026-08-02/switch_style_master_v9_fresh_expressive_alpha.png`.

V9 is the retained candidate that passes all of the selection gates together:
modern Starlit Tactile rendering, a large self-contained dark stage, clean
transparent perimeter, legibility on both light and dark app themes, granular
grounds distinct from smooth liquid, a supported centered valve ball, a
visibly hollow seat, and one continuous upstream-to-server flow path that does
not overlap solid mechanism geometry. Its asymmetric, subject-responsive stage
also replaces the rigid rounded-rectangle treatment. It is therefore the
visual and mechanical calibration reference for the remaining production queue.

Earlier revision records remain provenance and comparison material. They must
not be used as the primary style reference for new assets, and their rejected
valve topologies must not be copied. New illustrations may simplify irrelevant
internal mechanics, but any mechanism they do expose must remain physically
continuous and source-faithful at phone-card size.

## Generator and reproducibility limits

- Generator: built-in `image_gen` tool.
- Model/build identifier: not exposed.
- Seed: not exposed.
- Output size from the built-in tool: 1448 by 1086.
- Session cache filenames are recorded for provenance; cache locations are
  intentionally machine-independent in this document.
- Prompt replay is stochastic. The retained final alpha master should be used
  when an exact visual edit or deterministic downstream conversion is required.

The final master and compact reference payloads are retained under
`docs/brewing/illustration-style-explorations/starlit-tactile-2026-08-02`.
Historical raw outputs remain available through Git history; exact prompts,
hashes, and review decisions remain in this record.

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

    python '<imagegen-skill>\scripts\remove_chroma_key.py' --input '<temporary-dir>\starlit_tactile_switch_scaled_source.png' --out '<temporary-dir>\starlit_tactile_switch_scaled_alpha.png' --auto-key border --soft-matte --transparent-threshold 12 --opaque-threshold 220 --despill

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

## Grounded support revision

The user identified that the brewer appeared to float above an unrealistically
scaled receiving server. The preferred v2 alpha master was the sole edit
target. Request option: `num_last_images_to_include = 1`.

Output:

- Cache file: `exec-74c5f408-d763-4ba7-abae-334688829992.png`
- Preserved file: `switch_style_grounded_pilot_v1_chroma.png`
- SHA-256: `1eb052b5eddc77577c465c31570c5e7b368d1fc2fa9161fa1292f8bf37b81a73`

Exact prompt:

    Use case: precise-object-edit
    Asset type: chroma-keyed 4:3 mobile coffee-brewing instruction illustration
    Input images: Image 1 is the sole edit target: the preferred borderless Starlit Tactile Switch illustration.

    Primary request: Correct only the physical support relationship and realistic relative scale. The Switch brewer must no longer float above the receiving server.

    Required geometry:
    - Move and proportion the receiving glass server so its upper rim sits directly beneath and visibly supports the broad silicone base/flange of the Switch brewer. The base must overlap/rest on the server rim as real brewing equipment would; there must be no open-air gap between them and no invisible stand.
    - Keep the explanatory valve cutaway visible. Show the single coffee stream continuing from the open valve down inside the transparent server, visible through its glass.
    - Make the receiving server realistically proportioned to the brewer: its mouth should be about 60–70 percent of the base width, its body about 80–90 percent of the base width, and its height below the rim about two-thirds of the dripper height. It should read as a normal stable coffee server, neither miniature nor oversized.
    - Show only a very shallow initial coffee pool at the absolute bottom, no more than 6–8 percent of the server-bowl height.
    - Reduce the fingertip slightly if needed so it reads at a believable scale against the side trigger, while preserving clear contact with only the cool control.

    Teaching hierarchy: first the fingertip moving the side trigger; second the lifted stainless ball above the open circular seat; third the single stream entering the supported server. The equipment must look stable, load-bearing, and physically plausible.

    Invariants: Preserve the preferred textured dimensional Starlit Tactile rendering, elevated three-quarter view, exact paper filter and wet slurry, ribbed glass dripper, dark silicone base, trigger linkage, lifted ball, open seat, one stream, server handle, deep espresso organic stage, localized gold focus light, warm palette, lighting, and material textures. Do not change the taught valve action.

    Containment and backdrop: Keep the complete dark stage, brewer, server, and hand fully visible. The fingertip may break outside the dark stage, but every non-green pixel must remain at least 4.5 percent from every canvas edge. Nothing may be cropped or touch an edge. Use no outer border. Every area outside the dark stage and protruding fingertip must be perfectly flat solid #00FF00 chroma-key color with no shadow, gradient, texture, glow, halo, reflection, or lighting variation. Do not use #00FF00 inside the illustration island.

    Constraints: no floating brewer, no visible gap between base and server rim, no stand, no countertop, no extra equipment, no extra fingers, no extra streams, no deep coffee fill, no text, labels, numbers, arrows, logos, watermark, decorative stars, noise, or edge contact. Change only support geometry, receiving-server scale, shallow pool, and fingertip scale.

## Grounded scale revision

The grounded pilot was enlarged after phone-size QA. Input order used
`num_last_images_to_include = 3`: Image 1 was the chroma edit target; Images 2
and 3 were light/dark QA previews and explicitly ignored.

Output:

- Cache file: `exec-d2d9e377-4feb-41da-9cd0-c28681b7d58f.png`
- Preserved file: `switch_style_master_v3_grounded_chroma.png`
- SHA-256: `c2aff9335ac7cad488a20354a2b2f79c740111cbe5f984b94cce8c16d3dfef20`

Exact prompt:

    Use case: precise-object-edit
    Asset type: chroma-keyed 4:3 mobile coffee-brewing instruction illustration
    Input images: Image 1 is the green-background grounded Switch revision and is the sole edit target. Images 2 and 3 are light/dark QA previews only; ignore them completely.

    Primary request: Enlarge the complete illustration island from Image 1 by approximately 10 percent so it regains the confident mobile-card scale of the prior preferred master.

    Change only uniform scale and centering. Treat the dark stage, supported brewer, server, hand, mechanism, and all lighting as one locked group. Preserve exactly:
    - the silicone base visibly resting on and overlapping the receiving server rim;
    - no air gap and no floating equipment;
    - the realistic server-to-brewer proportions;
    - the single stream running inside the glass server;
    - the shallow initial pool;
    - the fingertip scale and contact with the cool side trigger;
    - the lifted stainless ball, open circular seat, linkage, paper filter, slurry, glass, textures, gold focus glow, warm palette, and Starlit Tactile rendering.

    Containment: Keep every non-green pixel at least 4.5 percent away from every canvas edge. Keep the complete dark-stage silhouette, server bottom, brewer top, and hand visible. Nothing may be cropped or touch an edge.

    Backdrop: Preserve a perfectly flat solid #00FF00 chroma-key perimeter outside the dark stage and protruding fingertip. No border, halo, shadow, gradient, texture, reflection, or lighting variation in the green area. Do not use #00FF00 inside the illustration island.

    Constraints: no geometry changes, no redrawing, no restyling, no floating brewer, no server resizing independent of the brewer, no deepened pool, no new objects, extra fingers, extra streams, text, labels, numbers, arrows, logos, watermark, decoration, noise, or edge contact.

## Grounded v3 transparency and QA

Conversion command:

    python '<imagegen-skill>\scripts\remove_chroma_key.py' --input '<temporary-dir>\starlit_tactile_switch_grounded_scaled_source.png' --out '<temporary-dir>\starlit_tactile_switch_grounded_scaled_alpha.png' --auto-key border --soft-matte --transparent-threshold 12 --opaque-threshold 220 --despill

Helper result:

- Detected key color: `#0ae41c`
- Transparent pixels: `848807 / 1572528`
- Partially transparent pixels: `3312 / 1572528`
- All four corner alpha values: `0`
- Preserved output: `switch_style_master_v3_grounded_alpha.png`
- SHA-256: `089e215658f2ee6d7cf69b51eee0ef6894072944d3a5875319429b0b65339b50`

The alpha output was inspected at 384 by 288 over `#F2E0D5` and `#52443C`.
Both theme previews preserved visible base-to-rim contact, plausible relative
scale, the lifted-ball cutaway, single internal stream, shallow pool, and clear
finger-to-trigger action.

## Modern mechanics and rendering revision

Research against Hario's current product pages and 2024 catalog confirmed the
functional story but not the earlier illustrated fork-and-shaft linkage. The
confirmed parts and behavior are a glass bowl, silicone base, PCT-resin switch,
stainless-steel ball that blocks flow, and drawdown after the switch is
pressed. Hario states that dripping continues after the finger is released.
The v4 image is therefore an explanatory functional cutaway, not a literal
manufacturer cross-section.

Primary sources:

- `https://www.hario-usa.com/products/switch-immersion-dripper`
- `https://www.hario-europe.com/collections/03-size/products/v60-immersion-dripper-switch`
- `https://www.hario.cc/PDF/pdf2024Eng/4.COFFEE_P48-83.pdf`
- `https://www.hario-usa.com/products/switch-server-set`

Direct local-reference handoff failed under the Windows split-root sandbox, so
the modern seed was generated from the verified scene specification. Each
subsequent edit used the immediately preceding conversation image with
`num_last_images_to_include = 1`.

### Rejected modern seed

- Cache file: `exec-c0d24f2d-4dee-4a89-ad40-73b19e24c963.png`
- Preserved file: `switch_style_modern_seed_rejected.png`
- SHA-256: `a1465b95a4b30c776a998a2f729d7a1bdd7a26508a9deb89f414d1242f3be4cc`
- Rejection: too close to product photography and invented a ball-on-post,
  piston, and disk assembly.

Exact prompt:

    Use case: stylized-concept
    Asset type: text-free in-app coffee-brewing instruction illustration for a compact mobile learning card
    Primary request: A clear functional cutaway of a Hario-style glass immersion switch dripper at the exact moment drainage begins. The glass cone with white paper filter, wet coffee bed and amber brew water is seated directly and physically on the rim of a proportionally sized clear glass coffee server; nothing floats. A single simplified fingertip presses the cool external switch lever. Inside the dark silicone base, show only the confirmed functional relationship: the lever's short inner end has lifted a small stainless-steel ball just clear of the circular outlet seal, allowing one clean amber stream to pass through the outlet into a shallow, correctly scaled pool in the server. Treat this as a simplified explanatory cutaway, not a literal engineering cross-section.
    Style/medium: contemporary high-fidelity stylized 3D editorial illustration aligned with polished Material 3 Expressive UI; precise CAD-like simplified geometry; soft matte materials; controlled translucent glass; broad intentional highlights; crisp layered silhouettes; subtly rounded friendly proportions; refined but unmistakably illustrated, not photorealistic.
    Composition/framing: portrait 4:3 asset; three-quarter front view; apparatus large and centered while fully contained; base rests convincingly on server rim; fingertip may extend slightly beyond the dark stage but remains well inside the outer image boundary; at least 4.5 percent clear perimeter on every side; strong readability at 384 by 288 pixels.
    Color palette: near-black warm espresso rounded-rectangle stage behind the apparatus; warm cream paper, amber coffee, muted cobalt switch accent, restrained coral fingertip, stainless-steel ball. The dark stage provides contrast in both light and dark app themes.
    Scene/backdrop: outside the dark rounded stage, perfectly flat uniform #00ff00 for local chroma-key removal. No shadow, gradient, texture, reflection, floor plane, or lighting variation outside the stage.
    Constraints: no text, numbers, labels, arrows, icons, logos, white border, frame, ornament, extra hardware, detached parts, steam, beans, plants, splashes, or watermark. Nothing touches the outer canvas edges. No invented fork, shaft, spring, hinge maze, or elaborate linkage. No use of #00ff00 inside the subject. Crisp antialiased edges.
    Avoid: retro poster, 1940s advertising, gouache, watercolor, paper grain, print texture, stipple, halftone, sepia, vintage palette, hand-painted brushwork, flat mid-century illustration, photorealism, camera noise, lens effects, realistic skin pores, busy reflections, visual noise.

### Mechanically restrained intermediate

- Cache file: `exec-196b5033-33ba-49e6-9fb5-808203581760.png`
- Preserved file: `switch_style_modern_mechanics_intermediate_chroma.png`
- SHA-256: `687f100d36977f4b1ed326799cb54f7098961db3ea1bf051a026b71226c32ce6`

Exact prompt:

    Use case: precise-object-edit
    Input image: Image 1 is the edit target.
    Primary request: Change only the exposed valve cutaway. Remove the vertical metal post, piston, disk, gasket stack, circular pivot cap, and all extra mechanical hardware. Show one loose stainless-steel sphere and one simple cobalt switch lever only: the lever's short inner tip directly nudges the loose sphere upward and slightly sideways, leaving a visibly open plain circular drainage aperture beneath it. Coffee flows through that aperture. The sphere is not mounted to anything. No other internal parts are visible. This is a simplified functional diagram, not literal manufacturer internals.
    Preserve unchanged: framing, grounded brewer on server rim, proportions, glass cone, paper filter, coffee bed, server, finger, stream, shallow pool, dark rounded stage, chroma background, outer safe area, and all other object geometry.
    Constraints: no post beneath the ball; no stem; no pedestal; no piston; no disk; no fork; no shaft; no spring; no hinge maze; no extra screw or ring; no text, arrows, labels, logos, border, decoration, or watermark. Keep the #00ff00 outer background perfectly flat and uniform with no lighting variation. Nothing touches the outer canvas edge.

### Accepted modern master

- Cache file: `exec-41cc5705-84e8-4d81-accd-34d0458d83e8.png`
- Preserved chroma file: `switch_style_master_v4_modern_chroma.png`
- Chroma SHA-256: `078ee9e8c5086c51a4836d9c0b34ae6662d8b39537dde83bd307928e0ea7d973`

Exact prompt:

    Use case: style-transfer
    Input image: Image 1 is the edit target.
    Primary request: Change only the rendering style from near-photorealistic product visualization to a contemporary high-fidelity stylized 3D editorial illustration for a polished Material 3 Expressive mobile app.
    Style/medium: vector-3D fusion; crisp simplified CAD-like forms; deliberately reduced material detail; smooth matte color planes; controlled translucent glass represented by two or three broad clean highlight shapes; precise soft tonal gradients; subtle ambient depth; rounded expressive geometry; premium modern app-illustration finish. Clearly illustrated at first glance, never mistaken for a photograph.
    Preserve unchanged: exact subject geometry, functional relationship, loose steel ball lifted by the lever tip, open aperture, coffee stream, composition, scale, camera angle, grounded contact on server rim, finger pose, proportions, color assignments, dark rounded stage, flat green outer background, safe area, and absence of text.
    Reduce: microtexture in coffee, paper, glass, metal, silicone and skin; realistic reflections; surface pores; tiny bubbles; optical caustics; photographic depth and glare. Simplify the coffee bed and liquid into clean readable layered shapes while retaining the wet bed and drainage action.
    Constraints: no text, labels, numbers, arrows, icons, logos, border, ornament, additional parts, scene props, or watermark. Keep the outside #00ff00 perfectly uniform and flat. Nothing touches the canvas edge.
    Avoid: photorealism, product photography, ray-traced realism, skin pores, lens effects, camera noise, glossy showroom render, retro poster, mid-century advertising, 1940s illustration, gouache, watercolor, paper grain, print texture, halftone, stipple, sepia, vintage palette, flat clip art.

### V4 transparency and QA

Conversion command:

    python '<imagegen-skill>\scripts\remove_chroma_key.py' --input 'docs\brewing\illustration-style-explorations\starlit-tactile-2026-08-02\switch_style_master_v4_modern_chroma.png' --out 'docs\brewing\illustration-style-explorations\starlit-tactile-2026-08-02\switch_style_master_v4_modern_alpha.png' --auto-key border --soft-matte --transparent-threshold 12 --opaque-threshold 220 --despill

Helper and integrity results:

- Detected key color: `#10e012`
- Transparent pixels: `460882 / 1572528`
- Partially transparent pixels: `4065 / 1572528`
- Alpha bounding box: `(179, 32, 1268, 1055)` on a 1448 by 1086 canvas
- Minimum transparent perimeter: approximately `2.9%`
- All four corner alpha values: `0`
- Preserved alpha file: `switch_style_master_v4_modern_alpha.png`
- Alpha SHA-256: `ed38072305320e57af73d0869e7e046a312c3df9733155b693ea65bd5c3ccaf6`

The approximately 2.9% stage perimeter intentionally lets the apparatus read
larger than the earlier 4.5% contract while retaining a complete transparent
edge and no cropped content. Production promotion still requires stage-specific
mechanics review. The alpha master was also reduced to 384 by 288 and
composited over `#F2E0D5` and `#52443C`; its teaching hierarchy and transparent
corners remained legible in both theme previews.

## Continuous-flow correction

The user correctly identified that v4 showed the downstream stream without a
clearly visible upstream path through the base. The official Hario manual was
downloaded from `https://www.hario.cc/Items/manual_pdf/SSD.pdf`, rendered, and
reviewed together with its extracted English assembly text. It states that hot
water flows from the bottom if the stainless-steel ball is not in place, and
describes inserting the switch's long narrow end into the base. This supports a
simple gravity passage controlled by the loose ball, but does not publish a
literal internal cross-section.

Input image: `switch_style_master_v4_modern_chroma.png`, reloaded into the
conversation as the sole edit target. Request option:
`num_last_images_to_include = 1`.

Output:

- Cache file: `exec-b43daf68-fe93-4be1-b77d-11652d62b1fe.png`
- Preserved chroma file: `switch_style_master_v5_continuous_flow_chroma.png`
- Chroma SHA-256: `48c4b2019987be52233618192bfb3635005817d2da65414c7f6d4f868b3e1c43`

Exact prompt:

    Use case: precise-object-edit
    Asset type: text-free 4:3 in-app coffee-brewing instruction illustration
    Input image: Image 1 is the sole edit target, the accepted modern Hario Switch style master.
    Primary request: Correct only the liquid-flow continuity inside the existing central cutaway. The amber brewed liquid must form one visibly uninterrupted gravity path from the pointed bottom of the paper-filtered V60 cone, through the central opening in the glass bowl, down the short central cavity of the silicone base above the steel ball, around the lifted loose ball, through the plain circular bottom outlet, and into the existing falling stream. Show a narrow amber column entering the valve cavity from directly above. At the lifted ball, show the liquid dividing naturally into two small clean amber ribbons around the sides of the sphere, then rejoining at the open outlet and continuing as the existing single stream. There must be no gap, teleportation, hidden jump, second reservoir, or disconnected stream.
    Mechanical accuracy: preserve one loose stainless-steel ball only. It remains displaced upward and slightly sideways by the long narrow inner end of the flush cobalt switch. The ball is not mounted to a post. The base contains only a simple central gravity passage and circular outlet—no pipework, pump, piston, disk, fork, shaft, spring, or invented linkage. Keep this an explanatory functional cutaway, not a manufacturer engineering cross-section.
    Preserve unchanged: exact overall composition, modern vector-3D editorial style, camera angle, large mobile-readable scale, grounded base seated on server rim, glass and paper geometry outside the narrow central cutaway, coffee bed and liquid level, finger and switch position, server, shallow collected pool, dark rounded stage, flat green perimeter, palette, lighting, and all exterior silhouettes.
    Backdrop: keep the outer #00ff00 background perfectly flat and uniform for local removal, with no shadows, gradients, texture, reflections, or lighting variation.
    Constraints: change only the central liquid path and the minimal cutaway needed to expose it. No text, labels, arrows, icons, bubbles, splashes, extra streams below the outlet, extra hardware, border, ornament, logo, or watermark. Nothing touches the outer canvas edges. Do not use #00ff00 inside the subject.

Transparency conversion:

    python '<imagegen-skill>\scripts\remove_chroma_key.py' --input 'docs\brewing\illustration-style-explorations\starlit-tactile-2026-08-02\switch_style_master_v5_continuous_flow_chroma.png' --out 'docs\brewing\illustration-style-explorations\starlit-tactile-2026-08-02\switch_style_master_v5_continuous_flow_alpha.png' --auto-key border --soft-matte --transparent-threshold 12 --opaque-threshold 220 --despill

Helper and integrity results:

- Detected key color: `#0fdc17`
- Transparent pixels: `460918 / 1572528`
- Partially transparent pixels: `4080 / 1572528`
- Alpha bounding box: `(179, 32, 1268, 1055)` on a 1448 by 1086 canvas
- All four corner alpha values: `0`
- Preserved alpha file: `switch_style_master_v5_continuous_flow_alpha.png`
- Alpha SHA-256: `51b819283203fa3ca720ff6cf708257c831e3a891850f5b6b875a4fb8be2e4bc`

Phone-size QA reduced the alpha master to 384 by 288 and composited it over
`#F2E0D5` and `#52443C`. Both previews retained the narrow upstream amber
channel above the ball, the two short side paths around the displaced ball,
the rejoined outlet stream, transparent corners, and the existing teaching
hierarchy.

## Ground-coffee material correction

The user requested enough ground-coffee texture to distinguish the particulate
coffee mass from the smooth brew water. The v5 chroma master was reloaded into
the conversation as the sole edit target. Both edits used
`num_last_images_to_include = 1`.

### Rejected bean-like texture seed

- Cache file: `exec-e9309261-fbff-4c0c-9864-da5105e23b7c.png`
- Preserved file: `switch_style_ground_texture_seed_rejected_chroma.png`
- SHA-256: `f1ac8690d7ccd0c94d6b4610bd126a32220ce7dd7a7f2e1b3b1814cee0456a8b`
- Rejection: repeated oval forms and center grooves read as whole coffee beans,
  not ground coffee.

Exact prompt:

    Use case: precise-object-edit
    Asset type: text-free 4:3 in-app coffee-brewing instruction illustration
    Input image: Image 1 is the sole edit target, the accepted v5 continuous-flow Switch master.
    Primary request: Change only the visible ground-coffee mass at the top of the brew. Give the wet grounds a restrained but unmistakably granular texture so they cannot be confused with liquid: irregular medium-fine coffee particles, small moist clumps, a gently uneven porous surface, and a slightly ragged granular edge where the grounds meet the brew water. Use a controlled range of deep and medium coffee browns with a few broad soft highlights indicating moisture. Make the particle clusters large and simplified enough to remain legible at 384 by 288 pixels.
    Material hierarchy: the grounds must read as opaque, particulate, damp coffee; the brew water beneath and around them must remain smooth, translucent, continuous amber liquid with no particle texture. Texture is localized only to the ground-coffee mass.
    Preserve unchanged: exact ground-coffee quantity and position; liquid level; paper filter; glass dripper; continuous amber flow path from filter tip around the lifted ball through the outlet; ball, switch, finger, base, server, shallow pool, composition, proportions, camera angle, modern vector-3D editorial style, dark rounded stage, flat green perimeter, lighting, and all exterior silhouettes.
    Backdrop: keep the outer #00ff00 background perfectly flat and uniform for local removal, with no shadows, gradients, texture, reflections, or lighting variation.
    Constraints: change only coffee-ground material texture. Do not add foam, crema, large bubbles, beans, floating debris, sediment in the server, texture in the liquid, paper grain, background noise, halftone, stipple, vintage print texture, text, labels, arrows, icons, logo, border, ornament, or watermark. Do not make the grounds photorealistic or noisy. Nothing touches the canvas edges. Do not use #00ff00 inside the subject.

### Accepted ground-coffee correction

- Cache file: `exec-7904ca40-1bfc-4ee5-b93b-62107f6941f5.png`
- Preserved chroma file: `switch_style_master_v6_ground_texture_chroma.png`
- Chroma SHA-256: `766672eece22a6550c440fc5d7f7a8a873c4b1b6f01cb5eee75ab289eb2e2d3e`

Exact prompt:

    Use case: precise-object-edit
    Input image: Image 1 is the edit target.
    Primary request: Correct only the coffee-ground texture. Replace every bean-like shape with genuinely ground coffee: much smaller irregular angular crumbs, tiny fractured particles, and a limited number of softly merged damp clumps. No individual particle may resemble a whole or partial coffee bean. Remove all oval bean silhouettes, center grooves, paired lobes, polished shells, and repeated seed shapes. The surface should read as a moist medium-fine coffee crust, not a pile of beans.
    Rendering: use simplified clustered texture with low-to-moderate visual density, broad dark-brown massing, subtle size variation, restrained highlights, and enough relief to remain distinct from the perfectly smooth liquid at 384 by 288 pixels. Keep the granular edge slightly uneven but not jagged or noisy.
    Preserve unchanged: exact coffee quantity and position; smooth amber liquid; paper filter; glass; continuous flow path above, around, and below the ball; loose ball; switch; finger; base; server; shallow pool; composition; modern vector-3D style; dark stage; green perimeter; geometry; lighting; and all other materials.
    Constraints: no whole beans, bean fragments, bean-shaped ovals, center creases, foam, bubbles, splashes, floating grounds, server sediment, micro-noise, photorealism, vintage texture, paper grain, text, labels, arrows, border, logo, or watermark. Change only the ground-coffee material.

Transparency conversion:

    python '<imagegen-skill>\scripts\remove_chroma_key.py' --input 'docs\brewing\illustration-style-explorations\starlit-tactile-2026-08-02\switch_style_master_v6_ground_texture_chroma.png' --out 'docs\brewing\illustration-style-explorations\starlit-tactile-2026-08-02\switch_style_master_v6_ground_texture_alpha.png' --auto-key border --soft-matte --transparent-threshold 12 --opaque-threshold 220 --despill

Helper and integrity results:

- Detected key color: `#12d014`
- Transparent pixels: `461839 / 1572528`
- Partially transparent pixels: `3954 / 1572528`
- Alpha bounding box: `(179, 32, 1268, 1055)` on a 1448 by 1086 canvas
- All four corner alpha values: `0`
- Preserved alpha file: `switch_style_master_v6_ground_texture_alpha.png`
- Alpha SHA-256: `084d79887fab1f27890c7d6917c003b647d98b3c5a1c377784741eeaa03f89ea`

Phone-size QA reduced the alpha master to 384 by 288 and composited it over
`#F2E0D5` and `#52443C`. The irregular grounds remained visibly particulate in
both previews; the brew water, valve flow, and server pool remained smooth and
visually distinct. The continuous flow path and transparent corners were also
preserved.

## Verified ball-valve physics correction

A closer visual review found that v6 incorrectly implied the external switch
pushed the ball from the side. The official Hario assembly diagram shows the
long narrow end of the same molded switch entering the base and the installed
switch moving up and down. The accepted explanatory interpretation is a
one-piece seesaw: pressing the outer handle down raises its inner end beneath
the loose ball. The ball remains centered, lifts only slightly above a smaller
circular seat, and exposes an annular gravity-flow opening. This is a
source-constrained functional explanation, not a claim about Hario's
unpublished internal dimensions.

Source reviewed:

- Official Hario Switch manual:
  `https://www.hario.cc/Items/manual_pdf/SSD.pdf`

Input image:
`switch_style_master_v6_ground_texture_chroma.png`, reloaded into the
conversation as the sole edit target after direct local-reference handoff was
blocked by the Windows split-root sandbox.

Request option: `num_last_images_to_include = 1`.

Output:

- Cache file: `exec-6f87cdca-35f1-4e6d-a96a-975e78313a17.png`
- Preserved chroma file:
  `switch_style_master_v7_verified_ball_physics_chroma.png`
- Chroma SHA-256:
  `ca8a781f7134155685582cd36aa79ce64ddd8347c6e65bb65f48fb5c70d6a8bf`

Exact prompt:

    Use case: precise-object-edit
    Asset type: text-free 4:3 in-app coffee-brewing instruction illustration
    Input image: Image 1 is the sole edit target. It is the accepted textured-ground Hario Switch instructional master. Preserve it except for the valve cutaway described below.

    PRIMARY REQUEST
    Replace only the exposed valve-mechanism cutaway inside the dark silicone base. Rebuild that small area as one mechanically coherent vertical section through the centerline of the glass-bowl outlet, stainless-steel ball, circular valve seat, one-piece PCT switch lever, and bottom outlet. This frame shows the OPEN and draining state at the instant the user has pressed the broad external handle down until it sits flush with the upper rim of the silicone base.

    COORDINATE SYSTEM AND SPATIAL RELATIONSHIPS
    - Vertical means gravity and downstream flow.
    - The glass brewer, filter, coffee bed, and upstream brew liquid are above.
    - The receiving server and downstream outlet are below.
    - The user-facing external switch handle extends to the right.
    - The sphere, circular seat, throat, bottom outlet, and falling stream must remain centered on one vertical axis beneath the brewer.
    - The cutaway is a simplified functional explanation, but every visible contact, support, passage, and motion must be physically coherent.

    ONE-PIECE SWITCH LEVER — CRITICAL
    - The cobalt-blue switch is exactly one continuous molded PCT-resin lever, not several connected pieces.
    - Its broad outer handle remains on the right and is shown pressed down and flush by the fingertip.
    - Inside the base, that very same blue molded part narrows into one long, slender inner arm running inward toward the central valve.
    - The lever rotates around one small molded fulcrum or convex bearing seated in a matching recess of the silicone base near the base wall.
    - This is a seesaw relationship: pressing the outer handle downward rotates the outer arm down while rotating the inner arm upward.
    - The upward-moving inner tip reaches the ball from BELOW and contacts the underside of the loose stainless-steel sphere.
    - The inner tip visibly supports and lifts the ball from below.
    - The inner tip must never push the ball from its side.
    - Do not invent a separate shaft, fork, piston, vertical post, spring, screw, axle, disk, hinge assembly, cradle, cage, or multi-part linkage.
    - Do not make the ball look attached to the lever.

    BALL MOTION AND VALVE SEAT — CRITICAL
    - Use exactly one loose stainless-steel sphere.
    - Keep it centered directly above one circular valve seat and outlet on the brewer's vertical axis.
    - The seat is a simple circular opening smaller in diameter than the sphere. In the closed state gravity would let the sphere rest on this seat and seal it.
    - This image is the open state: the rising inner lever tip lifts the ball nearly straight upward by only a small distance, approximately 10–15 percent of the ball diameter.
    - The ball remains nearly centered over the seat. Do not show conspicuous sideways displacement.
    - Show a clear, even annular opening between the ball's lower hemisphere and the circular seat.
    - The ball is mechanically supported from below by the lever tip. It must not appear to float.
    - No stem passes through the ball. No post holds it from beneath other than the contacting tip of the one-piece lever.

    LIQUID PHYSICS — CRITICAL
    - Smooth amber filtered coffee descends continuously from the pointed filter and glass throat into the compact upstream valve chamber above the seat.
    - That upstream chamber visibly contains liquid around the sides of the wet steel ball.
    - Liquid must not pass through the sphere, through the lever, or through solid silicone.
    - With the ball raised, gravity drives liquid inward and downward through the 360-degree annular gap between the sphere and the circular seat.
    - In the sectional cutaway, depict that ring flow as one continuous amber crescent wrapping around the underside of the ball, not as two unrelated decorative ribbons.
    - Immediately below the seat, all of that liquid converges into one centered vertical outlet passage.
    - That outlet continues directly into the existing single falling stream entering the server.
    - Every amber segment must visibly connect: upstream liquid chamber → annular gap around the underside of the ball → centered passage below the seat → one falling stream.
    - No teleporting liquid, disconnected amber patches, lateral pipes, two independent side streams, pumps, siphons, hidden reservoirs, or liquid appearing below the ball without a visible upstream route.

    CUTAWAY PRESENTATION
    - Reveal only a compact central section of the dark silicone base.
    - Make the silicone wall, liquid-filled chamber, circular seat, cobalt lever arm, steel ball, annular amber path, and centered lower outlet visually distinct at small mobile-card size.
    - Use clean explanatory sectional geometry rather than decorative plumbing.
    - Do not add arrows, labels, letters, numbers, symbols, legends, or callouts.
    - Do not claim an exact proprietary manufacturer cross-section; show only the verified functional relationship.

    PRESERVE UNCHANGED
    - Preserve the exact overall composition, viewpoint, crop, scale, and modern high-fidelity vector-3D rendering style of Image 1.
    - Preserve the textured damp medium-fine coffee grounds; they must remain visibly granular and distinct from the smooth amber liquid.
    - Preserve the paper filter, transparent glass cone, liquid level, glass ribs, and upstream liquid path.
    - Preserve the fingertip pose and the outer handle's down/flush position.
    - Preserve the dark silicone base resting firmly on the server rim, the receiving server, and its shallow amber pool.
    - Preserve the contained dark stage, transparent-ready perimeter composition, palette, lighting, shadows, and silhouettes.
    - Nothing should touch the canvas edge.

    BACKDROP
    - Outside the contained dark stage, use a perfectly flat uniform chroma green #00FF00 with no gradient, texture, noise, shadow, spill, or variation so it can be removed cleanly.

    NEGATIVE CONSTRAINTS
    No side-pushed ball. No horizontally displaced ball. No floating ball. No ball-on-post. No fork under ball. No multi-part linkage. No exaggerated lift. No two independent amber ribbons. No disconnected flow. No extra mechanism. No text. No arrows. No logo. No border. No white outline. No vintage poster treatment. No photorealism. No visual noise. No unnecessary props. No change outside the compact valve cutaway.

Transparency conversion:

    python '<imagegen-skill>\scripts\remove_chroma_key.py' --input 'docs\brewing\illustration-style-explorations\starlit-tactile-2026-08-02\switch_style_master_v7_verified_ball_physics_chroma.png' --out 'docs\brewing\illustration-style-explorations\starlit-tactile-2026-08-02\switch_style_master_v7_verified_ball_physics_alpha.png' --auto-key border --soft-matte --transparent-threshold 12 --opaque-threshold 220 --despill

Helper and integrity results:

- Detected key color: `#0de211`
- Transparent pixels: `461129 / 1572528`
- Partially transparent pixels: `4047 / 1572528`
- Alpha bounding box: `(179, 32, 1268, 1055)` on a 1448 by 1086 canvas
- All four corner alpha values: `0`
- Preserved alpha file:
  `switch_style_master_v7_verified_ball_physics_alpha.png`
- Alpha SHA-256:
  `5371d8ce80c8923b177d912880e9317a8efc31d65fe2e29e5c9dd0eca2ca3852`

Phone-size QA reduced the alpha master to 384 by 288 and composited it over
`#F2E0D5` and `#52443C`. In both previews, the external handle read as
pressed, the inner lever remained visible beneath the centered ball, the
annular amber passage stayed connected to the single outlet stream, the
grounds remained particulate, and the transparent perimeter remained clean.

## Hollow-bore flow-topology correction

V7 still allowed the liquid and the broad inner lever to occupy the same visual
volume. A direct-edit strategy repeatedly preserved that malformed topology, so
the accepted workflow separated physics verification from scene rendering:

1. Review the official Hario manual and a real disassembled-parts photograph.
2. Generate a standalone valve truth model.
3. Reject its solid-disk seat.
4. Correct the truth model to a hollow annular seat and continuous bore.
5. Use the corrected truth model with v7 to rebuild only the polished cutaway.

Research references:

- Official Hario manual:
  `https://www.hario.cc/Items/manual_pdf/SSD.pdf`
- Disassembled-parts photograph:
  `https://www.poru-coffee.com/wp-content/uploads/04_hario-switch-parts.jpg`
- Local research crop used for visual review only:
  `C:/tmp/hario-switch-lever-reference-crop.jpg`
- Crop rectangle on the 1920 by 1080 source: `(480, 560, 1450, 1080)`

Rejected direct-edit cache files, not preserved:

- `exec-50aae6ae-1e37-43a6-8f0b-f65ddfd1a6b5.png`: retained
  equator contact and an amber bowl.
- `exec-b4b95649-499f-4708-bee2-fb10fe94deb5.png`: retained
  equator contact and removed useful upstream continuity.
- `exec-a62663f5-93e7-452d-b734-eaf5a7dc26d8.png`: real-parts
  reference improved topology, but the inner arm still spanned the outlet.

### Rejected valve truth-model seed

Request option: no input images.

Output:

- Cache file: `exec-94997e8a-55ab-4ef3-b5f5-6c7c9eaca54d.png`
- Preserved file: `switch_style_valve_flow_truth_seed_rejected.png`
- SHA-256:
  `aa800ca3989e40573f4cf44107e9313f34191022e8870bef4b35a09f8f0a422a`
- Rejection: the dark seat rendered as a solid disk while the jet appeared
  beneath it, implying flow through solid material.

Exact prompt:

    Use case: scientific-educational
    Asset type: text-free mechanism truth reference for a mobile coffee-brewing illustration
    Primary request: Create one clean, isolated, mechanically accurate sectional diagram of an open gravity ball valve used in an immersion coffee dripper. This is a reference image for another illustrator, not a finished poster.

    COMPOSITION
    - Square canvas.
    - Center one compact valve section large on the canvas.
    - Pure flat white background.
    - Straight-on vertical section with slight three-quarter depth only where needed to show the rear lifting pin.
    - No brewer cone, no server, no hand, no exterior handle, no scenery.

    GEOMETRY
    - At top: a short vertical amber-filled inlet chamber.
    - At center: exactly one loose silver steel sphere, centered on the vertical flow axis.
    - Below sphere: one dark charcoal circular valve seat shown as a shallow ellipse; the opening is smaller than the sphere.
    - Ball is lifted straight upward by exactly 12 percent of its diameter above the seat.
    - Clearly show the narrow clearance between lower hemisphere and seat rim.
    - At lower rear-right: one slender cobalt rounded lifting pin, only 10 percent of ball diameter in width.
    - Pin approaches from behind at a shallow upward angle and contacts the lower rear-right underside of the sphere at the 5-o’clock position.
    - Contact point is clearly below the ball’s horizontal centerline.
    - Most of the pin is hidden by the ball; only a short tip is visible below the lower hemisphere.
    - Ball visibly rests on the solid pin tip. It does not float and is not attached.
    - Pin occupies only a small rear-right sector of the circular opening.
    - No broad arm, blade, spoon, fork, cradle, shelf, disk, post, piston, spring, or linkage.

    FLUID PHYSICS
    - Amber liquid fills the inlet chamber around the upper and side surfaces of the wet ball.
    - Liquid flows downward through the narrow annular clearance around the ball.
    - Show the visible front half of this annular sheet as a thin amber horseshoe precisely between ball and seat.
    - At the tiny rear-right sector occupied by the solid cobalt pin, the amber sheet is interrupted and splits around the pin.
    - Immediately below the seat, the annular sheet bends inward and downward, flows around the narrow pin, and merges into one centered vertical gravity jet.
    - The jet narrows slightly as it falls.
    - Amber never overlays or passes through cobalt, silver, or charcoal solids.
    - No amber bowl, saucer, puddle, platform, disk, floating ring, disconnected ribbon, or separate pipes.
    - One continuous topology: filled inlet → thin annular gap → circular outlet around narrow pin → single jet.

    STYLE
    - Contemporary high-fidelity vector-3D educational cutaway.
    - Crisp simplified geometry, controlled soft gradients, strong material separation.
    - Amber liquid semi-translucent; steel sphere reflective but illustrated; pin matte cobalt; seat matte charcoal.
    - Minimal, self-explanatory, no decorative elements.

    VALIDATION
    - With amber mentally removed, the narrow cobalt pin visibly supports the ball from below.
    - With cobalt mentally removed, the amber path remains continuous through the open seat into one jet.
    - The pin is much narrower than the outlet and does not span it.
    - The ball remains centered and lifted only slightly.

    CONSTRAINTS
    No text, labels, arrows, numbers, symbols, logo, border, hands, brewer, server, extra parts, noise, photorealism, vintage style, or visual clutter.

### Corrected hollow-seat valve truth model

Input image:
`switch_style_valve_flow_truth_seed_rejected.png`, reloaded as the sole edit
target.

Request option: `num_last_images_to_include = 1`.

Output:

- Cache file: `exec-95a5f321-ebcf-45fa-b7d8-698f7445a227.png`
- Preserved file: `switch_style_valve_flow_truth_model.png`
- SHA-256:
  `8780c72c77f5856a48f76f3ea75ba2c9422aa5bf8e58a422fec0213cb75b1e4b`

Exact prompt:

    Use case: precise-object-edit
    Input image: Image 1 is a rejected mechanism truth reference. Correct only the valve seat, the ball-to-seat spacing, and the local liquid through the outlet. Preserve the chamber, steel ball, small cobalt lifting pin, overall section, white background, palette, and rendering.

    CRITICAL ERROR
    The dark valve seat currently reads as a solid black disk, while the amber jet appears below it. That implies liquid teleports through solid material. Replace it with a visibly hollow circular seat and open bore.

    HOLLOW SEAT GEOMETRY
    - The valve seat is an annular torus or thick circular gasket ring, never a disk.
    - In front section, show two dark charcoal seat shoulders: one on the left and one on the right.
    - Between those shoulders, show a clearly open central bore.
    - In shallow three-quarter depth, the rear half of the ring may appear as a dark ellipse behind the opening, but the center must remain visibly hollow.
    - The bore diameter is approximately 55–65 percent of the ball diameter.
    - The ball diameter remains larger than the outer sealing diameter so it could close against the upper inner rim.
    - Raise the ball so the bottom of its sphere is visibly 10–12 percent of ball diameter above the upper seat rim.
    - Show a real open vertical clearance between lower hemisphere and seat shoulders.
    - Do not draw any dark material across the center of the bore.

    PIN
    - Preserve one slender cobalt pin contacting the lower rear-right underside of the ball.
    - Keep contact below the ball equator.
    - Pin occupies only a small rear-right sector of the open bore.
    - Ball hides most of pin.
    - Do not widen the pin and do not turn it into a support disk.

    LIQUID CONTINUITY
    - Amber liquid flows around the lower sphere through the narrow clearance above the annular seat.
    - Show the liquid crossing the plane of the seat ONLY through the visible open bore.
    - The amber sheets from the left and right gap curve inward into the hollow center.
    - Inside the bore, they visibly converge around the narrow rear pin.
    - Continue the converging amber volume through the full height of the open bore.
    - Only after leaving the lower edge of the bore may the flow contract into one vertical jet.
    - The jet must touch/connect to the liquid inside the bore with no gap.
    - Liquid must not pass through either dark seat shoulder or through the cobalt pin.
    - Use clean occlusion where solids displace the liquid.

    VISUAL TEST
    A straight vertical sightline through the center must show: amber chamber above → clear gap beneath ball → amber inside an open hole between two charcoal shoulders → one amber jet below. At no point may a solid dark disk block this sightline.

    CONSTRAINTS
    No solid seat disk. No teleporting jet. No amber painted over dark material. No bowl, puddle, saucer, decorative halo, new hardware, text, arrows, labels, border, logo, or other changes.

### Accepted v8 flow-topology master

Input images, in order:

1. `switch_style_master_v7_verified_ball_physics_chroma.png` — sole
   polished edit target.
2. `switch_style_valve_flow_truth_model.png` — functional geometry
   reference only.

Request option: `num_last_images_to_include = 2`.

Output:

- Cache file: `exec-997c6f01-2e95-473d-8d04-d67fb34b6163.png`
- Preserved chroma file:
  `switch_style_master_v8_verified_flow_topology_chroma.png`
- Chroma SHA-256:
  `a82352100a3a544b28917d326dd916f33d0c818bb2cadf32b60656e7fd245936`

Exact prompt:

    Use case: precise-object-edit
    Asset type: text-free 4:3 mobile coffee-brewing instruction illustration
    Input images:
    - Image 1 is the sole edit target: the accepted polished Switch illustration.
    - Image 2 is the verified functional valve truth model. Use Image 2 only for the exact local ball, pin, hollow seat, and liquid topology. Restyle that geometry to Image 1. Do not copy Image 2's white background, silver housing, scale, or standalone composition.

    PRIMARY REQUEST
    Replace only the compact central cutaway inside Image 1's dark silicone base with a simplified miniature version of Image 2's verified open valve. Preserve all other parts of Image 1.

    TRANSPLANT THESE RELATIONSHIPS FROM IMAGE 2
    - One centered loose steel ball.
    - Ball lifted only slightly above the seat.
    - A very narrow cobalt pin contacts the lower-right underside below the ball's equator and supports it.
    - Most of the pin is behind and occluded by the ball.
    - A dark annular seat with two visible shoulders and a clearly hollow central bore. Never a solid disk.
    - Amber liquid fills the chamber above and around the ball.
    - Liquid passes through the narrow ball-to-seat clearance.
    - Liquid then visibly enters the hollow bore between the two dark seat shoulders.
    - Liquid continues through the bore and only then contracts into one downward jet.
    - The narrow pin occupies only a small right-rear portion of the bore; liquid flows around it.
    - Solid cobalt, silver, and charcoal regions displace and occlude amber liquid; they never overlap it.

    ADAPTATION TO IMAGE 1
    - Keep Image 1's current front three-quarter camera, compact cutaway size, cobalt external handle, fingertip, and dark silicone housing.
    - Connect Image 1's broad external handle to the narrow inner pin through one subtle pivot inside the right wall, but do not let the broad handle enter or span the outlet.
    - Inside the chamber, only the narrow final pin tip may be visible.
    - Keep the pin at no more than one eighth of the ball diameter.
    - Make the hollow bore readable at phone size as an amber-filled open gap between left and right charcoal seat shoulders.
    - Show the upstream amber column from the glass throat visibly joining the liquid around the ball.
    - Show the amber inside the bore visibly touching the top of the existing single stream.
    - Preserve a small dark outline between steel ball, amber sheet, charcoal seat, and cobalt pin for clarity.

    REQUIRED VERTICAL SIGHTLINE
    Along the central axis the viewer must see, in order:
    1. upstream amber from the glass throat;
    2. amber chamber around the ball;
    3. the bottom of the centered steel ball;
    4. a small amber clearance below the ball;
    5. an open amber-filled bore between two dark seat shoulders;
    6. the same amber contracting into one stream;
    7. the stream continuing into the server.
    No solid disk or broad lever may interrupt this sightline.

    PRESERVE IMAGE 1 UNCHANGED
    Exact composition, crop, scale, modern vector-3D finish, textured damp grounds, smooth liquid, filter, glass, base exterior, server, pool, fingertip, outer handle, dark rounded stage, flat green perimeter, palette, lighting, and silhouettes. Nothing touches the canvas edge.

    BACKDROP
    Keep the outer perimeter perfectly flat uniform #00FF00 with no gradient, shadow, reflection, texture, noise, or spill.

    CONSTRAINTS
    No side contact at the ball equator. No broad blue arm beneath the ball. No ball sitting in an amber cup. No solid seat disk. No teleporting stream. No liquid painted through solids. No extra mechanism, pipe, reservoir, stream, hardware, text, arrows, labels, numbers, symbols, logo, border, white outline, vintage treatment, photorealism, or visual noise.

Transparency conversion:

    python '<imagegen-skill>\scripts\remove_chroma_key.py' --input 'docs\brewing\illustration-style-explorations\starlit-tactile-2026-08-02\switch_style_master_v8_verified_flow_topology_chroma.png' --out 'docs\brewing\illustration-style-explorations\starlit-tactile-2026-08-02\switch_style_master_v8_verified_flow_topology_alpha.png' --auto-key border --soft-matte --transparent-threshold 12 --opaque-threshold 220 --despill

Helper and integrity results:

- Detected key color: `#0fdc13`
- Transparent pixels: `461862 / 1572528`
- Partially transparent pixels: `3706 / 1572528`
- Alpha bounding box: `(179, 32, 1268, 1055)` on a 1448 by 1086 canvas
- All four corner alpha values: `0`
- Preserved alpha file:
  `switch_style_master_v8_verified_flow_topology_alpha.png`
- Alpha SHA-256:
  `c991b4fd8a6379a344d88dab31ee282d62d3d6d9a87bc2066cccbc39141dbc35`

Phone-size QA reduced the alpha master to 384 by 288 and composited it over
`#F2E0D5` and `#52443C`. Both previews retained the centered ball, lower
pin contact, two seat shoulders, open amber-filled bore, continuous single jet,
granular grounds, and clean transparent perimeter.

## Fresh expressive v9 production master

V9 was generated as a new composition from a blank canvas after rejecting two
attempts that inherited visual or mechanical degradation. It did not edit a
previous Switch scene.

Generator request:

- Built-in `image_gen` tool.
- Request option: `num_last_images_to_include = 2`.
- Exact prompt:
  `prompts/brewing/assets/instruction_p1_switch_official_20_240_stage_04_instruction_default/starlit_tactile_v9.txt`.
- Model/build and seed: not exposed.

Exact input images, in conversation order:

1. `switch_v9_input_truth_reference.jpg` — functional topology only; SHA-256
   `6535d73604ece61a7dcecde4a32244af328ea3903692362a628646182ab8ad47`.
2. `switch_v9_input_style_reference.jpg` — modern tactile rendering and
   expressive-stage language only; SHA-256
   `1ed09e943d73674b9f1fb0ace8ca76198d962782fd3160293fe3ac77c650c83c`.

The proxy inputs are preserved beside the master under
`docs/brewing/illustration-style-explorations/starlit-tactile-2026-08-02`.
They are the exact JPEG payloads supplied to the generator, not merely links to
their higher-resolution ancestors.

Output:

- Cache file: `exec-55238134-5d48-40fe-99bc-0d505123cc1b.png`.
- Chroma cache filename: `switch_style_master_v9_fresh_expressive_chroma.png`
  (hash retained below; binary available through Git history).
- Chroma SHA-256:
  `6a7c2984bef29b72f07ee431fca35d93884732db57c4bc055bfd88b911c29e2b`.
- Preserved alpha file: `switch_style_master_v9_fresh_expressive_alpha.png`.
- Alpha SHA-256:
  `cf0c7b412a64f7bbfc001886190dd82b02929726df5ec45d57853d4d311fa24f`.

Transparency conversion:

    python '<imagegen-skill>\scripts\remove_chroma_key.py' --input 'docs\brewing\illustration-style-explorations\starlit-tactile-2026-08-02\switch_style_master_v9_fresh_expressive_chroma.png' --out 'docs\brewing\illustration-style-explorations\starlit-tactile-2026-08-02\switch_style_master_v9_fresh_expressive_alpha.png' --auto-key border --soft-matte --transparent-threshold 12 --opaque-threshold 220 --despill

Transparency and phone-card QA:

- Detected key color: `#02e512`.
- Transparent pixels: `586590 / 1572528`.
- Partially transparent pixels: `4153 / 1572528`.
- Alpha bounding box: `(184, 20, 1366, 1069)` on 1448 by 1086.
- All four corner alpha values: `0`.
- Reviewed at 384 by 288 over `#F2E0D5` and `#52443C`.
- Both themes retain the complete organic stage, supported server, fingertip
  action, centered ball, narrow cobalt lift point, open amber passage, single
  stream, and granular slurry.

Rejected unpreserved attempts:

- `exec-0d010ea7-868a-4ca5-bffd-b07dbd4df675.png`: background-only edit of a
  compressed v8 proxy; rejected because the broad lever obstructed the valve
  topology and the rendering visibly degraded.
- `exec-306f9d9f-fd16-4fa7-badb-db66432f1c4f.png`: blank-canvas attempt without
  references; rejected because the server was cropped and the valve read as an
  amber cup rather than a supported ball above a hollow seat.
