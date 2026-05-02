---
applyTo: "app/src/main/java/**/data/{db,repository}/**/*.kt"
description: "Room, DataStore, and repository conventions for Starlit Coffee"
---

<!-- context-init:managed -->
- Keep Room entities and DAOs under `data/db`; keep repository wrappers under `data/repository`.
- DAOs should expose `Flow` for observable reads and `suspend` functions for writes.
- ViewModels depend on repositories rather than calling DAOs directly.
- When changing entities, update `AppDatabase` version, add explicit migrations, and keep exported schemas in `app/schemas/`.
- `AppDatabase.getInstance(context)` is the current manual singleton; do not introduce a second database creation path.
- `UserPreferencesRepository` owns DataStore preferences for onboarding, default method/filter/grinder, ratio, input direction, and quick-brew behavior.
- Keep repository methods small and predictable; put domain calculations in `domain/` or `util/`, not in DAOs.
