package com.adsamcik.starlitcoffee.util

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoStoragePolicyTest {

    @Test
    fun `landscape photos are bounded to the AI long edge`() {
        assertEquals(
            PhotoDimensions(width = 2048, height = 1536),
            PhotoStoragePolicy.scaledDimensions(width = 4032, height = 3024),
        )
    }

    @Test
    fun `portrait photos are bounded to the AI long edge`() {
        assertEquals(
            PhotoDimensions(width = 1536, height = 2048),
            PhotoStoragePolicy.scaledDimensions(width = 3000, height = 4000),
        )
    }

    @Test
    fun `photos already within the AI bound keep their dimensions`() {
        assertEquals(
            PhotoDimensions(width = 1200, height = 800),
            PhotoStoragePolicy.scaledDimensions(width = 1200, height = 800),
        )
    }

    @Test
    fun `thumbnail dimensions use the requested long edge`() {
        assertEquals(
            PhotoDimensions(width = 512, height = 384),
            PhotoStoragePolicy.scaledDimensions(width = 2048, height = 1536, maxLongEdgePx = 512),
        )
    }

    @Test
    fun `legacy decode sampling never decodes above the storage bound`() {
        assertEquals(2, PhotoStoragePolicy.boundedDecodeSampleSize(sourceLongEdgePx = 4032))
        assertEquals(4, PhotoStoragePolicy.boundedDecodeSampleSize(sourceLongEdgePx = 6000))
        assertEquals(1, PhotoStoragePolicy.boundedDecodeSampleSize(sourceLongEdgePx = 2048))
    }

    @Test
    fun `scan session owns stable permanent photo names`() {
        val storageKey = ScanPhotoStorage.storageKeyForSession("scan-session")

        assertEquals(storageKey, ScanPhotoStorage.storageKeyForSession("scan-session"))
        assertEquals("bag_${storageKey}_0.webp", ScanPhotoStorage.permanentPhotoFileName(storageKey, 0))
        assertEquals("thumb_$storageKey.webp", ScanPhotoStorage.focusedThumbnailFileName(storageKey))
        assertEquals(
            true,
            ScanPhotoStorage.isSessionOwnedPermanentPhotoName(".bag_${storageKey}_0.webp.tmp", storageKey),
        )
        assertEquals(
            true,
            ScanPhotoStorage.isSessionOwnedPermanentPhotoName(".thumb_$storageKey.webp.tmp", storageKey),
        )
    }
}
