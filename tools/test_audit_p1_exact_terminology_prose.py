import json
import sys
import tempfile
import unittest
from pathlib import Path

TOOLS = Path(__file__).resolve().parent
if str(TOOLS) not in sys.path:
    sys.path.insert(0, str(TOOLS))

import audit_p1_exact_terminology_prose as audit


class TerminologyProseAuditTest(unittest.TestCase):
    def test_committed_audit_matches_complete_locale_matrix(self) -> None:
        expected = audit.generate()
        self.assertEqual(expected, audit.read_json(audit.OUTPUT))
        self.assertEqual(22, expected["summary"]["locale_count"])
        self.assertEqual(3454, expected["summary"]["concept_occurrences"])
        self.assertEqual(501, expected["summary"]["withheld_pending_review"])
        self.assertGreater(expected["summary"]["native_review_required"], 0)

    def test_approved_locale_cannot_retain_prose_drift(self) -> None:
        catalog = audit.read_json(audit.LOCALIZATIONS)
        catalog["locales"]["cs"]["terminology"]["status"] = "approved"
        previous = audit.LOCALIZATIONS
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "catalog.json"
            path.write_text(json.dumps(catalog, ensure_ascii=False), encoding="utf-8")
            audit.LOCALIZATIONS = path
            try:
                with self.assertRaisesRegex(audit.AuditError, "approved terminology"):
                    audit.generate()
            finally:
                audit.LOCALIZATIONS = previous


if __name__ == "__main__":
    unittest.main()