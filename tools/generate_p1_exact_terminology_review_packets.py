#!/usr/bin/env python3
"""Generate one terminology review packet for every declared app locale."""

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
ENGLISH_GLOSSARY = ROOT / "app/src/main/res/raw/p1_exact_terminology.json"
OFFLINE_DRAFTS = ROOT / "docs/brewing/p1-exact-terminology-offline-drafts.json"
EDITORIAL_REVIEW_DIR = ROOT / "docs/brewing/p1-exact-guidance-editorial-reviews"
OUTPUT_DIR = ROOT / "docs/brewing/p1-exact-terminology-review-packets"
ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
EXPECTED_LOCALE_COUNT = 23


class ReviewPacketError(RuntimeError):
    """All-locale terminology review packets could not be generated safely."""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--check",
        action="store_true",
        help="verify every checked-in packet without writing",
    )
    return parser.parse_args()


def read_json(path: Path) -> dict:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (FileNotFoundError, json.JSONDecodeError) as error:
        raise ReviewPacketError(f"Cannot read {path}: {error}") from error


def locales() -> list[str]:
    try:
        root = ET.parse(LOCALE_CONFIG).getroot()
    except (FileNotFoundError, ET.ParseError) as error:
        raise ReviewPacketError(f"Cannot read locale config: {error}") from error
    values = [
        element.attrib[f"{{{ANDROID_NAMESPACE}}}name"]
        for element in root.findall("locale")
    ]
    if len(values) != EXPECTED_LOCALE_COUNT or len(set(values)) != len(values):
        raise ReviewPacketError(
            f"Expected {EXPECTED_LOCALE_COUNT} unique locales, found {len(values)}",
        )
    return values


def unresolved_concept(concept: dict) -> dict:
    return {
        "concept_id": concept["id"],
        "canonical_english": concept["canonical_english"],
        "preferred_terms": [],
        "accepted_alternatives": [],
        "avoid": [],
        "rationale": None,
        "evidence_source_ids": [],
        "resolution": "unresolved",
    }


def english_packet(references: dict) -> dict:
    glossary = read_json(ENGLISH_GLOSSARY)
    local_by_id = {
        term["concept_id"]: term["preferred_local"]
        for term in glossary["terms"]
    }
    return {
        "schema_version": 1,
        "source_schema_version": references["source_schema_version"],
        "source_execution_date": references["source_execution_date"],
        "source_sha256": references["source_sha256"],
        "locale": "en",
        "status": "approved",
        "ui_copy": glossary["ui_copy"],
        "sources": [
            {
                "id": "canonical_evidence_library",
                "title": "Canonical evidence library and implementation review",
                "category": "canonical_source",
            },
        ],
        "concepts": [
            {
                "concept_id": concept["id"],
                "canonical_english": concept["canonical_english"],
                "preferred_terms": [local_by_id[concept["id"]]],
                "accepted_alternatives": [],
                "avoid": [],
                "rationale": "Canonical English source terminology.",
                "evidence_source_ids": ["canonical_evidence_library"],
                "resolution": "approved",
            }
            for concept in references["concepts"]
        ],
        "reviewer": glossary["reviewer"],
        "reviewed_on": glossary["reviewed_on"],
        "blocking_requirements": [],
    }


def czech_packet(references: dict) -> dict:
    review = read_json(EDITORIAL_REVIEW_DIR / "cs.json")
    terminology = review["terminology_review"]
    concepts = terminology["concepts"]
    return {
        "schema_version": 1,
        "source_schema_version": references["source_schema_version"],
        "source_execution_date": references["source_execution_date"],
        "source_sha256": references["source_sha256"],
        "locale": "cs",
        "status": terminology["status"],
        "ui_copy": terminology["ui_copy"],
        "sources": terminology["sources"],
        "concepts": [
            {
                "concept_id": concept["id"],
                "canonical_english": concept["canonical_english"],
                **concepts[concept["id"]],
                "resolution": terminology["status"],
            }
            for concept in references["concepts"]
        ],
        "reviewer": terminology.get("reviewer"),
        "reviewed_on": terminology.get("reviewed_on"),
        "blocking_requirements": [
            "independent native Czech coffee-domain approval",
            "approval date and reviewer identity",
            "complete exact-guidance approval and promotion",
            "device accessibility and theme validation",
        ],
    }


def local_draft_packet(references: dict, drafts: dict, locale: str) -> dict:
    draft = drafts["locales"][locale]
    local_by_id = {
        term["concept_id"]: term["preferred_local"]
        for term in draft["terms"]
    }
    return {
        "schema_version": 1,
        "source_schema_version": references["source_schema_version"],
        "source_execution_date": references["source_execution_date"],
        "source_sha256": references["source_sha256"],
        "locale": locale,
        "status": "local_draft_complete",
        "ui_copy": draft["ui_copy"],
        "sources": [],
        "concepts": [
            {
                "concept_id": concept["id"],
                "canonical_english": concept["canonical_english"],
                "preferred_terms": [local_by_id[concept["id"]]],
                "accepted_alternatives": [],
                "avoid": [],
                "rationale": (
                    "Source-aware local draft; independent local-market "
                    "terminology evidence and native review are still required."
                ),
                "evidence_source_ids": [],
                "resolution": "local_draft_complete",
            }
            for concept in references["concepts"]
        ],
        "reviewer": None,
        "reviewed_on": None,
        "blocking_requirements": [
            "add at least two corroborating local-market sources across two categories",
            "review preferred, accepted, and avoided terminology",
            "obtain independent native coffee-domain approval",
            "complete exact-guidance review, promotion, and device validation",
        ],
    }


def research_packet(references: dict, locale: str) -> dict:
    return {
        "schema_version": 1,
        "source_schema_version": references["source_schema_version"],
        "source_execution_date": references["source_execution_date"],
        "source_sha256": references["source_sha256"],
        "locale": locale,
        "status": "research_required",
        "ui_copy": {
            "show_english_terms": None,
            "hide_english_terms": None,
            "heading": None,
        },
        "sources": [],
        "concepts": [
            unresolved_concept(concept)
            for concept in references["concepts"]
        ],
        "reviewer": None,
        "reviewed_on": None,
        "blocking_requirements": [
            "add at least two corroborating local-market sources across two categories",
            "resolve all preferred, accepted, and avoided terminology",
            "localize the contextual-control copy",
            "obtain independent native coffee-domain approval",
            "complete exact-guidance review, promotion, and device validation",
        ],
    }


def packets() -> dict[str, dict]:
    references = read_json(REFERENCE_MANIFEST)
    concepts = references.get("concepts")
    if not isinstance(concepts, list) or not concepts:
        raise ReviewPacketError("Canonical terminology concepts are missing")
    drafts = read_json(OFFLINE_DRAFTS)
    if drafts.get("source_sha256") != references.get("source_sha256"):
        raise ReviewPacketError("Offline terminology drafts belong to another source")
    result: dict[str, dict] = {}
    for locale in locales():
        if locale == "en":
            result[locale] = english_packet(references)
        elif locale == "cs":
            result[locale] = czech_packet(references)
        else:
            result[locale] = local_draft_packet(references, drafts, locale)
    return result


def encoded(document: dict) -> str:
    return json.dumps(document, ensure_ascii=False, indent=2) + "\n"


def main() -> int:
    args = parse_args()
    try:
        expected = packets()
        if args.check:
            actual_files = {path.stem for path in OUTPUT_DIR.glob("*.json")}
            if actual_files != set(expected):
                raise ReviewPacketError("Terminology review packet locale set is stale")
            for locale, document in expected.items():
                path = OUTPUT_DIR / f"{locale}.json"
                if path.read_text(encoding="utf-8") != encoded(document):
                    raise ReviewPacketError(f"Terminology review packet is stale: {locale}")
            print(f"P1 terminology review packets are current ({len(expected)} locales).")
            return 0

        OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
        for existing in OUTPUT_DIR.glob("*.json"):
            if existing.stem not in expected:
                existing.unlink()
        for locale, document in expected.items():
            (OUTPUT_DIR / f"{locale}.json").write_text(
                encoded(document),
                encoding="utf-8",
            )
        print(f"Wrote {len(expected)} terminology review packets to {OUTPUT_DIR}")
        return 0
    except (ReviewPacketError, KeyError, TypeError) as error:
        print(error, file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())