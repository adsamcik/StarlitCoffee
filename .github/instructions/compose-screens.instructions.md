---
applyTo: "app/src/main/java/**/ui/screen/*.kt"
description: "Compose screen conventions for Starlit Coffee"
---

<!-- context-init:managed -->
- All screens take `navController: NavHostController` and `brewViewModel: BrewViewModel` as parameters
- Observe state with `val state by brewViewModel.uiState.collectAsStateWithLifecycle()`
- Use `MaterialTheme.colorScheme` for colors, `MaterialTheme.typography` for text styles
- Add `Modifier.semantics { heading() }` on screen title Text composables
- Use `ElevatedCard` for result/info sections
- Navigate forward: `navController.navigate(RouteObject)`, back: `navController.popBackStack()`
- For Pulsar-specific UI, check `state.method == BrewMethod.PULSAR`
- Never hold brew state in `remember {}` — use BrewViewModel methods
