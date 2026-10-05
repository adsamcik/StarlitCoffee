"""Record generation provenance and build the current review. No raster edits."""

import argparse
import hashlib
import html
import json
import shutil
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def write_json(path, data):
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8", newline="\n")


def record(method, output, refinement=False):
    selection = json.loads((ROOT / "selection-v2.json").read_text(encoding="utf-8"))
    row = next(item for item in selection["methods"] if item["id"] == method)
    if refinement:
        row["file"] = f"selected-v2/{method}-refined.png"
        row["prompt_file"] = f"prompts-v2/{method}-refinement.txt"
        row["input_files"] = [f"selected-v2/{method}.png", row["reference_file"], "style-references/learn-brewer-icons-contact.png", "style-references/learn_brewer_icon_clever.webp", "style-references/learn_brewer_icon_batch.webp"]
    target = ROOT / row["file"]
    if target.exists():
        raise ValueError("Preserve existing revision output; record a separate refinement.")
    shutil.copyfile(output, target)
    prompt = (ROOT / row["prompt_file"]).read_text(encoding="utf-8").rstrip("\n")
    row.update({"sha256": sha(target), "status": "Generated; awaiting visual review"})
    path = ROOT / "generation-history-v2.json"
    history = json.loads(path.read_text(encoding="utf-8")) if path.exists() else {"tool": "built-in image_gen", "entries": []}
    history["entries"].append({"method": method, "submitted_prompt": prompt, "submitted_prompt_sha256": hashlib.sha256(prompt.encode("utf-8")).hexdigest(), "prompt_file": row["prompt_file"], "transparent_background": True, "input_files": [{"file": filename, "sha256": sha(ROOT / filename)} for filename in row["input_files"]], "output_file": row["file"], "output_sha256": sha(target), "original_output_path": str(output)})
    write_json(path, history)
    write_json(ROOT / "selection-v2.json", selection)


CSS = """
*{box-sizing:border-box}body{margin:0;background:#faf8fe;color:#30323b;font:16px/24px Roboto,Arial,sans-serif;--primary:#4e5e8b;--tonal:#dde2f9;--card:#f4f3fa;--outline:#b0b1bc;--muted:#5d5f68}header,main,footer{max-width:1240px;margin:auto;padding:20px}header{padding-bottom:0}h1{font-size:28px;line-height:36px;font-weight:600;margin:8px 0}h2{font-size:16px;line-height:24px;font-weight:500;margin:0}p{margin:8px 0 16px;color:var(--muted)}.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(270px,1fr));gap:16px}.method{background:var(--card);border-radius:36px;padding:20px;min-width:0}.row{display:flex;align-items:center;gap:16px;min-height:76px}.badge{width:68px;height:68px;flex-shrink:0;border-radius:36px;background:var(--tonal);display:grid;place-items:center}.badge img{width:52px;height:52px;object-fit:contain}.meta{font-size:12px;line-height:16px;color:var(--muted);margin:4px 0}.stage{display:grid;place-items:center;min-height:156px;margin:16px 0;background:var(--tonal);border-radius:20px}.stage img{width:140px;height:140px;object-fit:contain}.samples{display:flex;align-items:center;gap:12px;font-size:12px;color:var(--muted)}.samples span{display:grid;place-items:center;width:68px;height:68px;border-radius:36px;background:#dde2f9}.samples .dark{background:#3a4668}.samples img{width:48px;height:48px;object-fit:contain}details{margin-top:12px}summary{min-height:48px;display:flex;align-items:center;cursor:pointer;font-size:14px;font-weight:500}.reference{width:100%;max-height:220px;object-fit:contain;background:white;border-radius:20px}.comparison{display:flex;align-items:center;gap:16px}.comparison img{width:96px;height:96px;object-fit:contain}.comparison small{display:block;color:var(--muted)}button{min-height:48px;background:var(--tonal);color:inherit;border:0;border-radius:28px;padding:12px 24px;font:500 14px/20px Roboto,Arial,sans-serif;cursor:pointer}a{color:var(--primary);overflow-wrap:anywhere}button:focus-visible,summary:focus-visible,a:focus-visible{outline:3px solid var(--primary);outline-offset:4px}body.dark-page{background:#1a1210;color:#f0dfd5;--primary:#e8c9b0;--tonal:#5a3e2a;--card:#1d1b20;--outline:#9f8d83;--muted:#d7c3b8}footer{font-size:14px;line-height:20px;color:var(--muted)}.sheet.grid{grid-template-columns:repeat(auto-fit,minmax(230px,1fr))}.sheet .method{padding:16px}.sheet .row{gap:12px}@media(max-width:400px){.grid,.sheet.grid{grid-template-columns:minmax(0,1fr)}header,main,footer{padding:20px}.method{padding:16px}}
"""


def build():
    data = json.loads((ROOT / "selection-v2.json").read_text(encoding="utf-8"))
    old = {item["id"]: item for item in json.loads((ROOT / "manifest.json").read_text(encoding="utf-8"))["methods"]}
    e = html.escape
    cards, tiles = [], []
    for item in data["methods"]:
        filename = item["file"]
        img = f'<img src="{e(filename)}" alt="">' if (ROOT / filename).exists() else '<span aria-label="Artwork pending">…</span>'
        tag = "Existing app artwork" if item["origin"].startswith("Exact") else "Family extension candidate"
        row = f'<div class="row"><div class="badge">{img}</div><div><h2>{e(item["name"])}</h2><p class="meta">{tag}</p></div></div>'
        original = old[item["id"]]
        prompt = f' · <a href="{e(item["prompt_file"])}">Exact prompt</a>' if "prompt_file" in item else ''
        cards.append(f'<article class="method" id="{e(item["id"])}">{row}<div class="stage">{img}</div><div class="samples"><span>{img}</span><span class="dark">{img}</span>48 px</div><details><summary>Reference & previous candidate</summary><img class="reference" src="{e(item["reference_file"])}" alt="Equipment photograph for {e(item["name"])}"><p>{e(original["geometry_brief"])}</p><a href="{e(item["source_url"])}">Equipment source</a>{prompt}<div class="comparison"><div><img src="{e(original["candidate_file"])}" alt="Previous {e(item["name"])} candidate"><small>Previous glossy treatment</small></div><div>{img}<small>Current selection</small></div></div></details></article>')
        tiles.append(f'<a class="method" style="color:inherit;text-decoration:none" href="index.html#{e(item["id"])}">{row}</a>')
    head = f'<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>StarlitCoffee · Existing visual language</title><style>{CSS}</style>'
    header = '<header><h1>Brewing methods</h1><p>Ten existing app icons, with seven restrained family extensions.</p><button onclick="document.body.classList.toggle(\'dark-page\');this.setAttribute(\'aria-pressed\',document.body.classList.contains(\'dark-page\'))" aria-pressed="false">Dark surface</button> <a href="README.md">Review notes</a></header>'
    footer = '<footer>Learn recognition artwork only. Brew silhouettes and instructional images keep their own roles. Blue light surfaces approximate the captured emulator theme; the dark review uses the app’s coffee fallback. This browser artifact does not emulate Android dynamic color. Production app resources are unchanged.</footer></html>'
    (ROOT / "index.html").write_text(head + header + '<main class="grid">' + "\n".join(cards) + '</main>' + footer, encoding="utf-8", newline="\n")
    (ROOT / "sheet.html").write_text(head + header + '<main class="grid sheet">' + "\n".join(tiles) + '</main>' + footer, encoding="utf-8", newline="\n")


def validate():
    data = json.loads((ROOT / "selection-v2.json").read_text(encoding="utf-8"))
    report = []
    for item in data["methods"]:
        filename = ROOT / item["file"]
        row = {"id": item["id"], "origin": item["origin"], "errors": []}
        if not filename.exists():
            row["errors"].append("Missing selection")
        else:
            row["sha256"] = sha(filename)
            if row["sha256"] != item.get("sha256"):
                row["errors"].append("Selection hash changed")
            if "source_file" in item and sha(ROOT.parents[3] / item["source_file"]) != row["sha256"]:
                row["errors"].append("Copy differs from shipping resource")
            with Image.open(filename) as image:
                row.update({"size": list(image.size), "mode": image.mode})
                row["square_canvas"] = image.width == image.height
                row["production_square_export_needed"] = not row["square_canvas"]
                if "A" not in image.getbands():
                    row["errors"].append("Expected real alpha")
                else:
                    alpha = image.getchannel("A")
                    bounds = alpha.point(lambda x: 255 if x >= 16 else 0).getbbox()
                    row["alpha_extrema"] = list(alpha.getextrema())
                    row["visible_bounds_alpha_16"] = list(bounds) if bounds else None
                    border = [alpha.getpixel((x, 0)) for x in range(image.width)] + [alpha.getpixel((x, image.height - 1)) for x in range(image.width)] + [alpha.getpixel((0, y)) for y in range(image.height)] + [alpha.getpixel((image.width - 1, y)) for y in range(image.height)]
                    row["border_max_alpha"] = max(border)
                    row["production_alpha_cleanup_needed"] = max(border) > 0
                    if not bounds or alpha.getextrema() != (0, 255):
                        row["errors"].append("Missing clear background or opaque object")
                    if not "source_file" in item and bounds:
                        left, top, right, bottom = bounds
                        margin = min(left / image.width, top / image.height, (image.width - right) / image.width, (image.height - bottom) / image.height)
                        row["minimum_visible_margin"] = round(margin, 4)
                        if margin < .03 or max(border) > 1:
                            row["errors"].append("Extension fails clear margin/border check")
        report.append(row)
    history = json.loads((ROOT / "generation-history-v2.json").read_text(encoding="utf-8"))
    history_errors = []
    for entry in history["entries"]:
        if entry["submitted_prompt"] != (ROOT / entry["prompt_file"]).read_text(encoding="utf-8").rstrip("\n") or entry["submitted_prompt_sha256"] != hashlib.sha256(entry["submitted_prompt"].encode("utf-8")).hexdigest():
            history_errors.append(entry["method"] + ": prompt changed")
        for field in entry["input_files"]:
            if sha(ROOT / field["file"]) != field["sha256"]:
                history_errors.append(entry["method"] + ": input changed")
        if sha(ROOT / entry["output_file"]) != entry["output_sha256"]:
            history_errors.append(entry["method"] + ": output changed")
    summary = {"selections": len(report), "passing": sum(not item["errors"] for item in report), "new_generation_calls": len(history["entries"]), "history_errors": history_errors, "checks": "Design integrity: exact selected/shipping hashes, saved/submitted prompt and input/output hashes, real clear/opaque alpha, generated border alpha <=1 and >=3 percent visible margin. Canvas squareness and alpha residue are separate outstanding export flags; production readiness is not certified.", "results": report}
    write_json(ROOT / "validation-v2.json", summary)
    print(json.dumps({key: value for key, value in summary.items() if key != "results"}))
    for row in report:
        if row["errors"]:
            print(row["id"], row["errors"])
    generated_methods = {entry["method"] for entry in history["entries"]}
    expected_methods = {item["id"] for item in data["methods"] if "source_file" not in item}
    if summary["passing"] != 17 or generated_methods != expected_methods or history_errors:
        raise SystemExit(1)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=["record", "build", "validate"])
    parser.add_argument("--method")
    parser.add_argument("--output", type=Path)
    parser.add_argument("--refinement", action="store_true")
    args = parser.parse_args()
    if args.action == "record":
        record(args.method, args.output, args.refinement)
    elif args.action == "build":
        build()
    else:
        validate()
