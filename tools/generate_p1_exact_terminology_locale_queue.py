#!/usr/bin/env python3
"""Generate the all-locale terminology and exact-guidance readiness ledger."""

from __future__ import annotations

import argparse
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LOCALE_CONFIG = ROOT / "app/src/main/res/xml/locales_config.xml"
REFERENCE_MANIFEST = (
    ROOT / "app/src/main/assets/p1_exact_terminology_references_2026_07_27.json"
)
REVIEW_LEDGER = ROOT / "docs/brewing/p1-exact-guidance-reviewed-locales.json"
EDITORIAL_REVIEW_DIR = ROOT / "docs/brewing/p1-exact-guidance-editorial-reviews"
REVIEW_PACKET_DIR = ROOT / "docs/brewing/p1-exact-terminology-review-packets"
EXACT_DRAFTS = ROOT / "docs/brewing/p1-exact-guidance-offline-draft-translation-memory.json"
TERMINOLOGY_DRAFTS = ROOT / "docs/brewing/p1-exact-terminology-offline-drafts.json"
OUTPUT = ROOT / "docs/brewing/p1-exact-terminology-locale-queue.json"
ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
EXPECTED_LOCALE_COUNT = 23
EXPECTED_RECIPE_COUNT = 20
EXPECTED_STAGE_COUNT = 114


class LocaleQueueError(RuntimeError):
    """The locale readiness ledger could not be generated consistently."""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--check",
        action="store_true",
        help="verify the checked-in ledger without writing",
    )
    return parser.parse_args()


def read_json(path: Path) -> dict:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (FileNotFoundError, json.JSONDecodeError) as error:
        raise LocaleQueueError(f"Cannot read {path}: {error}") from error


def configured_locales() -> list[str]:
    try:
        root = ET.parse(LOCALE_CONFIG).getroot()
    except (FileNotFoundError, ET.ParseError) as error:
        raise LocaleQueueError(f"Cannot read locale config: {error}") from error
    locales = [
        element.attrib[f"{{{ANDROID_NAMESPACE}}}name"]
        for element in root.findall("locale")
    ]
    if len(locales) != EXPECTED_LOCALE_COUNT or len(set(locales)) != len(locales):
        raise LocaleQueueError(
            f"Expected {EXPECTED_LOCALE_COUNT} unique app locales, found {len(locales)}",
        )
    return locales


def resource_path(locale: str, name: str) -> Path:
    qualifier = "raw" if locale == "en" else f"raw-{locale}"
    return ROOT / "app/src/main/res" / qualifier / name


def editorial_state(locale: str) -> tuple[str | None, str | None]:
    path = EDITORIAL_REVIEW_DIR / f"{locale}.json"
    if not path.exists():
        return None, None
    review = read_json(path)
    terminology = review.get("terminology_review")
    terminology_status = (
        terminology.get("status") if isinstance(terminology, dict) else None
    )
    return review.get("status"), terminology_status


def missing_requirements(
    locale: str,
    exact_status: str,
    terminology_status: str,
    production_ready: bool,
) -> list[str]:
    if production_ready:
        return []
    missing: list[str] = []
    if terminology_status == "research_required":
        missing.extend(
            [
                "local-market terminology sources",
                "preferred, accepted, and avoided terms for every canonical concept",
                "localized contextual-control copy",
            ],
        )
    elif terminology_status == "local_draft_complete":
        missing.extend(
            [
                "local-market terminology sources",
                "native review of preferred, accepted, and avoided terminology",
            ],
        )
    if exact_status == "not_started":
        missing.append("complete 114-stage source-bound editorial review")
    elif exact_status == "local_draft_complete":
        missing.append("native editorial review of all 20 recipes and 114 stages")
    if exact_status != "approved" or terminology_status != "approved":
        missing.append("independent native coffee-domain approval")
    missing.extend(
        [
            "reviewed-locale ledger approval",
            "atomic guidance and terminology resource promotion",
            "exact-recipe production coverage registration",
            "device accessibility, text-scaling, and theme validation",
        ],
    )
    return list(dict.fromkeys(missing))


def generate() -> dict:
    locales = configured_locales()
    references = read_json(REFERENCE_MANIFEST)
    concepts = references.get("concepts")
    if not isinstance(concepts, list) or not concepts:
        raise LocaleQueueError("Canonical terminology concepts are missing")
    concept_ids = [concept.get("id") for concept in concepts]
    if len(concept_ids) != len(set(concept_ids)):
        raise LocaleQueueError("Canonical terminology concepts are duplicated")

    ledger = read_json(REVIEW_LEDGER)
    approvals = ledger.get("locales", {})
    exact_drafts = read_json(EXACT_DRAFTS).get("translations", {})
    terminology_drafts = read_json(TERMINOLOGY_DRAFTS).get("locales", {})
    records: list[dict] = []
    for locale in locales:
        editorial_status, terminology_status = editorial_state(locale)
        approval = approvals.get(locale)
        ledger_approved = isinstance(approval, dict) and approval.get("status") == "approved"
        if locale == "en":
            exact_status = "approved"
            terminology_status = "approved"
        else:
            exact_status = editorial_status or (
                "local_draft_complete" if locale in exact_drafts else "not_started"
            )
            terminology_status = terminology_status or (
                "local_draft_complete"
                if locale in terminology_drafts
                else "research_required"
            )
        packet_path = REVIEW_PACKET_DIR / f"{locale}.json"
        packet = read_json(packet_path)
        if packet.get("locale") != locale:
            raise LocaleQueueError(f"Terminology review packet locale differs: {locale}")
        if packet.get("source_sha256") != references.get("source_sha256"):
            raise LocaleQueueError(f"Terminology review packet source differs: {locale}")
        packet_concept_ids = [
            concept.get("concept_id")
            for concept in packet.get("concepts", [])
            if isinstance(concept, dict)
        ]
        if packet_concept_ids != concept_ids:
            raise LocaleQueueError(f"Terminology review packet concepts differ: {locale}")
        if packet.get("status") != terminology_status:
            raise LocaleQueueError(f"Terminology review packet status differs: {locale}")
        guidance_resource = resource_path(locale, "p1_exact_guidance.json").is_file()
        terminology_resource = resource_path(locale, "p1_exact_terminology.json").is_file()
        production_ready = all(
            (
                ledger_approved,
                exact_status == "approved",
                terminology_status == "approved",
                guidance_resource,
                terminology_resource,
            ),
        )
        records.append(
            {
                "locale": locale,
                "exact_guidance_status": exact_status,
                "terminology_status": terminology_status,
                "canonical_concept_ids": concept_ids,
                "review_packet": packet_path.relative_to(ROOT).as_posix(),
                "recipe_count": EXPECTED_RECIPE_COUNT,
                "stage_count": EXPECTED_STAGE_COUNT,
                "guidance_resource_present": guidance_resource,
                "terminology_resource_present": terminology_resource,
                "ledger_approved": ledger_approved,
                "production_ready": production_ready,
                "missing_requirements": missing_requirements(
                    locale,
                    exact_status,
                    terminology_status,
                    production_ready,
                ),
            },
        )

    return {
        "schema_version": 1,
        "source_schema_version": references["source_schema_version"],
        "source_execution_date": references["source_execution_date"],
        "source_sha256": references["source_sha256"],
        "locale_count": len(locales),
        "canonical_concept_count": len(concept_ids),
        "locales": records,
    }


def encoded(document: dict) -> str:
    return json.dumps(document, ensure_ascii=False, indent=2) + "\n"


def main() -> int:
    args = parse_args()
    try:
        expected = encoded(generate())
        if args.check:
            try:
                actual = OUTPUT.read_text(encoding="utf-8")
            except FileNotFoundError as error:
                raise LocaleQueueError(f"Missing locale ledger: {OUTPUT}") from error
            if actual != expected:
                raise LocaleQueueError("Checked-in terminology locale ledger is stale")
            print(f"P1 terminology locale ledger is current ({EXPECTED_LOCALE_COUNT} locales).")
            return 0
        OUTPUT.write_text(expected, encoding="utf-8")
        print(f"Wrote {OUTPUT}")
        return 0
    except LocaleQueueError as error:
        print(error, file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())