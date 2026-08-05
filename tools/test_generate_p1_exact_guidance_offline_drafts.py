import json
import sys
import unittest
from pathlib import Path

TOOLS = Path(__file__).resolve().parent
if str(TOOLS) not in sys.path:
    sys.path.insert(0, str(TOOLS))

import generate_p1_exact_guidance_localizations as base
import generate_p1_exact_guidance_offline_drafts as exact
import generate_p1_exact_terminology_offline_drafts as terminology


class OfflineExactGuidanceDraftTest(unittest.TestCase):
    def test_numeric_placeholders_restore_times_and_ranges(self) -> None:
        source = "At 2:00 and use 18–21 g"
        protected, replacements = exact.protect_numbers(source)
        self.assertEqual(
            "At StarlitAlpha:StarlitBeta and use StarlitGamma to StarlitDelta g",
            protected,
        )
        restored = exact.restore_protected_numbers(
            source,
            "V StarlitAlpha:StarlitBeta použijte StarlitGamma až StarlitDelta g",
            replacements,
            "cs",
        )
        self.assertEqual("V 2:00 použijte 18 až 21 g", restored)

    def test_added_small_numbers_are_spelled_out(self) -> None:
        self.assertEqual(
            "18 g kávy, pět až osm minut",
            exact.normalize_numbers(
                "18 g coffee, five-to-eight-minute drip",
                "18 g kávy, 5 až 8 minut",
                "cs",
            ),
        )

    def test_known_hallucination_sources_are_disambiguated(self) -> None:
        self.assertIn(
            "maximum fill line",
            exact.prepare_english(
                "Reservoir within maximum; carafe lid/valve correct"
            ),
        )
        self.assertEqual(
            "Controlled automatically by the coffee brewing machine",
            exact.prepare_english("Machine-controlled"),
        )
        self.assertIn(
            "liquid outlet is not blocked",
            exact.prepare_english("Paper seated and outlet unobstructed"),
        )

    def test_committed_memory_covers_every_non_english_locale(self) -> None:
        source = base.read_json(base.SOURCE)
        strings = base.translatable_strings(source)
        memory = base.read_json(exact.MEMORY_PATH)
        locales = list(exact.LOCALE_TARGETS)
        exact.validate_memory(memory, locales, strings)
        self.assertEqual(22, len(memory["translations"]))
        self.assertTrue(
            all(len(memory["translations"][locale]) == 827 for locale in locales)
        )


class OfflineTerminologyDraftTest(unittest.TestCase):
    def test_committed_drafts_cover_every_manual_locale(self) -> None:
        references = terminology.read_json(terminology.REFERENCE_MANIFEST)
        document = terminology.read_json(terminology.OUTPUT)
        terminology.validate(document, references)
        self.assertEqual(21, len(document["locales"]))
        self.assertTrue(
            all(len(draft["terms"]) == 12 for draft in document["locales"].values())
        )

    def test_regeneration_from_explicit_overrides_is_stable(self) -> None:
        references = terminology.read_json(terminology.REFERENCE_MANIFEST)
        expected = terminology.read_json(terminology.OUTPUT)
        self.assertEqual(expected, terminology.generate(references))


if __name__ == "__main__":
    unittest.main()
