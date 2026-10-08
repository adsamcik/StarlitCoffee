# Research validation record

Reviewed on 2 October 2026 against repository baseline `490fc2d4`.

## Review completed

Seventeen dedicated method researchers produced the guides and evidence records,
one researcher per method, including Chemex. The lead reviewed every guide and
record, cross-checked key claims against the cited primary sources and current
code, requested corrections, and marked all 17 records `editorially_reviewed`.
The selected recipes retain their equipment, quantity basis, timer origin and
completion cue. Interpretations and starting recipes are labelled as adaptations
or app-authored content.

The pack contains 123 source records marked verified; a source reused across
methods counts more than once. Blocked and partial sources remain separately
identified. Its 158 app-audit observations include existing behavior, proposed
corrections, missing support and implementation requirements; this is not a
count of confirmed defects. The [accuracy audit](AUDIT.md) prioritizes actionable
findings and preserves existing runtime corrections and exact-recipe gates.

## Checks completed

```text
python tools/check_brewing_method_guides.py --require-reviewed
```

Result: **17 of 17 checked, all editorially reviewed, zero errors**. The full
machine-readable output is [validation.json](validation.json).

The checker validates required metadata, source-reference integrity, quantity
and ratio bases, mass/volume addition totals, cumulative quantities, numeric
time ordering, temperature bounds and audit-path existence. It does not assess
whether a source actually supports a claim; that required reading the source.

Nine deliberately invalid records were tested in temporary copies and rejected:

- Incorrect input ratio.
- Incorrect mass addition and cumulative total.
- Unknown source ID.
- Unsupported claim of physical brewing.
- Incorrect volume addition and cumulative total.
- Espresso beverage output treated as brew-water input.
- Missing mass-addition steps for a mass-input recipe.
- Missing primary author on a source.
- Invalid one-sided water-temperature bound.

All 110 explicit code line references were within the cited baseline files.
Local Markdown targets and section anchors passed inspection. Staged whitespace
and file-scope checks passed before the research commit.

## Validation boundary

This pass produced English research guides and reviewed implementation findings.
It did not physically brew coffee, measure yield or taste, execute the app,
review translations or accessibility speech, certify illustrations, or validate
a release. App implementation remains a separate phase, as requested.

Unresolved evidence is recorded per method. Editorial review does not certify
an unresolved variant or clear an exact-recipe gate. The implementation phase
must recheck current code and sources, carry corrections into the relevant UI
and execution plans, and validate the resulting behavior independently.
