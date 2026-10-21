# Focused visual-first concepts

Two image-generator mockups, 2026-10-09, following the selected minimal theme and the project-wide visual-first principle. The Android implementation below uses the existing native bloom atlas rather than generated mockup artwork.

## Android implementation, 2026-10-21

The live brewing screen now shares this compact clock, quantities and bloom composition. Guided steps prioritize the current target and required action; Help opens the full source guidance, Recipe supports read-ahead, and More holds secondary controls. Reading does not advance or pause the brew. Source countdowns and custom reminders remain separately labelled, and concurrent brews stay accessible in a compact footer.

The Pulsar water-height schematic is limited to the reviewed instruction that actually specifies that target. Timing, confirmation, physical pause, completion and source safety rules continue to come from the existing session model. At large text sizes the bloom becomes smaller beside the clock when width permits; very narrow layouts stack. Long-duration clocks retain explicit units; unknown starts stay labelled.

The follow-up refinement keeps the coffee dose, current target, a brief action cue and relevant visual in the live view. Full recipe quantities, supporting instructions, completion prose and explanations are in Help. Known Pulsar warnings have concise source-specific live wording, while Help retains their complete text; unfamiliar warnings and structured stage safety remain visible verbatim.

The composition pass restores a shared clock-and-bloom band, an inline target and a side-labelled water-height cue. It uses the app's Material theme colours and existing bloom atlases. Clock sizing measures actual glyphs at the active font scale, and the full atlas frame is reserved to prevent overlap. The primary action has a filled, compact rounded shape; routine safety stays concise, while critical messages retain their emphasis. Full source safety text and accessible severity remain available.

Initial implementation verification (`b44781a2`): 1,720 JVM tests passed (four skipped), and 15 targeted native checks passed on the existing stable Android 17 / API 37 AVD using an isolated app package. Rendered light/dark and 320 dp / 200% text checks cover clocks, the guided target and schematic, source safety, 48 dp Help, background-session selection, Help/Recipe navigation, explicit completion and rejected-action recovery. TalkBack speech and physical-device behavior were not exercised.

The initial implementation also passed Detekt, debug lint, debug APK packaging and Android-test APK packaging. Independent review found no remaining blockers.

A real app smoke check of that implementation also covered onboarding, preparation, the advancing bloom clock and transition to Pour, reopening the same running session from its background control, and the final “Brew saved” state.

Refinement verification: 13 focused JVM tests and 15 targeted native checks passed on the same existing stable API 37 AVD with an isolated app package. A separate native check at actual Android 200% text and 320 dp verified the live target, bloom, pinned completion action, scrolling Help, Recipe and return to the unchanged brew. Full source instructions, quantities, safety and explanations remain available in Help. Native light/dark screenshots were inspected; large text scrolls for lower content. Emulator display and font settings were restored. TalkBack speech and physical-device behavior remain unverified.

The refinement passed Detekt and debug app/test APK packaging. Debug lint completed with only three dependency-update errors in the unchanged version catalog: Material 3 1.5.0-beta01 → 1.5.0-beta02, DataStore 1.2.1 → 1.3.0, and kotlinx serialization 1.11.0 → 1.12.0. There were no UI findings; dependencies and lint policy were left unchanged. Independent code and rendered review found no blockers.

Composition-pass verification (2026-10-22): Detekt, 13 focused JVM tests and both debug APK builds passed. Fifteen targeted native checks passed across focused runs, including nine affected checks rerun after the final corrections. The long-clock fixture exercises wider bloom artwork, source countdown and custom reminder at 200% text, with text-fit and separation checks. An additional native check at actual Android 200% text and 320 dp passed Help/Recipe navigation and explicit completion. Light/dark renders and the large-text Help were inspected; lower content scrolls with the primary action pinned. Independent source and rendered review found no blockers. The emulator's density/font settings were restored, and app data was preserved. One startup was terminated by Android for low memory before test discovery; the unchanged APKs passed on retry. TalkBack speech and physical-device behavior remain unverified.

Final debug lint completed with four dependency-update errors, all in the unchanged catalog: the three above plus Mindlayer SDK 1.0.0-alpha.9 → 1.0.0-alpha.10. No UI findings were reported; dependencies and lint policy remain unchanged by this pass.

## Basic timer and concurrent brew

![Basic timer](01-basic-timer.png)

Clock, coffee dose, cumulative water target and one conditional reference cue. Finish brew affects the foreground session; the cold-brew row identifies a separate reminder.

## Guided pour

![Guided pour](02-guided-pour.png)

Current cumulative target with one schematic water-height cue. The bracket measures above the coffee bed; Target identifies an instruction rather than a sensor reading. The report action remains separate from basic Finish brew.

The final bloom-animation pose remains in both views. Generated artwork approximates the existing atlas, which stays authoritative. Astra reviewed both images and found no blocking issues. Static review cannot validate comprehension, device layout, accessibility, interactions, animation or reminder delivery.

Exact built-in generator inputs are in [prompts.json](prompts.json); original output paths and hashes are in [manifest.json](manifest.json).
