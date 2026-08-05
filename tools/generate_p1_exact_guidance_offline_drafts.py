#!/usr/bin/env python3
"""Generate source-bound exact-guidance drafts with a strictly local NLLB model."""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

from generate_p1_exact_guidance_localizations import (
    DRAFT_ROOT,
    LocalizationError,
    SOURCE,
    disambiguate_coffee_english,
    load_memory,
    localized_document,
    read_json,
    restore_numbers,
    source_sha256,
    translatable_strings,
    validate_document,
    validate_expected_path,
    write_json,
)

ROOT = Path(__file__).resolve().parents[1]
MEMORY_PATH = ROOT / "docs/brewing/p1-exact-guidance-offline-draft-translation-memory.json"
LOCALE_TARGETS = {
    "bg": "bul_Cyrl", "cs": "ces_Latn", "da": "dan_Latn",
    "de": "deu_Latn", "el": "ell_Grek", "es": "spa_Latn",
    "et": "est_Latn", "fi": "fin_Latn", "fr": "fra_Latn",
    "hr": "hrv_Latn", "hu": "hun_Latn", "it": "ita_Latn",
    "lt": "lit_Latn", "lv": "lvs_Latn", "nl": "nld_Latn",
    "pl": "pol_Latn", "pt": "por_Latn", "ro": "ron_Latn",
    "sk": "slk_Latn", "sl": "slv_Latn", "sv": "swe_Latn",
    "zh": "zho_Hans",
}
ENGINE = (
    "facebook/nllb-200-distilled-600M; local-only inference; "
    "transformers 4.53.2; torch 2.7.1+cpu"
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--model-path", type=Path, required=True,
        help="local directory containing the downloaded NLLB model",
    )
    parser.add_argument(
        "--locales", nargs="+", choices=tuple(LOCALE_TARGETS),
        default=list(LOCALE_TARGETS),
    )
    parser.add_argument("--batch-size", type=int, default=32)
    parser.add_argument(
        "--check", action="store_true",
        help="validate committed memory and drafts without loading a model",
    )
    return parser.parse_args()


def validate_memory(memory: dict, locales: list[str], sources: list[str]) -> None:
    if memory.get("source_sha256") != source_sha256():
        raise LocalizationError("Offline draft memory belongs to another canonical source")
    if memory.get("engine") != ENGINE:
        raise LocalizationError("Offline draft memory has an unexpected engine identity")
    translations = memory.get("translations")
    if not isinstance(translations, dict):
        raise LocalizationError("Offline draft memory translations are missing")
    for locale in locales:
        localized = translations.get(locale)
        if not isinstance(localized, dict):
            raise LocalizationError(f"{locale}: offline draft memory is missing")
        missing = set(sources) - set(localized)
        unknown = set(localized) - set(sources)
        if missing or unknown:
            raise LocalizationError(
                f"{locale}: offline memory differs "
                f"({len(missing)} missing, {len(unknown)} unknown)"
            )


def translate_locales(
    model_path: Path,
    locales: list[str],
    sources: list[str],
    memory: dict,
    batch_size: int,
) -> None:
    try:
        import torch
        from transformers import AutoModelForSeq2SeqLM, AutoTokenizer
    except ImportError as error:
        raise LocalizationError(
            "Install torch, transformers, and sentencepiece locally"
        ) from error
    if not model_path.is_dir():
        raise LocalizationError(f"Local model directory does not exist: {model_path}")
    if batch_size < 1:
        raise LocalizationError("--batch-size must be positive")

    tokenizer = AutoTokenizer.from_pretrained(
        model_path, src_lang="eng_Latn", local_files_only=True
    )
    model = AutoModelForSeq2SeqLM.from_pretrained(
        model_path, local_files_only=True
    )
    model.eval()
    memory["engine"] = ENGINE

    for locale in locales:
        locale_memory = memory["translations"].setdefault(locale, {})
        for source in sources:
            if not re.search(r"[A-Za-z]{2,}", source):
                locale_memory.setdefault(source, source)
        missing = [source for source in sources if source not in locale_memory]
        batches = [
            missing[index:index + batch_size]
            for index in range(0, len(missing), batch_size)
        ]
        for index, batch in enumerate(batches, start=1):
            inputs = tokenizer(
                [disambiguate_coffee_english(value) for value in batch],
                return_tensors="pt", padding=True, truncation=True, max_length=384,
            )
            with torch.inference_mode():
                generated = model.generate(
                    **inputs,
                    forced_bos_token_id=tokenizer.convert_tokens_to_ids(
                        LOCALE_TARGETS[locale]
                    ),
                    max_new_tokens=384,
                    num_beams=1,
                )
            translated = tokenizer.batch_decode(
                generated, skip_special_tokens=True
            )
            if len(translated) != len(batch):
                raise LocalizationError(
                    f"{locale}: local model changed batch cardinality"
                )
            for source, localized in zip(batch, translated, strict=True):
                locale_memory[source] = restore_numbers(source, localized)
            write_json(MEMORY_PATH, memory)
            print(f"{locale}: local batch {index}/{len(batches)}", flush=True)


def main() -> int:
    args = parse_args()
    try:
        source = read_json(SOURCE)
        sources = translatable_strings(source)
        memory = load_memory(MEMORY_PATH)
        memory["engine"] = ENGINE

        if not args.check:
            translate_locales(
                args.model_path, args.locales, sources, memory, args.batch_size
            )
            write_json(MEMORY_PATH, memory)

        validate_memory(memory, args.locales, sources)
        for locale in args.locales:
            document = localized_document(source, locale, memory)
            validate_document(source, document, locale)
            output_path = DRAFT_ROOT / locale / "p1_exact_guidance.json"
            if args.check:
                validate_expected_path(source, locale, output_path, document)
            else:
                write_json(output_path, document)
                validate_expected_path(source, locale, output_path, document)
                print(f"Wrote {output_path}", flush=True)
        print(f"Validated {len(args.locales)} local-only locale drafts.")
        return 0
    except LocalizationError as error:
        print(error, file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
