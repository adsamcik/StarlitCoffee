#!/usr/bin/env python3
"""Generate locale review packets from the canonical terminology catalog."""

from __future__ import annotations

import argparse
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LOCALE_CONFIG = ROOT / "app/src/main/res/xml/locales_config.xml"
REFERENCES = ROOT / "app/src/main/assets/p1_exact_terminology_references_2026_07_27.json"
ENGLISH_GLOSSARY = ROOT / "app/src/main/res/raw/p1_exact_terminology.json"
CATALOG = ROOT / "docs/brewing/p1-exact-localizations.json"
SOURCE_REGISTER = ROOT / "docs/brewing/research/terminology-2026-08-06/coffee_brewing_terminology_sources_2026-08-06.jsonl"
OUTPUT_DIR = ROOT / "docs/brewing/p1-exact-terminology-review-packets"
ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
EXPECTED_LOCALE_COUNT = 23


class ReviewPacketError(RuntimeError):
    pass


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    return parser.parse_args()


def read_json(path: Path) -> dict:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (FileNotFoundError, json.JSONDecodeError) as error:
        raise ReviewPacketError(f"Cannot read {path}: {error}") from error


def read_jsonl(path: Path) -> list[dict]:
    try:
        return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]
    except (FileNotFoundError, json.JSONDecodeError) as error:
        raise ReviewPacketError(f"Cannot read {path}: {error}") from error


def locales() -> list[str]:
    root = ET.parse(LOCALE_CONFIG).getroot()
    values = [node.attrib[f"{{{ANDROID_NAMESPACE}}}name"] for node in root.findall("locale")]
    if len(values) != EXPECTED_LOCALE_COUNT or len(set(values)) != len(values):
        raise ReviewPacketError(f"Expected {EXPECTED_LOCALE_COUNT} unique locales")
    return values


def english_packet(references: dict) -> dict:
    glossary = read_json(ENGLISH_GLOSSARY)
    local_by_id = {term["concept_id"]: term["preferred_local"] for term in glossary["terms"]}
    return {
        "schema_version": 2,
        "source_schema_version": references["source_schema_version"],
        "source_execution_date": references["source_execution_date"],
        "source_sha256": references["source_sha256"],
        "locale": "en",
        "status": "approved",
        "research_completeness": "canonical_source",
        "ui_copy": glossary["ui_copy"],
        "sources": [{"id": "canonical_evidence_library", "title": "Canonical evidence library and implementation review", "category": "canonical_source"}],
        "concepts": [
            {
                "concept_id": concept["id"],
                "canonical_english": concept["canonical_english"],
                "preferred_terms": [local_by_id[concept["id"]]],
                "accepted_alternatives": [],
                "avoid": [],
                "rationale": "Canonical English source terminology.",
                "evidence_source_ids": ["canonical_evidence_library"],
                "classification": "CANONICAL_ENGLISH",
                "confidence": "HIGH",
                "display_policy": "canonical_language",
                "english_reference_policy": "not_applicable",
                "resolution": "approved",
            }
            for concept in references["concepts"]
        ],
        "reviewer": glossary["reviewer"],
        "reviewed_on": glossary["reviewed_on"],
        "blocking_requirements": [],
    }


def localized_packet(references: dict, catalog: dict, source_by_id: dict[str, dict], locale: str) -> dict:
    locale_record = catalog["locales"][locale]["terminology"]
    used_source_ids = list(dict.fromkeys(
        source_id
        for term in locale_record["terms"]
        for source_id in term["evidence_source_ids"]
    ))
    sources = []
    for source_id in used_source_ids:
        source = source_by_id[source_id]
        sources.append({
            "id": source["id"],
            "title": source["title"],
            "url": source["url"],
            "category": source["source_type"],
            "audience": source["audience"],
            "region": source["country_or_region"],
            "original_or_translated": source["original_or_translated"],
            "source_flags": source["source_flags"],
        })
    concepts = []
    for term in locale_record["terms"]:
        concepts.append({
            "concept_id": term["concept_id"],
            "canonical_english": term["canonical_english"],
            "preferred_terms": [] if term["preferred_display_term"] is None else [term["preferred_display_term"]],
            "accepted_alternatives": [item["term"] for item in term["accepted_alternatives"]],
            "inflected_or_adapted_forms": term["inflected_or_adapted_forms"],
            "avoid": [item["term"] for item in term["avoid_terms"]],
            "avoid_reasons": term["avoid_terms"],
            "regional_variants": term["regional_variants"],
            "rationale": f"Beginner: {term['beginner_description']} Professional: {term['professional_usage']}",
            "evidence_source_ids": term["evidence_source_ids"],
            "classification": term["classification"],
            "confidence": term["confidence"],
            "display_policy": term["display_policy"],
            "english_reference_policy": term["english_reference_policy"],
            "resolution": "insufficient_evidence" if term["preferred_display_term"] is None else locale_record["status"],
        })
    blocking = []
    if locale_record["insufficient_evidence_count"]:
        blocking.append(f"resolve {locale_record['insufficient_evidence_count']} insufficient-evidence terminology records")
    blocking.extend([
        "obtain independent native coffee-domain approval of every terminology record",
        "complete native editorial review of all exact-guidance sentences",
        "promote guidance and terminology resources atomically",
        "validate accessibility, text scaling, layout, and both themes on device",
    ])
    return {
        "schema_version": 2,
        "source_schema_version": references["source_schema_version"],
        "source_execution_date": references["source_execution_date"],
        "source_sha256": references["source_sha256"],
        "research_execution_date": catalog["terminology_research_execution_date"],
        "research_records_sha256": catalog["terminology_research_records_sha256"],
        "locale": locale,
        "status": locale_record["status"],
        "research_completeness": locale_record["research_completeness"],
        "ui_copy": locale_record["ui_copy"],
        "sources": sources,
        "concepts": concepts,
        "reviewer": locale_record["reviewer"],
        "reviewed_on": locale_record["reviewed_on"],
        "blocking_requirements": blocking,
    }


def packets() -> dict[str, dict]:
    references = read_json(REFERENCES)
    catalog = read_json(CATALOG)
    source_by_id = {source["id"]: source for source in read_jsonl(SOURCE_REGISTER)}
    if set(catalog.get("locales", {})) != set(locales()) - {"en"}:
        raise ReviewPacketError("Terminology catalog locale coverage differs")
    return {
        locale: english_packet(references) if locale == "en" else localized_packet(references, catalog, source_by_id, locale)
        for locale in locales()
    }


def encoded(document: dict) -> str:
    return json.dumps(document, ensure_ascii=False, indent=2) + "\n"


def main() -> int:
    args = parse_args()
    try:
        expected = packets()
        if args.check:
            if {path.stem for path in OUTPUT_DIR.glob("*.json")} != set(expected):
                raise ReviewPacketError("Terminology review packet locale set is stale")
            for locale, document in expected.items():
                if (OUTPUT_DIR / f"{locale}.json").read_text(encoding="utf-8") != encoded(document):
                    raise ReviewPacketError(f"Terminology review packet is stale: {locale}")
            print(f"P1 terminology review packets are current ({len(expected)} locales).")
            return 0
        OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
        for locale, document in expected.items():
            (OUTPUT_DIR / f"{locale}.json").write_text(encoded(document), encoding="utf-8", newline="\n")
        print(f"Wrote {len(expected)} terminology review packets to {OUTPUT_DIR}")
        return 0
    except (ReviewPacketError, KeyError, TypeError, ValueError, ET.ParseError) as error:
        print(error, file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())