# Brewing platform risk register

| Risk | User/engineering effect | Control | Owner checkpoint |
|---|---|---|---|
| Legacy data receives invented detail | Misleading recipes and logs | Null snapshots and conservative legacy adapters | Persistence migration tests |
| Unknown IDs fall back to a known method | Unsafe/misleading behavior | Preserve raw value and show repair path | Catalogue and load tests |
| Long timer expects exact platform scheduling | Missed or duplicate completion | Persist deadline/effect IDs and use policy-compliant scheduling | Session recovery tests |
| Visual scale is underestimated | Late, incomplete, or inaccurate teaching | Manifest, staged batch review, hidden-until-complete gate | Asset coverage report |
| Existing live-brew polish regresses | Core flow becomes worse | Adapter and PiP/dim/bloom regression tests | Session-engine gate |
| Locale work lands late | Missing strings and unusable layouts | Key parity and long-text checks per slice | Localization gate |
| Broad cleanup expands scope | Delayed feature work | Fix only debt touched by this program | Milestone review |
