#!/usr/bin/env python3
"""Create a complete, field-aware review packet for one exact-guidance draft."""

from __future__ import annotations

import argparse
import importlib.util
import re
import sys
import unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GENERATOR_PATH = ROOT / "tools/generate_p1_exact_guidance_localizations.py"

CS_SUSPICIOUS = {
    r"\bsládk\w*": "beer-brewer sense",
    r"\bpivovar\w*": "brewery sense",
    r"\bpozem\w*": "land/grounds sense",
    r"\bpokut\w*": "penalty/fines sense",
    r"\bpostel\w*": "literal bed sense",
    r"\bvýkvět\w*": "flower/bloom sense",
    r"\brozkvět\w*": "flower/bloom sense",
    r"\bpoměru\s+\d+:\d+": "timer rendered as a ratio",
    r"ZXQMARK|QXZ": "translation marker leakage",
}
DOMAIN_ENGLISH = re.compile(
    r"\b(brewer|grounds|bloom|fines|slurry|drawdown|dripper)\b",
    re.IGNORECASE,
)


def load_generator():
    spec = importlib.util.spec_from_file_location("p1_localizer", GENERATOR_PATH)
    if spec is None or spec.loader is None:
        raise RuntimeError("Cannot load localization generator")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--locale", required=True)
    parser.add_argument("--draft", type=Path)
    parser.add_argument("--output", type=Path)
    return parser.parse_args()


def user_visible_values(stage: dict) -> list[tuple[str, str]]:
    guidance = stage["guidance"]
    values = [(key, stage[key]) for key in LOCALIZER.STAGE_TEXT_KEYS]
    values.extend((f"full.{key}", guidance["full"][key]) for key in LOCALIZER.FULL_TEXT_KEYS)
    values.extend(
        (f"concise.{key}", guidance["concise"][key])
        for key in LOCALIZER.CONCISE_TEXT_KEYS
    )
    values.extend(
        (f"focused.{key}", guidance["focused"][key])
        for key in LOCALIZER.FOCUSED_TEXT_KEYS
    )
    return values


def automated_issues(locale: str, source_stage: dict, stage: dict) -> list[str]:
    issues: set[str] = set()
    for field, value in user_visible_values(stage):
        if value == LOCALIZER.SOURCE_NONE:
            continue
        english_terms = {
            match.group(0).lower() for match in DOMAIN_ENGLISH.finditer(value)
        }
        if locale == "cs":
            english_terms.discard("dripper")
        if english_terms:
            issues.add(
                f"{field}: untranslated ambiguous English coffee term "
                f"({', '.join(sorted(english_terms))})",
            )
        if any(unicodedata.category(character) == "Cf" for character in value):
            issues.add(f"{field}: invisible formatting character")
        if locale == "cs":
            for pattern, description in CS_SUSPICIOUS.items():
                if re.search(pattern, value, re.IGNORECASE):
                    issues.add(f"{field}: {description}")
    if locale == "cs":
        source_action = source_stage["action"].lower()
        draft_action = stage["action"]
        if draft_action and draft_action[0].islower():
            issues.add("action: sentence begins with a lowercase letter")
        concept_checks = (
            ("level the bed", r"(?:za|vy|s)?rovn|rovnoměr", "level/flatten concept may be missing"),
            ("bloom", r"bloom|smáčen|předspař|navlh", "coffee bloom concept may be missing"),
            ("spin", r"otoč|krouživ|zakruž|promíchej", "spin motion may be missing"),
            ("swirl", r"otoč|krouživ|zakruž|promíchej", "swirl motion may be missing"),
            ("drawdown", r"odte|odtok|odvod|sceď|vykap|okap|vyt[eé]", "drawdown concept may be missing"),
            ("drain", r"odte|odtok|odvod|sceď|vykap|okap|vyt[eé]", "drainage concept may be missing"),
        )
        for source_term, expected, description in concept_checks:
            if source_term in source_action and not re.search(expected, draft_action, re.IGNORECASE):
                issues.add(f"action: {description}")
        source_warning = source_stage["warning"]
        draft_warning = stage["warning"]
        if re.search(r"\b(do not|never|must not|avoid|without)\b", source_warning, re.IGNORECASE):
            if not re.search(r"\b(ne\w*|nikdy|bez|vyvar\w*|zabraň\w*)\b", draft_warning, re.IGNORECASE):
                issues.add("warning: prohibition or negation may be missing")
    return sorted(issues)


def escape_cell(value: str) -> str:
    return value.replace("|", "\\|").replace("\n", "<br>")


def render_packet(
    source: dict, draft: dict, locale: str, review: dict | None = None,
) -> str:
    stages: list[tuple[dict, dict, dict]] = []
    stage_reviews = (review or {}).get("stages", {})
    recipe_reviews = (review or {}).get("recipes", {})
    issue_count = 0
    lines = [
        f"# Exact P1 {locale} localization review packet",
        "",
        "Every row requires native-language coffee-domain review. Automated checks are",
        "triage only and never constitute approval.",
        "",
        "Review each row for action accuracy, warning polarity/severity, observable",
        "completion, exact quantities, natural terminology, and image alt-text fidelity.",
        "",
    ]
    for source_recipe, draft_recipe in zip(source["recipes"], draft["recipes"], strict=True):
        for source_stage, draft_stage in zip(
            source_recipe["stages"], draft_recipe["stages"], strict=True,
        ):
            stages.append((source_recipe, source_stage, draft_stage))
    approved_count = sum(
        1 for stage_review in stage_reviews.values()
        if stage_review.get("status") == "approved"
    )
    native_ready_count = sum(
        1 for stage_review in stage_reviews.values()
        if stage_review.get("status") == "ready_for_native_review"
    )
    recipe_native_ready_count = sum(
        1 for recipe_review in recipe_reviews.values()
        if recipe_review.get("status") == "ready_for_native_review"
    )
    category_counts = {
        field: sum(
            1 for stage_review in stage_reviews.values()
            if field in stage_review.get("reviewed_fields", [])
        )
        for field in sorted(LOCALIZER.REQUIRED_EDITORIAL_FIELDS)
    }
    lines.extend([
        f"- Recipes: {len(source['recipes'])}",
        f"- Stages: {len(stages)}",
        f"- Fully approved stages: {approved_count}/{len(stages)}",
        f"- Ready for native review stages: {native_ready_count}/{len(stages)}",
        f"- Ready for native review recipes: "
        f"{recipe_native_ready_count}/{len(source['recipes'])}",
        *(
            f"- {field.replace('_', ' ').title()} reviewed: "
            f"{count}/{len(stages)}"
            for field, count in category_counts.items()
        ),
        "",
    ])
    for index, (recipe, source_stage, draft_stage) in enumerate(stages, start=1):
        issues = automated_issues(locale, source_stage, draft_stage)
        issue_count += len(issues)
        full = draft_stage["guidance"]["full"]
        stage_key = f"{recipe['recipe_id']}/{source_stage['stage_id']}"
        stage_review = stage_reviews.get(stage_key, {})
        status = stage_review.get("status", "not_started")
        reviewed_fields = ", ".join(stage_review.get("reviewed_fields", [])) or "none"
        mark = "x" if status == "approved" else " "
        lines.extend([
            f"## [{mark}] {index:03d} · {recipe['recipe_id']} · {source_stage['stage_id']}",
            "",
            "| Field | Canonical English | Draft |",
            "|---|---|---|",
            f"| Action | {escape_cell(source_stage['action'])} | {escape_cell(draft_stage['action'])} |",
            f"| Warning | {escape_cell(source_stage['warning'])} | {escape_cell(draft_stage['warning'])} |",
            f"| Completion | {escape_cell(source_stage['completion_criterion'])} | "
            f"{escape_cell(draft_stage['completion_criterion'])} |",
            f"| Explanation | {escape_cell(source_stage['guidance']['full']['concise_explanation'])} | "
            f"{escape_cell(full['concise_explanation'])} |",
            f"| Alt text | {escape_cell(source_stage['guidance']['full']['accessible_alt_text'])} | "
            f"{escape_cell(full['accessible_alt_text'])} |",
            "",
            "Automated findings: " + ("; ".join(issues) if issues else "none"),
            f"Editorial status: {status}; reviewed fields: {reviewed_fields}",
            "",
        ])
    lines.insert(11, f"- Automated findings: {issue_count}")
    return "\n".join(lines) + "\n"


def main() -> int:
    args = parse_args()
    source = LOCALIZER.read_json(LOCALIZER.SOURCE)
    draft_path = args.draft or LOCALIZER.draft_resource_path(args.locale)
    output = args.output or (
        LOCALIZER.DRAFT_ROOT / args.locale / "review-packet.md"
    )
    draft = LOCALIZER.read_json(draft_path)
    LOCALIZER.validate_document(source, draft, args.locale)
    review_path = LOCALIZER.editorial_review_path(args.locale)
    review = LOCALIZER.read_json(review_path) if review_path.exists() else None
    packet = render_packet(source, draft, args.locale, review)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(packet, encoding="utf-8", newline="\n")
    print(f"Wrote {output}")
    return 0


LOCALIZER = load_generator()


if __name__ == "__main__":
    sys.exit(main())
