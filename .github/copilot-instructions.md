<!-- context-init:version:3.1.0 -->
<!-- context-init:generated:2026-05-02T09:24:05+02:00 -->

# Starlit Coffee

<!-- context-init:managed -->
Android coffee companion for planning, timing, logging, and improving brews. The app is Pulsar-first, but supports multiple brew methods, calculator-driven dose/water setup, bag inventory scanning, brew logs, rating reminders, and experimental LLM-assisted scan analysis.

For detailed reference, see `.github/context/ARCHITECTURE.md`, `.github/context/PATTERNS.md`, and `.github/context/DEVELOPMENT.md`.

## Tech Stack

<!-- context-init:managed -->
| Tech | Version | Purpose |
|------|---------|---------|
| Kotlin | 2.3.21 | Android language |
| AGP | 9.2.1 | Android build plugin |
| Gradle wrapper | 9.5.1 | Build runner |
| Jetpack Compose | BOM 2026.05.01 | UI |
| Material 3 | 1.5.0-alpha20 | Design system |
| Navigation Compose | 2.9.8 | Type-safe routes |
| Room | 2.8.4 | Local database |
| Lifecycle/ViewModel | 2.10.0 | State management |
| KSP | 2.3.8 | Room annotation processing |
| ML Kit | Barcode 17.3.0, Text 16.0.1 | Bag scanning |
| CameraX | 1.6.1 | Live scan camera |
| DataStore | 1.2.1 | User preferences |
| OpenCV | 4.13.0 | Image preprocessing |
| WorkManager | 2.10.1 | Rating reminder |
| minSdk / targetSdk | 26 / 37 | Android API range |

## Architecture Rules

<!-- context-init:managed -->
| Area | Rule |
|------|------|
| Entry point | `MainActivity` calls `enableEdgeToEdge()` and hosts `StarlitNavHost()` inside `StarlitCoffeeTheme`. |
| Navigation | Use `@Serializable` route objects from `Routes.kt`; never navigate with raw strings. |
| ViewModels | Keep app/brew state in `BrewViewModel`, calculator state in `CalculatorViewModel`, and live scan session state in nav-scoped `LiveScanViewModel`. |
| Persistence | Use repositories over Room DAOs; update migrations and exported schemas when changing DB entities. |
| DI | No DI framework yet; factories/manual wiring are intentional. Do not add ad-hoc service locators. |
| UI state | Composables may use `remember` for ephemeral UI only; brew data belongs in ViewModels. |

## Patterns to Follow

<!-- context-init:managed -->
- **StateFlow:** private mutable flow + public read-only flow (`_uiState` -> `uiState`); use `.update { it.copy(...) }` for derived mutations.
- **Compose screens:** collect state with `collectAsStateWithLifecycle()`, receive navigation callbacks, and use `MaterialTheme` colors/typography.
- **Calculator:** `CalculatorViewModel` owns token input; sync derived ratio/dose into `BrewViewModel` immediately before save/start actions.
- **Brew math:** keep deterministic brew calculations in `domain/BrewCalculator.kt`; `BrewViewModel.recalculate()` wires app-specific state around it.
- **Scanning:** `LiveScanViewModel` orchestrates `FrameEvidenceAccumulator`, `ConsensusEngine`, side detection, LLM escalation, and perf stats; users confirm/edit before saving.
- **Localization:** add every user-facing string to both `values/strings.xml` and `values-cs/strings.xml` with identical keys.

## Domain Knowledge

<!-- context-init:managed -->
- Pulsar default ratio is **1:17**, not 1:16.
- Pulsar filters are paper, 19K metal, and 40K metal; `FilterType` is meaningful only for Pulsar UI state.
- Grind recommendations are optional and must be presented as ranges plus adjustment notes.
- `StrengthPreset.ratioOffset` is `Int`: LIGHT=+1, BALANCED=0, STRONG=-1.
- `BrewMethod` enum entries own method defaults: ratio, bloom, pulses, temp, time, capacity, absorption, and grind descriptor.
- Decaf brews adjust target time and may inherit decaf state from the selected coffee bag unless manually overridden.

## Build & Test

<!-- context-init:managed -->
| Task | Command |
|------|---------|
| Debug build | `.\gradlew.bat assembleDebug` |
| Unit tests | `.\gradlew.bat testDebugUnitTest` |
| Detekt | `.\gradlew.bat detekt` |
| Install debug | `.\gradlew.bat installDebug` |
| Push scan test images | `.\gradlew.bat pushTestImages` |
| Instrumented tests | `.\gradlew.bat connectedDebugAndroidTest` |

## Do NOT

<!-- context-init:managed -->
- Do not overwrite unrelated dirty worktree changes; this repo often has parallel edits.
- Do not hardcode string routes, Pulsar ratio 1:16, or exact grinder settings.
- Do not bypass repositories for Room access from UI or ViewModels.
- Do not add broad silent fallbacks; log/surface failures consistently.
- Do not auto-save live scan results without user review.
- Do not put source-only strings directly in UI; use resources.

<!-- context-init:user-content-below -->
