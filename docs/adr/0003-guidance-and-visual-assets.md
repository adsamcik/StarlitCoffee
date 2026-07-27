# ADR 0003: Guidance levels and visual assets

**Status:** Accepted

## Context

Brewing needs to teach unfamiliar equipment without slowing experienced users.
Static method tips and decorative bloom artwork cannot supply reusable,
accessible stage guidance.

## Decision

One shared stage-content catalogue powers both Learn and live Brew. Each content
record supplies a primary instruction, optional explanation/tip/warning, alt
text, visibility policy, and an optional illustration asset ID.

Guidance is a preference per method family, with optional profile override and
temporary session override. The presentation levels are Full, Concise, Focused,
Utilities only, and advanced Custom. They change presentation only; recipe and
stage execution remain identical. Critical safety and equipment warnings are
always visible.

Instruction assets use a compile-time-safe manifest with drawable and string
resource references. Production illustrations are original, local, text-free,
reviewed WebP assets. The manifest records method/profile/stage, aspect ratio,
prompt, revision, review status, and accessibility text. Missing or unreviewed
mandatory assets prevent a profile from being release-complete.

## Consequences

- Learn can resume without creating an active brewing session.
- Existing `showBrewingInstructions` migrates to a conservative guidance level.
- No visual information may be the only way to understand an action.
- Asset validation becomes part of the build/test workflow.
- Custom layout stays behind progressive disclosure; default users see only the
  modules needed for their current recipe.
