# Timer and guided brewing scenario pairs

Created 2026-10-08 with the built-in image generator. Ten final boards: two per direction. Each board contains two screen views to show the scenario clearly. These are proposals for comparison and selection, not approved application changes.

The timer board shows a focused simple coffee stopwatch and an overview of that same stopwatch alongside an independently running cold brew. The guided board shows preparation and active bloom while a concurrent cold brew remains accessible. Learning support extends the brewing interface; it must not replace basic timing or cancel another brew.

| Direction | Basic and concurrent timers | Learning while brewing |
| --- | --- | --- |
| Focus | [Timer board](01-focus-timers.png) | [Guided board](01-focus-guided.png) |
| Brew timeline | [Timer board](02-timeline-timers.png) | [Guided board](02-timeline-guided.png) |
| Recipe workspace | [Timer board](03-workspace-timers.png) | [Guided board](03-workspace-guided.png) |
| Visual ritual | [Timer board](04-ritual-timers.png) | [Guided board](04-ritual-guided.png) |
| Instrument panel | [Timer board](05-instrument-timers.png) | [Guided board](05-instrument-guided.png) |

Focus offers the quietest everyday timer. Timeline gives the clearest stage orientation. Workspace keeps the recipe available. Visual ritual emphasizes instructional artwork. Instrument panel emphasizes labelled numbers and control grouping. These are alternative design directions, not five application modes.

## Scope and review

Requested scope: simple timers, concurrent timers for long running brews such as cold brew, and guided flows that help someone learn a new method while brewing. The ten boards depict these capabilities without changing application code.

Basic timer controls are scoped to the short stopwatch. Cold brew has an independent clock and reminder; there is no global end-all control or suggestion that pausing the stopwatch pauses physical steeping. The guided elapsed clock, step countdown, and background cold brew are separately labelled. Advancing guidance must not cancel or restart any other session.

Generated boards are layout explorations. Values and brewing copy are illustrative, and method-specific illustrations need correction against the actual brewer before use. The cone-shaped equipment in several boards is not an approved Pulsar illustration. Next-step enablement and early advancement must follow the selected recipe's actual constraints. Stopwatch pause is distinct from pausing physical brewing; final control wording must reflect existing clock semantics. Decorative timer arcs in boards 02 and 03 must not imply an undisclosed target or validated recipe deadline. Added bottom navigation in board 01, duplicate Recipe access, additional timings, and scientific explanations are generator additions, not approved product scope or validated teaching content.

The first Visual ritual timer attempt mistakenly included instructional valve artwork. It was corrected through the image generator so the final timer board contains clocks and controls with small decorative bean artwork. The first attempt is preserved in the generator output directory, and both prompts are recorded.

Independent review found no blockers for delivering the ten concept boards. Its concrete artwork finding was corrected: the active Visual ritual guide now depicts resting grounds without a pouring kettle. Future implementation refinements remain documented above. Both targeted artwork correction prompts are preserved.

## Provenance and verification

- [Exact generation and correction prompts](prompts.json)
- [Source filenames and final board mappings](manifest.json)
- Final boards were visually inspected for scenario coverage, concurrent clock access, labels, and complete controls.
- Saved image bytes were compared to source SHA-256 hashes, image dimensions checked, and prompt/manifest counts checked.
- Visual proposals do not establish native interaction, accessibility, background timer reliability, or notification behavior.

Original generator images and previous exploration boards are preserved.
