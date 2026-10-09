# Brewing process and display proposal

Research and design exploration, 2026-10-08. This is a proposal for discussion and mockup selection, not authorization to implement new behavior. No app code, source recipes, timing contracts, persisted quantities, safety rules, or animation assets are changed.

The user's requirements are five cleaner directions, two scenario images each, minimal presentation, basic and concurrent timers, learning while brewing, retained bloom-animation identity, useful dose/water/timing facts, information relevant to the current action, and tolerance of a somewhat slower person.

Subsequent selection, 2026-10-09: the user chose the left-aligned Reading order image and requested an anchored Finish brew control, a redesigned background-timer row, an adversarial Astra review, and all meaningful visual states of that theme. The selected-theme gallery supersedes continued refinement of the competing directions.

## Evidence and implications

- [Jonathan Gagné's original Pulsar process](https://coffeeadastra.com/2023/09/13/the-pulsar-dripper/) combines a timer with physical cues, scale targets, a valve, and slurry-level control. This supports a display that distinguishes elapsed cues from tasks actually completed. The same recipe's detailed copy already exists in app/src/main/res/raw/pulsar_learn_guidance.json.
- [Firsthand discussion of late pours and timer origin](https://www.reddit.com/r/pourover/comments/1azks3a/) includes a person pausing an app while their scale continues counting. That is evidence of confusion about which clock is being paused, not evidence that extraction stopped. The small, self-selected discussion is qualitative input, not a usability study.
- [Reviews of a guided brewing timer](https://play.google.com/store/apps/details?id=com.pareto.coffee) include a report that steps feel rushed. This supports avoiding unnecessary transitions and retaining the current task while the person's hands are occupied. These reports do not establish the frequency of the problem.
- [AeroPress's own instructions](https://aeropress.com/pages/how-to-use) separate preparation, water addition, a timed wait, and gentle pressing. A deadline should not encourage someone to press harder or hurry a physical operation.
- [OXO's cold-brew instructions](https://www.oxo.com/media/wysiwyg/PDF_Files/1403_gg_1272880%20Cold%20Brew%20Coffee%20Maker%20instructions.pdf) distinguish steeping from subsequent filtration. Reaching a reminder does not prove that someone strained the brew.

The following interface rules are design inferences from that evidence and the existing app contracts, not claims made by those sources or verified runtime behavior.

## What the app can know

The clock knows elapsed time. A recipe knows its intended quantities and reference times. A user can report an actual target or observation. Without a connected scale or an explicit report, the app does not know water already poured, remaining grams, whether a valve moved, or whether the coffee drained.

A reference time is not automatically a completion trigger. StagePlan.kt already separates reference targets, completion modes and source-defined advance constraints. A new presentation must preserve this distinction rather than applying one universal transition policy.

The brew clock follows the recipe's physical origin. For the illustrated Pulsar recipe, that is the start of the first water pour, not an earlier preparation screen. Reading help, switching views or pausing guidance does not freeze physical extraction. An unrelated free stopwatch may retain real pause controls; those controls should not be borrowed ambiguously for an ongoing physical brew.

## Information by phase

| Physical situation | Dominant information | Small useful context | Progress or completion |
| --- | --- | --- | --- |
| Preparing | Current setup action; coffee dose when measuring it | Total water, grind and temperature targets when useful for preparation; initial animation pose | Untimed. Start the brew clock with first water, after preparation. |
| Pouring bloom | Cumulative bloom-water target; valve cue needed during this particular recipe | Coffee dose, total recipe water, actual elapsed time | Keep the target until the person reports reaching it; don't infer grams from time. |
| Bloom resting | Time to the recipe's relevant wait boundary, then a calm ready state | Valve state, current water target, one preview of the next action | An elapsed-time cue can become ready; it must not silently complete unfinished pouring or valve operations. |
| Main pour or pulses | Cumulative scale target, plus the current technique cue in guidance | Dose, total recipe water, actual elapsed time; added amount clearly scoped to this phase | One meaningful report at the required target, not a tap for every pulse or micro-action. |
| Drawdown | Let water drain; the observable end cue | True total elapsed time; recipe time band as a secondary reference | Finish by the recipe's observation/report, not simply when the expected band ends. |
| Cold-brew steep | Reminder time or time remaining | Its own elapsed clock and pertinent storage guidance | Other short brews can proceed. Reminder expiry means ready for the next physical task, not strained or safely stored. |
| Straining or serving | Current physical action and its relevant handling cue | Actual brew time and useful serving quantities | User confirms completion; no needless countdown for an unknown physical duration. |

During an active brew, the compact dose and total-water context remains easy to find without dominating the action. Temperature is visible when someone needs to heat or add water, rather than becoming a giant permanent tile. Current and immediately upcoming amounts remain visible; the full planned schedule and past stages are available through Recipe. An observation-based recipe displays its observation, not an invented start timestamp.

The existing selected bloom animation remains present before and during bloom, with its final pose retained afterward in both timer-focused and guided presentations. It expresses the established bloom identity and progression, not connected-scale measurements or proof that every physical operation finished. Methods and plain timers without bloom do not receive it.

## Slower-person walkthroughs

| Situation | Proposed behavior | Behavior to avoid |
| --- | --- | --- |
| Preparation takes longer | Stay ready with setup instructions; no physical brew clock starts early. | Counting down while someone is still rinsing a filter or heating water. |
| Bloom pour is unfinished at a reference time | Preserve the water target and actual elapsed time; let the person finish the current physical action. Evaluate the real recipe's next cue after their report. | Changing the target, displaying a completed pour, or demanding several catch-up pours. |
| A wait is relative to the actual prior action | Use a reliably known physical-action origin when the source recipe specifies this clock basis. A late acknowledgement alone does not establish when the physical action happened; keep that uncertainty visible. | Counting from when an instruction appeared or silently treating a delayed acknowledgement as the physical action time. |
| A cue is relative to first water | Preserve the original brew-clock reference. An already-passed cue is ready after prerequisite actions, subject to source constraints. | Adding a fresh full wait or shifting the entire schedule automatically for every late tap. |
| Main pulse pouring is slower | Hold the cumulative target and water-level cue. Record the actual duration. | Advancing to drawdown because a nominal pour window expired or urging faster pouring. |
| A pour overshoots | Keep the final cumulative target clear. If actual added water is reported, an added-amount helper may use that report; otherwise label it as the recipe reference or omit it. | Pretending planned grams were measured or blindly adding the planned increment again. |
| Drawdown exceeds its usual band | Remain on the observable draining task. Show actual time without declaring completion. | Ending at the recipe's upper time reference while water remains. |
| Help is opened late or the phone sleeps | Resume the same physical clock and known task; maintain the other sessions. | Restarting the clock, replaying already completed actions, or interpreting silence as task completion. |
| Cold brew becomes ready during a short brew | Give a concise, distinguishable notification; retain each session and its state. | Stealing the foreground task, ending all clocks, or claiming filtration happened. |

This tolerance does not make timing irrelevant, promise an unchanged cup, suppress a genuine hazard, or erase a source-defined boundary. It keeps timing honest and gives the person a usable next action. A major delay may require recipe-specific recovery; there is no universal safe or taste-equivalent catch-up rule. These recovery policies need approval and method-specific checks before implementation.

## Basic versus guided use

Basic timing does not force task acknowledgements. It provides a clock, available recipe quantities, conditional reference cues such as After bloom, and access to other timers. It does not label physical progress as confirmed solely because time passed. A plain timer with no recipe should not require recipe setup.

For the chosen Pulsar example, the conditional cue includes valve-open and pulse-pour technique. Cumulative target is primary. An added-amount helper is optional recipe context and must not imply that a planned bloom mass was measured. A new standalone stopwatch with true pause is not part of this gallery; source-backed basic brewing and cold timer-only paths are covered instead.

Guided use adds the current operation, a brief completion cue, optional explanatory help, and only the meaningful confirmation needed when the app cannot know completion. In the final guided mockups, the bloom was already reported complete and the person is pulse-pouring at 2:10. The 340 g target stays in place until the person reports that their scale reaches it. A source-observed drawdown would follow; it is not represented as complete.

Cold brew remains independently reachable as a quiet background row in both scenarios. Opening Recipe or Help, switching foreground sessions, and finishing a short brew must not end the long brew. Storage and relevant safety handling must remain available and prominent when their source severity requires it; minimalist styling is not permission to remove safety content.

## What still requires a concrete decision before implementation

- Confirm the target/observation reporting wording and action grouping against the exact source plan; never silently record a target as a measured actual merely because Next was pressed.
- Confirm method-specific time origins, relative waits, constraints, alerts and handling of early or late confirmations.
- Confirm the meaning of Pause, Stop and Finish for standalone timers, guided presentation and actual brew tracking. They must not imply that the liquid stopped extracting.
- Check text scaling and one-handed use with a realistic beginner, including reaching help with occupied hands and retaining a slow pour. Static images cannot prove this.

The smallest useful implementation, if selected, should reuse the existing session owner, clocks, stage triggers, cue definitions and reader. These mockups and walkthroughs are not a reason to add a new workflow engine, sensors, settings hierarchy or speculative recovery system.
