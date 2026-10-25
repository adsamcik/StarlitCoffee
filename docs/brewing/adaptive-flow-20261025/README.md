# Adaptive brewing content

Brewing execution and optional teaching content have separate owners. Existing start factories, stage plans, reducers, clocks, restoration and completion controls remain authoritative. This refactor changes presentation rather than introducing another brewing engine.

| Boundary | Responsibility |
| --- | --- |
| Source adapters | Validate identity, units, source revision, locale and approved illustrations; normalize reviewed, exact and generic content. |
| Guidance policy + GuidanceContentProjector | Resolve session → profile → family → default density, then project reader/live/Help content consistently. |
| BrewStepContent / BrewReaderContent / BrewOverviewContent | Carry only the available presentation fields. Nullable fields and empty lists deliberately omit sections. |
| LiveBrewStep / GuideStepCanvas / overview renderer | Compose supplied targets, illustrations, safety and details without interpreting recipe schemas. |
| Durable session | Own physical steps, clock origins, timers, safety codes and permitted actions regardless of optional guidance. |

A target-only step needs no explanatory text or image:

```kotlin
BrewStepContent(
    id = stage.contentId,
    referenceCues = stage.referenceCues,
)
```

A reader can be assembled directly from normalized steps:

```kotlin
BrewReaderContent(
    key = recipeId,
    steps = listOf(targetOnlyStep, illustratedStep),
    // overview and commonContent can also be omitted
)
```

The same step renderer serves reader and Help. Live consumes a smaller LiveBrewContent packet alongside the existing session presentation, preserving timer → target → visual → action hierarchy. Missing optional copy produces no unavailable message or empty image frame. Secondary details produce a disclosure only when present. No readable steps means no Read/Steps controls; an independently authorized Brew action remains available.

Source selection for a restored session is resolved once for live, Help and read-ahead. Exact readers require the actual source quantities and stage plan. Missing sibling guidance is explicitly Partial and preserves compatible present stages and common content; it never skips a physical stage. Ambiguous stage mappings are rejected. Common Switch content remains readable while workflow selection is required.

Authored density records may be sparse. A missing density derives from base metadata; an explicitly present record keeps its null fields omitted. Base warnings survive density omission. Legacy String fields remain compatible and are normalized to nullable presentation fields at the adapter boundary.

Mandatory reviewed warnings are projected separately from optional teaching copy. Explicit reviewed source metadata controls familiar advice shown in Help; unknown required warnings remain live. Existing stage-safety and physical completion controls remain independent. Source-specific visual/wording associations are outside layout code.

Optional artwork remains optional presentation. An admitted illustration requires an approved asset and a nonblank truthful caption; source adapters still enforce scope, revision/hash, alias and localization checks. No artwork, frozen source contracts or recipe release gates were relaxed. Required-art/evidence/hardware/localization failures still block the corresponding exact recipe.

Before screenshots are actual API37 captures of the unchanged base commit 7caf5278, copied from its retained validation evidence. Verification results and after captures are recorded with this change.

See [verification](verification.md) and the [before/after screenshot gallery](screenshots.md).
