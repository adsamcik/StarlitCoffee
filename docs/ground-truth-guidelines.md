# Ground-Truth Dataset Annotation Guidelines

## Purpose

This document describes how to annotate a scanned coffee bag for the ground-truth
benchmark dataset. Each annotation captures the **human-verified correct values**
for every field the scan pipeline attempts to extract. The dataset is then used to
score the pipeline's accuracy across bags.

## Workflow

1. Place the bag on a flat surface with good lighting.
2. Scan the bag with the app (the pipeline produces its extraction).
3. **Independently** read the bag yourself and fill in the ground-truth entry.
4. Do **not** look at the pipeline output while annotating — this avoids
   confirmation bias.

## Fields (11 total)

| # | Field | Match type | Canonical form / rules |
|---|-------|-----------|----------------------|
| 1 | `name` | **Exact** | The coffee name or blend name exactly as printed. Include blend names and single-origin names. |
| 2 | `roaster` | **Semantic** | The roasting company. Case-insensitive. "Counter Culture Coffee" = "Counter Culture". |
| 3 | `origin` | **Semantic** | Country of origin. Prefer canonical noun form: "Ethiopia" not "Ethiopian". |
| 4 | `region` | **Semantic** | Sub-region within the country (e.g., "Yirgacheffe", "Huila"). |
| 5 | `variety` | **Semantic** | Coffee variety / cultivar. "SL28" = "SL-28". |
| 6 | `processType` | **Semantic** | Processing method. Canonical forms: Washed, Natural, Honey, Anaerobic, Wet-Hulled, etc. |
| 7 | `roastLevel` | **Exact** | One of: Light, Medium-Light, Medium, Medium-Dark, Dark. Pick the closest match from this list. |
| 8 | `tastingNotes` | **Set (Jaccard)** | Comma-separated list. Order does not matter. Score = \|intersection\| / \|union\|. |
| 9 | `altitude` | **Fuzzy** | Numeric range or single value in meters (e.g., "1800-2100"). SEMANTIC if midpoints are within 100 m. |
| 10 | `weight` | **Normalized** | Value with unit as printed (e.g., "12 oz", "340g"). Compared after normalizing to grams. |
| 11 | `roastDate` | **Exact** | ISO 8601 date (`YYYY-MM-DD`). Convert from any printed format. |

## Per-Field Rules

### `name`
Record the coffee's name or blend name exactly as printed on the bag. If the bag
shows "Big Trouble", write `Big Trouble`. Do not add the roaster name.

### `roaster`
The company that roasted the coffee. Minor variations are acceptable during
scoring (semantic match). Record what is printed; omit legal suffixes like
"LLC" or "Inc." if they are not part of the brand name.

### `origin`
Use the canonical country name in English: "Ethiopia", "Colombia", "Guatemala".
For multi-origin blends, list all origins separated by `/` (e.g.,
"Ethiopia/Colombia").

### `region`
The sub-region, cooperative, or farm name. If multiple regions are listed,
separate with `/`. If the bag only lists a country, leave this field null.

### `variety`
Coffee cultivar(s). Normalize hyphens and spacing: "SL 28" → "SL28". Multiple
varieties separated by `/`.

### `processType`
Use the canonical forms: Washed, Natural, Honey, Anaerobic, Wet-Hulled. If the
bag says "Fully Washed", record "Washed". If it says "Pulped Natural", record
"Honey" with a note.

### `roastLevel`
Must be one of the five canonical values: Light, Medium-Light, Medium,
Medium-Dark, Dark. If the bag uses non-standard terms (e.g., "City+" or
"Full City"), map to the closest canonical level and note the original term.

### `tastingNotes`
Record every tasting note printed on the bag as a comma-separated list.
Normalize case to lowercase. "Blueberry, Dark Chocolate, Citrus" →
`blueberry, dark chocolate, citrus`.

### `altitude`
Record as printed, preferring meters. If a range is given, use "low-high"
format (e.g., "1800-2100"). If given in feet, convert to meters and note the
original unit.

### `weight`
Record exactly as printed including the unit: "12 oz", "340g", "1 lb". Scoring
normalizes everything to grams for comparison.

### `roastDate`
Convert the printed date to ISO 8601 (`YYYY-MM-DD`). If only month/year is
given, use the first of the month and add a note.

## Edge Cases

### Multi-origin blends
List all origins separated by `/`:
- Origin: `"Ethiopia/Colombia"`
- Region: `"Yirgacheffe/Huila"` (or null if not specified per origin)

### "Not on label" vs "I can't read it"
Both result in a null ground truth with `isOnLabel = false` (not present) or
`isOnLabel = true` with `groundTruth = null` and a note explaining the
difficulty (e.g., "occluded by fold", "extremely small text").

### CJK / non-Latin scripts
Transliterate to Latin characters if possible. Note the original script in the
`notes` field (e.g., "Original: 珈琲豆"). If transliteration is uncertain,
record the original characters.

### Ambiguous fields
If a value could belong to multiple fields (e.g., a name that includes the
origin), record it in the most specific field and add a cross-reference note.

## Scoring Definitions

| Score | Meaning |
|-------|---------|
| **EXACT** | Values match exactly (case-insensitive, trimmed). |
| **SEMANTIC** | Values mean the same thing but differ in formatting (e.g., "Yirgacheffe" = "Yirgachefe"). |
| **WRONG** | Pipeline extracted a value, but it is incorrect. |
| **MISSING** | Field is on the label but the pipeline did not extract it. |
| **HALLUCINATED** | Pipeline produced a value that is not on the label at all. |
| **CORRECT_NULL** | Field is not on the label AND the pipeline correctly returned null. |

### Session Outcomes
- **COMPLETE**: ≥ 80% of on-label fields scored EXACT or SEMANTIC.
- **PARTIAL**: 40–80% of on-label fields scored EXACT or SEMANTIC.
- **FAILED**: < 40% correct, or any field scored HALLUCINATED.

### Batch Verdicts
- **SHIP**: ≥ 70% of sessions are COMPLETE, ≤ 20% hallucination rate.
- **ITERATE**: 50–70% session success rate.
- **RETHINK**: < 50% session success rate or > 30% hallucination rate.
