# Research Prompt: Optimal LLM Trigger Strategy for On-Device Label Scanning

## Context

We're building a coffee bag label scanning feature in an Android app. The camera runs continuously with ML Kit OCR every 400ms. We have an on-device multimodal LLM (Gemma 4 E2B, 2.3B params via LiteRT-LM) that we can fire 1-2 times per 30-60s scan session due to thermal/power constraints (~5-8W per call, 2-5s latency, S24 Ultra GPU terminates inference under sustained load per arXiv:2603.23640).

**The core problem: When should we fire the LLM for maximum information yield?**

Our current approach fires the LLM as a fallback after OCR consensus stalls for 5 seconds. An adversarial review of our proposed "best-of-window" trigger strategy revealed these critical flaws:

### Flaw 1: We optimize for frame quality, not information yield
Our golden frame scoring formula (blur × exposure × content richness × stability) picks the **sharpest, most text-rich frame**. But after the first LLM call resolves name/roaster/origin, the remaining value is in **hard fields** (tasting notes, process, altitude) that may be on a different side of the bag or in tiny text that scores LOW on our formula. The scoring doesn't incorporate what the LLM already extracted — it has no feedback loop.

### Flaw 2: No per-side budgeting
If the user shows the front twice before flipping to the back, both LLM calls fire on the front. The back (which often has the most detailed information) gets zero LLM analysis.

### Flaw 3: The trigger doesn't account for engine cold start
Gemma 4 E2B engine initialization takes up to 10 seconds. If we don't pre-warm when the scan screen opens, the first trigger fires and then waits 10s for init before inference even starts.

### Flaw 4: Timeout fallback fires on garbage
At 10s timeout, the system fires on the best-available frame even if quality is terrible. A confidently wrong LLM extraction (weight=10) poisons the consensus engine worse than no extraction at all.

## Questions for Research

1. **Information-theoretic trigger**: How should a scanning system decide when to fire an expensive one-shot analysis? Is there research on optimal stopping problems applied to document scanning — where you're accumulating cheap noisy observations (OCR) and must decide when to spend an expensive precise observation (LLM)?

2. **Post-extraction feedback loop**: After the first LLM call resolves some fields, how should the system re-score remaining frames? The scoring objective fundamentally changes: we no longer want "best overall frame" but "frame most likely to resolve the specific unresolved fields." Is there research on adaptive information acquisition where the acquisition function changes based on what's already been learned?

3. **Multi-view capture strategy**: Given a 3D object (bag) with information on multiple faces, and a budget of 1-2 expensive captures, what's the optimal strategy? Should we: (a) fire on front immediately + fire on back when detected, (b) wait to see both sides then fire on the more valuable one, (c) combine both sides' OCR text into a single LLM call? Is there research from robotics/inspection on optimal viewpoint selection under budget constraints?

4. **Pre-warming vs lazy initialization**: What's the best practice for managing expensive model initialization in mobile apps with short-lived features? Pre-warm on screen open (wastes resources if user navigates away) vs lazy init on first trigger (adds 10s latency) vs background warm-up with priority management?

5. **Graceful degradation**: When image quality is consistently poor (dim environment, shaky hands, reflective packaging), is it better to: (a) fire the LLM on a bad frame (it might still extract something), (b) never fire and rely on OCR consensus only, (c) lower expectations and extract fewer fields with higher confidence? What does the research say about LLM performance degradation on low-quality inputs — is it gradual or cliff-edge?

## Our System Details
- Gemma 4 E2B: 2.3B effective params (5.1B total, MoE), multimodal (text+image+audio), 128K context, AA-Omniscience hallucination score -24
- ML Kit OCR: ~0.05s per frame, returns per-symbol confidence + bounding boxes
- Bayesian consensus engine: Levenshtein clustering, temporal decay, vote floors, known-value prior boost
- Fields: name, roaster, origin, region, variety, processType, roastLevel, tastingNotes, altitude, weight, roastDate
- Source weights: OCR=4, QR=6, barcode=8, localMatch=9, LLM=10
