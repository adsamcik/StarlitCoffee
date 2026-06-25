<!-- context-init:version:3.1.0 -->
<!-- context-init:generated:2026-05-02T09:24:05+02:00 -->

# Starlit Coffee - Development Guide

<!-- context-init:managed -->

## Prerequisites

| Tool | Version / source | Notes |
|------|------------------|-------|
| JDK | 17 | `app/build.gradle.kts` compiles Java/Kotlin to JVM 17. |
| Android SDK | compileSdk 37, targetSdk 37, minSdk 26 | Install with Android Studio SDK Manager. |
| Gradle | Wrapper 9.5.1 | Use `.\gradlew.bat`, not a system Gradle. |
| Kotlin | 2.3.21 | Managed through `gradle/libs.versions.toml`. |
| GitHub CLI | optional | `settings.gradle.kts` can call `gh auth token` for Mindlayer GitHub Packages. |

## Environment

| Name | Required | Purpose |
|------|----------|---------|
| `JAVA_HOME` | Usually | Points Gradle to JDK 17. |
| `ANDROID_HOME` | Usually | Android SDK location if Android Studio has not configured it. |
| `GITHUB_TOKEN` | Optional | Token for the Mindlayer GitHub Packages registry. The repo is public, but GitHub Packages Maven reads still need a token — any GitHub account works. |
| `GITHUB_OWNER` | Optional | Owner for Mindlayer package repo; defaults to `adsamcik`. |

Credential lookup for Mindlayer is defined in `settings.gradle.kts`: `local.properties` -> Gradle property -> environment variable -> `gh auth token`.

## Setup

```powershell
git clone https://github.com/adsamcik/StarlitCoffee
Set-Location StarlitCoffee
.\gradlew.bat assembleDebug
```

The Mindlayer repo is public, but GitHub Packages Maven reads still require a token (a GitHub limitation, not a private-repo gate). Authenticate with `gh auth login` using any GitHub account, or provide `GITHUB_TOKEN`.

## Common Commands

| Task | Command |
|------|---------|
| Build debug APK | `.\gradlew.bat assembleDebug` |
| Run unit tests | `.\gradlew.bat testDebugUnitTest` |
| Run a test class | `.\gradlew.bat testDebugUnitTest --tests "com.adsamcik.starlitcoffee.viewmodel.BrewViewModelTest"` |
| Run Detekt | `.\gradlew.bat detekt` |
| Install debug app | `.\gradlew.bat installDebug` |
| Build release APK | `.\gradlew.bat assembleRelease` |
| Run instrumented tests | `.\gradlew.bat connectedDebugAndroidTest` |
| Push scan test images | `.\gradlew.bat pushTestImages` |
| Show dependency tree | `.\gradlew.bat dependencies` |
| Clean outputs | `.\gradlew.bat clean` |

## Outputs and Reports

| Artifact | Location |
|----------|----------|
| Debug APK | `app\build\outputs\apk\debug\app-debug.apk` |
| Unit test report | `app\build\reports\tests\testDebugUnitTest\index.html` |
| Room schemas | `app\schemas\com.adsamcik.starlitcoffee.data.db.AppDatabase\*.json` |
| Detekt config | `config\detekt\detekt.yml` |

## Test Data

The synthetic coffee-bag corpus is committed at `testdata\synthetic-coffee-bag-corpus\`
(WebP images under `prototypes\` + `corpus_metadata.json`). No symlink or local
real-photo setup is required.

```powershell
.\gradlew.bat pushTestImages
.\gradlew.bat connectedDebugAndroidTest
```

Corpus image names follow `lang_roaster_coffee_{front|back}_qTier.webp`; ground
truth lives in `corpus_metadata.json`. See `testdata\README.md`.

## Project Structure

```text
StarlitCoffee/
|-- app/
|   |-- build.gradle.kts
|   |-- schemas/
|   |-- src/main/
|   |   |-- AndroidManifest.xml
|   |   |-- assets/
|   |   |-- java/com/adsamcik/starlitcoffee/
|   |   |   |-- calculator/
|   |   |   |-- data/
|   |   |   |-- domain/
|   |   |   |-- navigation/
|   |   |   |-- scan/
|   |   |   |-- service/
|   |   |   |-- ui/
|   |   |   |-- util/
|   |   |   `-- viewmodel/
|   |   `-- res/values and values-cs
|   `-- src/test/java/com/adsamcik/starlitcoffee/
|-- config/detekt/detekt.yml
|-- gradle/libs.versions.toml
|-- gradle/wrapper/gradle-wrapper.properties
|-- settings.gradle.kts
`-- testdata/README.md
```

## Version Reference

Versions are centralized in `gradle/libs.versions.toml`.

| Dependency | Version |
|------------|---------|
| AGP | 9.2.1 |
| Kotlin | 2.3.21 |
| Compose BOM | 2026.05.01 |
| Material 3 | 1.5.0-alpha20 |
| Navigation Compose | 2.9.8 |
| Room | 2.8.4 |
| Lifecycle | 2.10.0 |
| CameraX | 1.6.1 |
| Coroutines | 1.11.0 |
| WorkManager | 2.10.1 |
| Detekt | 1.23.8 |
| Mindlayer SDK | 1.0.0-alpha01 |

## Targeting Android 17 (SDK 37)

The app targets API level 37. Notable platform behaviors to keep in mind when changing UI or background code:

| Area | Behavior on SDK 37 | What the codebase does |
|------|--------------------|------------------------|
| Large-screen orientation (sw>=600dp) | `android:screenOrientation`, `resizeableActivity`, `min/maxAspectRatio` are **ignored** on tablets/foldables. The opt-out from SDK 36 is gone. | `MainActivity` keeps `screenOrientation="portrait"` for phones; tablets will rotate. `configChanges` is declared so the Activity is not recreated on rotation/density/fontScale changes. |
| Predictive back | Available only when the app opts in. | `<application android:enableOnBackInvokedCallback="true">` in `AndroidManifest.xml`; all in-screen back handling uses Compose `BackHandler`. |
| Background audio hardening | Background audio playback, focus, and volume APIs require a foreground service. | Not applicable — the app performs no audio capture or playback. |
| `ACCESS_LOCAL_NETWORK` | Required to discover LAN devices (mDNS, casting, smart home). | Not applicable — the app has no LAN discovery. |
| `MessageQueue` lock-free / static-final reflection | Reflection on framework internals or `static final` fields fails. | Not applicable — the codebase does not reflect on framework internals. |
| Per-app keystore limit | Non-system apps capped at 50,000 keys. | Not applicable — no Keystore usage. |
| ECH (Encrypted Client Hello) | Enabled by default for SDK 37 apps. | No code change required; honoured automatically by the platform networking stack. |

When adding new background work, foreground services, or LAN features, re-check the [Android 17 behavior changes](https://developer.android.com/about/versions/17/behavior-changes-17) before wiring it in.

## Troubleshooting

| Problem | Likely cause | Fix |
|---------|--------------|-----|
| Mindlayer dependency cannot resolve | No token (public repo, but GitHub Packages Maven still requires one) | Run `gh auth login` (any GitHub account) or set `GITHUB_TOKEN`. |
| Gradle uses wrong Java | `JAVA_HOME` points to a non-17 JDK | Set `JAVA_HOME` to JDK 17 and retry. |
| Room build/KSP error after entity change | Migration/schema not updated | Update `AppDatabase` migration and exported schema. |
| Test images missing | Synthetic corpus not pushed to device | Run `.\gradlew.bat pushTestImages`; corpus lives in `testdata\synthetic-coffee-bag-corpus\`. |
| Localization compile failure | EN/CS string keys or placeholders diverged | Keep `values` and `values-cs` keys/placeholders aligned. |
| Navigation crash | Raw string route or missing typed route arg | Use `@Serializable` routes and `toRoute<T>()`. |
| Brew ratio mismatch in tests | Pulsar default assumed as 1:16 | Use `BrewMethod.PULSAR.defaultRatio == 17f`. |

<!-- context-init:user-content-below -->

## Scan LLM benchmark (instrumented)

The bag-scan extraction quality benchmark is a two-step instrumented flow:
`OcrFixtureCaptureTest` captures OCR text to the app's external files dir, then
`LlmCorpusBenchmarkTest` scores LLM extraction against the corpus ground truth.

Run it with the dedicated task — **not** `connectedDebugAndroidTest`:

```powershell
.\gradlew.bat scanBenchmark                # capture OCR fixtures, then benchmark
.\gradlew.bat scanBenchmark -PskipCapture  # reuse fixtures (prompt-only iteration)
```

Report: `app\build\reports\scan-benchmark\llm-fixture-quality-report.{txt,json}`.

> **Why a dedicated task?** `connectedAndroidTest` uninstalls the app after every
> run, wiping the OCR fixtures between the capture and benchmark steps. The
> `scanBenchmark` task installs both APKs once and drives instrumentation via
> `adb am instrument`, so the app and its fixtures survive across both passes. It
> also enables the Mindlayer debug auto-accept receiver so on-device inference
> runs unattended. See `.github/instructions/benchmark.instructions.md`.
