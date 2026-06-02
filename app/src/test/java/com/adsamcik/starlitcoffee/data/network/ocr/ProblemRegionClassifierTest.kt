package com.adsamcik.starlitcoffee.data.network.ocr

import android.graphics.Rect
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ProblemRegionClassifier] — pin the structural heuristics
 * used to decide whether an OCR block should be re-OCR'd at higher relative
 * resolution.
 *
 * No language-specific tokens here; the rules are purely geometric +
 * character-pattern based per the project's no-per-language-enumeration
 * convention.
 */
class ProblemRegionClassifierTest {

    private val classifier = ProblemRegionClassifier()

    private fun block(text: String, bbox: Rect?): RecognizedTextBlock =
        RecognizedTextBlock(text = text, boundingBox = bbox, lines = emptyList())

    // --- isProblem (top-level) ---

    @Test
    fun `empty text is never a problem`() {
        assertFalse(classifier.isProblem(block("", Rect(0, 0, 1000, 50))))
        assertFalse(classifier.isProblem(block("   ", Rect(0, 0, 1000, 50))))
    }

    @Test
    fun `null bounding box is never a problem`() {
        assertFalse(classifier.isProblem(block("Whatever", null)))
    }

    @Test
    fun `short single-word block is not a problem`() {
        // Roughly square-ish bbox, short text — normal case
        assertFalse(classifier.isProblem(block("Yirgacheffe", Rect(100, 100, 400, 160))))
    }

    @Test
    fun `comfortable spaced multi-word block is not a problem`() {
        // aspect = 500/50 = 10 > 8 BUT length = 12 < LONG_TEXT_MIN of 15
        val bbox = Rect(100, 150, 600, 200)
        val text = "Cafe Sauvage"
        assertFalse(classifier.isProblem(block(text, bbox)))
    }

    // --- isWideMashed ---

    @Test
    fun `wide aspect with long text triggers isWideMashed`() {
        // aspect = 900/50 = 18 > 8, length = 33 > 15
        assertTrue(classifier.isWideMashed(width = 900, height = 50, text = "Merrybeans Kolumbie TUMBAGA DECAF"))
    }

    @Test
    fun `wide aspect with short text does not trigger isWideMashed`() {
        // aspect = 18 > 8, but length = 6 < 15
        assertFalse(classifier.isWideMashed(width = 900, height = 50, text = "Coffee"))
    }

    @Test
    fun `narrow aspect with long text does not trigger isWideMashed`() {
        // aspect = 200/100 = 2 < 8
        assertFalse(
            classifier.isWideMashed(
                width = 200,
                height = 100,
                text = "A reasonably long block of text on multiple lines",
            ),
        )
    }

    @Test
    fun `zero-height bbox does not crash isWideMashed`() {
        // height = 0 — degenerate, should not divide by zero
        assertFalse(classifier.isWideMashed(width = 1000, height = 0, text = "AnyTextHere"))
    }

    // --- hasInternalCaseTransition ---

    @Test
    fun `lowercase-to-uppercase transition triggers`() {
        // Two words glued together — classic OCR mash
        assertTrue(classifier.hasInternalCaseTransition("MerrybeansKolumbie"))
    }

    @Test
    fun `single uppercase letter after lowercase is enough`() {
        // The Merrybeans bag's "MerybeansKubeBGADCA-" case
        assertTrue(classifier.hasInternalCaseTransition("MerybeansKubeBGADCA"))
    }

    @Test
    fun `clean single-case text does not trigger`() {
        assertFalse(classifier.hasInternalCaseTransition("merrybeans"))
        assertFalse(classifier.hasInternalCaseTransition("MERRYBEANS"))
        assertFalse(classifier.hasInternalCaseTransition("Merrybeans"))
        assertFalse(classifier.hasInternalCaseTransition("Yirgacheffe"))
    }

    @Test
    fun `properly spaced multi-word text does not trigger`() {
        assertFalse(classifier.hasInternalCaseTransition("Cafe Sauvage"))
        assertFalse(classifier.hasInternalCaseTransition("Counter Culture Coffee"))
    }

    // --- isLongUnspacedRun ---

    @Test
    fun `long unspaced run triggers`() {
        // 25 chars no spaces — likely multiple mashed tokens
        assertTrue(classifier.isLongUnspacedRun("Merrybeans250gPrazirnaABC"))
    }

    @Test
    fun `long text with spaces does not trigger`() {
        // 30+ chars but properly spaced
        assertFalse(classifier.isLongUnspacedRun("Some long but properly spaced text here"))
    }

    @Test
    fun `short unspaced text does not trigger`() {
        // Under the 20-char threshold even without spaces
        assertFalse(classifier.isLongUnspacedRun("Merrybeans"))
        assertFalse(classifier.isLongUnspacedRun("Yirgacheffe"))
    }

    // --- Realistic Merrybeans bag fixtures ---

    @Test
    fun `Merrybeans back sticker mashup is classified as problem`() {
        // Empirically observed PaddleOCR output for the back sticker.
        // Triggers via hasInternalCaseTransition ("MerybeansKubeBGADCA-" has
        // a lowercase→uppercase transition at position 9), not via the
        // Rect-based isWideMashed path. That keeps this integration test
        // independent of the Android.jar Rect stub (which doesn't preserve
        // field values on JVM unit-test runs).
        val bbox = Rect(120, 1800, 880, 1860)
        val text = "MerybeansKubeBGADCA-"
        assertTrue(
            "Merrybeans sticker mashup must be re-OCR'd",
            classifier.isProblem(block(text, bbox)),
        )
    }

    @Test
    fun `Merrybeans front cursive Merrybeahs is NOT classified as problem`() {
        // Single-word cursive misread — re-OCR will produce the same mistake
        // (cursive is the issue, not detection bbox grouping). Don't waste
        // cycles. Text is short, no case transition, no long unspaced run.
        val bbox = Rect(200, 500, 600, 700)
        val text = "Merrybeahs"
        assertFalse(
            "Single-token cursive misread should not be re-OCR'd",
            classifier.isProblem(block(text, bbox)),
        )
    }

    // NOTE: the wide-bbox + clean-spaces scenario (e.g. a wide body-text
    // line that triggers ONLY via isWideMashed) is exercised by the direct
    // `isWideMashed(width, height, text)` tests above. We cannot exercise it
    // through `isProblem(block)` in a JVM unit test because the Android.jar
    // stub of Rect zeroes out `left`/`top`/`right`/`bottom` field reads on
    // JVM. In production (where Rect is real) the Rect-bbox path fires
    // exactly as the direct tests show.
}
