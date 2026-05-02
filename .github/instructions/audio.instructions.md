---
applyTo: "app/src/main/java/**/audio/*.kt"
description: "Audio analysis conventions for Starlit Coffee"
---

<!-- context-init:managed -->
- Keep signal-processing and state-machine code pure Kotlin where possible; inject clocks/config for deterministic tests.
- Android microphone capture orchestration belongs in `BrewAudioManager` and capture/session classes, not in pure detector classes.
- Expose analysis state and events through flows (`StateFlow`/`SharedFlow`).
- Guard concurrent start/stop paths with explicit state/atomic checks.
- Preserve recording sidecars and metadata when changing WAV/flight-recorder behavior.
- Tune detector thresholds through `DetectorConfig`; avoid hardcoded assumptions that only work on one device or room.
- Add or update synthetic/regression tests for detector, FFT, preprocessing, and trajectory changes.
