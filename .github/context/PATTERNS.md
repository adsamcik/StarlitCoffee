<!-- context-init:version:3.0.0 -->
<!-- context-init:generated:2026-02-25T05:48:00Z -->

# Starlit Coffee — Patterns & Conventions

<!-- context-init:managed -->

## Naming Conventions

| Type | Pattern | Examples |
|------|---------|---------|
| Package names | `lowercase.dot.separated` | `data.model`, `ui.screen`, `data.db.dao` |
| Screen composables | `{Name}Screen` | `MethodPickerScreen`, `ResultScreen` |
| ViewModel | `{Domain}ViewModel` | `BrewViewModel` |
| UI state | `{Domain}UiState` | `BrewUiState` |
| Room entities | `{Name}Entity` | `SavedRecipeEntity`, `CoffeeBagEntity` |
| Room DAOs | `{Name}Dao` | `RecipeDao`, `CoffeeBagDao` |
| Route objects | PascalCase `@Serializable object` | `MethodPicker`, `AmountStrength` |
| Enum values | `SCREAMING_SNAKE` | `PULSAR`, `METAL_19K`, `COFFEE_TO_WATER` |
| Enum properties | camelCase | `displayName`, `defaultRatio`, `ratioOffset` |
| Private state flows | `_uiState` prefix | `private val _uiState = MutableStateFlow(...)` |
| Constants | `SCREAMING_SNAKE` in companion/top-level | `TRANSITION_DURATION`, `FADE_DURATION` |

## State Management

| Convention | Status |
|-----------|--------|
| Single `StateFlow<UiState>` per ViewModel | Follow |
| Use `_uiState.update { it.copy(...) }` for mutations | Follow |
| Screens observe with `collectAsStateWithLifecycle()` | Follow |
| Call `recalculate()` after any state change that affects brew output | Follow |
| Input validation in setter (reject non-numeric) before updating state | Follow |

```kotlin
// Follow: Setter validates then updates + recalculates
fun setAmount(amount: String) {
    if (amount.isNotEmpty() && amount.toFloatOrNull() == null) return
    _uiState.update { it.copy(amount = amount) }
    recalculate()
}
```

## Compose Patterns

| Convention | Status |
|-----------|--------|
| Screens receive `navController` + `brewViewModel` as params | Follow |
| Bottom nav bar visible only on tab root destinations | Follow |
| Fade+slide enter transitions (300ms), fade exit (200ms) | Follow |
| `ElevatedCard` for result/info cards | Follow |
| `MaterialTheme.colorScheme` for all colors | Follow |
| `Modifier.semantics { heading() }` for screen titles | Follow |
| `LiveRegion` announcements for timer updates | Follow |
| `FLAG_KEEP_SCREEN_ON` during brew timer | Follow |

## Navigation Patterns

| Convention | Status |
|-----------|--------|
| `@Serializable object` routes in `Routes.kt` | Follow |
| Navigate forward: `navController.navigate(RouteObject)` | Follow |
| Tab switches use `popUpTo(startDestination) { saveState = true }` | Follow |
| `launchSingleTop = true` + `restoreState = true` for tabs | Follow |

## Enum/Data Model Patterns

| Convention | Status |
|-----------|--------|
| Brew methods encode ALL defaults in enum constructor | Follow |
| New methods = new enum entry, no screen changes needed | Follow |
| `StrengthPreset.ratioOffset` is `Int` (not Float) | Follow |
| `FilterType` includes `description` and `cupProfile` | Follow |
| `TasteFeedback.getAdjustmentText()` takes `hasGrinder` + `isPulsar` | Follow |
| Grinder data in static `DefaultGrinders` object | Follow |
| Grind recommendations matched by `grinderId + methodId + filterType` | Follow |

## Error Handling

| Convention | Status |
|-----------|--------|
| Input fields reject invalid chars via `toFloatOrNull()` / `toIntOrNull()` | Follow |
| Guardrail warnings (not blocking): ratio, bloom, capacity | Follow |
| `coerceIn()` / `coerceAtLeast()` for numeric bounds | Follow |
| No try-catch in ViewModel calculation code (no IO) | Follow |

## Testing Patterns

| Convention | Status |
|-----------|--------|
| Test file mirrors source: `viewmodel/BrewViewModelTest.kt` | Follow |
| `@Before` sets `UnconfinedTestDispatcher` as main dispatcher | Follow |
| `@After` calls `Dispatchers.resetMain()` | Follow |
| Test names use backtick strings: `` `Pulsar default ratio is 17` `` | Follow |
| Tests assert on `viewModel.uiState.value` (synchronous with Unconfined) | Follow |
| Group tests by feature with `// --- Section ---` comments | Follow |

## Pulsar-Specific Patterns

| Convention | Status |
|-----------|--------|
| `BrewPhase` includes `instruction` and `valveState` fields | Follow |
| Timer phases have Pulsar-specific valve guidance text | Follow |
| `isPulsar` boolean passed to context-sensitive logic | Follow |
| Valve states: "open → close" (bloom), "open" (pours/drawdown) | Follow |
| Astringent feedback is Pulsar-aware with bed depth advice | Follow |

## Anti-Patterns (Do NOT)

| Anti-Pattern | Why |
|-------------|-----|
| Holding brew state in `remember {}` | Breaks shared state across screens |
| String-based navigation | Loses type safety, breaks at runtime |
| Hardcoding Pulsar ratio as 1:16 | Research shows 1:17 is correct |
| Single exact grind number | Varies by calibration; always show ranges |
| Blocking grinder selection | Optional layer — app must work without |
| Running Room queries on main thread | Will crash; use `suspend` in DAOs |
