"""Author the vessel family from shared filled shapes and restrained cutouts.

No bitmap tracing or painting is involved. Historical artwork stays untouched.
"""
from pathlib import Path
import hashlib
import json
import xml.etree.ElementTree as ET

BASE = Path(__file__).resolve().parent
ORIGINAL = BASE.parent / "rebuild-20261004"
SVG_NS = "{http://www.w3.org/2000/svg}"


def ellipse(cx, cy, rx, ry, hole=False):
    sign = 1 if hole else -1
    return (f"M{cx-rx} {cy} C{cx-rx} {cy+sign*ry*.5523} {cx-rx*.5523} {cy+sign*ry} {cx} {cy+sign*ry} "
            f"C{cx+rx*.5523} {cy+sign*ry} {cx+rx} {cy+sign*ry*.5523} {cx+rx} {cy} "
            f"C{cx+rx} {cy-sign*ry*.5523} {cx+rx*.5523} {cy-sign*ry} {cx} {cy-sign*ry} "
            f"C{cx-rx*.5523} {cy-sign*ry} {cx-rx} {cy-sign*ry*.5523} {cx-rx} {cy} Z")


def rounded_rect(x, y, w, h, r, hole=False):
    if hole:
        return (f"M{x+r} {y} Q{x} {y} {x} {y+r} L{x} {y+h-r} Q{x} {y+h} {x+r} {y+h} "
                f"L{x+w-r} {y+h} Q{x+w} {y+h} {x+w} {y+h-r} L{x+w} {y+r} "
                f"Q{x+w} {y} {x+w-r} {y} Z")
    return (f"M{x+r} {y} L{x+w-r} {y} Q{x+w} {y} {x+w} {y+r} L{x+w} {y+h-r} "
            f"Q{x+w} {y+h} {x+w-r} {y+h} L{x+r} {y+h} Q{x} {y+h} {x} {y+h-r} "
            f"L{x} {y+r} Q{x} {y} {x+r} {y} Z")


def handle(cx, cy, rx, ry, wall=72):
    return ellipse(cx, cy, rx, ry) + " " + ellipse(cx+8, cy, rx-wall, ry-wall, hole=True)


def open_vessel(outline, mouth, grip=None, details=()):
    result = [] if grip is None else [handle(*grip)]
    result.append(outline + " " + ellipse(*mouth, hole=True) + " " + " ".join(details))
    return result


def glass(left, right, top, bottom, base_inset, reflection=True):
    cx = (left+right)/2
    outer = (f"M{left} {top} C{left} {top-56} {cx-130} {top-76} {cx} {top-76} "
             f"C{cx+130} {top-76} {right} {top-56} {right} {top} "
             f"L{right-base_inset} {bottom-42} Q{right-base_inset-8} {bottom} {cx} {bottom} "
             f"Q{left+base_inset+8} {bottom} {left+base_inset} {bottom-42} Z")
    lower_left, lower_right = left+base_inset+24, right-base_inset-24
    band = (f"M{lower_left} {bottom-94} L{lower_left+4} {bottom-64} "
            f"C{cx-94} {bottom-39} {cx+94} {bottom-39} {lower_right-4} {bottom-64} "
            f"L{lower_right} {bottom-94} C{cx+94} {bottom-69} {cx-94} {bottom-69} {lower_left} {bottom-94} Z")
    details = [band]
    if reflection:
        details.append(f"M{left+56} {top+108} Q{left+32} {top+108} {left+36} {top+134} "
                       f"L{left+base_inset+26} {bottom-190} Q{left+base_inset+30} {bottom-166} {left+base_inset+52} {bottom-169} "
                       f"Q{left+base_inset+76} {bottom-172} {left+base_inset+72} {bottom-198} "
                       f"L{left+80} {top+128} Q{left+78} {top+104} {left+56} {top+108} Z")
    return open_vessel(outer, (cx, top-2, (right-left)/2-48, 40), details=details)


def double_wall(left, right, top, bottom, grip=None):
    cx = (left+right)/2
    outer = (f"M{left} {top} C{left} {top-62} {cx-145} {top-82} {cx} {top-82} "
             f"C{cx+145} {top-82} {right} {top-62} {right} {top} "
             f"C{right+6} {bottom-150} {right-48} {bottom} {cx} {bottom} "
             f"C{left+48} {bottom} {left-6} {bottom-150} {left} {top} Z")
    seam = (f"M{left+60} {top+80} C{left+62} {bottom-122} {left+106} {bottom-54} {cx} {bottom-54} "
            f"C{right-106} {bottom-54} {right-62} {bottom-122} {right-60} {top+80} "
            f"L{right-104} {top+80} C{right-106} {bottom-170} {right-138} {bottom-104} {cx} {bottom-104} "
            f"C{left+138} {bottom-104} {left+106} {bottom-170} {left+104} {top+80} Z")
    return open_vessel(outer, (cx, top-4, (right-left)/2-48, 40), grip, (seam,))


def press(with_handle=True, double=False):
    left, right = (228, 724) if with_handle else (208, 816)
    cx = (left+right)/2
    paths = [handle(774, 522, 118, 206)] if with_handle else []
    frame = rounded_rect(left, 300, right-left, 574, 84)
    frame += " " + rounded_rect(left+64, 364, right-left-128, 440, 60, hole=True)
    paths.append(frame)
    paths.append(rounded_rect(cx-28, 122, 56, 600, 28))
    paths.append(ellipse(cx, 126, 82, 40))
    paths.append(ellipse(cx, 274, (right-left)/2+26, 52))
    coffee = (f"M{left+88} 560 C{left+154} 584 {right-154} 584 {right-88} 560 "
              f"L{right-88} 748 Q{right-88} 780 {right-120} 780 L{left+120} 780 "
              f"Q{left+88} 780 {left+88} 748 Z")
    paths.append(coffee)
    if double:
        paths.append(rounded_rect(left-58, 346, 34, 442, 17))
        paths.append(rounded_rect(right+24, 346, 34, 442, 17))
    return paths


def build():
    icons = {}
    icons["espresso"] = open_vessel(
        "M202 264 C202 204 292 178 412 178 C532 178 622 204 622 264 "
        "L610 500 C604 626 544 688 412 688 C280 688 220 626 214 500 Z",
        (412, 258, 164, 38), (668, 394, 108, 126, 62))
    icons["espresso"].append("M142 752 Q128 749 132 769 C142 828 258 850 448 850 "
                             "C638 850 754 828 764 769 Q768 749 754 752 "
                             "C662 778 234 778 142 752 Z")
    icons["cortado"] = glass(208, 816, 172, 886, 64)
    icons["cappuccino"] = open_vessel(
        "M126 254 C126 190 246 164 410 164 C574 164 696 190 696 254 "
        "C684 526 606 746 410 746 C214 746 138 526 126 254 Z",
        (410, 248, 236, 42), (766, 406, 116, 138, 68))
    icons["mug"] = open_vessel(
        "M138 190 C138 130 248 106 410 106 C572 106 682 130 682 190 "
        "L680 674 Q680 846 510 846 L310 846 Q140 846 140 674 Z",
        (410, 184, 224, 40), (754, 400, 126, 174))
    travel = ET.parse(BASE.parent / "travel-refinement-20261006/travel.svg").getroot()
    icons["travel"] = [path.attrib["d"] for path in travel.iter(SVG_NS+"path")]
    icons["takeaway"] = [
        "M256 254 C336 282 430 294 512 294 C594 294 688 282 768 254 "
        "L714 828 Q710 886 512 886 Q314 886 310 828 Z "
        "M318 544 L330 652 C420 684 604 684 694 652 L706 544 C604 576 420 576 318 544 Z",
        "M266 170 C276 128 372 104 512 104 C536 104 560 105 586 108 "
        "C584 96 596 90 614 92 L646 98 C660 102 660 112 654 122 "
        "C714 132 752 150 758 170 L778 216 Q782 230 768 234 "
        "C688 258 592 270 512 270 C432 270 336 258 256 234 Q242 230 246 216 Z"]
    icons["latte_glass"] = glass(142, 882, 226, 870, 102)
    carafe = ("M302 174 C302 124 392 104 512 104 C632 104 722 124 722 174 "
              "C722 212 668 250 662 314 C656 400 784 534 804 662 "
              "C830 816 734 892 512 892 C290 892 194 816 220 662 "
              "C240 534 368 400 362 314 C356 250 302 212 302 174 Z")
    icons["carafe"] = open_vessel(carafe, (512, 166, 160, 36))
    icons["french_press"] = press()
    icons["kettle"] = [
        "M242 444 C242 228 332 102 512 102 C692 102 782 228 782 444 "
        "L694 444 C694 276 634 190 512 190 C390 190 330 276 330 444 Z",
        "M276 686 C130 662 124 538 120 472 C116 414 98 384 68 380 "
        "Q44 376 50 352 Q54 328 80 334 C166 348 176 416 180 476 "
        "C184 550 202 592 304 606 Z",
        "M288 406 C350 444 674 444 736 406 C754 462 780 650 780 736 "
        "Q780 880 512 880 Q244 880 244 736 C244 650 270 462 288 406 Z",
        ellipse(512, 370, 194, 42), rounded_rect(458, 290, 108, 92, 36)]
    jar = ("M330 164 C330 120 408 100 512 100 C616 100 694 120 694 164 "
           "L694 264 Q794 294 794 402 L794 766 Q794 888 672 888 "
           "L352 888 Q230 888 230 766 L230 402 Q230 294 330 264 Z")
    icons["mason_jar"] = open_vessel(jar, (512, 158, 134, 32), details=(
        "M354 232 L354 258 C436 278 588 278 670 258 L670 232 C588 252 436 252 354 232 Z",))
    bowl = ("M122 334 C122 264 286 236 512 236 C738 236 902 264 902 334 "
            "C884 600 764 782 594 806 L430 806 C260 782 140 600 122 334 Z")
    icons["bowl"] = open_vessel(bowl, (512, 322, 338, 52))
    icons["double_wall_espresso"] = double_wall(144, 880, 278, 802)
    icons["double_wall_tumbler"] = double_wall(176, 848, 194, 890)
    icons["double_wall_mug"] = double_wall(120, 690, 212, 862, (766, 452, 126, 170))
    icons["tall_latte_glass"] = glass(298, 726, 160, 910, 48)
    taster = ("M316 168 C316 122 394 102 512 102 C630 102 708 122 708 168 "
              "C708 224 656 280 656 346 C656 452 748 512 750 656 "
              "C752 764 670 828 584 842 L608 878 Q612 906 512 906 "
              "Q412 906 416 878 L440 842 C354 828 272 764 274 656 "
              "C276 512 368 452 368 346 C368 280 316 224 316 168 Z")
    icons["aroma_taster"] = open_vessel(taster, (512, 160, 146, 32))
    icons["spherical_latte"] = open_vessel(
        "M138 264 C138 202 250 178 412 178 C574 178 686 202 686 264 "
        "C732 510 664 686 504 768 L504 798 Q504 820 412 820 "
        "Q320 820 320 798 L320 768 C160 686 92 510 138 264 Z",
        (412, 254, 224, 40), (760, 416, 116, 138, 68))
    icons["spouted_espresso_server"] = open_vessel(
        "M190 286 C260 216 368 198 476 198 C606 198 688 226 688 278 "
        "L696 506 Q712 766 476 766 Q240 766 224 562 L192 376 "
        "L98 302 Q84 288 102 286 Z",
        (476, 278, 168, 40), (758, 426, 126, 142))
    icons["dot_carafe"] = [ellipse(512, 172, 102, 94)]
    icons["dot_carafe"] += [
        "M364 268 C432 288 592 288 660 268 L654 330 C622 380 644 424 700 494 "
        "C784 598 804 724 754 804 Q702 890 512 890 Q322 890 270 804 "
        "C220 724 240 598 324 494 C380 424 402 380 370 330 Z "
        "M350 574 C312 644 304 702 310 744 Q314 770 338 766 Q362 762 358 736 "
        "C354 690 370 640 394 598 Q406 574 384 562 Q362 550 350 574 Z"]
    icons["lid_server"] = [ellipse(512, 188, 96, 36),
        "M206 280 C226 210 352 190 512 190 C672 190 798 210 818 280 "
        "Q822 298 804 304 C648 346 376 346 220 304 Q202 298 206 280 Z",
        "M230 336 C364 372 660 372 794 336 C832 400 886 566 886 678 "
        "Q886 828 512 828 Q138 828 138 678 C138 566 192 400 230 336 Z"]
    bee = ("M290 180 C290 128 390 106 512 106 C634 106 734 128 734 180 "
           "C734 232 672 288 672 348 C672 414 730 468 750 540 "
           "C770 614 746 644 784 718 C846 840 772 906 512 906 "
           "C252 906 178 840 240 718 C278 644 254 614 274 540 "
           "C294 468 352 414 352 348 C352 288 290 232 290 180 Z")
    icons["beehive_server"] = open_vessel(bee, (512, 172, 168, 36), details=(
        "M300 510 L294 538 C408 582 616 582 730 538 L724 510 C616 554 408 554 300 510 Z",
        "M268 696 L262 724 C398 772 626 772 762 724 L756 696 C626 744 398 744 268 696 Z"))
    icons["barista_server"] = open_vessel(
        "M202 220 C254 162 350 140 466 140 C602 140 682 164 682 216 "
        "L708 722 Q714 866 466 866 Q218 866 218 722 L202 320 "
        "L114 238 Q96 218 120 216 Z",
        (466, 216, 174, 40), (778, 468, 126, 194))
    angular = ("M286 184 C286 134 382 112 512 112 C642 112 738 134 738 184 "
               "C738 224 670 268 662 316 L854 722 Q872 748 856 774 "
               "L792 872 Q782 890 754 890 L270 890 Q242 890 232 872 "
               "L168 774 Q152 748 170 722 L362 316 C354 268 286 224 286 184 Z")
    icons["angular_server"] = open_vessel(angular, (512, 176, 172, 36))
    icons["faceted_cortado"] = glass(152, 872, 200, 882, 116, reflection=False)
    # Shallow filled facets use the same 28-unit seam as the other glass bases.
    icons["faceted_cortado"][0] += (
        " M254 360 L294 754 L324 760 L284 366 Z"
        " M740 360 L700 754 L730 748 L770 354 Z")
    ceramic = ("M122 334 C122 264 286 236 512 236 C738 236 902 264 902 334 "
               "C884 574 790 710 608 782 L608 816 Q608 842 512 842 "
               "Q416 842 416 816 L416 782 C234 710 140 574 122 334 Z")
    icons["ceramic_latte_bowl"] = open_vessel(ceramic, (512, 322, 338, 52))
    icons["thermal_carafe"] = [handle(776, 474, 122, 222),
        "M234 238 C324 266 548 266 638 238 L678 778 Q678 902 470 902 "
        "Q262 902 262 778 L252 330 L176 260 Q162 240 184 238 Z",
        "M254 162 C254 118 346 96 470 96 C594 96 686 118 686 162 "
        "L704 202 Q708 218 692 224 C602 248 338 248 248 224 "
        "Q232 218 236 202 Z"]
    icons["double_wall_press"] = press(with_handle=False, double=True)
    return icons


def main():
    catalog = json.loads((ORIGINAL / "catalog.json").read_text())["entries"]
    history = json.loads((ORIGINAL / "trace-report.json").read_text())["icons"]
    icons = build()
    assert set(icons) == {entry["key"] for entry in catalog}
    destination = BASE / "svg"
    destination.mkdir(parents=True, exist_ok=True)
    report = {"date": "2026-10-06", "authoring": "Shared hand-authored filled vector family; historical generated sources preserved.",
              "rules": {"viewport": "0 0 1024 1024", "nominal_mouth_side_rim_units": 48, "nominal_handle_wall_units": 72,
                        "lid_seam_units": 24, "minimum_detail_units": 28,
                        "style": "Rounded filled silhouettes, elliptical openings, restrained anatomy details, theme tint."}, "icons": {}}
    for entry in catalog:
        key = entry["key"]
        svg = '<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 1024 1024">\n  <g fill="currentColor" fill-rule="nonzero">\n'
        svg += "\n".join(f'    <path d="{path}"/>' for path in icons[key]) + "\n  </g>\n</svg>\n"
        path = destination / f"{key}.svg"
        path.write_text(svg, encoding="utf-8", newline="\n")
        report["icons"][key] = {"svg_sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
                               "based_on_svg_sha256": history[key]["svg_sha256"], "paths": len(icons[key])}
    (BASE / "report.json").write_text(json.dumps(report, indent=2)+"\n", encoding="utf-8", newline="\n")
    print(f"Authored {len(icons)} consistent vessel vectors.")


if __name__ == "__main__":
    main()
