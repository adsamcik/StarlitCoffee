package com.adsamcik.starlitcoffee.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DestructiveActionPolicyTest {

    // --- New bags ---

    @Test
    fun `empty new bag can close without confirmation`() {
        assertFalse(
            shouldConfirmBagDismiss(
                isEditMode = false,
                initial = BagFormSnapshot(),
                current = BagFormSnapshot(),
                hasCapturedPhotos = false,
                hasTraceabilityData = false,
            ),
        )
    }

    @Test
    fun `prefilled new bag requires confirmation`() {
        val prefilled = BagFormSnapshot(name = "Ethiopia")

        assertTrue(
            shouldConfirmBagDismiss(
                isEditMode = false,
                initial = prefilled,
                current = prefilled,
                hasCapturedPhotos = false,
                hasTraceabilityData = false,
            ),
        )
    }

    @Test
    fun `captured photos require confirmation even without fields`() {
        assertTrue(
            shouldConfirmBagDismiss(
                isEditMode = false,
                initial = BagFormSnapshot(),
                current = BagFormSnapshot(),
                hasCapturedPhotos = true,
                hasTraceabilityData = false,
            ),
        )
    }

    // --- Existing bags ---

    @Test
    fun `unchanged existing bag can close without confirmation`() {
        val existing = BagFormSnapshot(name = "Ethiopia", roaster = "Starlight")

        assertFalse(
            shouldConfirmBagDismiss(
                isEditMode = true,
                initial = existing,
                current = existing,
                hasCapturedPhotos = true,
                hasTraceabilityData = true,
            ),
        )
    }

    @Test
    fun `edited existing bag requires confirmation`() {
        val existing = BagFormSnapshot(name = "Ethiopia", roaster = "Starlight")

        assertTrue(
            shouldConfirmBagDismiss(
                isEditMode = true,
                initial = existing,
                current = existing.copy(roaster = "Night Owl"),
                hasCapturedPhotos = false,
                hasTraceabilityData = false,
            ),
        )
    }

    @Test
    fun `enrichment updates untouched fields without overwriting edits`() {
        val previous = BagFormSnapshot(
            name = "OCR name",
            roaster = "OCR roaster",
            farm = "OCR farm",
            altitude = "1,800 masl",
            barcode = "111",
        )
        val current = previous.copy(name = "My corrected name", altitude = "1,900 masl")
        val incoming = previous.copy(
            name = "AI name",
            roaster = "AI roaster",
            farm = "AI farm",
            altitude = "2,000 masl",
            barcode = "222",
        )

        val merged = mergeBagFormEnrichment(current, previous, incoming)

        assertEquals("My corrected name", merged.name)
        assertEquals("AI roaster", merged.roaster)
        assertEquals("AI farm", merged.farm)
        assertEquals("1,900 masl", merged.altitude)
        assertEquals("222", merged.barcode)
    }

    @Test
    fun `enrichment preserves a user-edited barcode`() {
        val previous = BagFormSnapshot(barcode = "111")
        val current = previous.copy(barcode = "999")
        val incoming = previous.copy(barcode = "222")

        val merged = mergeBagFormEnrichment(current, previous, incoming)

        assertEquals("999", merged.barcode)
    }

    @Test
    fun `unrelated scan result waits while another draft is open`() {
        assertFalse(
            shouldApplyBagResultToDraft(
                isDraftOpen = true,
                currentSessionId = "manual-draft",
                incomingSessionId = "background-scan",
            ),
        )
        assertTrue(
            shouldApplyBagResultToDraft(
                isDraftOpen = false,
                currentSessionId = "manual-draft",
                incomingSessionId = "background-scan",
            ),
        )
    }
}
