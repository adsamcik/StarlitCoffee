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
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/assets/p1_exact_guidance_2026_07_27.json"
RES = ROOT / "app/src/main/res"
MEMORY = ROOT / "docs/brewing/p1-exact-guidance-translation-memory.json"
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


class LocalizationError(RuntimeError):
    """A locale could not be generated without violating source invariants."""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--translate", action="store_true",
        help="fill missing draft translation-memory entries with Google Translate",
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


def load_memory() -> dict:
    if not MEMORY.exists():
        return {
            "schema_version": 1,
            "source_sha256": source_sha256(),
            "engine": "deep-translator 1.11.4 / Google Translate",
            "translations": {},
        }
    memory = read_json(MEMORY)
    if memory.get("source_sha256") != source_sha256():
        raise LocalizationError("Translation memory belongs to a different canonical source")
    return memory


def write_json(path: Path, document: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(document, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8", newline="\n",
    )


def fill_memory(locale: str, sources: list[str], memory: dict) -> None:
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
                write_json(MEMORY, memory)
                print(f"{locale}: translated batch {index}/{len(batches)}")
                last_error = None
                break
            except Exception as error:  # network services can fail transiently
                last_error = error
                time.sleep(attempt * 2)
        if last_error is not None:
            raise LocalizationError(f"{locale}: batch {index} failed: {last_error}") from last_error


def localized_document(source: dict, locale: str, memory: dict) -> dict:
    if locale == "en":
        return copy.deepcopy(source)
    translations = memory["translations"].get(locale, {})
    localized = copy.deepcopy(source)
    for container, key in iter_text_slots(localized):
        source_value = container[key]
        if source_value == SOURCE_NONE:
            continue
        try:
            container[key] = translations[source_value]
        except KeyError as error:
            raise LocalizationError(f"{locale}: missing translation for {source_value!r}") from error
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


def main() -> int:
    args = parse_args()
    try:
        source = read_json(SOURCE)
        if args.check:
            for locale in args.locales:
                validate_resource(source, locale)
            print(f"Validated exact P1 guidance for {len(args.locales)} locales.")
            return 0
        memory = load_memory()
        sources = translatable_strings(source)
        for locale in args.locales:
            if args.translate:
                fill_memory(locale, sources, memory)
            document = localized_document(source, locale, memory)
            validate_document(source, document, locale)
            write_json(resource_path(locale), document)
            validate_resource(source, locale, expected=document)
            print(f"Wrote {resource_path(locale)}")
        write_json(MEMORY, memory)
        return 0
    except LocalizationError as error:
        print(error, file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
