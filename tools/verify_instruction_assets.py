#!/usr/bin/env python3
"""Verify the delivery contract for every committed instruction illustration.

Tracker-approved exact-stage assets use transparency so their expressive dark
stage can sit naturally on both light and dark app surfaces. Untracked review
inputs remain opaque until they pass through the same production loop. Retired
legacy resource names are rejected so obsolete artwork cannot be repackaged.
"""

from __future__ import annotations

import hashlib
import json
import re
import sys
from datetime import date
from pathlib import Path

from PIL import Image, UnidentifiedImageError

from generate_p1_exact_visual_review_catalog import (
    DEFAULT_GUIDANCE,
    DEFAULT_REVIEW,
    ReviewError,
    expected_assets,
    load_json,
    validate_review,
)
from generate_guidance_asset_catalog import load_admission


ROOT = Path(__file__).resolve().parents[1]
DRAWABLE_DIR = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi"
TRACKER_PATH = (
    ROOT / "docs" / "brewing" / "starlit-tactile-production-tracker-2026-08-02.json"
)
GENERATED_ADMISSION_PATH = ROOT / "docs/brewing/generated-guidance-20261025/admission.json"
PULSAR_TRACKER_PATH = ROOT / "docs/brewing/pulsar-learning-guide-production-2026-08-19.json"
GENERATED_CHECKS = frozenset({
    "full_resolution", "phone_light", "phone_dark", "equipment_geometry", "action_state",
    "hand_safety", "crop_and_flow", "alt_text_agreement", "minimalism",
})
EXPECTED_SIZE = (1024, 768)
MAX_ENCODED_BYTES = 300_000
RESOURCE_NAME = re.compile(r"[a-z][a-z0-9_]*")
RETIRED_RESOURCE_NAMES = frozenset(
    {
        "instruction_steep_and_release_clever_style_"
        "clever_style_insert_and_rinse_filter_default",
        "instruction_steep_and_release_hario_switch_"
        "hario_switch_add_coffee_default",
        "instruction_restricted_flow_gravity_concentrate_vietnamese_phin_"
        "vietnamese_phin_place_on_stable_cup_default",
    }
)


def sha256(path: Path) -> str:
    """Return a stable digest without decoding or rewriting the asset."""
    return hashlib.sha256(path.read_bytes()).hexdigest()


def load_approved_candidates() -> tuple[dict[str, Path], list[str]]:
    """Load tracker delivery candidates; this does not grant independent pixel approval."""
    errors: list[str] = []
    try:
        tracker = json.loads(TRACKER_PATH.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        return {}, [f"cannot load production tracker ({error})"]

    candidates: dict[str, Path] = {}
    for item in tracker.get("assets", []):
        asset_id = item.get("asset_id")
        if item.get("status") != "accepted_candidate":
            continue
        if item.get("qa") != "complete":
            errors.append(f"{asset_id or '<missing id>'}: accepted candidate is not QA-complete")
            continue
        candidate_value = item.get("candidate")
        if not asset_id or not candidate_value:
            errors.append("tracker item is missing asset_id or candidate")
            continue
        candidate = ROOT / candidate_value
        if not candidate.is_file():
            errors.append(f"{asset_id}: accepted candidate does not exist: {candidate_value}")
            continue
        if asset_id in candidates:
            errors.append(f"{asset_id}: duplicate tracker entry")
            continue
        candidates[asset_id] = candidate


    return candidates, errors


def admitted_file(scene: dict, path_key: str, hash_key: str) -> Path:
    """Resolve only repository-owned evidence and bind approval to its actual bytes."""
    relative = scene.get(path_key)
    digest = scene.get(hash_key)
    if not isinstance(relative, str) or not relative:
        raise ValueError(f"missing {path_key}")
    path = (ROOT / relative).resolve()
    if not path.is_relative_to(ROOT.resolve()) or not path.is_file():
        raise ValueError(f"{path_key} must name an existing repository file")
    if not isinstance(digest, str) or re.fullmatch(r"[0-9a-f]{64}", digest) is None:
        raise ValueError(f"invalid {hash_key}")
    if sha256(path) != digest:
        raise ValueError(f"{path_key} bytes differ from reviewed {hash_key}")
    return path


def load_pulsar_candidates() -> tuple[dict[str, Path], list[str]]:
    """Recognize existing transparent Pulsar delivery only through its exact production hashes."""
    try:
        tracker = json.loads(PULSAR_TRACKER_PATH.read_text(encoding="utf-8"))
        if tracker.get("schemaVersion") != 1 or tracker.get("guideId") != "pulsar_gagne_20_340":
            raise ValueError("unexpected tracker identity")
        stages = tracker.get("stages")
        if not isinstance(stages, list) or len(stages) != 8:
            raise ValueError("tracker must cover the eight source stages")
        candidates: dict[str, Path] = {}
        for stage in stages:
            number = stage.get("number")
            if not isinstance(number, int) or number not in range(1, 9):
                raise ValueError("invalid source stage number")
            content_id = f"pulsar_gagne_20_340_stage_{number:02d}_instruction"
            if stage.get("contentId") != content_id or not stage.get("review", "").strip():
                raise ValueError("stage content identity or review is missing")
            asset_id = f"instruction_{content_id}_default"
            if asset_id in candidates:
                raise ValueError(f"duplicate stage: {content_id}")
            drawable = admitted_file({
                "path": f"app/src/main/res/drawable-nodpi/{asset_id}.webp", "hash": stage.get("sha256"),
            }, "path", "hash")
            candidates[asset_id] = drawable
        return candidates, []
    except (OSError, json.JSONDecodeError, ValueError, TypeError, AttributeError) as error:
        return {}, [f"Pulsar production admission: {error}"]


def load_generated_candidates() -> tuple[dict[str, Path], list[str]]:
    """Admit only individually reviewed generated scenes with complete, hash-bound provenance."""
    if not GENERATED_ADMISSION_PATH.is_file():
        return {}, []
    try:
        # Share the strict binding/source rules with the runtime generator.
        load_admission(ROOT, GENERATED_ADMISSION_PATH)
        manifest = json.loads(GENERATED_ADMISSION_PATH.read_text(encoding="utf-8"))
    except (OSError, ValueError, KeyError, TypeError) as error:
        return {}, [f"cannot load generated admission manifest ({error})"]
    if not isinstance(manifest, dict) or manifest.get("schema_version") != 1 or not isinstance(manifest.get("scenes"), list):
        return {}, ["generated admission requires schema_version 1 and a scenes list"]
    admitted: dict[str, Path] = {}
    seen_assets: set[str] = set()
    bound_content: set[str] = set()
    errors: list[str] = []
    for scene in manifest["scenes"]:
        asset_id = scene.get("asset_id") if isinstance(scene, dict) else None
        try:
            if not isinstance(asset_id, str) or RESOURCE_NAME.fullmatch(asset_id) is None:
                raise ValueError("invalid scene asset_id")
            if asset_id in seen_assets:
                raise ValueError("duplicate scene asset_id")
            seen_assets.add(asset_id)
            drawable = admitted_file(scene, "drawable_path", "resource_sha256")
            if drawable.parent != DRAWABLE_DIR.resolve() or drawable.name != f"{asset_id}.webp":
                raise ValueError("drawable_path must match the packaged asset_id")
            raw_source = admitted_file(scene, "raw_source_path", "raw_source_sha256")
            prompt = admitted_file(scene, "prompt_path", "prompt_sha256")
            if len({drawable, raw_source, prompt}) != 3:
                raise ValueError("raw generator output, exact prompt and imported drawable must be preserved separately")
            if not isinstance(scene.get("method_id"), str) or not scene["method_id"].strip():
                raise ValueError("missing method_id")
            review = scene.get("review", {})
            if not isinstance(review, dict) or review.get("verdict") != "approved":
                raise ValueError("scene does not have an approved review")
            if not isinstance(review.get("reviewer"), str) or not review["reviewer"].strip():
                raise ValueError("missing reviewer")
            date.fromisoformat(review.get("reviewed_on", ""))
            checks = review.get("checks")
            if not isinstance(checks, dict) or set(checks) != GENERATED_CHECKS or any(
                verdict != "pass" for verdict in checks.values()
            ):
                raise ValueError("every required pixel, phone, mechanics, accessibility and minimalism check must pass")
            notes = review.get("notes")
            if not isinstance(notes, list) or not notes or any(not isinstance(note, str) or not note.strip() for note in notes):
                raise ValueError("review requires recorded findings for its content bindings")
            bindings = scene.get("bindings")
            if not isinstance(bindings, list) or not bindings:
                raise ValueError("scene requires explicit reviewed content bindings")
            for binding in bindings:
                if not isinstance(binding, dict):
                    raise ValueError("content binding must be an object")
                content_id = binding.get("content_id")
                if not isinstance(content_id, str) or RESOURCE_NAME.fullmatch(content_id) is None:
                    raise ValueError("invalid bound content_id")
                if content_id in bound_content:
                    raise ValueError(f"ambiguous duplicate content binding: {content_id}")
                bound_content.add(content_id)
                for key in ("target_content_revision", "alt_text"):
                    if not isinstance(binding.get(key), str) or not binding[key].strip():
                        raise ValueError(f"content binding requires {key}")
            admitted[asset_id] = drawable
        except (ValueError, TypeError, OSError) as error:
            errors.append(f"{asset_id or '<missing scene id>'}: generated admission: {error}")
    return admitted, errors


def verify_asset(path: Path, approved_candidate: Path | None) -> list[str]:
    """Return every contract violation for one drawable."""
    errors: list[str] = []
    resource_name = path.stem
    if RESOURCE_NAME.fullmatch(resource_name) is None:
        errors.append(f"{path.name}: invalid Android resource name")

    encoded_size = path.stat().st_size
    if encoded_size > MAX_ENCODED_BYTES:
        errors.append(
            f"{path.name}: expected at most {MAX_ENCODED_BYTES:,} bytes, "
            f"got {encoded_size:,}",
        )

    try:
        with Image.open(path) as image:
            image.load()
            if image.format != "WEBP":
                errors.append(f"{path.name}: expected WebP, got {image.format or 'unknown'}")
            if image.size != EXPECTED_SIZE:
                errors.append(
                    f"{path.name}: expected {EXPECTED_SIZE[0]}x{EXPECTED_SIZE[1]}, "
                    f"got {image.width}x{image.height}",
                )
            if approved_candidate is None:
                if image.mode != "RGB":
                    errors.append(f"{path.name}: expected legacy opaque RGB, got {image.mode}")
            else:
                if image.mode != "RGBA":
                    errors.append(f"{path.name}: expected reviewed RGBA, got {image.mode}")
                else:
                    alpha = image.getchannel("A")
                    alpha_min, alpha_max = alpha.getextrema()
                    corners = (
                        alpha.getpixel((0, 0)),
                        alpha.getpixel((image.width - 1, 0)),
                        alpha.getpixel((0, image.height - 1)),
                        alpha.getpixel((image.width - 1, image.height - 1)),
                    )
                    if any(value != 0 for value in corners):
                        errors.append(f"{path.name}: transparent perimeter has an opaque corner")
                    if alpha_min != 0 or alpha_max != 255:
                        errors.append(
                            f"{path.name}: expected both transparent and opaque pixels, "
                            f"got alpha range {alpha_min}..{alpha_max}",
                        )
            if getattr(image, "is_animated", False):
                errors.append(f"{path.name}: instructional assets must be static")
    except (OSError, UnidentifiedImageError) as error:
        errors.append(f"{path.name}: cannot decode image ({error})")

    if approved_candidate is not None and sha256(path) != sha256(approved_candidate):
        errors.append(f"{path.name}: deployed bytes differ from the accepted tracker candidate")

    return errors


def main() -> int:
    assets = sorted(DRAWABLE_DIR.glob("instruction_*.webp"))
    if not assets:
        print("No instruction WebP assets found.", file=sys.stderr)
        return 1

    approved_candidates, errors = load_approved_candidates()
    pulsar_candidates, pulsar_errors = load_pulsar_candidates()
    approved_candidates.update(pulsar_candidates)
    errors.extend(pulsar_errors)
    generated_candidates, generated_errors = load_generated_candidates()
    errors.extend(generated_errors)
    for asset_id, drawable in generated_candidates.items():
        tracker_candidate = approved_candidates.get(asset_id)
        if tracker_candidate is not None and sha256(tracker_candidate) != sha256(drawable):
            errors.append(f"{asset_id}: generated admission conflicts with the tracker candidate")
        approved_candidates[asset_id] = drawable
    # Independent rejection must survive tracker acceptance and hash-valid packaging.
    # The shared validator checks exact stage/alt-text identity, every QC verdict,
    # and the actual packaged bytes before the runtime admission catalog is generated.
    visual_reviews: list[dict] = []
    try:
        visual_reviews = validate_review(load_json(DEFAULT_REVIEW), expected_assets(load_json(DEFAULT_GUIDANCE)))
    except ReviewError as error:
        errors.append(f"independent visual admission: {error}")
    deployed_names = {asset.stem for asset in assets}
    for retired_name in RETIRED_RESOURCE_NAMES & deployed_names:
        errors.append(f"{retired_name}: retired legacy instruction asset is packaged")
    for asset_id in approved_candidates.keys() - deployed_names:
        errors.append(f"{asset_id}: approved tracker asset has no deployed drawable")

    errors.extend(
        error
        for asset in assets
        for error in verify_asset(asset, approved_candidates.get(asset.stem))
    )
    if errors:
        print("Instruction asset verification failed:", file=sys.stderr)
        for error in errors:
            print(f"  - {error}", file=sys.stderr)
        return 1

    largest = max(assets, key=lambda asset: asset.stat().st_size)
    print(
        "Instruction asset verification passed: "
        f"{len(assets)} static WebP files at "
        f"{EXPECTED_SIZE[0]}x{EXPECTED_SIZE[1]}; largest is "
        f"{largest.name} ({largest.stat().st_size:,} bytes). "
        f"{len(approved_candidates)} reviewed delivery candidates are transparent; "
        f"{sum(review['verdict'] == 'approved' for review in visual_reviews)} independently approved, "
        f"{sum(review['verdict'] == 'rejected' for review in visual_reviews)} rejected and excluded from runtime admission; "
        f"{len(generated_candidates)} generated scenes have complete admission provenance; "
        f"{len(assets) - len(approved_candidates)} untracked review inputs are opaque.",
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
