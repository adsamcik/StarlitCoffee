# ADR 0002: Ordered stage, session, and timer engine

**Status:** Accepted

## Context

The legacy timer has an elapsed clock and one optional bloom countdown. It has
no durable ordered workflow, stage actuals, recovery state, or long-duration
session support.

## Decision

Brewing behavior is represented by validated, immutable ordered stage plans.
Plans contain typed actions, targets, content IDs, completion modes, alert
policies, safety messages, and bounded optional/repeated sections. A compiler
expands the bounded portions into a deterministic executable sequence.

Each stage has exactly one completion trigger. Source reference values are
stored separately as typed time, water-mass, beverage-yield, and temperature
targets with exact, approximate, range, deadline, or starting-point semantics.
Each reference target has a stable cue ID, allowing first-flow and final-flow
cues to share one brew clock without collapsing into one averaged target.
This allows a pour to retain both a cumulative-water target and an elapsed-time
cue without making the reducer guess which value advances the stage. Compiled
sessions persist both contracts so an in-progress brew does not change when the
built-in catalogue evolves.

A pure session reducer receives events such as start, pause, timer reconcile,
manual advance, observed event, actual-value entry, cancel, and finish. It
returns next runtime state plus idempotent effects. Consequential state is
persisted before an effect can produce a notification, log, or inventory change.

The runtime uses a monotonic clock for in-process precision and persisted wall
clock timestamps for restoration. Clock changes are reconciled explicitly.
Short active brews keep a lightweight in-process ticker. Long passive brews use
persisted deadline scheduling and never retain an all-day coroutine or foreground
service. The initial implementation avoids exact-alarm permission.

## Consequences

- Compose renders generic stage models instead of method-specific phase trees.
- PiP, dim mode, haptics, and notifications become adapters over session state.
- Stage alerts and logging need stable idempotency keys.
- Cold brew can survive process death and schedule completion without continuous
  execution.
- The implementation must retain the current bloom/PiP experience as a
  regression-tested adapter while migrating it to stages.
