# Recommended label-recognition experience

Date: 2026-09-17. Product recommendation following the AI integration audit.
This document proposes behavior; it does not change the accepted architecture or
claim that the recommendations are implemented or user-tested.

**Use best effort to help fill the draft, strict evidence rules to accept values,
and explicit failure when the app cannot preserve or save the user's work.**
An optional inference stage failing should not make an otherwise usable coffee
draft fail. A model returning JSON should not by itself make a draft successful.

## The user's task and the golden path

Consider someone who has just bought a bag and wants to add it before brewing.
They know which coffee they photographed. They do not want to manage an OCR
provider, wait for fourteen fields, or learn why an inference stage failed.

1. **Take one photo or choose one from the gallery.** Create a durable draft as
   soon as the photo is staged. Offer another side when useful; do not require
   two photos or AI setup. Keep manual entry available.
2. **Open the editable review immediately.** Show the photo and fields together.
   Run the bundled recognizer first so optional Mindlayer readiness cannot delay
   baseline results. Populate usable fields as they arrive. Preserve the draft
   if the user leaves.
3. **Improve specific gaps while the user reviews.** If enrichment is already
   enabled and ready, use it where it can resolve a real ambiguity or read useful
   remaining detail. Do not run translation, vision, combine, and refinement
   merely because those stages exist.
4. **Keep the review stable.** Prefer empty-field fills. Apply a material change
   to an already displayed identity, weight, date, or roast value as a suggestion
   once review is underway. Never overwrite a user's edit or a focused field.
   Highlight meaningful uncertainty without forcing confirmation of every field.
5. **Let the user save as soon as the form is valid.** The current form requires
   a name and valid weight input; optional unknown fields should remain unknown.
   Saving commits the reviewed snapshot and ends its enrichment generation.
   Late results must not silently change the saved bag. Discard ends work too.

For an already configured user, the ordinary path is photo -> quick review ->
save. For a user without Mindlayer, that same path still works with less automatic
detail. Optional setup is a contextual way to improve recognition, not a detour
the user must take to complete the task.

## What best effort should mean

- Preserve independently valid fields when another field or pass fails.
- Keep source evidence and uncertainty through normalization and translation.
  A transformed string must not become stronger evidence than its source.
- Treat an absent optional label detail as a normal outcome. Distinguish it from
  an unreadable region or an unavailable recognizer; those are not evidence of
  absence. In particular, lack of a decaf marker is not proof of caffeinated coffee.
- Leave unsupported guesses blank or offer a clearly uncertain suggestion.
  Do not invent plausible dates, weight, origin, or identity to complete the form.
- Do not silently use an unconfirmed guess for downstream inventory changes or
  coffee-specific brewing adjustments. Use the app's explicit defaults when a
  property is unknown.
- Make reliable information available promptly. A slow optional stage must not
  hold the form, Save, or the baseline recognizer hostage.

## Failure and recovery contract

| Situation | User-facing behavior | System behavior |
| --- | --- | --- |
| Mindlayer missing, disabled, unsupported, or not configured | Continue with the editable draft; optional setup only when relevant | Run bundled OCR; no automatic repeated setup attempts or alternative-provider routing |
| Recognition is slow but useful fields exist | Keep review/save active; say "Checking for more details" while work is actually running | Bound enrichment; stop on Save/Discard; background continuation must remain within the same draft and deadline |
| Service busy, disconnected, or locally timed out | Keep results; if worthwhile, offer "Try again" | Retry the failed optional step once for a transient cause when time remains; honor service backoff and preserve parent cancellation |
| A model returns malformed output | Preserve already usable details; mention inability to read more only if it affects the result | Reject invalid fields/envelopes, salvage independently valid siblings, optionally make one bounded repair attempt; record the real failure |
| Photo is blurry, cropped, dark, or reflected | Explain the photo problem and offer "Retake photo" or "Add the other side" | Do not repeatedly run the same expensive model on unchanged unusable evidence |
| All recognition produces no usable fields | "Couldn't read this label. Enter the details or try another photo." Keep photo and manual form | Finish the attempt honestly; no empty "success" and no endless spinner |
| Two sources materially disagree | Mark the field for review and show the relevant photo evidence | Preserve alternatives; don't treat repeated calls to the same model as independent confirmation |
| User edits, saves, discards, or cancels | Respect the action immediately; do not display a failure for intentional cancellation | Reject stale generations; stop retries; do not resurrect drafts or overwrite edits |
| App restarts or a worker dies | Restore the draft and its useful fields; offer further recognition if useful | Recover from durable state; do not replay completed stages unnecessarily or loop forever |
| Draft/photo persistence or final save fails | Clearly say "Couldn't save. Your details are still here" only when they actually remain available; offer retry | Do not dismiss the form, claim success, delete staged inputs, or finalize the draft as saved before durable persistence succeeds |

The recovery action must follow the cause. No extracted fields does not imply a
bad photo, and an installed but disconnected service does not imply missing
authorization. Use quiet inline feedback for optional enrichment failures;
reserve blocking save feedback for an operation that cannot safely complete.

## Recommended orchestration

Persist draft -> bundled OCR -> publish valid partial fields -> optionally
enrich unresolved useful fields -> validate and merge -> finish with complete,
partial, or no-result recognition. User editing and saving remain available
throughout, independently of recognition state.

Use per-stage budgets inside a total deadline. After a short useful attempt,
settle the foreground experience with partial results; longer work should be
optional and preserve the draft. Establish actual time targets on supported
devices. The existing five-minute deadline is a runaway-work safety net, not
a reasonable foreground waiting experience.

Reuse successful per-photo OCR when adding a back photo; cancel obsolete
generations without discarding reusable evidence. Retry failed stages rather
than starting the entire scan again. Vision should target a specific unresolved
field or region. Combine should run only for meaningful disagreement; refinement
should run only for demonstrated normalization problems. Stop when another call
has little expected benefit. Empty optional fields alone are not a reason to
keep spending time.

## How this differs from current behavior

- [ADR 0004](../adr/0004-user-owned-label-recognition-drafts.md) already establishes
  durable, user-owned drafts, immediate review, edit protection, and terminal
  Save/Discard. Preserve these decisions.
- `StarlitCoffeeApp.PreferenceAwareOcrService` currently tries optional Mindlayer
  OCR before bundled OCR when enrichment is enabled. Make baseline availability
  independent of optional-provider failure and latency.
- `RecognitionUiStateMapper.fromPipeline` and `ScanAddBagReview` map generic
  installed-service unavailability to authorization required. Preserve the actual
  cause instead; reconnect failures should not prompt consent again.
- `RecognitionUiStateMapper.map` chooses Retake for a retriable failure solely
  because no fields exist. Carry photo-quality/provider-failure information so
  the user is not asked to retake a good photo after a service outage.
- `BagPhotoExtractor.runLlmEnrichmentStages` reports the text pass's status for
  later stages. Track each stage honestly, then derive the user outcome from
  usable fields and unresolved conflicts. Partial extraction may be useful even
  when a component failed.
- `AddBagSheet` already permits save during recognition, but separately disables
  it during QR exploration. An optional lookup should also be cancellable by
  saving the reviewed snapshot; it should not become another save dependency.

## Acceptance criteria

Test the same user journey with Mindlayer ready, absent, disconnected, slow,
returning malformed output, and dying mid-scan. A user must still be able to
finish an editable, persistable bag whenever manual input is possible. Verify
that unknown values stay unknown, trustworthy partial values survive, recovery
actions match the cause, edits remain stable, and Save/Discard cannot be undone
by a late result. Separately test true persistence failures to ensure the app
never falsely reports a successful save.

Measure time to first usable draft, corrections needed, incorrect accepted
fields, successful saves, and repeated-scan behavior. Count component failures
in diagnostics without equating them with user-task failure. The primary quality
target is a quickly saved bag with accurate accepted details, not maximum field
coverage or every AI stage completing.
