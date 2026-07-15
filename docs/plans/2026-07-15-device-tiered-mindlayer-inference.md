# Device-Tiered MindLayer Inference Plan

**Status:** Proposed  
**Date:** 2026-07-15  
**Scope:** Planning only; no implementation is authorized by this document.

## Objective

Support faster coffee-bag extraction and a wider range of Android devices by
allowing MindLayer to select smaller text-only models for constrained devices,
while retaining the existing vision-capable model for devices that can run the
full pipeline reliably.

The initial models to compare are:

- IBM Granite 4.0 350M as an experimental small text model.
- Gemma 3 270M IT as the small-model baseline.
- Gemma 3 1B IT as the likely balanced text-quality model.
- Gemma 4 E2B as the existing vision-capable quality tier.

Model placement is provisional. Benchmark results, not parameter count or
generic model scores, decide which model serves each profile.

## Current Constraints

### MindLayer

- MindLayer discovers multiple `.litertlm` files but currently selects one
  trusted chat model device-wide.
- The generative engine is initialized around that single selected model.
- Model capabilities are partly inferred from filenames, including Gemma 4
  vision support.
- Engine initialization, model integrity, backend fallback, memory budgeting,
  and consent are centralized in the MindLayer service.
- Model artifacts are currently distributed through Play AI Asset Packs in
  production and sideloaded only for development.

### Starlit Coffee

- Bag analysis is OCR-first. MindLayer's PaddleOCR pipeline produces text before
  the language model performs field extraction.
- The primary extraction path is text-only, with optional vision, combine, and
  refinement passes.
- The current Gemma-tuned extraction prompt needs an 8,192-token engine/session
  budget. It cannot be moved unchanged to a 2,048-token model.
- Users review and edit extracted fields before saving. Tiering must preserve
  that behavior.
- Existing corpus reports already measure field accuracy, recall, precision,
  hallucination, and abstention. These reports should remain the common quality
  contract across models.

## Proposed Direction

MindLayer should expose task-oriented inference profiles rather than requiring
client applications to select concrete model IDs.

```text
Starlit Coffee requests a profile and required capabilities
    -> MindLayer evaluates installed models and current device constraints
        -> MindLayer selects one compatible model for the session
            -> Starlit Coffee composes only the supported pipeline stages
```

The initial profile names are conceptual. Final API names should follow
MindLayer conventions when implementation begins.

| Profile | Intended device | Pipeline | Initial candidates |
|---|---|---|---|
| `FAST_TEXT` | Constrained or latency-sensitive | OCR, one compact text extraction, deterministic validation | Granite 350M, Gemma 270M |
| `QUALITY_TEXT` | Mainstream devices | OCR, text normalization/extraction, deterministic validation | Gemma 3 1B |
| `VISION` | Devices with sufficient memory and a healthy vision backend | OCR, text extraction, targeted vision, reconciliation/refinement | Gemma 4 E2B |

If no suitable model is available, the application should preserve the manual
bag-entry path and clearly report that AI extraction is unavailable.

## Architectural Principles

### Capability-Based Selection

Applications should request capabilities such as:

- text instruction following;
- structured JSON output;
- minimum context capacity;
- vision input;
- supported backend;
- latency or quality preference.

MindLayer should map those requirements to a model. Applications should not
hardcode filenames such as `granite-4.0-350m` or `gemma-4-E2B-it`.

### One Generative Model Per Session

The first implementation should keep only one generative model active for a
scan session. Loading a small model and then replacing it with a larger model
during every scan may lose the expected latency benefit through repeated cold
initialization.

Initial behavior should therefore be:

- select a profile before analysis starts;
- keep the selected model for the complete analysis session;
- avoid co-resident generative engines;
- unload and reinitialize only when the requested profile changes;
- initialize the engine with the session's real context ceiling.

Cross-model escalation can be reconsidered after model-switch latency and
memory fragmentation have been measured.

### Pipeline Composition Belongs to Starlit Coffee

MindLayer should report the selected model's capabilities and execute
inference. Starlit Coffee should decide which bag-analysis stages are useful:

- `FAST_TEXT` skips vision, combine, and refinement passes.
- `QUALITY_TEXT` normally skips vision but may retain separate normalization
  and extraction calls if benchmarks justify them.
- `VISION` may run vision only for unresolved or weak fields rather than on
  every bag.

Every profile must produce the same application-level field contract so the UI,
consensus logic, validation, and persistence do not fork by model.

## Required MindLayer Model Metadata

The model registry will eventually need explicit metadata instead of relying
only on filename heuristics:

| Metadata | Purpose |
|---|---|
| Stable model ID and version | Diagnostics and reproducible benchmarks |
| Trust origin and integrity hash | Preserve the existing model security boundary |
| Text and vision capabilities | Safe pipeline and engine configuration |
| Supported context ceilings | Prevent prompt/context mismatch |
| Chat template and structured-output support | Correct prompt formatting |
| Artifact size | Distribution and storage decisions |
| Measured peak memory by backend | Device-tier selection |
| Measured cold/warm latency | Profile selection |
| Supported languages | Avoid unsupported multilingual routing |
| Experimental/production status | Prevent unvalidated models becoming defaults |

Measured runtime properties should be tied to a model version, LiteRT-LM
version, backend, and representative device class.

## Model Distribution

Lower-end support is only useful if constrained devices are not forced to
download the largest model.

The implementation investigation should compare:

- separate optional AI Asset Packs per generative model;
- device-targeted delivery where Play supports the required targeting;
- user-selected optional quality/vision packs;
- shipping one default text model and making the vision model optional.

Release builds must retain integrity verification and must not load arbitrary
sideloaded models. Development sideloading should remain debug-only.

## Starlit Coffee Extraction Contract

Smaller models require a substantially more compact prompt and stronger
deterministic validation.

### Input

OCR lines should receive stable identifiers:

```text
L01: NOMAD COFFEE
L02: FINCA EL PARAISO
L03: Colombia - Cauca
L04: Castillo and Caturra
L05: Thermal Shock Washed
L06: 1900-2050 MASL
```

The prompt should:

- request only the fields needed by the current operation;
- distinguish roaster, producer, farm, region, and coffee name;
- prohibit inference of missing values;
- preserve proper nouns and unusual coffee terminology;
- require `null` for absent or uncertain values;
- require supporting OCR line IDs for every non-null value;
- avoid verbose examples and repeated rules where schema constraints suffice.

The compact prompt must be measured using each candidate model's tokenizer. A
model is not eligible when the system prompt, representative OCR input, and
maximum expected output cannot fit safely inside its supported context.

### Output

All models should return the same logical structure:

```json
{
  "roaster": {"value": "NOMAD COFFEE", "evidence": ["L01"]},
  "country": {"value": "Colombia", "evidence": ["L03"]},
  "producer": null,
  "varieties": [
    {"value": "Castillo", "evidence": ["L04"]},
    {"value": "Caturra", "evidence": ["L04"]}
  ],
  "processRaw": {
    "value": "Thermal Shock Washed",
    "evidence": ["L05"]
  }
}
```

The final schema may use shorter serialized property names if token savings are
material, while retaining descriptive Kotlin names at the application boundary.

### Deterministic Validation

After inference, Starlit Coffee should:

1. Reject evidence IDs that do not exist.
2. Reject values that cannot be grounded in their cited OCR lines, allowing
   only explicitly documented normalization such as whitespace or punctuation.
3. Parse dates, weights, altitude, and ranges with deterministic code.
4. Reject temperatures and brewing measurements used as coffee altitude or bag
   weight.
5. Reject bag weights supported only by brewing-recipe lines.
6. Preserve raw and normalized process values separately.
7. Surface conflicts and unsupported values for user review.
8. Never silently fill a missing field.

Evidence validation should run for every profile, including the full model.

## Granite 4.0 350M Experiment

Granite is an experimental candidate, not an approved production model.

The currently known community LiteRT-LM artifact is approximately 447 MiB, but
its quantization, Android parity, backend compatibility, latency, and peak
memory are not documented adequately enough for deployment decisions.

Before comparing extraction quality:

1. Pin the exact Granite source model, conversion code, LiteRT-LM version,
   artifact hash, context configuration, and quantization.
2. Confirm the artifact loads on Android CPU without custom untracked patches.
3. Compare representative source-model and LiteRT outputs for gross conversion
   regressions.
4. Measure cold initialization, warm latency, peak private memory, and failure
   behavior.
5. Test GPU only after the CPU path is stable; GPU support is not assumed.
6. Keep the artifact debug-only until all benchmark and integrity gates pass.

Granite should remain in the shortlist only if it demonstrates a meaningful
memory or latency advantage over Gemma 3 1B and competitive quality with the
small-model baseline.

## Benchmark Plan

### Corpus

Use the same captured OCR text for every candidate so model comparison is not
confounded by OCR variation.

The evaluation set should include:

- the existing synthetic corpus across Q0-Q4 capture tiers;
- English, Czech, German, Italian, French, and other representative bag
  languages;
- damaged OCR, merged words, missing diacritics, and front/back ordering noise;
- absent-field examples that test abstention;
- brewing instructions that contain misleading temperatures, doses, and
  weights;
- confusing roaster, producer, farm, region, and product-name combinations;
- unusual varieties and processing methods;
- eventually 100-200 reviewed real or realistically representative bag
  samples before a production default changes.

### Quality Metrics

Measure:

- exact and partial field accuracy;
- decision accuracy;
- recall and precision;
- invented-field frequency;
- evidence-line validity;
- roaster/name/producer/farm confusion;
- process extraction and normalization;
- correct abstention;
- structured-output validity;
- completed-analysis rate.

Quality reports must identify the model, artifact hash, runtime version,
backend, prompt version, validation version, and corpus revision.

### Runtime Metrics

Measure on representative constrained, mainstream, and high-end devices:

- model download and installed size;
- cold initialization time;
- warm time to first token;
- warm end-to-end extraction latency;
- peak private memory for the MindLayer process;
- total app plus service memory during analysis;
- low-memory kills and service restarts;
- battery and thermal behavior over repeated scans;
- CPU and supported accelerator behavior.

OCR and image buffers should be released before loading the generative model
where pipeline ordering permits it.

### Provisional Acceptance Gates

Exact thresholds should be finalized after the first reproducible benchmark,
but the decision rules should be:

- `FAST_TEXT` must provide a material memory and warm-latency improvement over
  `QUALITY_TEXT`.
- Its validated hallucination rate must not materially exceed the larger text
  model.
- Its decision accuracy may be lower, but must remain useful enough to reduce
  manual entry rather than merely move errors into the review form.
- `QUALITY_TEXT` must approach the existing Gemma 4 text-only quality while
  materially reducing model footprint or runtime cost.
- `VISION` must improve unresolved-field quality enough to justify its
  additional latency and memory.
- No profile may ship with reproducible native crashes, persistent service
  restart loops, or invalid structured output that bypasses validation.

## Device and Profile Selection

Profile selection should combine:

- total and currently available memory;
- MindLayer's existing device tier and memory budget;
- backend availability and recent backend failures;
- thermal and memory-pressure state;
- installed model packs;
- required context size;
- user preference where an override is safe.

Static RAM labels alone are insufficient. The same nominal device memory can
behave differently depending on OS build, concurrent applications, backend,
model quantization, and active image buffers.

The initial product behavior should choose one recommended profile
automatically. A later advanced setting may allow users to prefer speed,
quality, or vision, but unsafe combinations should remain unavailable.

## Implementation Phases for a Future Session

### Phase 0: Reproducible Model Benchmarks

- Acquire or build pinned Gemma 270M, Granite 350M, and Gemma 1B artifacts.
- Record integrity hashes and conversion metadata.
- Add model identity to benchmark reports.
- Run CPU smoke, parity, quality, latency, and memory measurements.
- Select at most one small model for the first product implementation.

**Gate:** A small model has a reproducible artifact and a defensible
quality/performance advantage.

### Phase 1: MindLayer Capability Metadata

- Replace filename-only capability inference with explicit model metadata.
- Represent text, vision, context, template, and experimental status.
- Keep the existing trust ordering and integrity checks.
- Expose selected-model capabilities through the SDK.

**Gate:** Existing Gemma 4 behavior remains unchanged when only its pack is
installed.

### Phase 2: MindLayer Profile Routing and Lifecycle

- Add profile/capability requests to session creation.
- Select a compatible installed model.
- Reinitialize safely when changing models.
- Integrate profile selection with memory budgeting and prewarm.
- Add diagnostics for requested profile, selected model, backend, and rejection
  reasons.

**Gate:** Model changes are deterministic, observable, and do not permit two
unbudgeted generative engines to remain resident.

### Phase 3: Model Pack Strategy

- Separate model assets where required.
- Ensure constrained devices can install the small profile without the vision
  model.
- Preserve production integrity and debug-only sideload rules.
- Verify upgrade, removal, missing-pack, and insufficient-storage behavior.

**Gate:** Installation footprint reflects the selected capability tier.

### Phase 4: Starlit Coffee Pipeline Profiles

- Request a MindLayer inference profile.
- Replace the large prompt with the compact evidence-grounded contract.
- Add deterministic evidence and field-type validation.
- Skip unsupported pipeline stages.
- Keep one application-level result type across profiles.
- Surface the active profile and meaningful degradation in diagnostics.

**Gate:** Manual review, save behavior, caching, cancellation, and failure
reporting remain consistent across profiles.

### Phase 5: Product Rollout

- Keep Granite and other new models behind an experimental/debug gate first.
- Run extended corpus and real-device testing.
- Introduce a small staged cohort only after quality and stability gates pass.
- Compare user correction rates and completed scans, not only offline metrics.
- Promote models independently from the profile API so implementations can be
  replaced without changing client behavior.

**Gate:** A profile demonstrates sustained device coverage or latency benefit
without unacceptable correction burden.

## Testing and Architecture Guards

Future implementation should add tests that enforce:

- client code requests capabilities/profiles rather than concrete model IDs;
- a text-only profile never attempts image inference;
- unsupported context requests fail before native inference;
- profile changes cannot retain an obsolete engine configuration;
- integrity and trust ordering apply to every model;
- experimental artifacts cannot become production defaults accidentally;
- all profiles return the same field contract;
- every accepted non-null field has valid evidence;
- low-memory and missing-model states surface explicit degradation;
- model and prompt identity are present in benchmark and diagnostic records.

## Risks

| Risk | Consequence | Mitigation |
|---|---|---|
| Small model quality is too low | Faster extraction creates excessive correction work | Corpus gates and user-correction telemetry |
| Community Granite conversion is incorrect | Invalid or subtly degraded outputs | Reproducible conversion and parity checks |
| Model switching is expensive | Tiering increases latency instead of reducing it | One model per session and measured lifecycle costs |
| Multiple packs increase storage complexity | Installation and upgrades become confusing | Select one small model initially; clear pack ownership |
| Prompt variants drift | Profiles produce inconsistent semantics | Shared schema, validation, and corpus tests |
| Context is too small | Truncated or invalid structured output | Tokenizer-specific prompt budgets and preflight checks |
| Device classification is wrong | Low-memory kills or unnecessary quality reduction | Runtime memory/backend signals and safe overrides |
| Vision runs too often | Full profile remains slow and memory-heavy | Trigger vision only for unresolved or weak fields |

## Open Decisions

- Whether Granite 350M or Gemma 270M is the first small model, if either.
- Whether Gemma 3 1B can fully replace Gemma 4 for text-only profiles.
- The minimum quality threshold that still provides a useful assisted-entry
  experience.
- Which Play delivery mechanism best supports optional model tiers.
- Whether model packs are selected automatically, explicitly by the user, or
  through a combination of both.
- Whether profile names should be exposed in the UI or remain an internal
  implementation detail.
- Whether later cross-model escalation is worthwhile after model-switch costs
  are measured.

## Completion Criteria

This initiative is ready for implementation planning when:

1. Candidate artifacts and runtime versions are pinned.
2. Granite has passed or failed a reproducible Android CPU smoke/parity test.
3. The compact extraction schema and evidence-validation rules are agreed.
4. Benchmark fixtures and device classes are defined.
5. MindLayer's profile/capability API shape is agreed.
6. Model distribution ownership and integrity requirements are agreed.
7. Initial quality, latency, and memory acceptance thresholds are recorded.

Until those conditions are met, the existing Gemma 4 pipeline remains the
production baseline.

## Reference Sources

- [Gemma 3 1B LiteRT artifacts and Android measurements](https://huggingface.co/litert-community/Gemma3-1B-IT)
- [Gemma 3 270M LiteRT artifacts](https://huggingface.co/litert-community/gemma-3-270m-it)
- [IBM Granite 4.0 350M model card](https://huggingface.co/ibm-granite/granite-4.0-350m)
- [Community Granite 4.0 350M LiteRT-LM conversion](https://huggingface.co/NeuML/granite-4.0-350m-litert-lm)
- [Qwen3 0.6B LiteRT artifacts and measurements](https://huggingface.co/litert-community/Qwen3-0.6B)
- [LiteRT-LM Android documentation](https://ai.google.dev/edge/litert-lm/android)
