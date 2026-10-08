# Brewing presentation

Historical preparation design from `codex/brew-preparation-design` at
`75ede43a`, retained on 6 October 2026. The shared preparation and coffee-memory
implementation is incorporated into the reviewed-guide runtime. The later
native guide design in `brewing/design/2026-10-02-visual-guides/` supersedes the
standalone Pulsar journey and earlier presentation details described here.

The brewing routes share preparation's label/value discipline, while each
composition serves its task. Astra owns the design: preparation organizes
equipment, the calculator makes editing obvious, and live brewing prioritizes
the current reading. The unrouted AmountStrength and MethodPicker screens are
outside this change.

## Shared language

- A label sits directly above its value. Equipment names and supporting
  explanations do not interrupt that pair.
- Quiet surfaces group related work where grouping helps. The brewing-only
  `BrewingPanel` and `BrewingReference` components retain preparation's neutral
  surface blend without changing the application-wide theme.
- Calculator selection uses a strong filled state. Operator keys keep a
  distinct treatment from number keys; supporting quantities remain visible.
- Live and separate bloom timers use a centered, unboxed reading with compact
  references. During timed bloom, the growing bean is the primary progress
  indicator, with the precise countdown beneath it. It shares the existing
  countdown and pause state; no independent animation clock or duplicate
  progress bar is introduced.
- The bean grows within a stable canvas so its growth does not move the
  countdown or controls. Artwork adapts to available width and remains visible
  with enlarged text. If no artwork is selected, the timer retains its large
  numeric reading.
- Guided stages prioritize remaining time when a countdown exists, with elapsed
  time secondary. Typed `BLOOM` stages with a positive countdown duration use
  the same growing bean; manual bloom and other stages do not imply a progress
  percentage. Stages without a countdown retain elapsed as their main reading.
- Outside timed bloom, the current target or timer remains the main value.
  Supporting quantities keep their names, units, qualifiers and input/output
  meaning.
- Primary actions share one treatment. Pause and resume have visible localized
  labels; finish, cancellation and other secondary actions remain available.
- Guidance can scroll while live controls remain reachable. Source-required
  recipe illustrations and safety messages keep their existing meaning and
  presentation. The progress animation does not replace them.
- Optional measured values and the returning post-brew check-in use the same
  surfaces. Their validation, callback and persistence behavior is preserved.

The original shared-language pass was a presentation change. Navigation, recipe calculation, timer ownership,
notifications, vibration, background recovery, cancellation, completion and
logging remain in their existing owners. PiP and dim-mode presentation retain
their existing lifecycle paths. Explicit temperature and beverage-yield labels
carry the same meaning from preparation into the live references.

Guided routes choose artwork from the existing enabled/weighted catalogue and
remember that choice in saved navigation state for the session. An all-disabled
catalogue yields no artwork and keeps the precise countdown. This adds no
setting or durable-session schema change. Guided selection reads the existing
display counts for rotation; it does not update the legacy display-count metric.

## Grinder artwork experiment

The accepted Grind section keeps its label and setting together, with a model
disclosure for the scale translation, range, adjustment and custom silhouette.
Astra compared that treatment with a restrained silhouette on the right.
The experiment uses the existing exact-model catalogue and accepted curves;
it does not add a setting or guess equipment from a model name.

The accepted clear setting remains the native default. The right-side version
adds character but does not improve the label-to-setting reading; the selected
model is already named below it. The current preview keeps that accepted clear
preparation unchanged, and native artwork stays inside the model disclosure.

Artwork must help equipment recognition without increasing the time needed to
find the setting. It remains decorative for accessibility, yields space to
larger text, and is removed from the main reading if it weakens that hierarchy.
The generated-image and vector provenance is retained in
[the icon catalogue](assets/brewing-icons/README.md).

## Guided learning study (2026-10-01)

Astra's interactive Pulsar study treats the learner as someone who may already
be brewing with the guide open. The browser study described here preceded the
native implementation recorded below. Its browser verification alone did not
establish Android behavior.

The recipe overview groups preparation, bloom, and pouring/finishing into one
readable journey. Reading a step does not start a session. Starting a guided
brew and joining an existing brew lead into the same steps; joining accepts the
existing elapsed time or leaves timing to the person's own timer.

During physical work, the scale target and required brewer state precede the
instructional image. An observable completion cue and a button naming the
completed action replace generic Next. Required handling warnings stay visible;
optional explanations teach why the action matters. Reading ahead preserves
the active step and its clock, with a direct return to the live brew.

During a known timed bloom, the growing bean remains central. It shares the
clock started with the first pour; entering bloom never starts another minute.
At the opening boundary, the heading and valve cue change in place, but only
the user's confirmation advances the physical step. With an external timer,
the guide uses its instructional illustration and condition instead of implying
a measured percentage. Observed drawdown completion stops the clock; lifting
and serving display the finished time without a resume control.

The example follows [Jonathan Gagne's original Pulsar recipe](https://coffeeadastra.com/2023/09/13/the-pulsar-dripper/)
and [the manufacturer's handling guidance](https://nextlevelbrewer.com/wp-content/uploads/2023/06/PulsarUserGuide.pdf):
20 g coffee, 340 g cumulative water, first pour with the valve open, closure at
the first drips, and opening at 1:00 from the first pour. The last 280 g is added
in small pulses with a low water level. No standing water completes drawdown;
the 3:30-4:30 reference is not an automatic completion trigger. Hot handling
uses the black base. Existing approved instructional images and bloom artwork
are reused.

Browser review exercised the complete physical sequence, timer-free reading,
reading ahead without advancing the active step, mid-brew entry, invalid elapsed
time, and the external-timer path. Normal and 320 px renders were reviewed;
the narrow first-pour controls measured at least 44 px high. Astra's final
corrections clarify valve orientation, remove contradictory closed-valve copy
after the bloom boundary, and keep serving's drawdown time read-only.

The browser study does not prove native persistence, recovery, notifications,
physical brewing, or enlarged-text behavior. Native integration below uses an
explicit reviewed recipe and transition contract, preserving the approved
illustrations, warnings and cues rather than inferring execution from prose.

## Coffee selection and grind memory study (2026-10-01)

This study extends preparation with selecting an owned coffee, remembering
grind settings, and adding another physical pack. Astra owns the composition.
The browser study used local demonstration data; the native implementation
recorded below connects the design to inventory and Android persistence.

### Implementation before this rework

- `GrindPrepScreen` already places `CoffeeBagSelector` in the Coffee section.
  Its absence in the earlier preparation preview was an omission. The picker
  currently lists physical bag rows rather than a shared coffee identity.
- `CoffeeBagEntity` is a physical pack: it owns remaining/initial weight, roast
  and opened dates, status, and an ID used by brew history and coffee usage.
  Barcode is indexed without uniqueness. `scanSessionId` is unique, allowing
  retrying one scanned-save operation without creating another pack.
- A bag's `grindSetting` is an unscoped string. Completion writes it, but
  `BrewDerivation` does not read it. Current Regular/Decaf behavior adjusts the
  recommendation; it does not remember independent user-chosen defaults.
- Scan to brew matches available physical bags locally. Inventory scanning can
  prefill a known name and roaster, but neither flow has a shared coffee ID.
  Photo matching currently proposes the old bag's remaining weight as a weight
  candidate; that must not become the starting weight of a newly bought pack.

### Product contract

Choose coffee in the existing Coffee section, directly beside the work of
weighing the dose. The choice shows a coffee name and the selected physical
pack's state/remaining weight. A selected bag supplies its known Regular/Decaf
type. Brewing without a bag keeps the explicit type choice.

Group the picker by coffee while keeping each pack independently selectable.
Prefer opened usable packs; include unopened packs with their own weight and
dates. Finished packs stay available to repeat-purchase recognition without
becoming brewable choices. Selecting or browsing a sealed pack must not mark
it opened. Keep the current brewing method when choosing coffee in preparation.

Grind retains one direct label/value. Its editor shows the current source and
reveals the scope for remembering a change. Removing an override reveals the
next applicable value. Broader saves preserve narrower overrides. A deliberate
change can apply to the current brew while also saving a broader default, but
the source label must make that distinction clear. Switching pack or equipment
clears an incompatible temporary override rather than leaking it to another
coffee.

Resolution order, within a compatible grinder and brewing context:

1. Explicit value for this brew.
2. Override for this physical pack.
3. Saved value for this coffee, reusable by its future packs.
4. Independent Regular or Decaf default.
5. Existing recommendation, or generic grind guidance when equipment is unknown.

Saved user values are final values: do not add the automatic decaf adjustment
again. Preserve the grinder's scale and unit; values from one grinder or method
cannot silently transfer to another. Ordinary brew completion records the used
value without overwriting remembered defaults or inferring that it was good.

Scan to choose and Add pack share recognition but have distinct intent. Choosing
finds an existing owned pack. Adding finds a coffee, reviews the new pack's
details, then commits one physical pack. A known coffee can reuse its saved
setting; it never inherits another pack's remaining grams, opened date, or
pack-specific override. Unknown or ambiguous results offer manual/photo review
or choosing the correct coffee. Recognition is not a promise of a universal
barcode catalogue. A changed coffee or lot can be kept separate.

### Persistence design

| Record | Ownership |
| --- | --- |
| Coffee identity | Stable ID for the coffee the user explicitly reuses; shared name, roaster and type, with barcode lookup associations |
| Physical pack | Existing bag ID plus coffee ID; its own weight, roast/open dates, state and scan-save identity |
| Barcode association | Normalized code and coffee ID; multiple packs can share a code, and conflicting coffee matches remain distinguishable |
| Remembered grind | Scope (type, coffee or pack), owner ID, exact grinder/calibration context, brewing context, value and scale |
| Brew snapshot | Selected pack and the effective grind at start; later setting edits cannot change a running or historical brew |

The first native implementation uses the following keys:

- `CoffeeIdentity(id, name, roaster, coffeeType)`, with an explicit stable ID.
  Keep lot-specific facts on the physical pack rather than changing all older
  purchases when a newly scanned label differs.
- Add `coffeeId` to the existing physical bag record. Preserve its `id`,
  `weightG`, `initialWeightG`, dates, state and scan-session identity. Pack numbers
  are presentation labels within a coffee, never identifiers or barcode keys.
- `CoffeeBarcode(coffeeId, normalizedCode, rawCode)` has a unique pair of coffee
  ID and normalized code, with a non-unique code lookup index. Reuse the existing
  UPC/EAN matching normalization; do not alter custom identifiers or assume that
  one barcode always means one coffee.
- Store type defaults, coffee settings and pack overrides with their respective
  owner keys. Each has a unique `(owner, grinderId, methodId, filterKey)` and the
  same value payload. Separate owner tables allow normal foreign keys for coffee
  and pack settings; one repository/resolver supplies the shared domain model.
  Use stable `BrewMethod` and `FilterType` identifiers, with a non-null sentinel
  for no filter. A user setting is an exact context match; the recommendation's
  filter-agnostic fallback is not permission to reuse a saved setting elsewhere.
- The value payload retains scale type, clicks-per-rotation when relevant, and
  update time. `DIAL_CLICKS` stores integer total clicks from the known reference:
  the example's `1.4` means one rotation plus four clicks, not 1.4 clicks or 1.4
  rotations. `PURE_CLICKS` also stores integer clicks; `NUMBERED_DIAL` stores a
  canonical decimal string. Format display values from these units and reject
  incompatible scale metadata. The current calibration style changes the
  recommendation range width; it must not rescale a saved user value. A future
  change to a physical zero reference requires a distinct compatible calibration
  context, not automatic conversion.
- A brew draft holds optional temporary grind and remembered selections for
  Regular and Decaf. Starting a brew freezes pack ID, coffee ID, type, grinder,
  method/filter context, effective value and its source into the session snapshot.
  Completing a brew consumes inventory once through the existing completion
  transaction; it does not write a remembered setting as a side effect.

Adding a pack and recording its scan-save identity must be atomic and
idempotent. A new intentional scan/add operation may create another pack of the
same coffee; repeated callbacks or retrying the same operation may not. A
barcode is a lookup association, not a physical-pack identifier. Where a code
matches incompatible coffee identities, require an explicit choice.

Implementation requires a non-destructive migration from Room version 20.
Preserve existing bag IDs, inventory balances, brew/usage links and legacy grind
strings. Do not fabricate grinder/method scope for a historical string or merge
coffees solely by fuzzy names. Conservative identity linking must retain
conflicting metadata and allow the user to keep a different coffee separate.
The current name/roaster-based auto-rotation and grind copying must be replaced
by identity-aware selection that leaves unopened packs sealed until used.

Implement in three coherent stages after the design is accepted:

1. Add identity, barcode associations and scoped grind storage. Initially give
   each legacy bag its own identity unless an existing explicit relationship
   proves a shared coffee. Later linking is deliberate and must preserve pack
   IDs, historical snapshots and conflicting lot facts; no migration wizard is
   required to keep brewing. Keep legacy grind strings as historical evidence
   until the user confirms their grinder/context and saves a scoped value.
2. Wire one resolver, the existing picker and the grind editor into all brewing
   entry paths. Freeze the effective result at start. Mark a sealed pack opened
   on confirmed use rather than picker selection, and replace legacy completion
   copying/rotation so later brews resolve the proper coffee or pack setting.
3. Wire recognition and reviewed pack addition to one durable add-operation
   token. Retries return the same committed pack; starting another intentional
   purchase produces a new token and pack. Known coffee recognition also searches
   identities with only finished packs. Unknown/ambiguous matches stay reviewable
   and can be kept as a separate coffee before the transaction commits.

### Value, costs and verification

- **User value / scenario:** recurring selection from owned coffee; faster repeat
  purchases and reuse of deliberate settings without conflating physical packs.
- **Default / discovery:** a contextual Coffee selector and editable Grind value;
  no required setup and no new permanent settings panel. Bag type is inferred
  when known; remembering scope is disclosed only while editing the setting.
- **Core flow / accessibility:** keep weighing, grinding and the primary brew
  action readable. Named choices, source text, selected semantics and adequate
  touch targets must work with long names and enlarged text.
- **Failure behavior:** canceling scanning or editing preserves inventory and
  settings. Offline recognition uses known coffee; unknown codes can be entered
  manually. Invalid/nonfinite values cannot be saved. Stale or incompatible
  settings fall back visibly rather than applying another grinder's value.
- **Technical cost:** shared identity and scoped-setting persistence, migration,
  transactional scan addition, snapshot wiring and replacement of legacy
  unscoped copying. Keep one resolver and one editor across brewing entry paths.
- **Removal criteria:** consolidate duplicate scan/picker entry points, hide
  unused scopes in the editor, and remove artwork or metadata that slows finding
  the selected coffee or grind.

Native verification requirements cover the migration with existing history, precedence
and exact context matching, independent type defaults, override removal,
pack switching, new-pack versus same-scan retry, known/unknown/ambiguous codes,
unchanged existing balances, and sealed-pack lifecycle. UI verification must
exercise picking among identical coffees' physical packs, contextual save scope,
long names, 320 dp and enlarged text. Camera permissions/recreation and actual
physical-pack handling remain device checks. Browser proof is separate.

### Browser study verification

The local interactive proposal was exercised with sample data at normal width
and a 320 px browser viewport. Astra reviewed the actual captures of Prepare,
the physical-pack picker, the grind editor and repeat-purchase review. The
narrow host wrapper yielded a 261 px product surface; those three main views
had equal client/scroll widths and measured controls at least 48 px high
(radio labels at least 64 px). Long coffee names wrapped in new-pack review.

Behavior checks confirmed isolated pack overrides, coffee-wide changes that
preserve narrower settings, independent Regular/Decaf defaults, remembered
type selections, and removal through pack → coffee → type → recommendation.
A broad default save applied the explicit value to the current brew without
replacing its narrower saved value; switching packs cleared that temporary
value. Invalid grind text was rejected.

Scan-to-choose returned existing physical packs. Adding another House blend
pack retained the existing 87 g and 250 g balances and inherited the coffee
setting. A previously known Worka Sakaro with no current packs recovered its
saved coffee setting; double-clicking the reviewed add action produced one new
sealed pack. New pack weight and roast date started blank. An opened pack with
blank remaining weight was rejected. Unknown-barcode manual review and cancel
left inventory unchanged. Active-type selected markers were corrected and
verified, and browser error/warning logs were empty after the exercised flows.

These checks prove the local design study only. The scan control is explicitly
simulated, ambiguous-identity resolution is specified rather than demonstrated,
and the demo's value validation is limited to its example grinder. Native
persistence, Room migration, camera/label recognition, equipment changes,
enlarged-text rendering and real-device inventory handling remain unverified.

## Native implementation (2026-10-01)

The accepted compositions now run in Android. Astra owns the preparation,
picker, grind editor, pack review and guided-learning layouts. Domain logic,
Room persistence and navigation connect those layouts to the existing brew flow.
The calculator's cup presets, custom feedback icons and existing live timer
compositions remain part of that shared language.

### Preparation and inventory

- Coffee selection is always available beside the dose. The picker groups owned
  packs by coffee, shows their individual remaining weight and state, and excludes
  finished, frozen and empty packs. Choosing coffee preserves the brewing method.
- Regular and Decaf retain separate draft selections and durable grind defaults.
  The editor exposes brew, pack, coffee and type scopes only when applicable.
  Exact grinder/method/filter matching prevents incompatible settings from leaking.
  Broader saves preserve narrower overrides; the explicit value still applies to
  the current draft. Removing a setting reveals the next applicable source.
- Dial-click values retain integer clicks and their rotation size. A value such
  as `1.14` remains one rotation plus fourteen clicks throughout editing,
  persistence and history. Saved values do not receive another decaf adjustment.
- Scan to choose requires a physical-pack choice in preparation. Scan a new pack
  uses known coffee identities, including coffees whose previous packs are finished.
  Ambiguous matches require choosing an identity. Unknown codes allow manual entry.
  New full weight and roast date start blank; opened packs require an explicit
  positive remaining weight. Each reviewed add operation has its own retry token.
- Room version 21 separates shared coffee identity from physical-pack inventory
  and adds the three remembered-setting tables. Migration preserves pack IDs,
  balances, history and legacy grind strings. Each legacy pack receives its own
  identity: names and barcodes are evidence, not permission to merge coffees or
  infer a remembered setting's scope.
- Starting a brew freezes its selected pack, coffee identity and effective grind.
  Completion retries produce one log and one inventory deduction. Ordinary
  completion does not update grind memory. The next pack of that identity remains
  sealed until used.

The existing inventory photo/OCR flow remains available in inventory. Preparation
does not expose a label-photo shortcut: that older flow commits a bag itself,
rather than returning recognition metadata to the new pack review. Barcode and
manual review are integrated here. Recognition no longer proposes an older pack's
remaining grams as the new pack's weight.

### Guided learning and execution

- The reviewed Gagné Pulsar curriculum has one journey for reading, starting and
  joining. Preparation and dosing precede the durable clock; the first pour starts
  it. Joining preserves an explicitly entered elapsed time and marks earlier
  stages skipped, without inventing physical measurements.
- `PulsarGuidedStagePlan` supplies the explicit executable contract. Its six
  physical stages use manual confirmation, typed equipment requirements, the
  first-pour bloom boundary and the approved handling warning. This contract does
  not derive transitions from instructional prose or invent a pulse count.
- The first-pour target, valve state and first-drips cue precede the illustration.
  Bloom shows the user's enabled, weighted bean artwork as the main progress
  indicator, backed by that same clock. Disabling artwork preserves numeric
  timing. At the boundary the guide requests opening the valve; it never advances
  a physical action automatically.
- External-timer guidance uses the approved instructional illustration and
  observable condition without claiming app-measured progress. Reading ahead
  preserves the active step and clock. Stage changes reset scroll position;
  clock ticks preserve it and do not show a saving state.
- Confirmations carry the rendered stage index and stable event identity, so a
  delayed callback cannot skip the next stage. Confirming drawdown freezes the
  elapsed time while handling and serving remain visible. Completion goes through
  the existing durable finalizer, including retry and snapshot storage.
- Saved navigation state preserves reading and joining context. Durable session
  restoration and notification entry route Pulsar journeys back to this layout.
  Other exact recipe learning pages use the new action/target/illustration
  hierarchy while retaining their reviewed read-only contracts.
- New UI strings are supplied in all 22 supported translated locales. Dialogs
  scroll at enlarged text, related switches have one labeled touch target, stage
  headings announce changes, and ticking numbers do not repeatedly announce.

The approved pulse-pour illustration has a pre-existing checkerboard-shaped
wedge baked into its pixels. This pass preserves the approved illustration's
complete geometry; replacing that asset remains separate artwork work.

### Native verification

- The full unit suite passed 1,399 cases with four skipped (1,403 total). This
  includes scoped-grind precedence, multi-digit click precision, independent
  coffee types, frozen legacy brew snapshots, idempotent completion, interrupted
  session creation and the controller's reading/joining/manual-confirmation paths.
- Detekt and the final isolated debug app/test APK builds passed. The build used
  a temporary 4 GB Gradle heap; repository build settings were unchanged.
- The focused Android 16 checks passed 46 distinct migration, real Room persistence
  and Compose cases across the main run and targeted reruns after final polish.
  Restored timing is checked through the explicit clock-reconciliation event;
  read-ahead return and edited-value attribution are checked in the current layouts.
- Native Room checks cover populated 20→21 migration and the complete 10→21 path,
  fresh repeat packs, concurrent same-token retries, barcode ambiguity, sealed
  opening behavior, restored guided execution, frozen coffee/grind history and
  exactly-once inventory consumption.
- Compose checks cover the production grinder catalogue, independently selected
  physical packs, explicit scan selection, exact remembered values, blank new-pack
  fields, and preparation/guided controls at 320 dp with 1.6x text. Existing live
  timer, separate bloom, cancellation, safety and completion behavior also pass.
- Astra accepted the native composition. The last polish corrections remove an
  old source attribution while the grind draft is edited and separate the narrow
  journey header's title and overview action. Native captures are retained locally
  in `.qa-screens/native-system/`.
- A smoke check in the actual review app followed More → Learn → Pulsar → Guide
  this brew, confirmed paper and dose preparation without starting the clock,
  started the first pour explicitly, and reached live bloom on that same clock.
  The synthetic journey was canceled afterward. Component fixtures and the real
  navigation smoke check are separate evidence.

Camera permission/recreation, real barcode capture, physical brewing, notification
delivery and physical-device behavior remain unverified. Barcode UI checks use
scanner-return data. Android lint was not rerun here; the previously recorded
unrelated findings below remain a separate validation boundary. This is a
worktree implementation, not a release or a deployment to the user's normal app.

## Earlier presentation verification

- The bloom-centered pass passed 25 focused unit cases for sprite selection,
  atlas geometry and stage presentation, plus all eight timer/guided Compose
  UI cases in one Android 16 emulator run. The new checks cover visible bloom
  artwork and reachable controls at 320 dp/1.6x text, paused guided bloom,
  no-artwork fallback, and omission on manual or non-bloom stages.
- Astra accepted all 15 bloom-centered native captures, including the visible
  artwork at enlarged text, guided growing/paused stages, and the preserved
  non-bloom states. Guided captures exercise the actual content/action bar with
  fixture presentation, without the persistence coordinator or navigation shell.
- The earlier hierarchy refinement passed all 13 focused Compose UI cases in one
  Android 16 emulator run: calculator selection and remembered setups,
  integrated/separate bloom, pause/resume, exit confirmation, passive cold brew,
  guided actions/safety, and saving/completed presentation. Native captures
  include the actual operator keypad and 320 dp layouts at 1.6x text.
- Debug app and instrumentation APKs build. Packaging required a temporary
  4 GB Gradle heap; repository Gradle settings are unchanged.
- Detekt passed for the selective refinement. The earlier full-flow unit run
  passed 1,384 cases, with four skipped; unit sources were unchanged by the
  subsequent visual refinement and that full run was not repeated.
- The earlier full-flow pass covered 31 distinct Compose UI cases:
  preparation and quantity meaning, remembered setup navigation, integrated
  and separate bloom, pause/resume, exit confirmation, passive cold brew,
  guided actions/safety, and saving/completed presentation.
- The bloom fixture permits its actual configured coroutine countdown plus
  emulator scheduling overhead. Its injected clock controls elapsed time;
  it does not accelerate the bloom countdown. During the earlier full-flow pass,
  the integrated case passed in isolation after a timeout under concurrent build
  load; the selective-refinement run passed all cases together.
- Astra accepted all 15 selective-refinement native captures, including
  light/dark and 320 dp, 1.6x text layouts. Guided captures
  exercise the actual content and action bar with a fixture presentation,
  without the persistence coordinator or full navigation shell.
- The current interactive comparison was rendered at normal and 320 px widths.
  It shows beginning/growing/opening bloom, guided bloom, live pouring and
  unchanged clear preparation. Resume advanced the countdown and bean; pause
  held both. Preview interactions do not demonstrate Android persistence.
- Debug app/test APK builds and Detekt passed. The latest Android lint run
  reports seven errors outside this change: the four previously recorded
  findings (Gradle version advice, configuration-aware resource access in the
  barcode amount message, unused `msg_learning_intro`, and `Uri.parse` in
  Settings), plus three Tracebox dependency update notices in the unchanged
  version catalogue. No finding points to the bloom-centered implementation.

Verification used a separate review package on an Android 16 emulator. The
latest review started a previously stopped emulator without a window and
installed only the separate review packages. Those packages were removed and
the emulator was returned to its stopped state afterward.
No physical-device, background recovery, notification delivery or PiP runtime
claim is made by this styling review.
