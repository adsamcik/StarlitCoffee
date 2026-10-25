"""Small fail-closed regressions for the image admission boundary; no Android build needed."""
from copy import deepcopy
import hashlib
import json
from pathlib import Path
import tempfile
import unittest

import generate_guidance_asset_catalog as catalog
import generate_p1_exact_visual_review_catalog as p1


class AdmissionTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.asset = "instruction_scene_test_default"
        self.review = {"verdict": "approved", "reviewer": "Independent reviewer", "reviewed_on": "2026-10-25",
                       "checks": dict.fromkeys(catalog.CHECKS, "pass"),
                       "notes": ["Same physical brewer and pouring state; hardware taxonomy explicitly reviewed."]}
        self.scene = {"asset_id": self.asset, "method_id": "test",
                      "source_physical_family_id": "manual_gravity", "source_physical_profile_id": "v60_02",
                      "review": self.review, "bindings": [self.binding("test_pour")]}
        for path_key, hash_key, path, data in [
            ("drawable_path", "resource_sha256", f"app/src/main/res/drawable-nodpi/{self.asset}.webp", b"pixels"),
            ("raw_source_path", "raw_source_sha256", "evidence/raw.png", b"raw pixels"),
            ("prompt_path", "prompt_sha256", "evidence/prompt.txt", b"exact prompt"),
        ]:
            file = self.root / path
            file.parent.mkdir(parents=True, exist_ok=True)
            file.write_bytes(data)
            self.scene[path_key] = path
            self.scene[hash_key] = hashlib.sha256(data).hexdigest()
        self.write_json("docs/brewing/p1-exact-independent-visual-review-2026-08-21.json", {"assets": []})
        self.write_json("docs/brewing/pulsar-learning-guide-production-2026-08-19.json", {"stages": []})

    def binding(self, cid):
        return {"content_id": cid, "target_family_id": "manual_gravity", "target_profile_id": "v60_02",
                "target_catalog": "reviewed", "target_content_revision": "1" * 64,
                "alt_text": "Water pours into a V60 02.", "review": deepcopy(self.review)}

    def write_json(self, path, document):
        file = self.root / path
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text(json.dumps(document), encoding="utf-8")
        return file

    def load(self, scene=None, existing=None):
        path = self.write_json("admission.json", {"schema_version": 1, "scenes": [scene or self.scene],
                                                  "existing_assets": existing or []})
        return catalog.load_admission(self.root, path)

    def test_reviewed_source_can_serve_multiple_explicit_targets_with_their_own_alt(self):
        self.scene["bindings"].append(self.binding("test_bloom"))
        self.scene["bindings"][1]["alt_text"] = "The same V60 02 pour wets the grounds."
        sources, targets = self.load()
        rendered = catalog.render(sources, targets)
        self.assertEqual(2, len(targets))
        self.assertIn("test_bloom", rendered)
        self.assertIn("The same V60 02 pour wets the grounds.", rendered)

    def test_changed_bytes_missing_qc_and_ambiguous_targets_are_rejected(self):
        changed = deepcopy(self.scene)
        changed["resource_sha256"] = "0" * 64
        failed = deepcopy(self.scene)
        failed["review"]["checks"]["equipment_geometry"] = "fail"
        ambiguous = deepcopy(self.scene)
        ambiguous["bindings"].append(deepcopy(ambiguous["bindings"][0]))
        no_binding_review = deepcopy(self.scene)
        del no_binding_review["bindings"][0]["review"]
        conflicting_revision = deepcopy(self.scene)
        conflicting_revision["bindings"][0]["content_revision"] = "0" * 64
        for scene in (changed, failed, ambiguous, no_binding_review, conflicting_revision):
            with self.subTest(scene=scene), self.assertRaises(ValueError):
                self.load(scene)

    def test_rejected_canonical_source_cannot_be_reused_or_overridden(self):
        self.write_json("docs/brewing/p1-exact-independent-visual-review-2026-08-21.json", {"assets": [
            {"asset_id": self.asset, "verdict": "rejected", "resource_sha256": self.scene["resource_sha256"]},
            {"asset_id": "instruction_canonical_step_default", "verdict": "approved", "resource_sha256": "2" * 64},
        ]})
        document = {"schema_version": 1, "scenes": [], "existing_assets": [self.scene]}
        with self.assertRaisesRegex(ValueError, "approved source ledger"):
            catalog.load_admission(self.root, self.write_json("admission.json", document))
        changed = deepcopy(self.scene)
        changed["bindings"][0]["content_id"] = "canonical_step"
        with self.assertRaisesRegex(ValueError, "override canonical"):
            self.load(changed)

    def test_large_tables_are_split_and_auxiliary_contexts_are_preserved(self):
        self.scene["bindings"] = [self.binding(f"step_{i}") for i in range(60)]
        self.scene["bindings"][-1]["purpose"] = "auxiliary"
        rendered = catalog.render(*self.load())
        self.assertIn("aliasesGroup3()", rendered)
        self.assertIn('"auxiliary"', rendered)

    def test_current_inventory_rejects_stale_contracts_and_auxiliary_relabeling(self):
        binding = self.scene["bindings"][0]
        inventory = {"bindings": [deepcopy(binding)], "unresolved": []}
        _, targets = self.load()
        self.assertEqual(set(), catalog.validate_target_inventory(targets, inventory))
        targets[0]["target_content_revision"] = "0" * 64
        with self.assertRaisesRegex(ValueError, "target_content_revision"):
            catalog.validate_target_inventory(targets, inventory)
        targets[0]["purpose"] = "auxiliary"
        with self.assertRaisesRegex(ValueError, "relabeled auxiliary"):
            catalog.validate_target_inventory(targets, inventory)

    def test_pulsar_tracker_alone_cannot_approve_and_bad_scene_hash_fails(self):
        cid = "pulsar_gagne_20_340_stage_01_instruction"
        aid = f"instruction_{cid}_default"
        self.write_json("docs/brewing/pulsar-learning-guide-production-2026-08-19.json",
                        {"stages": [{"contentId": cid, "sha256": self.scene["resource_sha256"]}]})
        empty = self.write_json("empty-admission.json", {"schema_version": 1, "scenes": []})
        missing = catalog.render(*catalog.load_admission(self.root, empty))
        self.assertIn("val pulsarAssets: List<InstructionAssetRecord> = emptyList()", missing)
        self.assertNotIn(aid, missing)
        row = deepcopy(self.scene)
        row.update(asset_id=aid, drawable_path=f"app/src/main/res/drawable-nodpi/{aid}.webp",
                   source_physical_family_id="valve_controlled_no_bypass", source_physical_profile_id="pulsar_standard")
        (self.root / row["drawable_path"]).write_bytes(b"pixels")
        row["bindings"] = [{**self.binding(cid), "target_family_id": "valve_controlled_no_bypass",
                             "target_profile_id": "pulsar_standard", "target_catalog": "pulsar_learn"}]
        rendered = catalog.render(*self.load(row))
        self.assertIn("pulsarAssetsGroup1()", rendered)
        self.assertIn(f'StageId("{cid.removesuffix("_instruction")}")', rendered)
        self.assertIn(row["resource_sha256"], rendered)
        bad_hash = deepcopy(row)
        bad_hash["resource_sha256"] = "0" * 64
        rejected = deepcopy(row)
        rejected["review"]["checks"]["hand_safety"] = "fail"
        no_self_binding = deepcopy(row)
        no_self_binding["bindings"][0]["content_id"] = "unreviewed_target"
        for invalid in (bad_hash, rejected, no_self_binding):
            with self.subTest(scene=invalid), self.assertRaises(ValueError):
                self.load(invalid)
        with self.assertRaisesRegex(ValueError, "approved source ledger"):
            self.load(existing=[row])


class P1ReviewMetadataTests(unittest.TestCase):
    def test_row_metadata_does_not_re_review_unchanged_rows(self):
        review = {"reviewer": {"name": "Original reviewer"}, "reviewed_on": "2026-08-21"}
        record = {"asset_id": "instruction_test_default", "resource_sha256": "1" * 64,
                  "checks": dict.fromkeys(p1.REQUIRED_CHECKS, "pass")}
        unchanged = p1.render(review, [record])
        replacement = p1.render(review, [{**record, "reviewer": "Replacement reviewer", "reviewed_on": "2026-10-25"}])
        self.assertIn("reviewer = P1ExactIndependentVisualReviewCatalog.REVIEWER", unchanged)
        self.assertIn('reviewer = "Replacement reviewer"', replacement)
        self.assertIn('"2026-10-25"', replacement)
        for bad in ("20261025", "2026-W43-7", "2026-02-30"):
            with self.subTest(date=bad), self.assertRaises(p1.ReviewError):
                p1.require_iso_date({"reviewed_on": bad}, "reviewed_on")


if __name__ == "__main__":
    unittest.main()
