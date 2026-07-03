# Real coffee-bag validation corpus

This directory holds a **small, real-world** ground-truth set of coffee-bag
photos, used to check whether extraction improvements measured on the *synthetic*
corpus (`../synthetic-coffee-bag-corpus/`) actually hold on real bags. The
synthetic corpus is a fast regression/stress suite; a synthetic delta is only a
**hypothesis** until a real-bag, paired comparison confirms it (see "Why" below).

> **This set ships empty on purpose.** Real bag photos are user-supplied — drop
> your own captures here following the format below. Nothing here is uploaded;
> it is local test data only.

## File layout (mirrors the synthetic corpus)

Per bag, three files sharing one slug `rcb-<NNN>-<locale>-q<tier>_<slug>`:

- `rcb-001-en-q1_example.front.jpg` — front photo (real capture).
- `rcb-001-en-q1_example.back.jpg`  — back photo, or omit if single-side.
- `rcb-001-en-q1_example.metadata.json` — ground truth + stratification tags.

### metadata.json schema (same keys as the synthetic sidecars)

```json
{
  "schema_version": 2,
  "fixture_type": "coffee_bag",
  "id": "rcb-001-en-q1",
  "automation_ready": true,
  "photos": { "front": "rcb-001-en-q1_example.front.jpg", "back": null },
  "language": ["en"],
  "fields": {
    "name": "…", "roaster": "…", "origin": "…", "region": "…", "farm": null,
    "variety": "…", "process": "…", "roastLevel": "…", "tastingNotes": "…",
    "altitude": null, "weight": "250 g", "roastDate": "2026-01-15",
    "expiryDate": null, "isDecaf": false
  },
  "extras": {
    "captureTier": "Q1",
    "bagMaterial": "…",
    "sourceCollection": "real",
    "riskTags": ["…"],
    "bagDescription": "…"
  },
  "notes": "…"
}
```

Set a field to `null` when it is genuinely **not visible** on the bag — the
scorer credits a correct abstention, so do not invent a value.

## Stratification — capture tiers (must match the synthetic tiers)

Photograph each real bag deliberately across the same quality tiers so the
per-tier report is comparable to synthetic. From `docs/synthetic-coffee-bag-corpus.md`:

| Tier | Meaning |
|------|---------|
| `Q0` | Studio perfect |
| `Q1` | Good real-world |
| `Q2` | Ordinary consumer |
| `Q3` | Degraded but recoverable (night/glare/motion softness) |
| `Q4` | Failure mode |

Also spread across **languages/locales** and **label styles** (minimalist vs.
dense, matte vs. glossy, curved/crinkled) so a regression hidden in one stratum
is not masked by a good aggregate — always read the per-tier / per-locale slices,
not just the overall number.

## How much real data do you need?

Per-rate 95% Wilson confidence-interval half-widths near 50% accuracy (the
report prints these as `recall=…±Npp`):

| Real bags | ± half-width | Verdict |
|-----------|--------------|---------|
| 20–30 | ±17–20pp | Exploratory only; catches gross synthetic overfit |
| 50 | ±13pp | Go/no-go gate for large (≥20pp) effects |
| 100 | ±10pp | Minimum to trust a claimed 10–20pp improvement |
| 200+ | ±7pp | Preferred for ~10pp decisions |

Compare **old vs. new pipeline on the SAME bags** (a paired design), not two
independent samples — a paired/McNemar view is far more sensitive. Treat any
delta smaller than the printed CI as noise.

## Why real validation matters (short version)

Document-AI benchmarks (SROIE, CORD, FUNSD) are built from real, messy documents
precisely because synthetic-only performance is known to diverge from
deployment. Keep the synthetic corpus as the fast regression suite; gate real
decisions on this set. A near-zero-cost source of real ground truth is the app's
own **opt-in on-device correction log** (Settings → Developer → "Log scan
corrections") — user edits of the review screen are naturally-occurring
(model value, corrected value) pairs. Note the selection bias: an explicit edit
is a strong "wrong" signal, but "not edited" is only a weak positive (users skip
low-stakes fields like tasting notes).
