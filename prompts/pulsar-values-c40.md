# Research prompt: Comandante C40 MK4 × NextLevel Pulsar dial settings

## Goal
Find **community-reported dial settings** for brewing on the **NextLevel
Pulsar** with a **Comandante C40 MK4** (red clix or std). Aggregate enough
data points to ground a recommendation per filter type:

- **PAPER**
- **METAL_40K** (finer mesh, cleaner cup)
- **METAL_19K** (~150 µm holes, body-forward)

## Grinder spec (so you can normalise reports)
- Comandante C40 MK4 — pure click count from zero
- ~30 µm/click
- "22 clicks" = the classic Hoffmann V60 setting
- Standard click range for filter: 18–35
- "Red clix" (1/3 click increments) vs "std" (full clicks): if a user
  cites red-clix values like "20 (R)" or "20.33", convert to nearest
  whole click for our app.

## Search angles
- `site:reddit.com Comandante Pulsar`
- `site:reddit.com C40 Pulsar grind`
- `site:home-barista.com Comandante Pulsar`
- `site:home-barista.com C40 Pulsar`
- `"C40" Pulsar paper`
- `"Comandante" Pulsar 19K`
- `"Comandante" Pulsar 40K`
- `"C40" "NextLevel"`
- Lance Hedrick / Hoffmann Pulsar video comments mentioning C40
- Coffee Forums UK / Coffee Snob — Pulsar threads

## Anti-patterns
- Don't include MK3 reports unless explicitly noted — burrs are similar
  but settings reported can be ~1 click off.
- Exclude espresso settings (C40 isn't an espresso grinder anyway, but
  occasionally users post Moka pot data — exclude that too).
- Beware of "20 clicks" being assumed as either Pulsar or generic V60 —
  only count if Pulsar is explicit.

## Output format

```
RAW DATA POINTS:
- 23 clicks | PAPER | 20 g, 1:17, 4:00 | r/coffee user X | <url>
- 25 clicks | PAPER | 22 g, 1:16     | home-barista Y | <url>
- 27 clicks | METAL_40K | ...         | <source>       | <url>
- 30 clicks | METAL_19K | ...         | <source>       | <url>
- ...

SYNTHESIS:
PAPER:    median <N> clicks | range <low>-<high> | n=<count>
METAL_40K: median <N> clicks | range <low>-<high> | n=<count>
METAL_19K: median <N> clicks | range <low>-<high> | n=<count>

CONFIDENCE: <high (5+) | medium (2-4) | low (0-1)>
NOTES: <agitation style, dose, anything notable>
```

## Current app value (replace if community data disagrees)
- PAPER: 24–28, suggested 25
- METAL_40K: 26–30, suggested 27
- METAL_19K: 28–32, suggested 29
