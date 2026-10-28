# Centered brewing implementation verification

## Result and scope

The selected Centered design replaces the normal UI of BrewSessionScreen, BrewTimerScreen and BloomTimerScreen through shared presentation components. Baseline: b2624181. The retained routes keep their existing clocks, alerts, pack-use/logging callbacks, exit behavior and navigation.

The live surface keeps the bloom, authoritative values, physical controls and necessary safety/recovery or measurement input. Instructions, instructional illustrations, recipe, read-ahead and secondary controls are on demand in Help. Optional guidance does not change the useful live geometry. No source duration, measurement or physical completion is invented.

Durable sessions still own physical stages, clocks, persistence, completion, source/asset gates, background brews and bounded PiP. Pause/Resume operates the physical clock; Next honors authoritative availability, including permitted confirmation while paused. Guidance pause remains a distinct secondary action. Completed screens show elapsed brew time and wait for the persisted log before enabling Done, except explicit timer-only sessions. A known target cannot hide an unknown clock origin. Disabled artwork cannot remove timing information.

No new raster generation, icon edits, data migrations, dependencies, SDK changes, remote publication, release or deployment.

## Verification

- 28 focused JVM tests passed: Hero 8, primary actions 3, guidance projection 14, Help projection 3. These results remain valid after the later presentation-only corrections.
- App and test APKs compiled. Final app + Detekt passed in 6m 38s. The unchanged test APK remained compatible and was used for the final native runs.
- All 27 unique selected native cases have passing results on the existing Android_17_Google_Play API 37 REL AVD. Results are combined across focused runs, not claimed as one clean 27-case batch.
- The core run passed nine unaffected accessibility/action cases and four legacy cases; its Help-target check found 40 dp. Both header controls were made explicitly 48 dp. The final affected header/legacy run passed 5/5 in 39.839s, the full/partial/no-guidance layout matrix passed 6/6 in 34.786s, and the final interaction run passed 7/7 in 67.142s.
- Native coverage includes strict text bounds, normal and 200% layouts, named controls, independent source/reminder clocks, unknown origins with/without targets, non-pour targets, missing artwork, physical Pause/Resume, Next, Help/Recipe without session events, failure recovery, persisted-completion presentation and both real legacy ViewModel routes.
- Stage announcements are separate from ticking-clock descendants. Native semantics assertions protect that separation; this is not a full TalkBack traversal.
- Astra independently reviewed the source, all six adaptive before/after pairs, corrected legacy/header renders, pressed Next, and final basic/paused/completed states. Final verdict: **Approved for commit and local integration. No remaining blockers.**

The final native windows confirmed font 1.0, physical size 1080×2424 and density 420 before/after. Large test screens use a local 320 dp viewport and 200% text; system font settings were not changed. The owner retained the existing 4 GB AVD configuration. Tracker packages/data/settings were untouched, and every window was explicitly released.

The first native attempt was interrupted when the emulator disappeared. It is not counted as successful runtime verification. The owner restarted the same AVD without a reset/wipe; later focused runs supplied the results above. The primary text-bounds and 40 dp header issues were corrected in production code without weakening assertions. The first Detekt filename mismatch was resolved by renaming the shared file, with no suppression or gate change.

Durable UI tests use real immutable recipe/runtime fixtures with dispatch boundaries controlled by the tests; they do not execute database or persisted-service brewing end to end. Legacy tests use actual transient BrewViewModel clocks and leave saved preferences, inventory and database state isolated.

## Evidence and limits

[Before/after gallery](screenshots.md) contains unchanged before images from b2624181 and untouched native after captures. The selected-design references are browser captures of the accepted code-built mockup. Machine-readable results and case IDs are in [verification.json](verification.json).

Build/native logs remain in the preserved task worktree build directory: centered-brew-build.log, centered-brew-final-build.log, centered-brew-button-build.log, centered-brew-header-build.log, and centered-brew-native-{core,header,matrix,interaction}.log. The existing Gradle wrapper/JBR, configured 2 GB JVM, max two workers and no daemon were used. No clean or cache deletion.

Not established: physical-device brewing, a full TalkBack traversal, a separate system-wide font-scale/reduced-motion run, exhaustive localization, or release certification. Still images do not establish motion feel.
