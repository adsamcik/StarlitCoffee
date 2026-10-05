"""Prepare text prompts and exact copies of existing art; never edit pixels."""

import hashlib
import json
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parents[3]
REUSE = {
    "chemex": "carafe", "automatic-drip": "batch", "pulsar": "pulsar",
    "v60": "v60_02", "clever": "clever", "hario-switch": "switch",
    "kalita-wave": "wave_185", "melitta": "wedge", "turkish": "cezve", "phin": "phin",
}
STYLE_INPUTS = [
    "style-references/learn-brewer-icons-contact.png",
    "style-references/learn_brewer_icon_clever.webp",
    "style-references/learn_brewer_icon_batch.webp",
]
STYLE = """Use case: stylized-concept. Asset: a StarlitCoffee Learn recognition icon, displayed at 48-52 dp inside an existing 68 dp tonal badge.
Input roles: Image 1 is the real equipment photograph, for anatomical construction only. Image 2 is the SHIPPED StarlitCoffee Learn icon family, the exact visual language to extend. Images 3 and 4 are individual shipped Clever and automatic-drip icons, for line weight, compact form, muted materials, glass treatment, and shading. Do not reproduce the contact sheet, labels, badges or other equipment. Deliver one icon only.
Primary request: extend that existing illustrated family with the requested brewer. Match the reference icons' softly illustrated, compact, simplified dimensional appearance. Muted periwinkle planes, dark navy outlines, warm ivory rim and restrained brown coffee accent. Essential readable equipment parts, modest soft highlights, matte/satin finish. Keep glass almost clear/ivory like the Clever, not bright blue. Preserve the photo's mechanical anatomy while simplifying its small textures and controls to the reference icons' detail level.
Avoid a new visual style: no shiny chrome product render, luminous bright blue glass, mirror-metal reflections, bright white specular streaks, elaborate machining, oversaturated blue, dramatic studio light, realistic photo texture or tiny control labels. Do not make the object more reflective or complex than its neighboring shipped icons.
Composition: same front three-quarter view and modest view into open tops as the existing family. Exactly one complete object, compact silhouette, centered with 8 percent clear margin on all sides. All handles, feet, lids and spouts fit. No ground shadow, scene, cup, hand, action, badge, border, letters, numbers, logo, watermark, checkerboard or background. Genuine transparent alpha.
"""


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    for folder in ["style-references", "selected-v2", "prompts-v2"]:
        (ROOT / folder).mkdir(exist_ok=True)
    sources = {
        "learn-brewer-icons-contact.png": REPO / "docs/brewing/learn-icon-candidates/learn-brewer-icons-contact.png",
        "learn_brewer_icon_clever.webp": REPO / "app/src/main/res/drawable-nodpi/learn_brewer_icon_clever.webp",
        "learn_brewer_icon_batch.webp": REPO / "app/src/main/res/drawable-nodpi/learn_brewer_icon_batch.webp",
    }
    for filename, source in sources.items():
        shutil.copyfile(source, ROOT / "style-references" / filename)
    original = json.loads((ROOT / "manifest.json").read_text(encoding="utf-8"))
    selected = []
    for method in original["methods"]:
        row = {"id": method["id"], "name": method["name"], "reference_file": method["reference_file"], "source_url": method["source_url"]}
        if method["id"] in REUSE:
            source = REPO / f"app/src/main/res/drawable-nodpi/learn_brewer_icon_{REUSE[method['id']]}.webp"
            target = ROOT / "selected-v2" / f"{method['id']}.webp"
            shutil.copyfile(source, target)
            row.update({"file": str(target.relative_to(ROOT)).replace("\\", "/"), "origin": "Exact reuse of shipping Learn art", "source_file": str(source.relative_to(REPO)).replace("\\", "/"), "sha256": sha(source), "note": "Existing recognition art retained; named photo is a geometry reference, not a claim of exact model depiction."})
        else:
            prompt = STYLE + f"\nMethod: {method['name']}. Representative anatomy: {method['model']}.\nEquipment: {method['geometry_brief']}\nExclude: {method['exclude']}\n"
            if method["id"] == "siphon":
                prompt += "Siphon details: no dark stopper in the upper chamber. Cloth filter rests on the floor of the upper chamber; thin chain and small retaining hook at lower stem end, never a second filter disc. Keep joined chambers and stable stand.\n"
            filename = f"prompts-v2/{method['id']}.txt"
            (ROOT / filename).write_text(prompt, encoding="utf-8", newline="\n")
            row.update({"file": f"selected-v2/{method['id']}.png", "origin": "New family extension", "prompt_file": filename, "status": "Awaiting calibrated generation", "input_files": [method["reference_file"], *STYLE_INPUTS]})
        selected.append(row)
    selection = {"revision": 2, "reason": "User rejected the independent prototype and overly glossy icon treatment; preserve existing visual language.", "role": "Learn recognition only; Brew equipment silhouettes and teaching art retain their distinct roles.", "methods": selected}
    path = ROOT / "selection-v2.json"
    if not path.exists():
        path.write_text(json.dumps(selection, indent=2) + "\n", encoding="utf-8", newline="\n")
    print("Prepared 10 exact shipping-art reuses and 7 extension prompts.")


if __name__ == "__main__":
    main()
