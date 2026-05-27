# Changelog

All notable changes to **Starlit Coffee** are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
- **Play Store feature graphic** asset.
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

[1.1.0]: https://github.com/adsamcik/StarlitCoffee/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/adsamcik/StarlitCoffee/releases/tag/v1.0.0
