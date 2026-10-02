# Starlit Coffee AI and telemetry integration audit

Reviewed on 2026-10-01 against Starlit Coffee `06e11f0` and the existing working changes. This audit preserves the original findings; subsequent fixes are recorded with each finding below. Existing staged and unstaged application work was preserved.

The published dependencies are compatible at build level, but the integration has gaps in OCR recovery, diagnostic privacy, AI failure reporting, and recognition UX. Passing unit tests does not cover these scenarios. The local development checkouts also differ from the latest published packages and require separate coordination.

## Versions and source boundaries

### Released Mindlayer integration — 2026-10-02

Starlit Coffee now consumes published SDK/shared `1.0.0-alpha.9`, from the
mainline commit `4e104599c38dc4aec17d32b1eb4a507eda81ef5e` tagged
[`v1.0.0-alpha.9`](https://github.com/adsamcik/Mindlayer/releases/tag/v1.0.0-alpha.9).
The standard [tag workflow](https://github.com/adsamcik/Mindlayer/actions/runs/36984032596)
published all four Maven modules and the service APK. This replaces the
branch-published alpha.8 dependency; no local SDK substitution is used.

The provider now admits `SUSPENDED_IDLE` and `RECOVERING` for text, vision,
combine, and refine. The SDK resumes an idle binding on `awaitConnected`; every
app pass retains its five-second connection limit. Terminal disconnection and
rejection stay blocked, and failed resume retains retry rather than consent.
OCR already invokes the SDK directly and needs no additional state gate.

CPU session budgets remain 8,192 tokens for label inference and 256 for the
diagnostic prompt. The new SDK's larger context range and context-aware warmup
do not change those workload limits. Warmup still connects without allocating
an engine; compact overflow recovery, bundled OCR fallback, and the process-wide
vision budget remain in effect. The SDK negotiates service capabilities; an
older service's unsupported readiness response remains a supported fallback.

The original snapshot and historical alpha.8 validation below remain as audit
history. Tracebox remains pinned to `0.1.0-alpha.7`.

Consumer validation passed with normal GitHub Packages resolution and
`--refresh-dependencies`: registry authentication, full debug unit tests,
Detekt, debug app and Android-test APK assembly, and release Kotlin compilation.
The suite ran 1,622 of 1,626 cases, with four skipped and zero failures/errors;
all 18 provider recovery cases passed, including idle admission, the five-second
resume budget, failed-resume retry, and caller cancellation. Dependency insight
confirmed SDK and shared at alpha.9. Resolved AAR SHA-256 values:

- SDK: `acd71101f66804c58737908f25d17133b7043eadf4cc6e9e27df77c971ca4fc9`
- Shared: `89a584e37f9406599bf2e4c8832e41574025e4cea27999abc4145a67ab22f179`

Logs, dependency reports, hashes, and JUnit evidence are retained under
`build/mindlayer-alpha9-integration`. These checks establish build and
app-policy compatibility. This integration has not been deployed to a device;
live Binder idle resume, native model inference, physical memory use, and
TalkBack speech were not revalidated here. The Android-test APK was built,
but its instrumented tests were not rerun for this dependency change.

The following table records the original audit snapshot. Subsequent dependency changes and their validation are recorded with the corresponding fixes below.

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

Resolution on 2026-10-01: generic connection/model unavailability now retains a temporary capability for enabled users. Only an explicit approval-needed SDK code, rejected-not-approved connection state, or the pinned SDK's documented unprefixed service auth-gate exception produces the authorization status. Denied consent, identity failures, bind permission failures, unknown codes, and model/backend/memory failures do not become approval invitations. Wire-prefixed security errors are decoded before classification; neither error-message text nor caller-supplied symbolic code names choose a consent action. Missing models retain setup, and missing OCR remains an input failure.

Text and optional vision/combine/refine stages preserve typed authorization/setup outcomes. The status survives durable result payloads and draft updates. Both ordinary and durable review use one presentation mapper, while successful retries clear stale blocking capability. Existing app opt-in remains contextual for undecided users; disabled recognition suppresses offers/recovery. Manual retry reconnects an unavailable shared client through the existing bounded reconnect path without requesting consent, and reuses a healthy client.

Pinned SDK boundary: alpha.7's registration path can collapse non-throttling security errors into `REJECTED_NOT_APPROVED` before the app sees their original numeric code. Available typed denied/identity/resource errors take precedence in the app's mapper; a state-only failure still follows the SDK's reported approval rejection. Recovering error detail already discarded by that SDK requires a separate library change. This fix removes the app's broad assumption that any installed-runtime unavailability means consent is missing.

Fix validation added 37 unit regressions across the actual provider, extraction stages, durable restoration/payloads, and presentation matrix. The full suite passed 1,507 tests (1,503 executed, four skipped), with zero failures/errors; Detekt, debug/app-test assembly, and release-source compilation passed. The log is `build/recognition-recovery-validation.log`. After correcting three complexity findings, a transient Windows resource-JAR lock interrupted one build; the final reduced-concurrency run passed without source or dependency workarounds.

Four full-form Compose tests passed on an API 36 x86_64 emulator: enabled temporary failure with values and without values both invoke retry and never consent; explicit authorization invokes approval; missing models invoke setup. The isolated `.privacycheck` app/test packages were removed afterward, preserving the existing development installation. The device log and APKs remain locally under `build/recognition-recovery-validation`. These tests verify provider classification, saved-state propagation, and visible button/callback routing; physical-device inference, live consent activities, and recovery against an actual failing service are not established.

The validation used the same temporary app-ID init script described for privacy testing, with `:app:testDebugUnitTest :app:detekt :app:assembleDebug :app:assembleDebugAndroidTest :app:compileReleaseKotlin --no-parallel --max-workers=2`, followed by the explicit `AddBagRecognitionRecoveryTest` instrumentation class. Normal app/test APK assembly then passed without the override and restored `.debug` output IDs (`build/recognition-recovery-restore-debug.log`). Mindlayer and Tracebox dependency pins remain unchanged.

### P2 Rescans conceal recognition that is still running

Draft review knows whether recognition is processing, but the existing-bag rescan route drops that state and its AI status before showing `RescanDeltaDialog`. The dialog can say “No changes found” or “Rescan results” and permit an update/new bag while AI is still working. It also lacks the consent, setup, and retry actions available for a new bag.

Evidence: `GuidedScanFlow.kt:329-333`, `376-388`, and `684-688`; `RescanDeltaDialog.kt:217-241` and `295-316`.

Smallest remedy: reuse the existing recognition presentation and recovery actions in rescan review. Make saving available partial results understandable without adding a parallel workflow. Verify running, partial, setup-required, failed, and completed rescans.

Resolution: rescan review now carries pipeline processing, typed AI status, recognition preference, and durable capability/run state into the same recognition card used for new-bag review. Retry, authorization, installation, model setup, manual preference, and retake actions share one controller. Retries reconnect first, preserve cancellation, and reject changed sessions/generations, active recognition, and saves in progress. Restored drafts retain running/partial state before their first stored result exists.

The dialog keeps recognition and changes in one scrollable review, preserves available photo/field updates while processing, and labels saving an incomplete scan as using current results. An empty or failed scan cannot claim that the existing label matches; the final match message requires readable completed fields. Saving disables recovery, transfer, and dismiss controls. Titles, partial-result actions, and field-count plurals are localized in all 23 resource locales.

Fix validation added 14 unit regressions for draft restoration, retry ordering/ownership/cancellation, and completion policy. The full suite passed 1,521 tests (1,517 executed, four skipped), with zero failures/errors; Detekt, debug/app-test assembly, and release-source compilation passed. The final log is `build/rescan-recognition-validation.log`. A complexity finding in the new guard was corrected before the final passing run. Normal app/test assembly then passed and restored `.debug`/`.debug.test` output IDs (`build/rescan-recognition-restore-debug.log`). Dependency pins remain unchanged.

Validation used the shared working tree, including unrelated brew, notification, calculation, and visual work. Those changes and their existing index state were preserved; this fix is committed separately. These builds are development validation, not a release qualification.

All 18 focused Compose cases passed on the API 36 x86_64 emulator: 14 rescan cases and the four existing new-bag recovery regressions. Coverage includes running and partial results, late completion, no false match after failure, retry/approval/setup/installation/retake routing, save-time disabling, scrolling, photo-only updates, and current-result actions at 1.6 times normal text size. The first two full runs passed, but their visual captures were obscured by unrelated launcher/System UI ANR dialogs. After closing those dialogs and verifying the home screen was clear, the large-text case passed again and produced the inspected, unobscured capture `build/rescan-recognition-validation/rescan-current-results-large-font-clean.png`. Logs and isolated APKs remain in that directory; isolated app/test packages were removed afterward. These tests establish visible presentation and callback behavior, not physical-device inference, actual external consent/setup recovery, TalkBack, or interrupted-save behavior against a live provider.

Separate ownership/navigation gaps observed during this fix: the existing durable rescan-to-new-bag transfer invokes a callback that pops navigation before its background/exit callback can pop again, and the transferred rescan may retain analysis for its former target. They predate this presentation fix and require a coherent transfer lifecycle change, including durable draft context and late-result ownership tests. The legacy in-memory preview also lacks session/generation identifiers; exact-session durable review rejects late generation/tombstone writes. These remain separate audit findings.

### P2 Recoverable connection states are treated as unavailable

The provider's availability and feature gates accept only `CONNECTED` and `CONNECTING`. During `RECOVERING`, which exists in published alpha.7, the extractor can return unavailable before reaching the SDK's bounded reconnecting call. A scan that starts during service replacement can therefore miss otherwise recoverable enrichment.

Evidence: `MindlayerLlmInferenceProvider.kt:206-235` and `BagPhotoExtractor.kt:1112`/`1147`.

Smallest remedy: distinguish permanently unavailable states from recoverable ones and allow a bounded `awaitConnected` attempt. The unreleased SDK's additional `SUSPENDED_IDLE` needs the same treatment for background work after idle disconnect. Current alpha.7 clients do not create that state, including when paired with the new service; this extension is an SDK-upgrade concern.

### P2 Warmup allocates more context than the app requests

The app requests 8,192 total session tokens but calls legacy `prewarm(CPU)` before inference. The published alpha.7 service warms at its device-tier ceiling under normal pressure, from 32,768 to 131,072 tokens. The app's session limit does not constrain that earlier allocation. The local unreleased SDK introduces context-aware prewarm for this purpose.

Evidence: app provider prewarm calls at `369`, `398`, `436`, `521`, and `594`; published Mindlayer tag `v1.0.0-alpha.7`, `ServiceBinder.prewarm`, `runtimeMaxTokensCeiling`, and `MemoryBudget.recommendedMaxTokens`. The app comment describing legacy prewarm as blocking until ready is also inaccurate: SDK submission is fire-and-forget.

Smallest remedy: coordinate SDK adoption and capability-gated `prewarmForContext(8192, CPU)`, preserving the existing CPU crash workaround and explicitly defining behavior with old services. Validate allocation and latency on physical hardware; no memory or performance reduction is established by this audit.

Implementation prepared on 2026-10-01: both the production provider and diagnostic prompt now select CPU directly in bounded ephemeral sessions (8,192 and 256 total tokens respectively). Provider warmup only connects, and no app inference path calls legacy model prewarm. A minimal SDK patch from published `v1.0.0-alpha.7` exposes nullable typed `SessionScope.backend`, forwards it through the canonical inference/open-session bridges, and preserves the existing default for callers and custom scopes. This uses the existing AIDL session configuration; no new service method or private backend override is used.

The published alpha.7 SDK cannot express this combination. The alpha.7-based release branch `codex/sdk-bounded-cpu-alpha8` at `20b1dbe` now publishes SDK/shared `1.0.0-alpha.8` through the existing [CI workflow](https://github.com/adsamcik/Mindlayer/actions/runs/36879488093). Its shared/SDK tests and publication passed; the SDK-only path skipped camera modules, GitHub Release creation, and service APK/AAB jobs. No service release or alpha.7 artifact overwrite was needed. The SDK implementation is also committed to the contextual Mindlayer checkout at `706908f`, with CI support at `20c63c2`, preserving unrelated dependency edits.

Initial consumer validation used an explicit temporary local-repository init script, never overwriting published alpha.7 coordinates or silently substituting the unrelated local contract 1.4 checkout. Normal GitHub Packages consumer resolution is validated separately below.

Source inspection of the alpha.7 service establishes that cold session creation passes `safeConfig.backend` and `safeConfig.maxTokens` to engine initialization. Already-loaded service engines retain their existing backend/context, which another client may have allocated. This change prevents Starlit's oversized speculative allocation; it does not establish an overall device memory ceiling or measured native RAM savings.

### P2 Context overflow has no compact recovery

Every request uses 8,192 tokens, while text prompts include uncapped OCR, existing values, and vocabulary. Memory pressure can reduce the service's effective context. Typed `INPUT_EXCEEDS_CONTEXT` becomes a generic nonretryable failure, without compacting optional prompt context or using the reported remaining budget.

Evidence: `MindlayerLlmInferenceProvider.kt:323-329`, `749-757`, and `921-923`.

Smallest remedy: budget optional context and use a compact extraction prompt or bounded chunking for typed overflow. Preserve source OCR and avoid automatic context growth under memory pressure. Test long front/back labels, large saved vocabulary, and reduced effective context.

Implementation prepared on 2026-10-01: optional reference vocabulary, existing context, and refinement suggestions use encoded JSON character budgets and retain whole values. Text extraction, combine, and refinement may make one recovery attempt only for numeric `INPUT_EXCEEDS_CONTEXT` (3006), using shorter system and user content. Recovery preserves original source OCR and core pass values, drops optional grounding, retains the response schema and 8,192-token request, and shares the original timeout/caller deadline. It stops if combined content cannot be reduced or the compact attempt also fails. The translation pre-pass continues to fall back to original OCR, and vision remains limited to one image inference per app process. Each completed attempt retains its actual prompt size and validated diagnostic outcome through Tracebox.

These character limits bound optional input size, not tokenizer usage. Full source text that still exceeds the effective service context remains a clean failure with existing partial values available; arbitrary label truncation and automatic context growth are avoided.

Prepared-fix validation passed the full app suite: 1,616 cases, 1,612 executed and four skipped, zero failures/errors. The 16 context-budget/recovery cases, 10 setup-launch coroutine cases, 21 recognition mapper cases and bounded CPU source guard passed. Detekt, both debug APK builds, and release-source compilation passed against the explicit local alpha.8 SDK repository. The SDK patch passed 649 tests with 11 existing skips, including Binder backend/context propagation and unchanged/default custom-scope compatibility. Logs and SDK artifacts remain under `build/ai-recovery-validation` and Mindlayer's isolated validation directory.

After successful CI publication, normal consumer validation passed without any init script or local SDK override: `checkGitHubPackagesAuth`, `:app:testDebugUnitTest`, `:app:detekt`, `:app:assembleDebug`, `:app:assembleDebugAndroidTest`, and `:app:compileReleaseKotlin`, using `--refresh-dependencies`. Registry access returned HTTP 200, and neither alpha.8 module exists in Maven Local. This build reused the previous unit results as up-to-date; a subsequent explicit `:app:testDebugUnitTest --rerun` passed all 1,612 executed cases, with four skipped and zero failures/errors. The normal debug application ID and APKs were restored after disposable emulator testing. Logs are `build/ai-recovery-validation/registry-consumer-validation.log` and `build/ai-recovery-validation/registry-unit-rerun.log`.

## Telemetry gaps

### Scan summaries have no producers

There are no production callers of `ScanSessionRingBuffer.save` and no `ScanSessionSummary` producers. `ScanAnalyticsTracker` has no boundary callers apart from field review; its boundary events also use DEBUG and would be dropped by the standard INFO policy. Current photo scans cannot populate the intended duration, completion, abandonment, retry, or resolved-field summary. Existing scan history can remain empty or show only old records.

Smallest remedy: emit one bounded start/terminal scan record from the durable photo workflow, using a shared scan/work correlation identifier, typed outcome, timing, stage/attempt counts, and field counts. Adapt the old frame-oriented summary to the current photo flow. Keep useful support events within the normal capture policy.

Resolution on 2026-10-01: the current photo workflow now emits typed INFO lifecycle records through Tracebox for queue/start, observed stages, partial field counts, completion, durable-result replay, retry, persisted review/background/save/discard transitions, cancellation, and enqueue failure. Worker and in-memory execution use numeric keys derived from strictly validated session/generation/work UUIDs; the same immutable coroutine context correlates AI passes. Replay has a separate event, technical cancellation is separate from explicit discard, and phase events are emitted only after a successful changed draft write. No additional diagnostic persistence or raw scan text was introduced. The unused legacy preference ring remains unpopulated.

Retry and OCR-checkpoint replacement also advance the durable draft's generation at the existing generation boundary, so their new work and results pass the same ownership checks as initial scans.

### Parsed failures are recorded as successful passes

TEXT, VISION, COMBINE, and REFINE record `SUCCESS` before parsing/validating the generated response. Malformed JSON returns `Failed`, but the retained diagnostic still says successful.

Evidence: `MindlayerLlmInferenceProvider.kt:306-307`, `474-475`, `557-558`, `630-631`, and `1204-1209`. Existing tests cover malformed JSON and diagnostic serialization independently, without verifying their combined outcome.

Smallest remedy: record the final validated result. Distinguish generation completion from parsing/validation failure with bounded statuses.

Resolution on 2026-10-01: TEXT, VISION, COMBINE, and REFINE record their final parsed outcome. Accepted usable fields produce `SUCCESS`, valid responses without usable fields produce `NO_RESULT`, and malformed responses produce `ERROR` with the closed `INVALID_RESPONSE` reason. Existing parser results and retry behavior are retained. Blank translation fallback records no result and the actual emitted character count. Caller cancellation and outer deadlines do not create false error/timeout outcomes.

### Release packages omit actionable AI failure codes

AI failure reasons are passed to Tracebox as ordinary strings and are correctly redacted. The raw reason lives in the separate LLM preferences, outside the release support package. Connection/readiness/setup failures can also return before any pass record is created. Support can receive “LLM enrichment failed: [redacted]” without the useful reason or a correlated pass.

Evidence: `BagPhotoExtractor.kt:1182`, `1192`, and `1233`; `MindlayerLlmInferenceProvider.kt:336-366`; release Diagnostics uses only the Tracebox package flow.

Smallest remedy: persist typed error codes, readiness/setup outcomes, pass, attempt, provider route, SDK/service version, context budget, and correlation metadata. Use bounded throwable structure when available. Do not mark arbitrary model/error strings public.

Resolution on 2026-10-01: the reviewed Tracebox package retains known numeric SDK codes, typed connection/approval/setup/model/timeout/inference/validation reasons, pass outcome, context-budget and character counts, and scan correlation. Actual readiness and connection failures receive bounded preflight records even when generation never starts. The six published model-readiness codes (including low memory, integrity mismatch, and unavailable backend) are retained as a closed enum; unknown or decorated reason text is excluded. Ordinary strings remain private. Provider/version, OCR-route attribution, and detailed performance instrumentation remain separate gaps below.

### OCR fallback and performance lack measurements

ML Kit failures can become null without retaining their outcome; the fallback helper also loses provider/fallback attribution. No app production call uses Tracebox's performance measurement APIs, although the embedded diagnostics UI exposes their controls. A package cannot reliably distinguish no text, primary failure, successful fallback, preprocessing time, permit waiting, generation time, and validation time.

Evidence: `MlKitOcrService.kt:35-46`, `FallbackOcrService.kt:38-51`, and `DiagnosticsScreen.kt:52-57`, plus production callsite searches.

Smallest remedy: add bounded provider/outcome events and timings at these existing boundaries. Keep them out of the primary UI. Test that diagnostics-disabled policy suppresses both measurements and retained support records.

The core `BrewSessionRuntime` also has no Tracebox callsites. Existing error logging in its callers can identify thrown failures, but cannot reconstruct a completed, missed, or recovered stage transition. If brew-timer/notification support needs that evidence, add a bounded transition/recovery record with session correlation and an outcome; avoid recording coffee names or introducing general usage analytics.

## Other usability gaps

“Finish setup” can silently do nothing when the service/setup action is null; launch exceptions are logged without user feedback (`BrewViewModel.kt:2298-2311`). Return an explicit launch outcome and provide concise inline feedback with an appropriate route to Mindlayer.

Recognition status has a polite live-region option, but `announceUpdate` defaults false and no production caller enables it (`AddBagSheet.kt:2013-2014`). Progress/completion changes can remain unannounced while a screen-reader user edits another field. Announce meaningful transitions and verify Compose semantics and actual TalkBack behavior.

Implementation prepared on 2026-10-01: setup returns explicit opened-setup/opened-app/unavailable/failed outcomes. A missing, expired, or failing setup action falls back to a visible installed Mindlayer launch intent. An owned query timeout permits fallback, while caller cancellation and outer deadlines never launch another app. New-bag and rescan review share inline opening/failure state, disable duplicate setup taps, cancel pending queries when the review identity or enabled state changes, and arm return recovery only after a successful launch for the same session/generation.

A dedicated stable polite semantics node announces coarse progress, readiness, no-result, setup, and recovery changes. It also exists when all fields are ready and no visual status card is needed. Changing counts, values, and controls stay outside the live region, preserving an edited field's focus. The four new messages are translated across the existing 23 resource sets. Semantics tests and fake launch outcomes do not establish actual TalkBack speech or external app setup completion.

All 31 focused Compose cases passed on the healthy API 36 x86_64 emulator in a disposable `.airecoverycheck` installation: 13 new setup/accessibility cases, 14 rescan regressions and four existing add-bag recovery cases. Coverage includes one stable live node through all-high-confidence completion, no count chatter, retained edited-field focus, inline failure with values/save available, duplicate-tap prevention, generation/disable cancellation before a pending setup destination opens, and arming recovery only for opened destinations. Both disposable packages were removed and the device claim released. The original emulator's package manager remained stalled; it was not reset or used for these tests. Native model allocation, physical ARM64 latency, actual TalkBack speech and a real external setup return remain unverified.

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

## Smarter label capture: follow-up investigation, 2026-10-01

The strongest near-term opportunity is to preserve reliable evidence and resolve only useful gaps. This improves the existing capture/review flow without a new mode or setting. It is mostly deterministic planning and merge behavior; AI contributes targeted reading, translation, and disambiguation.

Existing behavior already targets unsettled fields for vision, avoids replacing settled values in combine, and limits refinement to nearby vocabulary suggestions. The missing seams are concrete:

- `BagPhotoExtractor` still requests every bag field for the text pass, and changes to the photo list re-run OCR over the entire set.
- `BagDraftStore.mergeExtraction` protects edited/focused values, but a newer source revision can replace an older accepted, high-confidence field with a weaker result. Per-field draft storage preserves review state without independent confidence/source provenance.
- `BagVisionPlanner` treats consensus as authoritative without checking confidence and independent evidence. The resolver can also label repeated AI-only candidates as consensus while deliberately capping their confidence at medium. Agreement between AI outputs must not become a permanent reliable-field lock.
- Vision selects the globally sharpest photo/crop rather than the photo useful for its target. A clear front image can therefore displace a relevant back-label panel. Current capture guidance follows photo count, rather than the unresolved detail.

The smallest coherent first implementation is an immutable refinement plan for each scan generation, shared by text, vision, combine, and draft merge. Preserve user-confirmed values and values supported by reliable independent evidence; request only valuable missing, weak, or conflicting fields. A credible conflicting replacement should use the existing pending-suggestion review behavior rather than silently replacing a trusted field. Preserve evidence across durable drafts and process recreation, and reject late results from replaced generations. Distinguish actual user confirmation from automatically accepted high-confidence results. Removing or replacing a source photo must invalidate or reconsider its evidence, including after restart.

After that first slice, select the photo relevant to the unresolved field and reuse the existing scan-more action with one contextual hint when the evidence supports it. Optional details absent from a label should remain absent; they must not trigger mandatory extra photos. Reusing OCR for unchanged photos is a separate, bounded performance extension after ownership and evidence preservation are correct.

Validation should include an older reliable OCR value followed by weak AI output, an AI-only medium-confidence consensus, a genuine high-confidence conflict, a user edit during recognition, draft restart, front/back field relevance, and an absent optional field. Evaluate corrections per saved bag, repeat-photo rate, and time until useful editable fields against representative reviewed labels. Retain only typed/count telemetry under existing diagnostic controls; this proposal does not authorize raw-label collection or automatic training.

This is an investigated recommendation, not an implemented capture redesign. Its main cost is durable evidence migration and ownership testing; the initial slice adds no core-flow taps, terminology, or configuration.

The existing `BagPipelineRunner` corpus gate calls text extraction directly with every field and empty existing context. It does not exercise the current targeted vision/combine/refinement sequence, durable merge, or successive photo generations. The synthesized JSON corpus test is parser evidence, not photo/model quality proof. Add actual-pipeline sequence coverage before treating these gates as validation of smarter capture.

## Taste-aware next-brew advice: web research, 2026-10-01

A narrow on-device prototype is plausible. Use the model to interpret which sensations the person liked and what they want changed; keep comparable-history selection, allowable changes, quantities, and application of the result deterministic. Published model capabilities support trying this, but do not establish coffee-advice accuracy or usefulness.

Google's [Gemma 4 model card](https://ai.google.dev/gemma/docs/core/model_card_4) describes E2B/E4B as mobile/edge models with structured function calling, while identifying ambiguity and factual errors as limitations. The [LiteRT-LM benchmark](https://developers.google.com/edge/litert-lm/overview) lists a 2.58 GB E2B model and one S26 Ultra CPU result with 1,733 MB peak CPU memory. These are evidence of feasibility on that configuration, not a performance prediction for supported phones. Mindlayer's existing E2B provenance record names a 2,588,147,712-byte artifact with unverified quantization, tokenizer, and source revision. Measure the actual artifact on ARM64 hardware; do not substitute newer mobile memory estimates for this asset.

Structured output is necessary but insufficient. A [September 2026 preprint](https://arxiv.org/abs/2609.23742) examines structural versus semantic failures in small models; its results are provisional and do not evaluate our Gemma artifact or coffee task. Mindlayer's `CALLER_VALIDATES` path still permits invalid model JSON. The app must validate the returned decision and its grounding regardless of upstream constrained-decoding support.

The requested example, “pleasant acidity, but too thin,” contains both a positive and a desired change. Preserve the enjoyable acidity. “Thin” may concern strength or mouthfeel; it does not establish sourness or under-extraction. Controlled [2020 drip-coffee research](https://doi.org/10.1111/1750-3841.15326) distinguishes strength and extraction and measures multiple sensory effects, while the [2023 brewing-control study](https://doi.org/10.1111/1750-3841.16531) describes different consumer preference clusters. Those findings support a personalized, evidence-based experiment rather than a universal taste-to-grind rule; they should not be generalized to espresso or iced recipes.

There is already a data defect relevant to this proposal: `TasteIssue.TOO_WEAK` stores `TasteFeedback.TOO_SOUR`, and `TOO_STRONG` stores `TOO_BITTER`. Existing home coaching aggregates recent feedback without comparable-brew eligibility. Legacy taste enums alone cannot safely distinguish weakness from unwanted acidity, and that lost distinction cannot be reconstructed automatically. Preserve original notes and treat ambiguous older records as unknown.

`BrewLogEntity` stores bag, recipe, method, brewer, dose, water, grind, rating, notes, duration, and optional water/output measurements. Frozen snapshots additionally describe intended temperature, grinder, filter, technique, additions, and ratio semantics. They record settings and selected measurements, not measured temperature, dissolved-solids strength, or extraction yield. Require compatible, valid snapshots where those details matter; do not fabricate missing legacy data.

Additional grounding prerequisites: session completion can copy pre-brew presentation notes into `freeformNotes`, so an old note is not necessarily observed post-brew feedback. Quick rating currently passes null notes and an empty tag list to an update that replaces notes/tags, potentially erasing useful feedback. Legacy quick-log grind settings can also contain a recommendation range rather than an actual grinder setting. Preserve these distinctions and user notes before using history as evidence. Existing inferred grind outcomes and generic home hints are derived advice, not measured facts.

The smallest first prototype should serve repeated hot-filter brews of the same bag:

1. Use the existing notes/rating detail surface for one contextual next-brew action. Select at most two same-bag, same-method/brewer records with compatible grinder, filter, technique, and additions. Reject confounded or inadequately recorded comparisons, stale, deleted or reassigned bag/log associations, and incompatible recorded temperature, dose, or observations. Unknown settings do not establish matching conditions; a high rating alone does not explain what the person liked.
2. Generate a short list of small, single-control candidates from eligible history and host rules. Begin with a ratio-related candidate whose quantities are calculated by the app. State whether dose or water stays fixed and keep all other independently controlled settings fixed. Account for derived stage-volume and output changes in the reviewed draft; one independent control does not establish a single causal variable. An illustrative dose increase is appropriate only when eligible history supports that direction; the quoted note alone cannot justify a numeric prescription.
3. Give the model only the current note, selected records, and those candidates. Require a closed `actionId` or `ABSTAIN`, interpreted liked/unwanted facets, and supplied evidence IDs with exact note spans. The host checks IDs/spans, rejects unknown actions, invented evidence and multi-change outputs, and renders its own quantities and explanation.
4. Show one inspectable adjustment and its supporting saved brew, with a reviewable next-brew draft. Missing history, ambiguous notes, unavailable AI, malformed output or deadline expiry leave notes and manual adjustment available. Do not silently change a saved recipe or introduce another permanent AI setting.

The current brew-log detail surface does not expose a clone/brew-again callback. Creating a next-brew draft therefore requires a deliberate frozen-snapshot-to-editable-draft bridge with review, compatibility, and ownership checks; it is a proposed implementation, not an existing capability.

Use one short text inference with a bounded session and cancellation, through the existing Mindlayer connection. No new model/service API is required beyond the bounded CPU-session integration prepared above. Keep the selected notes local and outside raw diagnostic samples. Existing app preference copy authorizes enhanced label recognition, so the contextual next-brew action must explicitly request local advice and check service authorization/readiness there. An enabled label-recognition preference must not opt the user into background tasting-note analysis. The current SDK JSON output path is sufficient for an evaluation prototype when host validation is mandatory; function calling is an available capability, not a requirement to add an agent workflow.

Before shipping, compare against the existing rules using a held-out, reviewed fixture set. Cover English and Czech positives/negation, thinness versus body versus sourness, conflicting preferences, absent/legacy/confounded history, unsupported methods, malformed JSON, prompt-like note text, cancellation and service failures. Require zero invented-history and multi-change violations in that fixture set, a predeclared intent-quality target, useful coverage above the rules baseline, and measured cold/warm latency plus peak memory on the lowest intended ARM64 device. Schema validity and exact text spans do not prove the interpretation is right; assess that separately. Actual brewing and user feedback are needed to establish that the advice improves results.

This has promising value for repeat brewers who save meaningful notes, with frequency and benefit currently unmeasured. The principal costs are trustworthy history eligibility, taste-data migration, semantic evaluation, and device latency. If useful coverage or correction of intent does not beat simpler rules, keep those rules and omit the AI surface. No taste-advice feature or taste-data migration was implemented in this investigation.
