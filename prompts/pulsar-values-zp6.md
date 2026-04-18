# Research prompt: 1Zpresso ZP6 Special × NextLevel Pulsar dial settings

## Goal
Find **community-reported dial settings** for brewing on the **NextLevel
Pulsar** with a **1Zpresso ZP6 Special** grinder. Aggregate enough data
points to ground a recommendation per filter type:

- **PAPER** (default paper filter)
- **METAL_40K** (finer mesh, cleaner cup)
- **METAL_19K** (~150 µm holes, body-forward)

## Grinder spec (so you can normalise reports)
- 1Zpresso ZP6 Special — external numbered dial
- 9 numbered marks × 10 micro-clicks per number = **90 clicks/rotation**
- ~22 µm/click
- Shorthand: "5.2" means number 5 + 2 micro-clicks (some users write "52
  clicks" instead — same value).
- Translate any "clicks from zero" reports into X.Y notation:
  - 50 clicks = 5.0
  - 47 clicks = 4.7
  - 62 clicks = 6.2

## Search angles (run several)
- `site:reddit.com ZP6 Pulsar`
- `site:reddit.com "1Zpresso" Pulsar`
- `site:reddit.com ZP6 Special Pulsar grind`
- `site:home-barista.com ZP6 Pulsar`
- `site:home-barista.com 1Zpresso Pulsar`
- `"ZP6" Pulsar paper grind`
- `"ZP6" Pulsar 19K`
- `"ZP6" Pulsar 40K`
- `"ZP6 Special" "NextLevel"`
- `"Lance Hedrick" ZP6 Pulsar` (he switches grinders for reviews)
- Discord: search "Coffee" / "Specialty Coffee" Discord servers if
  accessible — `ZP6 Pulsar` keyword
- YouTube comments on Lance Hedrick / Hoffmann Pulsar videos —
  often have viewer recipes

## Anti-patterns
- Don't include reports for **other 1Zpresso models** (J-max, K-Plus, ZP6
  regular non-Special, X-Pro, etc.) — burrs differ. Only ZP6 **Special**
  burrs (the brew-focused titanium-coated ones).
- Be careful: many users say "5" without a decimal — assume that means
  5.0 unless they say "5 and a few micro clicks".
- Don't include espresso settings.

## Output format

Collect raw data points first:

```
RAW DATA POINTS:
- 5.2 | PAPER | 20 g, 1:17, 4:00 brew | r/coffee user X | <url>
- 4.8 | PAPER | 18 g, 1:16, 3:45    | home-barista user Y | <url>
- 5.5 | METAL_40K | ...             | <source>            | <url>
- 6.0 | METAL_19K | ...             | <source>            | <url>
- ...
```

Then synthesise:

```
SYNTHESIS:
PAPER:    median <X.Y> | range <low>-<high> | n=<count>
METAL_40K: median <X.Y> | range <low>-<high> | n=<count>
METAL_19K: median <X.Y> | range <low>-<high> | n=<count>

CONFIDENCE: <high (5+ data points) | medium (2-4) | low (0-1)>
NOTES: <agitation style, dose, anything that shifts the dial>
```

Aim for **at least 3 data points per filter** if possible. If a filter
has no community reports, say so explicitly — that's a real finding.

## Current app value (replace if community data disagrees)
- PAPER: 5.0–5.4, suggested 5.2
- METAL_40K: 5.3–5.7, suggested 5.5
- METAL_19K: 5.5–5.9, suggested 5.7
