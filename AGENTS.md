# Repository guidance for all agents

These instructions apply throughout the repository.

## Release discipline

Every release must include a complete, user-facing entry in `CHANGELOG.md` before
the release is built or published. The entry must use the release version and
date, follow the existing Keep a Changelog structure, and accurately cover
user-visible additions, changes, fixes, removals, and any required upgrade
notes. Do not ship a release with an empty, placeholder, or retrospective
changelog entry. Confirm that the changelog version matches the app's
`versionName` and that the release validation suite passes before declaring the
app ready to release.

## Product philosophy: intentional simplicity

Design and develop the application with the discipline commonly associated with Apple’s product ethos: every element must earn its place, the primary experience must feel coherent and polished, and internal complexity must not become user-facing complexity.

This does **not** mean copying Apple’s visual style. It means applying the following product principles.

### Agent responsibility and permission boundary

Apply this philosophy to product, design, architecture, implementation, review, testing, and documentation decisions.

While working, proactively notice code, interfaces, flows, settings, terminology, and architecture that conflict with these principles. When a meaningful improvement falls outside the user’s current request:

1. Identify the concrete misalignment and its effect on users or maintainability.
2. Propose the smallest coherent refactor, including likely scope, tradeoffs, risks, and verification.
3. Ask for explicit user permission before making the refactor.

Do not silently broaden the task, perform speculative cleanup, or treat this philosophy as standing permission for unrelated changes. Refactors that the user has already requested or explicitly approved may proceed without asking again. Small, directly necessary edits within an approved task should follow the existing task scope.

### 1. Protect the core experience

The application must be immediately understandable and comfortable to use for an average user who has no interest in configuring the product.

The most common task should be:

- Obvious.
- Fast.
- Reliable.
- Difficult to misuse.
- Accomplishable with the fewest reasonable decisions and interactions.
- Supported by carefully chosen defaults.

The core flow must receive the highest level of design, engineering, accessibility, performance, and testing attention. Advanced capabilities must never make the default path slower, more confusing, or visually crowded.

### 2. Every feature must earn its place

Do not add a feature merely because it is technically possible, requested by a small number of users, common in competing products, or inexpensive to implement.

Every proposed feature must have clearly documented value. Before including it, determine:

- Which concrete user problem it solves.
- How frequently that problem is likely to occur.
- Which users benefit from it.
- Whether the problem can instead be solved through better defaults, automation, inference, or improved existing behaviour.
- What cognitive, visual, maintenance, testing, performance, accessibility, and reliability costs it introduces.
- Whether its value justifies those costs.

Features without a clear and defensible benefit should be omitted, deferred, consolidated with an existing capability, or removed.

### 3. Prefer sensible behaviour over configuration

Do not expose a setting simply because the underlying implementation supports multiple values.

For every configurable behaviour, explicitly decide whether it should be:

1. Handled automatically.
2. Determined by a strong default.
3. Adapted contextually without user intervention.
4. Exposed only when relevant to the current task.
5. Placed in an advanced or infrequently visited configuration surface.
6. Exposed as a permanent user-facing control.
7. Not supported at all.

A visible toggle is not neutral. Every toggle asks the user to understand a concept, predict its consequences, and maintain another piece of product state. Expose one only when users have a meaningful, recurring reason to make different choices and the application cannot reliably choose on their behalf.

Do not use settings as a substitute for making a product decision.

### 4. Support advanced scenarios through progressive disclosure

The application may support sophisticated, uncommon, or professional workflows, but those capabilities must build upon the same coherent interface rather than creating separate artificial “basic” and “expert” products.

Advanced functionality should appear:

- At the moment it becomes relevant.
- In the context where its effect is understandable.
- Without competing with the primary action.
- Without requiring ordinary users to learn specialised terminology.
- Without forcing additional decisions into the default workflow.

Use progressive disclosure, contextual actions, expandable detail, remembered preferences, reusable presets, and automation where appropriate. Advanced users should be able to reach deeper control quickly, but ordinary users should not need to see or understand it.

### 5. Complexity belongs in the system, not in the interface

Where practical, absorb complexity through:

- Good defaults.
- Automatic detection.
- Safe inference.
- Constraint-aware recommendations.
- Validation and prevention rather than error recovery.
- Clear prioritisation.
- Graceful fallback behaviour.
- Remembered user intent.
- Context-sensitive presentation.
- Thoughtful handling of edge cases.

Do not transfer implementation complexity to the user through unexplained options, technical terminology, setup steps, or avoidable confirmation dialogs.

Automation must remain predictable. When the system makes a consequential choice, the result should be understandable and reversible where necessary.

### 6. Maintain one coherent product model

Avoid parallel modes, duplicated workflows, overlapping settings, and multiple ways to perform the same task unless each path serves a clearly distinct need.

The interface should communicate a consistent mental model across:

- Navigation.
- Terminology.
- Configuration.
- Primary actions.
- Feedback.
- Errors.
- Advanced controls.
- Cross-device and adaptive layouts.

New functionality should extend this model rather than introducing exceptions users must remember.

### 7. Polish is a functional requirement

Polish is not decorative work performed after implementation. It includes:

- Clear hierarchy.
- Precise spacing and alignment.
- Consistent interaction behaviour.
- Appropriate motion and feedback.
- Fast perceived and actual performance.
- Stable layouts.
- Thoughtful empty, loading, error, offline, and interrupted states.
- Accessible touch targets, semantics, contrast, focus order, and text scaling.
- Preservation of user state and intent.
- Reliable cancellation, retry, undo, and recovery behaviour.
- Concise, natural language that explains outcomes rather than implementation details.

The application should feel deliberate in both successful and unsuccessful scenarios.

### 8. Evaluate additions against the whole product

Do not evaluate a feature only in isolation. Consider how it changes the total experience.

For each proposed addition, document:

- **User value:** What measurable or observable improvement does it provide?
- **Target scenario:** Is it common, occasional, rare, or speculative?
- **Default behaviour:** What should happen without configuration?
- **Discoverability:** How will relevant users find it?
- **Core-flow impact:** Does it add taps, decisions, delay, terminology, or visual noise?
- **Configuration decision:** Is a visible control genuinely necessary?
- **Failure behaviour:** What happens when detection, automation, permissions, data, or connectivity fail?
- **Accessibility impact:** Can all supported users understand and operate it?
- **Technical cost:** What maintenance, state-management, compatibility, performance, and test burden does it create?
- **Removal criteria:** Under what conditions should it be simplified, hidden, or removed?

Reject or redesign additions whose benefits do not clearly outweigh their total product cost.

### 9. Use this decision hierarchy

When solving a product requirement, prefer solutions in this order:

1. Improve the existing behaviour.
2. Choose a better default.
3. Infer the correct behaviour safely from context.
4. Present a contextual action only when relevant.
5. Add an advanced control outside the primary path.
6. Add a permanent setting.
7. Introduce a new workflow or mode.

Moving further down this hierarchy requires increasingly strong justification.

### 10. Required outcome

The final application should feel simple not because it supports few capabilities, but because its capabilities are organised, automated, and revealed with discipline.

An average user should be able to succeed without studying the application or configuring it. An advanced user should be able to reach meaningful control without fighting oversimplification. Neither group should have to navigate complexity created for the other.

When uncertain, protect clarity, reliability, and the primary workflow. Prefer a smaller number of exceptionally well-resolved capabilities over a larger collection of weakly justified options.
