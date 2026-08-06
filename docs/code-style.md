# Code style policy

Starlit Coffee uses Kotlin's official conventions, adapted deliberately for an
Android application built with Jetpack Compose and generated brewing catalogs.
The policy is enforced by `.editorconfig`, Detekt, Android lint, and continuous
integration.

## Formatting

- Use UTF-8, a final newline, spaces, and four-space indentation in source and
  XML. JSON and YAML use two spaces.
- Kotlin and Kotlin DSL follow the official Kotlin style. Use trailing commas
  for multiline declarations and calls so reviews produce smaller diffs.
- Production Kotlin lines are limited to 160 characters. This deliberate limit
  accommodates readable Compose modifier chains and generated catalog records;
  ordinary control flow and prose should wrap earlier when that improves
  clarity.
- Do not hand-format generated guidance, terminology, localization, Room schema,
  or illustration catalog outputs. Change their generator or canonical source.

## Kotlin and Compose

- Prefer immutable state, explicit domain types, and small functions with one
  responsibility. Keep Android framework concerns at the application boundary.
- Hoist screen state and events. Reusable composables should accept a
  `Modifier`, keep it near the beginning of the parameter list, and apply it to
  their outermost meaningful element.
- Name composables with nouns or concise noun phrases. Name event callbacks
  `on…` and suspend operations by their result rather than implementation.
- Keep user-visible text in string resources, preserve semantic descriptions,
  and verify touch targets, focus order, contrast, and large-text layouts.
- Avoid wildcard imports, unexplained suppressions, and broad exception handling
  outside explicit integration boundaries.

## Quality gate

Every change must pass:

```powershell
.\gradlew.bat :app:detekt :app:lintDebug testDebugUnitTest :app:assembleDebug
```

Detekt and Android lint run without baselines: every unsuppressed finding fails
the build. Android lint treats warnings as errors across Kotlin, Compose,
resources, the manifest, and shrinker configuration. Suppress a rule only at
the narrowest source scope and document the concrete invariant or framework
constraint that makes the rule inapplicable. Never create or regenerate a
baseline to make a failing check disappear.

Formatting-only changes should be isolated from behavioural changes whenever
practical. Review generated output and policy exceptions as source code.
