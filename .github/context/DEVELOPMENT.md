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
| `GITHUB_TOKEN` | Optional | Explicit token for private Mindlayer GitHub Packages. |
| `GITHUB_OWNER` | Optional | Owner for Mindlayer package repo; defaults to `adsamcik`. |

Credential lookup for Mindlayer is defined in `settings.gradle.kts`: `local.properties` -> Gradle property -> environment variable -> `gh auth token`.

## Setup

```powershell
git clone https://github.com/adsamcik/StarlitCoffee
Set-Location StarlitCoffee
.\gradlew.bat assembleDebug
```

For private Mindlayer dependency access, authenticate with `gh auth login` or provide `GITHUB_TOKEN`.

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

Scan/instrumented image assets are intentionally not committed. Create `testdata\coffee-bags` as a symlink to local images, following `testdata\README.md`.

```powershell
.\gradlew.bat pushTestImages
.\gradlew.bat connectedDebugAndroidTest
```

Image names should follow `roaster_coffee_name_{front|back}.jpg`.

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
|   |   |   |-- audio/
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
| Mindlayer SDK | 0.3.0 |

## Troubleshooting

| Problem | Likely cause | Fix |
|---------|--------------|-----|
| Mindlayer dependency cannot resolve | Missing GitHub Packages credential | Run `gh auth login` or set `GITHUB_TOKEN`. |
| Gradle uses wrong Java | `JAVA_HOME` points to a non-17 JDK | Set `JAVA_HOME` to JDK 17 and retry. |
| Room build/KSP error after entity change | Migration/schema not updated | Update `AppDatabase` migration and exported schema. |
| Test images missing | `testdata\coffee-bags` symlink absent | Create the symlink described in `testdata\README.md`. |
| Localization compile failure | EN/CS string keys or placeholders diverged | Keep `values` and `values-cs` keys/placeholders aligned. |
| Navigation crash | Raw string route or missing typed route arg | Use `@Serializable` routes and `toRoute<T>()`. |
| Brew ratio mismatch in tests | Pulsar default assumed as 1:16 | Use `BrewMethod.PULSAR.defaultRatio == 17f`. |

<!-- context-init:user-content-below -->
