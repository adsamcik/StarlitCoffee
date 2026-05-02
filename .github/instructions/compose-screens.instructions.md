---
applyTo: "app/src/main/java/**/ui/screen/*.kt"
description: "Compose screen conventions for Starlit Coffee"
---

<!-- context-init:managed -->
- Screens receive explicit navigation callbacks (`onBack`, `onNavigateTo...`, `onComplete`) instead of a `NavController`.
- Wire route objects and `savedStateHandle` result passing in `StarlitNavHost.kt`, not inside screen files.
- Observe ViewModel flows with `collectAsStateWithLifecycle()`.
- Local `remember { mutableStateOf(...) }` is for ephemeral UI only: dialogs, sheets, permission flags, selected form rows, and one-shot processing guards.
- Brew method/filter/grinder/bag state belongs to `BrewViewModel`; calculator token/preview state belongs to `CalculatorViewModel`.
- Use `MaterialTheme.colorScheme`, `MaterialTheme.typography`, and Material 3 components for visual styling.
- Mark screen titles/top bars as headings with `Modifier.semantics { heading() }`.
- Use string resources for user-facing copy and content descriptions; keep EN/CS resources aligned.
- For long-running side effects, use `LaunchedEffect`/`DisposableEffect` with stable keys and clean up analyzers/executors/listeners in `onDispose`.
- Keep timer-specific behavior (`KeepScreenOn`, haptics, tones, bloom alerts) localized to timer screens/components.
