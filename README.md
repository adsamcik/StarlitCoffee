# Starlit Coffee

Starlit Coffee is a native Android companion for guided brewing, repeatable
recipes, and useful brew records. It combines a simple everyday brew flow with
progressively disclosed controls for people who want to understand or tune each
stage.

## Highlights

- Guided, durable brew sessions with observable completion cues and recovery.
- Evidence-bound exact recipes across multiple brewer families.
- Concise stage guidance with purpose-built, text-free illustrations.
- Coffee-bag inventory, barcode/OCR-assisted capture, quick gram-based usage
  tracking, and brew history.
- Material 3 Expressive UI with light, dark, dynamic-color, accessibility, and
  large-text support.
- Released, localized guidance in all 23 supported app languages.

## Project status

The app is under active development. The `main` branch is expected to build and
pass its automated localization and release validation. See
[CHANGELOG.md](CHANGELOG.md) for current release status and upgrade notes.

## Requirements

- Android Studio with Android SDK 37
- JDK 17
- Git
- Access to the public Mindlayer and Tracebox GitHub Packages dependencies

GitHub Packages requires authentication even for public Maven packages. The
build uses a classic token with `read:packages` or an authenticated GitHub CLI
session. Credential precedence is Gradle properties (including `-P`), environment
variables, `local.properties`, then `gh auth token --hostname github.com`.
Within each source, `GH_TOKEN` takes precedence over `GITHUB_TOKEN`. This lets CI
and command-line overrides replace stale local credentials.
Local Maven artifacts remain available through `mavenLocal()` for Mindlayer and
Tracebox contributors.

The setup helper verifies the exact Mindlayer and Tracebox versions using the
credentials Gradle selects. If needed, it signs in through GitHub CLI and adds
package-read access, keeping the token in GitHub CLI's credential store:

```powershell
.\scripts\setup-github-packages.ps1
```

For a check without interactive sign-in, run
`.\scripts\setup-github-packages.ps1 -CheckOnly` or
`.\gradlew.bat checkGitHubPackagesAuth`. The check reports the credential source
and verifies both registries with live authenticated HEAD requests, even when
dependencies are cached. It never displays tokens.

If authentication is rejected, replace or remove any explicit token override;
refreshing GitHub CLI does not change an override. For a CLI token, run
`gh auth refresh --hostname github.com --scopes read:packages`. Fine-grained
personal access tokens do not support this Maven registry.

Developers with multiple GitHub accounts can set `GITHUB_USERNAME` to select a
stored CLI account without changing the active login. For explicit tokens, use
the token owner's username. `GITHUB_OWNER` only selects the package repository
owner and defaults to `adsamcik`; CI uses `GITHUB_ACTOR` as the default username.
On Windows, Gradle also checks standard GitHub CLI installation paths when an
already-running Android Studio has an outdated `PATH`.

Android Studio normally creates `local.properties` with the local SDK path. The
file is intentionally ignored and must never be committed.

Local builds that need to bind to Mindlayer's signature-protected service can
provide an approved known-signer keystore through Gradle properties or the
equivalent environment variables:

| Gradle property | Environment variable |
| --- | --- |
| `starlit.knownSigner.keystore` | `STARLIT_KNOWN_SIGNER_KEYSTORE` |
| `starlit.knownSigner.storePassword` | `STARLIT_KNOWN_SIGNER_STORE_PASSWORD` |
| `starlit.knownSigner.keyAlias` | `STARLIT_KNOWN_SIGNER_KEY_ALIAS` |
| `starlit.knownSigner.keyPassword` | `STARLIT_KNOWN_SIGNER_KEY_PASSWORD` |

When the complete signing configuration is absent, debug builds use Android's
standard debug keystore and all ordinary app development remains available.

## Build and test

On Windows:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat :app:detekt :app:lintDebug :app:assembleDebug
```

On macOS or Linux, use `./gradlew` with the same tasks.

Authentication regression checks use synthetic tokens and a local registry:
`python tools/test_github_packages_auth.py`. They require Python 3 and the
configured Gradle/JDK runtime, and leave stored GitHub credentials unchanged.

The debug APK is written beneath `app/build/outputs/apk/debug/`. Device-backed
OCR/LLM benchmarks require a connected Android device and the committed
synthetic corpus; see [testdata/README.md](testdata/README.md).

## Repository guide

| Path | Purpose |
| --- | --- |
| `app/src/main` | Application code, resources, Room schemas, and shipped assets |
| `app/src/test` | JVM unit and contract tests |
| `app/src/androidTest` | Device and migration tests |
| `docs/adr` | Architecture decision records |
| `docs/brewing` | Brewing taxonomy, guidance, localization, and illustration contracts |
| `prompts/brewing` | Reproducible illustration briefs and accepted prompt history |
| `testdata` | Synthetic coffee-bag evaluation corpus |
| `tools` | Deterministic generators and repository validation scripts |

Start with the architecture decisions in [docs/adr](docs/adr) and the current
implementation report in
[docs/plans/2026-08-04-brewing-platform-implementation-report.md](docs/plans/2026-08-04-brewing-platform-implementation-report.md).

The [brewing guide research pack](docs/brewing/research/2026-10-02-method-guides/README.md)
contains English guides and an accuracy audit for 17 brewing methods. It records
recipe provenance, equipment limits, and sources separately from app support.

The [visual guide design](docs/brewing/design/2026-10-02-visual-guides/DESIGN.md)
includes an [interactive prototype](docs/brewing/design/2026-10-02-visual-guides/prototype.html)
for brewing alongside the guide or exploring the same steps at your own pace.
Chemex, espresso, and cold brew demonstrate the shared workflow; the design maps
all 17 researched methods. This is a design proposal, with
[browser review notes](docs/brewing/design/2026-10-02-visual-guides/REVIEW.md), before
Android implementation.

The [method icon review](docs/brewing/design/2026-10-02-method-icons/README.md)
pairs equipment photographs with individual generator briefs and transparent
candidates for all 17 methods, using the existing brewer icon style.

## Contributing and security

The baseline-free quality and exception policy is documented in
[docs/code-quality.md](docs/code-quality.md).

The production diagnostics architecture and privacy boundary are documented in
[docs/tracebox-integration.md](docs/tracebox-integration.md).

Please read [CONTRIBUTING.md](CONTRIBUTING.md) before proposing changes. Report
security or privacy issues through the process in [SECURITY.md](SECURITY.md),
not through a public issue.

## License

Starlit Coffee is source-available under the
[PolyForm Noncommercial License 1.0.0](LICENSE). You may use, modify, and
redistribute the project for permitted noncommercial purposes. Commercial use
requires a separate license from the project owner.

This is not an open-source license as defined by the Open Source Initiative.
Third-party components remain subject to their own license terms.
