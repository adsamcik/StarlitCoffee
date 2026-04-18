# Research prompt: Concrete Pulsar dial settings per grinder × filter

## Goal
Find **published, sourced dial settings** for the **NextLevel Pulsar dripper**
on each of these 6 grinders, ideally for all three filter variants
(PAPER, METAL_40K, METAL_19K) with a 20 g, 1:17 baseline recipe.

If exact values aren't published, find:
- Adjacent published values for the same grinder × similar dripper
  (V60 with paper, Origami flat, Hario Switch immersion mode) that can be
  used as a defensible anchor point.
- Lance Hedrick / James Hoffmann / Coffee ad Astra / Pulsar-specific reviews
  that mention a click count.

## Grinders to cover
1. **1Zpresso ZP6 Special** — dial in X.Y notation (X = number 0–9, Y =
   micro-click 0–9, total 90 clicks/rotation, ~22 µm/click)
2. **Comandante C40 MK4** — pure clicks from zero
3. **Fellow Ode Gen 2** — numbered dial, settings 1–11 in 0.5 increments
4. **Baratza Encore ESP** — numbered dial 1–40 (1–15 espresso, 10–40 filter)
5. **Niche Zero** — numbered dial 0–50
6. **Turin / DF64 Gen 2** — numbered dial 0–90 (per Honest Coffee Guide)

## Filters
- **PAPER** — high-flow paper filter (NextLevel's default)
- **METAL_40K** — finer mesh (40,000 holes), cleaner cup, can clog
- **METAL_19K** — coarser mesh (19,000 holes / ~150 µm), body-forward

## Search angles
For each grinder, try these queries (substitute grinder name):

- `"NextLevel Pulsar" "<grinder>"`
- `"Pulsar dripper" "<grinder>" grind setting`
- `"Pulsar" "<grinder>" click`
- `"<grinder>" Pulsar paper recipe`
- Reddit: `site:reddit.com Pulsar <grinder>`
- YouTube transcripts: `"Lance Hedrick" Pulsar <grinder>`,
  `"James Hoffmann" Pulsar <grinder>`
- `"Pulsar" "metal disc" 19K grind`
- `"Pulsar" "40K" grind`

For metal filters specifically, try:
- `"NextLevel" "stainless" recipe`
- `"Pulsar" "booster plate" grind`
- Lance Hedrick has multiple Pulsar review videos — check transcripts /
  pinned recipes
- Coffee ad Astra "Pulsar Dripper" article

## Preferred sources (in order)
1. **NextLevel Coffee official** — recipe cards, gear-guide PDFs, blog posts
   on nextlevel-coffee.com
2. **Lance Hedrick YouTube** — has dedicated Pulsar tutorials with explicit
   grind callouts; transcript or pinned comment usually has the recipe
3. **Coffee ad Astra (Jonathan Gagné)** — "The Pulsar Dripper" article and
   follow-ups, scientific approach with explicit settings
4. **James Hoffmann** — Pulsar review video transcript/recipe
5. **Grinder vendor's own Pulsar chart** — 1Zpresso, Comandante, Fellow,
   Baratza, Niche, DF64 community sometimes publish Pulsar entries
6. **Reddit r/coffee, r/pourover** — top-upvoted Pulsar threads with
   grinder-specific comments
7. **Home-barista.com** — Pulsar mega-thread

## Anti-patterns
- Don't extrapolate from V60 charts unless explicitly framed as "use V60
  setting for Pulsar paper". Pulsar's valve mechanic changes the calculus.
- Don't quote "medium-coarse" or "use your V60 setting" — we need numbers.
- If a source gives only one filter (usually paper), say so and don't invent
  the metal-filter offsets.

## Output format

For each grinder, fill out:

```
GRINDER: <name>
========
PAPER:
  setting: <click count or dial value>  | range: <low>-<high>
  source: <url> — <brief quote>
  recipe context: <dose, ratio, time>

METAL_40K:
  setting: <value or "no published data, infer +X from paper">
  source: <url or "extrapolated from <reference>">

METAL_19K:
  setting: <value or "no published data, infer +X from paper">
  source: <url or "extrapolated from <reference>">

CONFIDENCE: <high | medium | low>
NOTES: <anything important — burr swap effects, brewer agitation style, etc>
```

Repeat for all 6 grinders. If nothing is published for a grinder, say so
explicitly — that's a valid finding and tells me to keep my current
inferred values.

## Bonus
If you find a Pulsar grind chart that covers multiple grinders in one
place, prioritise that and note the source — it would let me anchor
all values to a single reference frame.
