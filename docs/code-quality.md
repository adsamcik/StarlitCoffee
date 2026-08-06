# Code quality

Starlit Coffee treats automated checks as a ratchet: new issues fail the build,
while larger historical refactors remain explicit and reviewable.

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
