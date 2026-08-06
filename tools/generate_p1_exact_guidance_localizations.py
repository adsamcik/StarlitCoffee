#!/usr/bin/env python3
"""Generate draft and validate reviewed locale-qualified exact P1 guidance."""

from __future__ import annotations

import argparse
import copy
import hashlib
import json
import re
import sys
import time
import unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/assets/p1_exact_guidance_2026_07_27.json"
RES = ROOT / "app/src/main/res"
DRAFT_ROOT = ROOT / "build/p1-exact-guidance-localization-drafts"
DRAFT_MEMORY = DRAFT_ROOT / "translation-memory.json"
REVIEWED_MEMORY = ROOT / "docs/brewing/p1-exact-guidance-translation-memory.json"
REVIEW_LEDGER = ROOT / "docs/brewing/p1-exact-guidance-reviewed-locales.json"
EDITORIAL_REVIEW_DIR = ROOT / "docs/brewing/p1-exact-guidance-editorial-reviews"
TERMINOLOGY_REFERENCE_MANIFEST = (
    ROOT / "app/src/main/assets/p1_exact_terminology_references_2026_07_27.json"
)
LOCALIZATION_SOURCE = ROOT / "docs/brewing/p1-exact-localizations.json"
LOCALES = (
    "en", "bg", "cs", "da", "de", "el", "es", "et", "fi", "fr", "hr", "hu",
    "it", "lt", "lv", "nl", "pl", "pt", "ro", "sk", "sl", "sv", "zh",
)
GOOGLE_TARGET = {"zh": "zh-CN"}
RECIPE_TEXT_KEYS = ("recipe_name", "recipe_approach")
STAGE_TEXT_KEYS = (
    "action", "start_time_or_preceding_condition", "target_duration_or_range",
    "added_water_target", "cumulative_water_target", "beverage_yield_target",
    "equipment_state", "completion_criterion", "observable_signs", "optional_tip", "warning",
)
FULL_TEXT_KEYS = (
    "imperative_instruction", "concise_explanation", "optional_practical_tip", "warning",
    "observable_completion_cue", "accessible_alt_text",
)
CONCISE_TEXT_KEYS = (
    "current_action", "current_target", "completion_cue", "essential_warning",
)
FOCUSED_TEXT_KEYS = ("action_label", "numerical_or_state_target", "next_action")
# One token includes decimal punctuation or locale-inserted thousands spacing;
# times such as 0:30 intentionally remain two independently preserved tokens.
NUMBER_RE = re.compile(r"\d+(?:(?:[.,]\d+)|(?:[ \u00a0]\d{3}))*")
MARKER_RE = re.compile(r"ZXQMARK(\d{4})QXZ")
SOURCE_NONE = "None"
REQUIRED_EDITORIAL_FIELDS = frozenset({
    "action", "warning", "completion", "operational_fields",
    "explanation", "alt_text", "concise", "focused",
})
REQUIRED_RECIPE_EDITORIAL_FIELDS = frozenset({"recipe_name", "recipe_approach"})
REQUIRED_TERMINOLOGY_CONCEPTS = frozenset({
    "brewer_dripper", "coffee_bed", "bloom", "grounds", "fines", "slurry",
    "drawdown", "swirl_spin", "server_carafe", "steep_immersion", "valve",
    "filter_paper",
})


class LocalizationError(RuntimeError):
    """A locale could not be generated without violating source invariants."""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--translate", action="store_true",
        help="fill missing draft translation-memory entries with Google Translate",
    )
    parser.add_argument(
        "--promote-reviewed", action="store_true",
        help="write reviewed memory to Android resources after ledger approval",
    )
    parser.add_argument(
        "--locales", nargs="+", choices=LOCALES, default=list(LOCALES),
        help="locales to generate or validate (default: every supported locale)",
    )
    parser.add_argument(
        "--check", action="store_true",
        help="validate checked-in resources without writing or network access",
    )
    return parser.parse_args()


def read_json(path: Path) -> dict:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError as error:
        raise LocalizationError(f"Missing JSON file: {path}") from error
    except json.JSONDecodeError as error:
        raise LocalizationError(f"Invalid JSON in {path}: {error}") from error


def resource_path(locale: str) -> Path:
    qualifier = "raw" if locale == "en" else f"raw-{locale}"
    return RES / qualifier / "p1_exact_guidance.json"


def terminology_resource_path(locale: str) -> Path:
    qualifier = "raw" if locale == "en" else f"raw-{locale}"
    return RES / qualifier / "p1_exact_terminology.json"


def draft_resource_path(locale: str) -> Path:
    return DRAFT_ROOT / locale / "p1_exact_guidance.json"


def editorial_review_path(locale: str) -> Path:
    return EDITORIAL_REVIEW_DIR / f"{locale}.json"


def iter_text_slots(document: dict):
    for recipe in document["recipes"]:
        for key in RECIPE_TEXT_KEYS:
            yield recipe, key
        for stage in recipe["stages"]:
            for key in STAGE_TEXT_KEYS:
                yield stage, key
            guidance = stage["guidance"]
            for key in FULL_TEXT_KEYS:
                yield guidance["full"], key
            for key in CONCISE_TEXT_KEYS:
                yield guidance["concise"], key
            for key in FOCUSED_TEXT_KEYS:
                yield guidance["focused"], key


def translatable_strings(document: dict) -> list[str]:
    return sorted({
        container[key]
        for container, key in iter_text_slots(document)
        if container[key] != SOURCE_NONE
    })


def restore_numbers(source: str, translated: str) -> str:
    source_numbers = NUMBER_RE.findall(source)
    matches = list(NUMBER_RE.finditer(translated))
    if len(matches) != len(source_numbers):
        raise LocalizationError(
            f"Translation changed numeric-token count: {source!r} -> {translated!r}",
        )
    for match, source_number in reversed(list(zip(matches, source_numbers, strict=True))):
        translated = translated[:match.start()] + source_number + translated[match.end():]
    return translated.strip()


def batch_strings(values: list[str], character_limit: int = 3500) -> list[list[str]]:
    batches: list[list[str]] = []
    current: list[str] = []
    current_size = 0
    for value in values:
        addition = len(value) + 24
        if current and current_size + addition > character_limit:
            batches.append(current)
            current = []
            current_size = 0
        current.append(value)
        current_size += addition
    if current:
        batches.append(current)
    return batches


def disambiguate_coffee_english(value: str) -> str:
    """Make polysemous coffee terms explicit before machine translation."""
    phrase_replacements = (
        (r"\bfinal very gentle spin and draw down\b", "gently rotate the coffee-and-water mixture one final time, then let it drain"),
        (r"\bvery gentle settling swirl\b", "very gently rotate the coffee-and-water mixture to settle it"),
        (r"\bspin the bloom\b", "rotate the coffee brewing device during the initial wetting phase"),
        (r"\blevel the bed\b", "make the layer of ground coffee level"),
        (r"\bby (\d+:\d+)\b", r"when the timer reads \1"),
        (r"\bto ([\d,]+ g)\b", r"until the scale reads \1"),
        (r"\bBloom all grounds\b", "Wet all ground coffee particles for the initial extraction phase"),
        (r"\bthe bloom\b", "the initial coffee-wetting phase"),
        (r"\bbloom\b", "initial coffee-wetting phase"),
        (r"\bcoffee bed\b", "layer of ground coffee"),
        (r"\bbed\b", "layer of ground coffee"),
        (r"\bgrounds\b", "ground coffee particles"),
        (r"\bslurry\b", "coffee-and-water mixture"),
        (r"\bfines\b", "fine coffee particles"),
        (r"\bdrawdown\b", "drainage through the filter"),
        (r"\bdripper\b", "coffee dripper"),
        (r"\bbrewer\b", "coffee brewing device"),
        (r"\bserver\b", "coffee serving vessel"),
        (r"\bswirl\b", "rotate gently in a circular motion"),
        (r"\bspin\b", "rotate in a circular motion"),
    )
    for pattern, replacement in phrase_replacements:
        value = re.sub(pattern, replacement, value, flags=re.IGNORECASE)
    return value

def translate_batch(translator, values: list[str]) -> dict[str, str]:
    payload = "\n".join(
        f"ZXQMARK{index:04d}QXZ\n{disambiguate_coffee_english(value)}"
        for index, value in enumerate(values)
    )
    translated_payload = translator.translate(payload)
    matches = list(MARKER_RE.finditer(translated_payload))
    if [int(match.group(1)) for match in matches] != list(range(len(values))):
        raise LocalizationError("Translation service changed batch boundary markers")
    translated: dict[str, str] = {}
    for index, match in enumerate(matches):
        end = matches[index + 1].start() if index + 1 < len(matches) else len(translated_payload)
        value = translated_payload[match.end():end].strip()
        if not value:
            raise LocalizationError(f"Translation service returned blank item {index}")
        translated[values[index]] = restore_numbers(values[index], value)
    return translated


def source_sha256() -> str:
    return hashlib.sha256(SOURCE.read_bytes()).hexdigest()


def load_memory(memory_path: Path) -> dict:
    if not memory_path.exists():
        return {
            "schema_version": 1,
            "source_sha256": source_sha256(),
            "engine": "deep-translator 1.11.4 / Google Translate",
            "translations": {},
        }
    memory = read_json(memory_path)
    memory_source = (
        memory.get("guidance_translation_source_sha256")
        if "locales" in memory
        else memory.get("source_sha256")
    )
    if memory_source != source_sha256():
        raise LocalizationError("Translation memory belongs to a different canonical source")
    return memory


def write_json(path: Path, document: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(document, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8", newline="\n",
    )


def fill_memory(
    locale: str,
    sources: list[str],
    memory: dict,
    memory_path: Path,
) -> None:
    if locale == "en":
        return
    try:
        from deep_translator import GoogleTranslator
    except ImportError as error:
        raise LocalizationError(
            "Install deep-translator==1.11.4 in an isolated environment to translate",
        ) from error
    locale_memory = memory["translations"].setdefault(locale, {})
    missing = [source for source in sources if source not in locale_memory]
    translator = GoogleTranslator(source="en", target=GOOGLE_TARGET.get(locale, locale))
    batches = batch_strings(missing)
    for index, batch in enumerate(batches, start=1):
        last_error: Exception | None = None
        for attempt in range(1, 4):
            try:
                locale_memory.update(translate_batch(translator, batch))
                write_json(memory_path, memory)
                print(f"{locale}: translated batch {index}/{len(batches)}")
                last_error = None
                break
            except Exception as error:  # network services can fail transiently
                last_error = error
                time.sleep(attempt * 2)
        if last_error is not None:
            raise LocalizationError(f"{locale}: batch {index} failed: {last_error}") from last_error


def sanitize_translated_text(value: str) -> str:
    return "".join(
        character for character in value
        if unicodedata.category(character) != "Cf"
    )


def terminology_catalog_locale(
    source: dict,
    locale: str,
    require_approved: bool,
    override: dict | None = None,
) -> dict:
    catalog = read_json(LOCALIZATION_SOURCE)
    for key in ("source_schema_version", "source_execution_date", "source_sha256"):
        if catalog.get(key) != source.get(key):
            raise LocalizationError(f"{locale}: terminology catalog {key} differs")
    record = override or catalog.get("locales", {}).get(locale, {}).get("terminology")
    if not isinstance(record, dict):
        raise LocalizationError(f"{locale}: terminology catalog locale is missing")
    status = record.get("status")
    if status not in {"researched_not_native_reviewed", "approved"}:
        raise LocalizationError(f"{locale}: terminology catalog status is invalid")
    terms = record.get("terms")
    if not isinstance(terms, list) or [term.get("concept_id") for term in terms if isinstance(term, dict)] != [
        concept["id"] for concept in canonical_terminology_concepts(source)
    ]:
        raise LocalizationError(f"{locale}: terminology catalog concepts differ")
    ui_copy = record.get("ui_copy")
    required_ui_copy = {"show_english_terms", "hide_english_terms", "heading"}
    if not isinstance(ui_copy, dict) or set(ui_copy) != required_ui_copy or any(
        not isinstance(ui_copy[key], str) or not ui_copy[key].strip() for key in required_ui_copy
    ):
        raise LocalizationError(f"{locale}: terminology catalog UI copy is incomplete")
    for term in terms:
        if (
            not isinstance(term.get("display_policy"), str)
            or not term["display_policy"].strip()
            or not isinstance(term.get("english_reference_policy"), str)
            or not term["english_reference_policy"].strip()
            or not isinstance(term.get("accepted_alternatives"), list)
            or not isinstance(term.get("inflected_or_adapted_forms"), list)
        ):
            raise LocalizationError(f"{locale}: terminology catalog term is incomplete")
    if require_approved:
        if status != "approved":
            raise LocalizationError(f"{locale}: terminology catalog is not approved")
        if record.get("insufficient_evidence_count") != 0:
            raise LocalizationError(f"{locale}: terminology catalog retains insufficient evidence")
        if not isinstance(record.get("reviewer"), str) or not record["reviewer"].strip():
            raise LocalizationError(f"{locale}: terminology catalog reviewer is missing")
        if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", str(record.get("reviewed_on") or "")):
            raise LocalizationError(f"{locale}: terminology catalog review date must use YYYY-MM-DD")
        if any(
            not isinstance(term.get("preferred_display_term"), str)
            or not term["preferred_display_term"].strip()
            or term.get("display_policy") == "withhold_pending_review"
            or term.get("english_reference_policy") == "suppress_pending_review"
            or not isinstance(term.get("evidence_source_ids"), list)
            or len(set(term["evidence_source_ids"])) < 2
            for term in terms
        ):
            raise LocalizationError(f"{locale}: approved terminology catalog contains unresolved terms or evidence")
    return record

def canonical_terminology_concepts(source: dict) -> list[dict]:
    manifest = read_json(TERMINOLOGY_REFERENCE_MANIFEST)
    if manifest.get("schema_version") != 1:
        raise LocalizationError("Unsupported terminology reference schema")
    for key in ("source_schema_version", "source_execution_date", "source_sha256"):
        if manifest.get(key) != source.get(key):
            raise LocalizationError(f"Terminology reference {key} differs from exact guidance")
    concepts = manifest.get("concepts")
    if not isinstance(concepts, list) or not concepts:
        raise LocalizationError("Canonical terminology concepts are missing")
    concept_ids = [concept.get("id") for concept in concepts if isinstance(concept, dict)]
    if (
        len(concept_ids) != len(concepts)
        or len(set(concept_ids)) != len(concept_ids)
        or set(concept_ids) != REQUIRED_TERMINOLOGY_CONCEPTS
        or any(
            not isinstance(concept.get("canonical_english"), str)
            or not concept["canonical_english"].strip()
            for concept in concepts
        )
    ):
        raise LocalizationError("Canonical terminology concepts are invalid")
    return concepts


def terminology_resource_document(
    source: dict,
    locale: str,
    catalog_locale: dict | None = None,
) -> dict:
    terminology = terminology_catalog_locale(
        source,
        locale,
        require_approved=True,
        override=catalog_locale,
    )
    concepts = canonical_terminology_concepts(source)
    terms_by_id = {term["concept_id"]: term for term in terminology["terms"]}
    return {
        "schema_version": 2,
        "source_schema_version": source["source_schema_version"],
        "source_execution_date": source["source_execution_date"],
        "source_sha256": source["source_sha256"],
        "locale": locale,
        "review_status": terminology["status"],
        "reviewer": terminology["reviewer"],
        "reviewed_on": terminology["reviewed_on"],
        "ui_copy": terminology["ui_copy"],
        "terms": [
            {
                "concept_id": concept["id"],
                "preferred_local": terms_by_id[concept["id"]]["preferred_display_term"],
                "display_policy": terms_by_id[concept["id"]]["display_policy"],
                "english_reference_policy": terms_by_id[concept["id"]]["english_reference_policy"],
                "accepted_aliases": list(dict.fromkeys(
                    [item["term"] for item in terms_by_id[concept["id"]]["accepted_alternatives"]]
                    + terms_by_id[concept["id"]]["inflected_or_adapted_forms"]
                )),
            }
            for concept in concepts
        ],
    }

def validate_terminology_resource_document(
    source: dict,
    localized: dict,
    locale: str,
) -> None:
    if localized.get("schema_version") != 2:
        raise LocalizationError(f"{locale}: runtime terminology schema is invalid")
    for key in ("source_schema_version", "source_execution_date", "source_sha256"):
        if localized.get(key) != source.get(key):
            raise LocalizationError(f"{locale}: runtime terminology {key} differs")
    if localized.get("locale") != locale:
        raise LocalizationError(f"{locale}: runtime terminology locale differs")
    if localized.get("review_status") != "approved":
        raise LocalizationError(f"{locale}: runtime terminology is not approved")
    if not isinstance(localized.get("reviewer"), str) or not localized["reviewer"].strip():
        raise LocalizationError(f"{locale}: runtime terminology reviewer is missing")
    if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", str(localized.get("reviewed_on", ""))):
        raise LocalizationError(f"{locale}: runtime terminology review date is invalid")
    ui_copy = localized.get("ui_copy")
    required_ui_copy = {
        "show_english_terms", "hide_english_terms", "heading",
    }
    if (
        not isinstance(ui_copy, dict)
        or set(ui_copy) != required_ui_copy
        or any(
            not isinstance(ui_copy[key], str) or not ui_copy[key].strip()
            for key in required_ui_copy
        )
    ):
        raise LocalizationError(f"{locale}: runtime terminology UI copy is invalid")

    expected_concepts = canonical_terminology_concepts(source)
    terms = localized.get("terms")
    if not isinstance(terms, list):
        raise LocalizationError(f"{locale}: runtime terminology terms are missing")
    if [term.get("concept_id") for term in terms if isinstance(term, dict)] != [
        concept["id"] for concept in expected_concepts
    ]:
        raise LocalizationError(f"{locale}: runtime terminology concept order differs")
    if any(
        not isinstance(term, dict)
        or set(term) != {
            "concept_id", "preferred_local", "display_policy",
            "english_reference_policy", "accepted_aliases",
        }
        or not isinstance(term["preferred_local"], str)
        or not term["preferred_local"].strip()
        or not isinstance(term["display_policy"], str)
        or not term["display_policy"].strip()
        or not isinstance(term["english_reference_policy"], str)
        or not term["english_reference_policy"].strip()
        or not isinstance(term["accepted_aliases"], list)
        or any(not isinstance(alias, str) or not alias.strip() for alias in term["accepted_aliases"])
        for term in terms
    ):
        raise LocalizationError(f"{locale}: runtime terminology term is invalid")


def validate_terminology_resource(source: dict, locale: str) -> None:
    validate_terminology_resource_document(
        source,
        read_json(terminology_resource_path(locale)),
        locale,
    )


def apply_editorial_review(
    source: dict,
    localized: dict,
    locale: str,
    require_approved: bool,
) -> None:
    if locale == "en":
        return
    path = editorial_review_path(locale)
    if not path.exists():
        if require_approved:
            raise LocalizationError(f"{locale}: editorial review file is missing")
        return
    review = read_json(path)
    if review.get("schema_version") != 1:
        raise LocalizationError(f"{locale}: unsupported editorial review schema")
    if review.get("source_sha256") != source_sha256():
        raise LocalizationError(f"{locale}: editorial review belongs to another source")
    if review.get("locale") != locale:
        raise LocalizationError(f"{locale}: editorial review locale does not match")
    catalog_reference = review.get("terminology_catalog")
    catalog = read_json(LOCALIZATION_SOURCE)
    if not isinstance(catalog_reference, dict) or catalog_reference != {
        "path": LOCALIZATION_SOURCE.relative_to(ROOT).as_posix(),
        "research_records_sha256": catalog.get("terminology_research_records_sha256"),
        "status": catalog.get("locales", {}).get(locale, {}).get("terminology", {}).get("status"),
    }:
        raise LocalizationError(f"{locale}: editorial terminology catalog reference differs")
    terminology_catalog_locale(source, locale, require_approved)
    reviews = review.get("stages")
    if not isinstance(reviews, dict):
        raise LocalizationError(f"{locale}: editorial stage reviews are missing")

    source_stages = {
        f"{recipe['recipe_id']}/{stage['stage_id']}": stage
        for recipe in source["recipes"]
        for stage in recipe["stages"]
    }
    localized_stages = {
        f"{recipe['recipe_id']}/{stage['stage_id']}": stage
        for recipe in localized["recipes"]
        for stage in recipe["stages"]
    }
    recipe_reviews = review.get("recipes")
    if not isinstance(recipe_reviews, dict):
        raise LocalizationError(f"{locale}: editorial recipe reviews are missing")
    source_recipes = {recipe["recipe_id"]: recipe for recipe in source["recipes"]}
    localized_recipes = {
        recipe["recipe_id"]: recipe for recipe in localized["recipes"]
    }
    unknown_recipes = set(recipe_reviews) - set(source_recipes)
    if unknown_recipes:
        raise LocalizationError(
            f"{locale}: editorial review contains unknown recipes: "
            f"{sorted(unknown_recipes)}",
        )
    if require_approved:
        missing_recipes = set(source_recipes) - set(recipe_reviews)
        unapproved_recipes = [
            recipe_id for recipe_id, recipe_review in recipe_reviews.items()
            if not isinstance(recipe_review, dict)
            or recipe_review.get("status") != "approved"
            or not REQUIRED_RECIPE_EDITORIAL_FIELDS.issubset(
                recipe_review.get("reviewed_fields", []),
            )
        ]
        if missing_recipes or unapproved_recipes:
            raise LocalizationError(
                f"{locale}: editorial recipe review is incomplete "
                f"({len(missing_recipes)} missing, "
                f"{len(unapproved_recipes)} unapproved)",
            )
    for recipe_id, recipe_review in recipe_reviews.items():
        if not isinstance(recipe_review, dict):
            raise LocalizationError(f"{locale}: invalid review for {recipe_id}")
        overrides = recipe_review.get("overrides", {})
        if not isinstance(overrides, dict):
            raise LocalizationError(f"{locale}: invalid overrides for {recipe_id}")
        for field, value in overrides.items():
            if field not in REQUIRED_RECIPE_EDITORIAL_FIELDS:
                raise LocalizationError(
                    f"{locale}: unsupported recipe editorial field "
                    f"{recipe_id}/{field}",
                )
            if not isinstance(value, str) or not value.strip():
                raise LocalizationError(
                    f"{locale}: blank recipe editorial override {recipe_id}/{field}",
                )
            if NUMBER_RE.findall(source_recipes[recipe_id][field]) != NUMBER_RE.findall(value):
                raise LocalizationError(
                    f"{locale}: recipe editorial override changed numbers in "
                    f"{recipe_id}/{field}",
                )
            localized_recipes[recipe_id][field] = sanitize_translated_text(value)

    unknown = set(reviews) - set(source_stages)
    if unknown:
        raise LocalizationError(
            f"{locale}: editorial review contains unknown stages: {sorted(unknown)}",
        )
    if require_approved:
        missing = set(source_stages) - set(reviews)
        unapproved = [
            stage_key for stage_key, stage_review in reviews.items()
            if not isinstance(stage_review, dict)
            or stage_review.get("status") != "approved"
            or not REQUIRED_EDITORIAL_FIELDS.issubset(
                stage_review.get("reviewed_fields", []),
            )
        ]
        if review.get("status") != "approved" or missing or unapproved:
            raise LocalizationError(
                f"{locale}: editorial review is incomplete "
                f"({len(missing)} missing, {len(unapproved)} unapproved)",
            )

    allowed_fields = set(STAGE_TEXT_KEYS) | {
        f"full.{key}" for key in FULL_TEXT_KEYS
    } | {
        f"concise.{key}" for key in CONCISE_TEXT_KEYS
    } | {
        f"focused.{key}" for key in FOCUSED_TEXT_KEYS
    }
    for stage_key, stage_review in reviews.items():
        if not isinstance(stage_review, dict):
            raise LocalizationError(f"{locale}: invalid review for {stage_key}")
        overrides = stage_review.get("overrides", {})
        if not isinstance(overrides, dict):
            raise LocalizationError(f"{locale}: invalid overrides for {stage_key}")
        source_stage = source_stages[stage_key]
        localized_stage = localized_stages[stage_key]
        for field, value in overrides.items():
            if field not in allowed_fields:
                raise LocalizationError(
                    f"{locale}: unsupported editorial field {stage_key}/{field}",
                )
            if not isinstance(value, str) or not value.strip():
                raise LocalizationError(
                    f"{locale}: blank editorial override {stage_key}/{field}",
                )
            source_container = source_stage
            localized_container = localized_stage
            key = field
            if "." in field:
                mode, key = field.split(".", 1)
                source_container = source_stage["guidance"][mode]
                localized_container = localized_stage["guidance"][mode]
            if NUMBER_RE.findall(source_container[key]) != NUMBER_RE.findall(value):
                raise LocalizationError(
                    f"{locale}: editorial override changed numbers in {stage_key}/{field}",
                )
            localized_container[key] = sanitize_translated_text(value)


def localized_document(
    source: dict,
    locale: str,
    memory: dict,
    require_approved_review: bool = False,
) -> dict:
    if locale == "en":
        return copy.deepcopy(source)
    if "locales" in memory:
        translations = memory.get("locales", {}).get(locale, {}).get("guidance", {}).get("translations", {})
    else:
        translations = memory.get("translations", {}).get(locale, {})
    localized = copy.deepcopy(source)
    for container, key in iter_text_slots(localized):
        source_value = container[key]
        if source_value == SOURCE_NONE:
            continue
        try:
            container[key] = sanitize_translated_text(translations[source_value])
        except KeyError as error:
            raise LocalizationError(f"{locale}: missing translation for {source_value!r}") from error
    apply_editorial_review(source, localized, locale, require_approved_review)
    for recipe in localized["recipes"]:
        for stage in recipe["stages"]:
            full = stage["guidance"]["full"]
            concise = stage["guidance"]["concise"]
            focused = stage["guidance"]["focused"]
            full["imperative_instruction"] = stage["action"]
            concise["current_action"] = stage["action"]
            focused["action_label"] = stage["action"]
            full["observable_completion_cue"] = stage["completion_criterion"]
            concise["completion_cue"] = stage["completion_criterion"]
            full["optional_practical_tip"] = stage["optional_tip"]
            full["warning"] = stage["warning"]
            concise["essential_warning"] = stage["warning"]
    return localized


def immutable_projection(document: dict) -> dict:
    projected = copy.deepcopy(document)
    for container, key in iter_text_slots(projected):
        container[key] = "<localized>"
    return projected


def validate_document(source: dict, localized: dict, locale: str) -> None:
    if immutable_projection(source) != immutable_projection(localized):
        raise LocalizationError(f"{locale}: stable IDs, structure, or provenance changed")
    source_slots = list(iter_text_slots(source))
    localized_slots = list(iter_text_slots(localized))
    changed = 0
    for (source_container, source_key), (localized_container, localized_key) in zip(
        source_slots, localized_slots, strict=True,
    ):
        if source_key != localized_key:
            raise LocalizationError(f"{locale}: localized field order changed")
        source_value = source_container[source_key]
        localized_value = localized_container[localized_key]
        if not isinstance(localized_value, str) or not localized_value.strip():
            raise LocalizationError(f"{locale}: blank localized value for {source_key}")
        if any(unicodedata.category(character) == "Cf" for character in localized_value):
            raise LocalizationError(f"{locale}: invisible formatting character in {source_key}")
        if source_value == SOURCE_NONE and localized_value != SOURCE_NONE:
            raise LocalizationError(f"{locale}: sentinel None was translated")
        if NUMBER_RE.findall(source_value) != NUMBER_RE.findall(localized_value):
            raise LocalizationError(f"{locale}: numeric source values changed in {source_key}")
        if source_value != localized_value:
            changed += 1
    if locale != "en" and changed < int(len(source_slots) * 0.75):
        raise LocalizationError(
            f"{locale}: too much English fallback ({changed}/{len(source_slots)} changed)",
        )
    for recipe in localized["recipes"]:
        for stage in recipe["stages"]:
            full = stage["guidance"]["full"]
            concise = stage["guidance"]["concise"]
            focused = stage["guidance"]["focused"]
            actions = (
                stage["action"], full["imperative_instruction"],
                concise["current_action"], focused["action_label"],
            )
            if len({action.strip().removesuffix(".") for action in actions}) != 1:
                raise LocalizationError(f"{locale}: correlated stage actions differ")
            if not (
                stage["completion_criterion"] == full["observable_completion_cue"]
                == concise["completion_cue"]
            ):
                raise LocalizationError(f"{locale}: correlated completion cues differ")
            if not (stage["warning"] == full["warning"] == concise["essential_warning"]):
                raise LocalizationError(f"{locale}: correlated warnings differ")
            if stage["optional_tip"] != full["optional_practical_tip"]:
                raise LocalizationError(f"{locale}: correlated practical tips differ")


def validate_resource(source: dict, locale: str, expected: dict | None = None) -> None:
    actual = read_json(resource_path(locale))
    validate_document(source, actual, locale)
    if expected is not None and actual != expected:
        raise LocalizationError(f"{locale}: checked-in resource is stale; regenerate it")


def validate_review_approval(locale: str) -> None:
    ledger = read_json(REVIEW_LEDGER)
    if ledger.get("source_sha256") != source_sha256():
        raise LocalizationError("Review ledger belongs to a different canonical source")
    approval = ledger.get("locales", {}).get(locale)
    if not isinstance(approval, dict) or approval.get("status") != "approved":
        raise LocalizationError(f"{locale}: reviewed-locale approval is missing")
    if not isinstance(approval.get("reviewer"), str) or not approval["reviewer"].strip():
        raise LocalizationError(f"{locale}: approval reviewer is missing")
    if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", str(approval.get("reviewed_on", ""))):
        raise LocalizationError(f"{locale}: approval date must use YYYY-MM-DD")
    if approval.get("recipe_count") != 20 or approval.get("stage_count") != 114:
        raise LocalizationError(f"{locale}: approval must cover all 20 recipes and 114 stages")


def validate_expected_path(source: dict, locale: str, path: Path, expected: dict) -> None:
    actual = read_json(path)
    validate_document(source, actual, locale)
    if actual != expected:
        raise LocalizationError(f"{locale}: generated output is stale")


def main() -> int:
    args = parse_args()
    try:
        source = read_json(SOURCE)
        if args.check:
            if args.translate or args.promote_reviewed:
                raise LocalizationError("--check cannot be combined with a write mode")
            for locale in args.locales:
                validate_resource(source, locale)
                validate_terminology_resource(source, locale)
            print(
                "Validated exact P1 guidance and terminology for "
                f"{len(args.locales)} locales.",
            )
            return 0
        if args.translate == args.promote_reviewed:
            raise LocalizationError("Choose exactly one of --translate or --promote-reviewed")

        memory_path = DRAFT_MEMORY if args.translate else REVIEWED_MEMORY
        memory = load_memory(memory_path)
        sources = translatable_strings(source)
        for locale in args.locales:
            if args.translate:
                fill_memory(locale, sources, memory, memory_path)
                output_path = draft_resource_path(locale)
            else:
                validate_review_approval(locale)
                output_path = resource_path(locale)
            document = localized_document(
                source,
                locale,
                memory,
                require_approved_review=args.promote_reviewed,
            )
            validate_document(source, document, locale)
            write_json(output_path, document)
            validate_expected_path(source, locale, output_path, document)
            print(f"Wrote {output_path}")
            if args.promote_reviewed:
                if locale == "en":
                    validate_terminology_resource(source, locale)
                else:
                    terminology_document = terminology_resource_document(source, locale)
                    terminology_path = terminology_resource_path(locale)
                    write_json(terminology_path, terminology_document)
                    validate_terminology_resource_document(
                        source,
                        read_json(terminology_path),
                        locale,
                    )
                    print(f"Wrote {terminology_path}")
        write_json(memory_path, memory)
        return 0
    except LocalizationError as error:
        print(error, file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
