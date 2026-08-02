# Starlit Tactile instruction-art exploration

Date: 2026-08-02

Status: preferred visual direction; not an approved instruction asset and not
registered in the app catalog.

## Outcome

The preferred direction uses a self-contained illustration island on a
transparent 4:3 canvas:

- A deep espresso, softly asymmetric stage isolates the instructional action.
- Equipment stays inside the stage. A hand or other action element may break
  the stage boundary when that makes the interaction clearer.
- Every visible pixel stays inside a 4.5% minimum canvas safe area. Nothing is
  cropped by or visually terminates at the asset edge.
- The area outside the stage is transparent, with no border. The containing
  Material surface therefore remains native to light, dark, and dynamic-color
  themes.
- The subject is large enough to read at 300-384 dp without sacrificing the
  complete equipment state.
- Palette, rounded geometry, warm material rendering, and restrained gold
  focus lighting align the art with the app's expressive cup and bloom assets.

The user preferred the textured `switch_style_master_v1_chroma.png` over the
flatter experiment. `switch_style_master_v2_chroma.png` applies the requested
larger scale and removes the outer cream rim. Its processed-alpha counterpart
is `switch_style_master_v2_alpha.png`.

## Clarity budget

Texture is permitted only when it separates an instructional material or
state: paper, glass, silicone, metal, coffee, wood, or skin. It must never
become ambient decoration.

Each illustration must have one visual hierarchy:

1. The action or equipment-state change.
2. The mechanism or completion cue that confirms it.
3. The receiving vessel or other necessary context.

Everything else is subordinate. Do not add stars, floating particles,
decorative props, scenery, labels, arrows, or redundant reflections. Use a
localized gold focus treatment only when it makes the taught action easier to
find. Text remains outside the image and immediately below it in the app.

## Theme behavior

The alpha master was composited at 384 by 288 over the app's fallback
`surfaceVariant` colors:

- Light: `#F2E0D5`
- Dark: `#52443C`

The transparent perimeter worked on both. The near-black stage intentionally
has stronger boundary contrast in light theme; in dark theme the brighter
equipment, gold mechanism focus, and hand preserve the subject hierarchy.
There is no fixed light halo or border that would fight dynamic color.

## Accuracy status

These images establish art direction and placement, not release approval. The
Switch style master still shows more collected coffee than the exact first
drawdown moment calls for. A production candidate must reduce that to a
shallow initial pool while preserving the preferred style, scale, containment,
trigger-to-ball linkage, lifted ball, open seat, and single vertical stream.

The Chemex pilot demonstrates the same style on a passive setup stage. Its
three-leaf side, one-leaf side, and open spout channel still require the normal
mechanics and mobile-size review before promotion.

## Promotion tooling note

The current deterministic candidate builder converts every source to RGB and
would discard this alpha perimeter. Before a transparent candidate is
promoted, update the manifest schema and verifier to preserve RGBA through
resize and WebP encoding while continuing to validate dimensions, alpha
corners, safe-area containment, hashes, and byte-exact lineage. Existing
opaque candidates must remain reproducible without changes to their output.

## Preserved artifacts

All PNGs are 1448 by 1086. The chroma sources are RGB; the processed master is
RGBA.

| File | Role | SHA-256 |
| --- | --- | --- |
| `chemex_style_pilot_v1.png` | Initial style pilot | `8a94d9929832ae8717e73d02923239eec1334d326854cf06c7b23a668bf7108d` |
| `switch_style_pilot_v1.png` | First mechanical stress test | `692c74c5b7553a6d403c8ffc38884776f1276c5b31092f1df54098d281f66697` |
| `switch_style_pilot_v2_shallow.png` | Targeted shallow-pool edit | `9520b2338a90a94c4210a30132c08759f7f3b95d3c17114823adc857c5fd89df` |
| `switch_style_master_v1_chroma.png` | User-preferred contained style master | `85a67d5cfc1823e8cef85b81e8c0b0deb815eafb71cdbe3350fe12dbaf2b5f6f` |
| `switch_style_flat_experiment_rejected.png` | Rejected over-simplified variant | `250d295b6337afd331ebd26b878944771a718ffc602f77e9dcb1b1ea675f2179` |
| `switch_style_master_v2_chroma.png` | Preferred larger, borderless chroma master | `f0914400afc865d5cd852ba0a2308ca55fc08b1b0325e790b0659c7d73dd2248` |
| `switch_style_master_v2_alpha.png` | Preferred larger, borderless alpha master | `1af5536d0402c105d8064e8aa9143f86bbe96b70363fb97ba1d9fd6990427d4e` |

## Reproduction record

The exact built-in image-generation prompts, reference order, intermediate
lineage, and alpha conversion command are recorded in
`prompts/brewing/starlit-tactile-style-exploration-2026-08-02.md`.

The built-in generator did not expose a seed or model/build identifier. Prompt
replay is therefore stochastic. Use the preserved PNG masters as edit/style
references; use the recorded prompt to continue the visual system rather than
expecting byte-identical regeneration.
