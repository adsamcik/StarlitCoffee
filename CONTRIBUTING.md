# Contributing to Starlit Coffee

Thanks for helping improve Starlit Coffee. Changes should protect the ordinary
brew flow while keeping advanced control available through progressive
disclosure.

## Before you start

1. Read [README.md](README.md) and the relevant decision records in
   [docs/adr](docs/adr).
2. Open an issue before a large product, schema, dependency, or architecture
   change so its user value and migration cost can be discussed.
3. Keep pull requests focused. Do not mix generated artifacts, broad formatting,
   and behavioral changes unless they are inseparable.

## Development workflow

Create a branch, make the smallest coherent change, and add tests at the layer
that owns the behavior. Before opening a pull request, run:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat :app:detekt :app:lintDebug :app:assembleDebug
```

Changes to exact guidance, terminology, or generated catalogs must also run the
relevant scripts documented in [docs/brewing](docs/brewing). Generated outputs
must be produced by their checked-in generator rather than edited by hand.

## Product and accessibility expectations

- Preserve stable brewing identifiers and source-bound recipe quantities.
- Keep safety warnings visible at every guidance level.
- Do not invent missing brewing values or compatibility claims.
- Verify light and dark themes, large text, screen-reader semantics, focus order,
  and touch targets for user-interface changes.
- Prefer clear defaults and contextual actions over permanent settings.
- Keep user-facing copy concise and natural.

## Commits and pull requests

Use descriptive commit messages. A pull request should explain the user-visible
outcome, validation performed, known tradeoffs, and any migration or release
notes. User-visible changes belong in the Unreleased section of
[CHANGELOG.md](CHANGELOG.md).
