"""Protect curated release notes against incomplete automatic PR summaries."""

import unittest

from extract_release_notes import extract_release_notes


class ExtractReleaseNotesTest(unittest.TestCase):
    def test_keeps_all_sections_and_excludes_other_releases(self):
        changelog = """# Changelog
## [Unreleased]
- Future feature
## [1.6.0] — 2026-10-15
### Added
- New timers
  Continued detail.
### Changed
- Updated guides
### Fixed
- Preserve notes
## [1.5.0] — 2026-08-11
- Previous feature
[1.6.0]: https://example.com/compare/v1.5.0...v1.6.0
"""
        notes = extract_release_notes(changelog.replace("\n", "\r\n"), "v1.6.0")
        self.assertIn("## 1.6.0 — 2026-10-15", notes)
        self.assertIn("- New timers\n  Continued detail.", notes)
        self.assertIn("### Changed\n- Updated guides", notes)
        self.assertIn("### Fixed\n- Preserve notes", notes)
        self.assertIn("**Full changelog**: https://example.com/compare/", notes)
        self.assertNotIn("Future feature", notes)
        self.assertNotIn("Previous feature", notes)

    def test_last_release_excludes_reference_definitions(self):
        notes = extract_release_notes(
            "## [1.0.0] — 2026-05-26\n\n- First release\n\n"
            "[1.0.0]: https://example.com/release\n", "v1.0.0"
        )
        self.assertNotIn("[1.0.0]:", notes)
        self.assertIn("- First release", notes)

    def test_missing_or_undated_version_fails(self):
        for changelog in ("## [1.5.0] — 2026-08-11\n- Old", "## [1.6.0]\n- New"):
            with self.subTest(changelog=changelog), self.assertRaises(ValueError):
                extract_release_notes(changelog, "v1.6.0")

    def test_duplicate_release_fails(self):
        entry = "## [1.6.0] — 2026-10-15\n- Change\n"
        with self.assertRaises(ValueError):
            extract_release_notes(entry + entry, "v1.6.0")

    def test_empty_release_fails(self):
        with self.assertRaises(ValueError):
            extract_release_notes("## [1.6.0] — 2026-10-15\n### Added\n", "v1.6.0")

    def test_invalid_date_fails(self):
        with self.assertRaises(ValueError):
            extract_release_notes("## [1.6.0] — 2026-02-30\n- Change\n", "v1.6.0")


if __name__ == "__main__":
    unittest.main()
