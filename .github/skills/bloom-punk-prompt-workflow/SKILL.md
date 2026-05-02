---
name: bloom-punk-prompt-workflow
description: Project-level workflow for creating Starlit Coffee punk bloom spritesheet prompts and matching final-still prompt locks. Uses council ideation, one-agent-per-theme drafting, reviewer passes, and strict 5x5 validation.
version: 1.0.0
triggers:
  - bloom punk prompts
  - punk bloom prompts
  - bloom spritesheet prompts
  - add bloom theme
  - final still prompts
---

# Bloom Punk Prompt Workflow

Use this project-level skill when adding or revising Starlit Coffee bloom spritesheet prompts, especially punk-flavored themes. The output lives in `prompts\bloom-spritesheet-frame-prompts.md`.

## Project Contracts

- Spritesheets are `25` frames in a `5 x 5` row-major atlas.
- Each variation has one section with:
  - `## Theme Name`
  - `Target file: bloom_<slug>_spritesheet.png`
  - fenced `text` block containing `Subject`, `Final object contract`, `Final-bloom lock`, and exactly 25 frame lines.
- Frame labels must be exactly `R1C1 Frame 01:` through `R5C5 Frame 25:`.
- One fixed roasted coffee bean base persists in every frame.
- Frames 01-10 are subtle setup, frames 11-20 are compressed pre-bloom, frames 21-25 are final release.
- Frame 23 is the first recognizable final silhouette.
- Frame 24 has exactly one unresolved detail.
- Frame 25 resolves only that detail and adds no new anatomy.
- Final still prompts use `bloom_<slug>_final.png` and must be locked to the matching `R5C5 Frame 25` line.

## Workflow

### Phase 1: Recon

Read:

- `prompts\bloom-spritesheet-frame-prompts.md`
- `docs\bloom-spritesheet-splicing.md`

Confirm whether the task is:

- adding new theme prompts,
- revising existing prompts,
- adding final-still locks,
- or reviewing prompt quality.

### Phase 2: Creative Council

For broad creative additions, run a four-role council before drafting:

| Role | Purpose |
|------|---------|
| Workflow Architect | Defines the production workflow, section format, and validation gates. |
| Genre Taxonomist | Separates motif vocabulary so themes do not collapse into the same visual language. |
| Animation Choreographer | Designs frame-by-frame growth laws and release timing. |
| Imagegen Critic | Finds wording that causes grid failures, scene drift, clutter, transparency, or final-frame drift. |

Council output must produce:

- one silhouette-first concept per theme,
- primary material and motion vocabulary,
- forbidden scene/collage elements,
- a part-count strategy for sprite readability,
- final-frame unresolved-detail plan.

### Phase 3: One Agent Per Theme

Draft each theme independently. The per-theme brief must require:

- one centered object, not a scene;
- genre cues attached to the object;
- 2-4 bold readable visual cues;
- concrete visible delta in every frame;
- wrap continuity for 05->06, 10->11, 15->16, and 20->21;
- no text, labels, UI, logos, backgrounds, extra props, duplicate beans, or duplicate final objects.

For punk themes, avoid "flower with decorations" unless the theme naturally supports a botanical object. Mechanical, elemental, textile, symbolic, or hybrid bloom objects are allowed if they remain a single coffee-bloom sprite.

### Phase 4: Normalize and Append

Before editing the prompt file:

1. Normalize headings, target filenames, and text fences.
2. Ensure all user-facing prompt text is ASCII unless the file already requires otherwise.
3. Lock counts for petals, panels, gears, blades, ribs, loops, or other repeated parts.
4. Replace imagegen-risk terms:
   - `holographic` -> opaque painted plate, etch, lens, or glow.
   - `levitating` -> raised, anchored, or supported.
   - scene words -> object-attached material words.
5. Keep the bean as roasted coffee bean anatomy, never as a renamed base or prop.

### Phase 5: Reviewer Council

Run two independent reviewers:

- Opus 4.7 for strict consistency, contradictions, and imagegen failure modes.
- GPT 5.5 for practical frame clarity and replacement wording.

Review dimensions:

- structure,
- frame standalone clarity,
- continuity,
- timing contract,
- genre uniqueness,
- imagegen risk,
- final-still compatibility.

Apply concrete fixes when both reviewers agree or when one reviewer identifies a hard contract violation.

### Phase 6: Final Still Locks

For every theme with a `bloom_<slug>_spritesheet.png`, add or verify:

- `Target file: `bloom_<slug>_final.png`` under `## Punk Final Still Prompt Contract`;
- source lock copied from the theme's exact `R5C5 Frame 25` line;
- wording that forbids new anatomy, new scale, new bean base, background scene, shadow, glow spill, or separate design polish.

Preferred still generation is extraction from frame 25. Separate still generation is allowed only when locked to the source line.

## Validation

Run a structural validation script or equivalent checks:

- all requested sections exist;
- all target filenames exist in prompt text;
- every section has exactly 25 labels;
- labels equal `R1C1 Frame 01:` through `R5C5 Frame 25:`;
- final still targets exist for every punk spritesheet target;
- no known risky words remain unless intentionally justified.

Reject prompts that:

- are fully finished before frame 23,
- use row starts as story resets,
- rely on style/lighting drift as the frame delta,
- place genre identity in backgrounds,
- wrap or repaint the coffee bean as a different object,
- make frame 25 add a new part instead of resolving one named detail.

