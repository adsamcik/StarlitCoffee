#!/usr/bin/env python3
"""Deterministic monocolor raster -> sparse cubic SVG / Android VectorDrawable.

Independent implementation of constrained least-squares cubic fitting inspired by
Schneider, Graphics Gems (1990). See docs/monochrome-vectorizer.md for contracts.
"""
from __future__ import annotations

import argparse
from functools import lru_cache
import hashlib
import importlib.metadata
import io
import json
import math
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

try:
    import numpy as np
    from PIL import Image, ImageDraw
    import pypdfium2 as pdfium
    from reportlab.pdfgen import canvas
except ImportError as exc:
    raise SystemExit("Missing vectorizer dependency: install tools/monochrome-vectorizer-requirements.txt in a project virtual environment. " + str(exc))

VERSION = "1.1.0"
VIEW = 24.0
QC_SIZE = 384
ANDROID_PATH_LIMIT = 3000


def unit(v):
    n = np.linalg.norm(v)
    return v / n if n > 1e-12 else np.array([1.0, 0.0])


def load_coverage(path):
    image = Image.open(path).convert("RGBA")
    a = np.asarray(image, dtype=float) / 255
    if min(image.size) < 32 or max(image.size) > 4096:
        raise ValueError("Source must be between 32 and 4096 pixels on each axis.")
    if np.min(a[:, :, 3]) < 0.99:
        coverage, mode = a[:, :, 3], "alpha"
        if np.count_nonzero(coverage >= .98) < 16:
            raise ValueError("Transparent source must contain an opaque ink interior; translucent silhouettes are unsupported.")
        # Alpha alone is meaningful only for a single foreground ink.
        rgb = a[:, :, :3][coverage > .95]
        if len(rgb) and float(np.max(np.std(rgb, axis=0))) > .06:
            raise ValueError("Transparent source contains multiple foreground colors; provide a flat monocolor icon.")
    else:
        rgb = a[:, :, :3]
        border = np.concatenate([rgb[0], rgb[-1], rgb[:, 0], rgb[:, -1]])
        if np.min(np.mean(border, axis=0)) < .96:
            raise ValueError("Opaque source requires a clean white background.")
        coverage, mode = 1 - rgb.mean(axis=2), "white-background"
        if float(np.max(np.ptp(rgb, axis=2))) > .07:
            raise ValueError("Opaque source must use black ink on white; use alpha for colored ink.")
    yy, xx = np.nonzero(coverage >= .5)
    if len(xx) < 16:
        raise ValueError("Empty or degenerate foreground (fewer than 16 source pixels).")
    if xx.min() == 0 or yy.min() == 0 or xx.max() == image.width - 1 or yy.max() == image.height - 1:
        raise ValueError("Foreground touches the source edge; add transparent/white margin first.")
    bbox = (int(xx.min()), int(yy.min()), int(xx.max() + 1), int(yy.max() + 1))
    scale = 20 / max(bbox[2] - bbox[0], bbox[3] - bbox[1])
    offset = np.array([12 - (bbox[0] + bbox[2]) * scale / 2, 12 - (bbox[1] + bbox[3]) * scale / 2])
    return coverage, {"mode": mode, "source_size": list(image.size), "bbox": list(bbox), "scale": scale, "offset": offset.tolist()}


def contours(coverage):
    """Subpixel 50% isocontours; foreground 4-connected at saddle cells.

    Directed edges keep foreground on the right. Thus outer/hole winding is
    opposite, supporting nonzero fill identically in SVG, PDF and Android.
    """
    a = np.pad(coverage, 1)
    on = a >= .5
    cases = on[:-1, :-1].astype(np.uint8) + 2 * on[:-1, 1:] + 4 * on[1:, 1:] + 8 * on[1:, :-1]
    pairs = {1: [(0, 3)], 2: [(1, 0)], 3: [(1, 3)], 4: [(2, 1)], 5: [(0, 3), (2, 1)], 6: [(2, 0)], 7: [(2, 3)], 8: [(3, 2)], 9: [(0, 2)], 10: [(1, 0), (3, 2)], 11: [(1, 2)], 12: [(3, 1)], 13: [(0, 1)], 14: [(3, 0)]}
    edges, points = {}, {}
    ys, xs = np.nonzero((cases != 0) & (cases != 15))
    for y, x in zip(ys.tolist(), xs.tolist()):
        keys = [(0, y, x), (1, y, x + 1), (0, y + 1, x), (1, y, x)]
        vals = [a[y, x], a[y, x + 1], a[y + 1, x + 1], a[y + 1, x]]
        starts = [(x, y), (x + 1, y), (x, y + 1), (x, y)]
        values = [(vals[0], vals[1]), (vals[1], vals[2]), (vals[3], vals[2]), (vals[0], vals[3])]
        for first, last in pairs[int(cases[y, x])]:
            for e in (first, last):
                if keys[e] not in points:
                    lo, hi = values[e]
                    t = float((.5 - lo) / (hi - lo))
                    dx, dy = ((t, 0) if e % 2 == 0 else (0, t))
                    points[keys[e]] = np.array(starts[e], dtype=float) + (dx - .5, dy - .5)
            if keys[first] in edges:
                raise ValueError("Ambiguous contour junction.")
            edges[keys[first]] = keys[last]
    result = []
    while edges:
        start = min(edges)
        p, key = [], start
        while True:
            p.append(points[key])
            if key not in edges:
                raise ValueError("Open contour encountered.")
            key = edges.pop(key)
            if key == start:
                break
        result.append(np.array(p))
    return result


def area(p):
    return float(np.sum(p[:, 0] * np.roll(p[:, 1], -1) - p[:, 1] * np.roll(p[:, 0], -1)) / 2)


def resample(p, spacing=.035):
    q = np.vstack([p, p[0]])
    lengths = np.linalg.norm(np.diff(q, axis=0), axis=1)
    keep = np.r_[True, lengths > 1e-10]
    q = q[keep]
    d = np.r_[0, np.cumsum(np.linalg.norm(np.diff(q, axis=0), axis=1))]
    if d[-1] < .12:
        raise ValueError("A component is too small to vectorize reliably; clean the source rather than silently deleting it.")
    u = np.linspace(0, d[-1], max(12, math.ceil(d[-1] / spacing)), endpoint=False)
    return np.column_stack([np.interp(u, d, q[:, k]) for k in (0, 1)])


def bezier(c, u):
    u = np.asarray(u)[:, None]
    return (1-u)**3*c[0] + 3*(1-u)**2*u*c[1] + 3*(1-u)*u**2*c[2] + u**3*c[3]


def fit_span(p, t0, t1, error):
    """Positive tangent lengths, least squares and monotone Newton refinement."""
    d = np.linalg.norm(np.diff(p, axis=0), axis=1)
    u = np.r_[0, np.cumsum(d)] / np.sum(d)
    chord = np.linalg.norm(p[-1]-p[0])
    if chord < 1e-9:
        return None
    # True straight edges use two anchors, provided they preserve the join.
    direction = unit(p[-1]-p[0])
    line_error = np.max(np.linalg.norm(p - (p[0] + np.clip((p-p[0]) @ direction, 0, chord)[:, None]*direction), axis=1))
    if line_error <= error and np.dot(t0, direction) > 1-1e-10 and np.dot(t1, direction) > 1-1e-10:
        return (np.array([p[0], p[-1]]), float(line_error))
    if len(p) == 2:
        c = np.array([p[0], p[0]+t0*chord/3, p[-1]-t1*chord/3, p[-1]])
        samples = bezier(c, np.linspace(0, 1, 17))
        projected = p[0]+np.clip((samples-p[0]) @ direction, 0, chord)[:, None]*direction
        deviation = float(np.max(np.linalg.norm(samples-projected, axis=1)))
        return (c, deviation) if deviation <= error else None
    best = None
    for _ in range(8):
        b0, b1, b2, b3 = (1-u)**3, 3*(1-u)**2*u, 3*(1-u)*u**2, u**3
        a = np.stack([b1[:, None]*t0, -b2[:, None]*t1], axis=2).reshape(-1, 2)
        rhs = (p - (b0+b1)[:, None]*p[0] - (b2+b3)[:, None]*p[-1]).ravel()
        lengths = np.linalg.lstsq(a, rhs, rcond=None)[0]
        if np.any(lengths <= 1e-7) or np.any(lengths > chord*2):
            return best
        c = np.array([p[0], p[0]+lengths[0]*t0, p[-1]-lengths[1]*t1, p[-1]])
        q = bezier(c, u)
        err = float(np.max(np.linalg.norm(q-p, axis=1)))
        if err <= error and (best is None or err < best[1]):
            best = c, err
        v = u[:, None]
        d1 = 3*(1-v)**2*(c[1]-c[0])+6*(1-v)*v*(c[2]-c[1])+3*v**2*(c[3]-c[2])
        d2 = 6*(1-v)*(c[2]-2*c[1]+c[0])+6*v*(c[3]-2*c[2]+c[1])
        denom = np.sum(d1*d1+(q-p)*d2, axis=1)
        step = np.divide(np.sum((q-p)*d1, axis=1), denom, out=np.zeros_like(u), where=np.abs(denom)>1e-12)
        new_u = np.clip(u-step, 0, 1)
        new_u[0], new_u[-1] = 0, 1
        if np.any(np.diff(new_u) <= 0):
            break
        if np.max(np.abs(new_u-u)) < 1e-6:
            break
        u = new_u
    return best


def fit_contour(raw, error):
    p = resample(raw)
    n = len(p)
    # Estimate corners at a meaningful visual scale, not one raster step.
    radius = max(2, min(8, n//16))
    incoming = p-np.roll(p, radius, axis=0)
    outgoing = np.roll(p, -radius, axis=0)-p
    cos = np.sum(incoming*outgoing, axis=1) / np.maximum(1e-12, np.linalg.norm(incoming, axis=1)*np.linalg.norm(outgoing, axis=1))
    candidates = np.flatnonzero(cos < math.cos(math.radians(53)))
    corners = []
    for i in sorted(candidates.tolist(), key=lambda k: (cos[k], k)):
        if all(min(abs(i-j), n-abs(i-j)) > radius for j in corners):
            corners.append(i)
    start = min(corners) if corners else int(np.lexsort((p[:, 0], p[:, 1]))[0])
    p = np.roll(p, -start, axis=0)
    corners = sorted((k-start) % n for k in corners)
    corner_set = set(corners)
    t = np.array([unit(p[(i+radius)%n]-p[(i-radius)%n]) for i in range(n)])
    p = np.vstack([p, p[0]])
    t = np.vstack([t, t[0]])
    # Initial candidates at regular arclength plus every preserved corner.
    knots = sorted(set([0, n] + corners + list(range(0, n, max(3, round(.65/.035))))))

    @lru_cache(maxsize=None)
    def span(i, j):
        if any(i < c < j for c in corners):
            return None
        left = unit(p[min(i+radius, j)]-p[i]) if i in corner_set else t[i]
        right = unit(p[j]-p[max(i, j-radius)]) if j % n in corner_set else t[j]
        return fit_span(p[i:j+1], left, right, error)

    # Refine infeasible elementary spans until the graph has a valid route.
    for _ in range(12):
        additions = []
        for i, j in zip(knots[:-1], knots[1:]):
            if span(i, j) is None:
                if j-i <= 1:
                    raise ValueError("Fit cannot meet tolerance without a degenerate segment; use a cleaner or larger source.")
                additions.append((i+j)//2)
        if not additions:
            break
        knots = sorted(set(knots+additions))
    # Shortest path over feasible spans, lexicographic segment count/error.
    # This is globally optimal over this candidate graph, not all possible knots.
    costs = [(10**9, float("inf"))] * len(knots)
    costs[0] = (0, 0.)
    prev = {}
    for b in range(1, len(knots)):
        for a in range(max(0, b-32), b):
            if costs[a][0] >= 10**9:
                continue
            fitted = span(knots[a], knots[b])
            if fitted is None:
                continue
            c, err = fitted
            cost = (costs[a][0]+1, costs[a][1]+err*err)
            if cost < costs[b]:
                costs[b], prev[b] = cost, (a, c)
    if len(knots)-1 not in prev:
        raise ValueError("No topology-preserving fit within the requested tolerance.")
    segments, corner_starts, k = [], [], len(knots)-1
    while k:
        k, c = prev[k]
        segments.append(c)
        corner_starts.append(knots[k] in corner_set)
    segments.reverse()
    corner_starts.reverse()
    return segments, len(corners), len(raw), len(knots), corner_starts


def path_data(paths):
    def pair(p):
        return " ".join(f"{v:.6f}".rstrip("0").rstrip(".") or "0" for v in p)
    result = []
    for segments in paths:
        result.append("M" + pair(segments[0][0]))
        for c in segments:
            result.append(("L" if len(c)==2 else "C") + " ".join(pair(p) for p in c[1:]))
        result.append("Z")
    return " ".join(result)


def compact_path_data(paths):
    """Lossless relative commands from the same six-decimal integer grid."""
    def number(value):
        sign = "-" if value < 0 else ""
        whole, fraction = divmod(abs(int(value)), 1_000_000)
        text = str(whole) + ("."+f"{fraction:06d}".rstrip("0") if fraction else "")
        if text.startswith("0."):
            text = text[1:]
        return sign+text
    def fixed(point):
        return np.array([int(f"{value:.6f}".replace(".", "")) for value in point], dtype=np.int64)
    def pair(point):
        return " ".join(number(value) for value in point)
    result = []
    for segments in paths:
        result.append("M"+pair(fixed(segments[0][0])))
        for segment in segments:
            start = fixed(segment[0])
            result.append(("l" if len(segment)==2 else "c") + " ".join(pair(fixed(point)-start) for point in segment[1:]))
        result.append("Z")
    return "".join(result)


def export_path_data(paths, limit=ANDROID_PATH_LIMIT):
    absolute = path_data(paths)
    if len(absolute) <= limit:
        return absolute
    compact = compact_path_data(paths)
    if len(compact) > limit:
        raise ValueError(f"A connected compound path exceeds Android's {limit}-character budget; simplify the source. Precision and holes are not discarded.")
    return compact


def contains_points(polygon, points):
    """Strict ray-crossing containment for source contour grouping."""
    a, b = polygon, np.roll(polygon, -1, axis=0)
    result = []
    for x, y in points:
        crossing = (a[:, 1] > y) != (b[:, 1] > y)
        edges_a, edges_b = a[crossing], b[crossing]
        intersections = edges_a[:, 0] + (y-edges_a[:, 1])*(edges_b[:, 0]-edges_a[:, 0])/(edges_b[:, 1]-edges_a[:, 1])
        result.append(bool(np.count_nonzero(intersections > x) % 2))
    return np.array(result)


def export_groups(paths, source_contours, limit=ANDROID_PATH_LIMIT):
    """Partition oversized compound artwork without changing any coordinates.

    A hole stays with its smallest containing foreground contour. Nested islands
    are separate foreground groups. Never split a hole into its own filled path.
    """
    if len(path_data(paths)) <= limit:
        return [list(range(len(paths)))]
    signed = [area(p) for p in source_contours]
    groups = {i: [i] for i, value in enumerate(signed) if value > 0}
    for i, value in enumerate(signed):
        if value >= 0:
            continue
        samples = source_contours[i][::max(1, len(source_contours[i])//16)]
        parents = [j for j in groups if signed[j] > abs(value) and np.all(contains_points(source_contours[j], samples))]
        if not parents:
            raise ValueError("Cannot safely assign a hole to an Android compound path; inspect source contour containment.")
        parent = min(parents, key=lambda j: (signed[j], j))
        groups[parent].append(i)
    result = list(groups.values())
    for group in result:
        export_path_data([paths[i] for i in group], limit)
    return result


def svg_path_groups(svg):
    return [parse_path(node.attrib["d"]) for node in ET.fromstring(svg).findall("{http://www.w3.org/2000/svg}path")]


def parse_path(d):
    tokens = re.findall(r"[MLCZmlcz]|-?(?:\d*\.)?\d+(?:[eE][+-]?\d+)?", d)
    paths, segments, current = [], [], None
    i = 0
    while i < len(tokens):
        command = tokens[i]
        i += 1
        if command.upper() == "Z":
            if np.linalg.norm(current-segments[0][0]) > .0002:
                raise ValueError("Serialized contour is not closed.")
            paths.append(segments)
            segments = []
            continue
        count = {"M": 2, "L": 2, "C": 6}[command.upper()]
        values = np.array([float(v) for v in tokens[i:i+count]]).reshape(-1, 2)
        i += count
        if command.islower() and current is not None:
            values = values+current
        if command.upper() != "M":
            segments.append(np.vstack([current, values]))
        current = values[-1]
    return paths


def render_svg(svg, pixels):
    """Render serialized SVG's restricted path grammar using PDFium cubics.

    This deliberately uses an independent production rasterizer, not fitting
    polylines. It is an SVG-subset adapter, not a general SVG implementation.
    """
    groups = svg_path_groups(svg)
    stream = io.BytesIO()
    pdf = canvas.Canvas(stream, pagesize=(VIEW, VIEW), invariant=1, pageCompression=0)
    pdf.translate(0, VIEW)
    pdf.scale(1, -1)
    pdf.setFillColorRGB(0, 0, 0)
    for paths in groups:
        p = pdf.beginPath()
        for segments in paths:
            p.moveTo(*segments[0][0])
            for c in segments:
                p.lineTo(*c[-1]) if len(c)==2 else p.curveTo(*c[1:].ravel())
            p.close()
        pdf.drawPath(p, fill=1, stroke=0, fillMode=1)
    pdf.showPage()
    pdf.save()
    document = pdfium.PdfDocument(stream.getvalue())
    page = document[0]
    bitmap = page.render(scale=pixels/VIEW)
    result = Image.fromarray(255-np.asarray(bitmap.to_pil().convert("L")))
    bitmap.close()
    page.close()
    document.close()
    return result


def normalized_source(coverage, transform, pixels):
    scale = transform["scale"] * pixels/VIEW
    off = np.array(transform["offset"]) * pixels/VIEW
    source = Image.fromarray(np.round(coverage*255).astype(np.uint8))
    return source.transform((pixels, pixels), Image.Transform.AFFINE, (1/scale, 0, -off[0]/scale, 0, 1/scale, -off[1]/scale), Image.Resampling.BICUBIC)


def topology(mask):
    cs = contours(mask.astype(float))
    signed = [area(c) for c in cs]
    return {"components": sum(v > 0 for v in signed), "holes": sum(v < 0 for v in signed)}


def distances(a, b):
    if not len(a) or not len(b):
        return np.array([float("inf")])
    answer = []
    for chunk in np.array_split(a, max(1, math.ceil(len(a)/128))):
        answer.append(np.sqrt(np.min(np.sum((chunk[:, None, :]-b[None, :, :])**2, axis=2), axis=1)))
    return np.concatenate(answer)


def curve_samples(paths, spacing=.015):
    out = []
    for segments in paths:
        points = []
        for c in segments:
            length = float(np.sum(np.linalg.norm(np.diff(c, axis=0), axis=1)))
            u = np.linspace(0, 1, max(3, math.ceil(length/spacing)))
            points.append(c[0]+u[:, None]*(c[-1]-c[0]) if len(c)==2 else bezier(c, u))
        out.append(np.vstack(points))
    return out


def assess(svg, coverage, transform, source_contours, error, corner_starts=None):
    source = normalized_source(coverage, transform, QC_SIZE)
    vector = render_svg(svg, QC_SIZE*3).resize((QC_SIZE, QC_SIZE), Image.Resampling.LANCZOS)
    a, b = np.asarray(source)>=128, np.asarray(vector)>=128
    intersection, union = np.count_nonzero(a & b), np.count_nonzero(a | b)
    paths = [path for group in svg_path_groups(svg) for path in group]
    sampled = curve_samples(paths)
    src = np.vstack([resample(c, .015) for c in source_contours])
    dst = np.vstack(sampled)
    forward, backward = distances(src, dst), distances(dst, src)
    combined = np.r_[forward, backward]
    source_topology = {"components": sum(area(c)>0 for c in source_contours), "holes": sum(area(c)<0 for c in source_contours)}
    vector_topology = topology(b)
    winding = [int(np.sign(area(c))) for c in source_contours] == [int(np.sign(area(c))) for c in sampled]
    join_angles, smooth_angles = [], []
    for contour_index, segments in enumerate(paths):
        for i, (left, right) in enumerate(zip(segments, segments[1:]+segments[:1])):
            tin = unit(left[-1]-left[-2])
            tout = unit(right[1]-right[0])
            angle = math.degrees(math.acos(float(np.clip(tin @ tout, -1, 1))))
            join_angles.append(angle)
            is_corner = corner_starts is not None and corner_starts[contour_index][(i+1)%len(segments)]
            if not is_corner:
                smooth_angles.append(angle)
    smooth_max = max(smooth_angles, default=0.)
    limits = {"min_iou": .97, "max_boundary_viewport_units": error*1.65+.025, "max_boundary_p99_viewport_units": error*1.3+.02}
    failures = []
    iou = intersection/max(1, union)
    if iou < limits["min_iou"]:
        failures.append("Silhouette IoU below acceptance floor")
    if np.max(combined) > limits["max_boundary_viewport_units"]:
        failures.append("Maximum bidirectional boundary deviation exceeds limit")
    if np.percentile(combined, 99) > limits["max_boundary_p99_viewport_units"]:
        failures.append("99th percentile boundary deviation exceeds limit")
    if source_topology != vector_topology or not winding:
        failures.append("Component, hole or winding topology changed")
    if smooth_max > .1:
        failures.append("Serialized smooth join exceeds 0.1 degree tangent mismatch")
    actual = {}
    for size in (24, 36, 48):
        s = normalized_source(coverage, transform, size*8).resize((size, size), Image.Resampling.LANCZOS)
        v = render_svg(svg, size*8).resize((size, size), Image.Resampling.LANCZOS)
        actual[str(size)] = {"mean_absolute_coverage_error": float(np.mean(np.abs(np.asarray(s, dtype=float)-np.asarray(v, dtype=float)))/255)}
    limits["max_actual_size_coverage_mae"] = .015
    if any(v["mean_absolute_coverage_error"] > limits["max_actual_size_coverage_mae"] for v in actual.values()):
        failures.append("Actual-size coverage error exceeds limit; check for translucent interiors or soft edges")
    metrics = {"passed": not failures, "failures": failures, "limits": limits, "silhouette_iou": iou, "boundary": {"source_to_vector_max": float(np.max(forward)), "vector_to_source_max": float(np.max(backward)), "p99": float(np.percentile(combined, 99)), "mean": float(np.mean(combined)), "sample_spacing": .015, "units": "24-unit viewport; sampled distance, not analytic Hausdorff"}, "source_topology": source_topology, "vector_topology": vector_topology, "winding_preserved": winding, "joins": {"smooth_max_tangent_mismatch_degrees": smooth_max, "preserved_corner_joins": len(join_angles)-len(smooth_angles), "smooth_joins": len(smooth_angles)}, "actual_sizes": actual}
    return metrics, source, vector, paths


def comparison(source, vector, svg, coverage, transform):
    bg = (249, 246, 240)
    image = Image.new("RGB", (900, 560), bg)
    draw = ImageDraw.Draw(image)
    draw.text((24, 12), "SOURCE / FITTED CUBIC / DIFFERENCE (blue source, red vector)", fill="#302821")
    def ink(mask):
        out = Image.new("RGB", mask.size, bg)
        out.paste((67, 51, 39), mask=mask)
        return out
    image.paste(ink(source.resize((260, 260))), (25, 48))
    image.paste(ink(vector.resize((260, 260))), (320, 48))
    a, b = np.asarray(source.resize((260, 260)), dtype=float)/255, np.asarray(vector.resize((260, 260)), dtype=float)/255
    diff = np.ones((260, 260, 3))*np.array(bg)
    diff -= np.minimum(a, b)[:, :, None]*np.array([95, 95, 95])
    diff[:, :, 0] -= np.maximum(a-b, 0)*240
    diff[:, :, 2] -= np.maximum(b-a, 0)*240
    image.paste(Image.fromarray(np.clip(diff, 0, 255).astype(np.uint8)), (615, 48))
    draw.text((25, 335), "Actual pixel sizes: source / vector", fill="#302821")
    for index, size in enumerate((24, 36, 48)):
        x = 30+index*260
        draw.text((x, 372), f"{size} px", fill="#302821")
        s = normalized_source(coverage, transform, size*8).resize((size, size), Image.Resampling.LANCZOS)
        v = render_svg(svg, size*8).resize((size, size), Image.Resampling.LANCZOS)
        image.paste(ink(s), (x, 405))
        image.paste(ink(v), (x+size+24, 405))
    draw.text((25, 506), "Production rasterizer: PDFium, using serialized SVG cubic paths and nonzero fill.", fill="#655b50")
    return image


def vectorize(source, output_dir, name=None, error=.055):
    source, output_dir = Path(source), Path(output_dir)
    name = name or source.stem
    if not re.fullmatch(r"[A-Za-z0-9_-]+", name):
        raise ValueError("Output name may contain only letters, digits, underscore and hyphen.")
    if not .015 <= error <= .15:
        raise ValueError("Error must be between .015 and .15 viewport units.")
    coverage, transform = load_coverage(source)
    cs = [c*transform["scale"]+transform["offset"] for c in contours(coverage)]
    if len(cs)>64:
        raise ValueError("More than 64 contours; clean source noise before tracing.")
    paths, info, corner_starts = [], [], []
    for c in cs:
        if abs(area(c)) < .004:
            raise ValueError("Tiny contour/noise detected; no foreground is silently dropped.")
        segments, corners, source_points, candidate_knots, corner_flags = fit_contour(c, error)
        paths.append(segments)
        corner_starts.append(corner_flags)
        info.append({"source_points": source_points, "segments": len(segments), "corners": corners, "candidate_knots": candidate_knots})
    groups = export_groups(paths, cs)
    data = [export_path_data([paths[i] for i in group]) for group in groups]
    svg_elements = ''.join(f'<path fill="currentColor" fill-rule="nonzero" d="{d}"/>' for d in data)
    svg = f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="24" height="24">{svg_elements}</svg>\n'
    android_elements = ''.join(f'    <path android:fillColor="#FF000000" android:fillType="nonZero" android:pathData="{d}"/>\n' for d in data)
    android = f'<?xml version="1.0" encoding="utf-8"?>\n<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">\n{android_elements}</vector>\n'
    order = [i for group in groups for i in group]
    metrics, normalized, rendered, serialized_paths = assess(svg, coverage, transform, [cs[i] for i in order], error, [corner_starts[i] for i in order])
    report = {"schema": 1, "algorithm": "subpixel-contours-schneider-constrained-dp", "version": VERSION, "source_file": source.name, "source_sha256": hashlib.sha256(source.read_bytes()).hexdigest(), "parameters": {"error": error, "viewport": VIEW, "padding": 2}, "normalization": transform, "dependencies": {name: importlib.metadata.version(name) for name in ("Pillow", "numpy", "reportlab", "pypdfium2")}, "contours": info, "total_segments": sum(len(p) for p in paths), "total_anchors": sum(len(p) for p in paths), "total_control_points": sum(2 for p in paths for c in p if len(c)==4), "svg_sha256": hashlib.sha256(svg.encode()).hexdigest(), "android_sha256": hashlib.sha256(android.encode()).hexdigest(), "quality": metrics}
    report["export"] = {"path_count": len(data), "path_data_lengths": [len(d) for d in data], "max_path_data_length": ANDROID_PATH_LIMIT, "contour_groups": groups, "coordinate_decimal_places": 6}
    output_dir.mkdir(parents=True, exist_ok=True)
    # Failed candidates remain explicitly named and cannot overwrite accepted art.
    suffix = "" if metrics["passed"] else ".rejected"
    (output_dir/(name+suffix+".svg")).write_text(svg, encoding="utf-8", newline="\n")
    (output_dir/(name+suffix+".xml")).write_text(android, encoding="utf-8", newline="\n")
    (output_dir/(name+suffix+".json")).write_text(json.dumps(report, indent=2, sort_keys=True)+"\n", encoding="utf-8", newline="\n")
    comparison(normalized, rendered, svg, coverage, transform).save(output_dir/(name+suffix+"-comparison.png"))
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--name")
    parser.add_argument("--error", type=float, default=.055, help="Maximum fitted sample error in a 24-unit viewport (default .055)")
    args = parser.parse_args()
    try:
        report = vectorize(args.source, args.output_dir, args.name, args.error)
    except (ValueError, OSError) as exc:
        parser.exit(2, f"Vectorization rejected: {exc}\n")
    print(json.dumps({"passed": report["quality"]["passed"], "segments": report["total_segments"], "iou": report["quality"]["silhouette_iou"], "boundary": report["quality"]["boundary"], "failures": report["quality"]["failures"]}, indent=2))
    return 0 if report["quality"]["passed"] else 2


if __name__ == "__main__":
    sys.exit(main())
