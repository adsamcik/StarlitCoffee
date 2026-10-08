import tempfile
import unittest
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw
from prepare_bloom_alpha_atlas import prepare


class NativeAlphaAtlasTest(unittest.TestCase):
    def source(self, path):
        image = Image.new('RGBA', (1254, 1254))
        draw = ImageDraw.Draw(image)
        edges = [round(i * 1254 / 5) for i in range(6)]
        for i in range(25):
            x, y = edges[i % 5], edges[i // 5]
            draw.rectangle((x + 100, y + 210 - i * 6, x + 150, y + 225), fill=(255, 0, 255, 255))
        image.save(path)

    def test_fractional_cells_preserve_growth_and_colored_surfaces(self):
        with tempfile.TemporaryDirectory() as directory:
            source, output = Path(directory) / 'source.png', Path(directory) / 'output.png'
            self.source(source)
            result = prepare(source, output)
            self.assertEqual(result['frame_count'], 25)
            image = Image.open(output)
            self.assertEqual(image.size, (1280, 1280))
            heights = [f['bounds'][3] - f['bounds'][1] for f in result['frames']]
            self.assertGreater(heights[-1], heights[0] * 8)
            self.assertTrue(all(a <= b for a, b in zip(heights, heights[1:])))
            self.assertTrue(all(f['bounds'][3] == 233 for f in result['frames']))
            data = np.asarray(image)
            opaque = data[:, :, 3] >= 250
            self.assertTrue(np.all(data[opaque][:, :3] == [255, 0, 255]))

    def test_refuses_opaque_source_instead_of_keying_petals(self):
        with tempfile.TemporaryDirectory() as directory:
            source, output = Path(directory) / 'source.png', Path(directory) / 'output.png'
            Image.new('RGBA', (100, 100), (255, 0, 255, 255)).save(source)
            with self.assertRaisesRegex(ValueError, 'no native transparency'):
                prepare(source, output)
            self.assertFalse(output.exists())

    def test_refuses_empty_animation_cells(self):
        with tempfile.TemporaryDirectory() as directory:
            source, output = Path(directory) / 'source.png', Path(directory) / 'output.png'
            Image.new('RGBA', (100, 100)).save(source)
            with self.assertRaisesRegex(ValueError, 'Empty frame'):
                prepare(source, output)

    def test_recovers_uneven_rows_without_cutting_or_rescaling_art(self):
        with tempfile.TemporaryDirectory() as directory:
            source, output = Path(directory) / 'source.png', Path(directory) / 'output.png'
            image = Image.new('RGBA', (1280, 1280))
            draw = ImageDraw.Draw(image)
            # Row three extends beyond its nominal y=768 boundary. All subjects
            # have the same native dimensions despite unequal vertical spacing.
            bottoms = [235, 491, 785, 1018, 1260]
            for row, bottom in enumerate(bottoms):
                for column in range(5):
                    x = column * 256
                    draw.rectangle((x + 104, bottom - 150, x + 151, bottom), fill=(128, 20, 190, 255))
            image.save(source)
            result = prepare(source, output)
            self.assertGreater(result['source_row_edges'][3], 785)
            self.assertEqual(len(set(tuple(f['bounds']) for f in result['frames'])), 1)

    def test_refuses_clipped_source_art(self):
        with tempfile.TemporaryDirectory() as directory:
            source, output = Path(directory) / 'source.png', Path(directory) / 'output.png'
            self.source(source)
            image = Image.open(source)
            image.putpixel((110, 0), (255, 0, 255, 255))
            image.save(source)
            with self.assertRaisesRegex(ValueError, 'touches the source canvas edge'):
                prepare(source, output)

    def test_refuses_art_crossing_every_possible_row_separator(self):
        with tempfile.TemporaryDirectory() as directory:
            source, output = Path(directory) / 'source.png', Path(directory) / 'output.png'
            self.source(source)
            image = Image.open(source)
            ImageDraw.Draw(image).rectangle((110, 1, 112, 1252), fill=(128, 20, 190, 255))
            image.save(source)
            with self.assertRaisesRegex(ValueError, 'No transparent separator'):
                prepare(source, output)
            self.assertFalse(output.exists())


if __name__ == '__main__':
    unittest.main()
