---
applyTo: "app/src/main/java/**/viewmodel/*.kt"
description: "ViewModel conventions for Starlit Coffee"
---

<!-- context-init:managed -->
- ViewModels expose read-only `StateFlow` from private `MutableStateFlow` (`_uiState` -> `uiState`, `_evidence` -> `evidence`, etc.).
- Mutate flow-backed state with `.update { it.copy(...) }` when deriving from previous state; direct `.value =` is acceptable for standalone session state in `LiveScanViewModel`.
- Keep brew configuration and derived brew output in `BrewViewModel`; composables call setters instead of owning brew data locally.
- Public setters validate numeric input before state mutation and call `recalculate()` when brew output changes.
- `BrewViewModel.recalculate()` delegates core math to `BrewCalculator` and then resolves grind, decaf, warnings, bloom duration, and selected-bag effects.
- Use `viewModelScope.launch` for repository, DataStore, timer, and scan orchestration work; use `withContext(Dispatchers.IO)` for bitmap/file/ML Kit work.
- Timer logic is wall-clock anchored with a 250 ms loop; bloom countdown is a separate coroutine and must cancel/resume with timer state.
- `LiveScanViewModel` is nav-scoped per scan session; do not share it at app scope.
- LLM/Mindlayer failures must be logged and surfaced with explicit unavailable/fallback state, not hidden as success.
- Factories manually wire Room repositories and providers; keep TODOs pointing to future DI rather than introducing ad-hoc globals.
