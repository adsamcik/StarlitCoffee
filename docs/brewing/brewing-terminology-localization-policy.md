# Brewing terminology localization policy

Status: required for every non-English exact-guidance locale.

## Product outcome

Brewing guidance uses the terminology most coffee drinkers in the target locale
actually encounter. It does not force literal translation and it does not force
English when the established local term is clearer. The default remains simple:
the app follows its language and uses the approved community-standard vocabulary
for that locale.

A retained English loanword is localized terminology when it is the established
local usage. For example, a locale may legitimately prefer `dripper` while
localizing `coffee bed` and `drawdown`. Brand and model names are never translated.

## Evidence and approval contract

Every non-English locale must contain source-bound records in the canonical
`p1-exact-localizations.json` for these concepts:

- brewer/dripper, coffee bed, bloom, grounds, fines, slurry;
- drawdown, swirl/spin, server/carafe, steep/immersion, valve, filter paper.

For each concept, the catalog records canonical English, preferred local usage,
accepted and inflected alternatives, misleading terms to avoid, audience and
regional context, confidence, display policy, and evidence source IDs. Native
approval requires at least two corroborating sources for every concept and at
least two source categories across the locale, such as localized manufacturer
material and an established local specialty-coffee educator or retailer.

Source counts are corroboration, not proof of popularity. A named native
coffee-domain reviewer must assess whether the evidence reflects current common
usage, review every instruction in context, and approve the glossary with a
date. Structural checks cannot replace that judgment. A locale remains
fail-closed until both sentence review and terminology review are approved.

## English-reference preference

Do not implement English terminology by replacing words inside localized
sentences. Inflection, agreement, compound terms, and warning phrasing make that
approach grammatically unreliable and potentially unsafe.

English exposure is determined first by the locale × concept policy in the canonical catalog. The optional contextual control is a secondary glossary aid:

- Localized guidance is always the primary default.
- Established English loans remain part of localized terminology where research supports them.
- Mixed, audience-dependent, context-dependent, and regional terms expose English only according to their recorded policy.
- `NO_ESTABLISHED_TERM` uses a natural description; `INSUFFICIENT_EVIDENCE` exposes no isolated label.
- The optional expanded reference keeps the localized instruction unchanged.
- The preference is shown only when the active locale has an approved catalog
  and the current content contains mapped terminology.
- Safety warnings and primary actions never become bilingual fragments.
- Users who want entirely English instructions should change the app language;
  that is a language choice, not terminology substitution.

No permanent settings row is added while English is the only production-approved
exact-guidance locale. The default-off preference is persisted only after a user
interacts with the contextual control, and the control remains absent until the
active approved locale and current stage provide at least one distinct term pair.
The first approved non-English locale remains the trigger for device-level
accessibility, text-scaling, and comprehension validation of its reviewed copy.

## All-locale support contract

The runtime decoder, semantic sidecar, preference, contextual disclosure, and
promotion path support every locale declared in `res/xml/locales_config.xml`:
English, Bulgarian, Czech, Danish, German, Greek, Spanish, Estonian, Finnish,
French, Croatian, Hungarian, Italian, Lithuanian, Latvian, Dutch, Polish,
Portuguese, Romanian, Slovak, Slovenian, Swedish, and Simplified Chinese.

All non-English locales are maintained in `p1-exact-localizations.json`. Source-bound packets under `p1-exact-terminology-review-packets` are generated review views, not independent glossaries. The catalog preserves all 12 concepts, evidence, usage classification, display policy, and unresolved fields for every locale.

`p1-exact-terminology-locale-queue.json` is the generated release-readiness
authority for those 23 locales. It records the exact-guidance editorial state,
terminology state, expected 12 concept IDs, review-packet path, Android resource
presence, ledger approval, production readiness, and every remaining
requirement. The generators fail if the Android locale list changes, concepts
are duplicated or reordered, packets drift, or the checked-in ledger becomes
stale.

Software support does not imply linguistic approval. A locale becomes user-visible only after its complete guidance, catalog terminology, ledger entry, and Android resources are approved. As of 2026-08-06, English is production ready. All 22 non-English locales have researched catalog records but still require native approval; 40 locale/concept records remain insufficiently evidenced and are explicitly withheld.

The detailed source-of-truth and sentence-translation rules are defined in `p1-exact-localization-standard.md`.

## Maintenance

Re-review terminology when manufacturer vocabulary changes, strong local usage
shifts, a new brewing family introduces an unmapped concept, or native feedback
shows that a preferred term is uncommon or confusing. Record the updated
sources, reviewer, and date; do not silently alter already approved guidance.