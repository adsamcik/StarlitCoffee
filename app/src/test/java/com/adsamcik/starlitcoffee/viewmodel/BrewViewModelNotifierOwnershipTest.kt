package com.adsamcik.starlitcoffee.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.adsamcik.starlitcoffee.notification.BrewSessionNotifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BrewViewModelNotifierOwnershipTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `clearing ViewModel store closes its notifier exactly once`() {
        val notifier = RecordingBrewSessionNotifier()
        val store = ViewModelStore()
        val provider = ViewModelProvider(
            store,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    BrewViewModel(brewSessionNotifier = notifier) as T
            },
        )
        provider[BrewViewModel::class.java]

        assertEquals(0, notifier.closeCalls)
        store.clear()
        store.clear()

        assertEquals(1, notifier.closeCalls)
    }

    private class RecordingBrewSessionNotifier : BrewSessionNotifier {
        var closeCalls = 0
            private set

        override fun onBrewStateChanged(state: BrewUiState) = Unit

        override fun close() {
            closeCalls += 1
        }
    }
}
