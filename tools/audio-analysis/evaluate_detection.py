"""
Evaluate detection accuracy against ground truth labels.

Computes precision, recall, and latency per event type using
configurable tolerance windows.

Usage:
    python evaluate_detection.py --detections detected.json --labels labels.txt
    python evaluate_detection.py --detections-dir session_dir/ --labels-dir session_dir/

Detection JSON format (array of objects):
    [{"type": "pour_start", "time_s": 5.2}, {"type": "pour_stop", "time_s": 18.1}, ...]

Labels: Audacity format (TSV: start_s  end_s  label)
"""

import argparse
import json
import os

import numpy as np

DEFAULT_TOLERANCES = {
    "pour_start": 1.5,
    "pour_stop": 2.0,
    "drip_start": 3.0,
    "drip_steady": 3.0,
    "drip_slowing": 5.0,
    "drawdown_complete": 5.0,
}


def read_labels(path: str) -> list[dict]:
    """Read Audacity label file."""
    labels = []
    with open(path) as f:
        for line in f:
            parts = line.strip().split("\t")
            if len(parts) >= 3:
                labels.append({"type": parts[2], "time_s": float(parts[0])})
    return labels


def read_detections(path: str) -> list[dict]:
    """Read detection results JSON."""
    with open(path) as f:
        return json.load(f)


def evaluate(
    detections: list[dict],
    ground_truth: list[dict],
    tolerances: dict[str, float] | None = None,
) -> dict:
    """
    Match detections to ground truth within tolerance windows.

    Returns per-event-type metrics:
        precision, recall, f1, mean_latency_s, tp, fp, fn
    """
    if tolerances is None:
        tolerances = DEFAULT_TOLERANCES

    all_event_types = set(
        [e["type"] for e in ground_truth]
        + [e["type"] for e in detections]
    )

    results = {}
    for event_type in sorted(all_event_types):
        tol = tolerances.get(event_type, 3.0)
        gt = [e for e in ground_truth if e["type"] == event_type]
        det = [e for e in detections if e["type"] == event_type]

        tp = 0
        latencies = []
        used_det = set()

        for g in gt:
            best_idx = None
            best_delta = float("inf")
            for j, d in enumerate(det):
                if j in used_det:
                    continue
                delta = abs(d["time_s"] - g["time_s"])
                if delta <= tol and delta < best_delta:
                    best_idx = j
                    best_delta = delta
            if best_idx is not None:
                tp += 1
                used_det.add(best_idx)
                latencies.append(det[best_idx]["time_s"] - g["time_s"])

        fp = len(det) - tp
        fn = len(gt) - tp
        precision = tp / (tp + fp) if (tp + fp) > 0 else 0.0
        recall = tp / (tp + fn) if (tp + fn) > 0 else 0.0
        f1 = (2 * precision * recall / (precision + recall)
               if (precision + recall) > 0 else 0.0)

        results[event_type] = {
            "precision": round(precision, 3),
            "recall": round(recall, 3),
            "f1": round(f1, 3),
            "mean_latency_s": round(float(np.mean(latencies)), 3) if latencies else None,
            "tp": tp,
            "fp": fp,
            "fn": fn,
            "tolerance_s": tol,
        }

    return results


def print_report(results: dict):
    """Print human-readable evaluation report."""
    print(f"\n{'Event Type':<25} {'Prec':>6} {'Rec':>6} {'F1':>6} {'Lat(s)':>7} {'TP':>4} {'FP':>4} {'FN':>4}")
    print("-" * 75)
    for event_type, m in results.items():
        lat = f"{m['mean_latency_s']:+.2f}" if m["mean_latency_s"] is not None else "  n/a"
        print(f"{event_type:<25} {m['precision']:>6.2f} {m['recall']:>6.2f} {m['f1']:>6.2f} {lat:>7} {m['tp']:>4} {m['fp']:>4} {m['fn']:>4}")

    # Overall summary
    all_tp = sum(m["tp"] for m in results.values())
    all_fp = sum(m["fp"] for m in results.values())
    all_fn = sum(m["fn"] for m in results.values())
    overall_p = all_tp / (all_tp + all_fp) if (all_tp + all_fp) > 0 else 0
    overall_r = all_tp / (all_tp + all_fn) if (all_tp + all_fn) > 0 else 0
    overall_f1 = 2 * overall_p * overall_r / (overall_p + overall_r) if (overall_p + overall_r) > 0 else 0
    print("-" * 75)
    print(f"{'OVERALL':<25} {overall_p:>6.2f} {overall_r:>6.2f} {overall_f1:>6.2f} {'':>7} {all_tp:>4} {all_fp:>4} {all_fn:>4}")


def main():
    parser = argparse.ArgumentParser(description="Evaluate brew event detection accuracy")
    parser.add_argument("--detections", "-d", required=True, help="Detection results JSON file")
    parser.add_argument("--labels", "-l", required=True, help="Ground truth labels (Audacity format)")
    parser.add_argument("--tolerances", "-t", help="Custom tolerances JSON file")
    parser.add_argument("--json", action="store_true", help="Output as JSON instead of table")
    args = parser.parse_args()

    detections = read_detections(args.detections)
    ground_truth = read_labels(args.labels)

    tolerances = DEFAULT_TOLERANCES
    if args.tolerances:
        with open(args.tolerances) as f:
            tolerances = json.load(f)

    results = evaluate(detections, ground_truth, tolerances)

    if args.json:
        print(json.dumps(results, indent=2))
    else:
        print_report(results)


if __name__ == "__main__":
    main()
