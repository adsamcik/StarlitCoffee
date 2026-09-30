# Beverage-output estimate

## Product decision

Add a contextual **Coffee out** option to the preparation calculator's water
input. Water input remains the default. When Coffee out is selected, the typed
amount is treated as the desired collected beverage and the calculator derives
both dry dose and water to pour with the method-specific apparent-loss model:

`beverage output = brew water - apparent-loss coefficient × dry coffee dose`

The coefficient is not described as absorption. It represents the measured
input-to-cup difference at a defined endpoint, including bed and equipment
retention, extracted solids, evaporation, and similar residuals.

## Whole-product evaluation

- **User value:** Lets a brewer plan for a mug or carafe amount without ending
  up short, while also showing an approximate collected amount from an ordinary
  dose or water input.
- **Target scenario:** Occasional but recurring for manual filter, immersion,
  AeroPress, and cold-concentrate brewers; especially useful when filling a
  known cup or sharing a brew.
- **Default behavior:** Existing dose and water-input calculation is unchanged.
  The estimate is opt-in and local to the preparation screen.
- **Discoverability:** The option appears beside the result only when the
  selected method has a defensible generic model and the user is working with a
  water amount.
- **Core-flow impact:** No extra action is required. Enabling the mode adds one
  tap and keeps the same calculator, ratio, method, and Start action.
- **Configuration decision:** This is a contextual input interpretation, not a
  permanent Settings toggle. It returns to Water in when an unsupported method
  is selected.
- **Failure behavior:** Espresso uses its direct dose-to-yield model; moka does
  not expose the generic inverse model. Invalid ratios fail closed instead of returning an impossible dose.
  French press copy notes that liquid intentionally left in the press is not
  included.
- **Accessibility impact:** The control uses a text label and selected state,
  not color alone. Calculated dose, water to pour, and approximate output have
  distinct semantic labels.
- **Technical cost:** One pure estimator, one calculator state variant, and
  targeted unit/UI-state tests. No database migration or new saved setting.
- **Removal criteria:** Remove or redesign the option if it is mistaken for a
  precise yield guarantee, materially increases preparation errors, or direct
  method data invalidate the central coefficients.

## Supported compact model

| Method | Apparent loss (g/g dry coffee) | Qualification |
| --- | ---: | --- |
| V60 | 2.1 | Fully drained manual filter central value |
| Pulsar | 2.2 | Protocol-sensitive inferred central value |
| French press | 2.2 | Excludes intentionally unpoured decant residual |
| AeroPress | 1.8 | Paper, normally fully pressed; protocol-sensitive |
| Cold brew | 2.0 | Paper/gravity-drained concentrate central value |
| Espresso | — | Direct dose-to-beverage yield; in-cup planning uses the beverage ratio |
| Moka pot | — | Pot, fill, heat, and stop point require calibration |

The UI presents rounded grams and explicitly labels the result as approximate.
The internal calculation retains full floating-point precision for downstream
brew setup.

## Remembered and named setups

Each method starts at its own ratio and remembers the expression, input
quantity, ratio, filter, and grinder across switches and app restarts. Espresso
uses Coffee and In cup; its Water in card is hidden because beverage yield does
not determine water input. Moka's In cup card is hidden until a calibrated output
model is supported. Available cards share the full row width, and the enlarged-text
layout stacks only those cards.
Derived filter-brew output is approximate; requested output and espresso yield
are planned targets. Compact espresso results also identify cup yield or dose,
never water input.

- **User value and scenario:** Repeated everyday brewing resumes without rebuilding
  the setup; named Home/Work favorites cover recurring changes of equipment.
- **Default and discoverability:** Remember automatically. The existing heart
  saves a named favorite; a contextual picker appears in the settings row when
  favorites exist. The existing Favorites screen handles deletion.
- **Core flow and configuration:** No new setting, mandatory step, or second
  preset collection. Each method's ratio options match that method, and fractional
  espresso ratios retain their decimals.
- **Failure and compatibility:** Invalid remembered entries fall back to method
  defaults. Legacy favorites load their dose and equipment. The old global ratio
  cannot be attributed to a method and is replaced by method defaults on first use.
- **Accessibility:** Named setups use standard labeled menu items and touch targets;
  the existing quantity cards expose selection and availability to TalkBack.
- **Technical cost:** Per-method DataStore values and optional calculator intent
  in the existing versioned recipe snapshot; no database migration.
- **Removal criteria:** Simplify the quick picker if it obscures the primary ratio
  action or duplicates a future unified favorites surface.
