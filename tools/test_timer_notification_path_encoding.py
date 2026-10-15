"""Verify lossless notification path encoding; run with Python's standard library."""
from decimal import Decimal
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

from import_timer_notification_cup import compact_traced_path

ROOT = Path(__file__).resolve().parents[1]
ANDROID = "{http://schemas.android.com/apk/res/android}"
SVG = "{http://www.w3.org/2000/svg}"


def geometry(data):
    """Independent segment comparison, treating monotonic straight cubics as lines."""
    tokens = re.findall(r"[A-Za-z]|[-+]?(?:\d*\.\d+|\d+\.?)(?:[eE][-+]?\d+)?", data)
    result, current, start, index = [], None, None, 0
    while index < len(tokens):
        command = tokens[index]
        count = {"M": 2, "C": 6, "H": 1, "V": 1, "Z": 0}[command]
        values = list(map(Decimal, tokens[index + 1:index + count + 1]))
        if command == "M":
            current = start = tuple(values)
            result.append((command, current))
        elif command == "Z":
            result.append((command, start))
            current = start
        elif command in ("H", "V"):
            current = (values[0], current[1]) if command == "H" else (current[0], values[0])
            result.append(("L", current))
        else:
            controls = [current, tuple(values[:2]), tuple(values[2:4]), tuple(values[4:])]
            axis = 1 if all(point[0] == current[0] for point in controls) else 0
            straight = all(point[1 - axis] == current[1 - axis] for point in controls)
            steps = [b[axis] - a[axis] for a, b in zip(controls, controls[1:])]
            monotonic = all(step >= 0 for step in steps) or all(step <= 0 for step in steps)
            current = controls[-1]
            result.append(("L", current) if straight and monotonic else ("C", tuple(values)))
        index += count + 1
    return result


class TimerNotificationPathEncodingTest(unittest.TestCase):
    def test_axis_aligned_monotonic_cubics_become_lines(self):
        self.assertEqual("M0,0H8V12H0Z", compact_traced_path(
            "M0,0C2.667,0,5.333,0,8,0C8,4,8,8,8,12C6,12,2,12,0,12Z"))

    def test_curves_and_backtracking_controls_are_preserved(self):
        for path in ("M0,0C2,1,5,0,8,0Z", "M0,0C-2,0,10,0,8,0Z"):
            self.assertEqual(path, compact_traced_path(path))

    def test_decimal_endpoints_and_subpath_winding_are_unchanged(self):
        path = "M.001,-2C.002,-2,.003,-2,.004,-2ZM8,8C8,6,8,2,8,0Z"
        self.assertEqual(geometry(path), geometry(compact_traced_path(path)))

    def test_both_approved_traces_match_native_geometry_below_lint_limit(self):
        base = ROOT / "docs/brewing/design/timer-notification-20261008"
        for name in ("outline", "bowl"):
            source = ET.parse(base / f"{name}.svg").getroot()
            native = ET.parse(ROOT / f"app/src/main/res/drawable/notification_cup_{name}.xml").getroot()
            source_paths = [element.attrib["d"] for element in source.iter(SVG + "path")]
            native_paths = [element.attrib[ANDROID + "pathData"] for element in native.iter("path")]
            self.assertEqual(list(map(geometry, source_paths)), list(map(geometry, native_paths)), name)
            self.assertTrue(all(len(path) < 3000 for path in native_paths), name)


if __name__ == "__main__":
    unittest.main()
