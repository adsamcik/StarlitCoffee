# ADR 0004: User-owned label-recognition drafts

**Status:** Accepted

## Context

Coffee-label extraction can take longer than capture and can finish after the
user has already corrected a field, left the screen, discarded the scan, or had
the Android process recreated. Treating a WorkManager result as the form's
owner makes review feel blocked and allows late work to resurrect or overwrite
user intent. Requiring Mindlayer setup before capture also makes an optional
enhancement a hard dependency of the core flow.

## Decision

Every still-photo scan creates a durable `BagScanDraft` identified by its exact
session ID. The draft, not the worker, owns staged photos, field values, user
revisions, review state, and terminal Save/Discard state. Review opens
immediately and remains editable while recognition publishes partial results.

The recognition baseline is bundled ML Kit OCR. Mindlayer OCR and structured
LLM passes are optional on-device enrichment selected through one preference
and contextual setup actions. Provider state maps through a provider-neutral UI
model before reaching Compose.

Recognition may replace only fields that have no user revision and are not
currently focused. A conflicting late value becomes a pending suggestion.
Generation IDs reject stale work. Save and Discard persist terminal tombstones;
workers, recovery, and notification delivery must check them before publishing.
Closed tombstones retain only ownership identifiers and redact label fields,
result payloads, review context, and staged-photo references.
Active drafts have no silent age-based expiry and are reachable from inventory
and exact notification deep links.

## Consequences

- Capture, gallery import, manual entry, editing, and save do not depend on
  Mindlayer installation, authorization, model readiness, or inference success.
- Process recreation can restore the current form without serializing the full
  draft through navigation state.
- Users explicitly own draft deletion; staged photos consume private storage
  until Save or Discard and are excluded from backup.
- The draft schema, atomic storage, merge rules, worker suppression checks, and
  exact-session navigation require dedicated regression coverage.
- Existing WorkManager checkpoint/result stores remain execution and recovery
  infrastructure; they are not an alternate source of user-visible form truth.
