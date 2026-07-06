---
applyTo: "app/src/main/res/values*/strings.xml"
description: "How to translate user-facing strings for Starlit Coffee (multi-language)"
---

# Translation Guide

Starlit Coffee ships in **23 languages**. English (`values/strings.xml`) is the source of truth; every other locale MUST have an identical `<string>` key set at all times.

| Language | Locale | Directory |
|---|---|---|
| English (source) | en | `values/` |
| Bulgarian | bg | `values-bg/` |
| Czech | cs | `values-cs/` |
| Danish | da | `values-da/` |
| German | de | `values-de/` |
| Greek | el | `values-el/` |
| Spanish | es | `values-es/` |
| Estonian | et | `values-et/` |
| Finnish | fi | `values-fi/` |
| French | fr | `values-fr/` |
| Croatian | hr | `values-hr/` |
| Hungarian | hu | `values-hu/` |
| Italian | it | `values-it/` |
| Lithuanian | lt | `values-lt/` |
| Latvian | lv | `values-lv/` |
| Dutch | nl | `values-nl/` |
| Polish | pl | `values-pl/` |
| Portuguese | pt | `values-pt/` |
| Romanian | ro | `values-ro/` |
| Slovak | sk | `values-sk/` |
| Slovenian | sl | `values-sl/` |
| Swedish | sv | `values-sv/` |
| Chinese (Simplified) | zh | `values-zh/` |

The EU-official locales cover the 22 "common" EU languages (all 24 minus Irish `ga` and Maltese `mt`); Chinese `zh` is the sole non-EU locale.

The supported locales are also declared in `res/xml/locales_config.xml` (referenced by `android:localeConfig` in the manifest), which drives the Android 13+ per-app language picker. **When adding or removing a locale, update both that file and this table.**

The Czech-specific glossary and conventions below remain the canonical worked example; apply the same affordance-aware philosophy (and each language's own coffee-community vocabulary and CLDR plural rules) to every locale.

## Core Principle

**Translate meaning and affordance, not words.** A literal 1:1 translation almost always reads wrong in the target language. Always check:

1. **Where does the string appear?** (Button label? Section header? Dialog body? Toast? `contentDescription`?) — this dictates grammar and register.
2. **What does the user need to do/understand here?** — copy that intent into the target language's natural phrasing, even if it uses different words or word order.
3. **What do native speakers in the coffee community actually say?** — not what a dictionary or machine translator outputs.

## Adding a New String

1. Add the key to `values/strings.xml` (English source) **and to every other `values-<locale>/strings.xml`** in the same logical section (use the `<!-- Section -->` comments). Never leave a locale missing a key.
2. Verify keys stay aligned across **all** locales (each `Compare-Object` must return nothing):
   ```powershell
   $en = (Select-Xml -Path app\src\main\res\values\strings.xml -XPath "//string").Node.name | Sort-Object
   foreach ($loc in 'bg','cs','da','de','el','es','et','fi','fr','hr','hu','it','lt','lv','nl','pl','pt','ro','sk','sl','sv','zh') {
       $tgt = (Select-Xml -Path "app\src\main\res\values-$loc\strings.xml" -XPath "//string").Node.name | Sort-Object
       $diff = Compare-Object $en $tgt
       if ($diff) { Write-Host "$loc MISMATCH"; $diff } else { Write-Host "$loc OK" }
   }
   ```
3. For `<plurals>`, use the correct CLDR categories per language: `one`/`other` for bg, da, de, el, es, et, fi, fr, hu, it, nl, pt, sv; `one`/`few`/`other` for hr, lt, ro; `zero`/`one`/`other` for lv; `one`/`two`/`few`/`other` for sl; `one`/`few`/`many`/`other` for cs, pl, sk; `other` only for zh.
4. Use `snake_case` keys with an affordance prefix:
   - `action_*` — button labels, menu actions
   - `label_*` — field labels, section titles
   - `msg_*` — full-sentence microcopy / body text
   - `screen_*` — screen titles
   - `format_*` — strings with `%1$s`/`%1$d`/`%1$.0f` placeholders
   - `instruction_*` — imperative step text (timer, live scan, etc.)
   - `phase_*` — brew phase names
   - `adjust_*` — taste-feedback adjustment advice
   - `cd_*` — `contentDescription` for accessibility
   - `warning_*` / `format_warning_*` — guardrail messages

## Affordance → Grammar Mapping

| Surface | English | Czech |
|---|---|---|
| Button (action) | Imperative / short verb: `Save`, `Cancel`, `Start brew` | Infinitive: `Uložit`, `Zrušit`, `Spustit přípravu` |
| Section header | Title case noun phrase: `Grind intelligence` | Noun phrase, sentence case: `Chytré mletí` |
| Dialog title | Noun phrase or question: `Discard scan?` | Matching: `Zahodit sken?` |
| Dialog body | Full sentence: `You have detected fields…` | Full sentence, formal: `Máte zjištěná pole…` |
| Confirmation | Question: `Delete this brew?` | Question: `Opravdu smazat tuto přípravu?` |
| Microcopy / supporting text | Full sentence with period | Full sentence with period |
| Toggle / switch label | Noun phrase | Noun phrase |
| Empty state | Short encouraging sentence | Short encouraging sentence |
| Toast / snackbar | Short present-tense statement, no period | Short statement, no period |
| `contentDescription` | Verb or noun describing action/content | Same — kept brief |

## Czech Conventions

- **Register**: formal "vy" form everywhere. No second-person singular ("ty").
- **Punctuation**: Czech quotation marks `„…"` in body copy; straight quotes in UI labels are acceptable. Em dash `—` for parenthetical asides (same as EN).
- **Numbers**: keep Android format placeholders identical to EN (`%1$.0f`, `%1$d`). You MAY reorder (`%2$s %1$s`) if Czech word order demands it — verify numbering is correct.
- **Declension**: loanwords decline in Czech. E.g. `bloom` → `bloomu` (gen./loc.), `při bloomu` (during bloom), `násobitel bloomu` (multiplier of bloom). Don't leave uninflected loanwords mid-sentence.
- **Gender agreement**: when you change the noun (e.g. bag: `sáček` m. → `balení` n.), update every agreeing adjective (`zapečetěný` → `zapečetěné`) and verb in its scope.
- **XML escaping**: `'` → `\'`, `&` → `&amp;`, `"` → `\"` (or wrap the whole value in `"…"`), `\n` for newline.

## Coffee Terminology Glossary

The Czech speciality-coffee community mixes native Czech terms with English loanwords. This table reflects **actual current usage** (Doubleshot, Nordbeans, Father's Coffee, CAFFé08, Kofio, La Boheme):

| English | Czech — use this | Avoid |
|---|---|---|
| bloom | **bloom** (loanword; decline: bloomu, při bloomu) | `předsmáčení` (too clinical; readers will read "bloom" on every roaster site they visit) |
| dose | **dávka** | ~~porce~~ |
| ratio | **poměr** | ~~index~~ |
| grind (noun) | **mletí** | |
| grind (verb) | **namlít** / **semlít** | |
| grinder | **mlýnek** | ~~mlecí stroj~~ |
| brew (verb) | **uvařit** / **připravit** | ~~vyrobit~~ |
| brew (noun — the drink) | **káva** / **nápoj**; for batch: **várka** | |
| brew (noun — the extraction) | **příprava** | |
| recipe | **recept** | |
| filter (paper) | **filtr** | |
| coffee bed | **lůžko** (kávy) / **vrstva kávy** | |
| pour (verb) | **nalévat** | |
| pour (noun, one pour) | **nálev** | |
| swirl | **zatočit** / **zakroužit** (the kettle/dripper) | |
| kettle | **konvice** | |
| valve | **ventil** | |
| drawdown | **drawdown** (loanword) or **průtok** | |
| preinfusion | **preinfuze** (loanword) | |
| cup | **šálek** | |
| bean(s) | **zrno** (sg.), **zrna** (pl.) | |
| coffee bag (retail) | **balení** (neuter — unified across app) | Mixing `sáček` / `balíček` / `balení`. `sáček` is also valid but we chose `balení` for consistency. |
| origin | **původ** | |
| roaster (company) | **pražírna** | |
| roast (level) | **pražení**; světlé / střední / tmavé pražení | |
| process (washed/natural) | **zpracování**: praná (washed), natural, honey | |
| tasting notes | **chuťové poznámky** or **chuťový profil** | ~~ochutnávkové poznámky~~ (literal calque, unnatural) |
| decaf (adj., consumer) | **bezkofeinová** | |
| decaf (adj., formal) | **dekofeinizovaná** | spelling is **dekofeinizace** (root `kofein`), NOT `dekafeinizace` |
| decaf (noun, hip cafe) | **decaf** (loanword) | |
| freshness / roasted on | **čerstvost** / **datum pražení** | |
| flavor / flavour | EN uses US spelling: `flavor` | |

## When to Keep English as a Loanword

Keep the English word in Czech when **all** of these are true:
1. It's a technical specialty-coffee term used broadly by Czech baristas (check any Czech roaster blog).
2. A native Czech alternative exists but is either rare, clinical, or inconsistent across sources.
3. The loanword declines naturally in Czech (bloom → bloomu; drawdown → drawdownu).

Examples: `bloom`, `drawdown`, `preinfusion`, `bypass`, `decaf`, `V60`, `AeroPress`, `Moka Pot`, `Pulsar` (brand name — always keep).

## When a String Can't Be Translated Well

Some strings resist a good translation because EN and CS make different distinctions:
- EN `Back` (toolbar) vs `Go back` (body) → CS collapses both to `Zpět`. Acceptable.
- EN `Close` vs `Dismiss` → CS collapses to `Zavřít`. Acceptable.
- Proper nouns (region/variety/origin names: `Yirgacheffe`, `Bourbon`, `Gesha`) — leave untranslated in both languages.

If you hit a collision that actually confuses users (two different screens rendering the same title, etc.), split the key and translate each contextually.

## Validation Checklist

Before committing translation changes:

- [ ] Every `values-<locale>/strings.xml` has the same `<string>` keys as `values/strings.xml` (`Compare-Object` returns nothing for all locales).
- [ ] `.\gradlew.bat assembleDebug` passes (no XML syntax errors, no missing references).
- [ ] New loanwords in Czech are declined properly in mid-sentence contexts.
- [ ] Button labels are infinitive verbs in CS.
- [ ] Section headers are noun phrases in CS.
- [ ] Gender/number agreement is consistent with the chosen nouns.
- [ ] `%` format specifiers are identical in EN and CS (indices may reorder with `%1$`/`%2$`).

## Example — Good Translation

```xml
<!-- EN -->
<string name="action_start_bloom">☕ Start bloom</string>
<string name="format_pour_bloom_water">Pour %1$.0fg bloom water</string>
<string name="label_bloom_multiplier">Bloom multiplier (e.g. 3.0)</string>

<!-- CS (good — loanword, declined, matches affordance) -->
<string name="action_start_bloom">☕ Začít bloom</string>
<string name="format_pour_bloom_water">Nalijte %1$.0fg vody pro bloom</string>
<string name="label_bloom_multiplier">Násobitel bloomu (např. 3.0)</string>
```

## Example — Bad Translation to Avoid

```xml
<!-- Bad: "Adjust weight" is a button. CS "Úprava hmotnosti" is a noun phrase ("Weight adjustment") — wrong affordance. -->
<string name="action_adjust_weight">Úprava hmotnosti</string>
<!-- Good: imperative-infinitive -->
<string name="action_adjust_weight">Upravit hmotnost</string>
```

```xml
<!-- Bad: literal calque -->
<string name="label_tasting_notes">Ochutnávkové poznámky</string>
<!-- Good: what Czech roasters actually write -->
<string name="label_tasting_notes">Chuťové poznámky</string>
```
