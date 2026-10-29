# Verification

The full source-reviewed Pulsar example is captured at normal text and local 200% text at 320dp. All 22 final images are untouched 1080×2424 Compose-root captures. The same frozen clock/amounts and real screen/reducer transitions are used before and after. The test uses an in-memory session and synthetic completion reference; it does not establish persistence or real-time brewing.

33 unique focused JVM cases passed across valid scoped runs: 9 clock/bloom, 16 guidance/target/source-warning, 5 action-selection and 3 Help-projection cases. Final Detekt and isolated app/test APK builds passed.

19 unique affected native cases have passing results on the existing API37 REL emulator: 2 full-flow, 8 clock/target/accessibility, 6 full/partial/missing-guidance and 3 basic/cold timer cases. The first batch passed 18 of 19; the stricter full-flow viewport assertion caught a duplicate wait panel in the large bloom-wait state. That panel was removed only when the live countdown depicts the same boundary. Both full flows then passed again, including all live-text and target visibility checks. The 17 unaffected cases were reused; this is not claimed as a single fresh 19-case batch.

Known routine Pulsar notes move to Help only for exact reviewed content IDs, expected actions, profile and warning revisions. The lifting cue remains at removal. Unknown/revised/custom warnings and targets, all typed non-ADVICE warnings, refrigeration, real inputs and recovery controls remain. Full source notes and references are unchanged in Help.

Shared-device windows were coordinated with Tracker and returned early. Font 1.0, physical 1080×2424, density 420 and orientation 1/0 stayed unchanged. No AVD restart, data clear or other-app change was performed. Physical-device brewing, a full TalkBack traversal and exhaustive localization were not repeated.

Independent Astra review inspected the final source and all 22 normal/200% frames. It found no remaining blockers and recommended commit and local main integration. Full TalkBack traversal, physical brewing and motion quality remain outside that review’s evidence.
