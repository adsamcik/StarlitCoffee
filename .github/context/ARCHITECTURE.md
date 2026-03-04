<!-- context-init:version:3.0.0 -->
<!-- context-init:generated:2026-02-25T05:48:00Z -->

# Starlit Coffee — Architecture

<!-- context-init:managed -->

## System Overview

```
┌─────────────────────────────────────────────────────────┐
│                    MainActivity                          │
│  enableEdgeToEdge() → StarlitCoffeeTheme                │
│                    ↓                                     │
│              StarlitNavHost                              │
│    ┌─────────────────────────────┐                      │
│    │  BrewViewModel (shared)     │← Single instance     │
│    │  MutableStateFlow<UiState>  │  for all screens     │
│    └─────────────┬───────────────┘                      │
│                  ↓                                       │
│    ┌─────────────────────────────┐                      │
│    │      NavHost (Compose)      │                      │
│    │                             │                      │
│    │  Brew Flow:                 │  Tab Roots:          │
│    │  MethodPicker → InputMode  │  MethodPicker        │
│    │  → AmountStrength → Result │  SavedRecipes        │
│    │  → BrewTimer → Feedback    │  BagInventory        │
│    │                             │  BrewLogList         │
│    └─────────────────────────────┘                      │
│                  ↓                                       │
│    ┌─────────────────────────────┐                      │
│    │  Room Database (AppDatabase)│← Not yet wired       │
│    │  ├── RecipeDao              │  to screens          │
│    │  ├── CoffeeBagDao           │                      │
│    │  ├── BrewLogDao             │                      │
│    │  └── GrinderDao             │                      │
│    └─────────────────────────────┘                      │
└─────────────────────────────────────────────────────────┘
```

## Package Structure

```
com.adsamcik.starlitcoffee/
├── MainActivity.kt              # Single activity entry point
├── StarlitCoffeeApp.kt          # Application class
├── data/
│   ├── model/                   # Domain models (enums, data classes)
│   │   ├── BrewMethod.kt        # 7 brew methods with defaults
│   │   ├── BrewState.kt         # Brew state enum
│   │   ├── CalibrationStyle.kt  # Grinder calibration options
│   │   ├── CoffeeBagStatus.kt   # Bag lifecycle states
│   │   ├── DefaultGrinders.kt   # Static grinder data + recommendations
│   │   ├── FilterType.kt        # Paper/19K/40K with descriptions
│   │   ├── GrindDescriptor.kt   # Generic grind descriptors
│   │   ├── Grinder.kt           # Grinder data model
│   │   ├── GrindRecommendation.kt # Grinder-specific recommendations
│   │   ├── InputMode.kt         # Coffee→Water, Water→Coffee, CupSize
│   │   ├── StrengthPreset.kt    # Light/Balanced/Strong with offsets
│   │   └── TasteFeedback.kt     # Taste feedback + adjustment logic
│   ├── db/                      # Room database layer
│   │   ├── AppDatabase.kt       # Room DB singleton
│   │   ├── entity/              # 4 Room entities
│   │   │   ├── SavedRecipeEntity.kt
│   │   │   ├── CoffeeBagEntity.kt
│   │   │   ├── BrewLogEntity.kt
│   │   │   └── GrinderEntity.kt
│   │   └── dao/                 # 4 Room DAOs
│   │       ├── RecipeDao.kt
│   │       ├── CoffeeBagDao.kt
│   │       ├── BrewLogDao.kt
│   │       └── GrinderDao.kt
│   └── repository/              # (Empty — repository layer planned)
├── navigation/
│   ├── Routes.kt                # 9 @Serializable route objects
│   └── StarlitNavHost.kt        # NavHost + bottom bar + transitions
├── ui/
│   ├── screen/                  # 9 full Compose screens
│   │   ├── MethodPickerScreen.kt
│   │   ├── InputModeScreen.kt
│   │   ├── AmountStrengthScreen.kt
│   │   ├── ResultScreen.kt
│   │   ├── BrewTimerScreen.kt
│   │   ├── TasteFeedbackScreen.kt
│   │   ├── SavedRecipesScreen.kt
│   │   ├── BagInventoryScreen.kt
│   │   └── BrewLogScreen.kt
│   ├── component/               # (Empty — shared components planned)
│   └── theme/
│       ├── Color.kt             # Coffee-themed palette (light + dark)
│       ├── Type.kt              # Material 3 typography
│       ├── Shape.kt             # Material 3 shapes
│       └── Theme.kt             # Dynamic color with fallback scheme
└── viewmodel/
    └── BrewViewModel.kt         # All state + calculations + timer
```

## Component Map

<!-- context-init:managed -->

### BrewViewModel (Core Engine)
- **Location**: `viewmodel/BrewViewModel.kt`
- **Purpose**: Holds all brew state, calculations, timer, feedback
- **Key types**: `BrewUiState`, `BrewPhase`, `GrindResult`
- **State**: Single `StateFlow<BrewUiState>` drives all screens
- **Methods**: `setMethod()`, `setAmount()`, `recalculate()`, `startTimer()`, `setTasteFeedback()`
- **Dependencies**: `DefaultGrinders` (static data)

### Data Models
- **Location**: `data/model/`
- **Purpose**: Domain enums and data classes
- **Key pattern**: `BrewMethod` enum encodes all defaults per method (ratio, bloom, pulses, grind, capacity)
- **Exports**: Used by ViewModel and Screens

### Navigation
- **Location**: `navigation/`
- **Purpose**: Type-safe Compose Navigation with bottom bar
- **Key file**: `StarlitNavHost.kt` — creates shared ViewModel, manages transitions
- **Routes**: 9 serializable objects (brew flow + tab roots)

### Room Database
- **Location**: `data/db/`
- **Purpose**: Persistence for recipes, bags, brew logs, grinders
- **Status**: Entities and DAOs defined, not yet wired to UI screens
- **Singleton**: `AppDatabase.getInstance(context)`

### Theme
- **Location**: `ui/theme/`
- **Purpose**: Material 3 Expressive coffee-themed design
- **Dynamic color**: Uses system dynamic colors (Android 12+) with fallback

## Data Flows

<!-- context-init:managed -->

### Primary Brew Flow
```
MethodPicker → user taps method → viewModel.setMethod()
  ↓
InputMode → user picks direction → viewModel.setInputMode()
  ↓
AmountStrength → user enters dose/ratio → viewModel.setAmount(), setStrengthPreset()
  ↓ (recalculate() fires on every change)
Result → displays computed coffeeG, waterG, bloom, pulses, grind
  ↓
BrewTimer → guided phases with valve instructions (Pulsar)
  ↓
TasteFeedback → user rates taste → viewModel.setTasteFeedback()
  → getAdjustmentText() returns next-brew advice
```

### Calculation Flow (inside BrewViewModel.recalculate())
```
effectiveRatio = customRatio ?: (method.defaultRatio + preset.ratioOffset)
  ↓
InputMode determines direction:
  COFFEE_TO_WATER: waterG = coffeeG × ratio
  WATER_TO_COFFEE: coffeeG = waterG ÷ ratio
  CUP_SIZE_TO_BOTH: same as WATER_TO_COFFEE
  ↓
bloomG = coffeeG × bloomMultiplier
remainingWaterG = waterG − bloomG
pulseSizeG = remainingWaterG ÷ pulseCount
  ↓
grindResult = resolveGrindResult(grinderId, method, filter, calibration)
timerPhases = buildTimerPhases(method, bloom, pulses, water, time)
warnings = {capacity, ratio, bloom} guardrails
```

## Known Gaps

<!-- context-init:managed -->
- Room DB not wired to SavedRecipesScreen, BagInventoryScreen, BrewLogScreen (use local placeholder state)
- No repository layer between ViewModel and Room
- Barcode scanning UI not implemented (ML Kit + CameraX deps present)
- No DI framework (manual singleton only)
- `ui/component/` directory empty — no shared composables extracted yet
