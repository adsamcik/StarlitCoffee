# Research Report: Guided Timer UX for Physical Actions

## Executive Summary
Research validates the "GPS Navigation" hypothesis: **successful guided apps explicitly prompt every physical action**, rather than assuming user knowledge. The most effective UI pattern is **Instruction → Action → Confirmation/Timer**.

For a coffee valve, this means you should **explicitly prompt "Open Valve" and "Close Valve"** as distinct, timed steps. Do not rely on the user remembering to do it.

## 1. Coffee & Cooking Apps (The Direct Precedents)

### Fellow Stagg EKG+ & Acaia Brewguide
*   **Finding:** These apps use a "Guide Mode" that breaks brewing down into granular physical steps.
*   **Key Mechanic:** They do not just show a timer. They display specific text prompts like "Heat water", "Pour 50g", and "Wait". Acaia even requires a physical button press on the scale to confirm a step is done before moving to the next.
*   **Relevance:** **High.** Directly validates that premium coffee tools treat physical actions (pouring, taring) as distinct, guided UI states.
*   **Source:** Official Docs / Reviews (High Confidence)

### Anova (Sous Vide)
*   **Finding:** The app guides users through setup steps that are purely physical: "Attach device", "Fill pot with water", "Place food in bag".
*   **UI Pattern:** Steps are often presented as a checklist or a sequence of screens where the user must tap "Next" to confirm completion.
*   **Relevance:** **High.** Proves that "dumb" physical actions (filling a pot) should be explicit steps in the digital workflow to ensure compliance.
*   **Source:** Official App Documentation (High Confidence)

### AeroPress Timers (Aeromatic, AeroPress Timer)
*   **Finding:** These apps treat physical phases as timed blocks. Steps are labeled "Bloom", "Stir", "Press".
*   **Insight:** They don't hide the "Press" step; they time it. The UI creates a sense of urgency and rhythm.
*   **Relevance:** **Medium.** Shows that physical exertion (pressing) can be gamified/timed.
*   **Source:** Community Consensus / App Reviews (High Confidence)

## 2. High-Stakes & Complex Movement (Medical & Fitness)

### MyTherapy (Injection/Medication)
*   **Finding:** For injections (e.g., Wegovy, Insulin), apps provide detailed, step-by-step lists: "Remove pen cap", "Select site", "Insert needle".
*   **Insight:** High-stakes physical actions utilize a "checklist" UI to prevent error. They do not assume the user remembers the protocol.
*   **Relevance:** **Medium.** Suggests that for the *critical* Valve operation (where mistakes ruin the brew), explicit confirmation might be safer than just a timed prompt.
*   **Source:** Official App Features (High Confidence)

### Peloton (Form Assist)
*   **Finding:** Uses visual highlights (e.g., red glow on an avatar's legs) and short text cues ("Push with legs") to correct complex physical form.
*   **Insight:** Instead of long explanations, they use **glanceable, simplified cues**.
*   **Relevance:** **Low/Medium.** Good for "how" to pour, but less relevant for binary "open/close" tasks.
*   **Source:** Engineering Blog / Product Updates (High Confidence)

## 3. Cognitive Load Research (The "Why")

### Turn-by-Turn (GPS) vs. Map-Based Navigation
*   **Finding:** **Turn-by-turn navigation (TBT) reduces cognitive load** for immediate task execution but reduces spatial learning (forming a mental map).
*   **Key Takeaway:** If your goal is *execution* (getting a good coffee *now*), the TBT model is superior. If your goal is *education* (teaching them to brew without the app), TBT is inferior.
*   **Scientific Consensus:** TBT allows "passive" successful execution by offloading the planning to the system.
*   **Relevance:** **Very High.** Validates the "blindly follow" approach for a utility timer app.
*   **Source:** Academic Papers (Nature, Frontiers in Virtual Reality) (High Confidence)

### Guided vs. Full Context Instruction
*   **Finding:** For **novices**, **fully guided instruction** (explicit steps) is significantly more effective than minimal guidance. "Discovery learning" (figuring it out) overwhelms cognitive load for beginners.
*   **Relevance:** **High.** A user new to the "Pulsar" brewer is a novice. They need explicit guidance.
*   **Source:** Educational Psychology Research (Clark, Kirschner & Sweller) (High Confidence)

## 4. Synthesis: The "Middle Ground" UI Pattern

The successful pattern for "Valve" guidance is **Startling Explicit Prompt + Timer**.

| Pattern | Description | Example App |
| :--- | :--- | :--- |
| **Full-Screen Takeover** | When a physical action is required, the UI should change drastically (color/size) to break the user's focus on the numbers. | Peloton (Red Highlights) |
| **Countdown for Action** | Give the user time to perform the action *before* the brew timer continues, or count the action as part of the phase. | AeroPress (Press Timer) |
| **Binary State Toggle** | Don't just show text; show the *state*. "Valve: OPEN" vs "Valve: CLOSED". | Anova (Status Monitor) |
| **Audio/Haptic Cues** | Essential for when the user is looking at the brewer, not the screen. | Fellow Stagg |

**Recommendation for Coffee App:**
Use the **GPS Turn-by-Turn** model.
1.  **Pre-Warning:** "Prepare to Close Valve" (5s before).
2.  **Action Command:** "CLOSE VALVE" (Big UI change, Haptic buzz).
3.  **State Confirmation:** "Steeping... (Valve Closed)" displayed during the passive phase.
