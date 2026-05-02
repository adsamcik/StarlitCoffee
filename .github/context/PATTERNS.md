<!-- context-init:version:3.1.0 -->
<!-- context-init:generated:2026-05-02T09:24:05+02:00 -->

# Starlit Coffee - Patterns & Conventions

<!-- context-init:managed -->

## Naming Conventions

| Type | Convention | Evidence |
|------|------------|----------|
| Packages | lowercase dot-separated | `data.model`, `ui.screen`, `scan.observability` |
| Composable screens | PascalCase ending in `Screen` | `CalculatorBrewScreen.kt`, `BrewTimerScreen.kt`, `LiveScanScreen.kt` |
| Shared components | PascalCase descriptive names | `BagCard.kt`, `BrewRatingSheet.kt`, `ScreenTopBar.kt` |
| ViewModels | `{Feature}ViewModel` | `BrewViewModel`, `CalculatorViewModel`, `LiveScanViewModel` |
| UI state | `{Feature}UiState` data classes | `BrewUiState`, `CalcUiState`, `LiveScanUiState` |
| Room entities | `{Name}Entity` | `CoffeeBagEntity`, `BrewLogEntity`, `CupPresetEntity` |
| Room DAOs | `{Name}Dao` | `CoffeeBagDao`, `BrewLogDao`, `CupPresetDao` |
| Routes | `@Serializable object` or `data class` | `CalculatorBrew`, `BrewLogDetail`, `RescanBag` |
| Enum values | `SCREAMING_SNAKE_CASE` | `PULSAR`, `METAL_19K`, `COFFEE_TO_WATER` |
| Private flows | leading underscore | `_uiState`, `_evidence`, `_liveScanUiState` |

## State and ViewModel Patterns

| Convention | Status | Evidence |
|------------|--------|----------|
| Private `MutableStateFlow`, public read-only `StateFlow` | Follow | `BrewViewModel.kt:175`, `CalculatorViewModel.kt:33`, `LiveScanViewModel.kt:70` |
| Mutate copied data state with `.update { it.copy(...) }` | Follow | `BrewViewModel.kt:309`, `CalculatorViewModel.kt:68`, `BrewAudioManager.kt:147` |
| Use `viewModelScope.launch` for async ViewModel work | Follow | `BrewViewModel.kt`, `CalculatorViewModel.kt`, `LiveScanViewModel.kt` |
| Keep brew state in `BrewViewModel`, calculator tokens in `CalculatorViewModel` | Follow | `CalculatorBrewScreen.kt:100-133` |
| Validate numeric input before mutation | Follow | `BrewViewModel.kt:318`, `BrewViewModel.kt:337`, `CalculatorViewModel.kt:171` |
| Expose multiple read-only flows when ViewModel owns multiple domains | Follow | `BrewViewModel.kt:179-191`, `LiveScanViewModel.kt:70-130` |

## Compose UI Patterns

| Convention | Status | Evidence |
|------------|--------|----------|
| Screens receive callbacks instead of direct route strings | Follow | `BagInventoryScreen.kt:76`, `BrewTimerScreen.kt:74`, `LiveScanScreen.kt:117` |
| State is observed with `collectAsStateWithLifecycle()` | Follow | `CalculatorBrewScreen.kt:100`, `BagInventoryScreen.kt:87`, `LiveScanScreen.kt:127` |
| `remember` stores ephemeral UI state only | Follow | dialogs/sheets/permissions in `CalculatorBrewScreen.kt`, `BagInventoryScreen.kt`, `LiveScanScreen.kt` |
| Use `MaterialTheme.colorScheme` and `MaterialTheme.typography` | Follow | common across `ui/screen` and `ui/component` |
| Add heading semantics to titles | Follow | present across screens/components; see `ScreenTopBar.kt` and screen title text |
| Keep screen on during brewing | Follow | `BrewTimerScreen.kt:83` calls `KeepScreenOn()` |
| Use resource strings for user-facing copy | Follow | screen code imports `R` and `stringResource`; translations exist in `values` and `values-cs` |

## Navigation Patterns

| Convention | Status | Evidence |
|------------|--------|----------|
| Type-safe `@Serializable` routes | Follow | `Routes.kt` |
| Navigate with route objects/classes, not strings | Follow | `StarlitNavHost.kt:160`, `StarlitNavHost.kt:237`, `StarlitNavHost.kt:341` |
| Use `toRoute<T>()` for route arguments | Follow | `StarlitNavHost.kt:365`, `StarlitNavHost.kt:406` |
| Bottom bar only on tab roots | Follow | `StarlitNavHost.kt:80-90`, `StarlitNavHost.kt:122` |
| Use `savedStateHandle` for one-shot scan results | Follow | `StarlitNavHost.kt:316-324`, `StarlitNavHost.kt:397-400` |

## Data and Persistence Patterns

| Convention | Status | Evidence |
|------------|--------|----------|
| Room entities/DAOs are isolated under `data/db` | Follow | `AppDatabase.kt`, `data/db/dao`, `data/db/entity` |
| DAOs expose `Flow` for observable lists and `suspend` for writes | Follow | `data/db/dao/*Dao.kt` |
| Repositories wrap DAOs/DataStore | Follow | `RecipeRepository.kt`, `CoffeeBagRepository.kt`, `UserPreferencesRepository.kt` |
| `AppDatabase` is a volatile singleton with explicit migrations | Follow | `AppDatabase.kt:52-170` |
| Keep schema exports in `app/schemas/` | Follow | KSP arg in `app/build.gradle.kts:62-64` |
| DataStore owns onboarding and user preference defaults | Follow | `UserPreferencesRepository.kt` |

## Domain Model Patterns

| Convention | Status | Evidence |
|------------|--------|----------|
| `BrewMethod` enum owns brew defaults | Follow | `BrewMethod.kt:5-138` |
| Ratio presets derive from each method default ratio | Follow | `BrewMethod.kt:129-137` |
| `FilterType` contains display, description, and cup profile | Follow | `FilterType.kt` |
| `StrengthPreset.ratioOffset` is `Int` | Follow | `StrengthPreset.kt` |
| Default grinder data is a fallback provider | Follow | `DefaultGrinders.kt:3-8` |
| Grind recommendations match `grinderId + methodId + filterType` | Follow | `DefaultGrinders.kt:54-85` |

## Scan Pipeline Patterns

| Convention | Status | Evidence |
|------------|--------|----------|
| Live scan session owns its own accumulator and consensus engine | Follow | `LiveScanViewModel.kt:159-174` |
| Frame accumulator owns a coroutine scope tied to camera session | Follow | `FrameEvidenceAccumulator.kt:32-67` |
| Regular frames are conflated; golden frames/enrichment are not dropped | Follow | `FrameEvidenceAccumulator.kt:70-75` |
| Consensus is pure-ish and testable | Follow | `ConsensusEngine.kt:18-30`, `ConsensusEngineTest.kt` |
| Users resolve/reset fields explicitly | Follow | `FrameEvidenceAccumulatorTest.kt:60-114` |
| LLM calls are optional and cached | Follow | `LiveScanViewModel.kt`, `data/network/llm/*` |

## Audio Patterns

| Convention | Status | Evidence |
|------------|--------|----------|
| Pure event detector with injected time provider | Follow | `BrewEventDetector.kt:28-36` |
| Audio manager composes capture -> preprocess -> spectral analysis -> detector | Follow | `BrewAudioManager.kt:23-49` |
| Audio state/events exposed as flows | Follow | `BrewAudioManager.kt:64-71` |
| Tests use synthetic features and deterministic time | Follow | `BrewEventDetectorTest.kt` |

## Testing Patterns

| Convention | Status | Evidence |
|------------|--------|----------|
| JUnit 4 annotations | Follow | `BrewViewModelTest.kt`, `CalculatorViewModelTest.kt`, `FrameEvidenceAccumulatorTest.kt` |
| Backtick test names describe behavior | Follow | `BrewViewModelTest.kt:51`, `CalculatorViewModelTest.kt:54`, `BrewEventDetectorTest.kt:37` |
| Coroutine tests set/reset main dispatcher | Follow | `BrewViewModelTest.kt:37-45`, `CalculatorViewModelTest.kt:38-48`, `FrameEvidenceAccumulatorTest.kt:31-38` |
| Float assertions use deltas | Follow | `BrewViewModelTest.kt:57-59`, `BrewEventDetectorTest.kt:43` |
| Test sections use `// --- Section ---` comments | Follow | `BrewViewModelTest.kt:48`, `FrameEvidenceAccumulatorTest.kt:41`, `BrewEventDetectorTest.kt:34` |
| Fake DAOs/utilities live under test sources | Follow | `testutil/FakeDaos.kt`, `CalculatorViewModelTest.kt` |

## Anti-Patterns

| Avoid | Why |
|-------|-----|
| String navigation routes | Breaks type safety; use route objects from `Routes.kt`. |
| Local `remember` for brew data | Loses shared state; use `BrewViewModel` setters. |
| Hardcoded Pulsar ratio 1:16 | Current default is 17f and tests rely on it. |
| Exact grinder settings without ranges | Grinder calibration varies; present ranges and adjustment notes. |
| Direct Room access from Composables | Use ViewModels and repositories. |
| Silent scan/LLM failure | Log and expose unavailable/fallback state. |
| Auto-saving live scan fields | User must confirm or edit before saving. |

<!-- context-init:user-content-below -->
