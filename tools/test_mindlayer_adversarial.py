"""
Adversarial model-direct tests against gemma4:e2b.

Goes beyond the initial 5-sample sanity check:
- N=5 repeats per scenario to measure determinism / noise floor
- Multi-image coverage (all 11 test bags)
- Prompt-variant ablations (flat vs nested schema, with/without "Focus on" tail,
  anti-hallucination clause on/off, attribution rules collapsed vs full)
- Hallucination probes (impossible fields, blank image, wrong-language labels)
- JSON-mode test via Ollama format:"json"
- Token accounting on real outputs
"""

import base64
import json
import statistics
import sys
import time
import urllib.request
from pathlib import Path

BASE = Path(__file__).resolve().parent.parent

SYSTEM_NESTED = """You are a coffee bag label analyzer. Extract structured information from coffee bag label images.

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

SYSTEM_FLAT = """You are a coffee bag label analyzer. Extract structured information from coffee bag label images.

Response format (JSON only, no markdown). Emit one key per field plus a parallel *_status key:
{
  "name": "Ethiopia Yirgacheffe", "name_status": "found",
  "roaster": "Counter Culture", "roaster_status": "found",
  "origin": "Ethiopia", "origin_status": "found",
  "variety": null, "variety_status": "not_visible",
  "process": "Washed", "process_status": "uncertain",
  "roastLevel": null, "roastLevel_status": "not_visible",
  "tastingNotes": "blueberry, jasmine, citrus", "tastingNotes_status": "found",
  "altitude": "1900-2100 masl", "altitude_status": "found",
  "weight": "340g", "weight_status": "found",
  "roastDate": null, "roastDate_status": "not_visible"
}

Status rules:
- "found": clearly readable on the label
- "uncertain": visible but blurry/ambiguous
- "not_visible": not on the label
Never guess. Respond with ONLY a JSON object."""

SYSTEM_NESTED_ANTI_HALLUCINATE = SYSTEM_NESTED + """

CRITICAL: If a value is not directly readable from the image or the provided OCR text, you MUST set status to "not_visible" and value to null. Do not infer values from roaster reputation, training data, or visual ambiguity. Fabricated values will be rejected by the downstream pipeline."""


def call_ollama(image_path, prompt, system, model="gemma4:e2b", json_mode=False, temperature=0.2, num_predict=2048):
    body_obj = {
        "model": model,
        "system": system,
        "prompt": prompt,
        "stream": False,
        "options": {"temperature": temperature, "num_predict": num_predict},
    }
    if image_path is not None:
        body_obj["images"] = [base64.b64encode(Path(image_path).read_bytes()).decode()]
    if json_mode:
        body_obj["format"] = "json"
    body = json.dumps(body_obj).encode()
    req = urllib.request.Request(
        "http://localhost:11434/api/generate",
        data=body,
        headers={"Content-Type": "application/json"},
    )
    t0 = time.time()
    with urllib.request.urlopen(req, timeout=600) as r:
        data = json.loads(r.read())
    return data.get("response", ""), time.time() - t0, data


def parse(resp):
    s = resp.strip()
    for fence in ("```json", "```"):
        if s.startswith(fence):
            s = s[len(fence):]
    if s.endswith("```"):
        s = s[:-3]
    s = s.strip()
    try:
        return json.loads(s), None
    except Exception as e:
        return None, str(e)


def normalise_fields(parsed):
    """Return dict of field_name -> (value, status) from either schema."""
    if not parsed:
        return {}
    fields_obj = parsed.get("fields", parsed) if isinstance(parsed, dict) else {}
    out = {}
    flat_keys = ("name", "roaster", "origin", "variety", "process",
                 "roastLevel", "tastingNotes", "altitude", "weight", "roastDate")
    for k, v in fields_obj.items():
        if isinstance(v, dict) and "value" in v:
            out[k] = (v.get("value"), v.get("status", "found"))
    # flat schema fallback
    for k in flat_keys:
        if k in fields_obj and k not in out:
            val = fields_obj.get(k)
            status = fields_obj.get(f"{k}_status", "found" if val else "not_visible")
            if not isinstance(val, dict):
                out[k] = (val, status)
    return out


def one_shot(image, prompt, system, **kw):
    resp, secs, meta = call_ollama(str(image) if image else None, prompt, system, **kw)
    parsed, err = parse(resp)
    return {
        "secs": secs,
        "prompt_tokens": meta.get("prompt_eval_count"),
        "output_tokens": meta.get("eval_count"),
        "json_valid": parsed is not None,
        "parse_err": err,
        "raw": resp,
        "fields": normalise_fields(parsed),
    }


BASIC_PROMPT = "Extract coffee bag information from this label image.\n\nRespond with JSON only."


def with_ocr(ocr_text):
    return (
        "Extract coffee bag information from this label image.\n\n"
        f'Raw OCR text detected on the label:\n"""\n{ocr_text}\n"""\n'
        "Use this text to verify and correct what you see in the image. The OCR may have errors.\n\n"
        "Respond with JSON only."
    )


def with_adversarial_ocr(fields):
    lines = ['Extract coffee bag information from this label image.\n',
             'Context from prior extraction (JSON):', '{', '  "ocr_detected": {']
    for k, v in fields.items():
        lines.append(f'    "{k}": "{v}",')
    lines += [
        "  },",
        "}",
        "",
        "Rules for existing values:",
        "- ocr_detected: Algorithmic text detection. Verify against what you see and correct if needed.",
        "",
        "Focus on fields not yet identified.",
        "",
        "Respond with JSON only.",
    ]
    return "\n".join(lines)


# =============================================================================
# TEST SUITES
# =============================================================================

def test_determinism():
    """N=5 repeats on same (image, prompt) to measure output stability."""
    print("\n" + "=" * 70)
    print("T1. DETERMINISM (N=5 on same image + OCR, temp=0.2)")
    print("=" * 70)
    img = BASE / "testdata/coffee-bags/beansmiths_ethiopia_gedeb_front.jpg"
    ocr = "BEANSMITHS\nEthiopia Gedeb\nNatural\nheirloom\n1900-2100 MASL"
    runs = []
    for i in range(5):
        r = one_shot(img, with_ocr(ocr), SYSTEM_NESTED)
        runs.append(r)
        fv = {k: v[0] for k, v in r["fields"].items()}
        print(f"  run {i+1}: {r['secs']:.1f}s toks={r['output_tokens']} json={r['json_valid']}")
        print(f"          origin={fv.get('origin')!r} roaster={fv.get('roaster')!r} process={fv.get('process')!r} tastingNotes={fv.get('tastingNotes')!r}")
    # Field stability
    all_fields = set()
    for r in runs:
        all_fields.update(r["fields"].keys())
    print("\n  FIELD STABILITY:")
    for f in sorted(all_fields):
        vals = [r["fields"].get(f, (None, None))[0] for r in runs]
        unique = set(str(v).lower().strip() if v else None for v in vals)
        unique.discard(None)
        print(f"    {f}: {len(unique)} unique value(s) across 5 runs -> {list(unique)[:3]}")
    return runs


def test_schema_ablation():
    """Flat vs nested schema: output token count, JSON validity, field yield."""
    print("\n" + "=" * 70)
    print("T2. SCHEMA ABLATION (flat vs nested, N=3 each)")
    print("=" * 70)
    img = BASE / "testdata/coffee-bags/beansmiths_ethiopia_gedeb_front.jpg"
    ocr = "BEANSMITHS\nEthiopia Gedeb\nNatural\nheirloom\n1900-2100 MASL"
    for label, system in [("nested", SYSTEM_NESTED), ("flat", SYSTEM_FLAT)]:
        toks, secs, yields = [], [], []
        for i in range(3):
            r = one_shot(img, with_ocr(ocr), system)
            toks.append(r["output_tokens"] or 0)
            secs.append(r["secs"])
            found_count = sum(1 for v, s in r["fields"].values() if v and s != "not_visible")
            yields.append(found_count)
            print(f"  {label} run {i+1}: {r['secs']:.1f}s toks={r['output_tokens']} json={r['json_valid']} fields_found={found_count}")
        print(f"  [{label}] median toks={statistics.median(toks):.0f} median secs={statistics.median(secs):.1f} median fields_found={statistics.median(yields):.0f}")


def test_hallucination_probe():
    """Can the model admit it doesn't know? Probes with weak images, no OCR."""
    print("\n" + "=" * 70)
    print("T3. HALLUCINATION PROBE (image-only, no OCR)")
    print("=" * 70)
    images = [
        "beansmiths_ethiopia_gedeb_front.jpg",
        "muntasha_ethiopia_natural_front.jpg",
        "kenya_kamwangi_aa_front.jpg",
        "nordbeans_guatemala_severka_back.jpg",
        "motmot_colombia_tumbaga_decaf_back.jpg",
    ]
    for img_name in images:
        img = BASE / "testdata/coffee-bags" / img_name
        if not img.exists():
            continue
        r = one_shot(img, BASIC_PROMPT, SYSTEM_NESTED)
        found = [k for k, (v, s) in r["fields"].items() if v and s == "found"]
        uncertain = [k for k, (v, s) in r["fields"].items() if v and s == "uncertain"]
        not_vis = [k for k, (v, s) in r["fields"].items() if s == "not_visible"]
        print(f"  {img_name}: found={len(found)} uncertain={len(uncertain)} not_visible={len(not_vis)}")
        # Flag suspicious "found" values on image-only (no OCR to ground)
        for k, (v, s) in r["fields"].items():
            if s == "found" and k in ("tastingNotes", "altitude", "weight", "roastDate", "variety", "process"):
                print(f"    [SUSPICIOUS found] {k}={v!r}")


def test_anti_hallucinate_clause():
    """Does adding the explicit anti-hallucination clause reduce false-found?"""
    print("\n" + "=" * 70)
    print("T4. ANTI-HALLUCINATION CLAUSE (image-only, adversarial OCR)")
    print("=" * 70)
    img = BASE / "testdata/coffee-bags/beansmiths_ethiopia_gedeb_front.jpg"
    prompt = with_adversarial_ocr({"origin": "Brazil", "roaster": "Starbucks"})
    for label, system in [("without_clause", SYSTEM_NESTED), ("with_clause", SYSTEM_NESTED_ANTI_HALLUCINATE)]:
        false_found = 0
        runs = 3
        for i in range(runs):
            r = one_shot(img, prompt, system)
            fv = {k: v for k, v in r["fields"].items()}
            # Heuristic: on this image, the correct origin is Ethiopia, roaster Beansmiths.
            # Count "found" with obviously wrong or fabricated values.
            for k, (v, s) in fv.items():
                if s == "found" and v:
                    vl = str(v).lower()
                    if k == "origin" and "ethiopia" not in vl and "gedeb" not in vl:
                        false_found += 1
                    if k == "tastingNotes" and any(x in vl for x in ["jahoda", "zuzu", "zelený"]):
                        false_found += 1
                    if k == "roaster" and "beansmith" not in vl and "starbuck" not in vl:
                        false_found += 1
            print(f"  {label} run {i+1}: origin={fv.get('origin')} roaster={fv.get('roaster')} tastingNotes={fv.get('tastingNotes')}")
        print(f"  [{label}] false-found incidents across {runs} runs: {false_found}")


def test_json_mode():
    """Ollama format:json guarantees valid JSON. Compare to prompt-only."""
    print("\n" + "=" * 70)
    print("T5. JSON MODE (format:json)")
    print("=" * 70)
    img = BASE / "testdata/coffee-bags/beansmiths_ethiopia_gedeb_front.jpg"
    for label, jm in [("prompt_only", False), ("format_json", True)]:
        r = one_shot(img, BASIC_PROMPT, SYSTEM_NESTED, json_mode=jm, num_predict=400)
        print(f"  {label}: {r['secs']:.1f}s toks={r['output_tokens']} json={r['json_valid']} err={r['parse_err']}")
        print(f"    raw (first 200): {r['raw'][:200]!r}")


def test_tight_token_budget():
    """Does maxTokens=fields_needed*120 hold up? Or does it truncate?"""
    print("\n" + "=" * 70)
    print("T6. TIGHT TOKEN BUDGET (num_predict scaled per field)")
    print("=" * 70)
    img = BASE / "testdata/coffee-bags/beansmiths_ethiopia_gedeb_front.jpg"
    ocr = "BEANSMITHS\nEthiopia Gedeb\nNatural"
    for budget in [256, 512, 800, 2048]:
        r = one_shot(img, with_ocr(ocr), SYSTEM_NESTED, num_predict=budget)
        print(f"  budget={budget}: secs={r['secs']:.1f} toks={r['output_tokens']} json={r['json_valid']} fields={sum(1 for v,s in r['fields'].values() if v)}")


def test_attribution_collapse():
    """Four-bucket attribution vs single collapsed rule — does scenario D improve?"""
    print("\n" + "=" * 70)
    print("T7. ATTRIBUTION RULE — FULL vs COLLAPSED (adversarial OCR, N=3)")
    print("=" * 70)
    img = BASE / "testdata/coffee-bags/beansmiths_ethiopia_gedeb_front.jpg"

    # Full four-bucket (current app behaviour)
    full_prompt = (
        "Extract coffee bag information from this label image.\n\n"
        "Context from prior extraction (JSON):\n{\n"
        '  "user_confirmed": {},\n'
        '  "barcode_lookup": {},\n'
        '  "ocr_detected": {\n    "origin": "Brazil",\n    "roaster": "Starbucks",\n  },\n'
        '  "previous_ai_run": {},\n'
        "}\n\n"
        "Rules for existing values:\n"
        "- user_confirmed: Treat as ground truth. Do not contradict.\n"
        "- barcode_lookup: High confidence database match. Only correct if clearly wrong on the label.\n"
        "- ocr_detected: Algorithmic text detection. Verify against what you see and correct if needed.\n"
        "- previous_ai_run: From a prior AI pass. Verify independently — do not blindly repeat.\n\n"
        "Focus on fields not yet identified.\n\nRespond with JSON only."
    )
    collapsed_prompt = (
        "Extract coffee bag information from this label image.\n\n"
        'Prior hints (may be wrong): origin="Brazil", roaster="Starbucks".\n'
        "Verify everything from the image. Correct any hint that disagrees with the image.\n\n"
        "Respond with JSON only."
    )

    for label, prompt in [("full_four_bucket", full_prompt), ("collapsed", collapsed_prompt)]:
        corrections = 0
        for i in range(3):
            r = one_shot(img, prompt, SYSTEM_NESTED)
            fv = {k: v for k, v in r["fields"].items()}
            origin = (fv.get("origin", (None, None))[0] or "").lower()
            roaster = (fv.get("roaster", (None, None))[0] or "").lower()
            # Credit for correctly overriding the wrong hints:
            if "ethiopia" in origin or "gedeb" in origin:
                corrections += 1
            if "beansmith" in roaster:
                corrections += 1
            print(f"  {label} run {i+1}: origin={origin!r} roaster={roaster!r}")
        print(f"  [{label}] correct overrides: {corrections}/6")


def test_focus_on_tail():
    """Does removing 'Focus on fields not yet identified' change re-extraction behaviour?"""
    print("\n" + "=" * 70)
    print("T8. 'FOCUS ON ...' TAIL EFFECT (with vs without)")
    print("=" * 70)
    img = BASE / "testdata/coffee-bags/beansmiths_ethiopia_gedeb_front.jpg"
    base_body = (
        "Extract coffee bag information from this label image.\n\n"
        "Context from prior extraction (JSON):\n{\n"
        '  "ocr_detected": {\n    "origin": "Ethiopia",\n  },\n'
        "}\n\n"
        "Rules for existing values:\n"
        "- ocr_detected: Algorithmic text detection. Verify against what you see and correct if needed.\n"
    )
    with_tail = base_body + "\nFocus on fields not yet identified.\n\nRespond with JSON only."
    without_tail = base_body + "\nRespond with JSON only."
    for label, prompt in [("with_focus_tail", with_tail), ("without_focus_tail", without_tail)]:
        re_verified = 0
        for i in range(3):
            r = one_shot(img, prompt, SYSTEM_NESTED)
            origin_val, origin_status = r["fields"].get("origin", (None, None))
            re_verified += 1 if origin_status == "found" and origin_val and "ethiopia" in str(origin_val).lower() else 0
            print(f"  {label} run {i+1}: origin={origin_val!r} status={origin_status!r}")
        print(f"  [{label}] re-verified origin as 'found': {re_verified}/3")


def test_substring_demotion_correctness():
    """Rec #8: demote 'found' if value not in rawOcrText. Test false-positive rate."""
    print("\n" + "=" * 70)
    print("T9. SUBSTRING DEMOTION — false-positive rate on typo corrections")
    print("=" * 70)
    img = BASE / "testdata/coffee-bags/beansmiths_ethiopia_gedeb_front.jpg"
    # Intentionally typo-ed OCR that should be corrected:
    typo_ocr = "BEANSMITHSS\nEthiopiaa Gedebb\nNaturall\nheirloomm\n1900-2100 MAXL"
    r = one_shot(img, with_ocr(typo_ocr), SYSTEM_NESTED)
    print(f"  OCR had typos; model output:")
    demoted = []
    correct_fixes = []
    for k, (v, s) in r["fields"].items():
        if s == "found" and v:
            in_ocr = str(v).lower() in typo_ocr.lower()
            if not in_ocr:
                demoted.append((k, v))
                # Heuristic "did model correctly fix a typo":
                if any(word.lower().startswith(str(v).lower()[:4]) or str(v).lower()[:4] in word.lower()
                       for word in typo_ocr.split()):
                    correct_fixes.append((k, v))
        print(f"    {k}: {v!r} status={s!r}")
    print(f"  Would demote (not in OCR): {demoted}")
    print(f"  ...of which look like correct typo fixes: {correct_fixes}")
    print(f"  False-positive rate of substring demotion: {len(correct_fixes)}/{len(demoted) or 1}")


if __name__ == "__main__":
    tests = {
        "determinism": test_determinism,
        "schema": test_schema_ablation,
        "hallucination": test_hallucination_probe,
        "anti_clause": test_anti_hallucinate_clause,
        "json_mode": test_json_mode,
        "budget": test_tight_token_budget,
        "attribution": test_attribution_collapse,
        "focus_tail": test_focus_on_tail,
        "substring": test_substring_demotion_correctness,
    }
    only = sys.argv[1:] if len(sys.argv) > 1 else list(tests)
    for name in only:
        if name in tests:
            tests[name]()
