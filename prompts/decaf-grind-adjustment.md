# Research prompt: Decaf coffee grind adjustment vs caffeinated

## Context
I'm building a brewing assistant Android app that recommends grind size based
on grinder + brew method + filter type. I want to add a **decaf adjustment
rule** that automatically nudges the grind for decaffeinated beans.

The app currently uses a 3-branch heuristic based on the bean's roast level:

- **LIGHT / CINNAMON / FILTER roast decaf** → grind **3 steps finer** than
  the equivalent caffeinated recommendation
- **MEDIUM / MEDIUM_LIGHT / MEDIUM_DARK / OMNIROAST decaf** → **2 steps finer**
- **DARK / ESPRESSO decaf** → **1 step finer**

Where "step" = the grinder's `adjustmentStepSize` (e.g. 1 click on a Comandante
C40, 0.2 numbers on a 1Zpresso ZP6, 1 dot on a Fellow Ode Gen 2).

The rule was derived from community consensus (Barista Hustle posts, r/coffee
threads) but is **not yet grounded in primary sources**.

## What I want
A sourced critique of this heuristic answering:

1. **Is the direction correct?** (decaf needs finer grind, true/false)
2. **Why?** Cell-wall mechanics — what does the decaffeination process
   (Swiss Water / sugar-cane EA / supercritical CO₂ / direct-solvent KVW)
   actually do to bean structure that affects extraction rate?
3. **Is the magnitude right?** Is "2–3 steps finer" reasonable for filter
   methods? Should the adjustment differ for espresso vs filter?
4. **Does roast level actually moderate the effect** the way the heuristic
   assumes (lighter = bigger adjustment)? Or is roast-level a red herring
   and decaffeination method matters more?
5. **Are there other variables** that should override the rule
   (e.g. age since roast, decaf method on the bag if disclosed)?

## Search angles to try
The previous attempt failed because chemistry-flavoured queries were
moderation-flagged. Try these phrasings instead:

- "decaf coffee extraction rate compared to caffeinated"
- "decaf grind adjustment barista guide"
- "Swiss Water decaf brewing"
- "decaffeination effect on coffee bean cell structure"
- "decaf espresso recipe vs regular"
- "EA decaf vs CO2 decaf cup difference"
- "barista hustle decaf grind"
- "James Hoffmann decaf"

## Preferred sources (in order)
1. Barista Hustle articles (baristahustle.com)
2. Coffee Ad Astra blog (jonathangagne.com / coffeeadastra.com — physics-based)
3. Specialty Coffee Association papers (sca.coffee)
4. Scott Rao writing
5. James Hoffmann YouTube + book
6. Roaster blog posts (Sweet Maria's, Onyx, Square Mile, Tim Wendelboe)
7. Decaffeinator-direct content (Swiss Water Process site,
   Descafecol / CR3 EA process explainers)
8. Reddit r/coffee top-voted threads on decaf brewing

## Anti-patterns
- Don't trust generic "decaf brews differently" hand-waves without a mechanism.
- Don't conflate the four decaf methods — they have different effects.
- Don't assume a single rule works for both filter and espresso without
  verifying.
- If the evidence says "no meaningful adjustment needed, just brew normally
  and dial in by taste", report that — that's a valid finding.

## Output format
```
DIRECTION: <finer | same | coarser> — <confidence>
MECHANISM: <2–3 sentences on why decaf extracts differently>
MAGNITUDE BY METHOD:
  - filter: <X steps finer | no change | varies>
  - espresso: <X steps finer | no change | varies>
ROAST-LEVEL MODERATION: <yes/no — does roast change the adjustment, with reasoning>
DECAF-METHOD MODERATION: <do Swiss Water / EA / CO2 / KVW differ in cup behavior?>
RECOMMENDED RULE: <single concise rule the app should use, or 2-branch / 3-branch alternative>
SOURCES:
- <url> — <quote or summary of relevant claim>
- <url> — ...
CONFIDENCE: <high | medium | low>
```
