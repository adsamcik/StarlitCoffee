#!/usr/bin/env python3
"""Audit localized guidance prose against the canonical terminology catalog."""

from __future__ import annotations

import argparse
import json
import re
import sys
import unicodedata
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / "tools"
if str(TOOLS) not in sys.path:
    sys.path.insert(0, str(TOOLS))

import generate_p1_exact_guidance_localizations as base
import generate_p1_exact_guidance_offline_drafts as drafts

GUIDANCE = ROOT / "app/src/main/assets/p1_exact_guidance_2026_07_27.json"
REFERENCES = ROOT / "app/src/main/assets/p1_exact_terminology_references_2026_07_27.json"
LOCALIZATIONS = ROOT / "docs/brewing/p1-exact-localizations.json"
OUTPUT = ROOT / "docs/brewing/p1-exact-terminology-prose-audit.json"
SPLIT_TERM = re.compile(r"\s*(?:/|／|\(|\)|\[|\]|;|,)\s*")
NON_WORD = re.compile(r"[^\w]+", re.UNICODE)
CJK = re.compile(r"[\u3400-\u9fff]")


class AuditError(RuntimeError):
    pass


def read_json(path: Path) -> dict:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (FileNotFoundError, json.JSONDecodeError) as error:
        raise AuditError(f"Cannot read {path}: {error}") from error


def normalize(value: str) -> str:
    return " ".join(NON_WORD.sub(" ", unicodedata.normalize("NFC", value).casefold()).split())


def variants(term: dict) -> list[str]:
    values: list[str] = []
    preferred = term.get("preferred_display_term")
    if isinstance(preferred, str):
        values.append(preferred)
        values.extend(part for part in SPLIT_TERM.split(preferred) if part)
    values.extend(item["term"] for item in term.get("accepted_alternatives", []))
    values.extend(term.get("inflected_or_adapted_forms", []))
    normalized = [normalize(value) for value in values if isinstance(value, str)]
    return list(dict.fromkeys(value for value in normalized if len(value) >= 2))


def avoided_variants(term: dict) -> list[str]:
    return list(dict.fromkeys(
        normalized
        for item in term.get("avoid_terms", [])
        if isinstance(item, dict) and isinstance(item.get("term"), str)
        if (normalized := normalize(item["term"]))
    ))


def string_leaves(value: object) -> list[str]:
    if isinstance(value, str):
        return [value]
    if isinstance(value, list):
        return [leaf for item in value for leaf in string_leaves(item)]
    if isinstance(value, dict):
        return [leaf for item in value.values() for leaf in string_leaves(item)]
    return []


def source_stage_by_content_id(guidance: dict) -> dict[str, dict]:
    stages: dict[str, dict] = {}
    for recipe in guidance["recipes"]:
        for stage in recipe["stages"]:
            content_id = f"p1_{recipe['recipe_id']}_{stage['stage_id']}_instruction"
            if content_id in stages:
                raise AuditError(f"Duplicate stage content ID: {content_id}")
            stages[content_id] = stage
    return stages


def translated_stage_text(stage: dict, translations: dict[str, str]) -> str:
    source_strings = string_leaves(stage)
    translated = [translations[value] for value in source_strings if value in translations]
    if not translated:
        raise AuditError("A referenced stage has no translated strings")
    return normalize(" ".join(translated))


def contains(text: str, candidate: str) -> bool:
    if CJK.search(candidate):
        return candidate in text
    return f" {candidate} " in f" {text} "


def generate() -> dict:
    guidance = read_json(GUIDANCE)
    references = read_json(REFERENCES)
    localizations = read_json(LOCALIZATIONS)
    locales = list(localizations["locales"])
    strings = base.translatable_strings(guidance)
    drafts.validate_memory(localizations, locales, strings)
    stages = source_stage_by_content_id(guidance)
    stage_references = references["stage_references"]
    if any(item["content_id"] not in stages for item in stage_references):
        raise AuditError("Terminology sidecar references an unknown guidance stage")

    locale_results: dict[str, dict] = {}
    total = Counter()
    for locale in locales:
        term_by_id = {term["concept_id"]: term for term in localizations["locales"][locale]["terminology"]["terms"]}
        translations = localizations["locales"][locale]["guidance"]["translations"]
        issues = []
        counts = Counter()
        for stage_reference in stage_references:
            content_id = stage_reference["content_id"]
            text = translated_stage_text(stages[content_id], translations)
            for concept_id in stage_reference["concept_ids"]:
                counts["concept_occurrences"] += 1
                term = term_by_id[concept_id]
                if term["display_policy"] == "withhold_pending_review":
                    counts["withheld_pending_review"] += 1
                    issues.append({
                        "content_id": content_id,
                        "concept_id": concept_id,
                        "result": "withheld_pending_review",
                    })
                    continue
                accepted = variants(term)
                matches = [value for value in accepted if contains(text, value)]
                avoided = [value for value in avoided_variants(term) if contains(text, value)]
                if matches:
                    counts["surface_form_match"] += 1
                else:
                    counts["native_review_required"] += 1
                if avoided:
                    counts["avoid_term_match"] += 1
                if not matches or avoided:
                    issues.append({
                        "content_id": content_id,
                        "concept_id": concept_id,
                        "result": "matched_with_avoid_term" if matches and avoided else "native_review_required",
                        "matched_forms": matches,
                        "avoid_term_matches": avoided,
                    })
        approved = localizations["locales"][locale]["terminology"]["status"] == "approved"
        if approved and (counts["native_review_required"] or counts["withheld_pending_review"] or counts["avoid_term_match"]):
            raise AuditError(f"{locale}: approved terminology has unresolved prose audit issues")
        locale_results[locale] = {
            "catalog_status": localizations["locales"][locale]["terminology"]["status"],
            "concept_occurrences": counts["concept_occurrences"],
            "surface_form_matches": counts["surface_form_match"],
            "withheld_pending_review": counts["withheld_pending_review"],
            "native_review_required": counts["native_review_required"],
            "avoid_term_matches": counts["avoid_term_match"],
            "issues": issues,
        }
        total.update(counts)
    return {
        "schema_version": 1,
        "source_sha256": guidance["source_sha256"],
        "terminology_research_records_sha256": localizations["terminology_research_records_sha256"],
        "audit_policy": {
            "purpose": "review_queue_not_machine_approval",
            "sentence_substitution": "forbidden",
            "draft_behavior": "report_surface_form_drift",
            "approved_behavior": "fail_on_unresolved_or_avoided_terms",
            "indirect_descriptions": "native_reviewer_must_resolve",
        },
        "summary": {
            "locale_count": len(locales),
            "concept_occurrences": total["concept_occurrences"],
            "surface_form_matches": total["surface_form_match"],
            "withheld_pending_review": total["withheld_pending_review"],
            "native_review_required": total["native_review_required"],
            "avoid_term_matches": total["avoid_term_match"],
        },
        "locales": locale_results,
    }


def encoded(value: dict) -> str:
    return json.dumps(value, ensure_ascii=False, indent=2) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    try:
        expected = generate()
        if args.check:
            if read_json(OUTPUT) != expected:
                raise AuditError("Checked-in terminology prose audit is stale")
            print("Validated terminology-to-prose audit for 22 locale drafts.")
            return 0
        OUTPUT.write_text(encoded(expected), encoding="utf-8", newline="\n")
        print(f"Wrote {OUTPUT}")
        return 0
    except (AuditError, KeyError, TypeError, ValueError) as error:
        print(error, file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())