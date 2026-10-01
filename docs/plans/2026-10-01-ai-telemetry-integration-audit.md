# Starlit Coffee AI and telemetry integration audit

Reviewed on 2026-10-01 against Starlit Coffee `06e11f0` and the existing working changes. This audit preserves the original findings; subsequent fixes are recorded with each finding below. Existing staged and unstaged application work was preserved.

The published dependencies are compatible at build level, but the integration has gaps in OCR recovery, diagnostic privacy, AI failure reporting, and recognition UX. Passing unit tests does not cover these scenarios. The local development checkouts also differ from the latest published packages and require separate coordination.

## Versions and source boundaries

| Component | App dependency | Latest published release | Local development checkout |
| --- | --- | --- | --- |
| Tracebox | `0.1.0-alpha.7` | [`0.1.0-alpha.8`](https://github.com/adsamcik/Tracebox/releases/tag/v0.1.0-alpha.8), published 2026-09-06, tag `007a3db` | `3515e41` on `codex/personal-project-scope`, declaring `0.1.0-alpha.3` |
| Mindlayer SDK | `1.0.0-alpha.7` | [`1.0.0-alpha.7`](https://github.com/adsamcik/Mindlayer/releases/tag/v1.0.0-alpha.7), published 2026-08-17 | `4ab0030` on `main`, with unreleased contract `1.4.0` and service API `11`; version-catalog edits already present |

Release metadata was checked through GitHub and compared with local tags and source. Remote default-branch heads were Tracebox `6dc5f0` and Mindlayer `4a5530c`; neither identifies the current local development checkout. Version numbers alone therefore do not establish which implementation is in use.

Tracebox alpha.8 adds bounded local history queries, exception cause/suppressed structure, and historical Android exit PSS/RSS. These are useful diagnostics improvements. Its optional-native consumer-rule fix applies to consumers that omit native capture; Starlit includes the native module. The existing configuration and logging API remain compatible with the published release. [Release notes](https://github.com/adsamcik/Tracebox/releases/tag/v0.1.0-alpha.8).

The local Tracebox branch is a different API contract: its logger accepts `String` templates and has no `LogTemplate`, while its UI strings accept literal `String` values. Starlit imports `LogTemplate` throughout production code and passes a resource ID to `TraceboxDiagnosticsUiStrings.supportDescription`. Substituting that checkout would require a source migration and localization review. It is not equivalent to changing the app pin to published alpha.8. This incompatibility is established by source inspection; an app build with the local checkout was not attempted.

The local Mindlayer SDK keeps the existing inference/OCR signatures and appends AIDL methods. Its new `prewarmForContext` method has a default implementation, and no exhaustive app `ConnectionState` switch creates an obvious source blocker. That is static compatibility evidence only; the app was not compiled against a newly built local SDK.

## Priority findings

### P1 An OCR timeout skips the bundled recognizer

`MindlayerOcrService.recognize` owns a 60-second `withTimeout`, but rethrows its `TimeoutCancellationException` through the general cancellation handler. `FallbackOcrService` also rethrows cancellation, so ML Kit is never tried. The worker then propagates cancellation before persisting its ordinary terminal failure result.

Evidence: `MindlayerOcrService.kt:117-132`, `FallbackOcrService.kt:38-45`, and `BagExtractionWorker.kt:123`. A standalone Kotlin probe compiled the exact current fallback helper and ran an inner timeout beneath an outer deadline. It reported `escaped:TimeoutCancellationException`, `fallbackCalls=0`, and `outerActive=true`. This confirms the coroutine behavior without claiming a live OCR engine timeout.

Smallest remedy: convert the timeout owned by OCR into a null primary result, while checking that the caller is still active. Preserve real cancellation and the overall scan deadline. Add distinct regressions for owned timeout, caller cancellation, outer deadline, and durable worker outcome. Existing tests cover ordinary failure and cancellation, but not this distinction.

Resolution on 2026-10-01: the production one-shot boundary now uses `withTimeoutOrNull`, returning a null primary result only when its own 60-second budget expires. The SDK's bounded five-second connection timeout also becomes unavailable when the caller remains active. Activity checks before converting timeouts or ordinary failures prevent fallback when the SDK translates a cancelled caller's timeout into a typed connection error. `FallbackOcrService` continues to propagate cancellation unchanged, and fallback uses the original scan deadline. Static timeout warnings contain no recognized text.

Twelve unit regressions exercise the production service with fake Mindlayer handles: submission/await timeouts, cleanup before fallback, timely success, caller cancellation, independently nested timeout, expiry during OCR or fallback, connection timeout ownership, and SDK timeout-to-error translation. Before the timeout fix, four cases reproduced the escaping timeout. Two additional cases reproduced fallback starting after SDK-translated caller cancellation before the activity guards were added. This verifies the coroutine boundary, not Android bitmap encoding, native recognition, or a durable worker run on a device.

Final fix validation used the repository's pinned Mindlayer `1.0.0-alpha.7` and Tracebox `0.1.0-alpha.7`: all 12 new regressions passed, and the full app unit suite reported 1,455 tests (1,451 executed, four skipped), zero failures, and zero errors. Detekt, debug APK assembly, and release-source compilation passed. The local log is `build/ocr-timeout-fix-validation.log`.

### P1 AI diagnostic text bypasses the visible privacy controls

Production wiring always injects `PersistentLlmDiagnosticsRecorder`. It stores up to 600 characters of model output and raw error messages in `scan_llm_diagnostics` SharedPreferences, regardless of Tracebox policy. Disabling Tracebox does not stop those records; deleting all Tracebox data does not delete them. The separate scan-history controls are debug-only, leaving release users without a matching removal control.

Evidence: `StarlitCoffeeApp.kt:175-178`, `MindlayerLlmInferenceProvider.kt:139-150`, `ScanLlmDiagnostics.kt:32-39`, and `SettingsScreen.kt:484-487`. `docs/privacy/index.html:84-87` promises default text exclusion and deletion of all diagnostic data. Backup rules do exclude these preferences, and no automatic uploader was found; the issue is local capture and control consistency.

Smallest remedy: keep bounded numeric and typed diagnostic records within the same capture/deletion policy. If raw content remains useful for debugging, make it explicit opt-in, removable, and separately reviewed before sharing. Verify disable, delete, and restart with a seeded model-output marker.

Resolution on 2026-10-01: AI pass records now contain closed pass/status enums, timing/count measurements, and optional recognized numeric SDK error codes. They are written directly through Tracebox's privacy-aware logger, using its capture gate, storage fences, deletion, and reviewed exports. The app no longer stores or reads raw AI pass output/error samples in preferences, and the old direct raw-pass report section has been removed.

Each app startup deletes the entire legacy `scan_llm_diagnostics` preferences file, including its backup and unknown keys, before providers can start. The existing Diagnostics delete-all handle also retries this cleanup. A failed legacy deletion makes an otherwise completed Tracebox deletion report pending rather than silently reporting success. Independent debug scan-session history remains available, with its count/clear wording updated in all 23 languages. The separately opted-in debug correction dataset is outside this AI pass-store finding.

Privacy fix validation passed 18 unit regressions for typed serialization, default Tracebox privacy classification, recognized numeric error codes, and delete-all cleanup/retry behavior. The full unit suite reported 1,470 tests (1,466 executed, four skipped), zero failures, and zero errors. Detekt, locale parity, debug/app-test APK assembly, and release-source compilation passed using the unchanged alpha.7 dependency pins. The local build log is `build/ai-diagnostics-privacy-validation.log`.

Three instrumented cases passed on an API 36 x86_64 emulator in a disposable `.privacycheck` installation: whole-file migration with a malformed backup and unknown keys; a positive durable Tracebox write followed by disable, delayed completion, delete-all, and no recreation; and a separate process restart proving persisted disable plus production startup migration. Every invocation reported `INSTRUMENTATION_STATUS_CODE: 0` and `OK (1 test)`, including the restart case. The runtime fixture used managed capture with native capture disabled. These results establish the Android storage/policy boundary; they do not establish live Mindlayer inference, native capture, or physical-device behavior.

The destructive instrumented cases deliberately skip ordinary app installations. Reproduce them with a temporary Gradle init script that sets `android.buildTypes.getByName('debug').applicationIdSuffix = '.privacycheck'` inside `androidComponents.finalizeDsl`, then run `:app:testDebugUnitTest :app:detekt :app:assembleDebug :app:assembleDebugAndroidTest :app:compileReleaseKotlin`. Install only the isolated app and test APKs. Invoke `AiDiagnosticsPrivacyInstrumentedTest#legacyMigrationDeletesSamplesUnknownKeysAndBackup`, then `#disableDeleteAndSeedRestartState`, force-stop only `com.adsamcik.starlitcoffee.privacycheck`, and invoke `#restartPreservesDisableAndPurgesLegacySamples` using separate `am instrument` commands. Require an executed pass rather than an assumption skip for all three phases.

Device logs and the isolated APKs are retained locally under `build/ai-privacy-validation`. Both disposable packages were removed after validation, preserving the existing development installation and data. Normal debug/app-test APK assembly then passed without the init script, restoring the `.debug` outputs (`build/ai-diagnostics-restore-debug.log`).

### P2 AI unavailability leads to the wrong recovery action

Connection failures, failed model readiness, and missing OCR text all become `Unavailable`. When Mindlayer is installed, the UI interprets ordinary unavailability as authorization required and offers “Use label recognition.” An already-approved user can therefore repeat consent and retry the same model or input failure. Low memory, integrity mismatch, and backend failure need different remedies.

Evidence: `MindlayerLlmInferenceProvider.kt:337-366`, `BagPhotoExtractor.kt:1181-1188`, `RecognitionUiState.kt:124-127`, and `GuidedScanFlow.kt:479-488`.

Smallest remedy: carry a typed availability reason through the pipeline and map it to retry, retake, setup, consent, or concise temporary-unavailability feedback. Test the complete provider-to-review mapping, including installed and already-authorized cases.

### P2 Rescans conceal recognition that is still running

Draft review knows whether recognition is processing, but the existing-bag rescan route drops that state and its AI status before showing `RescanDeltaDialog`. The dialog can say “No changes found” or “Rescan results” and permit an update/new bag while AI is still working. It also lacks the consent, setup, and retry actions available for a new bag.

Evidence: `GuidedScanFlow.kt:329-333`, `376-388`, and `684-688`; `RescanDeltaDialog.kt:217-241` and `295-316`.

Smallest remedy: reuse the existing recognition presentation and recovery actions in rescan review. Make saving available partial results understandable without adding a parallel workflow. Verify running, partial, setup-required, failed, and completed rescans.

### P2 Recoverable connection states are treated as unavailable

The provider's availability and feature gates accept only `CONNECTED` and `CONNECTING`. During `RECOVERING`, which exists in published alpha.7, the extractor can return unavailable before reaching the SDK's bounded reconnecting call. A scan that starts during service replacement can therefore miss otherwise recoverable enrichment.

Evidence: `MindlayerLlmInferenceProvider.kt:206-235` and `BagPhotoExtractor.kt:1112`/`1147`.

Smallest remedy: distinguish permanently unavailable states from recoverable ones and allow a bounded `awaitConnected` attempt. The unreleased SDK's additional `SUSPENDED_IDLE` needs the same treatment for background work after idle disconnect. Current alpha.7 clients do not create that state, including when paired with the new service; this extension is an SDK-upgrade concern.

### P2 Warmup allocates more context than the app requests

The app requests 8,192 total session tokens but calls legacy `prewarm(CPU)` before inference. The published alpha.7 service warms at its device-tier ceiling under normal pressure, from 32,768 to 131,072 tokens. The app's session limit does not constrain that earlier allocation. The local unreleased SDK introduces context-aware prewarm for this purpose.

Evidence: app provider prewarm calls at `369`, `398`, `436`, `521`, and `594`; published Mindlayer tag `v1.0.0-alpha.7`, `ServiceBinder.prewarm`, `runtimeMaxTokensCeiling`, and `MemoryBudget.recommendedMaxTokens`. The app comment describing legacy prewarm as blocking until ready is also inaccurate: SDK submission is fire-and-forget.

Smallest remedy: coordinate SDK adoption and capability-gated `prewarmForContext(8192, CPU)`, preserving the existing CPU crash workaround and explicitly defining behavior with old services. Validate allocation and latency on physical hardware; no memory or performance reduction is established by this audit.

### P2 Context overflow has no compact recovery

Every request uses 8,192 tokens, while text prompts include uncapped OCR, existing values, and vocabulary. Memory pressure can reduce the service's effective context. Typed `INPUT_EXCEEDS_CONTEXT` becomes a generic nonretryable failure, without compacting optional prompt context or using the reported remaining budget.

Evidence: `MindlayerLlmInferenceProvider.kt:323-329`, `749-757`, and `921-923`.

Smallest remedy: budget optional context and use a compact extraction prompt or bounded chunking for typed overflow. Preserve source OCR and avoid automatic context growth under memory pressure. Test long front/back labels, large saved vocabulary, and reduced effective context.

## Telemetry gaps

### Scan summaries have no producers

There are no production callers of `ScanSessionRingBuffer.save` and no `ScanSessionSummary` producers. `ScanAnalyticsTracker` has no boundary callers apart from field review; its boundary events also use DEBUG and would be dropped by the standard INFO policy. Current photo scans cannot populate the intended duration, completion, abandonment, retry, or resolved-field summary. Existing scan history can remain empty or show only old records.

Smallest remedy: emit one bounded start/terminal scan record from the durable photo workflow, using a shared scan/work correlation identifier, typed outcome, timing, stage/attempt counts, and field counts. Adapt the old frame-oriented summary to the current photo flow. Keep useful support events within the normal capture policy.

### Parsed failures are recorded as successful passes

TEXT, VISION, COMBINE, and REFINE record `SUCCESS` before parsing/validating the generated response. Malformed JSON returns `Failed`, but the retained diagnostic still says successful.

Evidence: `MindlayerLlmInferenceProvider.kt:306-307`, `474-475`, `557-558`, `630-631`, and `1204-1209`. Existing tests cover malformed JSON and diagnostic serialization independently, without verifying their combined outcome.

Smallest remedy: record the final validated result. Distinguish generation completion from parsing/validation failure with bounded statuses.

### Release packages omit actionable AI failure codes

AI failure reasons are passed to Tracebox as ordinary strings and are correctly redacted. The raw reason lives in the separate LLM preferences, outside the release support package. Connection/readiness/setup failures can also return before any pass record is created. Support can receive “LLM enrichment failed: [redacted]” without the useful reason or a correlated pass.

Evidence: `BagPhotoExtractor.kt:1182`, `1192`, and `1233`; `MindlayerLlmInferenceProvider.kt:336-366`; release Diagnostics uses only the Tracebox package flow.

Smallest remedy: persist typed error codes, readiness/setup outcomes, pass, attempt, provider route, SDK/service version, context budget, and correlation metadata. Use bounded throwable structure when available. Do not mark arbitrary model/error strings public.

### OCR fallback and performance lack measurements

ML Kit failures can become null without retaining their outcome; the fallback helper also loses provider/fallback attribution. No app production call uses Tracebox's performance measurement APIs, although the embedded diagnostics UI exposes their controls. A package cannot reliably distinguish no text, primary failure, successful fallback, preprocessing time, permit waiting, generation time, and validation time.

Evidence: `MlKitOcrService.kt:35-46`, `FallbackOcrService.kt:38-51`, and `DiagnosticsScreen.kt:52-57`, plus production callsite searches.

Smallest remedy: add bounded provider/outcome events and timings at these existing boundaries. Keep them out of the primary UI. Test that diagnostics-disabled policy suppresses both measurements and retained support records.

The core `BrewSessionRuntime` also has no Tracebox callsites. Existing error logging in its callers can identify thrown failures, but cannot reconstruct a completed, missed, or recovered stage transition. If brew-timer/notification support needs that evidence, add a bounded transition/recovery record with session correlation and an outcome; avoid recording coffee names or introducing general usage analytics.

## Other usability gaps

“Finish setup” can silently do nothing when the service/setup action is null; launch exceptions are logged without user feedback (`BrewViewModel.kt:2298-2311`). Return an explicit launch outcome and provide concise inline feedback with an appropriate route to Mindlayer.

Recognition status has a polite live-region option, but `announceUpdate` defaults false and no production caller enables it (`AddBagSheet.kt:2013-2014`). Progress/completion changes can remain unannounced while a screen-reader user edits another field. Announce meaningful transitions and verify Compose semantics and actual TalkBack behavior.

The Settings recognition switch records preference without consent/readiness/setup feedback. Its checked state should be understood as preference, not proof that models and authorization are ready. The contextual consent flow and manual fallback are sensible defaults; improve feedback without adding setup decisions to ordinary bag entry.

After a coordinated Mindlayer update, requalify the app's process-wide single-vision-attempt limit and global CPU workaround. Newer local LiteRT dependencies and emulator evidence do not prove those restrictions can safely be removed on physical devices. The existing architecture test checks source presence of CPU prewarm; it does not establish runtime backend choice, readiness, allocation, or repeated vision reliability.

## What is already sound

Tracebox installs before ordinary startup. The dedicated handler process skips Mindlayer and application recovery. Native capture is gated to supported 64-bit processes; Rust-only controls are omitted. Capture policy is configured to persist across restart, logging uses static templates with private values classified, sharing requires review, and storage/backup rules exclude diagnostic preferences and photos.

Baseline ML Kit OCR and manual entry remain available without opting into Mindlayer. Recognition preference starts undecided, consent appears contextually, drafts and background extraction have durable recovery paths, and users review/edit extracted fields before saving. These strengths should be preserved while addressing the identified failure cases.

## Validation evidence

The original pinned dependency set passed package access checks for both registries (HTTP 200), debug APK assembly, release Kotlin compilation, and Android-test Kotlin compilation. Its unit task reused up-to-date results containing 1,443 tests, 0 failures, 0 errors, and 4 skipped tests.

A temporary Gradle init script selected Tracebox alpha.8 for all `io.github.tracebox` modules without modifying the catalog or source. Debug and release Kotlin compilation, Android-test Kotlin compilation, and debug APK assembly passed against that selection. The unit task was explicitly rerun: 1,443 tests, 0 failures, 0 errors, and 4 skipped (1,439 executed). Detekt passed using its up-to-date result.

Lint failed with 24 errors on the unchanged source/configuration: one Gradle update notice, three Tracebox catalog update notices, one configuration-aware resource lookup finding, one modifier-parameter finding, 16 unused resources, and two UseKtx findings. The dependency override changes resolved artifacts, so lint still sees the catalog's alpha.7 declaration. This is a successful compilation/test validation with an incomplete static-analysis gate, not release readiness.

Evidence files from this run are `build/tracebox-alpha8-validation.log`, `build/integration-resolved-artifacts.log`, `app/build/test-results/testDebugUnitTest`, and `app/build/reports/lint-results-debug.xml`. Build outputs are local generated evidence rather than committed artifacts. No minified release APK was built.

The resolved artifact task confirmed Mindlayer SDK alpha.7 and all ten Tracebox modules at alpha.8. Key AAR SHA-256 hashes were:

| Artifact | SHA-256 |
| --- | --- |
| Mindlayer SDK alpha.7 | `14b8e6833dccc940457950d8602b214dc0ea653b59cff4383240ebc9a72dbcf1` |
| Tracebox alpha.8 | `ee29dba5d28cafdd13a3e0da711cf07e747bf1ca7e7f2f46f13dd6418b7dd152` |
| Tracebox native alpha.8 | `71cb1773f416deeb63c9747a8daeb7fc520c716311dd8ac9ae8b7730a923330e` |
| Tracebox Compose UI alpha.8 | `c40cdd83728cb703d85aad1c07ed703153bcd75f525d66f1dd99eec6739e09f2` |

The alpha.8 validation APK was retained separately at `build/integration-validation/tracebox-alpha8-app-debug.apk`, SHA-256 `3c3c99cf10ec63e61d6fa3acce249da8775743ad8f2ba9747bfe90c6b8ed71f9`. It was not installed.

After validation, normal `:app:assembleDebug` succeeded again without the override, restoring the ordinary debug APK to the repository's unchanged alpha.7 dependency pins. The preserved alpha.8 APK and test evidence remain separate.

The validation command used the installed Gradle 9.7.1 distribution with the init script and these tasks: `:app:testDebugUnitTest --rerun :app:compileReleaseKotlin :app:compileDebugAndroidTestKotlin :app:assembleDebug :app:detekt :app:lintDebug --continue --no-daemon --console=plain --stacktrace`.

The OCR timeout probe is isolated under the system temporary directory and used cached Kotlin/coroutines dependencies. It confirms owned-timeout propagation through the actual fallback helper, not a real engine, worker, camera, or device test.

During the original audit, device validation was not performed because the sole connected emulator was claimed by another task. No app install, device reset, model replacement, destructive diagnostics deletion, or sharing was performed during that audit. The later privacy fix has isolated device/restart coverage described with its resolution above. Native/JVM crash and ANR capture, reviewed package contents, TalkBack, physical-device inference, backend selection, memory use, and minified runtime behavior remain unverified.

## Proposed implementation order

1. Correct owned OCR timeout handling and diagnostic privacy/deletion semantics.
2. Carry typed AI availability/error reasons into both new-bag and rescan review, including recoverable connection states and setup-launch feedback.
3. Restore bounded, correlated scan/LLM/OCR telemetry and record validated outcomes.
4. Adopt published Tracebox alpha.8 and refresh the stale alpha.3 integration document.
5. Coordinate context-aware Mindlayer SDK adoption, then qualify memory, reconnect/idle, repeated vision, and accessibility behavior on devices.

These proposals require implementation work beyond this audit. They do not authorize changes to local library APIs, model distribution, product settings, or unrelated application flows.
