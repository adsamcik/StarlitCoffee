<!-- context-init:version:3.1.0 -->
<!-- context-init:generated:2026-05-02T09:24:05+02:00 -->

# Starlit Coffee - Architecture

<!-- context-init:managed -->

## System Overview

```text
MainActivity
  -> StarlitCoffeeTheme
  -> StarlitNavHost
       -> AppDatabase singleton + repositories + manual ViewModel factories
       -> NavHost with @Serializable routes
       -> Shared BrewViewModel
       -> CalculatorViewModel for expression/preset input
       -> LiveScanViewModel scoped to each LiveScan destination

Core feature layers:
  UI screens/components
    -> ViewModels
      -> domain/BrewCalculator, calculator/CalcEvaluator
      -> repositories
        -> Room DAOs/entities
      -> scan/network/util pipelines
```

## Entry Points

| Path | Purpose |
|------|---------|
| `app/src/main/java/com/adsamcik/starlitcoffee/MainActivity.kt` | Single `ComponentActivity`; handles rating reminder intents and hosts Compose. |
| `app/src/main/java/com/adsamcik/starlitcoffee/StarlitCoffeeApp.kt` | `Application` class referenced from the manifest. |
| `app/src/main/java/com/adsamcik/starlitcoffee/navigation/StarlitNavHost.kt` | Builds the navigation graph, bottom bar, repositories, and app-scoped ViewModels. |
| `app/src/main/java/com/adsamcik/starlitcoffee/navigation/Routes.kt` | Type-safe Navigation Compose route objects and route data classes. |
| `app/src/main/AndroidManifest.xml` | Permissions, `MainActivity`, `BrewTimerService`, FileProvider, and optional camera features. |

## Package Map

| Package | Role | Key files |
|---------|------|-----------|
| `calculator` | Pure calculator expression evaluation for dose/water preview. | `CalcEvaluator.kt` |
| `data/db` | Room database, converters, DAOs, entities, migrations. | `AppDatabase.kt`, `dao/*Dao.kt`, `entity/*Entity.kt` |
| `data/model` | Brew methods, grinder data, coffee metadata, scan models, UI domain models. | `BrewMethod.kt`, `FilterType.kt`, `DefaultGrinders.kt` |
| `data/network` | Coffee metadata lookups and on-device LLM abstraction. | `OpenFoodFactsClient.kt`, `QrLinkMetadataExplorer.kt`, `llm/*` |
| `data/repository` | Thin wrappers over DAOs/DataStore with domain mapping where needed. | `BrewLogRepository.kt`, `CoffeeBagRepository.kt`, `UserPreferencesRepository.kt` |
| `domain` | Pure brew calculation engine. | `BrewCalculator.kt` |
| `navigation` | Type-safe routes and single NavHost. | `Routes.kt`, `StarlitNavHost.kt` |
| `scan` | Live scan consensus, side detection, telemetry, benchmarking, observability. | `FrameEvidenceAccumulator.kt`, `ConsensusEngine.kt`, `SideDetector.kt` |
| `service` | Foreground timer notification and post-brew rating reminder. | `BrewTimerService.kt`, `RatingReminderWorker.kt`, `TimerStateHolder.kt` |
| `ui/component` | Shared Compose components, cards, sheets, dialogs, indicators. | `BagCard.kt`, `BrewRatingSheet.kt`, `ScreenTopBar.kt` |
| `ui/screen` | Full-screen Compose destinations. | `CalculatorBrewScreen.kt`, `BrewTimerScreen.kt`, `LiveScanScreen.kt` |
| `ui/theme` | Material theme, colors, typography, shapes. | `Theme.kt`, `Color.kt`, `Type.kt`, `Shape.kt` |
| `util` | OCR, parsing, normalization, inventory insights, image preprocessing, vibration. | `OcrFieldExtractor.kt`, `CoffeeMetadataNormalizer.kt`, `InventoryAlertEngine.kt` |
| `viewmodel` | App-facing orchestration and screen state. | `BrewViewModel.kt`, `CalculatorViewModel.kt`, `LiveScanViewModel.kt` |

## Component Map

### Navigation and app shell

- `StarlitNavHost.kt` creates `AppDatabase`, `UserPreferencesRepository`, `CupPresetRepository`, `BrewViewModel`, and `CalculatorViewModel`.
- Bottom navigation roots are `CalculatorBrew`, `BrewLogList`, and `More`.
- Nested routes include `GrindPrep`, `BloomTimer`, `BrewTimer`, `BagInventory`, `BarcodeScanner`, `LiveScan`, `BrewLogDetail`, `Settings`, onboarding, and `RescanBag`.
- Results between scan screens and inventory move through `savedStateHandle` keys such as `scan_fields`, `scanned_barcode`, and `rescan_fields`.

### BrewViewModel

- Location: `app/src/main/java/com/adsamcik/starlitcoffee/viewmodel/BrewViewModel.kt`
- Owns brew setup, selected bag, recipes, logs, flavor tags, bag photo processing, known scan values, rating state, timer state, and derived brew output.
- Depends on repositories, `GrinderDataProvider`, QR metadata explorer, LLM provider, ML Kit helpers, and `TimerStateHolder`.
- Uses `BrewCalculator.calculate(...)` for deterministic coffee/water/bloom/time math, then layers on grind, decaf, warnings, bag selection, and UI state.

### CalculatorViewModel

- Location: `app/src/main/java/com/adsamcik/starlitcoffee/viewmodel/CalculatorViewModel.kt`
- Owns calculator tokens, preset rows, ratio, input direction, and live preview.
- Seeds default cup presets and persists last ratio/default direction through `UserPreferencesRepository` when available.
- UI syncs preview dose and ratio into `BrewViewModel` before save/start actions.

### Live scan pipeline

```text
LiveScanScreen
  -> CameraX LifecycleCameraController + ML Kit text/barcode analyzers
  -> LiveScanViewModel
      -> FrameEvidenceAccumulator
          -> ConsensusEngine
          -> SideDetector
          -> enrichment channels for barcode/QR/API/LLM data
      -> LlmInferenceProvider + LlmResultCache
      -> ScanAnalyticsTracker / ScanPerfTracer / ring buffer
  -> user confirms or edits resolved fields
  -> BagInventoryScreen / BrewViewModel saves bag
```

Key properties:
- Regular frames use a conflated channel; golden frames and enrichments use unlimited channels.
- Consensus uses OCR medoid clustering, Bayesian priors from known coffee fields, and quality-weighted voting.
- The user must review/save; live scan results are not auto-saved.

### Persistence

- `AppDatabase.kt` is Room with `exportSchema = true`; current code declares version 15 and exports schemas under `app/schemas/`.
- DAOs return `Flow` for observable collections and `suspend` functions for inserts/updates/deletes.
- Repositories wrap DAOs and keep ViewModels away from direct database details.
- `UserPreferencesRepository` uses DataStore for onboarding, enabled methods, default method/filter/grinder, ratio, input direction, and quick-brew preference.

## Primary Flows

### Onboarding flow

```text
OnboardingMethods -> OnboardingPersonalize
  -> UserPreferencesRepository.completeOnboarding(...)
  -> BrewViewModel.setMethod/filter/grinder(...)
  -> CalculatorBrew
```

Onboarding selections are held in `remember { mutableStateOf(...) }` inside `StarlitNavHost` until the user finishes.

### Brew planning and timer flow

```text
CalculatorBrewScreen
  -> CalculatorViewModel previews dose/water from tokens and ratio
  -> BrewViewModel owns method/filter/grinder/bag and receives synced ratio/dose
  -> startNewBrewSession()
  -> GrindPrep or BrewTimer depending on quick-brew preference
  -> BrewTimerScreen
  -> BrewViewModel.logBrew()
  -> snackbar can navigate to BrewLogList
  -> RatingReminderWorker may prompt for later rating
```

### Bag inventory flow

```text
BagInventoryScreen
  -> manual add, photo picker, barcode scanner, or LiveScan
  -> BrewViewModel.processNewBagPhotos(...) for still images
  -> LiveScanScreen for continuous camera consensus
  -> AddBagSheet / RescanDeltaDialog
  -> CoffeeBagRepository persists bags
  -> selectBagForBrewing(...) returns to CalculatorBrew
```

### Brew calculation flow

```text
effectiveRatio = customRatio or selected RatioPreset
amount + InputMode -> BrewCalculator.computeCoffeeAndWater(...)
method defaults -> bloom, pulses, time, capacity, absorption
selected bag + manual override -> decaf state and time adjustment
grinder/filter/calibration -> GrindResult.Generic or GrindResult.Specific
warnings -> ratio, bloom, capacity, decaf mismatch
```

## Known Gotchas

<!-- context-init:managed -->
- Pulsar default ratio is 17f in `BrewMethod.PULSAR`; do not assume 16f.
- `FilterType` is only meaningful for Pulsar, but state remembers prior Pulsar filter while switching methods.
- `StrengthPreset.ratioOffset` is `Int`, not `Float`.
- `DefaultGrinders` is a static fallback; production grinder data can come from `GrinderDataSource.getInstance(...)` and `assets/grinders.json`.
- Manual DI is intentional for now; do not partly introduce a second injection style.
- Live scan is session-scoped and should be recreated for each camera destination.
- Room migrations and `app/schemas/` exports must be updated together for entity changes.

<!-- context-init:user-content-below -->
