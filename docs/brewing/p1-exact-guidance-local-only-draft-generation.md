# Local-only exact-guidance draft generation

## Outcome

Starlit Coffee has complete source-bound draft translations for all 22 supported non-English locales. Each locale covers the same 827 unique translatable strings, 20 recipes, and 114 ordered stages as the canonical English guidance.

These files are drafting and review inputs. They are not production resources and do not change the exact-recipe release gate. English remains the only production-approved exact-guidance locale. Czech retains its separately researched ready-for-native-review package.

## Privacy boundary

Canonical brewing guidance never leaves the workstation during draft generation.

The public model files are downloaded before translation. Generation then runs with HF_HUB_OFFLINE=1, TRANSFORMERS_OFFLINE=1, and local_files_only=True. No translation API is called and no source string is uploaded.

The model itself is stored outside the repository under C:\tmp\starlit-nllb-200-distilled-600M and is never bundled with the app.

## Exact-guidance generator

Tool: tools/generate_p1_exact_guidance_offline_drafts.py

Pinned local runtime:

- facebook/nllb-200-distilled-600M
- transformers 4.53.2
- torch 2.7.1+cpu
- sentencepiece 0.2.0
- sacremoses 0.1.1

Committed source-bound memory: docs/brewing/p1-exact-guidance-offline-draft-translation-memory.json

Ephemeral rendered drafts: build/p1-exact-guidance-localization-drafts/<locale>/p1_exact_guidance.json

The generator:

1. Reads only the immutable canonical source.
2. De-duplicates its 827 translatable strings.
3. Expands ambiguous coffee words before local inference.
4. Replaces every source numeric token with an opaque placeholder.
5. Translates similarly sized strings together to avoid excessive padding.
6. Rejects repeated decoder output with a three-token no-repeat constraint.
7. Restores canonical numbers exactly.
8. Falls back to translating text fragments around measurements if a whole sentence drops or reorders a target.
9. Checkpoints after each accepted batch.
10. Rebuilds the complete locale document and compares its immutable projection, correlated actions, completion cues, warnings, IDs, structure, and provenance with the canonical source.

The feedback loop rejected real model errors, including:

- a repeated timer continuation;
- a collapsed 18–21 range;
- a dropped cumulative pour target;
- Lithuanian “within maximum” becoming an invented “100 tons” limit;
- Dutch “machine-controlled” becoming an invented “30 mm” motor;
- Romanian “paper seated” becoming an invented “50 mm” surface.

The last three phrases now have explicit coffee-equipment disambiguations. Rejected batches were never written to the translation memory.

Regenerate locally with the pinned virtual-environment Python, the generator, the local model path, all 22 locale tags, and a batch size of 64. Set HF_HUB_OFFLINE, TRANSFORMERS_OFFLINE, and PYTHONUTF8 before running it.

Validate without loading a model by running the same generator, model path, and locale list with --check.

## Terminology and contextual UI copy

Machine terminology was not retained after representative review found incorrect or truncated words. The final local terminology drafts are explicit, human-readable candidate mappings in docs/brewing/p1-exact-terminology-local-draft-overrides.json.

They cover 12 canonical coffee concepts and three contextual English-terminology UI strings for each of 21 locales. Czech continues to use its separately researched packet.

Generator: tools/generate_p1_exact_terminology_offline_drafts.py

Generated artifact: docs/brewing/p1-exact-terminology-offline-drafts.json

Regenerate and validate by running the terminology generator once normally and once with --check.

## Status semantics

- approved: reviewed and eligible for production when all other gates pass.
- ready_for_native_review: source and evidence work is complete but native coffee-domain approval is outstanding.
- local_draft_complete: every required string or terminology concept has a local candidate, but independent native-market review and evidence are still outstanding.
- research_required or not_started: required candidate content is absent.

A complete local draft must never be relabeled as approved. Promotion still requires local-market terminology evidence, independent native coffee-domain review of all 20 recipes and 114 stages, reviewed-locale ledger approval, atomic guidance and terminology resource promotion, release-gate registration, and device accessibility and theme validation.
