package com.adsamcik.starlitcoffee.ui.screen

import com.adsamcik.starlitcoffee.ui.component.DecafFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrewLogScreenPolicyTest {

    @Test
    fun `selection changes only after current detail save succeeds`() {
        val selectedA = BrewLogSelectionState(selectedLogId = 1L)
        val savingA = requestBrewLogSelection(selectedA, requestedLogId = 2L)

        assertEquals(1L, savingA.selectedLogId)
        assertEquals(2L, savingA.pendingLogId)
        assertTrue(savingA.isSavingBeforeSelection)

        val selectedB = completeBrewLogSelection(savingA, saveSucceeded = true)

        assertEquals(2L, selectedB.selectedLogId)
        assertEquals(null, selectedB.pendingLogId)
        assertFalse(selectedB.isSavingBeforeSelection)
    }

    @Test
    fun `failed detail save keeps current selection and allows retry`() {
        val savingA = requestBrewLogSelection(
            BrewLogSelectionState(selectedLogId = 1L),
            requestedLogId = 2L,
        )

        val failed = completeBrewLogSelection(savingA, saveSucceeded = false)

        assertEquals(1L, failed.selectedLogId)
        assertEquals(null, failed.pendingLogId)
        assertFalse(failed.isSavingBeforeSelection)

        val retry = requestBrewLogSelection(failed, requestedLogId = 2L)
        assertEquals(2L, retry.pendingLogId)
    }

    @Test
    fun `brew log decaf filter returns to all when selected category disappears`() {
        assertEquals(
            DecafFilter.ALL,
            normalizeBrewLogDecafFilter(
                selected = DecafFilter.DECAF,
                regularCount = 1,
                decafCount = 0,
            ),
        )
        assertEquals(
            DecafFilter.DECAF,
            normalizeBrewLogDecafFilter(
                selected = DecafFilter.DECAF,
                regularCount = 0,
                decafCount = 0,
            ),
        )
    }
}
