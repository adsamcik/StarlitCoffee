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
    NUMBER_RE,
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
NUMBER_PLACEHOLDERS = (
    "StarlitAlpha", "StarlitBeta", "StarlitGamma", "StarlitDelta",
    "StarlitEpsilon", "StarlitZeta", "StarlitEta", "StarlitTheta",
)
NUMBER_WORDS = {
    "bg": "нула един два три четири пет шест седем осем девет десет единадесет дванадесет".split(),
    "cs": "nula jeden dva tři čtyři pět šest sedm osm devět deset jedenáct dvanáct".split(),
    "da": "nul en to tre fire fem seks syv otte ni ti elleve tolv".split(),
    "de": "null ein zwei drei vier fünf sechs sieben acht neun zehn elf zwölf".split(),
    "el": "μηδέν ένα δύο τρία τέσσερα πέντε έξι επτά οκτώ εννέα δέκα έντεκα δώδεκα".split(),
    "es": "cero uno dos tres cuatro cinco seis siete ocho nueve diez once doce".split(),
    "et": "null üks kaks kolm neli viis kuus seitse kaheksa üheksa kümme üksteist kaksteist".split(),
    "fi": "nolla yksi kaksi kolme neljä viisi kuusi seitsemän kahdeksan yhdeksän kymmenen yksitoista kaksitoista".split(),
    "fr": "zéro un deux trois quatre cinq six sept huit neuf dix onze douze".split(),
    "hr": "nula jedan dva tri četiri pet šest sedam osam devet deset jedanaest dvanaest".split(),
    "hu": "nulla egy kettő három négy öt hat hét nyolc kilenc tíz tizenegy tizenkettő".split(),
    "it": "zero uno due tre quattro cinque sei sette otto nove dieci undici dodici".split(),
    "lt": "nulis vienas du trys keturi penki šeši septyni aštuoni devyni dešimt vienuolika dvylika".split(),
    "lv": "nulle viens divi trīs četri pieci seši septiņi astoņi deviņi desmit vienpadsmit divpadsmit".split(),
    "nl": "nul een twee drie vier vijf zes zeven acht negen tien elf twaalf".split(),
    "pl": "zero jeden dwa trzy cztery pięć sześć siedem osiem dziewięć dziesięć jedenaście dwanaście".split(),
    "pt": "zero um dois três quatro cinco seis sete oito nove dez onze doze".split(),
    "ro": "zero unu doi trei patru cinci șase șapte opt nouă zece unsprezece doisprezece".split(),
    "sk": "nula jeden dva tri štyri päť šesť sedem osem deväť desať jedenásť dvanásť".split(),
    "sl": "nič ena dve tri štiri pet šest sedem osem devet deset enajst dvanajst".split(),
    "sv": "noll en två tre fyra fem sex sju åtta nio tio elva tolv".split(),
    "zh": list("零一二三四五六七八九") + ["十", "十一", "十二"],
}


def protect_numbers(source: str) -> tuple[str, list[tuple[str, str]]]:
    """Replace source numeric tokens with opaque words before local inference."""
    prepared = re.sub(r"(?<=\d)[–—-](?=\d)", " to ", source)
    matches = list(NUMBER_RE.finditer(prepared))
    if len(matches) > len(NUMBER_PLACEHOLDERS):
        raise LocalizationError("Guidance value has too many numeric tokens")
    replacements = [
        (NUMBER_PLACEHOLDERS[index], match.group())
        for index, match in enumerate(matches)
    ]
    for match, (placeholder, _) in reversed(list(zip(matches, replacements, strict=True))):
        prepared = prepared[:match.start()] + placeholder + prepared[match.end():]
    return prepared, replacements


def restore_protected_numbers(
    source: str,
    translated: str,
    replacements: list[tuple[str, str]],
    locale: str,
) -> str:
    for placeholder, number in replacements:
        pattern = re.compile(re.escape(placeholder), flags=re.IGNORECASE)
        if len(pattern.findall(translated)) != 1:
            raise LocalizationError(
                f"{locale}: local model changed numeric placeholder {placeholder} in {source!r}: {translated!r}"
            )
        translated = pattern.sub(number, translated)
    return normalize_numbers(source, translated, locale)


def translate_numeric_fragments(
    source: str,
    locale: str,
    tokenizer,
    model,
    torch,
    forced_bos_token_id: int,
) -> str:
    """Fallback that cannot lose or reorder canonical numeric tokens."""
    prepared = re.sub(
        r"(?<=\d)[–—-](?=\d)",
        " to ",
        disambiguate_coffee_english(source),
    )
    numbers = NUMBER_RE.findall(prepared)
    fragments = NUMBER_RE.split(prepared)
    nonblank = [fragment for fragment in fragments if fragment.strip()]
    localized_fragments: list[str] = []
    if nonblank:
        inputs = tokenizer(
            nonblank,
            return_tensors="pt",
            padding=True,
            truncation=True,
            max_length=384,
        )
        with torch.inference_mode():
            generated = model.generate(
                **inputs,
                forced_bos_token_id=forced_bos_token_id,
                max_new_tokens=96,
                num_beams=1,
            )
        localized_fragments = tokenizer.batch_decode(
            generated,
            skip_special_tokens=True,
        )
    localized_iterator = iter(localized_fragments)
    rebuilt: list[str] = []
    for index, fragment in enumerate(fragments):
        rebuilt.append(next(localized_iterator) if fragment.strip() else fragment)
        if index < len(numbers):
            rebuilt.append(numbers[index])
    return normalize_numbers(source, " ".join(rebuilt), locale)


def normalize_numbers(source: str, translated: str, locale: str) -> str:
    """Keep canonical numeric tokens and spell model-added small numbers as words."""
    source_numbers = NUMBER_RE.findall(source)
    translated_matches = list(NUMBER_RE.finditer(translated))
    extra = len(translated_matches) - len(source_numbers)
    if extra < 0:
        return restore_numbers(source, translated)
    if extra:
        source_cursor = 0
        replacements: list[tuple[int, int, str]] = []
        for match in translated_matches:
            token = match.group()
            if source_cursor < len(source_numbers) and token == source_numbers[source_cursor]:
                source_cursor += 1
                continue
            normalized = token.replace(" ", "").replace("\u00a0", "")
            if not normalized.isdigit() or int(normalized) >= len(NUMBER_WORDS[locale]):
                raise LocalizationError(
                    f"{locale}: local model introduced unsupported number {token!r}"
                )
            replacements.append(
                (match.start(), match.end(), NUMBER_WORDS[locale][int(normalized)])
            )
        if source_cursor != len(source_numbers) or len(replacements) != extra:
            raise LocalizationError(
                f"{locale}: local model reordered canonical numeric tokens"
            )
        for start, end, replacement in reversed(replacements):
            translated = translated[:start] + replacement + translated[end:]
    return restore_numbers(source, translated)


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
        "--memory-path", type=Path, default=MEMORY_PATH,
        help="checkpoint path; separate paths allow isolated local workers",
    )
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
    memory_path: Path,
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
            protected = [
                protect_numbers(disambiguate_coffee_english(value))
                for value in batch
            ]
            inputs = tokenizer(
                [value for value, _ in protected],
                return_tensors="pt", padding=True, truncation=True, max_length=384,
            )
            with torch.inference_mode():
                generated = model.generate(
                    **inputs,
                    forced_bos_token_id=tokenizer.convert_tokens_to_ids(
                        LOCALE_TARGETS[locale]
                    ),
                    max_new_tokens=160,
                    num_beams=1,
                )
            translated = tokenizer.batch_decode(
                generated, skip_special_tokens=True
            )
            if len(translated) != len(batch):
                raise LocalizationError(
                    f"{locale}: local model changed batch cardinality"
                )
            for source, localized, (_, replacements) in zip(
                batch, translated, protected, strict=True
            ):
                try:
                    locale_memory[source] = restore_protected_numbers(
                        source, localized, replacements, locale
                    )
                except LocalizationError:
                    locale_memory[source] = translate_numeric_fragments(
                        source,
                        locale,
                        tokenizer,
                        model,
                        torch,
                        tokenizer.convert_tokens_to_ids(LOCALE_TARGETS[locale]),
                    )
                    print(
                        f"{locale}: used measurement-safe fallback for {source!r}",
                        flush=True,
                    )
            write_json(memory_path, memory)
            print(f"{locale}: local batch {index}/{len(batches)}", flush=True)


def main() -> int:
    args = parse_args()
    try:
        source = read_json(SOURCE)
        sources = translatable_strings(source)
        memory = load_memory(args.memory_path)
        memory["engine"] = ENGINE

        if not args.check:
            translate_locales(
                args.model_path, args.locales, sources, memory, args.batch_size, args.memory_path
            )
            write_json(args.memory_path, memory)

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
