#!/usr/bin/env python3
"""Generate the runtime registry for tracker-accepted exact P1 artwork.`n`nTracker acceptance proves that an illustration passed image and guidance QA.`nExact-stage accessibility copy is supplied by the same locale-selected guidance`nrecord rendered below the image, so the generated asset record deliberately`ndoes not duplicate those strings. Runtime promotion remains fail-closed behind`ncomplete recipe-level localization coverage.`n"""

from __future__ import annotations

import argparse
import json
import sys
from dataclasses import dataclass
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_TRACKER = ROOT / "docs/brewing/starlit-tactile-production-tracker-2026-08-02.json"
DEFAULT_GUIDANCE = ROOT / "app/src/main/assets/p1_exact_guidance_2026_07_27.json"
DEFAULT_OUTPUT = (
    ROOT
    / "app/src/main/java/com/adsamcik/starlitcoffee/ui/guidance/"
    "P1TrackerAcceptedInstructionAssetCatalog.kt"
)


class GenerationError(ValueError):
    """The tracker or canonical stage data cannot safely produce a registry."""


@dataclass(frozen=True)
class ExactStage:
    asset_id: str
    recipe_id: str
    method_family_id: str
    brewer_profile_id: str
    stage_id: str
    content_id: str
    priority: str


@dataclass(frozen=True)
class AcceptedAsset:
    stage: ExactStage
    revision: str


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tracker", type=Path, default=DEFAULT_TRACKER)
    parser.add_argument("--guidance", type=Path, default=DEFAULT_GUIDANCE)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument(
        "--check",
        action="store_true",
        help="fail if the checked-in generated Kotlin does not match the sources",
    )
    return parser.parse_args()


def load_json(path: Path) -> dict:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError as error:
        raise GenerationError(f"Missing input: {path}") from error
    except json.JSONDecodeError as error:
        raise GenerationError(f"Invalid JSON in {path}: {error}") from error


def canonical_stages(guidance: dict) -> dict[str, ExactStage]:
    stages: dict[str, ExactStage] = {}
    for recipe in guidance.get("recipes", []):
        recipe_id = require_text(recipe, "recipe_id")
        family_id = require_text(recipe, "method_family_id")
        profile_id = require_text(recipe, "brewer_profile_id")
        for stage in recipe.get("stages", []):
            source_stage_id = require_text(stage, "stage_id")
            identity = f"p1_{recipe_id}_{source_stage_id}"
            asset_id = f"instruction_{identity}_instruction_default"
            if asset_id in stages:
                raise GenerationError(f"Duplicate canonical asset ID: {asset_id}")
            stages[asset_id] = ExactStage(
                asset_id=asset_id,
                recipe_id=recipe_id,
                method_family_id=family_id,
                brewer_profile_id=profile_id,
                stage_id=identity,
                content_id=f"{identity}_instruction",
                priority=require_text(stage, "visual_priority"),
            )
    if not stages:
        raise GenerationError("Canonical guidance contains no stages")
    return stages


def accepted_assets(tracker: dict, stages: dict[str, ExactStage], root: Path) -> list[AcceptedAsset]:
    accepted: list[AcceptedAsset] = []
    seen: set[str] = set()
    for tracker_asset in tracker.get("assets", []):
        if tracker_asset.get("status") != "accepted_candidate":
            continue
        asset_id = require_text(tracker_asset, "asset_id")
        if asset_id in seen:
            raise GenerationError(f"Duplicate accepted tracker asset ID: {asset_id}")
        seen.add(asset_id)
        stage = stages.get(asset_id)
        if stage is None:
            raise GenerationError(
                f"Accepted tracker asset does not map to canonical exact guidance: {asset_id}"
            )
        tracker_priority = require_text(tracker_asset, "priority")
        if tracker_priority != stage.priority:
            raise GenerationError(
                f"Priority mismatch for {asset_id}: tracker={tracker_priority}, canonical={stage.priority}"
            )
        candidate_path = root / require_text(tracker_asset, "candidate")
        if not candidate_path.is_file():
            raise GenerationError(f"Accepted candidate is missing: {candidate_path}")
        drawable_path = root / "app/src/main/res/drawable-nodpi" / f"{asset_id}.webp"
        if not drawable_path.is_file():
            raise GenerationError(f"Accepted candidate lacks matching packaged drawable: {drawable_path}")
        accepted.append(
            AcceptedAsset(
                stage=stage,
                revision=require_text(tracker_asset, "accepted_revision"),
            ),
        )
    if not accepted:
        raise GenerationError("Tracker contains no accepted candidates")
    return sorted(accepted, key=lambda asset: (asset.stage.recipe_id, asset.stage.stage_id))


def require_text(record: dict, key: str) -> str:
    value = record.get(key)
    if not isinstance(value, str) or not value.strip():
        raise GenerationError(f"Expected non-empty string '{key}' in {record}")
    return value


def kotlin_string(value: str) -> str:
    return json.dumps(value, ensure_ascii=False)


def render(tracker: dict, accepted: list[AcceptedAsset]) -> str:
    updated_on = require_text(tracker, "updated_on")
    lines = [
        "// Generated by tools/generate_p1_tracker_accepted_asset_catalog.py; do not edit.",
        "package com.adsamcik.starlitcoffee.ui.guidance",
        "",
        "import androidx.annotation.DrawableRes",
        "import com.adsamcik.starlitcoffee.R",
        "import com.adsamcik.starlitcoffee.domain.brewing.BrewerProfileId",
        "import com.adsamcik.starlitcoffee.domain.brewing.BuiltInRecipeId",
        "import com.adsamcik.starlitcoffee.domain.brewing.InstructionAssetId",
        "import com.adsamcik.starlitcoffee.domain.brewing.MethodFamilyId",
        "import com.adsamcik.starlitcoffee.domain.brewing.StageContentId",
        "import com.adsamcik.starlitcoffee.domain.brewing.StageId",
        "import java.time.LocalDate",
        "",
        "/**",
        " * Artwork that passed the production tracker image and guidance checks.",
        " *",
        " * Exact accessibility copy comes from the locale-selected stage guidance record.",
        " * Runtime promotion remains gated by complete recipe localization coverage.",
        " */",
        "data class P1TrackerAcceptedInstructionAsset(",
        "    val recipeId: BuiltInRecipeId,",
        "    val id: InstructionAssetId,",
        "    val familyId: MethodFamilyId,",
        "    val profileId: BrewerProfileId,",
        "    val stageId: StageId,",
        "    val contentId: StageContentId,",
        "    val visualPriority: P1ExactVisualPriority,",
        "    @param:DrawableRes val drawableRes: Int,",
        "    val trackerRevision: String,",
        ") {",
        "    init {",
        "        require(id.value == \"instruction_${contentId.value}_default\") {",
        "            \"Accepted P1 asset ID must use the exact-content drawable convention\"",
        "        }",
        "    }",
        "}",
        "",
        "private val trackerAcceptedAssets: List<P1TrackerAcceptedInstructionAsset> = listOf(",
    ]
    for item in accepted:
        stage = item.stage
        priority = stage.priority.upper().replace("-", "_")
        lines.extend(
            [
                "    P1TrackerAcceptedInstructionAsset(",
                f"        recipeId = BuiltInRecipeId({kotlin_string(stage.recipe_id)}),",
                f"        id = InstructionAssetId({kotlin_string(stage.asset_id)}),",
                f"        familyId = MethodFamilyId({kotlin_string(stage.method_family_id)}),",
                f"        profileId = BrewerProfileId({kotlin_string(stage.brewer_profile_id)}),",
                f"        stageId = StageId({kotlin_string(stage.stage_id)}),",
                f"        contentId = StageContentId({kotlin_string(stage.content_id)}),",
                f"        visualPriority = P1ExactVisualPriority.{priority},",
                f"        drawableRes = R.drawable.{stage.asset_id},",
                f"        trackerRevision = {kotlin_string(item.revision)},",
                "    ),",
            ],
        )
    lines.extend(
        [
            ")",
            "",
            "object P1TrackerAcceptedInstructionAssetCatalog {",
            f"    const val TRACKER_UPDATED_ON = {kotlin_string(updated_on)}",
            "    const val TRACKER_PATH = \"docs/brewing/starlit-tactile-production-tracker-2026-08-02.json\"",
            "",
            "    val assets: List<P1TrackerAcceptedInstructionAsset> = trackerAcceptedAssets",
            "",
            "    /** Accepted art is safe metadata; localized copy is enforced by the recipe gate. */",
            "    fun runtimeAssets(): List<InstructionAssetRecord> = assets.map { candidate ->",
            "        candidate.toRuntimeAsset()",
            "    }",
            "",
            "    private fun P1TrackerAcceptedInstructionAsset.toRuntimeAsset(): InstructionAssetRecord =",
            "        InstructionAssetRecord(",
            "            id = id,",
            "            familyId = familyId,",
            "            profileId = profileId,",
            "            stageId = stageId,",
            "            contentId = contentId,",
            "            namingConvention = InstructionAssetNamingConvention.EXACT_CONTENT_ID,",
            "            drawableRes = drawableRes,",
            "            mandatoryForFullGuidance = visualPriority != P1ExactVisualPriority.OPTIONAL,",
            "            safetySensitive = visualPriority == P1ExactVisualPriority.SAFETY_CRITICAL,",
            "            provenance = InstructionAssetProvenance(",
            "                promptDocument = TRACKER_PATH,",
            "                promptRevision = \"accepted_${trackerRevision}\",",
            "            ),",
            "            review = InstructionAssetReview(",
            "                status = InstructionAssetReviewStatus.APPROVED,",
            "                reviewer = \"Starlit tactile production tracker\",",
            "                reviewedOn = LocalDate.parse(TRACKER_UPDATED_ON),",
            "            ),",
            "        )",
            "}",
            "",
        ],
    )
    return "\n".join(lines)


def main() -> int:
    args = parse_args()
    tracker_path = args.tracker.resolve()
    guidance_path = args.guidance.resolve()
    output_path = args.output.resolve()
    root = ROOT
    tracker = load_json(tracker_path)
    stages = canonical_stages(load_json(guidance_path))
    output = render(tracker, accepted_assets(tracker, stages, root))
    if args.check:
        try:
            current = output_path.read_text(encoding="utf-8")
        except FileNotFoundError:
            print(f"Missing generated file: {output_path}", file=sys.stderr)
            return 1
        if current != output:
            print(
                f"Generated registry is stale: run {Path(__file__).as_posix()}",
                file=sys.stderr,
            )
            return 1
        print(f"P1 tracker accepted asset registry is current ({len(accepted_assets(tracker, stages, root))} assets).")
        return 0
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(output, encoding="utf-8")
    print(f"Wrote {output_path}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except GenerationError as error:
        print(f"error: {error}", file=sys.stderr)
        raise SystemExit(1)
