#!/usr/bin/env python3
"""Focused tests for exact-guidance catalog-based terminology promotion gates."""

from __future__ import annotations

import copy
import importlib.util
import unittest
from pathlib import Path

TOOL_PATH = Path(__file__).with_name("generate_p1_exact_guidance_localizations.py")
SPEC = importlib.util.spec_from_file_location("p1_localization_tool", TOOL_PATH)
assert SPEC is not None and SPEC.loader is not None
TOOL = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(TOOL)


class TerminologyCatalogPromotionTest(unittest.TestCase):
    def setUp(self) -> None:
        self.source = TOOL.read_json(TOOL.SOURCE)
        catalog = TOOL.read_json(TOOL.LOCALIZATION_SOURCE)
        self.locale = copy.deepcopy(catalog["locales"]["cs"]["terminology"])

    def approved_locale(self) -> dict:
        locale = copy.deepcopy(self.locale)
        locale["status"] = "approved"
        locale["reviewer"] = "Native Czech coffee reviewer"
        locale["reviewed_on"] = "2026-08-06"
        locale["insufficient_evidence_count"] = 0
        evidence_pool = list(dict.fromkeys(
            source_id
            for term in locale["terms"]
            for source_id in term["evidence_source_ids"]
        ))
        for term in locale["terms"]:
            term["review_status"] = "approved"
            term["evidence_source_ids"] = list(dict.fromkeys(
                term["evidence_source_ids"] + evidence_pool
            ))[:2]
        return locale

    def test_researched_catalog_has_complete_structure(self) -> None:
        TOOL.terminology_catalog_locale(self.source, "cs", require_approved=False)

    def test_canonical_concepts_match_runtime_sidecar(self) -> None:
        concepts = TOOL.canonical_terminology_concepts(self.source)
        self.assertEqual(TOOL.REQUIRED_TERMINOLOGY_CONCEPTS, {concept["id"] for concept in concepts})

    def test_checked_in_english_runtime_glossary_is_valid(self) -> None:
        glossary = TOOL.read_json(TOOL.terminology_resource_path("en"))
        TOOL.validate_terminology_resource_document(self.source, glossary, "en")

    def test_catalog_requires_localized_control_copy(self) -> None:
        locale = copy.deepcopy(self.locale)
        del locale["ui_copy"]["heading"]
        with self.assertRaisesRegex(TOOL.LocalizationError, "UI copy is incomplete"):
            TOOL.terminology_catalog_locale(self.source, "cs", False, override=locale)

    def test_approved_catalog_generates_policy_aware_runtime_glossary(self) -> None:
        glossary = TOOL.terminology_resource_document(
            self.source,
            "cs",
            catalog_locale=self.approved_locale(),
        )
        TOOL.validate_terminology_resource_document(self.source, glossary, "cs")
        drawdown = next(term for term in glossary["terms"] if term["concept_id"] == "drawdown")
        self.assertEqual("dokapání (drawdown)", drawdown["preferred_local"])
        self.assertEqual("contextual_first_occurrence", drawdown["english_reference_policy"])
        self.assertEqual("Zobrazit anglické termíny", glossary["ui_copy"]["show_english_terms"])

    def test_production_requires_native_catalog_approval(self) -> None:
        with self.assertRaisesRegex(TOOL.LocalizationError, "catalog is not approved"):
            TOOL.terminology_catalog_locale(self.source, "cs", require_approved=True)

    def test_production_requires_named_and_dated_reviewer(self) -> None:
        locale = self.approved_locale()
        locale["reviewer"] = None
        with self.assertRaisesRegex(TOOL.LocalizationError, "reviewer is missing"):
            TOOL.terminology_catalog_locale(self.source, "cs", True, override=locale)
        locale["reviewer"] = "Native coffee reviewer"
        locale["reviewed_on"] = None
        with self.assertRaisesRegex(TOOL.LocalizationError, "date must use YYYY-MM-DD"):
            TOOL.terminology_catalog_locale(self.source, "cs", True, override=locale)

    def test_approved_catalog_cannot_retain_withheld_terms(self) -> None:
        locale = self.approved_locale()
        locale["terms"][0]["display_policy"] = "withhold_pending_review"
        with self.assertRaisesRegex(TOOL.LocalizationError, "unresolved terms or evidence"):
            TOOL.terminology_catalog_locale(self.source, "cs", True, override=locale)

    def test_generic_locale_cannot_promote_region_dependent_terms(self) -> None:
        catalog = TOOL.read_json(TOOL.LOCALIZATION_SOURCE)
        locale = copy.deepcopy(catalog["locales"]["pt"]["terminology"])
        locale["status"] = "approved"
        locale["reviewer"] = "Portuguese coffee reviewer"
        locale["reviewed_on"] = "2026-08-06"
        evidence_pool = list(dict.fromkeys(
            source_id
            for term in locale["terms"]
            for source_id in term["evidence_source_ids"]
        ))
        for term in locale["terms"]:
            term["review_status"] = "approved"
            term["evidence_source_ids"] = list(dict.fromkeys(
                term["evidence_source_ids"] + evidence_pool
            ))[:2]

        with self.assertRaisesRegex(TOOL.LocalizationError, "generic locale cannot promote"):
            TOOL.terminology_catalog_locale(self.source, "pt", True, override=locale)

        TOOL.terminology_catalog_locale(self.source, "pt-BR", True, override=locale)
        TOOL.terminology_catalog_locale(self.source, "pt-PT", True, override=locale)


if __name__ == "__main__":
    unittest.main()
