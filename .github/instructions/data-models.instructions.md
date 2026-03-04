---
applyTo: "app/src/main/java/**/data/model/*.kt"
description: "Domain model conventions for Starlit Coffee"
---

<!-- context-init:managed -->
- Enums include all relevant properties in constructor (displayName, defaults, etc.)
- Adding a new brew method = adding enum entry to `BrewMethod` with full defaults — no screen changes needed
- `StrengthPreset.ratioOffset` is Int: LIGHT=+1, BALANCED=0, STRONG=-1
- `FilterType` must include `description` and `cupProfile` properties
- `TasteFeedback.getAdjustmentText()` takes `hasGrinder: Boolean` and `isPulsar: Boolean`
- Pulsar default ratio is 1:17 (not 1:16)
- Grinder recommendations are matched by `grinderId + methodId + filterType` triple
- Always present grind settings as ranges, never single exact values
