#!/usr/bin/env python3
"""Generate only hash-bound, independently admitted scene assets and explicit target aliases."""
from __future__ import annotations

import argparse
import hashlib
import json
import re
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ADMISSION = Path("docs/brewing/generated-guidance-20261025/admission.json")
DRAFT = Path("docs/brewing/generated-guidance-20261025/bindings-draft.json")
OUTPUT = Path("app/src/main/java/com/adsamcik/starlitcoffee/ui/guidance/GeneratedGuidanceCatalog.kt")
ID = re.compile(r"[a-z][a-z0-9]*(?:_[a-z0-9]+)*")
SHA = re.compile(r"[0-9a-f]{64}")
CHECKS = ("full_resolution", "phone_light", "phone_dark", "equipment_geometry", "action_state",
          "hand_safety", "crop_and_flow", "alt_text_agreement", "minimalism")
PULSAR_CONTENT_IDS = {f"pulsar_gagne_20_340_stage_{n:02d}_instruction" for n in range(1, 9)}
PULSAR_ASSET_IDS = {f"instruction_{cid}_default" for cid in PULSAR_CONTENT_IDS}


def text(record: dict, key: str) -> str:
    value = record.get(key)
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f"Missing nonblank {key}")
    return value


def identifier(record: dict, key: str) -> str:
    value = text(record, key)
    if not ID.fullmatch(value):
        raise ValueError(f"Invalid {key}: {value}")
    return value


def digest(record: dict, key: str) -> str:
    value = text(record, key)
    if not SHA.fullmatch(value):
        raise ValueError(f"Invalid SHA256 {key}")
    return value


def evidence_file(root: Path, record: dict, path_key: str, hash_key: str) -> Path:
    path = (root / text(record, path_key)).resolve()
    if not path.is_relative_to(root.resolve()) or not path.is_file():
        raise ValueError(f"Evidence file missing/outside repository: {path_key}")
    if hashlib.sha256(path.read_bytes()).hexdigest() != digest(record, hash_key):
        raise ValueError(f"Changed evidence bytes: {path_key}")
    return path


def reviewed(record: dict, full_checks: bool = False) -> dict:
    review = record.get("review")
    if not isinstance(review, dict) or review.get("verdict") != "approved":
        raise ValueError("Independent approved review required")
    text(review, "reviewer")
    stamp = text(review, "reviewed_on")
    if date.fromisoformat(stamp).isoformat() != stamp:
        raise ValueError("Review date must be YYYY-MM-DD")
    notes = review.get("notes")
    if isinstance(notes, str):
        notes = [notes]
    if not isinstance(notes, list) or not notes or any(not isinstance(n, str) or not n.strip() for n in notes):
        raise ValueError("Review requires concrete binding/hardware/action notes")
    if full_checks and any(review.get("checks", {}).get(k) != "pass" for k in CHECKS):
        raise ValueError("All nine scene QC checks must pass")
    return {**review, "notes": " ".join(notes)}


def canonical_sources(root: Path) -> tuple[dict, set[str]]:
    ledger = json.loads((root / "docs/brewing/p1-exact-independent-visual-review-2026-08-21.json").read_text(encoding="utf-8"))
    rows = {r["asset_id"]: r for r in ledger["assets"]}
    # Pulsar identities come from the shipped curriculum, never production-tracker
    # approval. Its replacement pixels require full new scene admission below.
    return rows, {a.removeprefix("instruction_").removesuffix("_default") for a in rows} | PULSAR_CONTENT_IDS


def load_admission(root: Path, admission_path: Path) -> tuple[list[dict], list[dict]]:
    document = json.loads(admission_path.read_text(encoding="utf-8"))
    if document.get("schema_version") != 1 or not isinstance(document.get("scenes"), list):
        raise ValueError("Admission requires schema_version1 and scenes[]")
    canonical, canonical_content = canonical_sources(root)
    sources, targets, seen_assets, seen_targets = [], [], set(), set()
    for kind, records in [("scene", document["scenes"]), ("existing", document.get("existing_assets", []))]:
        if not isinstance(records, list):
            raise ValueError("existing_assets must be a list")
        for row in records:
            asset_id = identifier(row, "asset_id")
            if asset_id in seen_assets:
                raise ValueError(f"Duplicate source asset: {asset_id}")
            seen_assets.add(asset_id)
            drawable = evidence_file(root, row, "drawable_path", "resource_sha256")
            if drawable != (root / "app/src/main/res/drawable-nodpi" / f"{asset_id}.webp").resolve():
                raise ValueError(f"Wrong drawable resource path: {asset_id}")
            # The old Pulsar production tracker records delivery, not independent
            # pixel admission. Reuse must carry the same complete review as new art.
            pulsar_source = asset_id in PULSAR_ASSET_IDS
            review = reviewed(row, full_checks=kind == "scene" or pulsar_source)
            family = identifier(row, "source_physical_family_id")
            profile = row.get("source_physical_profile_id")
            if profile is not None and (not isinstance(profile, str) or not ID.fullmatch(profile)):
                raise ValueError(f"Invalid source physical profile: {asset_id}")
            if pulsar_source and (family != "valve_controlled_no_bypass" or profile != "pulsar_standard"):
                raise ValueError(f"Canonical Pulsar hardware scope differs: {asset_id}")
            if kind == "scene":
                text(row, "method_id")
                raw = evidence_file(root, row, "raw_source_path", "raw_source_sha256")
                prompt = evidence_file(root, row, "prompt_path", "prompt_sha256")
                if len({raw, prompt, drawable}) != 3:
                    raise ValueError("Raw, prompt and drawable must be distinct provenance files")
            else:
                old = canonical.get(asset_id)
                if old is None or old.get("verdict") != "approved" or old.get("resource_sha256") != row["resource_sha256"]:
                    raise ValueError(f"Existing source lacks a matching approved source ledger: {asset_id}")
            source_content = asset_id.removeprefix("instruction_").removesuffix("_default")
            source = {**row, "review": review, "family": family, "profile": profile,
                      "canonical": source_content in canonical_content, "pulsar": pulsar_source}
            sources.append(source)
            bindings = row.get("bindings")
            if not isinstance(bindings, list) or not bindings:
                raise ValueError(f"Source needs explicit target bindings: {asset_id}")
            if pulsar_source and not any(b.get("content_id") == source_content for b in bindings):
                raise ValueError(f"Canonical Pulsar scene needs its reviewed self binding: {asset_id}")
            for binding in bindings:
                cid = identifier(binding, "content_id")
                if cid in seen_targets:
                    raise ValueError(f"Duplicate/ambiguous target binding: {cid}")
                seen_targets.add(cid)
                revision = digest(binding, "target_content_revision")
                if binding.get("content_revision", revision) != revision:
                    raise ValueError(f"Conflicting target revision mirrors: {cid}")
                target_review = reviewed(binding)
                if binding.get("purpose", "instruction") not in ("instruction", "auxiliary"):
                    raise ValueError(f"Unknown target purpose: {cid}")
                if cid in canonical_content and asset_id != f"instruction_{cid}_default":
                    raise ValueError(f"Alias cannot override canonical content: {cid}")
                targets.append({**binding, "target_content_revision": revision,
                                "family": identifier(binding, "target_family_id"),
                                "profile": identifier(binding, "target_profile_id"),
                                "target_catalog": text(binding, "target_catalog"),
                                "alt_text": text(binding, "alt_text"), "review": target_review,
                                "asset": source, "canonical": cid in canonical_content})
    return sources, targets


def q(value: str) -> str:
    return json.dumps(value, ensure_ascii=False).replace("$", "\\$")


def validate_target_inventory(targets: list[dict], expected: dict) -> set[str]:
    """Catch binding clerical errors before packaging; JVM tests also check live sources."""
    known = {r["content_id"]: r for r in expected["bindings"] + expected["unresolved"]}
    primary = set()
    for target in targets:
        cid = target["content_id"]
        if target.get("purpose", "instruction") == "auxiliary":
            if cid in known:
                raise ValueError(f"Primary instruction cannot be relabeled auxiliary: {cid}")
            continue
        contract = known.get(cid)
        if contract is None:
            raise ValueError(f"Unknown physical instruction target: {cid}")
        for field in ("target_family_id", "target_profile_id", "target_catalog", "target_content_revision"):
            if target[field] != contract[field]:
                raise ValueError(f"Binding differs from inventoried source {field}: {cid}")
        primary.add(cid)
    return known.keys() - primary


def review_code(review: dict) -> str:
    return ("InstructionAssetReview(InstructionAssetReviewStatus.APPROVED, " + q(review["reviewer"]) +
            ", LocalDate.parse(" + q(review["reviewed_on"]) + "), " + q(review["notes"]) + ")")


def asset_code(source: dict, pulsar: bool = False) -> list[str]:
    aid = source["asset_id"]
    if not aid.startswith("instruction_") or not aid.endswith("_default"):
        raise ValueError(f"Scene asset must use instruction_<scene>_default: {aid}")
    cid = aid.removeprefix("instruction_").removesuffix("_default")
    profile = "null" if source["profile"] is None else "BrewerProfileId(" + q(source["profile"]) + ")"
    lines = ["        InstructionAssetRecord(", f"            id = InstructionAssetId({q(aid)}),",
             f"            familyId = MethodFamilyId({q(source['family'])}), profileId = {profile},",
             f"            contentId = StageContentId({q(cid)}),"]
    if pulsar:
        lines.append(f"            stageId = StageId({q(cid.removesuffix('_instruction'))}),")
    lines += ["            namingConvention = InstructionAssetNamingConvention.EXACT_CONTENT_ID,",
              f"            drawableRes = R.drawable.{aid}, mandatoryForFullGuidance = {str(pulsar).lower()},",
              f"            safetySensitive = {str(pulsar and cid.endswith('_08_instruction')).lower()},",
              f"            provenance = InstructionAssetProvenance({q(source['prompt_path'])}, {q(source['prompt_sha256'])}),",
              f"            review = {review_code(source['review'])},",
              f"            resourceSha256 = {q(source['resource_sha256'])},", "        ),"]
    return lines


def render(sources: list[dict], targets: list[dict]) -> str:
    lines = ["// Generated by tools/generate_guidance_asset_catalog.py; do not edit.",
             '// Large literal tables are chunked for the JVM; review notes remain verbatim.',
             '@file:Suppress("LongMethod", "MaxLineLength", "TooManyFunctions")',
             "package com.adsamcik.starlitcoffee.ui.guidance", "",
             "import com.adsamcik.starlitcoffee.R", "import com.adsamcik.starlitcoffee.domain.brewing.BrewerProfileId",
             "import com.adsamcik.starlitcoffee.domain.brewing.InstructionAssetId",
             "import com.adsamcik.starlitcoffee.domain.brewing.MethodFamilyId",
             "import com.adsamcik.starlitcoffee.domain.brewing.StageContentId",
             "import com.adsamcik.starlitcoffee.domain.brewing.StageId", "import java.time.LocalDate", "",
             "object GeneratedGuidanceCatalog {", "    val assets: List<InstructionAssetRecord> = listOf("]
    for s in sorted(sources, key=lambda s: s["asset_id"]):
        if s["canonical"]:
            continue
        lines += asset_code(s)
    lines += ["    )", "", "    val pulsarAssets: List<InstructionAssetRecord> = listOf("]
    for s in sorted(sources, key=lambda s: s["asset_id"]):
        if s["pulsar"]:
            lines += asset_code(s, pulsar=True)
    lines += ["    )", "", "    val aliases: List<InstructionAssetAlias> = listOf("]
    for t in sorted(targets, key=lambda t: t["content_id"]):
        if t["canonical"]:
            continue
        s = t["asset"]
        profile = "null" if s["profile"] is None else "BrewerProfileId(" + q(s["profile"]) + ")"
        lines += ["        InstructionAssetAlias(", f"            contentId = StageContentId({q(t['content_id'])}),",
                  f"            sourceAssetId = InstructionAssetId({q(s['asset_id'])}),",
                  f"            familyId = MethodFamilyId({q(t['family'])}), profileId = BrewerProfileId({q(t['profile'])}),",
                  f"            sourceResourceSha256 = {q(s['resource_sha256'])},",
                  f"            targetContentRevision = {q(t['target_content_revision'])},",
                  f"            review = {review_code(t['review'])},",
                  f"            allowFamilySource = {str(s['profile'] is None).lower()},",
                  f"            sourcePhysicalFamilyId = MethodFamilyId({q(s['family'])}), sourcePhysicalProfileId = {profile},",
                  f"            altText = {q(t['alt_text'])},", "        ),"]
    lines += ["    )", "", "    val targets: List<AdmittedGuidanceTarget> = listOf("]
    for t in sorted(targets, key=lambda t: t["content_id"]):
        lines += ["        AdmittedGuidanceTarget(" + ", ".join(q(t[k]) for k in
                  ["content_id", "family", "profile", "target_catalog", "target_content_revision"]) +
                  ", " + q(t["asset"]["asset_id"]) + ", " + q(t["alt_text"]) +
                  ", " + q(t.get("purpose", "instruction")) + "),"]
    return bounded_tables(lines + ["    )", "}", ""])


def bounded_tables(lines: list[str]) -> str:
    """Keep large data tables below the JVM's per-method bytecode limit."""
    tables = [("assets", "InstructionAssetRecord"), ("pulsarAssets", "InstructionAssetRecord"),
              ("aliases", "InstructionAssetAlias"),
              ("targets", "AdmittedGuidanceTarget")]
    start = lines.index("object GeneratedGuidanceCatalog {")
    output, helpers = lines[:start] + [lines[start]], []
    for name, record_type in tables:
        first = lines.index(f"    val {name}: List<{record_type}> = listOf(") + 1
        last = lines.index("    )", first)
        records: list[list[str]] = []
        for line in lines[first:last]:
            if line.startswith(f"        {record_type}("):
                records.append([])
            records[-1].append(line)
        groups = [records[i:i + 24] for i in range(0, len(records), 24)]
        calls = " + ".join(f"{name}Group{i + 1}()" for i in range(len(groups))) or "emptyList()"
        output += [f"    val {name}: List<{record_type}> = {calls}", ""]
        for i, group in enumerate(groups):
            helpers += [f"private fun {name}Group{i + 1}(): List<{record_type}> = listOf("]
            helpers += [line[4:] for record in group for line in record]
            helpers += [")", ""]
    return "\n".join(output + ["}", ""] + helpers)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--admission", type=Path, default=ROOT / ADMISSION)
    parser.add_argument("--output", type=Path, default=ROOT / OUTPUT)
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--require-complete", action="store_true")
    args = parser.parse_args()
    sources, targets = load_admission(ROOT, args.admission)
    expected = json.loads((ROOT / DRAFT).read_text(encoding="utf-8"))
    missing = sorted(validate_target_inventory(targets, expected))
    print(f"Admitted {len(targets)} target contexts; missing {len(missing)} primary contexts.")
    if missing:
        print("Missing: " + ", ".join(missing))
    if args.require_complete and missing:
        raise ValueError("Physical instruction coverage is incomplete")
    output = render(sources, targets)
    if args.check:
        if not args.output.is_file() or args.output.read_text(encoding="utf-8") != output:
            raise ValueError("Generated guidance catalog is stale")
    else:
        args.output.write_text(output, encoding="utf-8")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (ValueError, OSError, KeyError, TypeError) as error:
        raise SystemExit(f"error: {error}") from error
