---
applyTo: "app/src/test/**/*.kt"
description: "Testing conventions for Starlit Coffee"
---

<!-- context-init:managed -->
- Use JUnit 4 with `@Test`, `@Before`, and `@After`.
- Test names use backtick behavior descriptions: `` `coffee to water with Pulsar default ratio` ``.
- Group related tests with `// --- Section ---` comments.
- For coroutine/ViewModel tests, set `Dispatchers.setMain(UnconfinedTestDispatcher())` in `@Before` and call `Dispatchers.resetMain()` in `@After`.
- Use `runTest` and `testScheduler.advanceUntilIdle()` when testing async accumulator/repository behavior.
- Assert `StateFlow` state directly when using `UnconfinedTestDispatcher`.
- Use fake DAOs/test utilities from `app/src/test/java/.../testutil` instead of real Room when unit testing ViewModels/repositories.
- Use `assertEquals(expected, actual, 0.01f)` for Float comparisons.
- Pulsar default ratio is 17f; 20g coffee should produce 340g water unless a preset/custom ratio overrides it.
- Include regression tests for parser/normalizer/scanner edge cases when adding coffee metadata fields.
