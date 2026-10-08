"""Regressions for preserving colored petal surfaces while removing guides."""
import unittest
from process_bloom_imagegen_spritesheets import AxisLayout, GridLayout, LineCluster, build_alpha


class BloomAlphaTest(unittest.TestCase):
    def alpha(self, pixels, grid=None):
        return build_alpha(
            width=3, height=2, pixels=bytearray(pixels), key=(0, 255, 255),
            guide_colors=[(255, 0, 255)], guide_tolerance=180,
            transparent_threshold=48, opaque_threshold=118, grid=grid,
        )[0]

    def test_native_alpha_keeps_magenta_and_purple_petals(self):
        # Broad guide tolerances formerly punched holes in both colors.
        pixels = [255, 0, 255, 255, 200, 60, 200, 220, 0, 0, 0, 0] * 2
        self.assertEqual(self.alpha(pixels), bytearray([255, 220, 0, 255, 220, 0]))

    def test_chroma_key_keeps_matching_guide_color_inside_artwork(self):
        pixels = [255, 0, 255, 255, 200, 60, 200, 255, 0, 255, 255, 255] * 2
        self.assertEqual(self.alpha(pixels), bytearray([255, 255, 0, 255, 255, 0]))

    def test_only_recovered_line_is_removed(self):
        pixels = [255, 0, 255, 255, 255, 0, 255, 255, 0, 0, 0, 0] * 2
        grid = GridLayout(1, 1,
            AxisLayout([(1, 3)], [LineCluster(0, 0, 1)], 'guides'),
            AxisLayout([(0, 2)], [], 'equal'))
        self.assertEqual(self.alpha(pixels, grid), bytearray([0, 255, 0, 0, 255, 0]))


if __name__ == '__main__':
    unittest.main()
