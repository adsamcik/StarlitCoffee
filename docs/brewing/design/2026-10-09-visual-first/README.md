# Visual-first brewing gallery

46 states across 16 boards, with shorter copy and functional visuals. Generated with the built-in image generator from the selected theme. Static proposals; app code is unchanged.

[Browse gallery](gallery.html) · [Design rules and coverage](DESIGN_NOTES.md) · [Exact prompts](state-prompts.json)

## Basic and guided comparison

![Basic timer and guided pour](01-pilot-v4.png)

## Preparation

G01 — Prime filter; G02 — Dose and tare; B01 — Basic ready.

![Preparation](01-preparation-v2.png)

## Guided bloom

G03 — Pour bloom; G04 — Close and swirl; G05 — Wait for bloom.

![Guided bloom](02-bloom-v2.png)

## Current target and drawdown

G06 — Bloom cue reached; G07 — Main pour; G08 — Drawdown.

![Current target and drawdown](03-pouring-v2.png)

## Serving and completion

G09 — Safe serving; G10 — Completed.

![Serving and completion](04-completion-v2.png)

## Unfinished work remains visible

L01 — Bloom pour unfinished; L02 — Main pour slower; L03 — Drawdown slower.

![Unfinished work remains visible](05-slower-brewing-v3.png)

## Basic recipe timers

B02 — Basic during bloom; B03 — Basic after bloom; B04 — Basic ended.

![Basic recipe timers](06-basic-timing.png)

## Cold timer-only start origins

C01 — Start now; C02 — Started earlier; C03 — Start unknown.

![Cold timer-only start origins](07-cold-start.png)

## Guided cold preparation

C04 — Clean and prepare; C05 — Combine and cover.

![Guided cold preparation](08-cold-preparation.png)

## Steep, reminder and filtration

C06 — Steeping; C07 — Reminder reached; C08 — Filtering.

![Steep, reminder and filtration](09-cold-progress-v2.png)

## Cold serving and cleanup

C09 — Dilute serving; C10 — Clean up; C11 — Cold completed.

![Cold serving and cleanup](10-cold-finish.png)

## Independent concurrent sessions

X01 — One background brew; X02 — Choose another brew; X03 — Other reminder reached.

![Independent concurrent sessions](11-concurrent.png)

## Help, recipe and paused guidance

H01 — Contextual help; H02 — Read recipe ahead; H03 — Guide paused.

![Help, recipe and paused guidance](12-reading-v3.png)

## Reminder controls and limitations

R01 — Edit reminder; R02 — Precise reminder off; R03 — Notifications denied.

![Reminder controls and limitations](13-reminders-v2.png)

## Coherent recoverable states

E01 — Guide unavailable, timer valid; E02 — Confirmation pending; E03 — Confirmation failed.

![Coherent recoverable states](14-recovery.png)

## Restoration and cancellation

S01 — Restoring; S02 — Saved session unavailable; S03 — Cancel selected brew.

![Restoration and cancellation](15-session-handling.png)

## Methods without bloom

N01 — Immersion; N02 — Gentle pressing; N03 — Beverage yield.

![Methods without bloom](16-without-bloom.png)

Astra reviewed the final visuals; see [review notes](ASTRA_REVIEW.md). Manifest records final selections and original generator files, including superseded attempts. Static review does not establish runtime or device behavior.
