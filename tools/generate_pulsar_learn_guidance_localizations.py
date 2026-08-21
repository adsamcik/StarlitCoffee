#!/usr/bin/env python3
"""Validate the released standalone Pulsar learning guide."""

from __future__ import annotations

import argparse
import copy
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
SOURCE = RES / "raw/pulsar_learn_guidance.json"
LOCALES = (
    "en", "bg", "cs", "da", "de", "el", "es", "et", "fi", "fr", "hr", "hu",
    "it", "lt", "lv", "nl", "pl", "pt", "ro", "sk", "sl", "sv", "zh",
)
TEXT_KEYS = (
    "instruction", "concise_instruction", "target", "completion_cue", "explanation",
    "practical_tip", "alt_text", "warning",
)
NUMBER_RE = re.compile(r"\d+(?:(?:[.,]\d+)|(?:[ \u00a0]\d{3}))*")


class LocalizationError(RuntimeError):
    """A localized guide violates the source contract."""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--check",
        action="store_true",
        required=True,
        help="validate the checked-in locale-qualified resources",
    )
    parser.add_argument(
        "--locales",
        nargs="+",
        choices=LOCALES,
        default=list(LOCALES),
        help="locales to translate or validate (default: every supported locale)",
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
    return RES / qualifier / "pulsar_learn_guidance.json"


def iter_text_slots(document: dict):
    for stage in document["stages"]:
        for key in TEXT_KEYS:
            value = stage[key]
            if value is not None:
                yield stage, key


def immutable_projection(document: dict) -> dict:
    projection = copy.deepcopy(document)
    projection["locale"] = "<locale>"
    for container, key in iter_text_slots(projection):
        container[key] = "<localized>"
    return projection


def validate_document(source: dict, localized: dict, locale: str) -> None:
    if localized.get("schema_version") != 1:
        raise LocalizationError(f"{locale}: unsupported Pulsar guide schema")
    if localized.get("guide_id") != source.get("guide_id"):
        raise LocalizationError(f"{locale}: Pulsar guide identity differs")
    if localized.get("locale") != locale:
        raise LocalizationError(f"{locale}: Pulsar guide locale differs")
    if localized.get("release_status") != "released":
        raise LocalizationError(f"{locale}: Pulsar guide is not released")
    if immutable_projection(source) != immutable_projection(localized):
        raise LocalizationError(f"{locale}: Pulsar guide structure differs")
    source_slots = list(iter_text_slots(source))
    localized_slots = list(iter_text_slots(localized))
    changed = 0
    for (source_container, source_key), (localized_container, localized_key) in zip(
        source_slots,
        localized_slots,
        strict=True,
    ):
        if source_key != localized_key:
            raise LocalizationError(f"{locale}: localized field order differs")
        source_value = source_container[source_key]
        localized_value = localized_container[localized_key]
        if not isinstance(localized_value, str) or not localized_value.strip():
            raise LocalizationError(f"{locale}: blank Pulsar guide string")
        if NUMBER_RE.findall(source_value) != NUMBER_RE.findall(localized_value):
            raise LocalizationError(f"{locale}: numeric Pulsar guidance changed")
        if source_value != localized_value:
            changed += 1
    if locale != "en" and changed < int(len(source_slots) * 0.9):
        raise LocalizationError(
            f"{locale}: too much English fallback ({changed}/{len(source_slots)} changed)",
        )


def main() -> int:
    args = parse_args()
    try:
        source = read_json(SOURCE)
        validate_document(source, source, "en")
        for locale in args.locales:
            validate_document(source, read_json(resource_path(locale)), locale)
        print(f"Validated released Pulsar learning guidance for {len(args.locales)} locales.")
        return 0
    except LocalizationError as error:
        print(error, file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
