"""Export reviewed source procedures without inventing quantities or physical completion."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "docs/brewing/research/2026-10-02-method-guides/methods"
DESTINATION = ROOT / "app/src/main/assets/brewing/reviewed-method-guides.json"

# Explicit source-clock boundaries. A step confirmation starts the physical event;
# preparation never starts extraction, and reference time never completes equipment work.
BOUNDARIES = {
    "aeropress": ("aeropress_standard", "seal_steep", "press"),
    "automatic-drip": ("automatic_batch_generic", "start", "finish"),
    "chemex": ("manual_thick_paper_carafe", "bloom", "drain"),
    "clever": ("clever_style", "add_coffee_start_clock", "release_and_drain"),
    "cold-brew": ("cold_immersion_generic", "steep", "steep"),
    "espresso": ("espresso_pump_generic", "start_shot", "stop_and_taste"),
    "french-press": ("french_press_generic", "add-water", "decant"),
    "hario-switch": ("hario_switch", "fill", "finish"),
    "kalita-wave": ("manual_wave_155", "bloom", "drain_serve"),
    "melitta": ("manual_wedge_generic", "bloom", "drain_serve"),
    "moka-pot": ("moka_generic_unspecified", "heat", "stop"),
    "percolator": ("presto_02822", "brew", "brew"),
    "phin": ("vietnamese_phin", "initial_wetting", "observe_drainage"),
    "pulsar": ("pulsar_standard", "open_start_bloom", "drain_and_serve"),
    "siphon": ("hario_technica_tcar3", "steep", "return"),
    "turkish": ("cezve_generic", "heat", "heat"),
    "v60": ("v60_02", "bloom_pour", "finish"),
}


def export() -> str:
    guides = []
    for path in sorted(SOURCE.glob("*.json")):
        raw = path.read_text(encoding="utf-8-sig").replace("\r\n", "\n")
        data = json.loads(raw)
        method = data["method_id"]
        assert data["review_status"] == "editorially_reviewed", method
        profile, origin, end = BOUNDARIES[method]
        recipe = data["recipe"]
        stage_ids = [step["id"] for step in recipe["steps"]]
        assert origin in stage_ids and end in stage_ids, (method, stage_ids, origin, end)
        assert len(stage_ids) == len(set(stage_ids)), method
        assert stage_ids.index(origin) <= stage_ids.index(end), method
        source_ids = recipe["source_ids"]
        sources = [source for source in data["sources"] if source["access_status"] == "verified"]
        assert set(source_ids).issubset({source["id"] for source in sources}), method
        guides.append({
            "id": "chemex_30_480" if method == "chemex" else f"reviewed_{method.replace('-', '_')}_v1",
            "methodId": method, "profileId": profile, "scope": data["scope"],
            "reviewedOn": data["research_date"], "sourceSha256": hashlib.sha256(raw.encode()).hexdigest(),
            "originStepId": origin, "endStepId": end,
            "recipe": recipe, "safety": data["safety"],
            "sources": [{"id": s["id"], "title": s["title"], "url": s["url"]} for s in sources],
            "variants": data["variants"], "troubleshooting": data["troubleshooting"],
            "uncertainties": data["uncertainties"], "doNotClaim": data["do_not_claim"],
        })
    assert len(guides) == len(BOUNDARIES) == 17
    return json.dumps({"schemaVersion": 1, "guides": guides}, ensure_ascii=False, indent=2) + "\n"


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    result = export()
    if args.check:
        assert DESTINATION.read_text(encoding="utf-8") == result, "Native guide export has drifted"
    else:
        DESTINATION.parent.mkdir(parents=True, exist_ok=True)
        DESTINATION.write_text(result, encoding="utf-8", newline="\n")
    print("17 reviewed guides retain source quantities, clock boundaries and evidence")
