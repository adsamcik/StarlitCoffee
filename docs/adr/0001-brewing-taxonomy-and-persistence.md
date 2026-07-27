# ADR 0001: Brewing taxonomy and persistence

**Status:** Accepted

## Context

The legacy brewing model stores a `BrewMethod` enum name and an optional flat
`FilterType` string. It cannot safely distinguish fundamental extraction
mechanics from brewer geometry, equipment configuration, and recipe variants.
It also cannot preserve future values without losing or misrepresenting data.

## Decision

The brewing domain uses the following separation:

`method family → brewer profile → equipment configuration → recipe`

- A **method family** describes extraction mechanics.
- A **brewer profile** describes material geometry, capacity, supported modes,
  and compatible equipment.
- An **equipment configuration** describes the selected physical setup,
  including an ordered filter stack and relevant accessories.
- A **recipe** describes user intent and is independent from guidance.

Every persisted identity is an explicit, locale-independent stable string ID.
Kotlin wrapper types prevent accidental substitution in code, but storage DTOs
retain raw strings. An alias resolver maps documented legacy values and renamed
IDs. Unknown values remain available for inspection and repair; they never fall
back to a known profile.

Built-in catalogue records live in validated Kotlin code for resource and type
safety. User-created brewer profiles, saved recipes, active sessions, and logs
are persisted. A filter is `Unspecified` when it is absent and `Unfiltered`
only when it is an explicit physical configuration.

Saved recipes and logs use versioned snapshots. Logs are immutable snapshots;
they do not reinterpret history from later catalogue changes.

## Consequences

- Existing `BrewMethod` and `FilterType` values need explicit aliases.
- A legacy adapter keeps existing screens functional while profiles migrate.
- Room retains legacy columns, adds stable IDs/snapshots, and never invents
  equipment or stage history for old rows.
- UI presents familiar brewer names before abstract family taxonomy.
- New branded drippers become profiles only when their mechanics materially
  differ; recipe variations and accessories do not become methods.
