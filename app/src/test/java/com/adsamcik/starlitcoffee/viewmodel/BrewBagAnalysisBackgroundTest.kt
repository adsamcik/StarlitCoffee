package com.adsamcik.starlitcoffee.viewmodel

import com.adsamcik.starlitcoffee.notification.BagAnalysisNotifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

        override fun notifyComplete(displayName: String?) {
            calls++
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
}
