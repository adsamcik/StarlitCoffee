"""
Eval harness: run gemma4:e2b with the actual Mindlayer prompt against eval/*.jpg
and score against eval/ground_truth.json.

Metrics (per-field, across images):
- precision: of values emitted as 'found'+'uncertain', how many match GT
- recall: of GT 'found' values, how many the model emitted (any status)
- abstention: when GT is 'not_visible', does the model correctly say 'not_visible'?
- hallucination: model emits 'found' when GT is 'not_visible'

Configurations tested:
  A) baseline: current production system prompt, image-only
  B) baseline + OCR (synthetic OCR = GT values concat'd)  — upper bound with perfect OCR
  C) format="json" mode
  D) 14-field extended schema (adds region/farm/expiryDate/isDecaf as currently missing)
"""

import base64
import json
import sys
import time
import unicodedata
import urllib.request
from pathlib import Path

BASE = Path(__file__).resolve().parent.parent
EVAL = BASE / "eval"
GT_PATH = EVAL / "ground_truth.json"

# -------------------------------------------------------------------
# PROMPTS (mirror MindlayerLlmInferenceProvider)
# -------------------------------------------------------------------

SYSTEM_10 = """You are a coffee bag label analyzer. Extract structured information from coffee bag label images.

For each field, report your confidence:
- "found": You can clearly see or read this on the label
- "uncertain": You think you see something but it is unclear or partially occluded
- "not_visible": This information is not visible on the label

Response format (JSON only, no markdown):
{
  "fields": {
    "name": {"value": "Ethiopia Yirgacheffe", "status": "found"},
    "roaster": {"value": "Counter Culture", "status": "found"},
    "origin": {"value": "Ethiopia", "status": "found"},
    "variety": {"value": null, "status": "not_visible"},
    "process": {"value": "Washed", "status": "uncertain"},
    "roastLevel": {"value": null, "status": "not_visible"},
    "tastingNotes": {"value": "blueberry, jasmine, citrus", "status": "found"},
    "altitude": {"value": "1900-2100 masl", "status": "found"},
    "weight": {"value": "340g", "status": "found"},
    "roastDate": {"value": null, "status": "not_visible"}
  }
}

Rules:
- Use "not_visible" when you cannot see the information. Never guess.
- Use "uncertain" when text is blurry, partially occluded, or ambiguous.
- Use "found" only when you can clearly read or determine the value.
- Respond with ONLY a JSON object. No markdown fences or explanation."""

SYSTEM_14 = """You are a coffee bag label analyzer. Extract structured information from coffee bag label images.

For each field, report your confidence:
- "found": You can clearly see or read this on the label
- "uncertain": You think you see something but it is unclear or partially occluded
- "not_visible": This information is not visible on the label

Response format (JSON only, no markdown):
{
  "fields": {
    "name":         {"value": "Ethiopia Yirgacheffe",  "status": "found"},
    "roaster":      {"value": "Counter Culture",       "status": "found"},
    "origin":       {"value": "Ethiopia",              "status": "found"},
    "region":       {"value": "Yirgacheffe",           "status": "found"},
    "farm":         {"value": null,                    "status": "not_visible"},
    "variety":      {"value": null,                    "status": "not_visible"},
    "process":      {"value": "Washed",                "status": "uncertain"},
    "roastLevel":   {"value": null,                    "status": "not_visible"},
    "tastingNotes": {"value": "blueberry, jasmine",    "status": "found"},
    "altitude":     {"value": "1900-2100 masl",        "status": "found"},
    "weight":       {"value": "340g",                  "status": "found"},
    "roastDate":    {"value": "2026-03-01",            "status": "found"},
    "expiryDate":   {"value": "2026-09-01",            "status": "found"},
    "isDecaf":      {"value": false,                   "status": "found"}
  }
}

Field notes:
- roastDate/expiryDate: normalize to YYYY-MM-DD if possible; otherwise emit the string as printed.
- isDecaf: boolean. Only set true when the label explicitly says decaf/bezkofeinová/decaffeinated; false when clearly regular; not_visible otherwise.
- Labels may be in English or Czech — translate tasting notes to English when confident; keep proper nouns verbatim.

Rules:
- Use "not_visible" when you cannot see the information. Never guess.
- Use "uncertain" when text is blurry, partially occluded, or ambiguous.
- Use "found" only when you can clearly read or determine the value.
- Respond with ONLY a JSON object. No markdown fences or explanation."""

# Mapping from model JSON key -> app-internal field name (matches MindlayerLlmInferenceProvider.fieldMapping)
FIELD_MAP_10 = {
    "name": "name", "roaster": "roaster", "origin": "origin",
    "variety": "variety", "process": "processType", "roastLevel": "roastLevel",
    "tastingNotes": "tastingNotes", "altitude": "altitude",
    "weight": "weight", "roastDate": "roastDate",
}
FIELD_MAP_14 = dict(FIELD_MAP_10, region="region", farm="farm",
                    expiryDate="expiryDate", isDecaf="isDecaf")

ALL_APP_FIELDS = [
    "name", "roaster", "origin", "region", "farm", "variety",
    "processType", "altitude", "tastingNotes", "roastLevel",
    "roastDate", "expiryDate", "weight", "isDecaf",
]


# -------------------------------------------------------------------
# OLLAMA
# -------------------------------------------------------------------

def call(image_path, prompt, system, model="gemma4:e2b",
         json_mode=False, temperature=0.1, num_predict=2048):
    body = {
        "model": model, "system": system, "prompt": prompt, "stream": False,
        "options": {"temperature": temperature, "num_predict": num_predict},
        "images": [base64.b64encode(Path(image_path).read_bytes()).decode()],
    }
    if json_mode:
        body["format"] = "json"
    req = urllib.request.Request(
        "http://localhost:11434/api/generate",
        data=json.dumps(body).encode(),
        headers={"Content-Type": "application/json"},
    )
    t0 = time.time()
    with urllib.request.urlopen(req, timeout=900) as r:
        data = json.loads(r.read())
    return data.get("response", ""), time.time() - t0, data


def parse_json(resp):
    s = resp.strip()
    for f in ("```json", "```"):
        if s.startswith(f):
            s = s[len(f):]
    if s.endswith("```"):
        s = s[:-3]
    try:
        return json.loads(s.strip()), None
    except Exception as e:
        return None, str(e)


def extract(parsed, field_map):
    """Return {app_field_name: (value, status)} from parsed model JSON."""
    if not parsed:
        return {}
    obj = parsed.get("fields", parsed) if isinstance(parsed, dict) else {}
    out = {}
    for json_key, app_name in field_map.items():
        v = obj.get(json_key)
        if isinstance(v, dict) and "value" in v:
            val, status = v.get("value"), v.get("status", "found")
        elif v is None:
            val, status = None, "not_visible"
        else:
            val, status = v, "found"
        out[app_name] = (val, status)
    # ensure all app fields present
    for f in ALL_APP_FIELDS:
        out.setdefault(f, (None, "not_visible"))
    return out


# -------------------------------------------------------------------
# MATCHING
# -------------------------------------------------------------------

def norm(s):
    if s is None:
        return ""
    if isinstance(s, bool):
        return "true" if s else "false"
    s = str(s).lower().strip()
    s = unicodedata.normalize("NFD", s)
    s = "".join(c for c in s if unicodedata.category(c) != "Mn")
    # unify punctuation/whitespace
    for ch in ",;-_/\\()[]{}":
        s = s.replace(ch, " ")
    return " ".join(s.split())


def is_match(gt_value, gt_aliases, predicted):
    if predicted is None:
        return False
    pn = norm(predicted)
    if not pn:
        return False
    candidates = [gt_value] + (gt_aliases or [])
    for c in candidates:
        if c is None:
            continue
        cn = norm(c)
        if not cn:
            continue
        if cn == pn or cn in pn or pn in cn:
            return True
    return False


def score_image(gt_fields, predicted):
    """Score one image. Returns per-field outcome dict."""
    outcomes = {}
    for fname in ALL_APP_FIELDS:
        gt = gt_fields.get(fname, {"value": None, "status": "not_visible"})
        gt_val = gt.get("value")
        gt_status = gt.get("status", "not_visible")
        gt_aliases = gt.get("aliases", [])
        pred_val, pred_status = predicted.get(fname, (None, "not_visible"))

        # Categorise
        if gt_status == "not_visible":
            if pred_status == "not_visible" or pred_val is None:
                outcomes[fname] = "TN"       # correct abstention
            else:
                outcomes[fname] = "FP"       # hallucination
        else:
            if pred_val is None or pred_status == "not_visible":
                outcomes[fname] = "FN"       # missed
            elif is_match(gt_val, gt_aliases, pred_val):
                outcomes[fname] = "TP"       # correct
            else:
                outcomes[fname] = "WRONG"    # emitted but wrong value
    return outcomes


def aggregate(all_outcomes):
    """Aggregate outcomes across images into per-field and overall metrics."""
    fields = {}
    for fname in ALL_APP_FIELDS:
        counts = {"TP": 0, "FP": 0, "FN": 0, "TN": 0, "WRONG": 0}
        for img_out in all_outcomes:
            counts[img_out[fname]] += 1
        tp, fp, fn, tn, wrong = counts["TP"], counts["FP"], counts["FN"], counts["TN"], counts["WRONG"]
        emitted = tp + fp + wrong
        gt_positive = tp + fn + wrong
        gt_negative = fp + tn
        precision = tp / emitted if emitted else None
        recall = tp / gt_positive if gt_positive else None
        abstain_acc = tn / gt_negative if gt_negative else None
        fields[fname] = {
            **counts,
            "precision": precision, "recall": recall, "abstention_accuracy": abstain_acc,
        }
    # Overall
    total = {"TP": 0, "FP": 0, "FN": 0, "TN": 0, "WRONG": 0}
    for f in fields.values():
        for k in total:
            total[k] += f[k]
    return fields, total


def fmt(x):
    return "—" if x is None else f"{x*100:4.0f}%"


# -------------------------------------------------------------------
# RUNS
# -------------------------------------------------------------------

def build_synth_ocr(gt_fields):
    """Simulated perfect OCR: concat all visible label text from GT. Upper bound."""
    lines = []
    for fname, gt in gt_fields.items():
        if gt.get("status") in ("found", "uncertain") and gt.get("value") is not None:
            v = gt["value"]
            if isinstance(v, bool):
                continue
            lines.append(str(v))
            for a in gt.get("aliases", []) or []:
                lines.append(str(a))
    return "\n".join(dict.fromkeys(lines))  # dedupe, keep order


def build_prompt_baseline():
    return "Extract coffee bag information from this label image.\n\nRespond with JSON only."


def build_prompt_with_ocr(ocr_text):
    return (
        "Extract coffee bag information from this label image.\n\n"
        f'Raw OCR text detected on the label:\n"""\n{ocr_text}\n"""\n'
        "Use this text to verify and correct what you see in the image. The OCR may have errors.\n\n"
        "Respond with JSON only."
    )


def run_config(label, gt_bags, system, field_map, *,
               use_ocr=False, json_mode=False):
    print(f"\n{'#' * 70}")
    print(f"# {label}")
    print(f"{'#' * 70}")
    outcomes = []
    details = []
    for bag in gt_bags:
        img_path = EVAL / bag["image"]
        if not img_path.exists():
            print(f"  SKIP {bag['image']} — not found")
            continue
        gt_fields = bag["fields"]
        if use_ocr:
            ocr = build_synth_ocr(gt_fields)
            prompt = build_prompt_with_ocr(ocr)
        else:
            prompt = build_prompt_baseline()
        resp, secs, meta = call(img_path, prompt, system, json_mode=json_mode)
        parsed, err = parse_json(resp)
        predicted = extract(parsed, field_map)
        out = score_image(gt_fields, predicted)
        outcomes.append(out)
        details.append({
            "image": bag["image"], "secs": secs,
            "prompt_toks": meta.get("prompt_eval_count"),
            "output_toks": meta.get("eval_count"),
            "json_valid": parsed is not None, "parse_err": err,
            "predicted": {k: v for k, v in predicted.items()},
            "outcomes": out,
        })
        tp = sum(1 for v in out.values() if v == "TP")
        fn = sum(1 for v in out.values() if v == "FN")
        fp = sum(1 for v in out.values() if v == "FP")
        tn = sum(1 for v in out.values() if v == "TN")
        wrong = sum(1 for v in out.values() if v == "WRONG")
        print(f"  {bag['image']} : {secs:5.1f}s tok_out={meta.get('eval_count')} "
              f"json={parsed is not None}  TP={tp} FN={fn} FP={fp} WRONG={wrong} TN={tn}")
        # Show hallucinations and wrongs inline
        for fname, res in out.items():
            if res == "FP":
                pv, ps = predicted[fname]
                print(f"    FP {fname}: pred={pv!r} (status={ps!r}) — GT not_visible")
            elif res == "WRONG":
                pv, ps = predicted[fname]
                gv = gt_fields.get(fname, {}).get("value")
                print(f"    WRONG {fname}: pred={pv!r} vs GT={gv!r}")

    agg, total = aggregate(outcomes)
    print(f"\n  ==== SUMMARY [{label}] ====")
    print(f"  {'field':<14} P       R       Abstain   TP FN FP TN WR")
    for fname in ALL_APP_FIELDS:
        s = agg[fname]
        print(f"  {fname:<14} {fmt(s['precision'])}  {fmt(s['recall'])}  {fmt(s['abstention_accuracy'])}     "
              f"{s['TP']}  {s['FN']}  {s['FP']}  {s['TN']}  {s['WRONG']}")
    print(f"  TOTAL TP={total['TP']} FN={total['FN']} FP={total['FP']} TN={total['TN']} WRONG={total['WRONG']}")
    p = total["TP"] / (total["TP"] + total["FP"] + total["WRONG"]) if (total["TP"] + total["FP"] + total["WRONG"]) else None
    r = total["TP"] / (total["TP"] + total["FN"] + total["WRONG"]) if (total["TP"] + total["FN"] + total["WRONG"]) else None
    aa = total["TN"] / (total["TN"] + total["FP"]) if (total["TN"] + total["FP"]) else None
    print(f"  overall P={fmt(p)} R={fmt(r)} Abstain={fmt(aa)}")
    return {"label": label, "details": details, "aggregate": agg, "total": total}


def main():
    gt = json.loads(GT_PATH.read_text(encoding="utf-8"))
    bags = gt["bags"]

    results = []

    # A) Baseline — current production prompt, 10-field, image-only
    results.append(run_config(
        "A) BASELINE: current 10-field system prompt, image-only",
        bags, SYSTEM_10, FIELD_MAP_10,
    ))

    # B) Baseline + synthetic perfect OCR (upper-bound for OCR path)
    results.append(run_config(
        "B) 10-field + synthetic perfect OCR (upper bound)",
        bags, SYSTEM_10, FIELD_MAP_10, use_ocr=True,
    ))

    # C) 14-field extended schema, image-only
    results.append(run_config(
        "C) 14-field extended schema (adds region/farm/expiryDate/isDecaf), image-only",
        bags, SYSTEM_14, FIELD_MAP_14,
    ))

    # D) 14-field + synthetic perfect OCR
    results.append(run_config(
        "D) 14-field + synthetic perfect OCR",
        bags, SYSTEM_14, FIELD_MAP_14, use_ocr=True,
    ))

    # E) 14-field + OCR + format=json
    results.append(run_config(
        "E) 14-field + synthetic OCR + format=json",
        bags, SYSTEM_14, FIELD_MAP_14, use_ocr=True, json_mode=True,
    ))

    # Compact comparison table
    print("\n\n" + "=" * 70)
    print("COMPARISON")
    print("=" * 70)
    print(f"  {'config':<70} TP  FN  FP  TN  WR")
    for r in results:
        t = r["total"]
        print(f"  {r['label'][:68]:<70} {t['TP']:<3} {t['FN']:<3} {t['FP']:<3} {t['TN']:<3} {t['WRONG']:<3}")

    (EVAL / "eval_results.json").write_text(
        json.dumps([{k: v for k, v in r.items() if k != "details"} | {"details": r["details"]} for r in results],
                   indent=2, default=str), encoding="utf-8")
    print("\nFull results -> eval/eval_results.json")


if __name__ == "__main__":
    main()
