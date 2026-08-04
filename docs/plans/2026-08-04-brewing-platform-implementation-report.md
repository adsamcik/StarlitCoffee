# Brewing platform implementation report

**Reviewed:** 2026-08-04
**Branch:** `main`
**App version:** 1.4.0 (development remains under `Unreleased`)

## Outcome

The brewing-platform architecture and the complete exact-stage illustration
production pass are implemented. The product now has stable brewing identities,
validated equipment and recipe models, versioned persistence, a durable staged
session engine, shared Learn/live guidance policies, exact P1 recipe setup, and
compile-time-bound instructional assets. Incomplete P1 profiles remain hidden by
the exact-recipe release gate.

The illustration inventory is complete:

- 20 exact P1 recipes.
- 114 ordered stages.
- 114 accepted static WebP assets; 0 open tracker items.
- Every production asset is 1024 x 768, transparent, text-free, and below the
  300 KB target; the largest is 157,404 bytes.
- Full-size, mobile-size, light-theme, dark-theme, physical-accuracy, safety,
  and unnecessary-detail reviews are recorded with prompt provenance and every
  rejected iteration.

## Architecture and product review

The implementation follows the model recorded in ADRs 0001–0003:

`family -> profile -> equipment -> exact recipe -> ordered stage plan -> guidance policy -> durable session -> immutable log`

Notable safeguards include stable string IDs, unknown-safe snapshot mapping,
explicit quantity roles, bounded stage plans, injected clocks, a pure reducer,
persist-before-effect coordination, idempotent finalization, safety content that
bypasses guidance density, and no generic recipe or illustration fallback for
exact P1 content. Setup and live guidance use progressive disclosure so ordinary
users keep a short path while experienced users can select denser or leaner
guidance without changing execution.

The final architecture pass also corrected stale repository context that still
described Room 15 and an in-memory timer. The current database is Room 18 and
active sessions persist recipe, compiled-plan, execution-context, and runtime
snapshots.

## Localization and release gate

All 23 supported locales contain the same 73 exact-recipe setup resources.
English and Czech setup copy are present, and locale key parity is intact.

The 114-stage curriculum itself remains canonical English JSON. It supplies
multiple guidance densities and source alt text, but it is not an Android
localization catalogue. There is no authoritative reviewed translation source
for the per-stage instructions, explanations, warnings, completion cues, and
accessible descriptions. Production therefore deliberately keeps:

- `P1ExactRecipeLocalizationCoverage.production` empty; and
- `P1ExactInstructionAssetLocalizations.production` empty.

This is a release gate, not a runtime defect. Marking all locales complete,
copying English into locale folders, or accepting unreviewed machine translation
would contradict the product's safety and accessibility requirements. P1 setup,
Learn, and live-session routes remain unavailable until editorial review provides
the missing resources. Existing released brewing flows are unaffected.

## Verification

Passed locally:

- `gradlew :app:testDebugUnitTest --no-daemon --console=plain`
- `gradlew :app:detekt :app:lintDebug :app:assembleDebug --no-daemon --console=plain`
- `gradlew :app:connectedDebugAndroidTest --no-daemon --console=plain`
- `python tools/verify_starlit_tactile_production_tracker.py`
- `python tools/verify_instruction_assets.py`
- exact-recipe setup resource count and key-parity audit across all 23 locales

The static build emits existing Detekt baseline findings but no failing finding.
The connected suite completed on a cold-started Android 16 `Medium_Phone` AVD:
48 tests were discovered, 36 executed, 12 intentionally skipped, and 0 failed.
The skipped cases are opt-in bag-scan quality and benchmark probes that require
the separately pushed synthetic image corpus, generated OCR fixtures, per-bag
instrumentation arguments, or a running and approved Mindlayer model service.
They are not brewing, Room migration, or Compose release tests.

The first complete device run exposed one test interaction defect: the Cezve
setup test used `performScrollTo()` for an uncomposed lazy-list item. The test now
scrolls the `LazyColumn` to the semantics matcher, and both the focused test and
the complete connected suite pass. No release version was assigned and no
deployment was performed.

## Remaining release requirement

Commission and review the exact-stage curriculum for every supported locale,
with high-quality Czech editorial review, then bind the reviewed string
resources to the 114 asset records and populate recipe-level locale coverage.
After that change, rerun unit, lint, Detekt, debug assembly, instrumentation,
migration, accessibility, large-text, and light/dark checks before enabling any
P1 profile or preparing a versioned changelog entry. The current device and
static validation establish a clean implementation baseline but cannot replace
native-language editorial approval.
