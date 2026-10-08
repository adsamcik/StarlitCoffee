"""Check guide coverage, evidence references, units and recipe arithmetic."""

from __future__ import annotations

import argparse
import json
import math
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / "docs/brewing/research/2026-10-02-method-guides"
SOURCE_TYPES = {"manufacturer", "original_creator", "roaster", "research", "standard", "government"}
PROVENANCE = {"published_recipe", "app_authored_synthesis", "manufacturer_procedure"}
RATIO_BASES = {"brew_water_input", "beverage_output", "equipment_fill", "volume_per_mass"}
SCALING = {"linear_safe_within_capacity", "revalidate_batch_size", "equipment_bound"}


def check_guide(entry: dict, errors: list[str]) -> dict:
    prefix = entry["id"]

    def require(condition: bool, message: str) -> None:
        if not condition:
            errors.append(f"{prefix}: {message}")

    def numeric(value: object, label: str, positive: bool = False) -> None:
        if value is None:
            return
        require(isinstance(value, (int, float)) and not isinstance(value, bool), f"{label} must be numeric or null")
        if isinstance(value, (int, float)):
            require(math.isfinite(value) and (value > 0 if positive else value >= 0), f"invalid {label}: {value}")

    file = PACK / entry["evidence"]
    try:
        data = json.loads(file.read_text(encoding="utf-8-sig"))
    except (OSError, ValueError) as exc:
        errors.append(f"{prefix}: cannot read JSON: {exc}")
        return {}
    require(data.get("method_id") == prefix, "method_id does not match coverage")
    require(data.get("research_date") == "2026-10-02", "wrong research date")
    require(data.get("review_status") in {"research_draft", "editorially_reviewed"}, "unknown review status")
    require(data.get("physical_brew_tested") is False, "physical brewing must not be claimed")
    for key in ("title", "scope", "uncertainties", "do_not_claim", "safety", "app_audit", "variants", "troubleshooting"):
        require(bool(data.get(key)) or key in {"app_audit", "variants"} and isinstance(data.get(key), list), f"missing {key}")
    guide = PACK / entry["guide"]
    require(guide.is_file(), "missing readable guide")
    if guide.is_file():
        text = guide.read_text(encoding="utf-8-sig")
        require(text.startswith("# "), "guide needs a clear title")
        require(len(text.split()) >= 300, "guide is too short to explain the complete workflow")
        require(not re.search(r"(?im)^.*\b(?:TODO|TBD|PLACEHOLDER)\b.*$", text), "unfinished placeholder in guide")

    sources = data.get("sources", [])
    ids = [source.get("id") for source in sources]
    require(len(set(ids)) == len(ids) and all(ids), "duplicate or missing source IDs")
    verified = {source["id"] for source in sources if source.get("access_status") == "verified"}
    require(len(verified) >= 2, "needs at least two verified primary sources")
    for source in sources:
        require(bool(source.get("title")) and bool(source.get("author_or_organization")), "source needs a title and primary author")
        require(source.get("source_type") in SOURCE_TYPES, "invalid source type")
        require(source.get("access_status") in {"verified", "partial", "blocked"}, "invalid source access status")
        require(bool(re.match(r"^https?://[^/]+/", source.get("url", ""))), "source URL must be a direct webpage")
        require(bool(source.get("supports")), "source must describe its supported claims")
        require(source.get("accessed") == data.get("research_date"), "source access date differs")

    def references(obj: object, location: str) -> None:
        if isinstance(obj, dict):
            if "source_ids" in obj:
                refs = obj["source_ids"]
                # Code-only findings can be evidenced by their audited path;
                # absence of external recipe evidence must not create a fake citation.
                code_only = ".app_audit[" in location and refs == []
                require(isinstance(refs, list) and (bool(refs) or code_only), f"{location}: missing source references")
                if isinstance(refs, list):
                    require(all(item in ids for item in refs), f"{location}: unknown source reference")
                    require(code_only or any(item in verified for item in refs), f"{location}: no verified supporting source")
            for key, child in obj.items():
                references(child, location + "." + key)
        elif isinstance(obj, list):
            for index, child in enumerate(obj):
                references(child, f"{location}[{index}]")

    references(data, prefix)
    recipe = data.get("recipe", {})
    require(bool(recipe.get("source_ids")), "recipe needs supporting source references")
    require(recipe.get("provenance") in PROVENANCE, "invalid recipe provenance")
    basis = recipe.get("ratio_basis")
    require(basis in RATIO_BASES, "invalid ratio basis")
    require(recipe.get("scaling") in SCALING, "invalid scaling decision")
    for key in ("name", "equipment", "grind_description", "completion_signal"):
        require(bool(recipe.get(key)), "missing recipe " + key)
    for key in ("coffee_dose_g", "brew_water_g", "brew_water_ml", "target_beverage_g", "ratio_value"):
        require(key in recipe, "missing recipe quantity " + key + " (use null if unknown)")
        numeric(recipe.get(key), key, positive=True)
    numeric(recipe.get("ice_g"), "ice_g")
    dose = recipe.get("coffee_dose_g")
    water = recipe.get("brew_water_g")
    volume = recipe.get("brew_water_ml")
    ratio = recipe.get("ratio_value")
    numerator = {"brew_water_input": water, "beverage_output": recipe.get("target_beverage_g"),
                 "volume_per_mass": volume, "equipment_fill": None}.get(basis)
    if ratio is not None:
        require(bool(dose) and numerator is not None, "numeric ratio has no quantities in its declared basis")
        if dose and numerator is not None:
            require(math.isclose(numerator / dose, ratio, rel_tol=0.005, abs_tol=0.02), "ratio does not match the example quantities")
    if basis == "equipment_fill":
        require(ratio is None, "equipment-fill procedure must not imply a universal numeric ratio")
    temperature = recipe.get("water_temperature_c", {})
    require(bool(temperature.get("control")), "temperature needs a control/applicability description")
    for key in ("min", "max"):
        numeric(temperature.get(key), "temperature " + key)
        if isinstance(temperature.get(key), (int, float)):
            require(temperature[key] <= 100, "invalid water temperature bound")
    if temperature.get("min") is not None and temperature.get("max") is not None:
        require(temperature["min"] <= temperature["max"] <= 100, "invalid water temperature range")
    timing = recipe.get("expected_time_s", {})
    for key in ("min", "max"):
        numeric(timing.get(key), "total time " + key)
    require(bool(timing.get("clock_start")) and bool(timing.get("clock_end")), "time needs a clock origin and end")
    if timing.get("min") is not None and timing.get("max") is not None:
        require(timing["min"] <= timing["max"], "time range is reversed")

    steps = recipe.get("steps", [])
    require(len(steps) >= 3, "recipe does not describe a complete sequence")
    step_ids = [step.get("id") for step in steps]
    require(all(step_ids) and len(set(step_ids)) == len(step_ids), "duplicate or missing step IDs")
    running_water = 0.0
    running_volume = 0.0
    previous_time = -1.0
    water_steps = 0
    volume_steps = 0
    for step in steps:
        require(bool(step.get("source_ids")), f"step {step.get('id')}: missing source references")
        for key in ("instruction", "why", "completion"):
            require(bool(step.get(key)), f"step {step.get('id')}: missing {key}")
        for key in ("water_add_g", "water_cumulative_g", "water_add_ml", "water_cumulative_ml", "start_s", "duration_s"):
            numeric(step.get(key), "step " + key)
        amount = step.get("water_add_g")
        if amount is not None:
            running_water += amount
            water_steps += 1
        cumulative = step.get("water_cumulative_g")
        if cumulative is not None and water_steps:
            require(math.isclose(cumulative, running_water, abs_tol=0.15), f"step {step.get('id')}: cumulative pour mismatch")
        added_volume = step.get("water_add_ml")
        if added_volume is not None:
            running_volume += added_volume
            volume_steps += 1
        cumulative_volume = step.get("water_cumulative_ml")
        if cumulative_volume is not None and volume_steps:
            require(math.isclose(cumulative_volume, running_volume, abs_tol=0.15), f"step {step.get('id')}: cumulative volume mismatch")
        start = step.get("start_s")
        if start is not None:
            require(start >= previous_time, f"step {step.get('id')}: times run backwards")
            previous_time = start
    if water is not None:
        require(water_steps > 0, "mass recipe needs water_add_g steps")
        if water_steps:
            require(math.isclose(water, running_water, abs_tol=0.15), "step additions do not equal total brew water")
    if volume is not None:
        require(volume_steps > 0, "volume recipe needs water_add_ml steps")
        if volume_steps:
            require(math.isclose(volume, running_volume, abs_tol=0.15), "step volumes do not equal total brew water")
    for audit in data.get("app_audit", []):
        path = re.sub(r":\d+(?:-\d+)?$", "", audit.get("path", ""))
        require((ROOT / path).is_file(), "audit path does not exist: " + path)
        require(audit.get("severity") in {"high", "medium", "low", "info"}, "invalid audit severity")
    return {"method": prefix, "review_status": data.get("review_status"), "verified_sources": len(verified),
            "steps": len(steps), "app_findings": len(data.get("app_audit", []))}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--allow-incomplete", action="store_true", help="Check only delivered guides while agents are working")
    parser.add_argument("--require-reviewed", action="store_true", help="Require editorial review for every delivered guide")
    args = parser.parse_args()
    coverage = json.loads((PACK / "coverage.json").read_text(encoding="utf-8"))
    entries = coverage["methods"]
    errors: list[str] = []
    if len(entries) != 17 or len({entry["id"] for entry in entries}) != 17:
        errors.append("coverage must contain 17 distinct method assignments")
    reports = []
    for entry in entries:
        if args.allow_incomplete and not (PACK / entry["evidence"]).is_file():
            continue
        reports.append(check_guide(entry, errors))
        if args.require_reviewed and reports[-1].get("review_status") != "editorially_reviewed":
            errors.append(f"{entry['id']}: editorial review is not complete")
    print(json.dumps({"checked": len(reports), "expected": len(entries), "reports": reports, "errors": errors}, indent=2))
    return 1 if errors else 0


if __name__ == "__main__":
    raise SystemExit(main())
