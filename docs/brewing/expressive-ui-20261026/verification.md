# Verification — Expressive brewing UI, 2026-10-26

No-guidance live projection now suppresses both approved step illustrations and source metadata visual cues. Basic Timer/minimal mode and the explicit utilities-only policy retain authoritative targets, physical controls, bloom and required warnings. Help remains an explicit way to request source guidance. No recipes, timing rules, persistence, artwork, icons, dependencies or admission records changed.

The existing MaterialExpressiveTheme and MotionScheme.expressive are reused. The live clock/bloom share one tonal surface; measured tabular numerals and target typography are stronger. Primary buttons use native Expressive shape morphs. Spatial springs handle disclosure expansion and chevron rotation; effects springs handle opacity and reader progress. The ticking clock and physical bloom progression retain their meaning. Motion follows the installed Material library's [spatial/effects scheme](https://m3.material.io/styles/motion).

## Checks

The final repository-wrapper batch passed Detekt, debug APK, Android-test APK and all 25 focused JVM tests: live guidance 14, hero/bloom 8 and primary action 3. New regressions verify both imagery paths disappear in no-guidance mode, including policy-only callers, while independent targets and unfamiliar required safety remain.

On the existing stable API37 emulator (preview SDK0), six adaptive full/partial/missing normal/200% cases passed in 43.05s. The additional 19 affected scenarios passed in 106.766s: Basic/Help/Recipe 6, accessibility 5, reader 3, sparse-content 3 and reviewed-guide 2. All 25 unique native scenarios passed. After correcting popup capture synchronization, the three affected Basic/Help/Recipe cases also passed again in 28.819s. The new motion case verifies intermediate disclosure bounds, reversal during motion and final collapse; the pressed primary capture is canceled and emits no physical confirmation event.

Large live cases use a 320dp viewport with fixture-local 200% font scaling; normal is 411.43dp. The device remains 1080×2424, density 420 and font_scale1.0. Only the existing configured highest stable API37 emulator was used; no AVD was created/cloned, no older API ran and no production app data was reset. Test apps use the isolated .minimalrefine suffix.

The tonal-clock foreground/background pairs in the static light/dark palettes range from 6.08:1 to 13.77:1. Dynamic system palettes were not numerically audited.

## Evidence and limits

The [comparison gallery](screenshots.md) preserves 12 before/after pairs plus the actual pressed state. Baselines reuse the matching verified 4019dcac native renders; all paired dimensions match. Ten idle pairs visibly change; two reader idle captures intentionally match because their changes are interactive. Actual PNG pixels are unmodified.

Popup typography uses the emulator window configuration, so 200% live/reader checks do not establish 200% popup text. No physical-device run, full TalkBack traversal, separate zero-duration-motion run, exhaustive localization/recipe acceptance or release certification is claimed. Standard Compose finite-animation APIs are used without an app override of framework motion scaling.

Popup evidence uses a synchronized whole-window capture because each sheet owns a separate Android window. The first capture retry encountered an unresponsive emulator; restarting the same AVD preserved its data. Android recorded the first post-startup attempt as a LOW_MEMORY kill, and the final settled-device run passed. The corrected Help and Recipe captures show their intended sheets.

Independent review found no remaining requirement, correctness, regression, scope or visual issues. The reviewer inspected all 12 comparison pairs, the pressed primary state and the final three popup captures, alongside the diff and actual check results.

Raw build/JUnit/native logs and capture files remain in the task checkout's ignored build directories. No remote publication or deployment occurred.
