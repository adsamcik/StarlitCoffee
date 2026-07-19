package com.adsamcik.starlitcoffee.viewmodel

import com.adsamcik.starlitcoffee.data.work.BagReviewContext
import com.adsamcik.starlitcoffee.notification.BagAnalysisNotifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Guards the public surface of the "analyzing bag" Skip / background controls
 * on [BrewViewModel]. The full bag-photo pipeline decodes bitmaps (Android), so
 * the delivery routing itself is verified on-device; these tests pin the
 * idle/no-op semantics so the toggles can never NPE when nothing is in flight.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BrewBagAnalysisBackgroundTest {
    private class RecordingNotifier : BagAnalysisNotifier {
        var calls = 0
            private set

        override fun notifyComplete(
            workId: String,
            displayName: String?,
            reviewContext: BagReviewContext?,
        ): Boolean {
            calls++
            return true
        }

        override fun notifyFailed(
            workId: String,
            reviewContext: BagReviewContext?,
        ): Boolean {
            calls++
            return true
        }
    }

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `skip and background toggles are safe to call when idle`() = runTest {
        val notifier = RecordingNotifier()
        val vm = BrewViewModel(bagAnalysisNotifier = notifier)

        vm.skipBagPhotoLlm()
        assertFalse(vm.retryBagPhotoLlm())
        vm.continueBagAnalysisInBackground()

        // No analysis is running, so nothing is delivered or notified.
        assertNull(vm.bagPhotoResult.value)
        assertNull(vm.completedBackgroundResult.value)
        assertEquals(0, notifier.calls)
    }

    @Test
    fun `promoteBackgroundResultToForeground is a no-op when nothing is pending`() = runTest {
        val vm = BrewViewModel()

        vm.promoteBackgroundResultToForeground()

        assertNull(vm.completedBackgroundResult.value)
        assertNull(vm.bagPhotoResult.value)
    }

    @Test
    fun `empty scan result keeps its draft session identity`() = runTest {
        val vm = BrewViewModel()

        vm.processNewBagPhotos(photosCsv = "", sessionId = "draft-123")

        assertEquals("draft-123", vm.bagPhotoResult.value?.sessionId)
    }

    @Test
    fun `empty rescan result keeps target review context`() = runTest {
        val vm = BrewViewModel()
        val reviewContext = BagReviewContext.rescan(42L)

        vm.processNewBagPhotos(
            photosCsv = "",
            sessionId = "rescan-draft",
            reviewContext = reviewContext,
        )

        assertEquals(reviewContext, vm.bagPhotoResult.value?.reviewContext)
    }

    @Test
    fun `background rescan result requires inventory back stack restoration`() = runTest {
        val vm = BrewViewModel()

        vm.processNewBagPhotos(
            photosCsv = "",
            deliverInBackground = true,
            sessionId = "background-rescan",
            reviewContext = BagReviewContext.rescan(42L),
        )

        assertTrue(vm.bagPhotoResult.value?.requiresInventoryBackStack == true)
    }

    @Test
    fun `new generation replaces stale review context for the same session`() = runTest {
        val vm = BrewViewModel()
        vm.processNewBagPhotos(
            photosCsv = "",
            sessionId = "draft-123",
            reviewContext = BagReviewContext.rescan(42L),
        )

        vm.processNewBagPhotos(
            photosCsv = "",
            sessionId = "draft-123",
            reviewContext = BagReviewContext.addNew(),
        )

        assertEquals(BagReviewContext.addNew(), vm.bagPhotoResult.value?.reviewContext)
    }

    @Test
    fun `new photo generation clears an older result for the same session`() = runTest {
        val vm = BrewViewModel()
        vm.processNewBagPhotos(photosCsv = "", sessionId = "draft-123")

        vm.processNewBagPhotos(
            photosCsv = "file:///new-generation.jpg",
            sessionId = "draft-123",
        )

        try {
            assertNull(vm.bagPhotoResult.value)
        } finally {
            vm.cancelBagPhotoProcessing("draft-123")
            vm.awaitCancelledBagPhotoProcessing()
        }
    }

    @Test
    fun `empty generation cancels older work and remains the terminal result`() = runTest {
        val vm = BrewViewModel()
        vm.processNewBagPhotos(
            photosCsv = "file:///old-generation.jpg",
            sessionId = "draft-123",
        )

        vm.processNewBagPhotos(photosCsv = "", sessionId = "draft-123")

        try {
            assertEquals("", vm.bagPhotoResult.value?.result?.capturedPhotoUris)
        } finally {
            vm.cancelBagPhotoProcessing("draft-123")
            vm.awaitCancelledBagPhotoProcessing()
        }
    }
}
