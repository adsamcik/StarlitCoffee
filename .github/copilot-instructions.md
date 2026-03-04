<!-- context-init:version:3.0.0 -->
<!-- context-init:generated:2026-02-25T05:48:00Z -->

# Starlit Coffee

<!-- context-init:managed -->
Android guided brew assistant focused on NextLevel Pulsar. Material 3 Expressive design, Jetpack Compose UI, single-activity architecture with shared ViewModel.

## Tech Stack

<!-- context-init:managed -->
| Tech | Version | Purpose |
|------|---------|---------|
| Kotlin | 2.1.0 | Language |
| AGP | 8.7.3 | Build system |
| Jetpack Compose | BOM 2024.12.01 | UI framework |
| Material 3 | 1.3.1 | Design system |
| Navigation Compose | 2.8.5 | Type-safe routing |
| Room | 2.6.1 | Local database |
| Lifecycle/ViewModel | 2.8.7 | State management |
| KSP | 2.1.0-1.0.29 | Annotation processing |
| ML Kit Barcode | 17.3.0 | Barcode scanning (planned) |
| CameraX | 1.4.1 | Camera (planned) |
| Coroutines | 1.9.0 | Async |
| Kotlinx Serialization | 1.7.3 | Type-safe nav routes |
| JUnit 4 | 4.13.2 | Unit tests |
| minSdk 26 / targetSdk 35 | | API range |

## Code Style

<!-- context-init:managed -->
| Type | Convention | Example |
|------|-----------|---------|
| Package | lowercase dot-separated | `com.adsamcik.starlitcoffee.data.model` |
| Classes/Objects | PascalCase | `BrewViewModel`, `DefaultGrinders` |
| Functions | camelCase | `setMethod()`, `recalculate()` |
| Composables | PascalCase | `MethodPickerScreen()`, `StarlitNavHost()` |
| Enums | SCREAMING_SNAKE_CASE values | `BrewMethod.PULSAR`, `FilterType.METAL_19K` |
| Constants | SCREAMING_SNAKE_CASE | `TRANSITION_DURATION` |
| State flows | `_uiState` / `uiState` pattern | Private MutableStateFlow, public StateFlow |

## Architecture

<!-- context-init:managed -->
- **Single Activity**: `MainActivity` → `StarlitCoffeeTheme` → `StarlitNavHost`
- **Shared ViewModel**: `BrewViewModel` created at `NavHost` level, passed to all screens
- **Navigation**: Type-safe routes via `@Serializable` objects in `Routes.kt`
- **State**: `BrewUiState` data class in `BrewViewModel` — all brew calculations are reactive
- **Data layer**: Room DB (entities + DAOs defined, not yet wired to screens)
- **No DI framework** — manual singleton for `AppDatabase`

## Patterns to Follow

<!-- context-init:managed -->
### State Management
All UI state lives in `BrewViewModel._uiState: MutableStateFlow<BrewUiState>`. Screens observe via `collectAsStateWithLifecycle()`. Never hold state in Composables for brew data.

```kotlin
// Good — update via ViewModel
viewModel.setMethod(BrewMethod.PULSAR)

// Bad — local state for brew data
var method by remember { mutableStateOf(BrewMethod.PULSAR) }
```

### Navigation
Use type-safe `@Serializable` route objects. Navigate with `navController.navigate(RouteObject)`.

```kotlin
// Good
navController.navigate(Result)

// Bad
navController.navigate("result")
```

### Compose Screens
All screens take `navController` and `brewViewModel` as parameters. Bottom bar shows on tab roots only (MethodPicker, SavedRecipes, BagInventory, BrewLogList).

### Enum-Driven Configuration
Brew methods, filters, grinders, and presets are all enum/data class driven. Add new brew methods by adding enum entries with defaults — no screen changes needed.

## Domain Knowledge

<!-- context-init:managed -->
### NextLevel Pulsar (Primary Brewer)
- Default ratio: **1:17** (not 1:16)
- Valve is the core mechanic — controls flow rate continuously
- Bloom: valve OPEN → pour → CLOSE → steep 45–60s → swirl → OPEN
- Filters: Paper (cleanest), 19K (bold/body), 40K (balanced)
- Dose range: 15–30g, optimal 20–25g. Below 20g risks astringency
- Slurry height ~1cm above bed during pours
- Target brew time: 3:30–4:30

### Strength Presets
`StrengthPreset.ratioOffset` is **Int** (not Float): LIGHT=+1, BALANCED=0, STRONG=-1. Applied to `BrewMethod.defaultRatio`.

### Grinder Support
Optional layer — app works without grinder selection. `DefaultGrinders.kt` has static grinder list + recommendation rules matched by grinderId + methodId + filterType.

## Testing

<!-- context-init:managed -->
- Framework: JUnit 4 + `kotlinx-coroutines-test`
- Location: `app/src/test/java/.../viewmodel/BrewViewModelTest.kt`
- 32 unit tests covering calculations, presets, guardrails, timer phases, feedback
- Run: `.\gradlew.bat testDebugUnitTest`
- Uses `UnconfinedTestDispatcher` for coroutine testing

## Build Commands

<!-- context-init:managed -->
```
.\gradlew.bat assembleDebug          # Build debug APK
.\gradlew.bat testDebugUnitTest      # Run unit tests
.\gradlew.bat installDebug           # Install on connected device
.\gradlew.bat assembleRelease        # Release build (needs signing)
```

## Do NOT

<!-- context-init:managed -->
- Do not present Pulsar grinder numbers as universal truth — always show ranges + "adjust by taste"
- Do not use string-based navigation routes — use `@Serializable` objects
- Do not hold brew state in Composable `remember` — use `BrewViewModel`
- Do not force grinder selection — it's an optional enhancement layer
- Do not hardcode ratio 1:16 for Pulsar — it's 1:17 based on research
- Do not present single exact grind numbers — always give range + adjustment note

<!-- context-init:user-content-below -->
