package com.adsamcik.starlitcoffee.util

import org.junit.Assert.assertEquals
import org.junit.Test

class BagPhotoImportSupportTest {

    // --- Gallery Selection ---

    @Test
    fun `limitImportedSelection keeps the first two gallery images`() {
        val selected = BagPhotoImportSupport.limitImportedSelection(
            selectedItems = listOf("front", "back", "extra"),
        )

        assertEquals(listOf("front", "back"), selected)
    }

    @Test
    fun `limitImportedSelection returns the whole gallery selection when under the max`() {
        val selected = BagPhotoImportSupport.limitImportedSelection(
            selectedItems = listOf("front", "back"),
        )

        assertEquals(listOf("front", "back"), selected)
    }

    // --- Cache Import Naming ---

    @Test
    fun `buildImportedPhotoName uses mime type extension and sanitizes the source name`() {
        val fileName = BagPhotoImportSupport.buildImportedPhotoName(
            sourceName = "Front Label 01.heic",
            mimeType = "image/jpeg",
            timestampMs = 1234L,
            index = 0,
            originalExtension = "heic",
        )

        assertEquals("coffee_label_1234_1_Front_Label_01.jpg", fileName)
    }

    @Test
    fun `buildImportedPhotoName falls back to jpg when the source type is unknown`() {
        val fileName = BagPhotoImportSupport.buildImportedPhotoName(
            sourceName = "no_extension",
            mimeType = null,
            timestampMs = 5678L,
            index = 1,
            originalExtension = null,
        )

        assertEquals("coffee_label_5678_2_no_extension.jpg", fileName)
    }
}
