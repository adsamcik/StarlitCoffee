"""Geometric acceptance tests using independently drawn source silhouettes."""
import tempfile
import unittest
from unittest.mock import patch
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

import monochrome_vectorizer as m


class VectorizerTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)

    def tearDown(self):
        self.temp.cleanup()

    def source(self, name, paint, opaque=False):
        image = Image.new("RGBA", (512, 512), "white" if opaque else (0, 0, 0, 0))
        paint(ImageDraw.Draw(image))
        path = self.root / (name+".png")
        image.save(path)
        return path

    def run_shape(self, name, paint, components=1, holes=0, max_segments=60, opaque=False, error=.055):
        path = self.source(name, paint, opaque)
        report = m.vectorize(path, self.root, error=error)
        self.assertTrue(report["quality"]["passed"], report["quality"])
        self.assertEqual(report["quality"]["source_topology"], {"components": components, "holes": holes})
        self.assertLessEqual(report["total_segments"], max_segments)
        self.assertGreater(report["quality"]["silhouette_iou"], .97)
        for size in ("24", "36", "48"):
            self.assertLess(report["quality"]["actual_sizes"][size]["mean_absolute_coverage_error"], .015)
        return path, report

    def test_circle_uses_four_to_eight_cubics(self):
        _, report = self.run_shape("circle", lambda d: d.ellipse((80, 80, 432, 432), fill="black"), max_segments=8)
        self.assertGreaterEqual(report["total_control_points"], 8)

    def test_rounded_rectangle_preserves_long_edges(self):
        self.run_shape("rounded", lambda d: d.rounded_rectangle((65, 140, 447, 372), radius=48, fill="black"), max_segments=20)

    def test_sharp_rectangle(self):
        self.run_shape("sharp", lambda d: d.rectangle((80, 100, 432, 412), fill="black"), max_segments=16)

    def test_hole_nonzero_winding(self):
        def paint(d):
            d.ellipse((50, 50, 462, 462), fill="black")
            d.ellipse((155, 155, 357, 357), fill=(0, 0, 0, 0))
        self.run_shape("ring", paint, holes=1, max_segments=16)

    def test_nested_island_and_multiple_components(self):
        def paint(d):
            d.ellipse((45, 60, 370, 450), fill="black")
            d.ellipse((110, 140, 305, 365), fill=(0, 0, 0, 0))
            d.ellipse((165, 205, 250, 300), fill="black")
            d.ellipse((403, 215, 466, 278), fill="black")
        self.run_shape("nested", paint, components=3, holes=1, max_segments=40)

    def test_tooth_and_notch_survive(self):
        def paint(d):
            d.polygon([(70, 140), (210, 140), (210, 85), (265, 85), (265, 140), (442, 140), (442, 400), (300, 400), (300, 325), (240, 325), (240, 400), (70, 400)], fill="black")
        self.run_shape("notched", paint, max_segments=48)

    def test_thin_ring_survives(self):
        def paint(d):
            d.ellipse((65, 65, 447, 447), fill="black")
            d.ellipse((80, 80, 432, 432), fill=(0, 0, 0, 0))
        self.run_shape("thin", paint, holes=1, max_segments=64, error=.025)

    def test_white_background_and_deterministic_bytes(self):
        path, first = self.run_shape("opaque", lambda d: d.ellipse((80, 100, 432, 412), fill="black"), opaque=True, max_segments=10)
        original = {ext: (self.root/("opaque"+ext)).read_bytes() for ext in (".svg", ".xml", ".json")}
        second = m.vectorize(path, self.root)
        self.assertEqual(first, second)
        for ext, expected in original.items():
            self.assertEqual(expected, (self.root/("opaque"+ext)).read_bytes())

    def test_noise_is_rejected_not_deleted(self):
        def paint(d):
            d.ellipse((80, 80, 432, 432), fill="black")
            d.point((30, 30), fill="black")
        with self.assertRaisesRegex(ValueError, "Tiny contour|too small"):
            m.vectorize(self.source("noise", paint), self.root)

    def test_empty_clipped_and_multicolor_rejected(self):
        for name, paint, message in [
            ("empty", lambda d: None, "opaque ink|Empty"),
            ("clipped", lambda d: d.rectangle((0, 40, 140, 400), fill="black"), "touches"),
            ("color", lambda d: (d.rectangle((40, 40, 250, 400), fill="black"), d.rectangle((251, 40, 470, 400), fill="red")), "multiple")]:
            with self.subTest(name=name), self.assertRaisesRegex(ValueError, message):
                m.vectorize(self.source(name, paint), self.root)

    def test_translucent_silhouette_is_rejected(self):
        path = self.source("translucent", lambda d: d.ellipse((80, 80, 432, 432), fill=(0, 0, 0, 153)))
        with self.assertRaisesRegex(ValueError, "opaque ink"):
            m.vectorize(path, self.root)

    def test_rejected_render_does_not_replace_accepted_vector(self):
        path = self.source("retain", lambda d: d.ellipse((80, 80, 432, 432), fill="black"))
        accepted = self.root / "retain.svg"
        accepted.write_text("previously accepted artifact", encoding="utf-8")
        with patch.object(m, "render_svg", side_effect=lambda svg, size: Image.new("L", (size, size))):
            report = m.vectorize(path, self.root)
        self.assertFalse(report["quality"]["passed"])
        self.assertEqual(accepted.read_text(encoding="utf-8"), "previously accepted artifact")
        self.assertTrue((self.root / "retain.rejected.svg").exists())
        self.assertTrue((self.root / "retain.rejected.json").exists())

    def test_serialized_curve_corruption_fails_quality(self):
        path = self.source("circle", lambda d: d.ellipse((80, 80, 432, 432), fill="black"))
        coverage, transform = m.load_coverage(path)
        cs = [c*transform["scale"]+transform["offset"] for c in m.contours(coverage)]
        bad = '<svg xmlns="http://www.w3.org/2000/svg"><path d="M2 2 L22 2 L22 22 L2 22 L2 2 Z"/></svg>'
        metrics, *_ = m.assess(bad, coverage, transform, cs, .055)
        self.assertFalse(metrics["passed"])
        self.assertGreater(metrics["boundary"]["vector_to_source_max"], 3)
        self.assertIn("Serialized smooth join exceeds 0.1 degree tangent mismatch", metrics["failures"])

    @staticmethod
    def rectangle_contour(left, top, right, bottom, hole=False):
        points = np.array([[left, top], [right, top], [right, bottom], [left, bottom]], dtype=float)
        if hole:
            points = points[::-1]
        segments = [np.array([points[i], points[(i+1)%4]]) for i in range(4)]
        return segments, points

    def test_compound_export_partition_preserves_nested_holes_and_every_coordinate(self):
        # Two disconnected shapes, with an island and another hole inside the
        # first shape's hole. Separate holes must never become filled paths.
        geometry = [self.rectangle_contour(1.123456, 1, 15, 23),
                    self.rectangle_contour(17, 5, 23, 19),
                    self.rectangle_contour(3, 3, 13, 21, hole=True),
                    self.rectangle_contour(5, 5, 11, 19),
                    self.rectangle_contour(7, 7, 9, 17, hole=True)]
        paths, contours = map(list, zip(*geometry))
        limit = max(len(m.path_data([paths[0], paths[2]])), len(m.path_data([paths[3], paths[4]])))
        groups = m.export_groups(paths, contours, limit)
        self.assertEqual(groups, [[0, 2], [1], [3, 4]])
        self.assertTrue(all(len(m.path_data([paths[i] for i in group])) <= limit for group in groups))
        original_contours = sorted(m.path_data([path]) for path in paths)
        partitioned_contours = sorted(m.path_data([paths[i]]) for group in groups for i in group)
        self.assertEqual(original_contours, partitioned_contours)
        def svg(data):
            return '<svg xmlns="http://www.w3.org/2000/svg">'+''.join('<path d="'+d+'"/>' for d in data)+'</svg>'
        combined = svg([m.path_data(paths)])
        partitioned = svg([m.path_data([paths[i] for i in group]) for group in groups])
        for size in (24, 36, 48, 384):
            # Match production supersampling; PDFium uses different direct
            # antialias fast paths for a standalone axis-aligned rectangle.
            before = m.render_svg(combined, size*8).resize((size, size), Image.Resampling.LANCZOS)
            after = m.render_svg(partitioned, size*8).resize((size, size), Image.Resampling.LANCZOS)
            np.testing.assert_array_equal(np.asarray(before), np.asarray(after))

    def test_over_budget_connected_component_rejected_without_precision_loss(self):
        path, contour = self.rectangle_contour(1.123456, 1, 15, 23)
        original = m.path_data([path])
        with self.assertRaisesRegex(ValueError, "connected compound path exceeds"):
            m.export_groups([path], [contour], 8)
        self.assertEqual(original, m.path_data([path]))

    def test_relative_compaction_preserves_six_decimal_cubic_coordinates(self):
        paths = m.parse_path("M12.123456 8.765432 C12.246912 7.654321 13.123456 6.654321 14.345678 9.876543 C15.456789 11.234567 10.123456 10.765432 12.123456 8.765432 Z")
        absolute = m.path_data(paths)
        compact = m.compact_path_data(paths)
        self.assertLess(len(compact), len(absolute))
        self.assertEqual(absolute, m.path_data(m.parse_path(compact)))
        self.assertEqual(compact, m.export_path_data(paths, len(compact)))
        for size in (24, 36, 48):
            def render(d):
                svg = '<svg xmlns="http://www.w3.org/2000/svg"><path d="'+d+'"/></svg>'
                return np.asarray(m.render_svg(svg, size*8).resize((size, size), Image.Resampling.LANCZOS))
            np.testing.assert_array_equal(render(absolute), render(compact))

    def test_export_cannot_turn_orphan_hole_into_filled_shape(self):
        outer, a = self.rectangle_contour(1, 1, 10, 20)
        hole, b = self.rectangle_contour(15, 5, 20, 15, hole=True)
        with self.assertRaisesRegex(ValueError, "assign a hole"):
            m.export_groups([outer, hole], [a, b], max(len(m.path_data([outer])), len(m.path_data([hole]))))


if __name__ == "__main__":
    unittest.main()
