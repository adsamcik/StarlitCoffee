"""Read-only raster inspection plus manifest integrity; no image edits."""

import hashlib
import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    manifest = json.loads((ROOT / "manifest.json").read_text(encoding="utf-8"))
    history_path = ROOT / "generation-history.json"
    history = json.loads(history_path.read_text(encoding="utf-8"))
    history_errors = []
    for entry in history["entries"]:
        submitted = entry["submitted_prompt"]
        saved = (ROOT / entry["prompt_file"]).read_text(encoding="utf-8")
        if submitted.replace("\r\n", "\n").rstrip("\n") != saved.rstrip("\n"):
            history_errors.append(f"{entry['method']} pass {entry['pass']}: saved prompt differs")
        if entry["submitted_prompt_sha256"] != hashlib.sha256(submitted.encode("utf-8")).hexdigest():
            history_errors.append(f"{entry['method']} pass {entry['pass']}: submitted prompt hash differs")
        if entry["output_sha256"] != digest(ROOT / entry["output_file"]):
            history_errors.append(f"{entry['method']} pass {entry['pass']}: generated output hash differs")
        for reference_input in entry["images"]:
            if reference_input["sha256"] != digest(ROOT / reference_input["file"]):
                history_errors.append(f"{entry['method']} pass {entry['pass']}: image input hash differs")
        expected_inputs = 2 if entry["pass"] == 1 else 3
        if len(entry["images"]) != expected_inputs or not entry["transparent_background"]:
            history_errors.append(f"{entry['method']} pass {entry['pass']}: missing image input or alpha request")
    rows = []
    for item in manifest["methods"]:
        reference = ROOT / item["reference_file"]
        candidate = ROOT / item["candidate_file"]
        prompt = ROOT / item["prompt_file"]
        errors = []
        if digest(reference) != item["reference_sha256"]:
            errors.append("Reference hash differs from selected input")
        with Image.open(reference) as image:
            image.verify()
        if not prompt.is_file() or "Image 1" not in prompt.read_text(encoding="utf-8") or "Image 2" not in prompt.read_text(encoding="utf-8"):
            errors.append("Missing image-role prompt")
        row = {"id": item["id"], "reference_sha256": digest(reference), "prompt_sha256": digest(prompt)}
        if not candidate.is_file():
            errors.append("Missing generated candidate")
        else:
            with Image.open(candidate) as image:
                width, height = image.size
                row.update({"candidate_sha256": digest(candidate), "size": [width, height], "mode": image.mode})
                if width != height:
                    errors.append("Candidate is not square")
                if "A" not in image.getbands():
                    errors.append("Missing real alpha channel")
                else:
                    alpha = image.getchannel("A")
                    raw_bounds = alpha.getbbox()
                    # Diagnostic mask only; never written back to the artwork.
                    # Alpha 1 residue from the generator is recorded separately.
                    bounds = alpha.point(lambda value: 255 if value >= 16 else 0).getbbox()
                    extrema = alpha.getextrema()
                    row.update({"alpha_extrema": list(extrema), "raw_nonzero_bounds": list(raw_bounds) if raw_bounds else None, "visible_object_alpha_threshold": 16, "visible_object_bounds": list(bounds) if bounds else None})
                    if not bounds or extrema != (0, 255):
                        errors.append("Alpha lacks both clear background and opaque object")
                    if bounds:
                        left, top, right, bottom = bounds
                        margins = [left / width, top / height, (width - right) / width, (height - bottom) / height]
                        row["clear_margins"] = [round(value, 4) for value in margins]
                        if min(margins) < 0.03:
                            errors.append("Object approaches canvas edge within 3 percent")
                    border = [alpha.getpixel((x, 0)) for x in range(width)] + [alpha.getpixel((x, height - 1)) for x in range(width)] + [alpha.getpixel((0, y)) for y in range(height)] + [alpha.getpixel((width - 1, y)) for y in range(height)]
                    row["fully_transparent_border"] = all(value == 0 for value in border)
                    row["border_max_alpha"] = max(border)
                    row["border_nonzero_pixels"] = sum(value > 0 for value in border)
                    row["production_alpha_cleanup_needed"] = not row["fully_transparent_border"]
                    if row["border_max_alpha"] > 1:
                        errors.append("Outer border contains more than alpha 1 generator residue")
        row.update({"errors": errors, "passed": not errors})
        rows.append(row)
    report = {"methods": len(rows), "passed": sum(row["passed"] for row in rows), "generation_calls": len(history["entries"]), "generation_history_errors": history_errors, "checks": "Design candidate checks: input hashes, submitted/saved prompt agreement, 2 image inputs per initial call and 3 per refinement, readable photos, square output, real alpha, border alpha <=1, and >=3 percent margin for pixels with alpha >=16. Raw alpha bounds and residue are retained; passing does not certify production alpha cleanup or hardware accuracy.", "results": rows}
    (ROOT / "validation.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8", newline="\n")
    print(json.dumps({key: value for key, value in report.items() if key != "results"}))
    for row in rows:
        if row["errors"]:
            print(row["id"] + ": " + "; ".join(row["errors"]))
    if report["passed"] != 17 or history_errors or len(history["entries"]) != 20:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
