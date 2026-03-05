"""
Spectrogram visualization for brew recordings.

Plots waveform, mel spectrogram, and RMS envelope with optional
ground truth label overlay (Audacity label format).

Usage:
    python plot_spectrogram.py path/to/recording.wav
    python plot_spectrogram.py path/to/recording.wav --labels path/to/labels.txt
    python plot_spectrogram.py path/to/recording.wav --save output.png
"""

import argparse

import librosa
import librosa.display
import matplotlib.pyplot as plt
import numpy as np
import soundfile as sf

LABEL_COLORS = {
    "ambient_baseline": "gray",
    "pour_start": "green",
    "pour_stop": "red",
    "drip_start": "blue",
    "drip_steady": "cyan",
    "drip_slowing": "dodgerblue",
    "drawdown_complete": "orange",
}


def read_audacity_labels(labels_path: str) -> list[dict]:
    """Read Audacity label track (TSV: start\\tend\\tlabel)."""
    labels = []
    with open(labels_path) as f:
        for line in f:
            parts = line.strip().split("\t")
            if len(parts) >= 3:
                labels.append({
                    "start": float(parts[0]),
                    "end": float(parts[1]),
                    "label": parts[2],
                })
    return labels


def plot_brew_spectrogram(
    wav_path: str,
    labels_path: str | None = None,
    save_path: str | None = None,
):
    """Plot three-panel visualization: waveform, spectrogram, RMS."""
    audio, sr = sf.read(wav_path, dtype="float32")
    if audio.ndim > 1:
        audio = audio[:, 0]

    fig, axes = plt.subplots(3, 1, figsize=(14, 10), sharex=True)

    # Panel 1: Waveform
    times = np.arange(len(audio)) / sr
    axes[0].plot(times, audio, linewidth=0.2, color="steelblue")
    axes[0].set_ylabel("Amplitude")
    axes[0].set_title(wav_path.split("/")[-1].split("\\")[-1])

    # Panel 2: Mel spectrogram (0–8 kHz, matching AudioAnalyzer.MAX_FREQUENCY)
    S = librosa.feature.melspectrogram(y=audio, sr=sr, n_mels=128, fmax=8000)
    S_db = librosa.power_to_db(S, ref=np.max)
    librosa.display.specshow(
        S_db, sr=sr, x_axis="time", y_axis="mel",
        ax=axes[1], fmax=8000, cmap="magma",
    )
    axes[1].set_ylabel("Frequency (Hz)")

    # Panel 3: RMS envelope (1024-sample frames like AudioAnalyzer)
    rms = librosa.feature.rms(y=audio, frame_length=1024, hop_length=1024)[0]
    rms_db = 20 * np.log10(rms + 1e-10)
    rms_times = librosa.frames_to_time(np.arange(len(rms)), sr=sr, hop_length=1024)
    axes[2].plot(rms_times, rms_db, linewidth=0.8, color="darkorange")
    axes[2].axhline(y=-40, color="r", linestyle="--", alpha=0.5, label="silence threshold (-40 dB)")
    axes[2].set_ylabel("RMS (dBFS)")
    axes[2].set_xlabel("Time (s)")
    axes[2].set_ylim(-96, 0)
    axes[2].legend()

    # Overlay labels if provided
    if labels_path:
        labels = read_audacity_labels(labels_path)
        for lbl in labels:
            color = LABEL_COLORS.get(lbl["label"], "gray")
            for ax in axes:
                ax.axvline(x=lbl["start"], color=color, linestyle="--", alpha=0.7)
            # Annotate on top panel
            axes[0].annotate(
                lbl["label"], xy=(lbl["start"], 0.9),
                xycoords=("data", "axes fraction"),
                fontsize=7, rotation=45, color=color,
            )

    plt.tight_layout()
    if save_path:
        plt.savefig(save_path, dpi=150, bbox_inches="tight")
        print(f"Saved to {save_path}")
    else:
        plt.show()


def main():
    parser = argparse.ArgumentParser(description="Brew recording spectrogram viewer")
    parser.add_argument("wav_path", help="Path to WAV file")
    parser.add_argument("--labels", "-l", help="Path to Audacity label file")
    parser.add_argument("--save", "-s", help="Save plot to file instead of showing")
    args = parser.parse_args()

    plot_brew_spectrogram(args.wav_path, args.labels, args.save)


if __name__ == "__main__":
    main()
