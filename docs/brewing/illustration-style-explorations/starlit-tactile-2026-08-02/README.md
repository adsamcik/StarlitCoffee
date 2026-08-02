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
- The current v4 stage uses a measured 2.9% minimum transparent perimeter so
  it reads confidently at phone size. Nothing is cropped by or visually
  terminates at the asset edge.
- The area outside the stage is transparent, with no border. The containing
  Material surface therefore remains native to light, dark, and dynamic-color
  themes.
- The subject is large enough to read at 300-384 dp without sacrificing the
  complete equipment state.
- Palette, rounded geometry, warm material rendering, and restrained gold
  focus lighting align the art with the app's expressive cup and bloom assets.

The user preferred the textured `switch_style_master_v1_chroma.png` over the
flatter experiment. `switch_style_master_v2_chroma.png` applied the requested
larger scale and removed the outer cream rim. The grounded v3 revision made the
silicone base visibly rest on a realistically proportioned receiving server.
The v4 revision replaced the retro textured treatment with a contemporary
vector-3D editorial finish and removed unverified linkage geometry. V5 made the
complete upstream-to-downstream liquid path visible. V6 gave the ground-coffee
mass a restrained granular material treatment. The current preferred revision,
`switch_style_master_v7_verified_ball_physics_alpha.png`, corrects the valve
physics: the one-piece switch pivots, its inner end supports the ball from
below, the ball lifts only slightly above the centered seat, and liquid passes
through the resulting annular gap into one outlet.

## Clarity budget

Texture is permitted only when it separates an instructional material or
state: paper, glass, silicone, metal, coffee, wood, or skin. It must never
become ambient decoration.

Ground coffee uses simplified irregular particles and damp clumps that survive
phone-size reduction. It must not use whole-bean silhouettes, center grooves,
repeated seed shapes, photorealistic micro-detail, or texture in the liquid.

Each illustration must have one visual hierarchy:

1. The action or equipment-state change.
2. The mechanism or completion cue that confirms it.
3. The receiving vessel or other necessary context.

Everything else is subordinate. Do not add stars, floating particles,
decorative props, scenery, labels, arrows, or redundant reflections. Use a
localized gold focus treatment only when it makes the taught action easier to
find. Text remains outside the image and immediately below it in the app.

## Theme behavior

The v7 alpha master was composited at 384 by 288 over the app's fallback
`surfaceVariant` colors:

- Light: `#F2E0D5`
- Dark: `#52443C`

The transparent perimeter worked on both. The near-black stage intentionally
has stronger boundary contrast in light theme. In dark theme, the brighter
glass, cream filter, cobalt control, steel ball, amber flow, and fingertip
preserve the subject hierarchy. There is no fixed light halo or border that
would fight dynamic color.

## Accuracy status

These images establish art direction and placement, not release approval. The
grounded v3 master corrected the floating-brewer illusion, normalized the
server-to-brewer scale, placed the silicone base on the server rim, carried the
stream inside the glass, and restored a shallow initial pool.

The Switch's documented functional story is a stainless-steel ball blocking
flow in the silicone base until the external switch is pressed, after which
coffee drains. Hario also states that drawdown continues after the finger is
released. Hario's official manual further warns that hot water flows from the
bottom when the ball is absent and describes inserting the switch's long narrow
end into the base. Its assembly diagram also shows the molded switch seated in
the base and moving up and down. V7 therefore presents one continuous molded
switch as a seesaw: the pressed external end moves down while the long inner
end rises beneath the loose ball. The ball remains centered and lifts only
enough to expose the annular opening around the circular seat. The cutaway
intentionally does not claim a literal manufacturer cross-section. Sources:
[Hario USA Switch product page](https://www.hario-usa.com/products/switch-immersion-dripper),
[Hario Europe Switch product page](https://www.hario-europe.com/collections/03-size/products/v60-immersion-dripper-switch),
the [official Hario instruction manual](https://www.hario.cc/Items/manual_pdf/SSD.pdf),
and the [official Hario 2024 coffee catalog](https://www.hario.cc/PDF/pdf2024Eng/4.COFFEE_P48-83.pdf).

V7 exposes a single continuous gravity path from the filtered glass-bowl
outlet, into the upstream valve chamber, through the annular gap around the
slightly lifted ball, through the centered bottom outlet, and into the server.
The visible internal liquid path is explanatory; its exact cutaway shape is not
a manufacturer-published cross-section.

The grounded server relationship is consistent with Hario's official Switch
server set, in which the dripper sits directly on the included glass server.
Source: [Hario USA Switch Server Set](https://www.hario-usa.com/products/switch-server-set).

A production candidate must still pass exact equipment, mechanism,
liquid-state, and mobile-size review before promotion.

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
| `switch_style_grounded_pilot_v1_chroma.png` | First physically grounded edit | `1eb052b5eddc77577c465c31570c5e7b368d1fc2fa9161fa1292f8bf37b81a73` |
| `switch_style_master_v3_grounded_chroma.png` | Preferred grounded and scaled chroma master | `c2aff9335ac7cad488a20354a2b2f79c740111cbe5f984b94cce8c16d3dfef20` |
| `switch_style_master_v3_grounded_alpha.png` | Preferred grounded and scaled alpha master | `089e215658f2ee6d7cf69b51eee0ef6894072944d3a5875319429b0b65339b50` |
| `switch_style_modern_seed_rejected.png` | Rejected near-photorealistic modern seed with invented valve hardware | `a1465b95a4b30c776a998a2f729d7a1bdd7a26508a9deb89f414d1242f3be4cc` |
| `switch_style_modern_mechanics_intermediate_chroma.png` | Corrected loose-ball mechanism before style simplification | `687f100d36977f4b1ed326799cb54f7098961db3ea1bf051a026b71226c32ce6` |
| `switch_style_master_v4_modern_chroma.png` | Preferred modern, mechanically restrained chroma master | `078ee9e8c5086c51a4836d9c0b34ae6662d8b39537dde83bd307928e0ea7d973` |
| `switch_style_master_v4_modern_alpha.png` | Preferred modern transparent master | `ed38072305320e57af73d0869e7e046a312c3df9733155b693ea65bd5c3ccaf6` |
| `switch_style_master_v5_continuous_flow_chroma.png` | Preferred continuous-flow chroma master | `48c4b2019987be52233618192bfb3635005817d2da65414c7f6d4f868b3e1c43` |
| `switch_style_master_v5_continuous_flow_alpha.png` | Preferred continuous-flow transparent master | `51b819283203fa3ca720ff6cf708257c831e3a891850f5b6b875a4fb8be2e4bc` |
| `switch_style_ground_texture_seed_rejected_chroma.png` | Rejected bean-like texture edit | `f1ac8690d7ccd0c94d6b4610bd126a32220ce7dd7a7f2e1b3b1814cee0456a8b` |
| `switch_style_master_v6_ground_texture_chroma.png` | Preferred textured-ground chroma master | `766672eece22a6550c440fc5d7f7a8a873c4b1b6f01cb5eee75ab289eb2e2d3e` |
| `switch_style_master_v6_ground_texture_alpha.png` | Preferred textured-ground transparent master | `084d79887fab1f27890c7d6917c003b647d98b3c5a1c377784741eeaa03f89ea` |
| `switch_style_master_v7_verified_ball_physics_chroma.png` | Preferred verified ball-valve physics chroma master | `ca8a781f7134155685582cd36aa79ce64ddd8347c6e65bb65f48fb5c70d6a8bf` |
| `switch_style_master_v7_verified_ball_physics_alpha.png` | Preferred verified ball-valve physics transparent master | `5371d8ce80c8923b177d912880e9317a8efc31d65fe2e29e5c9dd0eca2ca3852` |

## Reproduction record

The exact built-in image-generation prompts, reference order, intermediate
lineage, and alpha conversion command are recorded in
`prompts/brewing/starlit-tactile-style-exploration-2026-08-02.md`.

The built-in generator did not expose a seed or model/build identifier. Prompt
replay is therefore stochastic. Use the preserved PNG masters as edit/style
references; use the recorded prompt to continue the visual system rather than
expecting byte-identical regeneration.
