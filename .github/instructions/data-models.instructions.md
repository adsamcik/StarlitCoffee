---
applyTo: "app/src/main/java/**/data/model/*.kt"
description: "Domain model conventions for Starlit Coffee"
---

<!-- context-init:managed -->
- `BrewMethod` enum entries own method defaults: display name, icon, ratio, bloom, pulses, temperature, time target, capacity, grind descriptor, bloom duration, and absorption ratio.
- Pulsar default ratio is 17f. Do not encode 1:16 as the Pulsar default.
- `BrewMethod.defaultRatioPresets` derives bright/balanced/rich ratio presets from each method's default ratio.
- `FilterType` is Pulsar-specific and must include `displayName`, `description`, and `cupProfile`.
- `StrengthPreset.ratioOffset` is `Int`: LIGHT=+1, BALANCED=0, STRONG=-1.
- `DefaultGrinders` is static fallback/test data; production code should prefer `GrinderDataSource.getInstance(context)`.
- Grinder recommendations match by `grinderId`, `methodId`, and `filterType`; IDs must match canonical strings such as `1zpresso-zp6-special`.
- Present grind guidance as ranges plus adjustment notes, never as universal exact settings.
- Keep data models Android-free unless they intentionally reference resources such as `@StringRes` IDs.
- When adding coffee metadata enums/models, keep normalizers, scan field support, and tests aligned.
