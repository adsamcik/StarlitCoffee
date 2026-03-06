---
applyTo: "app/src/main/java/**/ui/screen/*.kt"
description: "Compose screen conventions for Starlit Coffee"
---

<!-- context-init:managed -->
- Screens receive navigation callbacks as lambda parameters instead of `NavController`
  - `onBack: () -> Unit` for back navigation
  - `onNavigateToX: () -> Unit` (or `(arg) -> Unit`) for forward navigation to specific destinations
- Lambda implementations are wired in `StarlitNavHost.kt` where `navController` is available
- Observe state with `val state by brewViewModel.uiState.collectAsStateWithLifecycle()`
- Use `MaterialTheme.colorScheme` for colors, `MaterialTheme.typography` for text styles
- Add `Modifier.semantics { heading() }` on screen title Text composables
- Use `ElevatedCard` for result/info sections
- For Pulsar-specific UI, check `state.method == BrewMethod.PULSAR`
- Never hold brew state in `remember {}` — use BrewViewModel methods
