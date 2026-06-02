package com.adsamcik.starlitcoffee.util

/**
 * Pure helper that merges per-photo OCR text into a single labeled string
 * for the LLM prompt.
 *
 * Coffee bag photos split their printed text across two physical sides:
 *  - **Front:** brand / name / origin / tasting notes — the marketing face.
 *  - **Back:** structured metadata strip — roast date, weight, batch number,
 *    EAN, process method, altitude.
 *
 * The bag-scan pipeline already runs OCR on every photo the user picked
 * (see `BrewViewModel.processNewBagPhotos`), but until this helper landed
 * it concatenated the per-photo text with `\n\n` and surrendered the
 * front-vs-back distinction. That loss was the root cause of the
 * "Could not read label" outcome on the Merrybeans Tumbaga Decaf bag in
 * the on-device validation: the user picked only one photo (front), the
 * back's date / weight / process never entered the prompt, and the LLM
 * had to emit `not_visible` for those fields even though they were
 * physically printed on the bag.
 *
 * This helper restores the section structure with explicit `--- FRONT ---`
 * / `--- BACK ---` ASCII headers. The headers do double duty:
 *  - For the model: contextualises tokens so a back-of-bag "12.12.2025"
 *    reads as a roastDate rather than a stray number, and a front-of-bag
 *    "Wildkaffee" reads as a name even when the back also mentions it.
 *  - For prompt-debug logging: makes the captured OCR text human-scannable.
 *
 * Empty per-photo text is dropped silently rather than emitting a header
 * with no content (a header-with-no-content can confuse the section-routing
 * heuristic). A single photo's text is returned unlabeled so the common
 * front-only scan path doesn't change prompt shape.
 *
 * Pure function — moved out of `BrewViewModel` so the merge contract can be
 * tested without spinning up a ViewModel + Android lifecycle.
 */
internal object BagOcrTextMerger {

    /**
     * @param sides per-photo `(side, fullText)` pairs in capture order.
     *              Order matters only for the final string layout; the
     *              labelling is driven by the [BagCaptureSide] in each
     *              pair, not by position.
     * @return either the single non-blank text (unlabeled), the labelled
     *         multi-section string, or an empty string when no photo
     *         produced text.
     */
    fun combineBySide(sides: List<Pair<BagCaptureSide, String>>): String {
        if (sides.isEmpty()) return ""
        val nonBlank = sides.filter { it.second.isNotBlank() }
        if (nonBlank.isEmpty()) return ""
        if (nonBlank.size == 1) return nonBlank.first().second.trim()
        return nonBlank.joinToString("\n\n") { (side, text) ->
            val label = when (side) {
                BagCaptureSide.FRONT -> "FRONT"
                BagCaptureSide.BACK -> "BACK"
            }
            "--- $label ---\n${text.trim()}"
        }
    }
}
