# Brewing taxonomy

## Identity layers

| Layer | Purpose | Example |
|---|---|---|
| Method family | Fundamental extraction mechanics | `manual_gravity` |
| Brewer profile | Geometry, capacity, compatibility, modes | `v60_02` |
| Equipment configuration | Actual physical setup | V60 02 + cone paper + 600 ml server |
| Recipe variant | Repeatable workflow choice | pulse pour over ice |
| Active session | One in-progress execution | current paused cold immersion |
| Brew log | Immutable historical result | completed V60 recipe and actual stage times |

## Initial built-in family IDs

- `valve_controlled_no_bypass`
- `manual_gravity`
- `full_immersion_press`
- `chamber_plunger`
- `espresso`
- `steam_pressure_multichamber`
- `cold_immersion`
- `steep_and_release`
- `heated_unfiltered`
- `automatic_batch`
- `restricted_flow_gravity_concentrate`

Future, non-user-visible specifications may add `vacuum`, `cold_drip`,
`cupping`, and regional filter families only after their full vertical slices are
ready.

## Legacy aliases

| Legacy ID | Family | Conservative profile |
|---|---|---|
| `PULSAR` | `valve_controlled_no_bypass` | `pulsar_standard` |
| `V60` | `manual_gravity` | `v60_unspecified` |
| `FRENCH_PRESS` | `full_immersion_press` | `french_press_generic` |
| `AEROPRESS` | `chamber_plunger` | `aeropress_standard` |
| `ESPRESSO` | `espresso` | `espresso_pump_generic` |
| `MOKA_POT` | `steam_pressure_multichamber` | `moka_generic_unspecified` |
| `COLD_BREW` | `cold_immersion` | `cold_immersion_generic` |

Unknown values remain raw, unavailable records. They are never mapped to Pulsar
or another known default.
