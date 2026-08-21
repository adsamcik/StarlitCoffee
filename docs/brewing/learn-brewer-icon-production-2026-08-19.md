# Learn brewer icon production — 2026-08-19

## Outcome

Learn uses thirteen profile-specific brewer icons generated with the built-in
OpenAI ImageGen tool. The selected set uses one tactile illustration language,
but preserves the physical geometry that helps users distinguish closely
related methods at a glance.

The source candidates remain in `docs/brewing/learn-icon-candidates/`. Shipping
assets are transparent, lossless 256 × 256 WebP files named
`learn_brewer_icon_*.webp` in `app/src/main/res/drawable-nodpi/`.

## Shared prompt contract

Every production prompt described the asset as a tiny 44–52 dp icon for an M3
Expressive Learn selector. After the first candidate established the visual
language, it was supplied as the style master for every subsequent call. The
shared direction was:

> Match the approved style master's premium tactile 3D enamel-pin / soft-clay
> rendering, deep ink-navy outline weight, periwinkle planes, warm ivory
> highlights, restrained coffee-brown or copper accent, camera angle, soft
> upper-left lighting, and compact visual density. Center one isolated brewer
> in a front three-quarter view, filling about 82% of a square canvas with a
> safe margin. Use genuinely transparent alpha. Keep the silhouette readable at
> 48 px. Do not include a scene, circular badge, border, platform shadow, text,
> numbers, logo, watermark, or checkerboard pattern.

The V60 02 style-master prompt was:

> Use case: stylized-concept. Asset type: a tiny brewer-profile icon displayed
> at 44–52 dp inside the Learn selector shown in the reference screenshot.
> Input image: reference only for the M3 Expressive palette, visual weight, and
> intended UI scale; do not reproduce any screen, card, circle, text, or
> interface. Primary request: create a single icon of a V60 02 conical
> pour-over dripper, clearly identifiable by its broad open cone, spiral
> interior ribs, small side handle, and flat base ring. Style/medium: premium
> tactile 3D enamel-pin / soft-clay product icon, simplified and slightly
> playful, with a bold clean silhouette and only essential detail that remains
> readable at 48 px. Composition: centered front three-quarter view, isolated
> object filling about 82% of a square canvas, consistent 6% safe margin. Color
> palette: deep ink navy outlines and body, periwinkle-blue planes, warm ivory
> highlights, and one restrained coffee-brown interior accent, matching the
> attached Learn screen. Lighting: soft upper-left studio light with compact
> dimensional shading. Background: genuinely transparent alpha. Constraints:
> exactly one brewer, no server, no cup, no beans, no hands, no scene, no
> circular badge, no border, no cast-shadow platform, no text, no letters, no
> numbers, no logo, no watermark, no checkerboard pattern. Produce a square
> icon asset.

## Profile-specific requests and selections

| Profile ID | Distinguishing prompt request | Selected ImageGen output | Shipping asset |
| --- | --- | --- | --- |
| `v60_02` | Broad V60 02 cone, spiral ribs, side handle, flat base ring | `exec-c81a03ca-bda1-420d-94f8-6bdc35fcf7e8.png` | `learn_brewer_icon_v60_02.webp` |
| `v60_unspecified` | Narrower generic V60 cone with spiral ribs and slim curved handle | `exec-9d79b107-84ad-47b7-9e0d-833db5e5fd7d.png` | `learn_brewer_icon_v60.webp` |
| `manual_wave_185` | Broad shallow flat-bottom basket with a fluted Wave filter and small handle | `exec-c907db69-383f-44fb-a456-e24051eff320.png` | `learn_brewer_icon_wave_185.webp` |
| `manual_wedge_generic` | Rectangular opening, trapezoid wedge body, flat slotted bottom, side handle | `exec-c0a13b1f-24aa-4500-920b-95ba53473ce2.png` | `learn_brewer_icon_wedge.webp` |
| `manual_thick_paper_carafe` | Continuous hourglass glass vessel, thick folded filter, dark collar and wood tie | `exec-9859dc98-dd31-4096-9ff9-407ba1e659e3.png` | `learn_brewer_icon_carafe.webp` |
| `manual_conical_generic` | Smooth narrow cone with plain walls, paper filter, small tab, no spiral ribs | `exec-3ba7391c-614f-48a4-ac9d-3e5286732b10.png` | `learn_brewer_icon_conical.webp` |
| `clever_style` | Clear tapered immersion chamber, lid, large handle, base and release-valve nub | `exec-63415639-0e91-49af-b81b-3331edfe93d1.png` | `learn_brewer_icon_clever.webp` |
| `hario_switch` | Glass V60 cone in a chunky immersion base with a readable lever and steel ball | `exec-339db5b3-f4a9-4b57-a56a-7597eb999f11.png` | `learn_brewer_icon_switch.webp` |
| `cezve_generic` | Hammered copper bulb, narrow neck, pouring lip and long upswept navy handle | `exec-54f2f2b3-1b4a-464a-91b1-7b55adf61a8f.png` | `learn_brewer_icon_cezve.webp` |
| `automatic_batch_generic` | Rounded batch machine with reservoir, basket, shower head and integrated carafe | `exec-aa680068-cba9-4c4b-91dc-76b0d7de40cf.png` | `learn_brewer_icon_batch.webp` |
| `automatic_single_cup_generic` | Tall podless filter machine, short spout and tiny cup on its drip tray | `exec-0f1cce7d-d83c-4e5f-a5e1-9e352d2d5526.png` | `learn_brewer_icon_single_cup.webp` |
| `vietnamese_phin` | Brushed cylindrical chamber, lid, perforated press and broad drip plate | `exec-b3fd891c-5312-4d6d-875a-fa8cd15cf917.png` | `learn_brewer_icon_phin.webp` |
| `pulsar_standard` | Straight clear cylinder, shower-screen cap, four-footed base and side valve lever | `exec-1afffc08-6940-4b2b-bd5d-c9dc653626c5.png` | `learn_brewer_icon_pulsar.webp` |

## References and review loop

The Learn screenshot established palette and target scale. Existing reviewed
guide artwork supplied brewer-anatomy references for Wave, wedge, carafe,
Clever, Switch, cezve, automatic batch, and phin. A product photograph supplied
the exact Pulsar chamber, base, shower-screen, and valve anatomy.

Each candidate was inspected immediately after generation. The full selected
set was then converted to transparent alpha, reduced to 256 px, and composited
at the equivalent of a 50 dp icon inside a 68 dp periwinkle badge. The contact
sheet in `docs/brewing/learn-icon-candidates/learn-brewer-icons-contact.png`
passed the final small-size review: every silhouette remained identifiable, no
important edge clipped, and adjacent brewer types remained distinguishable.

Some ImageGen responses baked a pale checkerboard into RGB pixels despite the
transparent-background request. Production cleanup removed only the connected
corner background, preserved the outlined object, and verified all thirteen
shipping files as `srgba` with a fully transparent corner pixel.
