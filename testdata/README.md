# Test Data

The coffee-bag test dataset is the committed synthetic corpus under
[`synthetic-coffee-bag-corpus/`](synthetic-coffee-bag-corpus/). It fully
replaces the old local-only real-photo set.

## What's Here

| Path | Description |
|------|-------------|
| `synthetic-coffee-bag-corpus/*.metadata.json` | Per-fixture ground truth sidecars (`schema_version = 2`). |
| `synthetic-coffee-bag-corpus/*.{webp,png,jpg,jpeg}` | Front/back bag images referenced by the sidecars. |
| `synthetic-coffee-bag-corpus/README.md` | Corpus details, tiers, file naming, and test usage. |

Committed fixture names follow:

- `<fixture>.front.<ext>`
- `<fixture>.back.<ext>` when a reverse side exists
- `<fixture>.metadata.json`

Example:

- `scb-014-en-q1_espresso_no_4.front.png`
- `scb-014-en-q1_espresso_no_4.back.png`
- `scb-014-en-q1_espresso_no_4.metadata.json`

## Running The Tests

Pure-JVM validation (parser contract, scoring math, corpus structure):

```powershell
.\gradlew.bat testDebugUnitTest
```

On-device quality tests push the corpus, then run the OCR fixture capture, LLM
quality report, and the Q0 best-case gate:

```powershell
.\gradlew.bat pushTestImages
.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.adsamcik.starlitcoffee.benchmark.OcrFixtureCaptureTest"
.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.adsamcik.starlitcoffee.benchmark.LlmCorpusBenchmarkTest"
.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.adsamcik.starlitcoffee.benchmark.BagScanBestCaseGateTest"
```

`pushTestImages` copies all root-level `*.metadata.json` sidecars plus the
sibling bag images to `/data/local/tmp/coffee-bags/` on the connected device
or emulator. Reports are written to the app's external files dir
(`/sdcard/Android/data/com.adsamcik.starlitcoffee/files/coffee-bags-fixtures/`)
as `*.json` + `*.txt` and echoed to logcat under the `StarlitBagBenchmark`
tag.
