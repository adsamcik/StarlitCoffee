# Minimal baseline

Created 2026-10-08 using the built-in image generator, in response to the user's feedback that the previous directions should be minimalistic. This is one stripped-down baseline with two scenario mockups, for feedback before producing more variations. It preserves the earlier requested basic/concurrent timers and learning-while-brewing scenarios.

- [Basic timer with concurrent cold brew](minimal-timers.png): one dominant elapsed clock, pause/end controls scoped to that clock, a quiet background brew row, and access to all timers.
- [Guided preparation with concurrent cold brew](minimal-guided.png): one dominant current quantity, a short actionable instruction, contextual help, a ready timer and one primary action. The independent cold brew remains accessible.

Visual intent: flat dark surface, typography-led hierarchy, restrained lilac action, no card stacks, illustrations, timelines, timer rings or status badges. Guidance adds only the current instruction; more explanation is reached through Help with this step, and recipe details through Recipe.

[Exact prompts](prompts.json) and [source mapping](manifest.json) are preserved. Original generator images and previous concepts remain available.

Both outputs were visually self-reviewed: clear labels and controls, readable complete screens, no decorative instruction artwork, and an accessible background cold-brew row. Saved copies were checked against source hashes and PNG dimensions; prompt/manifest counts and scoped Git whitespace checks were verified. No application code changed. These images propose presentation, not validated brewing instructions, implemented timer behavior, native accessibility or persistence guarantees. The final guided copy and control semantics must follow the real method and clock behavior if this direction is selected.
