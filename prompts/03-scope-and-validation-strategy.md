# Research Prompt: Right-Sizing a Mobile ML Pipeline — Scope, Validation, and Build-vs-Measure

## Context

We've designed a sophisticated intelligent scanning pipeline for a specialty coffee hobby app. The pipeline includes: a 7-state scan state machine, 6-state motion classifier (IMU + optical flow), composite golden frame scoring (6 dimensions), front/back side detection, OCR-based bag detection, per-side LLM budgeting, observability database, and draft→confirmed UX pattern. Total: 13 implementation todos across 3 dependency waves.

**Then an adversarial review demolished it.**

The most devastating critique (from a Claude Opus 4.6 agent that studied our full codebase history):

> "You're designing a system to optimize a pipeline you've never seen work. Every session in your history is fixing **wiring bugs**, not optimizing recognition accuracy. You're 0-for-0 on 'scan a real bag end-to-end and get correct results' — and the response is to plan 13 features across 3 dependency waves?"
>
> "The scanning pipeline has never worked end-to-end on real bags in the wild. You literally have zero evidence that the current (now-fixed) system is insufficient, because it was never functional."

Other critical findings from 10 adversarial agents:
- The 7-state machine duplicates orchestration logic the existing accumulator already handles (3 states would suffice)
- Golden frame weights are "gut feel dressed up as engineering" — untuneable without ground truth data
- Motion classifier solves a hypothetical problem with no evidence users struggle with capture timing
- A static roaster dataset (100-200 entries) is inherently biased and quickly outdated — user's own history is more valuable
- Building a Room-based observability database for a coffee app is disproportionate — SharedPreferences with last 10 JSON strings gives 80% of the value at 5% effort

The counter-proposal: **3 todos, not 13**: (1) pass OCR text to vision LLM, (2) fire LLM proactively on best frame instead of waiting for 5s stall, (3) ground LLM prompt with user's own history data. Test on 20+ real bags. Then decide what to build next.

## Questions for Research

1. **Build-vs-measure in ML product development**: What does the research say about the optimal ratio of building features vs validating assumptions in ML-powered products?
   - Is there a framework for deciding when to stop designing and start measuring?
   - How do successful ML product teams at Google, Apple, Meta handle the tension between "build the vision" and "validate incrementally"?
   - What's the cost of over-engineering before validation vs under-engineering and having to rewrite?
   - How does this apply specifically to on-device ML features where iteration cycles are slow (build → deploy → collect data → retrain)?

2. **Ground truth dataset design for label extraction**: We need to create a validation dataset of real coffee bag photos. What are best practices?
   - How many bags is "enough" for a meaningful accuracy benchmark? (We proposed 50-100, the adversary said 20+)
   - What diversity dimensions matter most? (scripts, packaging material, lighting, distance, angle, bag style)
   - Should ground truth include OCR-only accuracy, LLM-only accuracy, AND combined pipeline accuracy?
   - How to handle subjective fields? (Is "Ethiopian Yirgacheffe Kochere" one origin or three? Is "fruity, citrus" one note or two?)
   - Can we use the LLM itself to help generate ground truth labels (human-in-the-loop verification)?

3. **Minimal viable ML pipeline**: What's the simplest scanning pipeline architecture that delivers a good user experience?
   - The adversary proposed: OCR consensus + 1 proactive LLM call + prompt grounding with user history. Is this genuinely sufficient for specialty coffee bags?
   - What's the marginal value of each additional feature? Research on diminishing returns in ML pipeline components?
   - Are there case studies of mobile scanning apps (CamScanner, Google Lens, Amazon product scanner, Vivino wine scanner) that started simple and added complexity iteratively? What did they add first?
   - What features do users of scanning apps actually value most? Speed? Accuracy? Completeness? Ease of use?

4. **Observability for on-device ML — right-sizing**: What level of observability is appropriate for a consumer mobile app with on-device ML?
   - Firebase Analytics/Crashlytics vs custom Room database vs simple logcat?
   - What metrics actually matter for improving a scanning pipeline? (Is per-frame latency useful, or is session-level success rate sufficient?)
   - How do apps like Google Lens or Apple's text recognition handle telemetry?
   - Privacy implications of logging scan session data (GDPR, images, user content)?
   - What's the simplest "problem report" flow that captures enough data to diagnose scan failures without building a full observability stack?

5. **State machine complexity in mobile apps**: Is there research on optimal state machine size for mobile app features?
   - When does a state machine help vs when does it hurt? (The adversary argued our existing accumulator IS the state machine, and adding another layer creates conflicting control)
   - What's the typical state count for production scanning/camera features in well-known apps?
   - How do teams test complex state machines with time-dependent behavior and multiple input signals?
   - Is there a "complexity budget" concept — N states × M signals × K transitions = complexity score, with a recommended maximum?

## Our Situation Summary
- Coffee hobby app, not enterprise. Hundreds of users, not millions.
- On-device only, no cloud. Privacy-first.
- The LLM integration was literally broken (wiring bugs) until 2 days ago. Never tested end-to-end on real bags.
- We have a sophisticated consensus engine that's well-tested (757 tests) but never validated on real-world coffee bags.
- We have canonical data for origins (30), varieties (50+), processes (10), roast levels (9), flavor descriptors (189).
- Team size: 1 developer + AI assistants.
