# Test Data

The coffee-bag test dataset is the **committed synthetic corpus** under
[`synthetic-coffee-bag-corpus/`](synthetic-coffee-bag-corpus/). It fully
replaces the old local-only real-photo set — there is **no symlink to set up**
and no real label photos in the repo.

## What's here

| Path | Description |
|------|-------------|
| `synthetic-coffee-bag-corpus/corpus_metadata.json` | Ground truth for every bag (schema v1). |
| `synthetic-coffee-bag-corpus/prototypes/*.webp` | Front/back label images (lossy WebP, ~1024×1536). |
| `synthetic-coffee-bag-corpus/README.md` | Corpus details, tiers, and how the tests consume it. |

Image names follow `lang_roaster_coffee_{front|back}_qTier.webp` (e.g.
`en_north_axis_moonlit_orchard_front_q0.webp`). Quality tiers run `Q0`
(studio-perfect) through `Q4` (failure-mode).

## Running the tests

Pure-JVM validation (parser contract, scoring math, corpus structure) runs
with no device:

```powershell
.\gradlew.bat testDebugUnitTest
```

On-device quality tests push the corpus, then run the LLM quality report and
the Q0 best-case gate:

```powershell
.\gradlew.bat pushTestImages
# capture OCR fixtures once (only when the OCR pipeline changes)
.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.adsamcik.starlitcoffee.benchmark.OcrFixtureCaptureTest"
# quality report (numbers, not pass/fail)
.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.adsamcik.starlitcoffee.benchmark.LlmCorpusBenchmarkTest"
# Q0 best-case gate (must pass 100%)
.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.adsamcik.starlitcoffee.benchmark.BagScanBestCaseGateTest"
```

`pushTestImages` copies `corpus_metadata.json` + `prototypes/*.webp` to
`/data/local/tmp/coffee-bags/` on the connected device or emulator (cleaning
the directory first). Reports are written to the app's external files dir
(`/sdcard/Android/data/com.adsamcik.starlitcoffee/files/coffee-bags-fixtures/`)
as `*.json` + `*.txt` and echoed to logcat under the `StarlitBagBenchmark` tag.
