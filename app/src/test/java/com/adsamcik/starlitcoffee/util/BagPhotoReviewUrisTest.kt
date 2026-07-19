package com.adsamcik.starlitcoffee.util

import org.junit.Assert.assertEquals
import org.junit.Test

class BagPhotoReviewUrisTest {

    @Test
    fun `parse keeps every selected photo in capture order`() {
        assertEquals(
            listOf("file:///scan/front.jpg", "file:///scan/back.jpg"),
            BagPhotoReviewUris.parse(" file:///scan/front.jpg, ,file:///scan/back.jpg "),
        )
    }

    @Test
    fun `parse returns no photos for missing capture data`() {
        assertEquals(emptyList<String>(), BagPhotoReviewUris.parse(null))
    }
}
