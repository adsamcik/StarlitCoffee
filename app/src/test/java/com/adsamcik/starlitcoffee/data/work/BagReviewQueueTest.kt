package com.adsamcik.starlitcoffee.data.work

import org.junit.Assert.assertEquals
import org.junit.Test

class BagReviewQueueTest {

    @Test
    fun `only UUID work IDs are accepted from deep links`() {
        assertEquals(true, BagExtractionScheduler.isValidWorkId("9f8b49ea-4407-4129-a34f-472f8654f875"))
        assertEquals(false, BagExtractionScheduler.isValidWorkId("not-a-work-id"))
    }

    @Test
    fun `tapped notification work is prioritized without dropping older reviews`() {
        assertEquals(
            listOf("work-b", "work-a", "work-c"),
            prioritizeReviewWorkId(listOf("work-a", "work-b", "work-c"), "work-b"),
        )
    }

    @Test
    fun `new tapped notification work is added to the front`() {
        assertEquals(
            listOf("work-b", "work-a"),
            prioritizeReviewWorkId(listOf("work-a"), "work-b"),
        )
    }
}
