# Brewing terminology research snapshot

This directory preserves the evidence package supplied on 2026-08-06 for the
12 canonical brewing concepts across all 22 non-English app locales.

The structured records are research inputs, not production approval. Every
record is explicitly `researched_not_native_reviewed`; 40 records retain
`INSUFFICIENT_EVIDENCE`. Production localization remains fail-closed until the
existing native editorial, terminology, resource, release-gate, and device QA
requirements are satisfied.

## Files

- `coffee_brewing_terminology_records_2026-08-06.jsonl`: 264 locale/concept records.
- `coffee_brewing_terminology_sources_2026-08-06.jsonl`: deduplicated source register.
- `coffee_brewing_terminology_manifest_2026-08-06.json`: coverage and limitations.
- `coffee_brewing_terminology_qc_2026-08-06.json`: mechanical QC output.
- `coffee_brewing_terminology_report_2026-08-06.md`: human-readable research report.

## Imported-file SHA-256

```text
C650190CFF7C93AD84B2EF8B3914D10AA40E1C98EBB46D0B62D09255E2113D39  coffee_brewing_terminology_manifest_2026-08-06.json
D996348AA962704A2DA3C83B8BAD2BCB2EFB4A69AB4ADDA9E272A11F50D8C5E9  coffee_brewing_terminology_qc_2026-08-06.json
56AD15814E02D5BC6A6F33F0428D4F78BC878BD85724F258757CAA5EABA89E6F  coffee_brewing_terminology_records_2026-08-06.jsonl
A27A2CB73D913BD533EF089F9E516ED70764364E2C98E71D74427DAF7A3F966B  coffee_brewing_terminology_report_2026-08-06.md
CFCC6DA8A832BC1C78CECFBA55D4561C92334935B384BF8718F747193E3D2DA7  coffee_brewing_terminology_sources_2026-08-06.jsonl
```

The application uses locale `zh`; the research uses `zh-CN`. The integration
generator owns that explicit mapping. It also maps research concepts
`coffee_grounds` and `steeping_immersion` to canonical app IDs `grounds` and
`steep_immersion`. No other implicit ID transformation is permitted.
