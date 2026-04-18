# Research prompt: Turin DF64 (Gen 2) × NextLevel Pulsar dial settings

## Goal
Find **community-reported dial settings** for brewing on the **NextLevel
Pulsar** with a **Turin DF64 Gen 2** (or G-IOTA / DF64P clones with
matching stock 64mm flat burrs). Aggregate enough data points to ground
a recommendation per filter type:

- **PAPER**
- **METAL_40K** (finer mesh, cleaner cup)
- **METAL_19K** (~150 µm holes, body-forward)

## Grinder spec (so you can normalise reports)
- Turin DF64 / DF64 Gen 2 — 64mm flat steel burrs (stock SSP-clone)
- **Dial scale: 0–90** (per Honest Coffee Guide), NOT 0–10!
  - Some users write "4.0" meaning the big number 4 (= dial value 40)
  - Some write "40" directly
  - Some write "4 + 5 ticks" = 45
  - **Treat any value < 11 as multiply-by-10** unless context says otherwise
- V60 typical 30–50, French Press 55–80
- Stock burrs are espresso-tuned; SSP MP / Brew burrs shift filter ~10
  finer

## Search angles
- `site:reddit.com DF64 Pulsar`
- `site:reddit.com "DF64 Gen 2" Pulsar`
- `site:home-barista.com DF64 Pulsar`
- `"DF64" "NextLevel"`
- `"DF64" Pulsar paper grind`
- `"DF64" Pulsar 19K`
- `"DF64" Pulsar 40K`
- `"DF64P" Pulsar`
- `"G-IOTA" Pulsar` (clone with same burrs)
- r/espresso DF64 Pulsar threads
- DF64 Owners Facebook group posts

## Anti-patterns
- **Reject SSP burr reports** unless explicitly noted — burr swaps shift
  ranges ~10 dial units finer.
- Reject DF64V (variable speed) reports if the speed isn't standard
  (1500 RPM affects fines profile).
- **CRITICAL: Normalise the dial scale.** Many users will say "4" or "5"
  meaning a coarse setting around 40–50; some say it directly. Read
  context carefully.
- Don't include espresso settings.

## Output format

```
RAW DATA POINTS:
- 38 | PAPER | 20 g, 1:17, 4:00 | r/coffee user X | <url> | dial-notation: "3.8"
- 42 | PAPER | 22 g, 1:16     | home-barista Y | <url> | dial-notation: "4.2"
- 40 | METAL_40K | ...         | <source>       | <url>
- 45 | METAL_19K | ...         | <source>       | <url>
- ...

SYNTHESIS:
PAPER:    median <N> | range <low>-<high> | n=<count>
METAL_40K: median <N> | range <low>-<high> | n=<count>
METAL_19K: median <N> | range <low>-<high> | n=<count>

CONFIDENCE: <high (5+) | medium (2-4) | low (0-1)>
NOTES: <stock burrs vs SSP, agitation, dose, anything notable>
```

## Current app value (replace if community data disagrees)
- PAPER: 30–45, suggested 36
- METAL_40K: 32–48, suggested 38
- METAL_19K: 35–50, suggested 40

## Note
DF64 owners often run filter brewing as a secondary use (it's espresso-
focused). Reports may be thinner here. If Pulsar-specific data is sparse,
fall back to Honest Coffee Guide's V60 chart (30–50) as PAPER anchor and
extrapolate the metal offsets.
