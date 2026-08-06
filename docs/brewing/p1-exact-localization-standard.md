# P1 localization and terminology standard

Status: canonical maintenance contract for exact brewing guidance.

## One language model

P1 localization has one semantic model and one maintained terminology catalog:

- stable concept IDs describe brewing meaning;
- each locale records authentic preferred usage, alternatives, inflected forms,
  misleading terms, audience/context rules, regional variants, confidence,
  evidence, and review state;
- guidance is translated as complete natural sentences and linked to those
  concept IDs by the semantic sidecar;
- no runtime or build-time word substitution is allowed inside translated
  guidance.

The canonical multilingual source is
`docs/brewing/p1-exact-localizations.json`. Each non-English locale has one
record containing its complete guidance-string translations and the same 12
terminology concepts with policy and evidence. Review packets, readiness
ledgers, search/glossary metadata, English-reference behavior, rendered drafts,
and future Android locale resources are derived from it. They are not
independent places to edit translated language.

The dated files under `docs/brewing/research/terminology-2026-08-06` are immutable
provenance for the current catalog. They support audit and regeneration but are
not a second product glossary.

## Translation contract

1. Select the target locale and, where required, the target region.
2. Read the locale's catalog entries for every concept used by the source stage.
3. Translate the entire instruction naturally for its audience and context.
4. Preserve source quantities, states, warnings, observable cues, and completion
   behavior exactly.
5. Use a preferred term or accepted alternative only where its recorded context
   permits it. Use natural inflection rather than pasting a dictionary form.
6. If a record is `NO_ESTABLISHED_TERM`, describe the meaning naturally.
7. If a record is `INSUFFICIENT_EVIDENCE`, do not invent or expose a glossary
   label. Retain a descriptive sentence and block terminology approval.
8. Run concept-aware consistency validation, then native coffee-domain review.

The catalog guides sentence translation; it never assembles sentences.

`tools/audit_p1_exact_terminology_prose.py` checks every semantic stage/concept
occurrence against the locale catalog. For unapproved drafts it creates a review
queue, because inflection and indirect descriptions cannot be judged safely by
substring matching. For an approved locale, withheld records, unmatched
concepts, or terms marked to avoid are release-blocking. The audit never rewrites
prose.

## English-reference behavior

English usage is decided per locale and concept:

| Classification | Localized guidance default | English behavior |
|---|---|---|
| `LOCALIZED_DOMINANT` | Preferred localized term | Glossary/search alias only |
| `ENGLISH_DOMINANT` | Established English loan used as local terminology | Display normally |
| `MIXED_STABLE` | Natural local or mixed form | Contextual first-occurrence reference |
| `AUDIENCE_DEPENDENT` | Beginner-appropriate local wording | Reveal in advanced context |
| `CONTEXT_DEPENDENT` | Wording appropriate to the current method/action | Reveal only where relevant |
| `REGION_DEPENDENT` | Region-specific preferred form | Apply the region record first |
| `NO_ESTABLISHED_TERM` | Descriptive phrase | Suppress isolated English label |
| `INSUFFICIENT_EVIDENCE` | Descriptive sentence only | Suppress pending review |

A user's optional English-reference preference is a secondary glossary aid. It
does not override established local usage, reveal withheld labels, or make
primary instructions bilingual. Users wanting entirely English instructions
select English as the app language.

## Source-of-truth boundaries

- Canonical English guidance: `app/src/main/assets/p1_exact_guidance_2026_07_27.json`.
- Semantic stage/concept links: `app/src/main/assets/p1_exact_terminology_references_2026_07_27.json`.
- All localized sentence drafts, terminology, and locale UI copy: `docs/brewing/p1-exact-localizations.json`.
- Research provenance: the dated research directory.

Generated review packets, locale queues, and Android `raw-*` resources must
never be edited as translation authorities. A change starts in the appropriate
source above and is regenerated downstream.

## Approval and failure behavior

Research is not native approval. Every non-English locale remains fail-closed
until its complete sentence set and terminology catalog are approved by a named
native coffee-domain reviewer, recorded in the review ledger, promoted
atomically, registered in the release gate, and validated on device for themes,
text scaling, accessibility, and layout.

The current catalog contains 264 records. Forty remain
`INSUFFICIENT_EVIDENCE`; eleven locales therefore have partial research
coverage. These records must remain visibly unresolved in maintenance tooling
and absent from production glossary surfaces.

The current generic Portuguese record contains five `REGION_DEPENDENT`
concepts. It is research input, not a promotable production locale. Portuguese
exact guidance must be split into complete `pt-BR` and `pt-PT` records (including
full-sentence guidance, terminology, ordinary app copy, and device QA) before
either regional variant can be approved. A combined `pt` glossary must never be
promoted by choosing one market's wording for both.
