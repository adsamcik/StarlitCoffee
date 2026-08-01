#!/usr/bin/env python3
"""Rebuild or verify the five retained brewing-instruction review candidates.

The source of truth is docs/brewing/illustration-generation-manifest.json.
This tool does not generate imagery. It deterministically converts a committed
raw PNG into the recorded WebP candidate and verifies the byte-level lineage.
"""

from __future__ import annotations

import argparse
import hashlib
import io
import json
import sys
from pathlib import Path
from typing import Any

from PIL import Image, __version__ as PILLOW_VERSION


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_MANIFEST = ROOT / "docs" / "brewing" / "illustration-generation-manifest.json"


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def sha256_file(path: Path) -> str:
    return sha256_bytes(path.read_bytes())


def repository_path(value: str) -> Path:
    path = (ROOT / value).resolve()
    try:
        path.relative_to(ROOT.resolve())
    except ValueError as error:
        raise ValueError(f"Manifest path escapes repository: {value}") from error
    return path


def read_manifest(path: Path) -> dict[str, Any]:
    try:
        manifest = json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError as error:
        raise ValueError(f"Manifest not found: {path}") from error
    except json.JSONDecodeError as error:
        raise ValueError(f"Invalid manifest JSON: {error}") from error

    if manifest.get("schema_version") != 1:
        raise ValueError("Unsupported illustration-generation manifest schema")
    candidates = manifest.get("candidates")
    if not isinstance(candidates, list) or not candidates:
        raise ValueError("Manifest must contain at least one candidate")
    return manifest


def image_errors(path: Path, expected_format: str, expected_mode: str, dimensions: tuple[int, int]) -> list[str]:
    errors: list[str] = []
    if not path.is_file():
        return [f"missing: {path.relative_to(ROOT)}"]

    try:
        with Image.open(path) as image:
            if image.format != expected_format:
                errors.append(
                    f"{path.relative_to(ROOT)}: expected {expected_format}, got {image.format or 'unknown'}"
                )
            if image.mode != expected_mode:
                errors.append(
                    f"{path.relative_to(ROOT)}: expected {expected_mode}, got {image.mode}"
                )
            if image.size != dimensions:
                errors.append(
                    f"{path.relative_to(ROOT)}: expected {dimensions[0]}x{dimensions[1]}, got {image.size[0]}x{image.size[1]}"
                )
            if getattr(image, "n_frames", 1) != 1:
                errors.append(f"{path.relative_to(ROOT)}: expected a static image")
    except OSError as error:
        errors.append(f"{path.relative_to(ROOT)}: unreadable image ({error})")
    return errors


def render_candidate(raw_path: Path, quality: int, method: int) -> bytes:
    with Image.open(raw_path) as source:
        rendered = source.convert("RGB").resize((1024, 768), Image.Resampling.LANCZOS)
        encoded = io.BytesIO()
        rendered.save(encoded, "WEBP", quality=quality, method=method)
        rendered.close()
    return encoded.getvalue()


def entry_errors(entry: dict[str, Any], write: bool) -> list[str]:
    errors: list[str] = []
    asset_id = entry.get("asset_id", "<unknown>")
    raw = entry.get("raw_master", {})
    candidate = entry.get("candidate", {})
    conversion = entry.get("conversion", {})

    try:
        raw_path = repository_path(raw["path"])
        candidate_path = repository_path(candidate["path"])
    except (KeyError, ValueError) as error:
        return [f"{asset_id}: invalid repository path ({error})"]

    raw_dimensions = tuple(raw.get("dimensions", []))
    if raw_dimensions != (1448, 1086):
        errors.append(f"{asset_id}: manifest raw dimensions must be [1448, 1086]")
    errors.extend(image_errors(raw_path, "PNG", raw.get("mode", "RGB"), (1448, 1086)))

    if raw_path.is_file() and sha256_file(raw_path) != raw.get("sha256"):
        errors.append(f"{asset_id}: raw-master SHA-256 does not match manifest")
    if raw_path.is_file() and raw_path.stat().st_size != raw.get("bytes"):
        errors.append(f"{asset_id}: raw-master byte count does not match manifest")

    expected_pillow = conversion.get("pillow_version")
    if PILLOW_VERSION != expected_pillow:
        errors.append(
            f"{asset_id}: Pillow {PILLOW_VERSION} cannot reproduce recorded Pillow {expected_pillow} bytes"
        )
        return errors

    if errors:
        return errors

    rendered = render_candidate(
        raw_path,
        int(conversion["webp_quality"]),
        int(conversion["webp_method"]),
    )
    rendered_sha = sha256_bytes(rendered)
    expected_sha = candidate.get("sha256")
    if rendered_sha != expected_sha:
        errors.append(
            f"{asset_id}: deterministic render SHA-256 {rendered_sha} does not match manifest {expected_sha}"
        )
        return errors

    if write:
        candidate_path.parent.mkdir(parents=True, exist_ok=True)
        candidate_path.write_bytes(rendered)

    errors.extend(image_errors(candidate_path, "WEBP", "RGB", (1024, 768)))
    if candidate_path.is_file() and sha256_file(candidate_path) != expected_sha:
        errors.append(f"{asset_id}: candidate SHA-256 does not match manifest")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--manifest",
        type=Path,
        default=DEFAULT_MANIFEST,
        help="manifest path relative to the repository or absolute",
    )
    parser.add_argument(
        "--id",
        dest="asset_ids",
        action="append",
        help="limit verification/rebuild to one stable asset ID; may be repeated",
    )
    parser.add_argument(
        "--write",
        action="store_true",
        help="write the deterministic WebP result to the candidate path after verifying its raw source",
    )
    parser.add_argument(
        "--check",
        action="store_true",
        help="explicit read-only mode; this is also the default",
    )
    args = parser.parse_args()

    manifest_path = args.manifest
    if not manifest_path.is_absolute():
        manifest_path = (ROOT / manifest_path).resolve()
    manifest = read_manifest(manifest_path)

    requested = set(args.asset_ids or [])
    entries = [
        entry
        for entry in manifest["candidates"]
        if not requested or entry.get("asset_id") in requested
    ]
    missing = requested - {entry.get("asset_id") for entry in entries}
    if missing:
        print(f"Unknown asset ID(s): {', '.join(sorted(missing))}", file=sys.stderr)
        return 2

    all_errors: list[str] = []
    for entry in entries:
        errors = entry_errors(entry, args.write)
        if errors:
            all_errors.extend(errors)
        else:
            action = "rebuilt" if args.write else "verified"
            print(f"{action}: {entry['asset_id']}")

    if all_errors:
        print("Illustration candidate verification failed:", file=sys.stderr)
        for error in all_errors:
            print(f"- {error}", file=sys.stderr)
        return 1

    print(
        f"Verified {len(entries)} illustration candidate(s) with Pillow {PILLOW_VERSION}."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
