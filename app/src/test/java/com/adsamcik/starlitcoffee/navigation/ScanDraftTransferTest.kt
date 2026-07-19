package com.adsamcik.starlitcoffee.navigation

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanDraftTransferTest {

    @Test
    fun `saved state navigation result is consumed exactly once`() {
        val handle = SavedStateHandle(mapOf(SCANNED_BARCODE_RESULT_KEY to "8591234567890"))

        assertEquals(
            "8591234567890",
            handle.consumeOneShotResult<String>(SCANNED_BARCODE_RESULT_KEY),
        )
        assertNull(handle.consumeOneShotResult<String>(SCANNED_BARCODE_RESULT_KEY))
    }

    @Test
    fun `same barcode can be delivered again after consumption`() {
        val handle = SavedStateHandle()
        handle[SCANNED_BARCODE_RESULT_KEY] = "8591234567890"
        assertEquals(
            "8591234567890",
            handle.consumeOneShotResult<String>(SCANNED_BARCODE_RESULT_KEY),
        )

        handle[SCANNED_BARCODE_RESULT_KEY] = "8591234567890"

        assertEquals(
            "8591234567890",
            handle.consumeOneShotResult<String>(SCANNED_BARCODE_RESULT_KEY),
        )
    }

    @Test
    fun `rescan transfer round trips fields photos identities and thumbnail focus`() {
        val transfer = ScanDraftTransfer(
            eventId = "event-1",
            fields = mapOf(
                "farm" to "Chelbesa",
                "altitude" to "1950-2200 masl",
                "isDecaf" to "false",
            ),
            capturedPhotoUris = "file:///front.jpg,file:///back.jpg",
            scanSessionId = "scan-session",
            generationId = "generation-3",
            thumbnailFocus = ScanThumbnailFocus(0.1f, 0.2f, 0.8f, 0.9f),
            detectedBarcode = "8591234567890",
            detectedQrUrl = "https://example.com/trace",
        )

        val decoded = ScanDraftTransferCodec.decode(ScanDraftTransferCodec.encode(transfer))

        assertEquals(transfer, decoded)
        assertEquals(0.1f, decoded?.thumbnailFocus?.toBagPhotoRect()?.leftFraction)
    }
}
