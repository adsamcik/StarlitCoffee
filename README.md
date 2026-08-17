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
- Reviewed English guidance plus clearly labelled, opt-in translation previews
  for the other supported app locales.

## Project status

The app is under active development. The `main` branch is expected to build and
pass its automated validation, but non-English exact-guidance previews are not
presented as editorially reviewed translations. See [CHANGELOG.md](CHANGELOG.md)
for current release status and upgrade notes.

## Requirements

- Android Studio with Android SDK 37
- JDK 17
- Git
- Access to the public Mindlayer GitHub Packages dependency

GitHub Packages requires authentication even for public Maven packages. The
build checks `GITHUB_TOKEN` and then an authenticated GitHub CLI session
(`gh auth token`). A token used for package downloads needs `read:packages`.
Local Maven artifacts remain available through `mavenLocal()` for Mindlayer
contributors.

The repository includes a one-time setup helper. It signs in through GitHub CLI,
requests only package-read access, and keeps the token in GitHub CLI's credential
store:

```powershell
.\scripts\setup-github-packages.ps1
```

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

## Contributing and security

The baseline-free quality and exception policy is documented in
[docs/code-quality.md](docs/code-quality.md).

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
