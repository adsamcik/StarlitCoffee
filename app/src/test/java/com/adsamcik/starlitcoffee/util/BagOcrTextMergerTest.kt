package com.adsamcik.starlitcoffee.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the front+back OCR text merger.
 *
 * The merger is the load-bearing piece of the bag-scan front+back fusion
 * work: it takes per-photo OCR text and produces a single labeled string
 * that the LLM extraction prompt consumes. Getting the section markers
 * wrong (or skipping a side, or labelling an empty photo) would either
 * cause the LLM to mis-route tokens between fields or surface as the
 * confusing "section header with no content" failure mode.
 */
class BagOcrTextMergerTest {

    @Test
    fun `empty input returns empty string`() {
        assertEquals("", BagOcrTextMerger.combineBySide(emptyList()))
    }

    @Test
    fun `all-blank input returns empty string`() {
        val result = BagOcrTextMerger.combineBySide(
            listOf(
                BagCaptureSide.FRONT to "",
                BagCaptureSide.BACK to "   ",
            ),
        )
        assertEquals("", result)
    }

    @Test
    fun `single non-blank photo returns unlabeled text`() {
        // Single-photo path stays unlabeled so the common front-only
        // scan path doesn't change prompt shape vs the pre-fusion world.
        // If this regresses, every single-photo scan suddenly gets a
        // `--- FRONT ---` header for no reason — confusing the LLM and
        // breaking the prompt-shape invariant the SDK tests rely on.
        val result = BagOcrTextMerger.combineBySide(
            listOf(BagCaptureSide.FRONT to "  BEANSMITHS\nETHIOPIA  "),
        )
        assertEquals("BEANSMITHS\nETHIOPIA", result)
    }

    @Test
    fun `front-plus-back produces labeled multi-section string`() {
        val result = BagOcrTextMerger.combineBySide(
            listOf(
                BagCaptureSide.FRONT to "BEANSMITHS\nETHIOPIA GEDEB",
                BagCaptureSide.BACK to "DATUM PRAZENI 09.12.25\n250g",
            ),
        )
        assertEquals(
            "--- FRONT ---\nBEANSMITHS\nETHIOPIA GEDEB\n\n--- BACK ---\nDATUM PRAZENI 09.12.25\n250g",
            result,
        )
    }

    @Test
    fun `blank side is dropped silently`() {
        // A header-with-no-content confuses the LLM's section-routing
        // heuristic — it might infer that the BACK is genuinely empty
        // and emit not_visible for date / weight when actually OCR
        // just failed on a faded sticker.
        val result = BagOcrTextMerger.combineBySide(
            listOf(
                BagCaptureSide.FRONT to "MERRYBEANS\nKOLUMBIE TUMBAGA DECAF",
                BagCaptureSide.BACK to "",
            ),
        )
        assertEquals("MERRYBEANS\nKOLUMBIE TUMBAGA DECAF", result)
    }

    @Test
    fun `whitespace-only side is treated as blank and dropped`() {
        val result = BagOcrTextMerger.combineBySide(
            listOf(
                BagCaptureSide.FRONT to "MUNTASHA",
                BagCaptureSide.BACK to "   \n\n  \t  ",
            ),
        )
        assertEquals("MUNTASHA", result)
    }

    @Test
    fun `each section is independently trimmed`() {
        val result = BagOcrTextMerger.combineBySide(
            listOf(
                BagCaptureSide.FRONT to "\n\nKAFFA WILDKAFFEE  \n",
                BagCaptureSide.BACK to "  L 044\n13.02.2027\n\n\n",
            ),
        )
        assertEquals(
            "--- FRONT ---\nKAFFA WILDKAFFEE\n\n--- BACK ---\nL 044\n13.02.2027",
            result,
        )
    }

    @Test
    fun `section label reflects the BagCaptureSide enum case`() {
        // Defensive guard: if BagCaptureSide ever gains a new case,
        // this test fires on the resulting incomplete `when`. The
        // merger's contract is that FRONT and BACK are the only valid
        // section labels per the system prompt.
        val front = BagOcrTextMerger.combineBySide(
            listOf(
                BagCaptureSide.FRONT to "F1",
                BagCaptureSide.FRONT to "F2",
            ),
        )
        // Two FRONTs (unusual but legal) produces two FRONT-labeled
        // sections rather than collapsing — caller can fix capture
        // metadata if needed.
        assertEquals("--- FRONT ---\nF1\n\n--- FRONT ---\nF2", front)
    }

    @Test
    fun `multiple back photos all get BACK label`() {
        // Edge case the bag-scan UI doesn't currently produce but the
        // merger should handle defensively if a future capture flow
        // ever supplies multiple back photos (e.g. wide + close-up of
        // the metadata strip).
        val result = BagOcrTextMerger.combineBySide(
            listOf(
                BagCaptureSide.FRONT to "FRONT TEXT",
                BagCaptureSide.BACK to "BACK TEXT 1",
                BagCaptureSide.BACK to "BACK TEXT 2",
            ),
        )
        assertEquals(
            "--- FRONT ---\nFRONT TEXT\n\n--- BACK ---\nBACK TEXT 1\n\n--- BACK ---\nBACK TEXT 2",
            result,
        )
    }
}
