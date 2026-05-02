---
applyTo: "app/src/main/java/**/{scan,util}/**/*.kt"
description: "Live scan and coffee metadata extraction conventions"
---

<!-- context-init:managed -->
- Live scan is a user-reviewed pipeline: accumulate evidence, surface confidence/guidance, then let the user confirm or edit before save.
- Keep OCR normalization and parser logic deterministic and covered by unit tests.
- `FrameEvidenceAccumulator` owns camera-session coroutine work; regular frames may be conflated, but golden frames and enrichment payloads must not be dropped.
- `ConsensusEngine` should stay pure/testable: medoid clustering, Bayesian priors, and quality-weighted voting belong there.
- LLM escalation is optional and cached; failures must be logged and reflected as unavailable/fallback state.
- Preserve source/evidence metadata (`BagFieldEvidence`, source type, confidence) so UI can explain scan results.
- When adding a new scan field, update extractor, field support, known values, UI mapping, translations, and tests together.
