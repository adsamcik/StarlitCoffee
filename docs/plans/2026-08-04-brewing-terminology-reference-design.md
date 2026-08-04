# Brewing terminology reference design

Status: accepted implementation design, 2026-08-04.

## Decision

Use a semantic terminology sidecar keyed by stable concept and stage-content IDs.
Do not interpolate placeholders into guidance and do not search or replace words
at runtime.

The canonical guidance remains complete authored prose. A separate canonical
sidecar records which concepts are relevant to each exact stage. Every approved
locale supplies a reviewed glossary for those same concept IDs. The renderer may
show the canonical English term as secondary reference material, but it never
modifies the localized sentence.

## Data model

The locale-independent reference manifest contains:

- the exact-guidance source hash;
- stable concept IDs and canonical English display terms;
- stable stage content IDs mapped to ordered, deduplicated concept IDs.

The locale-qualified glossary contains:

- locale and exact-guidance source hash;
- preferred local display terms for each supported concept;
- reviewed UI copy for expanding and collapsing English references;
- review identity and date for non-English production locales.

Both documents fail closed on unknown IDs, duplicate IDs, source-hash mismatch,
blank terms, or locale mismatch. The exact recipe release gate requires the
matching reviewed terminology glossary before enabling a non-English exact
recipe. English remains valid without displaying redundant English-to-English
references.

## Runtime behavior

The user's stable preference is `LOCAL` or `LOCAL_WITH_ENGLISH_REFERENCE`, with
`LOCAL` as the default. It is global because terminology preference is user
intent, not recipe state, but the affordance is contextual:

1. Resolve the current stage's concept IDs.
2. Join them to the active locale glossary and canonical English concepts.
3. Omit concepts whose preferred local term equals the canonical English term.
4. Hide the terminology control when the result is empty.
5. When collapsed, show one low-emphasis text action after ordinary guidance.
6. When expanded, show compact `local — English` rows after the complete
   guidance block. References never interrupt structured or authored warnings,
   which remain visually separate and unchanged.
7. Persist the preference only when the user explicitly changes it.

The reference rows are supplementary semantics. They never enter the live-region
announcement for stage changes, never replace illustration alt text, and never
appear in Picture-in-Picture.

## Why this model

- Complete localized sentences keep grammar, inflection, compounds, and warning
  polarity under editorial control.
- Stable concept IDs survive wording changes and allow locale-specific loanwords.
- A sidecar preserves the immutable evidence-source guidance document and hash.
- Contextual disclosure avoids an inert permanent settings row and keeps novice
  brew flow uncluttered.
- Experts can learn the cross-language vocabulary once and retain the preference
  across recipes.

## Rejected alternatives

- **Sentence placeholders:** grammar and word order are not composable across
  languages.
- **Runtime search/replace:** ambiguous words, morphology, and partial matches
  can corrupt instructions or warnings.
- **Always bilingual guidance:** doubles visual and accessibility load during a
  time-sensitive task.
- **English-only guidance switch:** changes the whole instruction language and
  does not solve terminology learning.
- **Permanent settings-only toggle:** hard to discover and currently inert while
  English is the sole approved exact-guidance locale.

## Verification

Tests must cover strict sidecar decoding, locale/source identity, unknown and
duplicate concepts, stage ordering, redundant-term omission, preference default
and persistence, contextual visibility, content order, and preservation of
warnings at every guidance density.