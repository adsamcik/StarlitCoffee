# Code quality

Starlit Coffee treats automated checks as a ratchet: new issues fail the build,
while larger historical refactors remain explicit and reviewable.

Formatting, Kotlin, and Compose conventions are defined in
[code-style.md](code-style.md). `.editorconfig` keeps IDE formatting consistent,
and the code-quality workflow enforces Detekt and Android lint on every relevant
pull request.

## Required checks

Run the following before opening a pull request:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat :app:detekt :app:lintDebug :app:assembleDebug
```

Localization and generated brewing catalogs have additional deterministic
checks in the localization workflow and release workflow.

## Detekt baseline

`config/detekt/baseline.xml` records the static-analysis findings that existed
when the public cleanup baseline was established. `maxIssues` remains zero, so
Detekt reports no accepted historical noise and fails on every new finding.

## Android lint baseline

`app/lint-baseline.xml` records 117 findings that predate strict warning
enforcement. The largest groups are 59 unused resources, 34 KTX modernization
suggestions, eight locale plural-quantity gaps, and four intentionally
duplicated illustration pairs; the remaining 12 are focused Compose, PiP,
preferences, SDK, typo, and lifecycle findings.

All new Android lint warnings are errors. Regenerate the baseline only after a
reviewed fix removes entries:

```powershell
.\gradlew.bat :app:updateLintBaseline
.\gradlew.bat :app:lintDebug
```

Treat this baseline with the same ratchet policy as Detekt: do not add or replace
entries merely to make CI pass.

## Current debt snapshot

The 2026-08-06 baseline contains 157 findings. Most are concentrated rather
than spread evenly across the product:

- 102 line-length findings in data-heavy brewing and guidance catalogs.
- 37 size and API-shape findings: too many functions, long methods, long
  parameter lists, and large orchestration classes.
- 18 smaller complexity, return-count, allocation, and style findings.

The highest-value refactor targets are the scan/extraction scheduler, photo
storage, and LLM orchestration boundaries. Catalog formatting should be improved
at the generator or data-model level instead of through hand-edited wrapping.
This snapshot describes maintainability debt, not known user-facing defects.

The baseline is not a waiver or a target. When a finding is fixed, regenerate
the baseline with:

```powershell
.\gradlew.bat :app:detektBaseline
.\gradlew.bat :app:detekt
```

Review baseline diffs like source code. A change should remove entries or
accompany a clearly justified refactor; do not regenerate it merely to make a
new warning disappear.

## Generated content

Guidance, terminology, localization, and accepted illustration catalogs have a
single canonical source plus checked-in generators. Do not hand-edit generated
outputs. Generator `--check` modes are used in CI to detect drift.
