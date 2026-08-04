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

Every non-English editorial review must contain a source-bound
`terminology_review` with these canonical concepts:

- brewer/dripper, coffee bed, bloom, grounds, fines, slurry;
- drawdown, swirl/spin, server/carafe, steep/immersion, valve, filter paper.

For each concept, record canonical English, preferred local terms, accepted
alternatives, misleading terms to avoid, a concise rationale, and at least two
evidence source IDs. The complete source set must contain at least two sources
and span at least two categories, such as localized manufacturer material and
an established local specialty-coffee educator or retailer.

Source counts are corroboration, not proof of popularity. A named native
coffee-domain reviewer must assess whether the evidence reflects current common
usage, review every instruction in context, and approve the glossary with a
date. Structural checks cannot replace that judgment. A locale remains
fail-closed until both sentence review and terminology review are approved.

## English-reference preference

Do not implement English terminology by replacing words inside localized
sentences. Inflection, agreement, compound terms, and warning phrasing make that
approach grammatically unreliable and potentially unsafe.

The safe future preference is **Terminology: Local / Local + English reference**:

- `Local` is the default and shows only approved localized guidance.
- `Local + English reference` keeps the localized instruction unchanged and
  exposes the canonical English concept in a secondary, expandable terminology
  surface.
- The preference is shown only when the active locale has an approved crosswalk
  and the current content contains mapped terminology.
- Safety warnings and primary actions never become bilingual fragments.
- Users who want entirely English instructions should change the app language;
  that is a language choice, not terminology substitution.

No permanent setting is added while English is the only production-approved
exact-guidance locale: it would have no effect and add avoidable complexity. The
first approved non-English locale is the implementation trigger for the
contextual preference and its accessibility, text-scaling, and comprehension
validation.

## Maintenance

Re-review terminology when manufacturer vocabulary changes, strong local usage
shifts, a new brewing family introduces an unmapped concept, or native feedback
shows that a preferred term is uncommon or confusing. Record the updated
sources, reviewer, and date; do not silently alter already approved guidance.