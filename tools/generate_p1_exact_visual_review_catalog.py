#!/usr/bin/env python3
"""Validate the independent exact-stage pixel ledger and generate its runtime catalogue."""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_GUIDANCE = ROOT / "app/src/main/assets/p1_exact_guidance_2026_07_27.json"
DEFAULT_REVIEW = ROOT / "docs/brewing/p1-exact-independent-visual-review-2026-08-21.json"
DEFAULT_OUTPUT = (
    ROOT
    / "app/src/main/java/com/adsamcik/starlitcoffee/ui/guidance/"
    "P1ExactIndependentVisualReviewCatalog.kt"
)
DRAWABLES = ROOT / "app/src/main/res/drawable-nodpi"
REQUIRED_CHECKS = (
    "full_resolution",
    "phone_light",
    "phone_dark",
    "equipment_geometry",
    "action_state",
    "hand_safety",
    "crop_and_flow",
    "alt_text_agreement",
)
CHECK_VERDICTS = frozenset({"pass", "fail"})


class ReviewError(ValueError):
    """The visual review cannot safely authorize runtime artwork."""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--guidance", type=Path, default=DEFAULT_GUIDANCE)
    parser.add_argument("--review", type=Path, default=DEFAULT_REVIEW)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument(
        "--check",
        action="store_true",
        help="fail when the generated Kotlin catalogue differs from the review ledger",
    )
    return parser.parse_args()


def load_json(path: Path) -> dict:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError as error:
        raise ReviewError(f"Missing input: {path}") from error
    except json.JSONDecodeError as error:
        raise ReviewError(f"Invalid JSON in {path}: {error}") from error


def require_text(record: dict, key: str) -> str:
    value = record.get(key)
    if not isinstance(value, str) or not value.strip():
        raise ReviewError(f"Expected non-empty string '{key}' in {record}")
    return value


def expected_assets(guidance: dict) -> dict[str, tuple[str, str, str]]:
    expected: dict[str, tuple[str, str, str]] = {}
    for recipe in guidance.get("recipes", []):
        recipe_id = require_text(recipe, "recipe_id")
        for stage in recipe.get("stages", []):
            source_stage_id = require_text(stage, "stage_id")
            identity = f"p1_{recipe_id}_{source_stage_id}"
            asset_id = f"instruction_{identity}_instruction_default"
            if asset_id in expected:
                raise ReviewError(f"Duplicate guidance asset identity: {asset_id}")
            try:
                accessible_alt_text = require_text(
                    stage["guidance"]["full"],
                    "accessible_alt_text",
                )
            except (KeyError, TypeError) as error:
                raise ReviewError(
                    f"Canonical guidance lacks accessible alt text for {asset_id}"
                ) from error
            expected[asset_id] = (recipe_id, identity, accessible_alt_text)
    if not expected:
        raise ReviewError("Canonical guidance contains no exact-stage assets")
    return expected


def validate_review(
    review: dict,
    expected: dict[str, tuple[str, str, str]],
) -> list[dict]:
    if review.get("schema_version") != 1:
        raise ReviewError("Independent visual review schema_version must be 1")
    reviewer = review.get("reviewer")
    if not isinstance(reviewer, dict):
        raise ReviewError("Independent visual review requires reviewer metadata")
    require_text(reviewer, "name")
    require_text(reviewer, "independence_basis")
    require_text(review, "reviewed_on")
    context = review.get("review_context")
    if not isinstance(context, dict):
        raise ReviewError("Independent visual review requires review_context")
    if context.get("native_resolution_px") != [1024, 768]:
        raise ReviewError("Native review must cover the packaged 1024x768 resources")
    if context.get("phone_card_px") != [360, 280]:
        raise ReviewError("Phone review must use the documented 360x280 card")

    records = review.get("assets")
    if not isinstance(records, list):
        raise ReviewError("Independent visual review assets must be a list")
    by_id: dict[str, dict] = {}
    for record in records:
        if not isinstance(record, dict):
            raise ReviewError("Independent visual review records must be objects")
        asset_id = require_text(record, "asset_id")
        if asset_id in by_id:
            raise ReviewError(f"Duplicate visual review asset ID: {asset_id}")
        by_id[asset_id] = record

    missing = expected.keys() - by_id.keys()
    unexpected = by_id.keys() - expected.keys()
    if missing or unexpected:
        raise ReviewError(
            f"Visual review coverage mismatch: missing={sorted(missing)}, "
            f"unexpected={sorted(unexpected)}"
        )

    for asset_id, record in by_id.items():
        recipe_id, stage_id, accessible_alt_text = expected[asset_id]
        if record.get("recipe_id") != recipe_id or record.get("stage_id") != stage_id:
            raise ReviewError(f"Recipe/stage identity mismatch for {asset_id}")
        if record.get("accessible_alt_text") != accessible_alt_text:
            raise ReviewError(f"Reviewed alt text no longer matches guidance for {asset_id}")
        drawable = DRAWABLES / f"{asset_id}.webp"
        if not drawable.is_file():
            raise ReviewError(f"Reviewed packaged drawable is missing: {drawable}")
        actual_hash = hashlib.sha256(drawable.read_bytes()).hexdigest()
        if record.get("resource_sha256") != actual_hash:
            raise ReviewError(f"Reviewed SHA-256 no longer matches {asset_id}")
        checks = record.get("checks")
        if not isinstance(checks, dict) or tuple(checks.keys()) != REQUIRED_CHECKS:
            raise ReviewError(
                f"Review checks for {asset_id} must be ordered exactly as {REQUIRED_CHECKS}"
            )
        if any(value not in CHECK_VERDICTS for value in checks.values()):
            raise ReviewError(f"Review checks for {asset_id} must use pass/fail")
        approved = all(value == "pass" for value in checks.values())
        expected_verdict = "approved" if approved else "rejected"
        if record.get("verdict") != expected_verdict:
            raise ReviewError(f"Verdict does not match checks for {asset_id}")
        findings = record.get("findings")
        if not isinstance(findings, list) or any(
            not isinstance(finding, str) or not finding.strip() for finding in findings
        ):
            raise ReviewError(f"Findings for {asset_id} must be a string list")
        if not approved and not findings:
            raise ReviewError(f"Rejected visual review requires a finding: {asset_id}")

    summary = review.get("summary")
    approved_count = sum(record["verdict"] == "approved" for record in records)
    rejected_count = len(records) - approved_count
    expected_summary = {
        "asset_count": len(records),
        "approved_count": approved_count,
        "rejected_count": rejected_count,
    }
    if summary != expected_summary:
        raise ReviewError(f"Visual review summary must be {expected_summary}")
    return sorted(records, key=lambda record: record["asset_id"])


def kotlin_string(value: str) -> str:
    return json.dumps(value, ensure_ascii=False)


def render(review: dict, records: list[dict]) -> str:
    reviewer = review["reviewer"]["name"]
    reviewed_on = review["reviewed_on"]
    lines = [
        "// Generated by tools/generate_p1_exact_visual_review_catalog.py; do not edit.",
        "package com.adsamcik.starlitcoffee.ui.guidance",
        "",
        "import com.adsamcik.starlitcoffee.domain.brewing.InstructionAssetId",
        "import java.time.LocalDate",
        "",
        "private val independentVisualReviews: List<P1ExactInstructionVisualReview> = listOf(",
    ]
    for record in records:
        checks = record["checks"]
        mechanics = all(
            checks[key] == "pass"
            for key in ("equipment_geometry", "action_state", "hand_safety", "crop_and_flow")
        )
        lines.extend(
            [
                "    P1ExactInstructionVisualReview(",
                f"        assetId = InstructionAssetId({kotlin_string(record['asset_id'])}),",
                f"        resourceSha256 = {kotlin_string(record['resource_sha256'])},",
                "        reviewer = P1ExactIndependentVisualReviewCatalog.REVIEWER,",
                "        reviewedOn = LocalDate.parse(",
                "            P1ExactIndependentVisualReviewCatalog.REVIEWED_ON,",
                "        ),",
                f"        fullResolutionReviewed = {str(checks['full_resolution'] == 'pass').lower()},",
                "        phoneScaleReviewed = "
                f"{str(checks['phone_light'] == 'pass' and checks['phone_dark'] == 'pass').lower()},",
                f"        mechanicsReviewed = {str(mechanics).lower()},",
                "        altTextReviewed = "
                f"{str(checks['alt_text_agreement'] == 'pass').lower()},",
                "    ),",
            ]
        )
    lines.extend(
        [
            ")",
            "",
            "/** Hash-bound independent pixel verdicts for every packaged exact-stage drawable. */",
            "object P1ExactIndependentVisualReviewCatalog {",
            f"    const val REVIEWER = {kotlin_string(reviewer)}",
            f"    const val REVIEWED_ON = {kotlin_string(reviewed_on)}",
            "    const val REVIEW_PATH =",
            "        \"docs/brewing/p1-exact-independent-visual-review-2026-08-21.json\"",
            "",
            "    val reviews: List<P1ExactInstructionVisualReview> = independentVisualReviews",
            "}",
            "",
        ]
    )
    return "\n".join(lines)


def main() -> int:
    args = parse_args()
    guidance = load_json(args.guidance.resolve())
    review = load_json(args.review.resolve())
    records = validate_review(review, expected_assets(guidance))
    output = render(review, records)
    output_path = args.output.resolve()
    if args.check:
        try:
            current = output_path.read_text(encoding="utf-8")
        except FileNotFoundError:
            print(f"Missing generated file: {output_path}", file=sys.stderr)
            return 1
        if current != output:
            print(f"Generated visual review catalogue is stale: {output_path}", file=sys.stderr)
            return 1
        print(
            "Independent P1 visual review catalogue is current "
            f"({len(records)} assets; {review['summary']['approved_count']} approved; "
            f"{review['summary']['rejected_count']} rejected)."
        )
        return 0
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(output, encoding="utf-8")
    print(f"Wrote {output_path}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except ReviewError as error:
        print(f"error: {error}", file=sys.stderr)
        raise SystemExit(1)
