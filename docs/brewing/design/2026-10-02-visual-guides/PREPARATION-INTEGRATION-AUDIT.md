# Preparation and journey integration audit

4 October 2026. Comparison and scoped integration of `codex/brew-preparation-design` at `75ede43a` against this implementation branch and canonical main. This is development evidence, not release readiness.

## Confirmed state

The preparation commit is not an ancestor of canonical main. Its changes span 104 files, including Room migration 20→21, coffee identities, barcode associations, pack identity, three scoped grind-setting tables, their repositories and UI, and a Pulsar journey. The compatible persistence and preparation work is now integrated. The reviewed Gagné Pulsar procedure is adapted through the shared guide canvas and durable coordinator. Its six source steps retain actual valve, first-water, pour and drainage criteria, alongside the compatible existing exact recipe; no second runtime was copied.

`GrindMemoryResolver` already supplies one precedence model: temporary brew setting, pack, coffee, then coffee type. Values include grinder scale and clicks-per-rotation compatibility. `PreparedGrind` carries that result into preparation without applying recommendation offsets twice. The guide implementation should consume that result; it must not introduce another preference, DAO, coffee identity or remembered grind value.

The source branch's `PulsarJourneyViewModel` uses the durable `BrewSessionCoordinator` for execution and a separate saved reading cursor. The native integration reuses that ownership contract through `ReviewedGuideStartFactory`, the shared `LearnBrewerScreen` reader and `BrewSessionScreen`. Reading does not dispatch brew events. A compatible remembered Pulsar grind requires the paper filter; a metal-filter setting cannot silently become the source recipe's grind.

## Integration order

1. Reconcile the preparation commit's database model and migration against the implementation baseline. Retain physical pack IDs, stock balances, log links and legacy unscoped settings. Run its persistence/migration checks before connecting any new UI.
2. Reuse its coffee selector, scoped grind resolver and prepared-grind presentation in the existing Start preparation flow. Preserve the actual calculator method, quantities and selected bag in the immutable session execution context.
3. Adapt the Pulsar journey to the shared canvas, read-ahead and activity strip. Retain its exact Gagné plan, manually observed physical completion and separate reading cursor. Reuse the canonical approved Pulsar drawable from `2fff9e14`; the earlier preview silhouette is not an override.
4. Exercise route changes, cancellation, recreation and logging with both prepared and legacy sessions. Check one-shot persistence and stock deduction, not only screen appearance.

## Native verification

Both APKs build and detekt passes. All 214 focused JVM checks pass. On the dedicated API 36 emulator, all 11 migration checks, four coffee-memory persistence checks, four calculator-session persistence checks and four preparation UI checks pass. Migration 20→21 preserves physical pack stock, historical links and legacy unscoped settings. New packs remain independent and unopened until explicitly opened. Finalization freezes coffee identity and the prepared grind setting without overwriting unrelated memories.

The two grind-editor UI fixtures initially selected ZP6 with espresso, a combination without a verified recommendation. The correct fallback hides the precise editor. The fixtures now use supported Pulsar/paper/ZP6 context; no unsupported recommendation was added. Narrow editor coverage uses 1.6 font scale. Logs: `build/native-preparation-build.log`, `build/native-preparation-emulator.log`, `build/native-preparation-ui-fixed.log`. The installed emulator database was backed up before migration, and its existing running Pulsar was retained.
