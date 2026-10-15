"""Extract a dated release entry for GitHub without losing non-PR changes."""

from __future__ import annotations

import argparse
from datetime import date
from pathlib import Path
import re


def extract_release_notes(changelog: str, tag: str) -> str:
    changelog = changelog.replace("\r\n", "\n")
    version = tag.removeprefix("v")
    header = re.compile(
        rf"^## \[{re.escape(version)}\] — (\d{{4}}-\d{{2}}-\d{{2}})$",
        re.MULTILINE,
    )
    matches = list(header.finditer(changelog))
    if len(matches) != 1:
        raise ValueError(f"Expected exactly one dated changelog entry for {tag}")
    match = matches[0]
    release_date = date.fromisoformat(match.group(1))
    body = re.split(
        r"(?m)^(?:## |\[[^\]\n]+\]: )", changelog[match.end():], maxsplit=1
    )[0].strip()
    if not re.search(r"(?m)^- \S", body):
        raise ValueError(f"Changelog entry for {tag} has no release changes")
    notes = f"## {version} — {release_date.isoformat()}\n\n{body}\n"
    comparison = re.search(
        rf"(?m)^\[{re.escape(version)}\]: (https://\S+)$", changelog
    )
    if comparison:
        notes += f"\n**Full changelog**: {comparison.group(1)}\n"
    return notes


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tag", required=True)
    parser.add_argument(
        "--changelog", type=Path,
        default=Path(__file__).resolve().parents[1] / "CHANGELOG.md",
    )
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    try:
        notes = extract_release_notes(args.changelog.read_text(encoding="utf-8-sig"), args.tag)
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(notes, encoding="utf-8", newline="\n")
    except (OSError, ValueError) as exc:
        parser.error(str(exc))


if __name__ == "__main__":
    main()
