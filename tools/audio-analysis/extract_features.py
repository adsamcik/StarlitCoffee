"""
Feature extraction pipeline for Starlit Coffee brew recordings.

Extracts the same features as AudioAnalyzer.kt (RMS dB, peak dB, ZCR, dominant freq)
for offline comparison and analysis.

Usage:
    python extract_features.py path/to/recording.wav
    python extract_features.py path/to/recording.wav --output features.json
    python extract_features.py path/to/recording.wav --plot
"""

import argparse
import json
import sys

import numpy as np
import soundfile as sf

# Must match AudioAnalyzer.kt constants
SILENCE_DB = -96.0
FRAME_SIZE = 1024  # matches AudioCaptureSession buffer
EPSILON = 1e-10
MIN_FREQUENCY = 50
MAX_FREQUENCY = 8000
MIN_SAMPLES_FOR_FREQUENCY = 256
AUTOCORRELATION_THRESHOLD = 0.3


def compute_rms_db(frame: np.ndarray) -> float:
    """RMS in dBFS, matching AudioAnalyzer.computeRmsDb."""
    if len(frame) == 0:
        return SILENCE_DB
    rms = np.sqrt(np.mean(frame ** 2))
    if rms < EPSILON:
        return SILENCE_DB
    return max(20.0 * np.log10(rms), SILENCE_DB)


def compute_peak_db(frame: np.ndarray) -> float:
    """Peak amplitude in dBFS, matching AudioAnalyzer.computePeakDb."""
    if len(frame) == 0:
        return SILENCE_DB
    peak = np.max(np.abs(frame))
    if peak < EPSILON:
        return SILENCE_DB
    return max(20.0 * np.log10(peak), SILENCE_DB)


def compute_zcr(frame: np.ndarray) -> float:
    """Zero-crossing rate, matching AudioAnalyzer.computeZeroCrossingRate."""
    if len(frame) < 2:
        return 0.0
    signs = np.sign(frame)
    crossings = np.sum(np.diff(signs) != 0)
    return float(crossings) / (len(frame) - 1)


def estimate_dominant_frequency(frame: np.ndarray, sr: int) -> float:
    """Autocorrelation-based frequency estimation, matching AudioAnalyzer."""
    if len(frame) < MIN_SAMPLES_FOR_FREQUENCY:
        return 0.0

    min_lag = sr // MAX_FREQUENCY
    max_lag = min(sr // MIN_FREQUENCY, len(frame) // 2)
    if min_lag >= max_lag:
        return 0.0

    zero_lag = np.sum(frame.astype(np.float64) ** 2)
    if zero_lag < EPSILON:
        return 0.0

    # Compute normalized autocorrelation for candidate lags
    for lag in range(min_lag + 1, max_lag):
        corr = np.sum(frame[:len(frame) - lag].astype(np.float64)
                       * frame[lag:].astype(np.float64))
        acf_val = corr / zero_lag

        # Check for local maximum above threshold
        corr_prev = np.sum(frame[:len(frame) - (lag - 1)].astype(np.float64)
                           * frame[lag - 1:].astype(np.float64)) / zero_lag
        corr_next = np.sum(frame[:len(frame) - (lag + 1)].astype(np.float64)
                           * frame[lag + 1:].astype(np.float64)) / zero_lag

        if (acf_val > AUTOCORRELATION_THRESHOLD
                and acf_val >= corr_prev
                and acf_val >= corr_next):
            return float(sr) / lag

    return 0.0


def extract_features(wav_path: str, frame_size: int = FRAME_SIZE) -> list[dict]:
    """
    Extract per-frame features from a WAV file.

    Returns list of dicts with: frame, time_s, rms_db, peak_db, zcr, dominant_freq_hz
    """
    # Read as int16 to match Android's PCM 16-bit representation
    audio_int16, sr = sf.read(wav_path, dtype="int16")

    # Normalize to -1.0..1.0 (same as AudioAnalyzer's normalization by Short.MAX_VALUE)
    audio = audio_int16.astype(np.float64) / 32767.0

    # If stereo, take first channel
    if audio.ndim > 1:
        audio = audio[:, 0]

    num_frames = len(audio) // frame_size
    results = []

    for i in range(num_frames):
        frame = audio[i * frame_size:(i + 1) * frame_size]

        results.append({
            "frame": i,
            "time_s": round(i * frame_size / sr, 4),
            "rms_db": round(compute_rms_db(frame), 2),
            "peak_db": round(compute_peak_db(frame), 2),
            "zcr": round(compute_zcr(frame), 4),
            "dominant_freq_hz": round(estimate_dominant_frequency(frame, sr), 1),
        })

    return results


def main():
    parser = argparse.ArgumentParser(description="Extract audio features from WAV files")
    parser.add_argument("wav_path", help="Path to WAV file")
    parser.add_argument("--output", "-o", help="Output JSON path (default: stdout)")
    parser.add_argument("--plot", action="store_true", help="Plot features over time")
    args = parser.parse_args()

    features = extract_features(args.wav_path)

    if args.output:
        with open(args.output, "w") as f:
            json.dump(features, f, indent=2)
        print(f"Wrote {len(features)} frames to {args.output}")
    elif not args.plot:
        json.dump(features, sys.stdout, indent=2)

    if args.plot:
        _plot_features(features, args.wav_path)


def _plot_features(features: list[dict], title: str):
    import matplotlib.pyplot as plt

    times = [f["time_s"] for f in features]
    rms = [f["rms_db"] for f in features]
    zcr = [f["zcr"] for f in features]
    freq = [f["dominant_freq_hz"] for f in features]

    fig, axes = plt.subplots(3, 1, figsize=(14, 8), sharex=True)

    axes[0].plot(times, rms, linewidth=0.8)
    axes[0].axhline(y=-40, color="r", linestyle="--", alpha=0.5, label="silence threshold")
    axes[0].set_ylabel("RMS (dBFS)")
    axes[0].set_title(title.split("/")[-1].split("\\")[-1])
    axes[0].legend()

    axes[1].plot(times, zcr, linewidth=0.8, color="green")
    axes[1].set_ylabel("Zero-Crossing Rate")

    axes[2].scatter(times, freq, s=2, alpha=0.5, color="purple")
    axes[2].set_ylabel("Dominant Freq (Hz)")
    axes[2].set_xlabel("Time (s)")

    plt.tight_layout()
    plt.show()


if __name__ == "__main__":
    main()
