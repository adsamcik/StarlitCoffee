# Audio Brew Detection — Testing & Validation Strategy

> **Status**: Planning  
> **Scope**: End-to-end validation for mic-based brew event detection  
> **Audience**: Small team — pragmatic minimum-viable approach that scales

---

## 1. Recording Protocol

### 1.1 Controlled Variables

Every recording session captures a **metadata sidecar** (JSON or filename-encoded):

| Variable | Why it matters | How to capture |
|----------|---------------|----------------|
| **Phone model** | Mic frequency response and noise floor vary wildly | Device name from `Build.MODEL` |
| **Phone placement** | Distance and angle to brewer change RMS by 10–20 dB | Categorical: `on_counter_30cm`, `in_hand`, `tripod_15cm` |
| **Dose (g)** | Affects pour volume/duration and drip intensity | User input (already in `BrewUiState`) |
| **Grind setting** | Finer grind → slower drawdown → different drip cadence | User input (grinder + setting) |
| **Filter type** | Paper vs metal 19K/40K changes flow acoustic profile | Enum from `FilterType` |
| **Water temp** | Minor effect; capture for completeness | User input |
| **Ambient noise level** | Kitchen fan, music, conversation → false positives | Pre-brew 5s silence sample, report RMS dB |
| **Brew method** | Pulsar valve mechanics create unique acoustic signature | Enum from `BrewMethod` |
| **Ambient environment** | Room acoustics affect reflections | Categorical: `quiet_kitchen`, `office`, `cafe`, `outdoors` |

### 1.2 Session Structure

```
Per recording session:
  1. 5s ambient-only baseline (phone in position, no brewing)
  2. Full brew with phase-segmented WAV files (already implemented via onPhaseChanged)
  3. Manual event log: tap a "mark" button at each visible transition
     — OR sync with video (§2)
```

### 1.3 Minimum Dataset for Phase 1 (MVP)

| Category | Sessions | Rationale |
|----------|----------|-----------|
| **Your phone, quiet kitchen** | 10 brews | Baseline — tune thresholds here |
| **Your phone, noisy kitchen** | 5 brews | Fan/music playing — validate robustness |
| **Second phone model** | 5 brews | Catch mic-dependent assumptions |
| **Edge cases** (§7) | 5 targeted | One per top-5 edge case |

**Total: ~25 sessions, ~100 phase WAVs.** Enough to set initial thresholds and build the regression test suite.

### 1.4 File Naming Convention

Already implemented as `brew_{timestamp}_phase_{index}_{phaseName}.wav`. Extend with metadata sidecar:

```
brew_1720000000000_meta.json
{
  "phone_model": "Pixel 7",
  "placement": "on_counter_30cm",
  "dose_g": 20.0,
  "grind_setting": "1zpresso-zp6-special/30",
  "filter_type": "PAPER",
  "method": "PULSAR",
  "ambient_rms_db": -52.3,
  "environment": "quiet_kitchen",
  "notes": "Fridge compressor kicked on at ~2:00"
}
```

### 1.5 Implementation: Metadata Export

Add to `BrewAudioManager` or a new `RecordingSession` class:

```kotlin
// On startRecording(), write sidecar JSON alongside WAV files
fun writeSessionMetadata(uiState: BrewUiState, ambientRmsDb: Float) {
    val meta = mapOf(
        "phone_model" to Build.MODEL,
        "timestamp" to brewTimestamp,
        "dose_g" to uiState.coffeeG,
        "method" to uiState.method.name,
        "filter_type" to uiState.filterType.name,
        // ... etc
    )
    File(outputDirectory, "brew_${brewTimestamp}_meta.json")
        .writeText(Json.encodeToString(meta))
}
```

---

## 2. Ground Truth Labeling

### 2.1 Strategy Comparison

| Approach | Accuracy | Effort | Recommended? |
|----------|----------|--------|-------------|
| **Timer tap events** (user taps "next phase") | ±1–3s | Free (already logged) | ✅ Coarse baseline |
| **Video sync** (phone camera on brewer) | ±0.2s | Medium — need to align timestamps | ✅ For gold-standard subset |
| **Manual annotation tool** (Audacity + label track) | ±0.1s | High per file but precise | ✅ For regression test suite |
| **Automated from audio features** (peak detection) | Variable | Low once built | ❌ Circular — can't validate detector with detector |

### 2.2 Recommended Two-Tier Approach

**Tier 1 — Automatic (all sessions):**
- Log every phase transition timestamp from the timer UI
- Log `AudioDetectionEvent` emissions with wall-clock timestamps
- These give you coarse labels for bulk analysis (±2s accuracy)

```kotlin
// In BrewAudioManager.processBuffer(), already emitting events.
// Add: timestamp to each event for alignment with WAV files.
data class TimestampedEvent(
    val event: AudioDetectionEvent,
    val wallClockMs: Long,
    val phaseIndex: Int,
    val elapsedBrewMs: Long // offset from brew start
)
```

**Tier 2 — Gold-standard (regression suite, 20–30 recordings):**
- Use **Audacity** (free, cross-platform) to manually place label markers:
  - `pour_start`, `pour_stop`, `drip_start`, `drip_stop`, `drawdown_complete`
- Export as Audacity label file (TSV: `start_time\tend_time\tlabel`)
- Store alongside WAV: `brew_{timestamp}_phase_{index}_labels.txt`

**Label format:**
```
0.000000	0.000000	ambient_baseline
5.120000	5.120000	pour_start
18.450000	18.450000	pour_stop
18.900000	18.900000	drip_start
45.200000	45.200000	drip_steady
62.800000	62.800000	drip_slowing
78.500000	78.500000	drawdown_complete
```

### 2.3 Video Sync (Optional Gold Standard)

For the highest accuracy labels:
1. Start phone video (separate device) pointed at brewer
2. Clap or tap counter at brew start (visible + audible sync point)
3. In post-processing, align video clap spike with WAV clap spike
4. Label transitions from video, transfer timestamps to WAV labels

**When to bother:** Only for the ~10 most important regression test recordings. Not needed for every session.

---

## 3. Synthetic Test Data

### 3.1 Philosophy

`AudioAnalyzer` is a pure Kotlin object with no Android dependencies — ideal for deterministic unit tests with synthetic signals. The goal is to test the **feature extraction** and **state machine logic** independently from real recordings.

### 3.2 Synthetic Signal Library (Kotlin, for unit tests)

Add to `app/src/test/java/.../audio/SyntheticSignals.kt`:

```kotlin
object SyntheticSignals {
    /**
     * Generate pure sine wave — models tonal components.
     */
    fun sineWave(
        frequencyHz: Double,
        durationMs: Int,
        amplitudeNormalized: Double = 0.5, // 0.0–1.0
        sampleRate: Int = 44100
    ): ShortArray {
        val numSamples = (sampleRate * durationMs / 1000.0).toInt()
        return ShortArray(numSamples) { i ->
            val t = i.toDouble() / sampleRate
            (sin(2.0 * PI * frequencyHz * t) * amplitudeNormalized * Short.MAX_VALUE)
                .toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    /**
     * White noise — models broadband signals like pouring water.
     * Pour sound is roughly white/pink noise in 200–4000 Hz range.
     */
    fun whiteNoise(
        durationMs: Int,
        amplitudeNormalized: Double = 0.3,
        sampleRate: Int = 44100
    ): ShortArray {
        val random = Random(42) // deterministic seed
        val numSamples = (sampleRate * durationMs / 1000.0).toInt()
        return ShortArray(numSamples) { _ ->
            ((random.nextDouble() * 2.0 - 1.0) * amplitudeNormalized * Short.MAX_VALUE)
                .toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    /**
     * Silence — models quiet periods between pours.
     */
    fun silence(durationMs: Int, sampleRate: Int = 44100): ShortArray {
        return ShortArray((sampleRate * durationMs / 1000.0).toInt())
    }

    /**
     * Impulse train — models periodic drip impacts.
     * Each drip is a short burst (5–15ms) with exponential decay.
     *
     * @param dripIntervalMs time between drip impacts (e.g., 300ms for fast drip)
     * @param dripDurationMs length of each impact transient
     */
    fun dripTrain(
        durationMs: Int,
        dripIntervalMs: Int = 500,
        dripDurationMs: Int = 10,
        dripAmplitude: Double = 0.4,
        sampleRate: Int = 44100
    ): ShortArray {
        val totalSamples = (sampleRate * durationMs / 1000.0).toInt()
        val buffer = ShortArray(totalSamples)
        val intervalSamples = (sampleRate * dripIntervalMs / 1000.0).toInt()
        val dripSamples = (sampleRate * dripDurationMs / 1000.0).toInt()

        var pos = 0
        while (pos < totalSamples) {
            for (j in 0 until dripSamples.coerceAtMost(totalSamples - pos)) {
                val decay = exp(-j.toDouble() / (dripSamples * 0.3))
                val sample = (decay * dripAmplitude * Short.MAX_VALUE *
                    sin(2.0 * PI * 2000.0 * j / sampleRate)) // 2kHz impact tone
                buffer[pos + j] = sample.toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            pos += intervalSamples
        }
        return buffer
    }

    /**
     * Concatenate multiple signals to build a complete brew scenario.
     */
    fun concatenate(vararg segments: ShortArray): ShortArray {
        val total = segments.sumOf { it.size }
        val result = ShortArray(total)
        var offset = 0
        for (segment in segments) {
            segment.copyInto(result, offset)
            offset += segment.size
        }
        return result
    }

    /**
     * Add background noise to a signal.
     */
    fun addNoise(signal: ShortArray, noiseAmplitude: Double = 0.05): ShortArray {
        val random = Random(123)
        return ShortArray(signal.size) { i ->
            val noise = (random.nextDouble() * 2.0 - 1.0) * noiseAmplitude * Short.MAX_VALUE
            (signal[i] + noise.toInt())
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }
}
```

### 3.3 Synthetic Brew Scenarios (Test Cases)

```kotlin
// --- Scenario: Clean Pulsar bloom pour ---
val cleanBloom = SyntheticSignals.concatenate(
    SyntheticSignals.silence(2000),           // Pre-pour quiet
    SyntheticSignals.whiteNoise(5000, 0.4),   // Pour (5s, moderate level)
    SyntheticSignals.silence(500),            // Brief pause
    SyntheticSignals.dripTrain(45000, 500),   // Bloom dripping (45s, slow drips)
    SyntheticSignals.silence(3000),           // Drawdown done
)

// --- Scenario: Noisy kitchen ---
val noisyBrew = SyntheticSignals.addNoise(
    SyntheticSignals.whiteNoise(5000, 0.4),  // Pour
    noiseAmplitude = 0.15                     // Kitchen background
)

// --- Scenario: False positive — conversation during brew ---
val conversation = SyntheticSignals.concatenate(
    SyntheticSignals.sineWave(200.0, 3000, 0.3),  // Low voice
    SyntheticSignals.sineWave(350.0, 2000, 0.2),  // Higher voice
)
```

### 3.4 What Synthetic Tests Can and Cannot Validate

| ✅ Validates | ❌ Does not validate |
|-------------|---------------------|
| Feature extraction math (RMS, ZCR, frequency) | Real acoustic signatures |
| Threshold crossing logic | Threshold values themselves |
| State machine transitions | Mic hardware behavior |
| Edge cases (clipping, silence, DC offset) | Environmental acoustics |
| Regression after code changes | User experience quality |

**Key insight**: Synthetic tests lock down the **logic**; real recordings validate the **thresholds**.

---

## 4. Metrics

### 4.1 Core Metrics per Event Type

For each detectable event (`pour_start`, `pour_stop`, `drip_start`, `drawdown_complete`):

| Metric | Definition | Target (MVP) |
|--------|-----------|--------------|
| **Precision** | TP / (TP + FP) — "when we detect it, are we right?" | ≥ 0.85 |
| **Recall** | TP / (TP + FN) — "do we catch every real event?" | ≥ 0.80 |
| **Detection latency** | Time from actual event to detection (seconds) | ≤ 2.0s for pour, ≤ 5.0s for drawdown |
| **False positive rate** | False detections per brew session | ≤ 1 per session |

### 4.2 Matching Criteria

A detected event **matches** a ground truth label if:
- Same event type
- Within a **tolerance window** of the ground truth timestamp

| Event | Tolerance window | Rationale |
|-------|-----------------|-----------|
| `pour_start` | ±1.5s | Users notice if detection lags their action |
| `pour_stop` | ±2.0s | Trailing drips make the boundary fuzzy |
| `drip_start` | ±3.0s | Gradual transition, not a sharp edge |
| `drawdown_complete` | ±5.0s | Slow tail — even humans disagree by ~3s |

### 4.3 Aggregate Metrics

| Metric | Definition | Target |
|--------|-----------|--------|
| **Session accuracy** | % of sessions where ALL events detected correctly | ≥ 70% (MVP) |
| **Phase timing error** | Mean absolute error of detected vs actual phase duration | ≤ 3s |
| **Nuisance rate** | Unexpected alerts/transitions per brew | ≤ 0.5 per brew |

### 4.4 UX-Level Metrics (for A/B testing, §6)

| Metric | Measures | Collection method |
|--------|---------|-------------------|
| Manual override rate | How often user taps "skip" or corrects detection | In-app event log |
| Timer completion rate | % of brews where timer runs to natural end | Analytics |
| Retry rate | User restarts brew due to detection confusion | Analytics |

### 4.5 Evaluation Script (Pseudocode)

```python
def evaluate_session(detected_events, ground_truth_labels, tolerances):
    results = {}
    for event_type in EVENT_TYPES:
        gt = [e for e in ground_truth_labels if e.type == event_type]
        det = [e for e in detected_events if e.type == event_type]
        tp, fp, fn = match_events(gt, det, tolerances[event_type])
        results[event_type] = {
            "precision": tp / (tp + fp) if (tp + fp) > 0 else 0,
            "recall": tp / (tp + fn) if (tp + fn) > 0 else 0,
            "latency_ms": mean([d.time - g.time for g, d in matched_pairs])
        }
    return results
```

---

## 5. Regression Testing

### 5.1 Architecture

```
test-recordings/                    ← Git LFS or shared drive (NOT in APK)
├── quiet_kitchen_pixel7_001/
│   ├── brew_1720000000_meta.json
│   ├── brew_1720000000_phase_0_bloom.wav
│   ├── brew_1720000000_phase_0_bloom_labels.txt   ← Audacity labels
│   ├── brew_1720000000_phase_1_main_pour.wav
│   └── ...
├── noisy_kitchen_samsung_002/
│   └── ...
└── manifest.json                   ← Index of all recordings + expected results
```

### 5.2 Regression Test Runner (Kotlin, JVM-only)

Since `AudioAnalyzer` is pure Kotlin, we can feed real WAV data through it in unit tests:

```kotlin
@Test
fun `regression - quiet kitchen pour detection`() {
    val wavBytes = javaClass.getResourceAsStream("/recordings/quiet_001_bloom.wav")!!
    val samples = WavReader.readPcm16Mono(wavBytes)
    val labels = LabelReader.read("/recordings/quiet_001_bloom_labels.txt")

    val detections = runDetector(samples, sampleRate = 44100)
    val results = evaluate(detections, labels, DEFAULT_TOLERANCES)

    assertTrue("Pour precision: ${results.pourPrecision}", results.pourPrecision >= 0.85)
    assertTrue("Pour recall: ${results.pourRecall}", results.pourRecall >= 0.80)
    assertTrue("Pour latency: ${results.pourLatencyMs}ms", results.pourLatencyMs <= 2000)
}
```

### 5.3 Minimum Regression Suite

Start with **10 gold-labeled recordings** that cover:

| # | Scenario | Purpose |
|---|----------|---------|
| 1 | Clean Pulsar bloom, quiet, paper filter | Happy path baseline |
| 2 | Clean Pulsar bloom, quiet, 19K metal filter | Different flow acoustics |
| 3 | Full Pulsar brew (bloom + 3 pulses), quiet | Multi-phase transitions |
| 4 | Kitchen fan running | Background noise rejection |
| 5 | Music playing | Tonal interference rejection |
| 6 | Phone in hand (unstable position) | Handling noise |
| 7 | Fast pour (high flow rate) | Amplitude saturation |
| 8 | Slow pour (low flow rate) | Near-silence detection |
| 9 | Second phone model | Cross-device generalization |
| 10 | Long drawdown (>60s) | Patience test — no false "done" |

### 5.4 CI Integration

**Phase 1 (now):** Run regression tests locally before changing thresholds.

```bash
# In app/build.gradle.kts, add test resource directory:
# sourceSets { test { resources.srcDirs("src/test/resources") } }

./gradlew testDebugUnitTest --tests "*RegressionTest*"
```

**Phase 2 (later):** GitHub Actions with LFS recordings.

```yaml
# .github/workflows/audio-regression.yml
name: Audio Regression
on:
  pull_request:
    paths: ['app/src/main/java/**/audio/**']
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with: { lfs: true }
      - uses: actions/setup-java@v4
        with: { java-version: 17, distribution: temurin }
      - run: ./gradlew testDebugUnitTest --tests "*AudioRegressionTest*"
```

### 5.5 Threshold Change Protocol

When modifying detection thresholds:
1. Run full regression suite **before** changes (baseline)
2. Make threshold change
3. Run full regression suite **after** changes
4. Compare metrics side-by-side
5. If any recording regresses by >5% on any metric, investigate before merging

---

## 6. A/B Testing in Production

### 6.1 Feature Flags

```kotlin
enum class AudioDetectionMode {
    DISABLED,       // Control: manual timer only
    PASSIVE,        // Record + analyze but don't act (shadow mode)
    ASSISTIVE,      // Suggest transitions, user confirms
    AUTOMATIC,      // Auto-advance phases (future)
}
```

### 6.2 Rollout Strategy

| Phase | Mode | Audience | Duration | What we learn |
|-------|------|----------|----------|---------------|
| **1. Shadow** | `PASSIVE` | All users with recording enabled | 2 weeks | Collect real-world recordings. Detection runs but doesn't affect UI. Log what it *would have* done. |
| **2. Opt-in assistive** | `ASSISTIVE` | Users who enable "audio assist" in settings | 4 weeks | Does detection match user expectations? Override rate. |
| **3. Default assistive** | `ASSISTIVE` | New default, opt-out available | Ongoing | Satisfaction, completion rates. |

### 6.3 Metrics to Track

| Metric | How to measure | Success threshold |
|--------|---------------|-------------------|
| **Manual override rate** | Count taps on "skip phase" / total phase transitions | < 20% |
| **Shadow accuracy** | Compare PASSIVE detections to user's manual taps | > 80% agreement |
| **Timer completion rate** | % of brews where timer runs to the end | Increase vs control |
| **Brew abandonment** | User stops timer mid-brew | No increase vs control |
| **Session duration** | Wall clock time for complete brew | No significant change |
| **User feedback** | Optional "Was audio detection helpful?" prompt | > 70% positive |

### 6.4 Shadow Mode Implementation

During `PASSIVE` mode, log detection events alongside user tap events:

```kotlin
data class ShadowComparisonLog(
    val brewTimestamp: Long,
    val phaseIndex: Int,
    val userTapTimeMs: Long,        // When user tapped "next phase"
    val detectedTimeMs: Long?,      // When audio would have triggered (null = missed)
    val deltaMs: Long?,             // detected - userTap (negative = early, positive = late)
    val falsePositiveCount: Int     // Detections with no matching user tap
)
```

This gives you a direct measurement of **"how close would we have been?"** before ever showing detection results to users.

---

## 7. Edge Case Catalog

### 7.1 False Positive Risks (Detects event that didn't happen)

| # | Scenario | Acoustic signature | Test approach |
|---|----------|-------------------|---------------|
| 1 | **Kitchen faucet running** | Broadband noise similar to pour | Record faucet at brewing distance; verify no `pour_start` |
| 2 | **Conversation / voices** | 100–300 Hz energy, speech cadence | Synthetic: mixed speech-frequency sine waves during silent phase |
| 3 | **Music playing** | Rhythmic, broadband, variable amplitude | Record brew with background music; check false trigger rate |
| 4 | **Fridge compressor kicks on** | Low-frequency hum starts abruptly | Record compressor cycle; verify no false `sound_resumed` |
| 5 | **Phone notification sound** | Brief tonal burst | Synthetic: 800Hz sine, 500ms duration mid-brew |
| 6 | **Kettle boiling nearby** | Rolling broadband noise, building intensity | Record kettle boil; similar spectral profile to pour |
| 7 | **Dog barking / baby crying** | Broadband transients | Synthetic: high-amplitude impulses at irregular intervals |
| 8 | **User tapping phone screen** | Sharp mechanical transient through chassis | Record taps on screen during silent phase |
| 9 | **Placing mug on counter** | Single loud thump | Record; verify doesn't trigger `pour_start` |
| 10 | **Stirring/swirling brewer** | Sloshing liquid — periodic, broadband | Record stir/swirl; verify doesn't trigger `pour_start` |

### 7.2 False Negative Risks (Misses event that happened)

| # | Scenario | Why it fails | Test approach |
|---|----------|-------------|---------------|
| 11 | **Very slow pour** (low flow rate) | RMS barely above silence threshold | Record slow pours at various rates; find minimum detectable flow |
| 12 | **Phone far from brewer** (>50cm) | Signal too quiet, masked by ambient | Record at 30cm, 50cm, 100cm; plot detection rate vs distance |
| 13 | **Metal filter dripping** (19K/40K) | Drip sound different from paper filter | Record both filter types; ensure drip detection works for both |
| 14 | **Very fine grind → fast drawdown** | Drawdown completes before detection stabilizes | Record fast drawdown (<30s); verify `drawdown_complete` fires |
| 15 | **Very coarse grind → slow drawdown** | Drawdown takes >90s; system times out or false-triggers "done" | Record slow drawdown; verify patience |
| 16 | **Cheap phone mic** (low sensitivity) | Mic has high noise floor, clips easily | Test on budget Android device |
| 17 | **Multiple brews back-to-back** | State machine doesn't fully reset between brews | Run 3 brews in sequence; verify clean transitions |

### 7.3 System / Environmental Edge Cases

| # | Scenario | Risk | Test approach |
|---|----------|------|---------------|
| 18 | **Mic permission revoked mid-brew** | Crash or hang | Revoke mic permission in Android settings during active brew |
| 19 | **Phone call interrupts brew** | AudioRecord released by system | Start brew, receive call, return to app; verify recovery |
| 20 | **App backgrounded during brew** | Audio capture may be suspended | Background app for 30s during brew; check for gaps in recording |

### 7.4 Priority for Testing

**Must test before shipping (P0):** #1, #3, #8, #11, #12, #18, #19, #20  
**Should test (P1):** #2, #5, #6, #10, #13, #14, #15, #17  
**Nice to have (P2):** #4, #7, #9, #16

---

## 8. Offline Analysis Toolkit

### 8.1 Tool Overview

Build a minimal Python toolkit for analyzing the WAV recordings we're collecting:

```
tools/audio-analysis/
├── requirements.txt
├── extract_features.py       # Feature extraction pipeline
├── plot_spectrogram.py       # Spectrogram visualization
├── annotate_viewer.py        # View WAV + labels side by side
├── evaluate_detection.py     # Score detection vs ground truth
└── batch_analyze.py          # Run feature extraction across all recordings
```

### 8.2 `requirements.txt`

```
numpy>=1.24
scipy>=1.11
matplotlib>=3.7
librosa>=0.10
soundfile>=0.12
```

No ML frameworks needed for Phase 1. `librosa` handles all DSP. `soundfile` reads WAVs.

### 8.3 Feature Extraction Pipeline (`extract_features.py`)

Must extract the **same features as `AudioAnalyzer.kt`** so we can compare Kotlin-side and Python-side results:

```python
import numpy as np
import librosa
import soundfile as sf
import json

def extract_features(wav_path, hop_length=1024, sr=44100):
    """Extract frame-level features matching AudioAnalyzer.kt output."""
    audio, sr = sf.read(wav_path, dtype='int16')
    audio_float = audio.astype(np.float32) / 32767.0

    # Frame the signal (same 1024-sample frames as Android)
    frames = librosa.util.frame(audio_float, frame_length=hop_length, hop_length=hop_length)

    results = []
    for i, frame in enumerate(frames.T):
        rms = np.sqrt(np.mean(frame ** 2))
        rms_db = 20 * np.log10(rms + 1e-10)
        peak_db = 20 * np.log10(np.max(np.abs(frame)) + 1e-10)
        zcr = np.sum(np.diff(np.sign(frame)) != 0) / (len(frame) - 1)

        results.append({
            "frame": i,
            "time_s": i * hop_length / sr,
            "rms_db": float(np.clip(rms_db, -96, 0)),
            "peak_db": float(np.clip(peak_db, -96, 0)),
            "zcr": float(zcr),
        })

    return results

def save_features(features, output_path):
    with open(output_path, 'w') as f:
        json.dump(features, f, indent=2)
```

### 8.4 Spectrogram Visualization (`plot_spectrogram.py`)

```python
import matplotlib.pyplot as plt
import librosa
import librosa.display
import soundfile as sf
import numpy as np

def plot_brew_spectrogram(wav_path, labels_path=None, output_path=None):
    """Plot spectrogram with optional ground truth labels overlaid."""
    audio, sr = sf.read(wav_path, dtype='float32')

    fig, axes = plt.subplots(3, 1, figsize=(14, 10), sharex=True)

    # 1. Waveform
    times = np.arange(len(audio)) / sr
    axes[0].plot(times, audio, linewidth=0.3)
    axes[0].set_ylabel("Amplitude")
    axes[0].set_title(wav_path.split("/")[-1])

    # 2. Spectrogram (0–8kHz, matching AudioAnalyzer.MAX_FREQUENCY)
    S = librosa.feature.melspectrogram(y=audio, sr=sr, n_mels=128, fmax=8000)
    S_db = librosa.power_to_db(S, ref=np.max)
    librosa.display.specshow(S_db, sr=sr, x_axis='time', y_axis='mel',
                             ax=axes[1], fmax=8000)
    axes[1].set_ylabel("Frequency (Hz)")

    # 3. RMS over time (matches AudioAnalyzer.computeRmsDb)
    rms = librosa.feature.rms(y=audio, frame_length=1024, hop_length=1024)[0]
    rms_db = 20 * np.log10(rms + 1e-10)
    rms_times = librosa.frames_to_time(np.arange(len(rms)), sr=sr, hop_length=1024)
    axes[2].plot(rms_times, rms_db)
    axes[2].axhline(y=-40, color='r', linestyle='--', label='silence threshold')
    axes[2].set_ylabel("RMS (dBFS)")
    axes[2].set_xlabel("Time (s)")
    axes[2].legend()

    # Overlay labels if provided
    if labels_path:
        _overlay_labels(axes, labels_path)

    plt.tight_layout()
    if output_path:
        plt.savefig(output_path, dpi=150)
    else:
        plt.show()

def _overlay_labels(axes, labels_path):
    """Read Audacity-format labels and draw vertical lines."""
    colors = {
        'pour_start': 'green', 'pour_stop': 'red',
        'drip_start': 'blue', 'drawdown_complete': 'orange'
    }
    with open(labels_path) as f:
        for line in f:
            parts = line.strip().split('\t')
            if len(parts) >= 3:
                t = float(parts[0])
                label = parts[2]
                color = colors.get(label, 'gray')
                for ax in axes:
                    ax.axvline(x=t, color=color, linestyle='--',
                               alpha=0.7, label=label)
```

### 8.5 Detection Evaluation (`evaluate_detection.py`)

```python
def evaluate(detections, ground_truth, tolerances):
    """
    Score detections against ground truth labels.

    detections: [{"type": "pour_start", "time_s": 5.2}, ...]
    ground_truth: [{"type": "pour_start", "time_s": 5.0}, ...]
    tolerances: {"pour_start": 1.5, "pour_stop": 2.0, ...}
    """
    results = {}
    for event_type, tol in tolerances.items():
        gt = [e for e in ground_truth if e["type"] == event_type]
        det = [e for e in detections if e["type"] == event_type]

        tp = 0
        matched_latencies = []
        used_det = set()

        for g in gt:
            best_match = None
            best_delta = float('inf')
            for j, d in enumerate(det):
                if j in used_det:
                    continue
                delta = abs(d["time_s"] - g["time_s"])
                if delta <= tol and delta < best_delta:
                    best_match = j
                    best_delta = delta
            if best_match is not None:
                tp += 1
                used_det.add(best_match)
                matched_latencies.append(det[best_match]["time_s"] - g["time_s"])

        fp = len(det) - tp
        fn = len(gt) - tp

        results[event_type] = {
            "precision": tp / (tp + fp) if (tp + fp) > 0 else 0.0,
            "recall": tp / (tp + fn) if (tp + fn) > 0 else 0.0,
            "mean_latency_s": np.mean(matched_latencies) if matched_latencies else None,
            "tp": tp, "fp": fp, "fn": fn
        }

    return results
```

### 8.6 Batch Analysis (`batch_analyze.py`)

```python
"""
Run feature extraction across all recordings and generate a summary report.

Usage: python batch_analyze.py /path/to/test-recordings/ --output report.html
"""
import os, glob, json

def batch_process(recordings_dir):
    sessions = glob.glob(os.path.join(recordings_dir, "**/brew_*_meta.json"), recursive=True)
    report = []

    for meta_path in sessions:
        meta = json.load(open(meta_path))
        session_dir = os.path.dirname(meta_path)
        wavs = sorted(glob.glob(os.path.join(session_dir, "*.wav")))

        session_report = {"meta": meta, "phases": []}
        for wav in wavs:
            features = extract_features(wav)
            labels_path = wav.replace(".wav", "_labels.txt")
            has_labels = os.path.exists(labels_path)
            session_report["phases"].append({
                "file": os.path.basename(wav),
                "duration_s": len(features) * 1024 / 44100,
                "mean_rms_db": np.mean([f["rms_db"] for f in features]),
                "has_labels": has_labels,
            })
        report.append(session_report)

    return report
```

---

## 9. Minimum Viable Validation — What to Do First

### Phase 1: Foundation (Week 1–2)

- [ ] Add `SyntheticSignals.kt` test utility
- [ ] Write 5 synthetic scenario tests for `AudioAnalyzer`
- [ ] Record 10 brews with metadata sidecars
- [ ] Label 5 recordings in Audacity (gold standard)
- [ ] Build `extract_features.py` and `plot_spectrogram.py`
- [ ] Visually inspect spectrograms — understand what each phase looks like

### Phase 2: Regression Baseline (Week 3–4)

- [ ] Build `evaluate_detection.py`
- [ ] Create WAV-reading regression tests (5 labeled recordings)
- [ ] Establish baseline metrics for current thresholds
- [ ] Add shadow mode logging to `BrewAudioManager`

### Phase 3: Iterate (Ongoing)

- [ ] Tune thresholds using offline analysis toolkit
- [ ] Run regression suite after every threshold change
- [ ] Expand recording dataset (target: 25 sessions)
- [ ] Add edge case recordings as they're encountered
- [ ] Ship assistive mode with manual override tracking

---

## Appendix: Key Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Label format | Audacity TSV labels | Free tool, widely understood, simple format |
| Regression test location | JVM unit tests reading WAV resources | Runs in CI, uses same `AudioAnalyzer` as production |
| Recording storage | Git LFS or shared Google Drive | WAVs are too large for git, too important to lose |
| Python vs Kotlin for offline analysis | Python (librosa) | Better visualization, faster iteration, standard in audio ML |
| Synthetic test determinism | Fixed random seeds | Reproducible across runs |
| Feature parity | Python extracts same features as Kotlin | Ensures offline analysis matches real-time behavior |
