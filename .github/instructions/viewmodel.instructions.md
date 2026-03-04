---
applyTo: "app/src/main/java/**/viewmodel/*.kt"
description: "ViewModel conventions for Starlit Coffee"
---

<!-- context-init:managed -->
- Single `MutableStateFlow<BrewUiState>` with `_uiState` / `uiState` pattern
- All public setters validate input then call `recalculate()`
- `recalculate()` is the central computation method — updates ALL derived fields
- Use `_uiState.update { it.copy(...) }` for state mutations
- `BrewPhase` includes `instruction` and `valveState` for guided timer
- `buildTimerPhases()` creates Pulsar-specific valve instructions when `method == PULSAR`
- Timer uses `viewModelScope.launch` with `delay(1000L)` per tick
- Grind resolution: no grinder → Generic(descriptor), grinder → Specific(recommendation)
- Guardrails: ratio outside 10–20 warns, bloom > water warns, capacity exceeded warns
