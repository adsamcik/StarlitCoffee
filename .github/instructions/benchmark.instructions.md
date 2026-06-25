---
applyTo: "app/src/androidTest/**/*.kt"
description: "Instrumented bag-scan benchmark conventions for Starlit Coffee"
---

# Instrumented scan benchmark (androidTest)

The bag-scan LLM benchmark is a **two-step instrumented flow** on a real device/emulator:

1. `OcrFixtureCaptureTest` runs the full OCR pipeline once and writes per-bag OCR
   text to the app's external files dir (`coffee-bags-fixtures/`).
2. `LlmCorpusBenchmarkTest` reads those fixtures, runs the LLM extraction, and
   scores per-field accuracy against the corpus ground truth.

## Run it with the `scanBenchmark` task — not `connectedAndroidTest`

```powershell
.\gradlew.bat scanBenchmark                # capture OCR fixtures, then benchmark
.\gradlew.bat scanBenchmark -PskipCapture  # reuse fixtures (prompt-only iteration)
```

The report is pulled to `build\reports\scan-benchmark\llm-fixture-quality-report.{txt,json}`.

**Why not `connectedDebugAndroidTest`:** it UNINSTALLS the app after every run,
which wipes the OCR fixtures between the capture and benchmark steps (`run-as`
then reports "unknown package"). Running the two tests as two separate
`connectedAndroidTest` invocations therefore silently loses the fixtures. The
`scanBenchmark` task installs both APKs **once** and drives instrumentation
directly via `adb am instrument`, so the app and its fixtures survive across
both passes. If you must do it by hand, install once
(`installDebug installDebugAndroidTest`) and use `adb shell am instrument`, never
back-to-back `connectedAndroidTest` runs.

## Other notes

- The on-device Mindlayer AI service needs consent; the `scanBenchmark` task
  enables the debug auto-accept receiver automatically. Manual runs must send
  that broadcast first or the OCR/LLM passes will block on a consent prompt.
- The debug app id is `com.adsamcik.starlitcoffee.debug` (test id
  `…debug.test`); fixtures and the report live under
  `/sdcard/Android/data/com.adsamcik.starlitcoffee.debug/files/coffee-bags-fixtures/`.
- Re-capture OCR fixtures only when OCR/preprocessing changes; prompt- or
  extraction-only changes can reuse fixtures with `-PskipCapture`.
- The benchmark produces **numbers, not a verdict** — it asserts only run
  validity. On-device inference is slow (~30 min, ~1–2 LLM calls/bag) and noisy
  at low temperature; trust only large A/B deltas.
