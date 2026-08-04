# Brewing platform implementation report

**Reviewed:** 2026-08-04
**Branch:** `main`
**App version:** 1.4.0 (development remains under `Unreleased`)

## Outcome

The brewing-platform architecture and the complete exact-stage illustration
production pass are implemented. The product now has stable brewing identities,
validated equipment and recipe models, versioned persistence, a durable staged
session engine, shared Learn/live guidance policies, exact P1 recipe setup, and
compile-time-bound instructional assets. All exact P1 recipes and illustrations
are enabled for reviewed English guidance. Other app locales remain fail-closed
until their exact-stage copy receives native-language editorial approval.

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

## Localization, accessibility, and release gate

All 23 supported locales contain the same 73 exact-recipe setup resources.
English and Czech setup copy are present, and locale key parity is intact.

The canonical English curriculum remains in the versioned asset for tooling and
source validation. The reviewed runtime copy is also packaged as Android raw
resource `res/raw/p1_exact_guidance.json`; Android resource selection and the
application-scoped loader are locale-aware.

`P1ExactRecipeLocalizationCoverage.production` records English for all 20
recipes. Eligibility now checks the active app locale instead of requiring an
all-or-nothing 23-locale launch. This enables the complete exact setup, Learn,
and live-session experience in reviewed English while keeping unreviewed locales
on existing localized flows. There is no silent English exact-guidance fallback.

All 114 accepted assets now produce approved runtime metadata. Exact images take
their nonblank content description from the same locale-selected stage record
rendered beneath them, eliminating duplicate XML/JSON accessibility copy and
preventing illustration/text drift. Scoped legacy assets retain their existing
compile-time string-resource contract.

A Czech machine-translation pilot was visually and linguistically audited,
rejected, and removed after polysemous coffee terms were mistranslated. No draft
translation or machine memory is shipped. The reproducible draft-generation,
structural validation, per-stage native review, and promotion process is recorded
in `docs/brewing/p1-exact-guidance-localization-workflow.md`.

## Verification

Passed locally:

- `gradlew :app:testDebugUnitTest --no-daemon --console=plain`
- `gradlew :app:detekt :app:lintDebug :app:assembleDebug --no-daemon --console=plain`
- `gradlew :app:connectedDebugAndroidTest --no-daemon --console=plain`
- `python tools/verify_starlit_tactile_production_tracker.py`
- `python tools/verify_instruction_assets.py`
- `python tools/generate_p1_tracker_accepted_asset_catalog.py --check`
- `python tools/generate_p1_exact_guidance_localizations.py --check --locales en`
- exact-recipe setup resource count and key-parity audit across all 23 locales

The static build emits existing Detekt baseline findings but no failing finding.
The connected suite completed on a cold-started Android 16 `Medium_Phone` AVD:
48 tests were discovered, 36 executed, 12 intentionally skipped, and 0 failed.
The skipped cases are opt-in bag-scan quality and benchmark probes that require
the separately pushed synthetic image corpus, generated OCR fixtures, per-bag
instrumentation arguments, or a running and approved Mindlayer model service.
They are not brewing, Room migration, or Compose release tests.

An earlier complete device run exposed one test interaction defect: the Cezve
setup test used `performScrollTo()` for an uncomposed lazy-list item. The test now
scrolls the `LazyColumn` to the semantics matcher, and both the focused test and
the complete connected suite pass. The activation run also updated the image
ordering assertions to inspect the localized stage alt text actually rendered by
Learn/live Brew. The final complete connected suite passed. No release version
was assigned and no deployment was performed.

## Remaining localized rollout

There is no remaining English implementation or illustration-production work.
Expanding exact P1 guidance to another supported locale requires a native
coffee-domain editorial review of all 114 stages, generation of that locale's raw
resource, and explicit coverage promotion. Each promoted locale must repeat the
unit, lint, Detekt, assembly, instrumentation, accessibility, large-text, and
light/dark checks. The current validators establish structural and numerical
safety but cannot replace native-language editorial approval.
