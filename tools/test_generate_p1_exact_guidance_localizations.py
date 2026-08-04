#!/usr/bin/env python3
"""Focused tests for exact-guidance terminology promotion gates."""

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


class TerminologyReviewValidationTest(unittest.TestCase):
    def setUp(self) -> None:
        self.review = TOOL.read_json(TOOL.editorial_review_path("cs"))

    def test_reviewed_draft_has_complete_evidence_structure(self) -> None:
        TOOL.validate_terminology_review(self.review, "cs", require_approved=False)

    def test_production_requires_native_approval(self) -> None:
        with self.assertRaisesRegex(
            TOOL.LocalizationError,
            "terminology review is not approved",
        ):
            TOOL.validate_terminology_review(self.review, "cs", require_approved=True)

    def test_production_requires_named_and_dated_reviewer(self) -> None:
        review = copy.deepcopy(self.review)
        review["terminology_review"]["status"] = "approved"
        with self.assertRaisesRegex(TOOL.LocalizationError, "reviewer is missing"):
            TOOL.validate_terminology_review(review, "cs", require_approved=True)

        review["terminology_review"]["reviewer"] = "Native coffee reviewer"
        with self.assertRaisesRegex(TOOL.LocalizationError, "date must use YYYY-MM-DD"):
            TOOL.validate_terminology_review(review, "cs", require_approved=True)

        review["terminology_review"]["reviewed_on"] = "2026-08-04"
        TOOL.validate_terminology_review(review, "cs", require_approved=True)

    def test_every_required_concept_needs_corroborating_sources(self) -> None:
        review = copy.deepcopy(self.review)
        del review["terminology_review"]["concepts"]["bloom"]
        with self.assertRaisesRegex(TOOL.LocalizationError, "missing concepts"):
            TOOL.validate_terminology_review(review, "cs", require_approved=False)

        review = copy.deepcopy(self.review)
        review["terminology_review"]["concepts"]["bloom"][
            "evidence_source_ids"
        ] = ["hario_cz"]
        with self.assertRaisesRegex(TOOL.LocalizationError, "evidence sources are invalid"):
            TOOL.validate_terminology_review(review, "cs", require_approved=False)

    def test_sources_must_span_independent_categories(self) -> None:
        review = copy.deepcopy(self.review)
        for source in review["terminology_review"]["sources"]:
            source["category"] = "specialty_retailer"
        with self.assertRaisesRegex(TOOL.LocalizationError, "two source categories"):
            TOOL.validate_terminology_review(review, "cs", require_approved=False)


if __name__ == "__main__":
    unittest.main()