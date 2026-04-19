"""
Test the Mindlayer LLM prompts against Gemma 4 E2B via Ollama.

Replicates the exact system prompt and extraction prompt used by
MindlayerLlmInferenceProvider, then evaluates how a small LLM responds.
"""

import base64
import json
import sys
import time
from pathlib import Path

import urllib.request

SYSTEM_PROMPT = """You are a coffee bag label analyzer. Extract structured information from coffee bag label images.

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


def build_prompt_with_context(ocr_text=None, known_origins=None, known_roasters=None, existing_ocr=None):
    parts = ["Extract coffee bag information from this label image."]

    if existing_ocr:
        parts.append("\nContext from prior extraction (JSON):")
        parts.append("{")
        parts.append('  "ocr_detected": {')
        for k, v in existing_ocr.items():
            parts.append(f'    "{k}": "{v}",')
        parts.append("  },")
        parts.append("}")
        parts.append("\nRules for existing values:")
        parts.append("- ocr_detected: Algorithmic text detection. Verify against what you see and correct if needed.")
        parts.append("\nFocus on fields not yet identified.")

    if known_origins or known_roasters:
        parts.append("\nReference vocabulary from user's coffee collection:")
        if known_origins:
            parts.append(f"- Known origins: {', '.join(known_origins[:20])}")
        if known_roasters:
            parts.append(f"- Known roasters: {', '.join(known_roasters[:20])}")
        parts.append("Prefer these values when a match is close. Do not force a match if the label clearly says something different.")

    if ocr_text:
        parts.append("\nRaw OCR text detected on the label:")
        parts.append('"""')
        parts.append(ocr_text)
        parts.append('"""')
        parts.append("Use this text to verify and correct what you see in the image. The OCR may have errors.")

    parts.append("\nRespond with JSON only.")
    return "\n".join(parts)


def call_ollama(image_path, prompt, system=SYSTEM_PROMPT, model="gemma4:e2b"):
    img_b64 = base64.b64encode(Path(image_path).read_bytes()).decode()
    body = json.dumps({
        "model": model,
        "system": system,
        "prompt": prompt,
        "images": [img_b64],
        "stream": False,
        "options": {"temperature": 0.2, "num_predict": 1024},
    }).encode()
    req = urllib.request.Request(
        "http://localhost:11434/api/generate",
        data=body,
        headers={"Content-Type": "application/json"},
    )
    t0 = time.time()
    with urllib.request.urlopen(req, timeout=600) as r:
        data = json.loads(r.read())
    return data.get("response", ""), time.time() - t0, data


def try_parse(resp):
    cleaned = resp.strip()
    for fence in ("```json", "```"):
        if cleaned.startswith(fence):
            cleaned = cleaned[len(fence):]
    if cleaned.endswith("```"):
        cleaned = cleaned[:-3]
    cleaned = cleaned.strip()
    try:
        return json.loads(cleaned), None
    except Exception as e:
        return None, str(e)


SCENARIOS = [
    {
        "name": "A. Image only (bare)",
        "image": "testdata/coffee-bags/beansmiths_ethiopia_gedeb_front.jpg",
        "prompt": build_prompt_with_context(),
        "ground_truth": {"origin": "Ethiopia", "roaster": "Beansmiths"},
    },
    {
        "name": "B. Image + OCR text",
        "image": "testdata/coffee-bags/beansmiths_ethiopia_gedeb_front.jpg",
        "prompt": build_prompt_with_context(
            ocr_text="BEANSMITHS\nEthiopia Gedeb\nNatural\nheirloom\n1900-2100 MASL\nblueberry, chocolate, wine\n250g"
        ),
        "ground_truth": {"origin": "Ethiopia", "roaster": "Beansmiths"},
    },
    {
        "name": "C. Image + OCR + known values grounding",
        "image": "testdata/coffee-bags/beansmiths_ethiopia_gedeb_front.jpg",
        "prompt": build_prompt_with_context(
            ocr_text="BEANSMITHS\nEthiopia Gedeb\nNatural\nheirloom\n1900-2100 MASL",
            known_origins=["Ethiopia", "Kenya", "Colombia", "Guatemala"],
            known_roasters=["Beansmiths", "Counter Culture", "Nordbeans", "Muntasha"],
        ),
        "ground_truth": {"origin": "Ethiopia", "roaster": "Beansmiths"},
    },
    {
        "name": "D. Image + adversarial OCR (wrong fields)",
        "image": "testdata/coffee-bags/beansmiths_ethiopia_gedeb_front.jpg",
        "prompt": build_prompt_with_context(
            existing_ocr={"origin": "Brazil", "roaster": "Starbucks"},
        ),
        "ground_truth": {"origin": "Ethiopia", "roaster": "Beansmiths"},
    },
    {
        "name": "E. Kenya Kieni from Coffee Collective",
        "image": "testdata/coffee-bags/coffeecollective_kenya_kieni_front.jpg",
        "prompt": build_prompt_with_context(),
        "ground_truth": {"origin": "Kenya", "roaster": "Coffee Collective"},
    },
]


def main():
    for sc in SCENARIOS:
        img = Path(sc["image"])
        if not img.exists():
            print(f"SKIP {sc['name']}: missing {img}")
            continue
        print("=" * 70)
        print(f"[{sc['name']}]  image={img.name}")
        print(f"  prompt len = {len(sc['prompt'])} chars")
        try:
            resp, secs, meta = call_ollama(str(img), sc["prompt"])
        except Exception as e:
            print(f"  ERROR: {e}")
            continue
        parsed, err = try_parse(resp)
        print(f"  latency: {secs:.1f}s  eval_count={meta.get('eval_count')}  prompt_eval_count={meta.get('prompt_eval_count')}")
        print(f"  JSON valid: {parsed is not None}  err={err}")
        print(f"  raw response ({len(resp)} chars):")
        safe = resp.replace("\n", "\n  ")[:1500].encode("ascii", "replace").decode("ascii")
        print("  " + safe)
        if parsed:
            fields = parsed.get("fields", parsed)
            print("  extracted summary:")
            for k, v in fields.items():
                if isinstance(v, dict):
                    print(f"    {k}: {v.get('value')!r} [{v.get('status')}]")
                else:
                    print(f"    {k}: {v!r}")
            gt = sc.get("ground_truth", {})
            for k, expected in gt.items():
                entry = fields.get(k) if isinstance(fields.get(k), dict) else {"value": fields.get(k)}
                got = (entry or {}).get("value") or ""
                ok = expected.lower() in str(got).lower()
                print(f"  GT {k}: expected~'{expected}' got='{got}' -> {'OK' if ok else 'MISS'}")


if __name__ == "__main__":
    main()
