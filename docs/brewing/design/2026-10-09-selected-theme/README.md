# Selected brewing theme — state gallery

46 representative screens on 16 boards, plus the refined baseline. Generated with the built-in image generator, 2026-10-09, from the user's selected Reading order theme and the existing bloom animation atlas. This is a static design proposal; app code is unchanged.

[Open the browsable gallery](gallery.html) · [State coverage and shared variants](STATE_MATRIX.md) · [Exact initial prompts](state-prompts.json)

The footer anchors the current brew action above a separate two-line background session row. Dose and relevant recipe quantities stay visible; guide actions report physical work, while basic timers remain light. Bloom identity is retained after bloom and omitted from methods without bloom.

## Refined baseline

![Refined basic timer](theme-baseline-v2.png)

## Boards

### Preparation

G01 — Prime filter; G02 — Dose and tare; B01 — Basic ready.

![Preparation](01-preparation.png)

### Guided bloom

G03 — Pour bloom; G04 — Close and swirl; G05 — Wait for bloom.

![Guided bloom](02-bloom-v2.png)

### Current target and drawdown

G06 — Bloom cue reached; G07 — Main pour; G08 — Drawdown.

![Current target and drawdown](03-pouring-v2.png)

### Serving and completion

G09 — Safe serving; G10 — Completed.

![Serving and completion](04-completion.png)

### Unfinished work remains visible

L01 — Bloom pour unfinished; L02 — Main pour slower; L03 — Drawdown slower.

![Unfinished work remains visible](05-slower-brewing-v3.png)

### Basic recipe timers

B02 — Basic during bloom; B03 — Basic after bloom; B04 — Basic ended.

![Basic recipe timers](06-basic-timing-v2.png)

### Cold timer-only start origins

C01 — Start now; C02 — Started earlier; C03 — Start unknown.

![Cold timer-only start origins](07-cold-start-v2.png)

### Guided cold preparation

C04 — Clean and prepare; C05 — Combine and cover.

![Guided cold preparation](08-cold-preparation-v2.png)

### Steep, reminder and filtration

C06 — Steeping; C07 — Reminder reached; C08 — Filtering.

![Steep, reminder and filtration](09-cold-progress-v2.png)

### Cold serving and cleanup

C09 — Dilute serving; C10 — Clean up; C11 — Cold completed.

![Cold serving and cleanup](10-cold-finish.png)

### Independent concurrent sessions

X01 — One background brew; X02 — Choose another brew; X03 — Other reminder reached.

![Independent concurrent sessions](11-concurrent-v3.png)

### Help, recipe and paused guidance

H01 — Contextual help; H02 — Read recipe ahead; H03 — Guide paused.

![Help, recipe and paused guidance](12-reading-v2.png)

### Reminder controls and limitations

R01 — Edit reminder; R02 — Precise reminder off; R03 — Notifications denied.

![Reminder controls and limitations](13-reminders-v2.png)

### Coherent recoverable states

E01 — Guide unavailable, timer valid; E02 — Confirmation pending; E03 — Confirmation failed.

![Coherent recoverable states](14-recovery-v2.png)

### Restoration and cancellation

S01 — Restoring; S02 — Saved session unavailable; S03 — Cancel selected brew.

![Restoration and cancellation](15-session-handling-v2.png)

### Methods without bloom

N01 — Immersion; N02 — Gentle pressing; N03 — Beverage yield.

![Methods without bloom](16-without-bloom-v2.png)

## Review and verification

Astra independently reviewed all original boards and targeted revisions against the user's full task history. Corrections cover the immediate first-drip valve cue, above-bed water-height reference, cold start and filtration timing, water roles, foreground-only cancellation, and retrying a report without repeating a pour. See [review notes](ASTRA_REVIEW.md).

The gallery and manifest select final revisions explicitly. Exact prompts, amendments, correction prompts and generated-source records retain first-attempt provenance; superseded workspace PNG copies can be recovered from their original generator paths. The source atlas is authoritative; generated bloom poses are approximations rather than replacement assets.

Static image review does not verify touch targets, text scaling, scroll behavior, animation or Android reminder delivery. Shared states and method variants are indexed in STATE_MATRIX.md; this is not every combination of numeric timer values, source recipe text, permissions and device layout. No app behavior is implemented or approved here.
