# Chemex support

Research checked on 2026-09-30. These are practical starting recommendations;
neither the recipe nor the app has been tested with a physical Chemex in this task.

## Evidence and decisions

| Primary source | Evidence | App decision |
| --- | --- | --- |
| [Chemex six-cup instructions](https://chemexcoffeemaker.com/products/six-cup-classic-chemex) | Medium-coarse grind, bonded paper with three layers facing the spout, about 30 seconds of bloom, slow pours with room below the rim. The six-cup vessel holds 30 US fl oz; the brand defines a cup as 5 fl oz. | Prepare and rinse compatible bonded paper; keep the spout air channel open. No universal capacity or assumed number of ordinary mugs. |
| [Chemex three-cup instructions](https://chemexcoffeemaker.com/collections/glass-handle-chemex/products/three-cup-glass-handle-chemex) | This size uses FP-2/FP-2N half-moon paper and has a 15 US fl oz capacity. | The everyday method leaves size unspecified and reminds users to fit the paper to the brewer. It never substitutes a V60 filter or assumes a six-cup vessel. |
| [Stumptown Chemex guide](https://www.stumptowncoffee.com/pages/brew-guide-chemex) | An eight-cup example uses 42 g coffee, 700 g water, roughly 96 °C water, a 45-second bloom, and roughly four minutes overall. | Supports a medium-coarse start and a 45-second bloom. Keep this eight-cup source distinct from the existing six-cup exact recipe. |
| [Blue Bottle Chemex guide](https://bluebottlecoffee.com/us/eng/brew-guides/chemex) | 36–46 g coffee with 600 g water, medium-coarse grind, 93–99 °C water, a 45-second bloom, and 4:30–5:30 total time. | Use 1:16 and 93–96 °C as approachable starts within the published guidance. A 4–5½ minute window spans these two roaster examples; it is not a completion deadline or a guarantee for every batch. |
| [Baratza Encore ESP manual, January 2023 v1.0, p. 5](https://assets.breville.com/ZCG495/manual-encoreesp-v1-0-en-010923.pdf) | The manufacturer explicitly lists Chemex at setting 30 and says to adjust for recipe and taste. | Start at the published point 30, with no invented numeric range. Other grinders retain medium-coarse descriptive guidance. |

The default three-times-dose bloom and two subsequent additions are app-authored
starting choices, not an exact reproduction of either roaster's pouring schedule.
Let the water level fall between additions and observe drainage. Check spout
airflow before interpreting slow drainage as a grind problem. Adjust by taste.
No fixed decaf time reduction is applied to Chemex.

## Product fit

- **User value and scenario:** Chemex owners can brew regularly from the normal
  calculator without interpreting a generic brewer label or selecting a V60.
- **Default behaviour:** A method selection supplies ratio, grind, bloom, and
  time recommendations; bonded paper is part of the equipment snapshot.
- **Discoverability:** Chemex appears in onboarding, method settings and the
  existing method picker. Previously configured method lists remain unchanged;
  owners can enable Chemex in Settings. The existing exact recipe is named
  Chemex in its setup and Learn surfaces.
- **Core flow and configuration:** No new permanent settings or separate mode.
  Existing remembered setups and favorites retain the method's own ratio,
  calculator expression and grinder. Advanced pour controls stay contextual.
- **Failure behaviour:** Drainage and serving require user confirmation; a timer
  never implies the coffee has physically drained. Unknown grinder settings use
  descriptive guidance. The calculator offers coffee and brew-water input only:
  a method-specific bonded-paper beverage-loss coefficient has not been verified.
  Existing session output snapshots retain the platform's approximate 2 g/g
  retention model; it is not a measured Chemex yield.
- **Accessibility:** Localized preparation/timer copy and method-name arrays
  cover all 23 app locales. Text instructions explain filter orientation,
  drainage and safe handling without requiring an illustration.
- **Technical cost:** One appended enum identity, one unspecified-size brewer
  profile and bonded-paper category, four durable stages and their curriculum.
  Existing persistence codecs use stable names and need no schema migration.
- **Removal criteria:** Simplify defaults if device or brew testing shows that
  their timing or pour guidance causes confusion. Add numeric grinder settings
  only when evidence identifies the precise grinder/burr configuration.

## Compatibility and verification

`CHEMEX` is appended to the enum and every localized name array so existing
ordinal positions remain intact. `chemex_unspecified` is the everyday profile;
`manual_thick_paper_carafe`, `chemex_42_700`, and its reviewed assets keep their
existing IDs. The exact recipe remains a separate immutable source recipe with
its own 42/700 quantities, stage targets, and production eligibility checks.
Its older 5–6½ minute timing is not replaced with the everyday start window.

Regression coverage checks calculator quantities, grinder fallback, durable
session equipment, manual drainage, log identity, saved setup round trips,
localized array alignment and stage-linked guidance. Debug build and static
checks establish software compatibility only; device and physical brewing
validation are reported separately.

### Results from this worktree

- Full unit suite after integrating main's grinder work: 1,410 tests, zero
  failures/errors, four skipped.
- Detekt passed; debug app and Android test APKs built successfully.
- Exact guidance localization, tracker asset registry and independent visual
  review generators passed their checks. Existing recipe/artwork IDs were kept.
- Lint completed with four existing findings: the Gradle version advisory,
  configuration-aware resource access in barcode UI, an unused Learn intro
  string, and a KTX URI suggestion in Settings. Their reported code is unchanged
  by this feature. No Chemex finding was reported; the full lint gate remains
  failing, so this is not a release-readiness claim.
- The two in-memory Compose tests compile. The emulator already had the debug
  app running; execution is pending permission to restart that instance.
- Follow-up integration combines this feature with main's existing grinder
  edits, preserving their source-backed policy and staged/unstaged state.

Debug APK SHA-256:
`09ce60db9486161268e944b52f9bd07ab895faf7dedd810734cbf3d7a1780182`.
