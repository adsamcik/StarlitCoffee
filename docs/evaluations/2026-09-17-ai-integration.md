# AI integration evaluation — 2026-09-17

Evaluated Starlit Coffee `main` at `2c75a3f` with Mindlayer SDK
`1.0.0-alpha.7`. Scope: application wiring, OCR and LLM orchestration,
grounding, parsing, caching, cancellation, durable delivery, diagnostics,
and the evaluation harness. No production behavior was changed.

**Verdict:** the integration has a useful local-first foundation and substantial
defensive infrastructure, but reliability needs fixes and the current quality
gates do not establish production accuracy. Device performance is unmeasured in
this evaluation; the code contains several sources of avoidable work.

| Dimension | Assessment |
| --- | --- |
| Quality | Grounding prompts, field provenance, review, normalization, and abstention scoring exist. Parser weaknesses, incorrect OCR geometry, and benchmark divergence limit confidence. No live accuracy percentage is established here. |
| Reliability | Durable drafts, partial results, shared connection ownership, retries, and an overall deadline are strong foundations. Provider-local OCR timeouts can bypass recovery, and empty OCR results suppress fallback. |
| Performance | Work runs off the main thread and LLM calls are serialized. OCR amplification, unconditional translation, repeated session setup, CPU prewarming, and a process-wide vision limit need device-backed assessment. |

## Evidence and limits

- The working tree was clean when review began.
- Focused JVM run: **301 tests discovered, 299 passed, 2 skipped, zero failures
  or errors**. This comprises 292 passing existing tests, seven passing audit
  probes, and two skipped existing tests. Gradle completed successfully in
  11 minutes including compilation; that build duration is not inference latency.
  Per-suite results are archived in
  `2026-09-17-ai-integration-test-results.json`.
  The 33 suites skipped two symbolic-link isolation tests because symbolic links
  were unavailable to that JVM (`BagExtractionInputStoreTest` and
  `BagExtractionResultStoreTest`).
- Audit probes use actual application parser, fallback, deadline, cache
  orchestration, and scorer functions with synthetic inputs/fake providers.
  They do not run a model or Android OCR. A passing probe confirms the documented
  undesirable behavior; it does not certify the integration as correct.
- `adb devices -l` returned no connected devices. Local Android virtual-device
  definitions exist, but no emulator/model service was started. No live OCR,
  inference, physical-device latency, thermal, memory, battery, or process-death
  test was performed.
- Existing test XML files from August were not counted as current evidence.
- The 143 committed corpus sidecars are a useful dataset inventory, not an
  accuracy result. Device tests load only the automation-ready subset.
- The separate Mindlayer service implementation and native engine were not
  comprehensively audited. Service-crash explanations in source comments are
  historical rationale, not newly verified behavior of a deployed service.

## Findings, in priority order

### F1 — High: OCR timeouts bypass the bundled fallback

`MindlayerOcrService.recognize` applies a 60-second `withTimeout`, then rethrows
every `CancellationException` in its failure handler. Capability connection
timeouts can escape the same way. `FallbackOcrService` rethrows cancellation,
so neither timeout becomes a failed optional provider with bundled OCR recovery.
The scan deadline catches its own expiration only; a nested provider timeout
escapes it. The worker then rethrows cancellation before writing its normal
terminal result.

Reproduction: a 10 ms provider-local timeout inside a still-active 1,000 ms scan
deadline produces `TimeoutCancellationException`, calls fallback zero times,
and leaves the parent coroutine active. This isolates a provider timeout from
actual user cancellation. It does not claim a particular WorkManager/UI terminal
state, which requires Android testing.

A related quality failure is that a non-null `RecognizedText("", emptyList())`
counts as success and prevents usable bundled OCR from running. The production
Mindlayer result converter can construct this empty result.

Smallest fix: handle local timeouts at the provider boundary with an active-parent
check, retain propagation of genuine parent cancellation, and fall back when OCR
has no usable text. Preserve initial OCR if an optional region refinement fails.

Sources: `MindlayerOcrService.kt:113-130,137-150,163`,
`FallbackOcrService.kt:33-51`, `ScanDeadline.kt:14-19`,
`BagExtractionWorker.kt:124-126`. The distinction follows the official
[Kotlin cancellation and timeout contract](https://kotlinlang.org/docs/cancellation-and-timeouts.html).

### F2 — Medium: refined OCR boxes use the wrong coordinate system

`HierarchicalOcrService` crops and sometimes upscales a problem region, invokes
OCR on that crop, and appends its blocks directly to the full-image blocks.
There is no inverse scale/translation of bounding boxes or line corner points.
The `RecognizedText` contract requires coordinates relative to the image being
returned to the caller.

For a crop away from the image origin, a crop-local `(10, 10)` is therefore
interpreted as full-image `(10, 10)`. These mixed coordinates feed alignment,
label-region selection, thumbnail focus, and field evidence rectangles. This
can add background to the next OCR crop and misplace evidence shown to users.

Smallest fix: retain the padded crop origin and effective scale, map all returned
geometry back to the parent bitmap, then merge. Add an Android test with an
off-origin, upscaled crop. This finding is established by source inspection;
image rendering was not exercised here.

Sources: `data/network/ocr/HierarchicalOcrService.kt:102-125,129-138,149-187`,
`data/network/ocr/RecognizedText.kt:12-15`,
`scan/BagPhotoExtractor.kt:617-620,833-849,1080-1087`.

### F3 — Medium: structured output is not fully validated, and diagnostics overstate success

All inference passes use `CALLER_VALIDATES`. The response schema omits required
properties and leaves values untyped. The parser catches errors when parsing the
top-level JSON, but later `.jsonObject` / `.jsonPrimitive` conversions are outside
that boundary.

Two reproduced cases:

- A valid `name` plus an array-valued `origin` throws `IllegalArgumentException`
  instead of returning a typed failure or preserving the valid name. The provider
  maps the exception to a failed whole call; text extraction may repeat both
  translation and extraction.
- `{"error":"Unable to process this label"}` is accepted as `Success` with no
  fields. There is no distinction from a valid all-abstained extraction.

Separately, text/vision/combine/refine diagnostics record `SUCCESS` before
`parseResponse`. Syntactically invalid JSON can consequently be reported as a
successful pass even though its returned result is `Failed`. Nested shape errors
can create both success and error records for the same attempt.

Smallest fix: validate envelope and field shapes before accepting them, preserve
valid siblings where appropriate, require recognized status values for the
structured format, and record the parsed outcome. Continue supporting an explicit
legacy flat format if compatibility needs it. Distinguish valid abstention from
invalid output.

Sources: `data/network/llm/MindlayerLlmInferenceProvider.kt:278-305,472-473,555-556,628-629,1156-1178,1196-1269`.

### F4 — Medium: cache keys omit context that changes model answers

Combine/refine prompts include original OCR context, but their cache
serialization omits it. Combine also omits the known-value vocabulary. The
general extraction/vision cache keys omit known values despite including them
in prompts.

Reproduction: call `runCombineEnrichmentIfNeeded` twice on the same extractor
with identical conflicting candidates but different OCR context. A fake provider
that answers from that context is called once; the second call gets the old
answer. Changing only the vocabulary reproduces the same problem.

This matters when reusing an extractor/cache, such as the retained ViewModel
fallback. Normal durable workers currently construct a new cache for each work
execution, which limits exposure and also limits cache performance benefits.
This is not evidence of cross-user or persistent cache contamination.

Smallest fix: hash a canonical serialization of all effective prompt inputs,
including grounding text and vocabulary; include prompt/parser version and model
identity where a cache can outlive a model change. Use structured serialization
instead of delimiter-concatenated field strings.

Sources: `scan/BagPhotoExtractor.kt:1146-1165,1366-1374,1428-1434,1500-1508,1553-1561`,
`data/network/llm/LlmCacheKey.kt:29-58`,
`data/network/llm/MindlayerLlmInferenceProvider.kt:984-1042`,
`data/work/BagExtractionWorker.kt:339-350`, `viewmodel/BrewViewModel.kt:248-257`.

### F5 — Medium: the quality gates do not test production output or reject hallucinations

The Q0 gate calls `BagPipelineRunner`, which reconstructs OCR and invokes only
text extraction. It omits production rule-based field candidates, lookup
precedence, vision selection, combine/refine, quality attenuation, field
resolution, deadlines, and durable delivery.

The separately named `FullPipelineBenchmarkTest` adds vision/combine, but still
implements a different flow: independent full-field vision reads, fresh process
per bag, and map overlays instead of production candidate resolution. It omits
refine. These are useful component experiments, but cannot establish end-to-end
application quality or same-process repeated-scan reliability.

The pass/fail scorer explicitly excludes fields whose expected value is absent.
A Q0 score with a hallucinated `roastLevel = Dark` therefore passes if its visible
gate fields are correct. The report does count hallucinations; the blocking gate
does not. The regular CI quality workflow runs JVM/static/build checks, not live
model quality or latency gates.

Smallest fix: add a corpus harness around the production extractor and score its
final user-facing evidence/prefill. Require correct abstention on absent critical
fields, fail required runs when prerequisites are missing, and retain the existing
component experiments with narrower names. Include several scans in one process.

Sources: `androidTest/.../benchmark/BagPipelineRunner.kt:39-66`,
`androidTest/.../benchmark/BagScanBestCaseGateTest.kt:87-100`,
`androidTest/.../benchmark/FullPipelineBenchmarkTest.kt:142-207`,
`sharedTest/kotlin/.../corpus/BagFieldScorer.kt:128-138`,
`.github/workflows/code-quality.yml:45-58`.

## Performance and grounding risks to measure

These are source-derived work counts and design constraints, not measured timings.

| Behavior | Consequence | Smallest useful next experiment |
| --- | --- | --- |
| Three OCR passes per photo; each hierarchical pass permits five region re-reads | Up to `2 × 3 × (1 + 5) = 36` Mindlayer OCR calls for two photos before LLM work, subject to cancellation/deadline. The same image is re-read when alignment produces no change. | Compare adaptive second/third passes and targeted re-reads against the fixed pipeline using final field accuracy plus OCR time. |
| Every nonblank OCR request is translated before extraction | Two serial model generations even for English labels; any text retry repeats normalization. Optional vision, combine, and refine raise a successful scan to as many as five generations. | A/B one-pass multilingual extraction and an evidence-preserving normalization path, separately by language and capture tier. |
| Translation accepts any nonblank output and replaces source OCR for extraction | A dropped number or altered proper noun becomes the downstream extractor's apparent source evidence. Original OCR is not included alongside the translation in this extraction prompt. | Check identity/numeric token preservation; compare extracted values against original evidence. Keep uncertain corrections reviewable. |
| All passes request CPU prewarm and 8,192-token session capacity | Emulator stability policy is also applied on phones; memory/latency tradeoffs are not tiered here. 8,192 is a total context budget, not a measured output-token count. | Measure supported device/backend combinations before changing the existing crash mitigation. |
| Translation/extraction receive unbounded merged OCR text | Three OCR outputs plus optional region outputs can inflate context. An image byte cap does not bound the token budget. | Record actual input/output tokens and context rejections; deduplicate/chunk with preserved source spans. |
| One vision attempt per application process | Later scans lose visual rescue until process restart, including after a failed first attempt. Per-bag process restarts in benchmarks conceal this product limitation. | Repeated-scan tests against the supported Mindlayer/native versions; retain the guard until safe recovery is demonstrated. |
| Five-minute total scan budget versus six-minute generation and 390-second call limits | The total budget expires first during a genuinely stalled generation, so configured timeout retries cannot rescue that scan. Queue waits also consume total time. | Measure time to first usable partial result, queue time, and completion; introduce stage budgets that leave time for fallback. |

Relevant implementation: `BagPhotoExtractor.kt:134-149,617-636,1201-1229,1258-1301`,
`HierarchicalOcrService.kt:94,207`,
`MindlayerLlmInferenceProvider.kt:161-188,254-258,749-770,830-842,907-917`,
`ScanDeadline.kt:24`, `LlmDiagnostics.kt:23-42`.

Existing per-pass diagnostics contain elapsed milliseconds, prompt/output
character lengths, and requested capacity. They do not capture model/backend
identity, actual token usage, cold initialization time, or queue time. Correct
their outcome semantics before using them to compute a success rate.

## What is already sound

- Optional enrichment is explicitly preference-gated; bundled ML Kit OCR is wired
  for users without Mindlayer. QR exploration requires a separate user action.
- Each LLM request uses an ephemeral session, avoiding conversational carryover.
  Label and vocabulary strings are JSON-escaped and described as untrusted data.
- The resolver caps LLM-only confidence below HIGH and prioritizes authoritative
  lookup candidates. Vision has an evidence-presence check for selected fields.
  These are useful defenses, although model self-reported evidence is not
  independent verification.
- Calls run on IO dispatchers; production LLM orchestration uses a shared mutex.
- The overall deadline, incremental previews, generation/session identity checks,
  durable result replay, and startup recovery address important lifecycle risks.
- There is extensive JVM coverage plus synthetic multilingual corpus/scoring
  infrastructure. The remaining gap is validated production behavior on devices.

## Recommended order

1. Repair OCR timeout/empty-result fallback, parser validation/outcome diagnostics,
   and crop geometry. Add desired-behavior regression tests for each fix.
2. Correct cache input coverage and make a production-path corpus gate enforce
   abstention as well as visible-field extraction.
3. Run at least low/mid/high supported physical-device tiers with the actual
   supported model/service versions. Compare baseline OCR, text-only enrichment,
   and full production enrichment; measure per-field accuracy, hallucination and
   abstention, time to partial result, p50/p95 completion, failures, memory, and
   cold/warm behavior. Include repeated scans, cancellation, service death,
   missing models, and disconnected service.
4. Optimize only stages whose marginal quality benefit justifies their measured
   latency and resource cost. Do not remove CPU/vision crash guards based on JVM
   results alone.

## Reproducing the audit probes

The companion `2026-09-17-ai-integration-probes.kt` deliberately characterizes
current defects and is stored outside normal test sources. From the repository
root, temporarily copy it into the scan test package, run only its class, and
remove the temporary copy. Do not commit that copy as a regression contract.

```powershell
$auditTarget = 'app/src/test/java/com/adsamcik/starlitcoffee/scan/AiIntegrationAuditProbeTest.kt'
if (Test-Path -LiteralPath $auditTarget) { throw 'Refusing to overwrite existing test' }
Copy-Item -LiteralPath 'docs/evaluations/2026-09-17-ai-integration-probes.kt' -Destination $auditTarget
try {
    .\gradlew.bat :app:testDebugUnitTest --tests 'com.adsamcik.starlitcoffee.scan.AiIntegrationAuditProbeTest' --console=plain --max-workers=2
    if ($LASTEXITCODE -ne 0) { throw 'Audit probe run failed' }
} finally {
    Remove-Item -LiteralPath $auditTarget
}
```

The focused existing-test selection covers `data.network.llm.*`,
`data.network.ocr.*`, `scan.*`, `data.work.Bag*`,
`BagScanCaptureViewModelTest`, `BrewBagScanTest`, `MindlayerReconnectTest`,
`MindlayerStartupPolicyTest`, and `MindlayerInferencePrewarmGuardTest`.
