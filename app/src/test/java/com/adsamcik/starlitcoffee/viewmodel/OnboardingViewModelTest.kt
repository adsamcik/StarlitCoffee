package com.adsamcik.starlitcoffee.viewmodel

import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.FilterType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `completion waits for one atomic write and removes hidden Pulsar filter`() =
        runTest(dispatcher) {
            val gate = CompletableDeferred<Unit>()
            var calls = 0
            var persistedFilter: FilterType? = FilterType.PAPER
            val store = object : TestUserPreferencesStore() {
                override suspend fun completeOnboarding(
                    enabledMethods: Set<BrewMethod>,
                    defaultMethod: BrewMethod,
                    defaultFilterType: FilterType?,
                    selectedGrinderId: String?,
                ) {
                    calls++
                    persistedFilter = defaultFilterType
                    gate.await()
                }
            }
            val viewModel = OnboardingViewModel(store)

            viewModel.complete(
                enabledMethods = setOf(BrewMethod.V60),
                defaultMethod = BrewMethod.V60,
                filterType = FilterType.METAL_19K,
                grinderId = "grinder",
            )
            viewModel.complete(
                enabledMethods = setOf(BrewMethod.PULSAR),
                defaultMethod = BrewMethod.PULSAR,
                filterType = FilterType.PAPER,
                grinderId = null,
            )

            assertEquals(1, calls)
            assertTrue(viewModel.uiState.value.isSubmitting)
            assertNull(viewModel.uiState.value.completedSubmission)

            gate.complete(Unit)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isSubmitting)
            assertNull(persistedFilter)
            assertNull(requireNotNull(viewModel.uiState.value.completedSubmission).filterType)
        }

    @Test
    fun `failed completion can be retried`() = runTest(dispatcher) {
        var shouldFail = true
        val store = object : TestUserPreferencesStore() {
            override suspend fun completeOnboarding(
                enabledMethods: Set<BrewMethod>,
                defaultMethod: BrewMethod,
                defaultFilterType: FilterType?,
                selectedGrinderId: String?,
            ) {
                if (shouldFail) error("DataStore failure")
            }
        }
        val viewModel = OnboardingViewModel(store)

        viewModel.complete(setOf(BrewMethod.PULSAR), BrewMethod.PULSAR, null, null)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.failure)

        shouldFail = false
        viewModel.complete(setOf(BrewMethod.PULSAR), BrewMethod.PULSAR, null, null)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.failure)
        assertEquals(BrewMethod.PULSAR, viewModel.uiState.value.completedSubmission?.defaultMethod)
    }
}
