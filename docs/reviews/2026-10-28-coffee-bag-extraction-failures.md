# Coffee bag extraction failures — 2026-10-28

The production Q0 extraction gate failed **all four bags**. Real Mindlayer OCR
and CPU generation worked, but those readiness checks do not establish accurate
or timely coffee extraction. Heavy local verification is stopped at the user's
request; it should not be repeated on this PC without explicit authorization.

## Workstation resource limit

The emulator's temporary 12 GB allocation has been removed. The existing
`Android_17_Google_Play` AVD now runs with **4096 MB RAM**, matching its unchanged
configuration. Guest readback was `MemTotal: 4006180 kB`. Apps, models and userdata
were retained; no AVD was created or cloned. No inference was run after reducing
the memory.

Earlier 4 GB and 8 GB attempts lost the native inference process under memory
pressure. A later cold run with a 12 GB guest also failed: the first bag timed
out and the next three reported `UNAVAILABLE`; Android recorded a 4096 MB
foreground-service memory limit. Guest RAM does not remove per-process limits.
Increasing the guest allocation did not establish a working extraction flow.

The AVD is shared with other chats. This RAM-reduction restart interrupted
Tracker's active native investigation. After booting at 4 GB, the AVD was handed
back to Tracker for its reserved acceptance window, and the StarlitCoffee UI
chat was notified. Coordinate shared access before any future restart, install
or UI action; no further emulator actions are needed for this report.

Keep ordinary local Android work within 4 GB. Use existing reports and focused,
resource-light checks. Further full-model quality/performance verification needs
suitable hardware and separate authorization for its resource cost.

## Final production gate

The last run used the real production extractor, including its five-minute
deadline, normal OCR fallback, optional enrichment, resolution and sanitization.
These are mismatches against the committed synthetic corpus's expected values.

| Fixture | Pipeline status | Expected → actual gate mismatches |
| --- | --- | --- |
| `scb-001-en-q0` | `TIMED_OUT` | Name: `Moonlit Orchard` → `North Axis`; roaster: `North Axis Roastery` → `Moonlit Orchard` |
| `scb-006-en-q0` | `TIMED_OUT` | Name: `Perihelion` → `ASTRAL COMMON`; roaster: `Astral Common Roasters` → `Perihelion`; roast: `Filter` → `LIGHT` |
| `scb-007-de-q0` | `SUCCEEDED` | Roaster: `Studio Bohne` → `STUDIO BOHNE KAFFEEROSTEREI` (partial); roast: `Espresso` → `LIGHT` |
| `scb-010-fr-q0` | `TIMED_OUT` | Roaster: `Maison Meridian` → `Carte Solaire` |

Three bags reached the deadline. One completed, but its values still failed the
gate. Coffee names and roasters were assigned to the wrong roles on multiple
fixtures; roast values also disagreed with the corpus. The run does not isolate
whether each error originates in OCR, model output or downstream resolution.

Useful partial data survived: origin, process and weight matched all four bags.
Across all 56 visible fields, 40 were exact, five partial, seven wrong and four
missing. The roughly 71% exact-field rate is descriptive; **it is not a gate
pass**. The combined invocation took 1304.728 seconds and ended with two passing
native tests and one failing JUnit gate test covering the four bags.

## Separate OCR and lifecycle failures

- A direct native OCR control using a wide `640×160` image did not return its
  `COFFEE` label. A square `640×640` control passed. The inspected backend
  stretches images to a square; its responsibility for the wide failure has
  not been established. The square pass proves engine health only.
- The final gate retained the same warmed native process throughout. JUnit
  finished the foreground activity after CPU smoke; it was restored about
  96 seconds into the first bag and remained foreground for the other three.
  This is not continuously foregrounded evidence for the first bag or proof of
  reliable cold/background operation.
- Native-process survival does not prove correct cancellation, acceptable
  latency or physical-device behavior.

## What passed, and what remains unverified

The eight focused Android geometry/bitmap-ownership and draft-conversion tests
passed in 22.87 seconds. Two direct native readiness tests passed in 74.779
seconds, then passed again before the failing quality gate. Generation used
CPU with an 8192-token engine context; mocks were disabled. Debug/test builds,
Detekt and the recorded native ABI/model invocation checks also passed. None
of these checks was rerun for this documentation or memory reduction.

Those native results came from the **12 GB API 37 emulator** with a locally
converted PaddleOCR bundle and matching Mindlayer debug APK. They do not
qualify the originally published OCR artifacts, Play/release model delivery,
physical ARM64 devices or the interactive consent flow. The Q0 extractor gate
also excludes the worker/recovery, review UI and final-save lifecycle.

The test's temporary debug auto-accept was restored to `false`, and its logcat
observer stopped. No production model manifest pins were changed. The local
APK used explicit pins for its derived OCR artifacts.

## Evidence and follow-up targets

Tested extraction code: StarlitCoffee `8e3fd2ec` (native readiness tests; earlier
extraction fixes in `e2dbdb2d`). Mindlayer source: `293e98403ccff87769a339079e20999070e72790`.

Local evidence is retained outside regenerable build output at
`C:\Users\adam-\GitHub\StarlitCoffee\.models\mindlayer-alpha10\validation\`:

- `native-health-and-quality-gate.txt`: final JUnit failure and exact values.
- `q0-best-case-gate-report.json` / `.txt`: field scores.
- `current-session-diagnostics.txt`: per-bag terminal status and native logs.
- `cold-production-terminal.txt`, `cold-q0-report.json`, `wide-label-failure.txt`:
  separate failed conditions, preserved despite the readiness passes.
- `4gb-memory-pressure.txt`, `8gb-memory-pressure.txt`: recorded native-process
  kills and memory-limit events from the earlier attempts.
- `focused-android-tests.txt`, `native-health-square-and-text.txt`:
  the passing checks, kept separate from quality acceptance.

The sibling `provisioning.json` records model/APK hashes and historical test
conditions. The sibling `SETUP.txt` records the current 4 GB workstation limit.

The next investigation should distinguish name/roaster role assignment, roast
normalization and the cost/cancellation of repeated enrichment passes. Start
with these saved outputs and source inspection. Do not use more RAM or repeat
the long live-model run on this PC to investigate them.
