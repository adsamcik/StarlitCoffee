# Brewing flow design directions

Created 2026-10-08 using the built-in image generator. These are visual proposals for selection, not approved product changes or validated brewing instructions.

Each board compares a short brew ready to start bloom with a cold brew already steeping. The intent is to compare information structure as well as visual style.

| Direction | Central idea | Trade-off |
| --- | --- | --- |
| 01 Focus | One current instruction and quantity, compact recipe facts, one primary control. Closest to the older timer. | Upcoming stages need a disclosure. |
| 02 Brew timeline | Current and upcoming stages share a continuous timeline. | More information remains visible during brewing. |
| 03 Recipe workspace | Keep the recipe available and anchor the timer and action below. | Less emphasis on the current quantity from a distance. |
| 04 Visual ritual | Use instructional illustrations to explain physical actions. | More artwork to maintain and less room for large text. |
| 05 Instrument panel | Large labelled quantities and timer in a precise grid. | More technical appearance and more panel boundaries. |

Recommendation: explore Focus first for daily use, with Brew timeline as the alternative when read-ahead is important. These are alternatives, not five new application modes.

## Review notes

All five generated boards were visually inspected: two complete screens per board, clear distinction between bloom and total water, one start-bloom control, labelled long-duration timing, visible reminder-access action, and a concise storage cue. No application code was changed or native interaction tested.

Generated content is illustrative. The generator added some secondary copy, recipe values and decoration beyond the requested layout; those additions are not approved behavior or recipe changes. In particular, the cold-brew recipe values in board 03 and bloom timing in board 04 are examples, not validated instructions. Reminder-access copy must preserve the real distinction between approximate and precise reminders. Artwork in board 04 is conceptual and must be checked against actual equipment before use. Critical hazards must remain prominent when relevant; the compact storage row here depicts routine guidance.

## Artifacts

- [01 Focus](01-focus.png)
- [02 Brew timeline](02-timeline.png)
- [03 Recipe workspace](03-workspace.png)
- [04 Visual ritual](04-ritual.png)
- [05 Instrument panel](05-instrument.png)
- [Exact generator prompts](prompts.json)

Original generator files are preserved under the Codex generated-images directory. The PNGs here are unchanged copies.
