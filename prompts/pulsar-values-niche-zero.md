# Research prompt: Niche Zero × NextLevel Pulsar dial settings

## Goal
Find **community-reported dial settings** for brewing on the **NextLevel
Pulsar** with a **Niche Zero** grinder. Aggregate enough data points to
ground a recommendation per filter type:

- **PAPER**
- **METAL_40K** (finer mesh, cleaner cup)
- **METAL_19K** (~150 µm holes, body-forward)

## Grinder spec (so you can normalise reports)
- Niche Zero — 63mm conical burrs, numbered dial 0–50
- 0 = espresso end, 50 = max coarse (cold brew)
- Hoffmann V60 starting point: ~28–32
- Filter brewing band typically 25–40

## Search angles
- `site:reddit.com Niche Zero Pulsar`
- `site:reddit.com "Niche" Pulsar grind`
- `site:home-barista.com Niche Pulsar`
- `"Niche Zero" "NextLevel"`
- `"Niche" Pulsar paper recipe`
- `"Niche Zero" Pulsar 19K`
- `"Niche Zero" Pulsar 40K`
- r/Niche subreddit (if exists) Pulsar threads
- Niche owners group Facebook posts

## Anti-patterns
- Don't conflate with **Niche Duo** (newer flat-burr model — different
  scale and cup profile).
- Be careful: many Niche owners use it primarily for espresso, so filter
  Pulsar reports may be sparser. Prioritise dedicated filter brewers in
  the Niche community.

## Output format

```
RAW DATA POINTS:
- 32 | PAPER | 20 g, 1:17, 4:00 | r/coffee user X | <url>
- 34 | PAPER | 22 g, 1:16     | home-barista Y | <url>
- 37 | METAL_40K | ...         | <source>       | <url>
- 40 | METAL_19K | ...         | <source>       | <url>
- ...

SYNTHESIS:
PAPER:    median <N> | range <low>-<high> | n=<count>
METAL_40K: median <N> | range <low>-<high> | n=<count>
METAL_19K: median <N> | range <low>-<high> | n=<count>

CONFIDENCE: <high (5+) | medium (2-4) | low (0-1)>
NOTES: <agitation, dose, anything notable>
```

## Current app value (replace if community data disagrees)
- PAPER: 32–40, suggested 35
- METAL_40K: 35–42, suggested 37
- METAL_19K: 37–44, suggested 39

## Note
If Pulsar-specific Niche reports are sparse, fall back to:
- Hoffmann's Niche V60 setting (~28–32) as PAPER anchor
- +2 for 40K, +4 for 19K based on the verified Pulsar filter ordering
- Document the extrapolation explicitly in the source field
