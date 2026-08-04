# Brewing platform expansion implementation plan

**Status:** Implemented behind release gates; localization review remains
**Planning date:** 2026-07-27  
**Implementation review:** 2026-08-04
**Audited branch:** `main`  
**Audited SHA:** `3ca386081447f7365a05457e98eb88b6de2ea4b3`  
**Source brief:** “StarlitCoffee brewing platform expansion”

## 1. Outcome

Evolve brewing from a flat seven-method registry into one coherent offline
platform:

`method family → brewer profile → equipment configuration → recipe → ordered stages → guidance policy → active session → immutable brew log`

The work must preserve the current calculator, coffee-bag selection and
inventory decrement, grinder recommendations, saved recipes, logs, bloom
experience, dim mode, Picture-in-Picture, and background notifications.

The common path remains short:

1. Choose an amount.
2. Choose a familiar brewer only when necessary.
3. Prepare.
4. Brew.
5. Finish and log.

Equipment details, recipe editing, guidance controls, and utilities are
progressively disclosed. Incomplete profiles stay unavailable rather than
appearing with generic or misleading behavior.

### Implementation outcome

The stable brewing taxonomy, exact equipment and recipe selection, versioned
snapshots, Room 18 active-session persistence, pure session reducer, shared
Learn/live guidance, four guidance levels, P1 stage plans, and release gates are
implemented. The complete P1 visual inventory contains 114 reviewed, transparent
1024 x 768 WebP assets, with prompt provenance and accepted/rejected review
history retained in `docs/brewing`.

The P1 selection, Learn, and live-session routes intentionally remain hidden.
Recipe setup copy is present in all 23 locales, but the 114-stage curriculum is
still canonical English JSON. Reviewed per-stage instruction and alt-text
resources do not yet exist for every supported locale, so the exact-recipe gate
continues to fail closed instead of treating English fallback or machine
translation as release-ready safety guidance. See the dated implementation
report for the completed validation and the remaining external editorial gate.

## 2. Verified baseline

The attached brief is substantially accurate at the audited SHA.

- `BrewMethod` is still a seven-entry enum whose entries own ratios,
  temperature, time, bloom/pulse flags, capacity, absorption, output semantics,
  timing mode, and static guidance.
- `FilterType` is still a flat Paper/19K/40K enum. The UI and state now explicitly
  limit it to Pulsar, but identity is serialized using enum names.
- `BrewCalculator` still performs shared coffee/water, bloom, equal-pulse,
  refill, absorption, cup-volume, time, and ratio-warning calculations.
- Espresso now has explicit `BEVERAGE_YIELD` semantics, but quantity roles are
  still named generically as `waterG`.
- The timer has improved since the older baseline: it has PiP, dim behavior,
  bloom and target alerts, a monotonic in-process clock, and background status
  notifications.
- The timer remains an in-memory `BrewUiState` concern. `advancePhase()` is an
  empty stub, no active-session table exists, and process death loses session
  state.
- Cold brew is marked `PASSIVE_LONG_DURATION`, and timer start remains a no-op.
- Saved recipes and brew logs still contain only flat legacy fields. Loading an
  unknown recipe method currently falls back to Pulsar.
- Room is version 17. Exported schemas begin at version 10, and the existing
  migration test covers 10→17 plus selected individual migrations.
- DataStore persists enabled/default enum methods, a Pulsar filter, one global
  `showBrewingInstructions` boolean, and other display/notification settings.
- Grinder recommendations key on grinder ID, enum method name, and the legacy
  Pulsar filter. They do not model source class, confidence, profile, equipment,
  technique, or verification date.
- The app ships 23 locales. English is the source of truth and every locale must
  retain the same resource keys; Czech requires first-class editorial review.
- There were no open GitHub issues or pull requests at planning time.
- `testDebugUnitTest`, `detekt`, and `assembleDebug` completed successfully in
  one Gradle invocation. Detekt emits existing findings, including brewing
  findings in `BrewTimerScreen` and `BrewDerivation`, but does not fail the
  current build. Instrumented, migration-on-device, lint, and release tasks were
  not run during planning.

The repository changed during the audit and was fetched again before this plan
was finalized. Implementation must repeat the preflight and record a new
starting SHA rather than assuming the SHA above remains current.

## 3. Current-capability audit

| Current capability | Source | Current behavior | Correctness or durability problem | Migration impact | New owner | Required test |
|---|---|---|---|---|---|---|
| Method registry | `data/model/BrewMethod.kt` | Seven enum constants own identity, calculations, capacity, timing, and copy | Locale-independent identity is coupled to enum names; model cannot express profiles or recipe variants | Alias all seven stored names | `BrewingCatalog`, `MethodFamily`, `BrewerProfile` | Stable IDs, aliases, unknown IDs, catalogue validation |
| Filters | `data/model/FilterType.kt`, calculator/settings/onboarding screens | Paper/19K/40K are shown only for Pulsar and remembered in shared state | No physical medium/geometry/size/stack model; `null` is ambiguous; claims are not evidence-tagged | Map legacy values only for legacy Pulsar; retain invalid raw values | `FilterProfile`, `FilterStack`, `CompatibilityValidator` | Stack order, explicit unfiltered, incompatible/unknown filter |
| Recipe inputs | `BrewState.kt`, `BrewUiState` | Dose, generic water, ratio, temperature text, bloom, pulses, grinder, filter | Temperature and technique are not saved; generic water/yield names obscure method semantics | New versioned snapshot with legacy adapter | `BrewRecipe`, `BrewQuantities`, `RatioDefinition` | Round trips and each output/ratio model |
| Calculations | `domain/BrewCalculator.kt`, `viewmodel/BrewDerivation.kt` | Shared math plus espresso yield special case and method-wide decaf adjustment | Generic absorption/refill logic is inaccurate for several families; strings leak from domain; decaf policy is family-incomplete | Legacy results remain available through adapter until each slice migrates | Family calculation policies composed by `RecipeCalculator` | Parameterized output, ice, bypass, capacity, invalid-input tests |
| Grinder guidance | `GrindRecommendation.kt`, `GrinderDataSource.kt`, `assets/grinders.json` | Exact filter match then method fallback; values are shown as equivalent ranges | No profile/config/technique specificity or evidence quality; enum parsing can throw on future values | Alias current method/filter keys and preserve existing ranges | `GrindRecommendationResolver` and versioned evidence records | Exact→profile→family→generic fallback and unknown safety |
| Brew setup | `CalculatorBrewScreen`, `MethodPickerScreen`, `AmountStrengthScreen`, onboarding/settings | Users choose familiar enum methods; Pulsar exposes filter selection | Adding profiles to the enum would create catalogue sprawl; current defaults are enum-name DataStore values | Migrate enabled/default methods to visible profile IDs | `BrewerSelectionPolicy`, setup ViewModel state | Legacy default restore, compatible-only choices, generic profile |
| Preparation | `GrindPrepScreen.kt` | Static method/filter tips with method branches | Guidance content is hard-coded by method and cannot be reused for Learn | No DB migration; content IDs need aliases only if persisted | `StageContentCatalog` rendered by shared stage UI | Content resolution and guidance visibility |
| Short timer | `BrewTimerController.kt`, `BrewTimerScreen.kt` | One elapsed timer, pause/resume, optional bloom countdown, target window | No ordered stages or stage actuals; state is lost on process death; controller state is partly outside `BrewUiState` | Active legacy timer is not currently persisted, so no historical migration is possible | `BrewSessionEngine`, `SessionReducer`, injected clocks | State transitions, pause/resume, late restore, bloom regression |
| Long timer | `BrewTimingMode`, `BrewTimerController.start()` | Cold-brew start is rejected | No persisted deadline, completion work, recovery, or finish/filter state | New active-session rows only | `LongSessionScheduler` plus `ActiveSessionRepository` | Restart, clock change, denied notifications, one completion |
| Background/PiP | `MainActivity.kt`, `BrewSessionNotifier.kt`, `BrewTimerScreen.kt` | PiP and in-process background status/bloom/target notifications | Notification state and duplicate guards are process-memory only; status is not stage-aware | Reuse channels where suitable; persist event IDs | `SessionNotificationCoordinator` driven by session effects | One notification per event, deep-link restore, PiP state |
| Saved recipes | `SavedRecipeEntity.kt`, `RecipeRepository.kt`, `BrewViewModel.loadRecipe()` | Flat method/dose/water/ratio/grind/filter/decaf/notes | Unknown method silently becomes Pulsar; important equipment/recipe choices disappear | Add stable IDs and nullable versioned snapshot; retain all legacy columns | `RecipeRepository` mapping storage DTOs to domain records | Seven legacy mappings, unknown repair, complete round trip |
| Brew logs | `BrewLogEntity.kt`, log screens/share card | Flat recipe values, feedback, total time, bag link | Logs depend on current display interpretation and omit stage/equipment snapshots | Add immutable snapshot and unique session ID; retain legacy presentation | `BrewLogRepository` with immutable `BrewRecordSnapshot` | Immutability, stage actuals, no duplicate log/inventory mutation |
| Preferences | `UserPreferencesRepository.kt` | Enum method sets/default, Pulsar filter, one instruction boolean | Not per-family/profile; unknown values are dropped; no temporary override model | Read legacy keys, write stable-key maps, retain unknown raw entries | `BrewingPreferenceStore` over existing DataStore | Preference migration, per-profile override, never auto-reduce |
| Visual teaching | Bloom spritesheets and static method icons | Decorative bloom visuals; no action illustration manifest or Learn flow | Not instructional coverage and cannot validate stage-to-asset completeness | No user-data migration | `InstructionAssetCatalog`, `LearnMethodScreen` | Manifest/resource/aspect/size/content coverage checks |
| Localization | `res/values*` | 23 aligned locales | Mandatory curriculum introduces a large amount of copy and alt text | Resource-only, but all key sets must remain aligned | `StageContentCatalog` with resource IDs | Key parity, format parity, EN/CS editorial QA |
| Architecture docs | `.github/context/*` | Documents the broad architecture | Some generated context is stale (for example Room version/service description) | None | Updated context and ADRs | Documentation review in release gate |

## 4. Architectural decisions to record before implementation

Create three ADRs and approve their contracts before production code:

1. **Brewing taxonomy and serialization**
   - Stable string IDs, alias resolution, unknown-value retention.
   - Family/profile/equipment/recipe boundaries.
   - Built-in Kotlin catalogue versus user-owned persisted profiles.
2. **Stage/session/timer engine**
   - Validated ordered plans, bounded repetition, completion modes, reducer
     events/effects, clocks, persistence, scheduling, recovery, and idempotency.
3. **Guidance and visual assets**
   - Per-family preference policy, live override, safety independence, shared
     Learn/Brew content, compile-time-safe asset manifest, and release coverage.

The ADRs must reject enum ordinals, localized identity, Kotlin class-name
serialization, runtime drawable-name reflection, and a general-purpose stage
scripting language.

## 5. Target domain contracts

### 5.1 Stable identity and catalogue

Use validated stable IDs such as:

- `manual_gravity`
- `v60_02`
- `pulsar_standard`
- `paper_cone_02`

Kotlin wrappers improve type safety, but persisted DTOs store plain strings:

- `MethodFamilyId`
- `BrewerProfileId`
- `FilterProfileId`
- `AccessoryProfileId`
- `BasketProfileId`
- `RecipeVariantId`
- `StageId`
- `ContentId`
- `InstructionAssetId`

Parsing must preserve unknown raw IDs. Catalogue lookup returns an unavailable
record rather than substituting a default. A centralized alias registry maps
legacy names and documented renames.

Keep the curated built-in catalogue in validated Kotlin structures so resource
references and compatibility stay type-safe. Persist only user-owned custom
profiles and recipe/session/log snapshots.

### 5.2 Equipment

`EquipmentConfiguration` contains:

- Brewer profile and optional size/capacity override.
- Ordered `FilterStack`; explicit `Unfiltered` is a physical choice, while an
  absent selection remains `Unspecified`.
- Accessories, valve/cap, basket, optional papers/screens, and heat-source class.
- Server/cup capacity when it affects overflow safety.
- Unknown/custom records that retain their raw IDs.

`CompatibilityValidator` returns structured blocking errors, critical safety
warnings, and nonblocking advice. It runs when equipment changes, when a recipe
loads, and immediately before starting a session.

### 5.3 Recipe and calculation semantics

`BrewRecipeSnapshotV1` stores the full user intent independently from guidance:

- Stable family/profile/equipment IDs.
- Dry coffee dose.
- Brew/reservoir input, beverage or concentrate target, final served target.
- Ratio definition, temperature, grind, bloom, pours/pulses, agitation, steep,
  orientation, valve sequence, pre-infusion, heat strategy, ice, bypass,
  dilution, serving additions, stage variant, decaf, notes, and schema version.

Use explicit quantity roles and output policies:

- Brew input minus retention.
- Direct beverage yield.
- Collected concentrate.
- Prepared unfiltered volume.
- Reservoir input to estimated machine output.
- User-measured output.
- No meaningful automatic output.

Each `RatioDefinition` names both sides. Calculator results expose named values;
UI labels never infer semantics from a generic `waterG`.

Family policies may share reusable equations, but the selected recipe/profile
chooses the policy. Capacity and refill behavior are profile-specific workflow
results, not a universal division.

### 5.4 Stage plans

Use an immutable, validated plan:

- `BrewStagePlan`: stable plan/version IDs and ordered stages.
- `BrewStage`: action, content, illustration, safety, targets, equipment state,
  completion rule, alert policy, visibility, optional condition, and skippability.
- `StageCompletionMode`: manual, countdown, elapsed range, cumulative amount,
  added amount, beverage yield, observed event, external marker, or immediate.
- Bounded repeats and optional sections are expanded by a `StagePlanCompiler`
  into a deterministic executable sequence. No arbitrary loops or expressions.
- `StagePlanValidator` rejects duplicate IDs, missing content/assets, impossible
  targets, unbounded repeats, incompatible equipment states, and hidden critical
  warnings.

Composables render action/target/content models. They do not switch on every
brewing action.

### 5.5 Active sessions

Make session transitions pure:

- `SessionEvent` represents start, pause, resume, timer tick/reconcile, manual
  advance, observed event, actual-value entry, skip, cancel, finish, and restore.
- `SessionReducer` returns the next `SessionRuntimeState` plus idempotent
  `SessionEffect` values for persistence, alerts, scheduling, and final logging.
- Persist before executing externally visible effects where ordering matters.
- Use stable event/effect IDs so restoration cannot duplicate alerts, logs, or
  coffee-bag inventory decrement.

Timing uses:

- An injected monotonic clock for in-process elapsed precision.
- Persisted wall-clock start/deadline values for process/device restoration.
- Explicit reconciliation when wall time moves backward or forward.
- A lightweight ticker only while the app process is actively presenting a
  short brew.
- Battery-efficient scheduled work for long passive completion. Do not keep a
  foreground service or coroutine alive for a 12–24-hour brew.
- No exact-alarm permission in the first implementation. Revisit only with a
  documented platform-policy and user-value case.

### 5.6 Guidance

`GuidanceLevel` has `FULL`, `CONCISE`, `FOCUSED`, `UTILITIES_ONLY`, and an
advanced `CUSTOM` layout. A centralized `GuidancePolicy` decides which content
and operational modules are visible; the stage plan does not change.

Preference precedence:

1. Current-session override.
2. Remembered brewer-profile override.
3. Remembered method-family preference.
4. Default policy.

Migration/default policy:

- Existing users with `showBrewingInstructions=true` begin at Concise for the
  seven migrated families, preserving the current level of interruption.
- Existing users with it disabled begin at Focused, not Utilities-only.
- A newly encountered family/profile begins at Full.
- Temporary changes are not persisted until “Remember for this method” is used.
- The app may suggest less guidance after a centralized local threshold, but
  never changes it automatically.
- Critical safety and incompatibility content bypasses routine visibility rules.

### 5.7 Shared learning content and visual assets

One `StageContentCatalog` supplies both Learn and live Brew:

- Primary imperative instruction.
- Optional concise explanation, tip, warning, help topic, alt text.
- Guidance visibility and variant conditions.
- Stable illustration asset ID.

Use a Kotlin `InstructionAssetCatalog` with `@DrawableRes` and `@StringRes`
references. Each entry records family, profile, stage/content, 4:3 aspect ratio,
variant, safety status, revision, prompt document, and review state.

Final assets are local, text-free WebP files in `drawable-nodpi`. Validation
checks unique IDs, resource existence, alt text, 4:3 tolerance, dimensions,
encoded-size policy, orphans, and mandatory Full-guidance coverage. Missing or
unreviewed assets keep a profile behind its release gate.

The mandatory curriculum is roughly 120–130 distinct action/state
illustrations. Treat asset production and physical review as a first-class
workstream, not final polish.

## 6. Persistence design

Implement one planned Room migration from the actual implementation-start
version (currently 17) to the next version. Define the complete mandatory
storage shape before incrementing Room so intermediate commits do not create a
chain of avoidable development-only schemas.

### 6.1 Existing tables

Retain every legacy column. Add nullable/indexed stable fields and versioned
snapshots:

**`saved_recipes`**

- `methodFamilyId`
- `brewerProfileId`
- `snapshotVersion`
- `recipeSnapshotJson`

**`brew_logs`**

- `methodFamilyId`
- `brewerProfileId`
- `snapshotVersion`
- `brewSnapshotJson`
- `sourceSessionId` with a unique index (nullable for legacy logs)

New writes require a valid snapshot. Legacy rows may keep a null snapshot and
are mapped conservatively at the repository boundary; loading them must not
invent temperature, size, equipment, or stage history.

### 6.2 New tables

**`active_brew_sessions`**

- Stable session ID and lifecycle status.
- Recipe and compiled stage-plan snapshots with schema versions.
- Current stage ID/index and runtime-state snapshot.
- Planned and actual timestamps, accumulated active time, pause state.
- Long-duration target, scheduled-event token, notification state.
- User-entered actuals, last processed event/effect IDs, optional completed log ID.
- Created/updated timestamps.

**`custom_brewer_profiles`**

- Stable user-owned ID, display name, family ID, schema version, profile JSON,
  created/updated timestamps.
- Built-in profiles never become database rows.

Guidance and utility-layout preferences remain in the existing DataStore unless
query or transactional requirements prove otherwise.

### 6.3 Legacy mapping

Backfill top-level IDs with explicit mappings:

| Legacy method | Family ID | Profile ID |
|---|---|---|
| `PULSAR` | `valve_controlled_no_bypass` | `pulsar_standard` |
| `V60` | `manual_gravity` | `v60_unspecified` |
| `FRENCH_PRESS` | `full_immersion_press` | `french_press_generic` |
| `AEROPRESS` | `chamber_plunger` | `aeropress_standard` |
| `ESPRESSO` | `espresso` | `espresso_pump_generic` |
| `MOKA_POT` | `steam_pressure_multichamber` | `moka_generic_unspecified` |
| `COLD_BREW` | `cold_immersion` | `cold_immersion_generic` |

Map Paper/19K/40K only when the legacy row is Pulsar. Preserve a filter string
attached to another method in the legacy column for audit, but do not treat it
as valid equipment. Null remains unspecified. Unknown method/profile/filter IDs
remain visible and repairable; they never fall back to Pulsar.

### 6.4 DataStore migration

Add stable profile/family keys while continuing to read legacy enum-name keys:

- Enabled visible brewer profile IDs.
- Default brewer profile ID.
- Per-family guidance map.
- Optional per-profile override map.
- Per-family custom utility modules.

Write only the new format after migration. Preserve unknown raw values so a
future app version can recover them. Keep the legacy keys for at least one
release as a downgrade/fallback aid and document downgrade limitations.

## 7. Milestones and commit gates

Every numbered milestone ends with a coherent local commit and a green
milestone-specific validation set. Do not expose a profile before its vertical
slice gate passes.

### Milestone 0 — baseline, evidence, and ADRs

1. Fetch `main`, record SHA and dirty state, and rerun baseline build/tests.
2. Update the audit table if current behavior changed.
3. Write the three ADRs.
4. Create the brewing taxonomy, evidence-ledger template, visual style guide,
   asset prompt/review template, and risk register.
5. Record authoritative sources and unresolved questions for every mandatory
   profile before encoding claims.

**Gate:** contracts reviewed; no production behavior changed; baseline results
and existing Detekt debt documented.

**Commit:** `docs: define brewing platform architecture and evidence process`

### Milestone 1 — domain and compatibility foundation

1. Add stable ID types, alias registry, unknown-ID handling, and catalogue
   validation.
2. Add method families, brewer/filter/accessory/basket profiles, equipment
   configuration, compatibility results, recipes, quantities, ratios, and
   output policies.
3. Add the seven legacy mappings and a compatibility adapter that lets the
   existing UI continue using `BrewMethod` while new domain slices land.
4. Replace raw domain warning strings with structured warning codes rendered by
   localized UI resources.
5. Add evidence-aware grinder records and resolution without deleting valid
   legacy recommendations.

**Gate:** all domain tests pass; current calculator UI still behaves as before
through the adapter; no database change yet.

**Commit:** `feat: add brewing domain and legacy compatibility foundation`

### Milestone 2 — versioned persistence

1. Add snapshot DTOs with explicit serialization versions and tolerant decoders.
2. Add new recipe/log columns, active sessions, custom profiles, DAOs, and
   repositories.
3. Implement the current-version migration, legacy mapping, lazy legacy recipe
   adapter, unknown-value repair state, and immutable log mapping.
4. Add DataStore stable-ID/guidance migration.
5. Export the new Room schema.

**Gate:** current→new and earliest-exported→new migration tests pass; all seven
legacy recipes load; unknowns survive; full recipe/equipment/log/session round
trips pass.

**Commit:** `feat: persist versioned recipes logs equipment and brew sessions`

### Milestone 3 — generalized stage/session engine

1. Add stage definitions, compiler, validator, session reducer, repositories,
   injected clocks, and scheduler interfaces.
2. Migrate current elapsed timer and bloom countdown into stage events while
   retaining UI adapters.
3. Persist each consequential transition and actual stage value.
4. Add long-session scheduled completion and app-start/device-restart
   reconciliation.
5. Make notifications stage-aware and idempotent; retain quiet status plus
   important stage alerts.
6. Restore PiP, dim mode, keep-screen-on timeout, haptics, and current recipe
   targets over the new runtime state.

**Gate:** deterministic timer/recovery suite passes; cold brew survives process
recreation; bloom/PiP/background behavior has no visible regression; one session
can create at most one log and one inventory decrement.

**Commit:** `feat: execute and recover ordered brew sessions`

### Milestone 4 — guidance and visual platform

1. Add guidance preference migration and policy.
2. Add shared stage content, Learn routes/screens, and one responsive live-brew
   screen with guidance-dependent modules.
3. Add current-brew override and explicit remember action.
4. Add the compile-time-safe illustration manifest, placeholders limited to
   debug builds, validation tasks, and missing-asset report.
5. Integrate an initial reviewed asset set and adjacent-stage preloading.
6. Add safety-independent rendering, alt text, stable focus order, large-font
   behavior, and reduced-motion handling.

**Gate:** all four required guidance modes render the same plan correctly;
critical warnings remain visible; Learn resumes independently; missing assets
are honest; asset checks pass.

**Commit:** `feat: add adaptive guidance learn flow and visual instruction platform`

### Milestone 5 — correct the seven current methods

Complete each as a separate vertical-slice commit where practical:

1. **Pulsar:** standard profile; verified Paper/19K/40K profiles; valve stages;
   continuous and pulse variants; defensible capacity/refill workflow; resolve
   19K/40K nomenclature before technical copy.
2. **Manual gravity/V60:** migrate V60 identity; sizes and conservative generic
   dripper; continuous/single/pulse, bloom/agitation, flash ice, bypass, and
   large-format variants.
3. **French press:** capacity/screen configurations; classic and
   crust-break/settle variants; slow plunge and decant stages.
4. **AeroPress:** standard and documented larger profile; caps/filter stacks;
   full beverage, concentrate+bypass, iced/cold, concentrated variants;
   standard orientation by default and a documented decision on inverted use.
5. **Espresso:** explicit dose/yield ratio, basket compatibility and dose range,
   pre-infusion, manual stop at yield, recipe styles, optional papers/screens.
6. **Moka:** nominal size mapping, safety-valve water input, no tamp, observable
   first flow and heat change, collected output, optional documented paper.
7. **Cold immersion:** concentrate/ready-to-drink, environment, vessel/filter
   configurations, durable timer, filter/output/dilution/storage stages.

For every slice, update calculations, catalogue/equipment, stages/content,
assets, recipe persistence, logs/share presentation, evidence, EN/CS copy and
all-locale key parity, plus tests.

**Gate:** all seven satisfy the per-profile release checklist; legacy recipes
remain usable; no method is backed by generic timer copy alone.

**Commits:** `feat(brewing): migrate <family/profile> to staged guidance`

### Milestone 6 — mandatory P1 additions

Implement in dependency order:

1. Expanded manual-gravity profiles: conical sizes, wave/flat bottom,
   wedge/trapezoid, thick-paper carafe, and generic manual dripper.
2. Steep-and-release: Clever-style, Switch, generic valve-release; Switch
   supports immersion-release and manual-gravity recipe modes.
3. Cezve/ibrik: capacity, prepared volume, heat/foam observed events, optional
   bounded rises, neutral aliases, and always-visible heat/flame safety.
4. Automatic batch: generic home batch, justified podless single-cup, and
   user-defined profile without pretending to control hidden machine stages.
5. Restricted-flow concentrate: Vietnamese phin, insert variants, first-drip
   observation, flow troubleshooting, concentrate and separate serving stages.

Use the same vertical-slice gate as Milestone 5. Do not expose vacuum/siphon,
cold drip, cupping, South Indian filter, cloth/nel, percolator, or Neapolitan
profiles; leave evidence-backed, non-user-visible specifications only.

**Commits:** `feat(brewing): add <family/profile> vertical slice`

### Milestone 7 — localization, accessibility, performance, and release readiness

1. Complete all 23 locale key sets; professionally review English and Czech;
   validate format arguments and long-copy layouts.
2. Run TalkBack/manual semantics, large font/display scaling, high contrast,
   light/dark, reduced motion, keyboard/switch access, touch-target, and
   one-handed checks.
3. Measure cold start, stage transition, bitmap decode/churn, memory, and APK
   size. Record per-asset and total illustration impact.
4. Update architecture context, taxonomy, stage engine, guidance matrix, asset
   docs, evidence ledger, migration/downgrade notes, and final report.
5. Add an `Unreleased` changelog entry during development. Only assign a release
   version/date and change `versionName` when a release is explicitly prepared;
   verify both match.

**Gate:** debug build, unit tests, lint, Detekt, migration tests, instrumented/UI
tests on a claimed emulator/device, and practical release compilation pass.
Every skipped check is documented. No placeholder assets or exposed incomplete
profiles remain.

**Commit:** `docs: finalize brewing platform release readiness`

## 8. Per-profile vertical-slice checklist

A profile is user-visible only when all items pass:

- Evidence ledger covers mechanics, defaults, safety, calculations, equipment,
  and wording with confidence.
- Stable family/profile IDs and aliases are registered.
- Compatible equipment and blocking validation are complete.
- Quantity, ratio, output, capacity, ice/bypass/dilution semantics are correct.
- At least one conservative default recipe and its complete stage plan validate.
- Full, Concise, Focused, and Utilities-only policies render correctly.
- Mandatory critical safety is independent of guidance.
- Learn content and live content share the same content records.
- Every mandatory visual stage has a reviewed text-free asset and alt text.
- Recipe save/load and immutable log snapshot round-trip.
- Active-session restoration and notification behavior are covered.
- Grinder guidance has the correct fallback and visible confidence.
- English/Czech are reviewed; all 23 locale resource sets align.
- Domain, persistence, timer, UI, accessibility, and asset tests pass.
- The profile is added to selection/onboarding only in the final enabling commit.

## 9. Test and validation matrix

### Domain

- Stable/alias/unknown ID parsing.
- Catalogue uniqueness and cross-reference validation.
- Filter stack/accessory/basket compatibility and safety severity.
- All output and ratio policies, ice/bypass/dilution, capacities, and edge values.
- Stage compiler validation, bounded repeats, optional branches, content/assets.
- Grinder exact configuration, profile, family, and generic fallback plus
  evidence-confidence preservation.
- Guidance visibility with mandatory safety override.

### Persistence

- Version 17→new and earliest schema 10→new.
- Legacy mapping for all seven methods, null/unknown/invalid filters, unknown
  family/profile values, and downgrade documentation.
- Full recipe, equipment, stage plan, active runtime, and immutable log snapshots.
- DataStore enum-key→stable-ID and instruction-boolean→guidance migration.
- Unique source session and idempotent finish/log/inventory transaction.

### Sessions and scheduling

- Start, pause, resume, cancel, finish, skip, manual/observed/amount completion.
- Countdown, elapsed range, stage transition, repeat, optional stage.
- Bloom through the generalized engine.
- Process reconstruction, late restore, wall-clock forward/backward change.
- Long completion after process/device restart.
- Notification permission denial/channel disable, missed target, duplicate
  notification prevention, and duplicate log prevention.
- PiP, dim mode, keep-screen-on timeout, and deep-link resume regression.

### UI and accessibility

- First-use Full, remembered Concise/Focused/Utilities-only, current override,
  remember action, and no silent reduction.
- Learn/resume, missing asset, incompatible equipment, unknown legacy repair.
- Cold-brew remaining/finish flow and each family’s utilities modules.
- Small phone, wide/landscape/tablet, large font, English/Czech long copy,
  light/dark/high contrast, reduced motion, semantic focus order.

### Assets and release

- Unique manifest IDs and valid drawable/alt-text references.
- Aspect ratio, dimensions, encoded-size budget, orphan and coverage reports.
- Manual full-size and phone-size physical/safety review.
- APK-size delta and live-timer bitmap churn measurement.
- `assembleDebug`, `testDebugUnitTest`, `detekt`, lint, migration instrumentation,
  Compose/instrumented tests, and practical release compilation.

## 10. Risk register

| Risk | Consequence | Control |
|---|---|---|
| Scope is treated as “add methods” | Shallow, misleading profiles and duplicated branches | Enforce vertical-slice and hidden-until-complete gates |
| Legacy snapshot migration invents detail | Users see false equipment/history | Keep snapshot nullable for legacy rows and label unspecified values |
| Unknown IDs fall through to defaults | Wrong recipe or unsafe equipment | Preserve raw IDs and require repair; never default to Pulsar |
| Android background scheduling is assumed exact | Missed or duplicated long-brew alerts | Persist deadlines/effect IDs, use policy-compliant scheduled work, explain degraded alerts |
| Timer rewrite regresses current PiP/dim/bloom behavior | Core workflow becomes worse | Keep adapters and dedicated regression tests before switching UI source |
| Illustration volume/accuracy is underestimated | Release blocked late or unsafe visuals ship | Start manifest/prompts in M0, generate in batches, require physical review per asset |
| 23-locale copy expands too late | Broken resources or hidden controls | Add resource keys with every slice; run parity/format checks continuously |
| `BrewViewModel` absorbs all new responsibilities | Another large, fragile coordinator | Put pure domain/session logic and repositories outside it; keep it as UI orchestration only |
| Grinder precision outpaces evidence | Misleading exact settings | Evidence class/confidence in model and fallback copy |
| Concurrent main changes invalidate assumptions | Merge churn or stale migrations | Fetch/rebase and rerun baseline at every milestone boundary |
| Broad cleanup consumes the program | Delayed value and unrelated regressions | Document existing Detekt debt; fix only findings touched or required by this scope |

## 11. Release and integration strategy

- Keep all mandatory work local until explicitly authorized to push or open a PR.
- Commit after each completed milestone or coherent method slice.
- If separate worktrees are used, keep ownership boundaries explicit and merge
  them back into the main worktree only after the complete effort is validated.
- Do not bump `versionName` merely for development.
- Prefer two readiness checkpoints without forcing publication:
  1. P0 foundation plus all seven corrected current methods.
  2. Mandatory P1 method additions plus final asset/localization polish.
- Partial P1/P2/P3 catalogue entries remain internal and disabled.
- Before declaring release readiness, ensure the dated/versioned changelog entry
  matches `versionName`, all release validation passes, and the final report
  lists exact commits, files, tests, device details, APK delta, migrations,
  accessibility results, limitations, and deferred work.

## 12. Definition of done

The program is complete when:

- The flat enum and booleans are no longer the source of brewing behavior.
- Stable family/profile/equipment/recipe/session/log identities and aliases are
  durable and unknown-safe.
- All legacy recipes/logs remain readable and Pulsar filter selections migrate.
- One validated stage engine powers Learn, all guidance levels, utilities, PiP,
  notifications, short timers, long timers, recovery, and stage actuals.
- Current methods and mandatory P1 additions are complete vertical slices.
- Espresso, moka, iced/bypass, concentrate, automatic, and unfiltered output
  semantics are explicit and method-correct.
- Guidance is per family/profile, changeable during a brew, never silently
  reduced, and cannot hide critical safety.
- Mandatory instructional images are original, text-free, reviewed, local,
  accessible, and performance-budgeted.
- Unit/static/migration/instrumented/accessibility/localization/asset validation
  is complete or any unavailable check is precisely documented.
- The common brewing flow remains fast and familiar despite the deeper model.
