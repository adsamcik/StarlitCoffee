# Research prompt: Baratza Encore ESP × NextLevel Pulsar dial settings

## Goal
Find **community-reported dial settings** for brewing on the **NextLevel
Pulsar** with a **Baratza Encore ESP** grinder. Aggregate enough data
points to ground a recommendation per filter type:

- **PAPER**
- **METAL_40K** (finer mesh, cleaner cup)
- **METAL_19K** (~150 µm holes, body-forward)

## Grinder spec (so you can normalise reports)
- Baratza Encore **ESP** (NOT base Encore — burrs differ)
- Numbered dial 1–40
  - 1–15: espresso band
  - 10–40: filter band
- ~25 µm/step (rough)
- V60 typical 13–18, French Press 30–35

## Search angles
- `site:reddit.com "Encore ESP" Pulsar`
- `site:reddit.com Encore Pulsar grind`
- `site:home-barista.com Encore ESP Pulsar`
- `"Encore ESP" "NextLevel"`
- `"Baratza Encore ESP" Pulsar paper`
- `"Encore ESP" Pulsar 19K`
- `"Encore ESP" Pulsar 40K`
- r/baratza Pulsar threads
- Baratza official Pulsar chart (if exists)

## Anti-patterns
- **Reject base Encore reports** (the original non-ESP) — burrs and dial
  range differ. Only Encore ESP counts.
- Don't include Vario, Sette, Virtuoso, or Forte data.
- Watch out for "Encore" without "ESP" — check the post context.

## Output format

```
RAW DATA POINTS:
- 18 | PAPER | 20 g, 1:17, 4:00 | r/coffee user X | <url>
- 22 | PAPER | 22 g, 1:16     | r/baratza Y    | <url>
- 24 | METAL_40K | ...         | <source>       | <url>
- 26 | METAL_19K | ...         | <source>       | <url>
- ...

SYNTHESIS:
PAPER:    median <N> | range <low>-<high> | n=<count>
METAL_40K: median <N> | range <low>-<high> | n=<count>
METAL_19K: median <N> | range <low>-<high> | n=<count>

CONFIDENCE: <high (5+) | medium (2-4) | low (0-1)>
NOTES: <agitation, dose, anything notable>
```

## Current app value (replace if community data disagrees)
- PAPER: 18–24, suggested 20
- METAL_40K: 20–26, suggested 22
- METAL_19K: 22–28, suggested 24

## Note
The Encore ESP is one of the most popular budget grinders so community
data may be richest here. If volume is high, prefer recent (2024+) posts
since the grinder launched 2023 and early-adopter recipes were noisy.
