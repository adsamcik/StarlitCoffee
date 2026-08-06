#!/usr/bin/env python3
"""Validate the canonical multilingual P1 terminology catalog."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LOCALE_CONFIG = ROOT / "app/src/main/res/xml/locales_config.xml"
REFERENCES = ROOT / "app/src/main/assets/p1_exact_terminology_references_2026_07_27.json"
CATALOG = ROOT / "docs/brewing/p1-exact-localizations.json"
RESEARCH_DIR = ROOT / "docs/brewing/research/terminology-2026-08-06"
RECORDS = RESEARCH_DIR / "coffee_brewing_terminology_records_2026-08-06.jsonl"
SOURCES = RESEARCH_DIR / "coffee_brewing_terminology_sources_2026-08-06.jsonl"
QC = RESEARCH_DIR / "coffee_brewing_terminology_qc_2026-08-06.json"
ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
RECORDS_SHA256 = "56ad15814e02d5bc6a6f33f0428d4f78bc878bd85724f258757caa5eaba89e6f"
LOCALE_MAP = {"zh-CN": "zh"}
CONCEPT_MAP = {"coffee_grounds": "grounds", "steeping_immersion": "steep_immersion"}
CLASSIFICATIONS = {
    "LOCALIZED_DOMINANT", "ENGLISH_DOMINANT", "MIXED_STABLE",
    "AUDIENCE_DEPENDENT", "CONTEXT_DEPENDENT", "REGION_DEPENDENT",
    "NO_ESTABLISHED_TERM", "INSUFFICIENT_EVIDENCE",
}
DISPLAY_POLICIES = {
    "localized_default", "established_english_default", "contextual_english",
    "region_and_context_required", "descriptive_only", "withhold_pending_review",
}
ENGLISH_POLICIES = {
    "glossary_and_search", "established_local_usage", "contextual_first_occurrence",
    "contextual_advanced_guidance", "contextual_when_relevant", "region_specific",
    "suppress_use_description", "suppress_pending_review",
}
LOCALE_STATUSES = {"researched_not_native_reviewed", "approved"}
DATE = re.compile(r"\d{4}-\d{2}-\d{2}")


class CatalogError(RuntimeError):
    pass


def read_json(path: Path) -> dict:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (FileNotFoundError, json.JSONDecodeError) as error:
        raise CatalogError(f"Cannot read {path}: {error}") from error


def read_jsonl(path: Path) -> list[dict]:
    try:
        return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]
    except (FileNotFoundError, json.JSONDecodeError) as error:
        raise CatalogError(f"Cannot read {path}: {error}") from error


def locales() -> list[str]:
    root = ET.parse(LOCALE_CONFIG).getroot()
    return [
        node.attrib[f"{{{ANDROID_NAMESPACE}}}name"]
        for node in root.findall("locale")
        if node.attrib[f"{{{ANDROID_NAMESPACE}}}name"] != "en"
    ]


def app_locale(value: str) -> str:
    return LOCALE_MAP.get(value, value)


def concept_id(value: str) -> str:
    return CONCEPT_MAP.get(value, value)


def validate() -> None:
    references = read_json(REFERENCES)
    catalog = read_json(CATALOG)
    research = read_jsonl(RECORDS)
    sources = read_jsonl(SOURCES)
    qc = read_json(QC)
    if hashlib.sha256(RECORDS.read_bytes()).hexdigest() != RECORDS_SHA256:
        raise CatalogError("Imported terminology research hash differs")
    if qc.get("errors") != []:
        raise CatalogError("Imported terminology research has QC errors")
    for key in ("source_schema_version", "source_execution_date", "source_sha256"):
        if catalog.get(key) != references.get(key):
            raise CatalogError(f"Catalog {key} differs from canonical guidance")
    if catalog.get("terminology_research_records_sha256") != RECORDS_SHA256:
        raise CatalogError("Catalog research provenance differs")
    standard = catalog.get("translation_standard", {})
    if standard.get("runtime_substitution") != "forbidden" or standard.get("sentence_strategy") != "translate_complete_natural_sentences":
        raise CatalogError("Catalog translation standard permits fragmented sentences")

    locale_ids = locales()
    localization_records = catalog.get("locales")
    locale_records = {
        locale: record.get("terminology")
        for locale, record in localization_records.items()
    } if isinstance(localization_records, dict) else None
    if not isinstance(locale_records, dict) or list(locale_records) != locale_ids or any(not isinstance(record, dict) for record in locale_records.values()):
        raise CatalogError("Catalog locale coverage or order differs from the app")
    canonical = {item["id"]: item["canonical_english"] for item in references["concepts"]}
    canonical_order = list(canonical)
    research_by_key = {(app_locale(item["locale"]), concept_id(item["concept_id"])): item for item in research}
    source_by_id = {item["id"]: item for item in sources}
    source_ids = set(source_by_id)
    if len(research_by_key) != 264 or len(source_ids) != 115:
        raise CatalogError("Research matrix or source register is incomplete")

    total_insufficient = 0
    for locale, locale_record in locale_records.items():
        status = locale_record.get("status")
        if status not in LOCALE_STATUSES:
            raise CatalogError(f"{locale}: invalid catalog status")
        terms = locale_record.get("terms")
        if not isinstance(terms, list) or [term.get("concept_id") for term in terms] != canonical_order:
            raise CatalogError(f"{locale}: concept coverage or order differs")
        if status == "approved":
            if not isinstance(locale_record.get("reviewer"), str) or not locale_record["reviewer"].strip():
                raise CatalogError(f"{locale}: approved catalog lacks a reviewer")
            if not isinstance(locale_record.get("reviewed_on"), str) or not DATE.fullmatch(locale_record["reviewed_on"]):
                raise CatalogError(f"{locale}: approved catalog lacks a review date")
        elif locale_record.get("reviewer") is not None or locale_record.get("reviewed_on") is not None:
            raise CatalogError(f"{locale}: unapproved catalog cannot claim native review")
        ui_copy = locale_record.get("ui_copy", {})
        if set(ui_copy) != {"show_english_terms", "hide_english_terms", "heading"} or any(
            not isinstance(value, str) or not value.strip() for value in ui_copy.values()
        ):
            raise CatalogError(f"{locale}: terminology UI copy is incomplete")

        insufficient = 0
        region_dependent = 0
        for term in terms:
            term_id = term["concept_id"]
            if term.get("canonical_english") != canonical[term_id]:
                raise CatalogError(f"{locale}/{term_id}: canonical English differs")
            if term.get("classification") not in CLASSIFICATIONS:
                raise CatalogError(f"{locale}/{term_id}: classification differs")
            if term.get("display_policy") not in DISPLAY_POLICIES or term.get("english_reference_policy") not in ENGLISH_POLICIES:
                raise CatalogError(f"{locale}/{term_id}: display policy differs")
            if term.get("review_status") not in {"researched_not_native_reviewed", "approved"}:
                raise CatalogError(f"{locale}/{term_id}: review status differs")
            evidence_ids = term.get("evidence_source_ids")
            if not isinstance(evidence_ids, list) or any(value not in source_ids for value in evidence_ids):
                raise CatalogError(f"{locale}/{term_id}: evidence source differs")
            if status == "approved" and len(set(evidence_ids)) < 2:
                raise CatalogError(f"{locale}/{term_id}: approved term lacks corroborating evidence")
            is_insufficient = term["classification"] == "INSUFFICIENT_EVIDENCE"
            insufficient += is_insufficient
            is_region_dependent = term["classification"] == "REGION_DEPENDENT"
            region_dependent += is_region_dependent
            if is_region_dependent:
                variants = term.get("regional_variants")
                if not isinstance(variants, list) or len(variants) < 2 or any(
                    not isinstance(variant, dict)
                    or not isinstance(variant.get("region"), str)
                    or not variant["region"].strip()
                    or not isinstance(variant.get("term"), str)
                    or not variant["term"].strip()
                    for variant in variants
                ):
                    raise CatalogError(f"{locale}/{term_id}: regional variants are incomplete")
            if is_insufficient:
                if term.get("preferred_display_term") is not None or term.get("display_policy") != "withhold_pending_review":
                    raise CatalogError(f"{locale}/{term_id}: insufficient term is exposed")
            elif not isinstance(term.get("preferred_display_term"), str) or not term["preferred_display_term"].strip():
                raise CatalogError(f"{locale}/{term_id}: preferred display term is missing")
            if status != "approved":
                source = research_by_key[(locale, term_id)]
                expected_preferred = None if source["classification"] == "INSUFFICIENT_EVIDENCE" else source["preferred_term"]
                if term.get("preferred_display_term") != expected_preferred or term.get("classification") != source["classification"] or term.get("confidence") != source["confidence"]:
                    raise CatalogError(f"{locale}/{term_id}: unreviewed term differs from researched evidence")
        if status == "approved":
            approved_source_ids = {
                source_id for term in terms for source_id in term["evidence_source_ids"]
            }
            if len({source_by_id[source_id]["source_type"] for source_id in approved_source_ids}) < 2:
                raise CatalogError(f"{locale}: approved terms lack source-category diversity")
        if insufficient != locale_record.get("insufficient_evidence_count"):
            raise CatalogError(f"{locale}: insufficient-evidence count differs")
        if status == "approved" and insufficient:
            raise CatalogError(f"{locale}: approved catalog retains insufficient evidence")
        if status == "approved" and region_dependent and "-" not in locale:
            raise CatalogError(
                f"{locale}: generic locale cannot approve region-dependent terminology",
            )
        total_insufficient += insufficient
    if total_insufficient != 40:
        raise CatalogError("Catalog insufficient-evidence total differs")
    counts = Counter(term["classification"] for locale in locale_records.values() for term in locale["terms"])
    if sum(counts.values()) != 264:
        raise CatalogError("Catalog term matrix differs")


def main() -> int:
    argparse.ArgumentParser(description=__doc__).parse_args()
    try:
        validate()
        print("Validated canonical terminology catalog: 22 locales, 264 concepts, 115 sources.")
        return 0
    except (CatalogError, KeyError, TypeError, ValueError, ET.ParseError) as error:
        print(error, file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
