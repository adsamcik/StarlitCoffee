# Code quality

Starlit Coffee treats automated checks as release gates. The repository carries
no Detekt or Android lint baseline: every unsuppressed finding fails the build.

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
checks in the localization and release workflows. Release optimization and
retrace-artifact requirements are documented in
[release-optimization.md](release-optimization.md).

## Build-tool security dependencies

The root `build.gradle.kts` constrains vulnerable transitive AGP dependencies on
the buildscript classpath. `app/build.gradle.kts` also constrains the independent
`androidLintTool` configuration, which does not inherit the root constraints.
Bouncy Castle's provider, PKIX and utility modules stay aligned; jose4j, JDOM,
Commons Lang and HttpClient use patched minimum versions where affected. These
constraints do not add dependencies to the app. Review them when upgrading AGP
and remove each override once upstream resolves a sufficiently patched version.
Use `buildEnvironment` and `:app:dependencies --configuration androidLintTool`
to inspect both tooling graphs, and
`:app:dependencies --configuration releaseRuntimeClasspath` to check app scope.

## Zero-baseline enforcement

`config/detekt/detekt.yml` sets `maxIssues` to zero and
`app/build.gradle.kts` enables Android lint's `warningsAsErrors` and
`abortOnError` behavior. Do not create a baseline to accept existing or new
findings. Fix the finding, improve the canonical generator, or—only when the
rule genuinely does not model the code—add the narrowest source-local exception
with a concrete rationale.

Exceptions are part of the reviewed policy, not hidden debt. They must explain
which invariant or framework constraint makes the rule inapplicable. Broad,
undocumented, or convenience-only suppressions are rejected. Integration
boundaries may catch the framework-level `Exception` type when their explicit
contract is to log and degrade safely; this boundary policy is configured once
rather than repeated as source annotation noise.

The wrapper declaration has an exact-source `AndroidGradlePluginVersion`
exception for the deliberately pinned, compatible Gradle toolchain. That check
reports the availability of newer releases, not a broken build contract. Its
properties-file detector ignores inline suppression comments, so `app/lint.xml`
matches only the wrapper file. Qualify toolchain upgrades separately;
compatibility and security checks remain enabled.

Android lint has one reviewed configuration file, `app/lint.xml`. Its resource
exception covers generated WebP aliases that intentionally share bytes
while retaining stable resource identifiers. New aliases must be added to the
canonical asset tracker and independently reviewed; the exception must never be
expanded into a general duplicate-resource waiver.

## Generated content

Guidance, terminology, localization, and accepted illustration catalogs have a
single canonical source plus checked-in generators. Do not hand-edit generated
outputs. Generator `--check` modes are used in CI to detect drift.
