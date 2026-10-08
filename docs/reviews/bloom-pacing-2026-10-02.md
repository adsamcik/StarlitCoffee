# Bloom pacing — 2026-10-02

All 44 animations now spend more of the countdown growing stems and closed buds, with the completed pose reserved for countdown completion. The same accepted artwork is retained. The browser comparison plays over 30 seconds instead of the previous 12-second inspection playback.

Astra inspected eight representative current atlases and the exact timing implementation. It approved this change for the assumption that mature forms appear too early. It did not observe live playback; its browser controls were unavailable. The numerical tradeoff is explicit: this delays maturity while traversing the late poses faster relative to the same total countdown. It does not make every petal opening or berry color change uniformly slower.

| Elapsed countdown | Artwork progression | Displayed pose |
| --- | --- | --- |
| 0% | 0% | 1 |
| 10% | 8% | 3 |
| 25% | 16% | 5 |
| 50% | 33% | 9 |
| 75% | 58% | 15 |
| 90% | 83% | 21 |
| 100% | 100% | 25 |

The curve is applied after the existing one-second linear countdown smoothing. Full bloom is an authoritative countdown state, and the renderer uses the target directly on pause, reset and completion. A virtual-clock native test caught a stale reset pose while the animation coroutine was processing its snap; this was fixed and the exact rebuilt APKs were tested again.

## Validation

- Isolated committed baseline plus only the four pacing implementation/test files; debug application and test APK builds passed. Committed source uses LF line endings; its complete Kotlin text matches the tested snapshot after line-ending normalization.
- 19 focused JVM tests passed: seven timing behavior tests and twelve grid tests. Detekt reported zero findings.
- Four instrumentation tests passed on Android 16 / API 36: all 44 animations rendered distinct seed/middle/final stages in both themes; all 88 final previews exactly matched the played finish; Rose's full 25-pose audit passed in both themes; the virtual Compose clock verified pause, restart and countdown completion.
- All 44 atlas bytes match the prior accepted artwork. Installed APK hashes match the tested build. The manifest retains the sandbox setup failure and the initial reset-test failure.
- The frame audit samples an elapsed time for each physical artwork pose. Uniform elapsed sampling would deliberately repeat early poses under this curve, so its readable-growth assertions were retained rather than weakened.

These checks cover pure timing behavior, paused native rendering and virtual-clock interaction. They do not establish real-time playback timing, physical-device behavior or the full unit suite. Continuous progress visits every pose, but short one-second countdown updates can skip late intermediate poses: at 15 seconds the last nonzero countdown reaches pose 22 before snapping to the finish. No claim is made that every pose is displayed for every possible duration.

## Review limits

On a 30-second countdown the later poses last roughly 0.74–0.75 seconds instead of 1.25 seconds. Raspberry's first pink fruit moves from about 20.63 to 24.43 seconds, while ripening through pose 24 contracts from 7.5 to 4.47 seconds. Blackberry and Blueberry have the same late-ripening tradeoff. Coffee brew's larger curls develop earlier in the artwork, so its late acceleration affects finer finishing details. If the concern is specifically rushed opening or color transitions, that requires a further pacing or artwork revision.

Occasional painted-pose seams and small final-pose differences remain as documented in the earlier design review.

[Timing and native evidence manifest](bloom-pacing-2026-10-02.json). [Play the same-artwork comparison](http://127.0.0.1:8765/.qa-screens/bloom-pacing-2026-10-02/index.html).
