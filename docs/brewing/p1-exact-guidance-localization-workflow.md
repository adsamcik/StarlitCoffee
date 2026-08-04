# Exact P1 guidance localization workflow

This workflow localizes the 20 exact P1 recipes and 114 ordered stages without
changing recipe identity, brewing quantities, timing, equipment state, evidence,
or illustration linkage.

## Production boundary

- `app/src/main/assets/p1_exact_guidance_2026_07_27.json` is the immutable
  canonical English and tooling source.
- `app/src/main/res/raw/p1_exact_guidance.json` is the reviewed English runtime
  resource selected by Android.
- A reviewed translation belongs at
  `app/src/main/res/raw-<locale>/p1_exact_guidance.json`.
- `P1ExactRecipeLocalizationCoverage.production` is the release authority. Add
  a locale to a recipe only after every stage in that recipe has passed the
  reviews below.
- The release gate evaluates the active app locale. It never silently promotes
  canonical English as localized guidance for another language.

The accepted illustrations are locale-independent. Their content descriptions
come from each selected stage's `accessible_alt_text`, so the image, instruction,
warning, and accessibility description always share one localized source.

## Reproducible draft generation

`tools/generate_p1_exact_guidance_localizations.py` inventories 827 unique
user-facing source strings, maintains a source-hash-bound translation memory,
generates draft or explicitly approved Android JSON, and validates immutable
fields.

Machine output is a draft only. The tested setup was an isolated virtual
environment with Python and `deep-translator==1.11.4` using Google Translate:

```powershell
python -m venv C:\tmp\starlit-translation-venv
C:\tmp\starlit-translation-venv\Scripts\python.exe -m pip install deep-translator==1.11.4
C:\tmp\starlit-translation-venv\Scripts\python.exe tools\generate_p1_exact_guidance_localizations.py --translate --locales cs
```

The generator expands ambiguous English coffee terms before machine translation.
For example, it distinguishes a brewing device from a beer brewer, coffee
grounds from land, bloom from flowers, fines from penalties, and drawdown from
an unrelated general-language meaning. It also restores every numeric token
from the canonical source after translation.

Machine drafts and draft memory are written only under
`build/p1-exact-guidance-localization-drafts`; `--translate` cannot write Android
resources. Field corrections and per-stage decisions belong in the locale's
source-hash-bound file under
`docs/brewing/p1-exact-guidance-editorial-reviews`. Draft generation applies
partial corrections, but production promotion additionally requires that file's
top-level status and all 114 stable-ID stage records to be `approved`.

After reviewing all 114 stages, copy the machine memory to
`docs/brewing/p1-exact-guidance-translation-memory.json`, approve the editorial
review file, add the locale approval to
`docs/brewing/p1-exact-guidance-reviewed-locales.json`, and promote explicitly:

```powershell
python tools\generate_p1_exact_guidance_localizations.py --promote-reviewed --locales cs
```

## Required human review

Review all 114 stages in order for each recipe and locale. Do not sample only a
few stages.

1. Confirm the action describes the illustrated operation and physical brewer.
2. Confirm every warning preserves its prohibition, condition, and severity.
3. Confirm completion remains observation-driven where the source is
   observation-driven.
4. Confirm dose, brew water, reservoir input, beverage yield, concentrate, ice,
   bypass, dilution, and final beverage remain distinct.
5. Confirm time, temperature, mass, percentages, ratios, and cumulative targets
   exactly match the canonical source.
6. Confirm coffee-community terminology is natural for the target language;
   avoid literal general-language senses of brewer, grounds, bed, bloom, fines,
   slurry, drawdown, spin, swirl, server, and dripper.
7. Confirm brand and model names remain correct.
8. Confirm alt text accurately describes the accepted image without adding
   unsupported actions or invisible details.
9. Confirm the full, concise, focused, and utilities-only presentations remain
   consistent and that safety stays visible at every density.
10. Run the locale in both light and dark themes with large text and screen-reader
    semantics enabled.

## Automated checks

Run the offline validator for reviewed locales:

```powershell
python tools\generate_p1_exact_guidance_localizations.py --check --locales en cs
python tools\generate_p1_tracker_accepted_asset_catalog.py --check
```

The localization validator fails if a translation changes stable IDs, JSON
structure, provenance, evidence, utilities, visual priority, source metadata,
numeric tokens, `None` sentinels, correlated actions, completion cues, warnings,
or practical tips. It also rejects blank output and excessive English fallback.

These checks establish structural and numerical safety. They do not replace the
native-language and brewing-domain review above.

## Rejected Czech pilot, 2026-08-04

The first Google-translated Czech pilot was rejected and removed. It translated
`brewer` as `sládek` (a beer brewer), `grounds` as land, `bloom` as a flower,
and `fines` as penalties. Domain disambiguation corrected those categories in a
later draft, but several sentences remained unnatural. No Czech draft or
translation memory was promoted or committed.

This failure is the reason production currently records reviewed English only.
Do not add a locale to production coverage merely because the generator's
structural checks pass.
