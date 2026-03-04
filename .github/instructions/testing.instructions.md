---
applyTo: "app/src/test/**/*.kt"
description: "Testing conventions for Starlit Coffee"
---

<!-- context-init:managed -->
- Use JUnit 4 with `@Test`, `@Before`, `@After` annotations
- Set `Dispatchers.setMain(UnconfinedTestDispatcher())` in `@Before`
- Call `Dispatchers.resetMain()` in `@After`
- Assert on `viewModel.uiState.value` directly (synchronous with UnconfinedTestDispatcher)
- Test names use backtick format: `` `description of expected behavior` ``
- Group tests with `// --- Section ---` comments
- Use `assertEquals(expected, actual, delta)` for Float comparisons (delta = 0.01f)
- Pulsar default ratio is 17f — expected water for 20g coffee = 340g
- Grinder IDs must match `DefaultGrinders`: e.g. "1zpresso-zp6-special" not "zp6-special"
