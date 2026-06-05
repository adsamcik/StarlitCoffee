# Bloom Spritesheet Splicing

Use a two-step pipeline for image-generated bloom animations:

1. Generate a source atlas that is friendly to slicing.
2. Run `tools/process_bloom_imagegen_spritesheets.py` to produce the app atlas.

The new app atlas contract is:

- `25` frames per animation.
- `5` columns by `5` rows.
- `256 x 256` pixels per frame.
- Final PNG size: `1280 x 1280`.
- RGBA output with transparent background.

The standard generation pipeline now requires the modern `25` frame contract end to end: source atlas `5 x 5`, processed atlas `5 x 5`, and final still extracted from `R5C5`. Legacy `6 x 5` and `9 x 5` work is migration-only and requires the explicit `--allow-legacy-grid` escape hatch in the tooling.

## Source Atlas Prompt Contract

Ask the image generator for a single animation variation per source atlas:

```text
Create one 25-frame spritesheet source atlas for a coffee bean blooming animation.

Highest priority: this is a technical slicing atlas first and artwork second. If any style request conflicts with the grid, slicing, alignment, or frame-continuity rules, obey the grid, slicing, alignment, and frame-continuity rules.
Arrange the sheet as exactly 5 columns left-to-right and exactly 5 rows top-to-bottom, making exactly 25 equal square cells in one square 1:1 atlas.
Do not generate legacy `6 x 5`, `9 x 5`, `30` frame, or `45` frame sheets.
The canvas itself is the atlas: no margins, gutters, title strip, caption area, empty band, extra panel, partial cell, merged cell, inset sheet, or decorative border. The outer magenta border touches the canvas edges.
Reading order is row-major only as a cell address map: row 1 contains frames 1-5, row 2 contains frames 6-10, row 3 contains frames 11-15, row 4 contains frames 16-20, and row 5 contains frames 21-25.
Rows are layout only. Do not treat rows as story stages, timing bands, or progress groups. Do not infer a new phase from the start of a row.
The variation-specific frame plan is the only story authority. Each individual frame line describes exactly what that cell should contain. Draw the line literally.
For each cell, preserve every visible part introduced in earlier frames unless the frame line says it changes by opening, unfolding, lengthening, lifting, widening, rounding, or settling.
Every adjacent frame must be visibly different from the previous frame by the specific named anatomy change in its own frame line, not by a generic style change. Do not copy, duplicate, mirror, hold, or redraw the same pose across multiple cells.
Frame 25 is the first finished sprite. No earlier frame may look finished. Frame 24 still has exactly one unfinished silhouette detail. Frame 25 changes only that final planned detail.

Acceleration and final-bloom contract:
The animation must feel like it accelerates. Frames 1-10 are slow setup changes, frames 11-20 are medium build changes, and frames 21-25 are the largest final-bloom changes.
Frame 20 is the loaded pre-bloom pose: a closed bud, tight cup, compact steam/foam structure, or compact plant top with visible tension.
Frames 21, 22, 23, 24, and 25 are the final bloom sequence. Frames 21-23 contain the largest silhouette changes, frame 24 is near-final, and frame 25 resolves one remaining visible unfinished detail only.
Frame 21 starts the release from the exact frame-20 pose, frame 22 opens wider, frame 23 creates the recognizable final silhouette, frame 24 is almost final with exactly one unfinished outer silhouette or major visible detail, and frame 25 completes only that detail with a small but readable final change.
Do not let frames 21-25 look like five copies of the finished sprite. Do not reach the recognizable final open bloom before frame 23.

Wrap-edge continuity contract:
The wrap pairs 5->6, 10->11, 15->16, and 20->21 are ordinary adjacent animation steps. The first cell of a new row is not a restart and not a new stage.
For frames 6, 11, and 16, the second frame must look like the immediate next drawing after the previous frame: same scale, same baseline, same bean opening, same stem position, same leaf position, same bloom position, and only the small named change in that frame line.
For frame 21, keep the same pose identity as frame 20, but begin the intentionally larger final-bloom release. No repositioning, no new scale, no restart, and no jump to a finished flower.

Individual-frame guard:
If a frame says tiny, small, hint, point, nub, faint, slightly, barely visible, closed, cupped, compact, or unfinished, draw the smallest readable version of that feature. Do not substitute a larger later-frame form.
Do not draw a leaf when the frame says tip, a stem when it says point, a flower when it says bud, a flat blossom when it says cup, a full plant when it says sprout, or a finished bloom before frame 25.
Do not use completion percentages, rows, or broad phases to decide what a cell should contain. Use only the individual frame description for that exact frame.
Make every frame visually different: at least one named part must change size, angle, count, openness, curvature, or position in every adjacent pair.

Draw exactly one complete animation frame inside each equal square cell.
Keep the lowest visible pixel of the sprite on the same bottom-center baseline in every cell, near y=88% of the cell height with empty cyan below it. Keep the root anchor fixed near x=50%, y=82%; keep final sprite bounds inside x=18%-82% and y=10%-88%.
Keep one original roasted coffee bean base in every frame. When the bean opens, it becomes the same two bean-shell halves at the bottom of the sprite. Do not create a second coffee bean, a second stem, or a second final flower.
By frame 20, the bean halves are already settled at their final open base angle. Frames 21-25 keep the same already-open bean base while only the bloom, plant, foam, or steam finishes.
Use a locked orthographic front view. No camera zoom, no perspective tilt, no rotation changes, no side-view replacements, no changing art style between cells.
Use a perfectly flat solid #00ffff chroma-key background. Every non-art, non-grid pixel must be pure #00ffff: no gradients, texture, transparency, cast shadows, reflections, glow spill, contact shadows, or background objects.
Any glow, sparkle, steam highlight, or star-shaped highlight named by a frame plan must be an opaque painted part of the sprite artwork, contained inside the sprite bounds. No aura, haze, transparency, or color spill outside the sprite silhouette.
The lowest colored pixels in each cell must belong to the coffee bean shell or plant itself. Do not draw any oval, smear, glow, teal outline, cyan halo, dark base mark, or grounding stroke underneath the bean.
Draw one axis-aligned slicing grid only: exactly 6 vertical #ff00ff lines and exactly 6 horizontal #ff00ff lines, including the outer border. Lines must be straight, continuous, uniform thickness, and shared between adjacent cells.
Keep every piece of the subject fully inside its cell with at least 12% empty cyan padding on every side.
The R1C1 / Frame 01 labels in variation prompts are instructions only. Never draw those labels.
Render zero text: no letters, digits, labels, frame numbers, shadows, watermark, UI, symbols, or decorative background.
```

Then add the variation-specific `25` frame plan from:

```text
prompts/bloom-spritesheet-frame-prompts.md
```

Regenerate the source atlas if the grid is missing, broken, warped, uneven, labeled, merged, or not exactly `5 x 5` cells. Regenerate if the model adds frame numbers or any other text.

## Processing

Run:

```powershell
python tools\process_bloom_imagegen_spritesheets.py source.png:app\src\main\res\drawable-nodpi\bloom_coffee_flower_spritesheet.png --columns 5 --rows 5 --key-color '#00ffff' --guide-tolerance 180 --transparent-threshold 170 --opaque-threshold 250 --no-despill --clear-output-border 3 --output-padding 10 --remove-stray-components 250 --debug-dir tmp\bloom-splice-debug
```

For absolute Windows paths, use `=>` instead of `:`:

```powershell
python tools\process_bloom_imagegen_spritesheets.py C:\tmp\source.png=>C:\tmp\bloom_coffee_flower_spritesheet.png --columns 5 --rows 5 --key-color '#00ffff' --guide-tolerance 180 --transparent-threshold 170 --opaque-threshold 250 --no-despill --clear-output-border 3 --output-padding 10 --remove-stray-components 250 --debug-dir C:\tmp\bloom-splice-debug
```

The processor will:

- Recover the guide grid when present.
- Fall back to equal slicing if no guide grid is found.
- Remove the chroma-key background or preserve source alpha if the input is already transparent.
- Keep all visible pieces inside each cell together, including steam, sparks, petals, and split bean halves.
- Clear a small output-frame border when requested so guide remnants cannot leak into app frames.
- Optionally scale each source cell into an inset output rectangle with `--output-padding`.
- Optionally remove tiny disconnected matte artifacts with `--remove-stray-components`.
- Write debug overlays and coverage reports when `--debug-dir` is provided.

## Alignment

After processing, align the final RGBA sheets to the shared app anchor:

```powershell
python tools\align_bloom_spritesheets.py source.png:aligned.png --columns 5 --rows 5 --baseline 244 --padding 10 --alpha-threshold 24
```

For the app bloom sheets, the final alignment contract is:

- Every frame remains `256 x 256`.
- The visible sprite bottom lands on `y=244`.
- Every frame keeps at least `10px` transparent padding.
- The bottom-center anchor is centered around `x=128`.
- Low-alpha matte noise below alpha `24` is cleared.

## Final Stills

Each bloom animation may also have a completed-bloom still that stays visible for the rest of the brewing period. The still is not a separate design pass. It must be the same completed sprite as frame 25 (`R5C5`) of the matching processed and aligned spritesheet.

Final still contract:

- One `256 x 256` RGBA PNG per bloom variation.
- Filename format: `bloom_<slug>_final.png`, matching `bloom_<slug>_spritesheet.png`.
- Source is frame 25 only: column 5, row 5 in the `5 x 5` atlas, crop rectangle `(1024, 1024, 1280, 1280)`.
- Preserve transparency, scale, baseline, anchor, padding, and all visible anatomy exactly.
- Do not regenerate new art unless extracting from the sheet fails. If regeneration is necessary, use the final still prompt contract in `prompts\bloom-spritesheet-frame-prompts.md` and treat the matching `R5C5 Frame 25` line as the source lock.

Preferred extraction command:

```powershell
python -c "from PIL import Image; from pathlib import Path; src=Path(r'app\src\main\res\drawable-nodpi\bloom_cyberpunk_spritesheet.png'); out=Path(r'app\src\main\res\drawable-nodpi\bloom_cyberpunk_final.png'); Image.open(src).convert('RGBA').crop((1024,1024,1280,1280)).save(out)"
```

For a generated standalone still, process it through the same transparency and alignment expectations as a single app frame. The resulting still must be visually identical to frame 25; reject it if it changes the silhouette, adds a new part, changes the bean base, changes scale, adds shadow, adds glow spill, or introduces a background scene.

## Verification

Before shipping or replacing bloom assets, verify the app resources still match the modern grid:

```powershell
python tools\verify_bloom_modern_grid.py
```

The check passes only when every `bloom_*_spritesheet.png` is `1280 x 1280` (`5 x 5` at `256px` cells) and every existing `bloom_*_final.png` is `256 x 256`.

## Why 25 Frames

The 45-frame and 30-frame sources were giving the image generator too much empty work: it often reached the final image early and then repeated or jumped around the last row. The 25-frame version is a square `5 x 5` sheet, makes each frame more meaningful, and leaves less room for row-level drift.
