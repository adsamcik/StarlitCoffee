<!-- context-init:version:3.0.0 -->
<!-- context-init:generated:2026-02-25T05:48:00Z -->

# Starlit Coffee — Development Guide

<!-- context-init:managed -->

## Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| JDK | 17 (Temurin recommended) | Set `JAVA_HOME` |
| Android SDK | API 35 (compileSdk) | Install via Android Studio |
| Android SDK | API 26+ (minSdk) | Emulator or device |
| Kotlin | 2.1.0 | Managed by Gradle |
| Gradle | 8.13 | Wrapper included (`gradlew.bat`) |

## Environment Setup

```bash
# 1. Clone the repository
git clone <repo-url>
cd StarlitCoffee

# 2. Ensure JAVA_HOME points to JDK 17
# Windows: set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot
# Verify:
java -version

# 3. Ensure Android SDK is available
# Default location: %LOCALAPPDATA%\Android\Sdk
# Set ANDROID_HOME if different

# 4. Build
.\gradlew.bat assembleDebug
```

## Common Commands

| Command | Purpose |
|---------|---------|
| `.\gradlew.bat assembleDebug` | Build debug APK |
| `.\gradlew.bat testDebugUnitTest` | Run unit tests (32 tests) |
| `.\gradlew.bat installDebug` | Install on connected device/emulator |
| `.\gradlew.bat assembleRelease` | Build release APK (needs signing) |
| `.\gradlew.bat clean` | Clean build artifacts |
| `.\gradlew.bat dependencies` | Show dependency tree |

## APK Output Location

```
app/build/outputs/apk/debug/app-debug.apk
```

## Project Structure

```
StarlitCoffee/
├── app/
│   ├── build.gradle.kts          # App module build config
│   ├── proguard-rules.pro        # ProGuard/R8 rules
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   └── java/com/adsamcik/starlitcoffee/
│       │       ├── MainActivity.kt
│       │       ├── StarlitCoffeeApp.kt
│       │       ├── data/model/     # 12 domain models
│       │       ├── data/db/        # Room database
│       │       ├── data/repository/ # (planned)
│       │       ├── navigation/     # Routes + NavHost
│       │       ├── ui/screen/      # 9 screens
│       │       ├── ui/component/   # (planned)
│       │       ├── ui/theme/       # Material 3 theme
│       │       └── viewmodel/      # BrewViewModel
│       └── test/
│           └── java/.../viewmodel/BrewViewModelTest.kt
├── build.gradle.kts              # Root build config
├── settings.gradle.kts           # Module includes
├── gradle/
│   └── libs.versions.toml        # Version catalog
└── gradle.properties             # Gradle config
```

## Key Dependencies (from libs.versions.toml)

| Dependency | Version | Purpose |
|-----------|---------|---------|
| Compose BOM | 2024.12.01 | UI framework version alignment |
| Material 3 | 1.3.1 | Design system |
| Navigation Compose | 2.8.5 | Type-safe navigation |
| Room | 2.6.1 | SQLite persistence |
| Lifecycle/ViewModel | 2.8.7 | Lifecycle-aware state |
| KSP | 2.1.0-1.0.29 | Room annotation processor |
| ML Kit Barcode | 17.3.0 | Barcode scanning (deps only) |
| CameraX | 1.4.1 | Camera API (deps only) |
| Coroutines | 1.9.0 | Async operations |
| kotlinx-serialization-json | 1.7.3 | Navigation route serialization |

## Testing

```bash
# Run all unit tests
.\gradlew.bat testDebugUnitTest

# Run specific test class
.\gradlew.bat testDebugUnitTest --tests "com.adsamcik.starlitcoffee.viewmodel.BrewViewModelTest"

# Test report location
app/build/reports/tests/testDebugUnitTest/index.html
```

### Test Setup
- Uses `UnconfinedTestDispatcher` — all StateFlow updates are synchronous
- Tests assert directly on `viewModel.uiState.value` without `advanceUntilIdle()`
- 32 tests covering: calculations, presets, custom ratio, grinder, timer, guardrails, feedback

## Emulator

| Property | Value |
|----------|-------|
| Device | emulator-5554 "Medium Phone" |
| API Level | Android 16 (API 36) |
| Install | `adb install app/build/outputs/apk/debug/app-debug.apk` |
| Launch | `adb shell am start -n com.adsamcik.starlitcoffee/.MainActivity` |

## Troubleshooting

| Problem | Cause | Solution |
|---------|-------|---------|
| Build fails with "Could not determine java version" | Wrong JDK | Set `JAVA_HOME` to JDK 17 |
| KSP errors on Room entities | Missing `@Entity` or `@PrimaryKey` | Check entity annotations |
| "Skipped N frames" on first launch | Compose compilation on device | Normal on first run, subsequent launches are fast |
| Navigation crash "No destination found" | String route instead of object | Use `@Serializable` objects from `Routes.kt` |
| `settings.gradle.kts` resolution error | Wrong API name | Use `dependencyResolutionManagement` (not `dependencyResolution`) |
| Test fails with ratio mismatch | Pulsar default is 1:17 | Verify `BrewMethod.PULSAR.defaultRatio = 17f` |
| StrengthPreset math wrong | `ratioOffset` is Int, not Float | LIGHT=+1, BALANCED=0, STRONG=-1 |

## Permissions

| Permission | Required | Purpose |
|-----------|----------|---------|
| `CAMERA` | Optional (`required="false"`) | Barcode scanning (planned) |
| `VIBRATE` | Optional | Haptic feedback on brew timer |

<!-- context-init:user-content-below -->
