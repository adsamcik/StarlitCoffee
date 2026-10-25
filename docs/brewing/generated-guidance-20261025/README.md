# Reviewed brewing instruction artwork — 2026-10-25

The admission manifest covers all **344 physical instruction contexts** across the 17 reviewed methods, generic/legacy guidance and canonical P1/Pulsar curricula, plus one auxiliary live water-height cue. Safety-only and utility-only copy is not counted as a separate physical instruction.

The 230 packaged resources contain 205 distinct delivered images. This batch adds 124 independently reviewed generated masters and retains 83 canonical P1 resources after a fresh full-size and phone review. Canonical content IDs remain stable; shared scenes require an explicit current-content binding.

## Admission boundary

- Generate artwork with the built-in image generator. Preserve the exact prompt and original generated PNG separately from mechanical delivery conversion. Never repair brewer geometry by hand.
- Check actual manufacturer/recipe references and the exact target instruction. Keep quantities, timing, variant selection, safety and completion criteria in authored text; an illustration may show an explicitly bounded subaction or state.
- Independently inspect full-resolution artwork and the 1024×768 alpha WebP at 320×240 on light and dark backgrounds. New scenes require all nine recorded checks: full resolution, both phone themes, equipment geometry, action/state, hand safety, crop/flow, caption agreement and minimalism.
- Reject misleading anatomy, valve positions, disconnected liquid, unsafe grips, ambiguous filter/variant assumptions, unreadable critical geometry or captions that overclaim the pixels. Rejected iterations and superseded review history remain in this directory and the delivery trackers.
- Admit only the reviewed resource/raw/prompt SHA-256 values, reviewer/date, physical source scope, literal caption and exact target-content revision. Shared-scene bindings fail closed if their instruction changes. Canonical Pulsar assets require complete independent scene admission; the old delivery tracker cannot approve them.

The 83 retained P1 resources passed the renewed original eight-check review. Their older tactile shading and occasional dark backing shapes differ from the new editorial scenes; the independent reviewer confirmed that they remain focused and legible. This is not a claim that every retained image has the same flat visual style. See `reviews/legacy-renewal/retained-art-minimalism-qc-statement.json` for that bounded assessment.

## Evidence and maintenance

- `admission.json` is the final hash-bound admission record; `bindings-draft.json` is the frozen source-contract inventory and initial proposals, not an approval record.
- `binding-overrides.json` records the final exact-stage replacements and bounded reuse decisions.
- `groups/*/manifest.json` preserves generated candidates and source references. Independent reports and full-size/phone evidence are in each group's reviews directory.
- `reviews/legacy-renewal/` preserves the original delivery/review records and renewed canonical P1 inspection, including the 31 failures replaced by this batch.
- Native before/after evidence and actual verification results are recorded in `verification.md` after execution.

Run from the checkout root:

```text
python tools/generate_guidance_asset_catalog.py --check --require-complete
python tools/generate_p1_tracker_accepted_asset_catalog.py --check
python tools/generate_p1_exact_visual_review_catalog.py --check
python tools/verify_instruction_assets.py
python tools/test_guidance_asset_admission.py
```

The existing localization and release workflows enforce complete admission and generated-catalog consistency. JVM coverage reconstructs current contracts from shipped source catalogs rather than trusting the frozen inventory alone. Native samples establish rendering and interactions only for the exercised screens; they do not establish physical brewing accuracy or runtime traversal of all 344 contexts.
