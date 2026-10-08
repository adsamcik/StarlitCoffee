# Minimal baseline

Created 2026-10-08 using the built-in image generator, in response to the user's feedback that the previous directions should be minimalistic. This is one stripped-down baseline with two scenario mockups, for feedback before producing more variations. It preserves the earlier requested basic/concurrent timers and learning-while-brewing scenarios.

- [Timer after bloom with concurrent cold brew](minimal-bloom-timers.png): retained completed animation pose, one dominant elapsed clock, scoped controls, and a quiet background brew row.
- [Guided preparation with concurrent cold brew](minimal-bloom-guided.png): initial bloom-animation pose, current quantity and instruction, contextual help, and one primary action.

Earlier drafts are preserved: [generic timer without bloom history](minimal-timers.png) and [superseded guide that omitted bloom identity](minimal-guided.png).

Visual intent: flat dark surface, typography-led hierarchy and restrained lilac action. The existing bloom animation is core brewing identity and stays visible for bloom-capable sessions. Surrounding UI avoids card stacks, equipment illustrations, timelines, timer rings and status badges. Guidance adds the current instruction; more explanation is reached through Help with this step, and recipe details through Recipe.

## Bloom identity correction

The user's clarification supersedes the earlier draft's treatment of the bean as optional decoration. The selected bloom animation starts in its initial pose before bloom, animates with bloom progress, and retains its completed pose through the rest of a brew that included bloom. This applies to both timer-focused and guided presentation. Plain timers and methods without bloom do not gain a bloom animation.

Current bloom-capable mockups:

- [Timer after bloom](minimal-bloom-timers.png): the existing animation's completed coffee-flower pose remains above the elapsed clock.
- [Guided bloom preparation](minimal-bloom-guided.png): the existing initial animation pose remains above the bloom quantity and instruction.

The earlier generic timer still illustrates a timer without bloom history. The earlier guided image is preserved as a superseded draft. The new pair uses the actual `bloom_coffee_flower_spritesheet.png` as its art reference. These generated static mockups approximate the source frames; the existing spritesheet and animation renderer remain authoritative. No replacement animation or artwork asset is introduced.

The current behavior was verified in `BrewTimerScreen.kt` and `BloomSpritesheetAnimation.kt`: a bloom-capable session displays the selected animation, and its final frame remains through subsequent pours. [Exact correction prompts](bloom-identity-prompts.json) and [source mapping](bloom-identity-manifest.json) are preserved. Both new images were visually self-reviewed for animation presence, retained minimal hierarchy, complete controls and independent cold-brew access, with source copy hashes and portrait dimensions checked.

[Exact prompts](prompts.json) and [source mapping](manifest.json) are preserved. Original generator images and previous concepts remain available.

Both outputs were visually self-reviewed: clear labels and controls, readable complete screens, no decorative instruction artwork, and an accessible background cold-brew row. Saved copies were checked against source hashes and PNG dimensions; prompt/manifest counts and scoped Git whitespace checks were verified. No application code changed. These images propose presentation, not validated brewing instructions, implemented timer behavior, native accessibility or persistence guarantees. The final guided copy and control semantics must follow the real method and clock behavior if this direction is selected.
