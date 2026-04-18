# Research prompt: Fellow Ode Gen 2 × NextLevel Pulsar dial settings

## Goal
Find **community-reported dial settings** for brewing on the **NextLevel
Pulsar** with a **Fellow Ode Gen 2** grinder. Aggregate enough data points
to ground a recommendation per filter type:

- **PAPER**
- **METAL_40K** (finer mesh, cleaner cup)
- **METAL_19K** (~150 µm holes, body-forward)

## Grinder spec (so you can normalise reports)
- Fellow Ode **Gen 2** — 64mm flat steel burrs, numbered dial 1–11 in 0.5
  increments
- **CRITICAL**: Gen 1 charts do NOT apply (Gen 2 grinds ~25 µm finer per
  step). Only count reports explicitly for **Gen 2**.
- Filter range typically 4–8 on the dial
- Cannot grind for espresso or moka pot

## Search angles
- `site:reddit.com "Ode Gen 2" Pulsar`
- `site:reddit.com Fellow Ode Pulsar`
- `site:home-barista.com Ode Pulsar`
- `"Ode Gen 2" "NextLevel"`
- `"Fellow Ode" Pulsar paper`
- `"Ode 2" Pulsar 19K`
- `"Ode 2" Pulsar 40K`
- Reddit r/Coffee_Pulsar (if exists)
- Lance Hedrick Ode Gen 2 review video comments

## Anti-patterns
- **Reject Gen 1 reports** — burrs differ. Only count Gen 2.
- Don't extrapolate from V60 charts unless user explicitly says "I use the
  same setting on Pulsar".
- Be aware Fellow's official chart starts coarser than community uses for
  V60 — Pulsar may follow community values more closely.

## Output format

```
RAW DATA POINTS:
- 5.0 | PAPER | 22 g, 1:17, 4:00 | r/coffee user X | <url>
- 5.5 | PAPER | 20 g, 1:16     | home-barista Y | <url>
- 5.5 | METAL_40K | ...         | <source>       | <url>
- 6.0 | METAL_19K | ...         | <source>       | <url>
- ...

SYNTHESIS:
PAPER:    median <X.Y> | range <low>-<high> | n=<count>
METAL_40K: median <X.Y> | range <low>-<high> | n=<count>
METAL_19K: median <X.Y> | range <low>-<high> | n=<count>

CONFIDENCE: <high (5+) | medium (2-4) | low (0-1)>
NOTES: <agitation style, dose, anything notable>
```

## Current app value (replace if community data disagrees)
- PAPER: 3–5, suggested 4
- METAL_40K: 4–6, suggested 5
- METAL_19K: 5–7, suggested 6

## Note
If you find very few Pulsar-specific reports, fall back to Fellow's
**official Gen 2 V60 setting** as a proxy for PAPER (Pulsar paper ≈ V60
paper for the Ode), and document that explicitly as the source.
