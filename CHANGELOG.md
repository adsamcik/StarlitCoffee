# Changelog

All notable changes to **Starlit Coffee** are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- **Coffee-use tracking without guided brewing** — active bags now offer a quick
  gram-based usage entry that updates remaining weight, contributes to dose
  estimates, appears in the bag's history, and can be undone immediately.

### Changed

- Bag scans now use one five-minute deadline across WorkManager restarts, and
  Mindlayer connection checks stop after five seconds instead of waiting
  indefinitely. When time runs out, the editable fields already found are
  returned rather than leaving analysis running forever.
- Updated the Mindlayer SDK integration to `1.0.0-alpha.7` and now checks the
  required OCR/chat model readiness before starting doomed AI work.
- Saved barcode and QR data now takes precedence over AI guesses, and AI-only
  values no longer appear as high-confidence facts.

### Fixed

- Model setup failures now lead directly to Mindlayer's Models screen, while
  transient busy/resource errors honor the SDK retry hint within a small cap.
- Cancelling an outer scan deadline continues through provider-level timeout
  handling so native Mindlayer inference receives the cancellation promptly.
- Unknown or malformed decaf output stays unset instead of being guessed, and
  Skip AI now opens the latest saved OCR/barcode result without rerunning OCR.

## [1.5.0] — 2026-08-11

### Added

- **Durable staged brewing** — active brews now use an ordered, method-aware
  stage plan that can be restored after the app or process leaves the foreground.
- **Learn and live guidance foundation** — the same brewer-profile content can
  support a standalone learning flow and concise, stage-specific guidance while
  brewing.
- **Background continuation for bounded stages** — timed stage transitions can
  surface a direct-return alert when the relevant brew is not open on screen.
- **Opt-in guidance translation previews** — all supported app languages can
  preview the complete exact-recipe curriculum. Preview copy is clearly marked,
  stays disabled until the user consents, can be turned off from brew setup, and
  retains the canonical English warning beside safety-critical translations.

### Changed

- **Smaller, verifiable release builds** — production packages now use AGP 9.3's
  integrated R8 code and resource optimizer, with automated checks that confirm
  obfuscation and preserve the retrace mapping for diagnostics.
- **Noncommercial project license** — Starlit Coffee is now source-available
  under PolyForm Noncommercial 1.0.0. Commercial use requires a separate
  license from the project owner.
- **Existing brewer curricula** now resolve through the shared profile and
  guidance catalogue rather than falling back to a generic brewer experience.
- **Active-brew Picture-in-Picture** is limited to bounded timed stages and
  presents a compact current-action timer.

### Fixed

- Automatic stage deadlines are reconciled through the persisted session state
  when the brew screen becomes active again, so a completed foreground countdown
  does not wait for a separate recovery path.
- **Readable full-screen dim mode** — brew timers now resolve status colors from
  the active dim theme and paint the dark canvas behind display cutouts and hidden
  system bars, keeping timer guidance legible without shifting controls.

### Release status

- **P1 exact guidance is available in reviewed English.** The other 22 app
  locales package complete, structurally validated preview translations behind
  explicit consent. They are never labelled as reviewed, unresolved glossary
  terms remain hidden, and safety-critical steps retain their English warning.
  Promotion from preview to reviewed guidance still requires the locale-specific
  editorial, evidence, accessibility, theme, and device gates.

## [1.4.0] — 2026-07-25

### Added

- **Brewing companion** — a live brew can now enter a compact,
  phase-and-timer-only Picture-in-Picture window when the app is minimized.
- **Background brew alerts** — an ongoing notification keeps the elapsed timer
  recoverable outside the app, while bloom completion and recipe target time
  arrive as high-visibility alerts that return directly to the brew.
- **Brew vibration themes** — Soft, Classic, and Bold haptic personalities now
  give minute markers, bloom warning/completion, and target-time cues distinct
  meanings. The selected theme is available in Settings and applies to
  background alert channels as well as on-screen haptics.

- **Adaptive large-screen layouts (Android 17 / SDK 37)** — on `sw>=600dp` the
  portrait orientation lock is ignored, so the app now adapts to tablets,
  foldables, landscape, and desktop windows: a side **navigation rail** replaces
  the bottom bar on wide windows; the **calculator** splits into a display +
  settings pane beside the keypad in wide landscape; and the **brew log** becomes
  a list-detail view with the selected entry shown alongside the list on large
  screens. Width is tracked live via a new `WindowWidthClass` so resizing and
  rotation reflow instantly.

### Removed

- **Non-bloom timer animations** — dropped the scene/object spritesheet themes
  that don't depict a flower opening (fantasy citadel, cyber city vines, pixel
  micro-world, tabletop adventure map, alchemy astrolabe, espresso robot, space
  habitat ring, nerd library nook, arcade platformer level, clockwork dragonfly,
  observatory dome, cyber-alchemy engine, tabletop space colony, observatory
  library) plus the cursed-relic medallion and the orphaned punk-theme assets.
  The bloom picker now keeps only true floral, fruiting-plant, and coffee blooms.

## [1.2.0] — 2026-06-07

The **AI coffee-bag scanning** release. The bag-scan path was rebuilt end to
end around the on-device Mindlayer LLM: a guided capture flow, a durable
multi-stage extraction pipeline (OCR → text LLM → cropped vision → combine),
live progress, and a corpus-backed quality harness. Ships alongside a brew-timer
refresh, dim-mode rework, expanded localization, and a broad build/lint/warning
sweep.

### Added — AI bag scanning

- **Guided bag-scan capture** — a single full-screen flow that captures front /
  back photos, lets you "scan more", and starts extraction after the first
  photo, refining as more arrive.
- **Durable on-device extraction** — bag-label analysis runs through a
  WorkManager job so it survives app backgrounding and process death, with an
  ongoing foreground-service progress notification and a "tap to review" result
  notification.
- **Phase-aware progress UI** — the analyzing screen and notification now show a
  determinate, per-stage bar (reading the label → understanding the details →
  inspecting the image → combining → finishing) instead of an opaque spinner.
- **Final combine / name-selection pass** — a text-only reconciliation pass
  consumes only the structured outputs of the prior text and vision passes (plus
  the user's known-value vocabulary) to choose the single best value per field,
  especially proper-noun identity fields like name / roaster.
- **Cropped-label vision pass** — a single multimodal pass over the detected,
  cropped label region recovers identity and concept fields (name, roaster,
  origin, region, variety, process, roast level, tasting notes, decaf) and
  visual-only cues such as a roast-level dot scale or decaf icon.
- **Hierarchical OCR** with region-targeted re-recognition for mashed text,
  **front + back photo fusion**, **"roasted for" (Filter / Espresso / Omni)**
  roast-purpose extraction, multi-page cross-reference, and brand-logo vs
  product-sticker name/roaster disambiguation.
- **Language-blind extraction prompts** — concept fields are translated to
  canonical English while proper nouns stay verbatim, with no per-language
  vocabulary tables.
- **Mindlayer consent flow** surfaced from the add-bag "AI unavailable" card; an
  **Analyzing screen** with Skip-AI and run-in-background; **gallery import**;
  **tap-to-view full-screen** bag photos; and **AI-label-focused thumbnails**.
- **Quality harness** — a committed synthetic coffee-bag corpus, a JVM scoring
  harness, an instrumented LLM benchmark, and a Q0 best-case extraction gate.

### Added — Brewing & UI

- **Cup preset editor** screen and navigation.
- **Bloom spritesheet selector** with new artwork, runtime grid parser
  hardening, and an ambient-occlusion-style halo.
- **Dim mode** reworked into a theme override with a force-dark-in-light
  sub-toggle and a frozen fullscreen layout.
- **Brew-timer hero** swapped to the target total water; redundant Bloom pill
  removed; water-target hero kept readable with the time window moved to the Now
  pill.
- **"Show brewing instructions"** Settings toggle.
- **Rating-reminder notification** and a bag-selection prompt.

### Changed

- **Mindlayer SDK → 1.0.0 (v1 API)** — gallery-photo OCR migrated from ML Kit to
  Mindlayer, the SDK is warmed off the main thread at startup, and AI service
  binding is declared via a `<queries>` package-visibility block (Android 11+).
- **Bag fields are now LLM-only** — the legacy regex field extractor was removed;
  the LLM is the single field source over merged OCR text (text-only enrichment).
- **AI inference timeouts raised to ≥ 5 minutes** to match the Mindlayer 5-minute
  single-inference server cap, so a legitimately long on-device generation is no
  longer aborted client-side.
- **`versionCode`** bumped `2 → 3`; **`versionName`** bumped `1.1.0 → 1.2.0`.

### Fixed

- **Status sentinels** (`not_visible`, `uncertain`, …) no longer leak into the
  "Detected Details" chips when the model places a status token in the value slot.
- **Camera freeze** during AI scanning resolved by keeping inference and bitmap
  marshalling off the main thread.
- The scan no longer **tells users to retake the photo** when the failure was
  that the AI service was simply unavailable.
- **Native LiteRT-LM crash** avoided by prewarming the CPU backend before the
  first inference.
- DataStore recovery, atomic LiveScan state and accumulator-job resilience,
  Mindlayer availability reporting, dark-mode primary-button readability, and EXIF
  rotation on photo previews.

### Engineering

- Broke the `scan ↔ data.network.llm` package cycle; extracted
  `BagFieldContextMapper`, `BrewDerivation`, and `BrewTimerController`; made
  `logBrew` atomic via a `TransactionRunner`.
- Added a `MigrationTestHelper` safety net and fixed schema drift; instrumented
  benchmark + corpus tooling.
- Cleared Android Lint errors/warnings, swept Kotlin compiler warnings, pruned
  dead code and unused resources, and adopted AndroidX KTX extensions.

### Localization

- **9 locale translations** added with per-app language configuration.

---

## [1.1.0] — 2026-05-27

First post-launch release. Focused on the Android 16 / SDK 37 platform jump,
cleanup of experimental work, and a clean build pipeline.

### Added

- **Android 16 (SDK 37) support** — `compileSdk` and `targetSdk` raised to 37,
  predictive back gesture opted in, and tablet/foldable configuration changes
  declared so the activity handles screen-size, smallest-width, density, and
  layout-direction transitions without restarting.

### Changed

- **AGP 9.2.1 + new DSL migration** — moved the build script to AGP's new DSL
  surface (`ApplicationExtension`, `androidComponents.sdkComponents.adb`),
  silencing all `android { … }` DSL deprecation warnings ahead of AGP 10.
- **`versionCode`** bumped `1 → 2`; **`versionName`** bumped `1.0.0 → 1.1.0`.
- **Kotlin compiler warning sweep** — repository now builds with zero `w:`
  lines across `compileDebugKotlin`, `compileReleaseKotlin`,
  `compileDebugUnitTestKotlin`, and `kspReleaseKotlin`. Notable migrations:
  - Opted in to the future `param + property` annotation default
    (KT-73255) via `-Xannotation-default-target=param-property`.
  - `rememberModalBottomSheetState(skipPartiallyExpanded = true)` →
    `rememberBottomSheetState(initialValue = Hidden, enabledValues = …)` in
    five sheets / screens.
  - `Locale("xx")` / `Locale("", code)` → `Locale.forLanguageTag(…)` /
    `Locale.Builder().setRegion(…)` in coffee dictionary & metadata
    normalizer plus their tests.
  - `Context.VIBRATOR_SERVICE` → `getSystemService(Vibrator::class.java)`.
  - `Window.statusBarColor` removed; relies on `enableEdgeToEdge()` for
    transparent system bars.
  - `CancellableContinuation.resume(value, onCancellation)` → single-arg
    `kotlin.coroutines.resume`.

### Removed

- **Experimental brew audio pipeline** — the spectral / probe / flight-recorder
  audio detection stack is gone. Brew progression is now driven entirely by
  the timer + user input, simplifying the codebase and removing the
  microphone runtime permission from the on-device experience.

### Clarified

- The **"No grinder"** option in setup now explicitly covers grinders that
  are not in the picker list, so users with off-list gear don't feel
  forced to skip the question.

---

## [1.0.0] — 2026-05-26

Initial public release. Everything below is the cumulative work that
shipped in version `1.0.0` (`versionCode = 1`).

### Highlights

- A Pulsar-first multi-method brew companion: dose / water calculator,
  guided timer with bloom and pours, brew log with ratings, on-device
  coffee-bag inventory with OCR + barcode scanning, and an experimental
  on-device LLM extraction path.

### Added — Brewing

- **Multi-method brew flow** with method-specific math, stages, and
  guidance for Pulsar (paper, 19K, 40K filters), V60, Aeropress, French
  press, espresso, moka, and cold brew (passive duration).
- **Calculator screen** — dose / water / ratio numpad with locked ratio
  presets, strength offsets (`LIGHT`, `BALANCED`, `STRONG`), and ratio
  guardrail warnings that propagate into the brew flow.
- **Guided brew timer** with bloom + pour phases, valve-state cues,
  next-action surface, target time / temp / valve hints, drift feedback,
  pause / resume, and a Finish action.
- **Bloom timer animation** — 17 punk-themed spritesheets with fair
  rotation across blooms; spritesheet locked per brew.
- **Smart context cards** below "Start Brewing": post-brew check-in,
  Bag Age Wisdom, Use Me First (freshness triage), One Twist
  (single-variable experiment suggestions).
- **House Favorites** — save and load recipes from the home screen.
- **Share a Cup** — share brew details from the log detail screen.
- **Screensaver / dim mode** — `DimImportant` overlay system that dims
  the timer UI when running idle; user-toggleable preference, default on.
- **Onboarding flow** with method, filter, grinder, and cup setup.
- **Cup presets** — full-color vessel icons, reachable color and icon
  picker, default colors restored on reset.
- **Per-bag grind settings** with grind preview, bag auto-rotation,
  and decaf-aware coarsening rules.
- **Grinder dial recommendations** for Pulsar across Comandante C40,
  Encore ESP, DF64, Ode Gen 2, Niche Zero, and ZP6 Special, grounded in
  community and vendor sources.

### Added — Coffee tracking

- **Coffee bag inventory** with manual entry, weight tracking, freshness
  / expiry surfacing, and per-bag brew history.
- **Bag scanning pipeline**: guided camera flow, ML Kit barcode
  scanning, Open Food Facts barcode lookup, OpenCV preprocessing, dual
  text-detection-based alignment, regex-driven OCR field extraction
  with Unicode-aware boundaries and Danish + Czech language hints.
- **Live continuous scanning** with frame evidence accumulator, OCR
  consensus engine, additive confidence, rescan-with-delta-detection,
  visible LLM status indicator, golden-frame JPEG capture, and
  Snap & Approve confirmation UI.
- **On-device LLM extraction** via Mindlayer SDK (LiteRT-LM backend) —
  14-field OCR-guarded extraction, structured output, SHA-256-keyed
  caching, source-attributed context, retry / gating around inference,
  and a settings-side connection tester.
- **Brew rating sheet** with half-star ratings, brew log persistence,
  and a rating reminder.
- **Decaf support** — bag field, decaf-aware grind rule, decaf process
  capture, and decaf-aware timer suggestions.

### Added — UI & platform

- **Material 3 Expressive theme**, hero `displayLarge` timer
  typography, shape tokens, spring-physics navigation transitions, and
  expressive motion on sheets, the log, and the bag list.
- **`LoadingIndicator`** in place of indeterminate spinners.
- **Liquid Pill** brew visualization (replacing the older brewer
  diagram) with steel valve, blue glass, and refined depth.
- **Full Czech (cs) localization** alongside English; ~200 string
  resources, unified bag terminology, "bloom" loanword used in Czech,
  CS grammar polish.
- **GitHub Pages landing site** under `docs/`.
- **Bilingual privacy policy** (later consolidated to English only).
- **Play Asset Delivery** wiring for AI model distribution.

### Added — Engineering

- **Room persistence** with entities, DAOs, and forward migrations
  through schema v15; non-destructive migration policy plus a
  `v14 → v15` migration test.
- **Detekt** + **JaCoCo** static analysis and coverage.
- **Bilingual translation conventions** documented for Copilots.
- **Auto Backup** restricted to exclude bag photos and scan telemetry.
- **Modular nav stack** with lambda callbacks in place of
  `NavController` parameter passing.

### Fixed

- Bloom timer resumes after pause; bloom alert guard; bloom auto-advance
  on the noisy path disabled.
- Brew log feedback loss; favorite spelling; orientation lock during
  brewing; screen kept on; session state reset on a new brew.
- Filter / grinder selections preserved across back navigation; method
  selections preserved on back; method overrides reset when switching
  method.
- Calculator ratio syncs into the brew flow; calculator keyboard pinned
  to the bottom of the screen and given looser spacing on small phones.
- Slider max bounded by brewer capacity instead of brew size.
- ToneGenerator leak, FAB dismiss, color parsing crash, and four
  other logic bugs surfaced by audit.
- Camera freeze during AI scanning; threading cleanup.
- EXIF rotation applied to all photo previews.
- Accessibility: 48 dp touch targets on all `IconButton`s, content
  descriptions, heading semantics, flexible labels, drift feedback.
- Bottom-sheet dismissal blocked by `confirmValueChange`.
- Stale brew state on back navigation guarded with `BackHandler`.
- Transient UI state preserved across config changes via
  `rememberSaveable`.
- Quick brew toggle now actually skips `GrindPrep`.
- Bag selection visible end-to-end; warnings surface before bloom.
- Manifest: corrected Mindlayer service permission name; added
  missing `BIND_ML_SERVICE` permission.

### Removed (pre-1.0 cleanup)

- Legacy phase system, dead `TasteFeedback` / `Result` routes,
  dead QR link explorer card, duplicate Cup picker entry, dead
  legacy escalation config, and unused `AmountStrengthScreen` preview.
- Background timer & rating-reminder services in favour of
  in-process state and `WorkManager` where appropriate.

[Unreleased]: https://github.com/adsamcik/StarlitCoffee/compare/v1.5.0...HEAD
[1.5.0]: https://github.com/adsamcik/StarlitCoffee/compare/v1.4.0...v1.5.0
[1.4.0]: https://github.com/adsamcik/StarlitCoffee/compare/v1.2.0...v1.4.0
[1.2.0]: https://github.com/adsamcik/StarlitCoffee/compare/v1.1.0...v1.2.0
[1.1.0]: https://github.com/adsamcik/StarlitCoffee/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/adsamcik/StarlitCoffee/releases/tag/v1.0.0
