package com.adsamcik.starlitcoffee.scan

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanDeadlineTest {
    @Test
    fun `deadline uses the original persisted scan start`() {
        var now = 1_200L
        val deadline = ScanDeadline.fromStartedAt(
            startedAtEpochMs = 1_000L,
            budgetMillis = 500L,
            nowEpochMs = { now },
        )

        assertEquals(300L, deadline.remainingMillis)
        now = 1_600L
        assertEquals(0L, deadline.remainingMillis)
    }

    @Test
    fun `expired deadline fails before starting another stage`() = runTest {
        val deadline = ScanDeadline.fromStartedAt(
            startedAtEpochMs = 1L,
            budgetMillis = 1L,
            nowEpochMs = { 3L },
        )

        var failedAtDeadline = false
        try {
            deadline.run { delay(1); "late" }
        } catch (_: ScanDeadlineExceededException) {
            failedAtDeadline = true
        }
        assertTrue(failedAtDeadline)
    }
}
