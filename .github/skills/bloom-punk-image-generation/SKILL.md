---
name: bloom-punk-image-generation
description: Project-level workflow for generating, processing, aligning, validating, and installing Starlit Coffee punk bloom spritesheets and their completed final still images.
version: 1.0.0
triggers:
  - generate bloom images
  - generate punk bloom spritesheets
  - process bloom spritesheets
  - bloom final stills
  - install bloom images
---

# Bloom Punk Image Generation

Use this project-level skill when turning `prompts\bloom-spritesheet-frame-prompts.md` sections into app-ready spritesheet PNGs and completed-bloom still PNGs.

## Inputs

Required:

- Theme slug, for example `cyberpunk`.
- Source atlas image generated from the common prompt plus the theme-specific frame plan.

Optional:

- Standalone final still image generated from the final still prompt contract. Prefer extracting frame 25 from the processed spritesheet instead.

Project files:

- Prompt source: `prompts\bloom-spritesheet-frame-prompts.md`
- Processing docs: `docs\bloom-spritesheet-splicing.md`
- Processor: `tools\process_bloom_imagegen_spritesheets.py`
- Aligner: `tools\align_bloom_spritesheets.py`
- App assets: `app\src\main\res\drawable-nodpi`

## Generation Contract

The source atlas must satisfy:

- exactly `5 x 5` cells,
- exactly `25` frames,
- row-major order,
- one square `1:1` atlas,
- flat `#00FFFF` chroma-key background,
- magenta guide grid if the model can follow it,
- no labels, numbers, text, UI, watermarks, backgrounds, shadows, glow spill, or extra panels.

Regenerate the source atlas if:

- grid is missing, warped, uneven, merged, or labeled;
- frame count is not 25;
- cells contain text or frame numbers;
- the object restarts at row boundaries;
- frame 25 is not the first finished sprite;
- the coffee bean changes identity;
- the theme becomes a scene or collage.

## Spritesheet Pipeline

Use the repository processor first:

```powershell
python tools\process_bloom_imagegen_spritesheets.py source.png:app\src\main\res\drawable-nodpi\bloom_<slug>_spritesheet.png --columns 5 --rows 5 --key-color '#00ffff' --guide-tolerance 180 --transparent-threshold 170 --opaque-threshold 250 --no-despill --clear-output-border 3 --output-padding 10 --remove-stray-components 250 --debug-dir tmp\bloom-splice-debug
```

For absolute Windows paths, use `=>` instead of `:`:

```powershell
python tools\process_bloom_imagegen_spritesheets.py C:\tmp\source.png=>C:\tmp\bloom_<slug>_spritesheet.png --columns 5 --rows 5 --key-color '#00ffff' --guide-tolerance 180 --transparent-threshold 170 --opaque-threshold 250 --no-despill --clear-output-border 3 --output-padding 10 --remove-stray-components 250 --debug-dir C:\tmp\bloom-splice-debug
```

Then align:

```powershell
python tools\align_bloom_spritesheets.py app\src\main\res\drawable-nodpi\bloom_<slug>_spritesheet.png:app\src\main\res\drawable-nodpi\bloom_<slug>_spritesheet.png --baseline 244 --padding 10 --alpha-threshold 24
```

If using absolute paths, use `=>` in the align pair too.

## Final Still Pipeline

Preferred path: extract the final still from frame 25 of the processed and aligned spritesheet.

For a `5 x 5`, `256 x 256` atlas, frame 25 is column 5, row 5:

- crop left: `1024`
- top: `1024`
- right: `1280`
- bottom: `1280`

Example:

```powershell
python -c "from PIL import Image; from pathlib import Path; src=Path(r'app\src\main\res\drawable-nodpi\bloom_<slug>_spritesheet.png'); out=Path(r'app\src\main\res\drawable-nodpi\bloom_<slug>_final.png'); Image.open(src).convert('RGBA').crop((1024,1024,1280,1280)).save(out)"
```

Only use standalone final still generation if extraction is impossible. In that case:

1. Use the common final still prompt contract in `prompts\bloom-spritesheet-frame-prompts.md`.
2. Add the matching theme source lock.
3. Reject the image if it differs from frame 25 in silhouette, bean base, scale, anchor, anatomy, or background.

## Validation

Validate every spritesheet:

- PNG size is `1280 x 1280`.
- Alpha output is RGBA.
- Each frame is `256 x 256`.
- Frame 25 exists and is complete.
- Frame 24 still has exactly one visibly unfinished detail.
- No guide remnants remain after processing.
- No matte noise remains below the target baseline.

Validate every final still:

- PNG size is `256 x 256`.
- Alpha output is RGBA.
- It visually matches frame 25 of the matching spritesheet.
- It has no new art, new props, new glow spill, background, shadow, or changed coffee bean.
- Filename is `bloom_<slug>_final.png`.

## App Integration Checklist

When assets are ready:

1. Add spritesheet PNGs and final PNGs to `app\src\main\res\drawable-nodpi`.
2. Wire the spritesheet resource into `BloomSpritesheetAnimation.kt` only after the PNG exists.
3. Keep the selected bloom variant stable between the active animation and the final still.
4. During active bloom, render `bloom_<slug>_spritesheet.png`.
5. After bloom completion, render `bloom_<slug>_final.png` for the rest of the brew, with fallback to the spritesheet frame 25 if no final still exists.
6. Run the existing Android validation commands appropriate for UI/resource changes.

## Failure Recovery

- If processing cuts off art: increase source padding or regenerate with stricter cell padding.
- If guide lines leak: use `--clear-output-border 3` or larger and inspect debug output.
- If final still differs from frame 25: discard it and crop frame 25 instead.
- If a theme reads as scenery: return to `bloom-punk-prompt-workflow` and revise the prompt before generating again.
- If frame 21 jumps to a finished icon: regenerate; do not patch by hand unless only one frame is affected and continuity remains intact.

