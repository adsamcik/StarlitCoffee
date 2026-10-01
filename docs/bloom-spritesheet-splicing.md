# Bloom Spritesheet Preparation

The active bloom pipeline uses native transparent artwork. Generate a 25-pose
atlas, review the complete growth sequence, then prepare the app resource with
`tools/prepare_bloom_alpha_atlas.py`. Do not chroma-key new flower artwork.

## App resource contract

- Exactly 25 frames, in row-major order in a 5 × 5 grid.
- Each frame is 256 × 256; the RGBA atlas is 1280 × 1280.
- The visible-bottom target is y=232, with a one-pixel allowance after scaling
  and alpha cleanup; the bean base is centered near x=128.
- A 24-pixel transparent inset target protects the sprite and renderer halo,
  with a one-pixel allowance for rounding and the inclusive y=232 baseline.
- Clear alpha below 24, including its otherwise invisible RGB matte.
- Use one scale for the whole animation, preserving growth between poses.

The app plays these poses according to the existing bloom countdown. Finished
previews and the played finish use the same frame-25 registration and halo.
Keep each existing animation ID and filename so stored selections and weights
remain valid.

## Generation and review

Use the built-in image generator with `transparent_background=true` and the
current prompts in `prompts/bloom-spritesheet-native-alpha-prompts.md`. A cropped
finished sprite is a useful style reference: an old full atlas can bias a new
sequence toward its old resets. Preserve each variant's identity and palette.

Ask for exactly five rows and five columns with no visible guides, labels,
background, shadows, gutters, or scenery. Keep one complete plant in each cell
with generous empty space. A fixed front view, fixed bean size and base, and
persistent leaf/petal/fruit attachment points are essential.

Each adjacent pose should advance a named, readable anatomical change. Distribute
growth through the middle of the sequence; finish with progressive opening and
settling. Do not require a closed frame 20 or delay the recognizable flower until
frame 23. Those old constraints concentrated the payoff into a sudden late jump.
The final pose should complete an already understandable bloom.

Review all 24 adjacent transitions, especially 5→6, 10→11, 15→16 and 20→21.
Check that leaves, petals, bells, fruit and decorative structures persist after
appearing. Reject reversed ripening, recupping, camera changes, disappearing
parts, clipping, holes in colored surfaces and prolonged duplicate poses.

Inspect opaque light and dark composites before rejecting matte artifacts.
Nearly transparent pixels may contain bright RGB that looks alarming in a raw
image preview but does not survive correct alpha compositing. Conversely,
opaque holes or missing structures require an artwork correction.

Native edits can repair specific poses. Whole native-generated poses may also be
manually selected and ordered across compatible attempts. Record the source
atlas and frame number for every selected pose. Do not draw intermediate poses
in code, morph anatomy, swap individual components, recolor fruit, hide repeated
holds, or independently resize poses to imply growth. Recheck the assembled
sequence for style, scale and anatomical continuity.

## Prepare the atlas

```powershell
python tools\prepare_bloom_alpha_atlas.py source.png app\src\main\res\drawable-nodpi\bloom_coffee_flower_spritesheet.png --preview-dir .qa-screens\bloom-preparation
```

The preparer preserves the supplied alpha and colors, clears low-alpha noise,
finds fully transparent separators near the nominal row/column boundaries,
applies one common native-pixel scale, and registers the bean base. Uneven native
row spacing can otherwise split a bean between two equal-height cells. Recovered
cell sizes never receive independent scaling. The report records the recovered
edges, common scale and all final bounds.

The tool refuses opaque sources, empty cells, missing transparent separators and
artwork touching the source canvas edge. Regenerate clipped or overlapping art;
padding and alignment cannot restore missing anatomy. Inspect prepared contact
sheets at the actual small display size in both themes, including the halo.

```powershell
python tools\verify_bloom_modern_grid.py
python -m unittest discover -s tools -p 'test_*bloom*.py'
```

The grid check proves resource dimensions, and pipeline tests protect slicing,
shared scale and color preservation. Neither proves artistic continuity. Native
Compose rendering tests additionally compare all finished previews against the
played finish and check distinct start/middle/finish stages in both themes.
Use `bloomCapture=true` instrumentation arguments to save native stage captures.

## Finished stills

The application reads frame 25 directly; a separate generated final image is
unnecessary. If an external still is needed, extract R5C5 from the prepared atlas,
rectangle `(1024, 1024, 1280, 1280)`. Preserve alpha, registration and anatomy.
Do not redraw the finish separately.

## Legacy keyed sources

`tools/process_bloom_imagegen_spritesheets.py` remains available for existing
keyed source material. Its guide-color cleanup is restricted to recovered guide
bands; matching pink or purple artwork elsewhere must survive. Choose key colors
absent from the artwork and inspect the resulting alpha carefully.

`prompts/bloom-spritesheet-frame-prompts.md` records the historical keyed prompt
set. Its compressed-until-frame-20 timing is superseded by the native-alpha
workflow. Legacy 6 × 5 and 9 × 5 sources are migration-only and require the
processor's explicit `--allow-legacy-grid` option.
