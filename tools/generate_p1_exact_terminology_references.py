#!/usr/bin/env python3
"""Generate the canonical semantic terminology sidecar for exact P1 guidance."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/assets/p1_exact_guidance_2026_07_27.json"
OUTPUT = ROOT / "app/src/main/assets/p1_exact_terminology_references_2026_07_27.json"
SCHEMA_VERSION = 1
EXPECTED_REFERENCED_STAGE_COUNT = 82
EXPECTED_CONCEPT_COUNTS = {
    "brewer_dripper": 10,
    "coffee_bed": 20,
    "bloom": 11,
    "grounds": 15,
    "fines": 5,
    "slurry": 5,
    "drawdown": 24,
    "swirl_spin": 6,
    "server_carafe": 17,
    "steep_immersion": 3,
    "valve": 21,
    "filter_paper": 20,
}
CONCEPTS = {
    "brewer_dripper": (
        "brewer / dripper",
        re.compile(r"\b(?:brewer|dripper)s?\b", re.IGNORECASE),
    ),
    "coffee_bed": (
        "coffee bed",
        re.compile(r"\b(?:coffee\s+)?bed\b", re.IGNORECASE),
    ),
    "bloom": (
        "bloom",
        re.compile(r"\bbloom(?:ing|ed|s)?\b", re.IGNORECASE),
    ),
    "grounds": (
        "coffee grounds",
        re.compile(r"\b(?:coffee\s+)?grounds?\b", re.IGNORECASE),
    ),
    "fines": (
        "coffee fines",
        re.compile(r"\b(?:coffee\s+)?fines?\b|\bfine particles?\b", re.IGNORECASE),
    ),
    "slurry": (
        "coffee slurry",
        re.compile(r"\bslurry\b", re.IGNORECASE),
    ),
    "drawdown": (
        "drawdown",
        re.compile(
            r"\bdraw[ -]?down\b|\bdrain(?:s|ed|ing)?\b|"
            r"\bdrip(?:ping)? (?:stops?|finishes?)\b",
            re.IGNORECASE,
        ),
    ),
    "swirl_spin": (
        "swirl / spin",
        re.compile(r"\b(?:swirl|spin|rotate)(?:s|ed|ing)?\b", re.IGNORECASE),
    ),
    "server_carafe": (
        "server / carafe",
        re.compile(r"\b(?:server|carafe)s?\b", re.IGNORECASE),
    ),
    "steep_immersion": (
        "steep / immersion",
        re.compile(r"\b(?:steep|steeping|steeps|immersion)\b", re.IGNORECASE),
    ),
    "valve": (
        "valve",
        re.compile(r"\bvalves?\b", re.IGNORECASE),
    ),
    "filter_paper": (
        "filter paper",
        re.compile(r"\b(?:filter paper|paper filter|paper)\b", re.IGNORECASE),
    ),
}


class TerminologyReferenceError(RuntimeError):
    """The semantic sidecar could not be generated safely."""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--check",
        action="store_true",
        help="verify the checked-in sidecar without writing",
    )
    return parser.parse_args()


def read_json(path: Path) -> dict:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (FileNotFoundError, json.JSONDecodeError) as error:
        raise TerminologyReferenceError(f"Cannot read {path}: {error}") from error


def displayed_stage_text(stage: dict) -> str:
    guidance = stage["guidance"]
    values = [
        stage[key]
        for key in (
            "action",
            "equipment_state",
            "completion_criterion",
            "observable_signs",
            "optional_tip",
            "warning",
        )
    ]
    values.extend(
        guidance["full"][key]
        for key in (
            "imperative_instruction",
            "concise_explanation",
            "optional_practical_tip",
            "warning",
            "observable_completion_cue",
        )
    )
    values.extend(guidance["concise"].values())
    values.extend(guidance["focused"].values())
    return " ".join(values)


def stage_content_id(recipe_id: str, stage_id: str) -> str:
    return f"p1_{recipe_id}_{stage_id}_instruction"


def generate(source: dict) -> dict:
    references: list[dict] = []
    concept_counts = {concept_id: 0 for concept_id in CONCEPTS}
    for recipe in source["recipes"]:
        recipe_id = recipe["recipe_id"]
        for stage in recipe["stages"]:
            text = displayed_stage_text(stage)
            concept_ids = [
                concept_id
                for concept_id, (_, pattern) in CONCEPTS.items()
                if pattern.search(text)
            ]
            if not concept_ids:
                continue
            for concept_id in concept_ids:
                concept_counts[concept_id] += 1
            references.append(
                {
                    "content_id": stage_content_id(recipe_id, stage["stage_id"]),
                    "concept_ids": concept_ids,
                },
            )

    if len(references) != EXPECTED_REFERENCED_STAGE_COUNT:
        raise TerminologyReferenceError(
            "Referenced stage count changed: "
            f"expected {EXPECTED_REFERENCED_STAGE_COUNT}, found {len(references)}",
        )
    if concept_counts != EXPECTED_CONCEPT_COUNTS:
        raise TerminologyReferenceError(
            f"Concept counts changed: expected {EXPECTED_CONCEPT_COUNTS}, found {concept_counts}",
        )

    return {
        "schema_version": SCHEMA_VERSION,
        "source_schema_version": source["source_schema_version"],
        "source_execution_date": source["source_execution_date"],
        "source_sha256": source["source_sha256"],
        "concepts": [
            {
                "id": concept_id,
                "canonical_english": canonical_english,
            }
            for concept_id, (canonical_english, _) in CONCEPTS.items()
        ],
        "stage_references": references,
    }


def encoded(document: dict) -> str:
    return json.dumps(document, ensure_ascii=False, indent=2) + "\n"


def main() -> int:
    args = parse_args()
    try:
        document = generate(read_json(SOURCE))
        expected = encoded(document)
        if args.check:
            try:
                actual = OUTPUT.read_text(encoding="utf-8")
            except FileNotFoundError as error:
                raise TerminologyReferenceError(f"Missing sidecar: {OUTPUT}") from error
            if actual != expected:
                raise TerminologyReferenceError(
                    "Checked-in terminology sidecar is stale; regenerate it",
                )
            print(
                "P1 terminology sidecar is current "
                f"({len(document['stage_references'])} referenced stages).",
            )
            return 0
        OUTPUT.write_text(expected, encoding="utf-8")
        print(f"Wrote {OUTPUT}")
        return 0
    except TerminologyReferenceError as error:
        print(error, file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())