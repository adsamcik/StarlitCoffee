#!/usr/bin/env python3
"""Generate local-only terminology and UI-copy drafts for every app locale."""

from __future__ import annotations

import argparse
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LOCALE_CONFIG = ROOT / "app/src/main/res/xml/locales_config.xml"
REFERENCE_MANIFEST = ROOT / "app/src/main/assets/p1_exact_terminology_references_2026_07_27.json"
OUTPUT = ROOT / "docs/brewing/p1-exact-terminology-offline-drafts.json"
ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
ENGINE = (
    "facebook/nllb-200-distilled-600M; local-only inference; "
    "transformers 4.53.2; torch 2.7.1+cpu"
)
TARGETS = {
    "bg": "bul_Cyrl", "da": "dan_Latn", "de": "deu_Latn",
    "el": "ell_Grek", "es": "spa_Latn", "et": "est_Latn",
    "fi": "fin_Latn", "fr": "fra_Latn", "hr": "hrv_Latn",
    "hu": "hun_Latn", "it": "ita_Latn", "lt": "lit_Latn",
    "lv": "lvs_Latn", "nl": "nld_Latn", "pl": "pol_Latn",
    "pt": "por_Latn", "ro": "ron_Latn", "sk": "slk_Latn",
    "sl": "slv_Latn", "sv": "swe_Latn", "zh": "zho_Hans",
}
CONCEPT_INPUTS = {
    "brewer_dripper": "coffee brewing device or pour-over coffee dripper",
    "coffee_bed": "layer of ground coffee in the filter",
    "bloom": "initial wetting phase of ground coffee",
    "grounds": "ground coffee particles",
    "fines": "very small particles of ground coffee",
    "slurry": "mixture of ground coffee and water",
    "drawdown": "drainage phase through the coffee filter",
    "swirl_spin": "gently rotate the coffee brewing device in a circular motion",
    "server_carafe": "coffee serving vessel or coffee carafe",
    "steep_immersion": "soak ground coffee in water; immersion brewing",
    "valve": "liquid flow control valve",
    "filter_paper": "coffee filter paper",
}
UI_INPUTS = {
    "show_english_terms": "Show English coffee terminology",
    "hide_english_terms": "Hide English coffee terminology",
    "heading": "English coffee terminology",
}


class DraftError(RuntimeError):
    pass


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model-path", type=Path)
    parser.add_argument("--check", action="store_true")
    return parser.parse_args()


def read_json(path: Path) -> dict:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (FileNotFoundError, json.JSONDecodeError) as error:
        raise DraftError(f"Cannot read {path}: {error}") from error


def configured_locales() -> list[str]:
    try:
        root = ET.parse(LOCALE_CONFIG).getroot()
    except (FileNotFoundError, ET.ParseError) as error:
        raise DraftError(f"Cannot read locale config: {error}") from error
    return [
        node.attrib[f"{{{ANDROID_NAMESPACE}}}name"]
        for node in root.findall("locale")
        if node.attrib[f"{{{ANDROID_NAMESPACE}}}name"] in TARGETS
    ]


def expected_header(references: dict) -> dict:
    return {
        "schema_version": 1,
        "source_schema_version": references["source_schema_version"],
        "source_execution_date": references["source_execution_date"],
        "source_sha256": references["source_sha256"],
        "engine": ENGINE,
        "status": "local_draft_complete",
    }


def validate(document: dict, references: dict) -> None:
    for key, value in expected_header(references).items():
        if document.get(key) != value:
            raise DraftError(f"Terminology draft {key} differs")
    locales = document.get("locales")
    if not isinstance(locales, dict) or set(locales) != set(TARGETS):
        raise DraftError("Terminology draft locale coverage differs")
    concept_ids = [concept["id"] for concept in references["concepts"]]
    for locale, draft in locales.items():
        if draft.get("status") != "local_draft_complete":
            raise DraftError(f"{locale}: draft status differs")
        ui_copy = draft.get("ui_copy")
        if not isinstance(ui_copy, dict) or set(ui_copy) != set(UI_INPUTS):
            raise DraftError(f"{locale}: UI-copy coverage differs")
        if any(not isinstance(value, str) or not value.strip() for value in ui_copy.values()):
            raise DraftError(f"{locale}: UI copy is incomplete")
        terms = draft.get("terms")
        if not isinstance(terms, list) or [
            term.get("concept_id") for term in terms if isinstance(term, dict)
        ] != concept_ids:
            raise DraftError(f"{locale}: concept coverage or order differs")
        if any(
            set(term) != {"concept_id", "preferred_local"}
            or not isinstance(term["preferred_local"], str)
            or not term["preferred_local"].strip()
            for term in terms
        ):
            raise DraftError(f"{locale}: terminology draft is incomplete")


def generate(model_path: Path, references: dict) -> dict:
    try:
        import torch
        from transformers import AutoModelForSeq2SeqLM, AutoTokenizer
    except ImportError as error:
        raise DraftError("Install the pinned local translation runtime") from error
    if not model_path.is_dir():
        raise DraftError(f"Local model directory does not exist: {model_path}")

    tokenizer = AutoTokenizer.from_pretrained(
        model_path, src_lang="eng_Latn", local_files_only=True
    )
    model = AutoModelForSeq2SeqLM.from_pretrained(
        model_path, local_files_only=True
    )
    model.eval()
    concept_ids = [concept["id"] for concept in references["concepts"]]
    if concept_ids != list(CONCEPT_INPUTS):
        raise DraftError("Canonical terminology concepts differ from local prompts")
    inputs = list(CONCEPT_INPUTS.values()) + list(UI_INPUTS.values())
    result = {**expected_header(references), "locales": {}}

    for locale in configured_locales():
        encoded = tokenizer(inputs, return_tensors="pt", padding=True, truncation=True)
        with torch.inference_mode():
            generated = model.generate(
                **encoded,
                forced_bos_token_id=tokenizer.convert_tokens_to_ids(TARGETS[locale]),
                max_new_tokens=48,
                num_beams=1,
                no_repeat_ngram_size=3,
            )
        translations = [
            value.strip()
            for value in tokenizer.batch_decode(generated, skip_special_tokens=True)
        ]
        if len(translations) != len(inputs) or any(not value for value in translations):
            raise DraftError(f"{locale}: local terminology generation is incomplete")
        term_values = translations[:len(concept_ids)]
        ui_values = translations[len(concept_ids):]
        result["locales"][locale] = {
            "status": "local_draft_complete",
            "ui_copy": dict(zip(UI_INPUTS, ui_values, strict=True)),
            "terms": [
                {"concept_id": concept_id, "preferred_local": value}
                for concept_id, value in zip(concept_ids, term_values, strict=True)
            ],
        }
        print(f"{locale}: drafted {len(concept_ids)} terms and {len(UI_INPUTS)} UI strings", flush=True)
    return result


def encoded(document: dict) -> str:
    return json.dumps(document, ensure_ascii=False, indent=2) + "\n"


def main() -> int:
    args = parse_args()
    try:
        references = read_json(REFERENCE_MANIFEST)
        if args.check:
            document = read_json(OUTPUT)
            validate(document, references)
            print(f"Validated local-only terminology drafts for {len(TARGETS)} locales.")
            return 0
        if args.model_path is None:
            raise DraftError("--model-path is required unless --check is used")
        document = generate(args.model_path, references)
        validate(document, references)
        OUTPUT.write_text(encoded(document), encoding="utf-8", newline="\n")
        print(f"Wrote {OUTPUT}")
        return 0
    except DraftError as error:
        print(error, file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
