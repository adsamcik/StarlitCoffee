#!/usr/bin/env python3
"""Generate the exact P1 guidance asset from the canonical brewing library.

The output is a deliberately narrow, deterministic projection: only the 20
P1 recipes and their ordered stage/guidance records are packaged. The source
hash is checked before extraction so similarly named or newer libraries cannot
silently change shipped instructions.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from typing import Any


SOURCE_SHA256 = "aa006a366297d659332986f8971b5442d77bf168eba30e520708742b3f76506d"
SOURCE_SCHEMA_VERSION = "1.0.0"
SOURCE_EXECUTION_DATE = "2026-07-27"

P1_RECIPE_IDS = (
    "v60_official_15_250",
    "v60_rao_20_330",
    "v60_kasuya_4_6_20_300",
    "v60_kurasu_flash_16_150_70",
    "wave185_ozone_25_400",
    "wedge_pulse_23_5_400",
    "chemex_42_700",
    "generic_conical_low_agitation_20_320",
    "clever_water_first_15_250",
    "clever_coffee_first_15_250",
    "switch_official_20_240",
    "switch_ole_boen_hybrid_16_5_240",
    "switch_gravity_15_250",
    "cezve_turkish_single_rise_6_65",
    "cezve_bounded_repeated_rise_12_130",
    "auto_batch_500_30",
    "auto_batch_1000_60",
    "auto_cupone_20_300",
    "phin_gravity_14_118",
    "phin_screw_18_120",
)

RECIPE_FIELDS = (
    "recipe_id",
    "recipe_name",
    "method_family_id",
    "brewer_profile_id",
    "recipe_approach",
    "evidence_status",
    "confidence",
    "original_source_or_provenance",
)

STAGE_FIELDS = (
    "stage_id",
    "order",
    "action",
    "start_time_or_preceding_condition",
    "target_duration_or_range",
    "added_water_target",
    "cumulative_water_target",
    "beverage_yield_target",
    "equipment_state",
    "completion_criterion",
    "observable_signs",
    "optional_tip",
    "warning",
    "evidence_sources",
    "visual_priority",
    "guidance",
)


def project(source_path: Path) -> str:
    source_bytes = source_path.read_bytes()
    actual_hash = hashlib.sha256(source_bytes).hexdigest()
    if actual_hash != SOURCE_SHA256:
        raise ValueError(
            f"Canonical source hash mismatch: expected {SOURCE_SHA256}, got {actual_hash}"
        )

    source = json.loads(source_bytes)
    metadata = source["metadata"]
    if metadata["schema_version"] != SOURCE_SCHEMA_VERSION:
        raise ValueError("Canonical source schema version changed")
    if metadata["execution_date"] != SOURCE_EXECUTION_DATE:
        raise ValueError("Canonical source execution date changed")

    source_recipes = {recipe["recipe_id"]: recipe for recipe in source["recipes"]}
    missing = set(P1_RECIPE_IDS).difference(source_recipes)
    if missing:
        raise ValueError(f"Canonical source is missing P1 recipes: {sorted(missing)}")

    recipes: list[dict[str, Any]] = []
    for recipe_id in P1_RECIPE_IDS:
        source_recipe = source_recipes[recipe_id]
        recipe = {field: source_recipe[field] for field in RECIPE_FIELDS}
        recipe["stages"] = [
            {field: stage[field] for field in STAGE_FIELDS}
            for stage in source_recipe["ordered_stages"]
        ]
        recipes.append(recipe)

    manifest = {
        "source_schema_version": SOURCE_SCHEMA_VERSION,
        "source_execution_date": SOURCE_EXECUTION_DATE,
        "source_sha256": SOURCE_SHA256,
        "recipe_count": len(recipes),
        "stage_count": sum(len(recipe["stages"]) for recipe in recipes),
        "recipes": recipes,
    }
    if manifest["recipe_count"] != 20 or manifest["stage_count"] != 114:
        raise ValueError(
            "P1 manifest coverage changed: "
            f"{manifest['recipe_count']} recipes, {manifest['stage_count']} stages"
        )
    return json.dumps(manifest, ensure_ascii=False, indent=2) + "\n"


def main() -> None:
    project_root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path, help="Canonical coffee library JSON")
    parser.add_argument(
        "--output",
        type=Path,
        default=project_root / "app/src/main/assets/p1_exact_guidance_2026_07_27.json",
    )
    parser.add_argument(
        "--check",
        action="store_true",
        help="Fail when the existing output differs instead of writing it",
    )
    args = parser.parse_args()

    expected = project(args.source)
    if args.check:
        actual = args.output.read_text(encoding="utf-8")
        if actual != expected:
            raise SystemExit(f"{args.output} is not the canonical generated projection")
        print(f"Verified {args.output}")
        return

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(expected, encoding="utf-8")
    print(f"Wrote {args.output}")


if __name__ == "__main__":
    main()
