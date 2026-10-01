# High value AI opportunities for Starlit Coffee

Explored on 2026-10-01 after the [AI and telemetry integration audit](2026-10-01-ai-telemetry-integration-audit.md). This is a product proposal grounded in current source and primary research. Expected value and frequency below are hypotheses, not observed adoption or proven brewing outcomes. No feature implementation is authorized by this document.

The strongest new opportunity is helping someone choose one useful next-brew experiment from their own feedback. The most immediately grounded opportunity is making existing label capture require fewer corrections and repeated photos. Both fit the current product: contextual assistance, a short review, and an ordinary brewing flow that continues without AI.

## Priorities

| Opportunity | Expected user value | Likely scenario | Readiness and cost | Recommendation |
| --- | --- | --- | --- | --- |
| Better completion of uncertain label fields | Less typing and fewer repeat scans when adding coffee | Occasional for most users; recurring for people changing bags often | Existing extraction, evidence and corpus; medium effort, dependent on audit fixes | First improvement to existing AI |
| Suggest taste tags from nuanced notes | Reduce manual tag selection while retaining the original observation | Recurring for users who already write notes and tag brews | Existing notes, rating and flavor tags; medium effort, with feedback-schema limitations | Prototype interpretation offline, then test contextual suggestions |
| One next-brew experiment | Reduce aimless adjustments and help repeat a preferred cup | Recurring while dialing in the same coffee | Rich recipe snapshots, but uneven observations; higher effort | Highest potential new brewing value, staged behind data quality |
| Import recipe settings from text or a photo | Avoid manually transcribing brewing instructions | Occasional, especially for recipe enthusiasts | Existing recipe persistence; new import/review flow, medium to higher effort | Strong second independent feature |
| Find and compare past brews | Recover a successful setup or understand personal preferences | Occasional; becomes useful with a substantial history | Structured log queries exist; basic search comes first, medium effort for semantic search | Conditional on history-search evidence |
| Clarify a specific guide question | Help a novice understand a step in their own context | Occasional | Authored guidance already covers much of this; source retrieval and grounding add cost | Lower priority; only for demonstrated unanswered questions |

“High value” means improving task completion, correction effort, or the user's next decision. It does not mean increasing AI usage or generating more text.

## Better label capture

The next step for scanning should be preserving what is already reliable and resolving only what matters. A useful review might retain all confirmed values and offer “Photograph the roast-date label” when that date is important and unreadable. Missing optional farm or altitude details should not create another task.

The app already has field candidates, evidence, image-quality handling, and real/synthetic corpus scoring. However, the text pass currently requests all 14 fields (`BagPhotoExtractor.kt:1116`), and the OCR adapter drops SDK line confidence, rotated geometry and orientation (`MindlayerOcrService.kt:189-205`). The model's `found` status also becomes HIGH confidence (`MindlayerLlmInferenceProvider.kt:1273-1276`). Neither that self-report nor nullable OCR confidence establishes calibrated reliability.

OCR and deterministic logic should handle dates, units, known vocabulary, glare/blur feedback, agreement and contradictions. AI earns its cost on unusual multilingual wording, visually ambiguous labels, and reconciliation that the simpler path cannot resolve. Use source regions and measured reliability rather than invented confidence percentages. Preserve user edits and keep source text available for review.

Compare the improvement with the current pipeline on held-out real front/back labels, languages, reflective packaging, blur and missing fields. Measure manual corrections, incorrect accepted fields, repeated captures, model calls, time to a usable draft and abandonment. Keep it only if it reduces work without increasing incorrect dates, weights or decaf status. The existing benchmark's permissive `SHIP` threshold is not sufficient justification for bypassing review of important fields.

## Taste notes and one next-brew experiment

A short note can carry more useful information than one complaint category: “Pleasant acidity, but too thin and a little drying.” The value of AI is preserving those separate observations and interpreting paraphrases, negation and multilingual wording. It must keep the original note and the user's rating; it should not infer a rating or treat liking acidity as complaining about sourness.

Prototype interpretation offline first; summarizing a short note or asking someone to review it again is not sufficient product value. If it succeeds, use it behind an existing task: suggest a few taste tags or support a requested next-brew experiment. `FlavorTagEntity` already associates descriptors with individual brews, and `FlavorDescriptor` offers 15 existing categories; paraphrased flavor suggestions can reuse those rather than creating another vocabulary. The original note remains the evidence. No new permanent mode or required questions are needed. Ordinary ratings and notes continue immediately when the model is absent or slow. Keyboard dictation can initially supply text, but its processing/privacy depends on the keyboard; it is not automatically an offline Mindlayer feature.

Two current data issues precede personalization. `TasteIssue.kt:15-16` stores “Too weak” as TOO_SOUR and “Too strong” as TOO_BITTER. The original distinction is lost and cannot reliably be recovered from those legacy values alone. Also, legacy `BrewViewModel.buildBrewLogEntity` records a recommended grinder range (`837-840`), rather than proof of the actual setting used. Newer versioned recipe snapshots preserve grinder identity, planned temperature and recipe semantics, but planned values must still be distinguished from observed values. Existing flavor tags support multiple categorical descriptors; separate strength, acidity, body and compound observations lack a dedicated representation beyond the note and single taste-feedback string. Persisting that richer feedback requires an explicit schema decision.

The app already provides rule-based coaching in `HomeContextCard.resolveCoachingTip` using the last five rated brews across contexts (`187-235`). First improve that baseline with same-bag and compatible-equipment selection and faithful feedback. Comparable brews require compatible brewer, recipe semantics, filter and grinder, plus separation of actual observations from planned values. Unknown actual grind settings or mixed contexts should prevent numeric personalization; a generic qualitative suggestion should be labelled as such. AI should interpret nuanced notes and select among constrained experiment templates only when it adds something beyond those rules. Deterministic code owns comparison, units, grinder compatibility, calculations and allowed adjustments.

An illustrative result is “Try one slightly stronger-ratio experiment; keep the other settings.” Show its basis using the actual source brew and let the user preview the change to a personal setup. It is a hypothesis to test, not a diagnosis or guarantee. It must not rewrite an authored exact recipe, invent grinder click conversions, assume a measured extraction yield, or silently change a favorite. If actual settings or comparable history are missing, give a limited qualitative suggestion or retain the existing guidance.

Research supports respecting individual preferences and separating observations from causal claims. A controlled study of 118 black-coffee consumers found different preference groups across strength and extraction; its medium-roast drip setup and participant population limit generalization. That supports the rationale for personalization, not proof that an AI coach improves cups. [Cotter et al., 2021](https://ift.onlinelibrary.wiley.com/doi/10.1111/1750-3841.15561).

Evaluate note interpretation on an explicitly reviewed multilingual corpus, including “not bitter,” “bright acidity that I like,” and mixed strength/mouthfeel observations. Compare with glossary matching and existing controls for attribute precision, invented/omitted observations, correction time and taps. Then compare constrained suggestions with the improved deterministic baseline for supported adjustments, factual use of history, and the number of comparable attempts before a user likes the cup. Better/equal/worse next-cup feedback is useful, but ordinary observational logs do not prove causality: age, water, technique and serving conditions can change too.

Remove or simplify the model if it mostly restates existing tips, requires more work than direct feedback, or produces unsupported adjustments. Personalization should initially use retrieval and explicit observations; a model-training pipeline or custom fine-tune is not necessary for the first useful version.

## Recipe settings import

“Use these brewing instructions” is a clear transformation task. Begin with pasted text or a photo/screenshot while the user has already selected a brewer. Read dose, water, temperature, ratio and source notes into a compact editable draft alongside the original. It could save substantial transcription effort for someone collecting roaster recipes.

Current `SavedRecipeEntity`, `BrewRecipeSnapshotV1` and `RecipeRepository.insertVersionedRecipe` provide useful persistence foundations. Ratio, temperature, time and source semantics already exist. A user-facing recipe-import route was not found, so this entails a new contextual action and review surface. Start with settings import; compiling arbitrary timed instructions into executable stage plans is a substantially larger project.

Use OCR for printed text and short structured inference to interpret prose. Deterministic code validates supported brewer/equipment, converts units, calculates derived ratios and rejects unsupported combinations. Missing quantities remain missing. Preserve water input versus beverage yield and cumulative versus added pour amounts. Every saved number must come from the source or a clearly identified calculation; imported instructions are data, not authority to change app behavior. Record imports as user-provided sources without fabricating the trusted provenance of built-in exact recipes.

Measure correction effort and time against manual entry. Evaluate °F/°C, decimal punctuation, hot water versus ice/bypass water, cumulative pours, incomplete text and conflicting values. Manual calculator/brewer setup remains the fallback. Keep import only if it saves work after review while reliably preserving quantities and source meaning. Unsupported stage plans should remain source notes rather than becoming timers by inference.

## Finding what worked in history

Two useful requests are “Find that fruity filter coffee I liked last month” and “What worked for this coffee?” The output should be real brew cards or a short factual comparison with links to the relevant brews. It can reduce searching and help someone return to a preferred setup.

The log already stores bag/recipe links, ratings, notes, dose, water, ratio, time and versioned recipe context; `BrewLogDao.getByBag`/`getByRecipe` support retrieval. The current log UI offers limited filtering. Ordinary text search, date/method filters, deterministic rating ranking and a comparison table should establish a useful baseline first. “Repeat my best-rated recipe” alone does not need generative AI.

AI can translate an ambiguous request into an allowlisted filter object or rank semantically similar notes. App code resolves dates and applies exact filters; it should not execute generated SQL. Embeddings are a possible improvement only when lexical search fails on meaningfully different phrasing. They require advertised capability, an installed embedding model, model/dimension identity, and correct indexing after edits and deletion. For small histories, those costs may outweigh the value.

A narrative can say “You described these two longer brews as sweeter,” if both notes support that statement. It should not say duration caused sweetness. Compare lexical search, structured filtering and semantic retrieval on annotated queries for relevance and time to find the intended brew. Measure comparison-table usefulness before adding a narrative. Remove AI if ordinary filters work equally well, relevant history is sparse, or explanations repeatedly overstate patterns.

## Specific learning clarification

The existing learning surface already includes authored instructions, warnings, completion cues and expandable details (`LearnBrewerScreen.kt:278-343`). Better authored text and a glossary may cover most confusion. AI's remaining opportunity is adapting a short explanation to a specific question and the selected equipment, using the approved guide as its evidence.

If observed questions justify it, place “Explain this step” within expanded details, outside the active timing path. Keep quantities, timing, temperature and safety facts fixed to the source. A failed or unsupported answer returns to the original guide. Test comprehension and source support against an authored FAQ; omit the model when ordinary explanations solve the same problem with less delay.

## Shared product and technical constraints

All candidates should be discoverable within their existing task and optional. Use concise previews, correction and dismissal, and preserve user state during cancellation. Help should become more limited when uncertain. These choices align with the evaluated [Guidelines for Human-AI Interaction](https://www.microsoft.com/en-us/research/publication/guidelines-for-human-ai-interaction/); those guidelines do not establish the effectiveness of these particular features. Source links, edits and changes must work with TalkBack and large text; announce meaningful status transitions and make speech input optional.

No permanent new AI toggle is justified for individual experiments. Reuse service authorization and availability infrastructure where appropriate, while making each contextual action's use of private notes or history apparent and keeping explicit control over retained profiles. Enabling label recognition does not automatically authorize these new data uses. The app supports Android 26+, but its current Mindlayer path requires Android 31+; rules/manual behavior must remain useful on older or unsupported devices.

Use compact dedicated requests containing the relevant note, selected source fragments and a bounded set of comparable brews. Keep calculations outside the model. Measure cold/warm latency, cancellation, typed failure recovery, physical-device memory and energy. Model or SDK support does not prove a task meets interactive performance requirements.

Custom voice recording is a possible later input improvement. Mindlayer advertises audio capabilities and a single-clip transcription path, but Starlit has no microphone permission or recording workflow. Google's model documentation establishes audio understanding capability, not end-to-end reliability on this app's installed model/runtime. Add that path only if typed or keyboard-dictated notes first prove valuable and local audio is separately qualified. [Gemma audio documentation](https://ai.google.dev/gemma/docs/capabilities/audio).

The audited privacy, cancellation, availability and telemetry gaps should be repaired before sending additional notes or history through inference. Evaluate with local bounded metadata: model/prompt version, task outcome, correction/skipped status, source-support checks, time and resource observations. Raw ground truth belongs in an explicitly reviewed corpus. An unchanged field or an accepted suggestion is not proof of accuracy or better coffee.

## Uses that do not yet justify AI

Dose/water arithmetic, amount scaling, freshness/stock reminders, exact barcode matching, grinder unit conversion, sorting by rating and repeating a known setup should remain deterministic. Their existing inputs already support predictable outcomes.

A general coffee chat surface, automatically invented recipes, image-only taste/extraction diagnosis, and automatic generated ratings have weaker evidence of benefit and much higher ambiguity. They should not displace the contextual opportunities above. Predicting freshness precisely from a label or photograph also lacks the observations needed to justify such precision.

## Suggested first exploration

First repair the integration audit's privacy and recovery issues. In parallel, define small reviewed evaluation sets for targeted label completion and nuanced taste notes. Establish the simpler baseline for each before changing product code.

The first technical prototype should interpret a short taste note into bounded observations while preserving its original text and rating. If it retains meaning, test whether tag suggestions or one constrained next-brew experiment save effort or improve decisions for comparable cups. Interpretation alone should not become an extra review workflow. Treat recipe settings import as an independent next candidate. Only expand history search or guide explanation when actual use shows the simpler surfaces are insufficient.

This exploration inspected current source and research. It did not run inference, conduct a user study, establish effectiveness, or change product behavior.
