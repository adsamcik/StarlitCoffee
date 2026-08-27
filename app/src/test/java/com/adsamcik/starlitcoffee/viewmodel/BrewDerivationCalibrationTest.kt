package com.adsamcik.starlitcoffee.viewmodel

import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.GrindRecommendation
import com.adsamcik.starlitcoffee.data.model.Grinder
import com.adsamcik.starlitcoffee.data.model.GrinderDataProvider
import com.adsamcik.starlitcoffee.data.model.InputMode
import com.adsamcik.starlitcoffee.domain.BeverageOutputCalibration
import org.junit.Assert.assertEquals
import org.junit.Test

class BrewDerivationCalibrationTest {

    @Test
    fun `derivation passes calibrated apparent loss into forward calculation`() {
        val result = BrewDerivation.derive(
            state = calibratedState(inputMode = InputMode.COFFEE_TO_WATER, amount = "20"),
            selectedBag = null,
            grinderData = EmptyGrinderData,
            nowMs = 0L,
        )

        assertEquals(50f, result.retainedWaterG, TOLERANCE)
        assertEquals(270f, result.predictedCupVolumeG, TOLERANCE)
    }

    @Test
    fun `derivation uses same calibrated loss for inverse cup target`() {
        val result = BrewDerivation.derive(
            state = calibratedState(inputMode = InputMode.BREW_SIZE_TO_BOTH, amount = "270"),
            selectedBag = null,
            grinderData = EmptyGrinderData,
            nowMs = 0L,
        )

        assertEquals(20f, result.coffeeG, TOLERANCE)
        assertEquals(320f, result.waterG, TOLERANCE)
        assertEquals(270f, result.predictedCupVolumeG, TOLERANCE)
    }

    private fun calibratedState(inputMode: InputMode, amount: String) = BrewUiState(
        method = BrewMethod.V60,
        inputMode = inputMode,
        amount = amount,
        customRatio = "16",
        beverageOutputCalibration = BeverageOutputCalibration.Profile(
            method = BrewMethod.V60,
            apparentLossGPerCoffeeG = 2.5f,
            sampleCount = 3,
            priorSampleCount = 0,
            scope = BeverageOutputCalibration.Scope.PROCESS,
        ),
    )

    private object EmptyGrinderData : GrinderDataProvider {
        override val grinders: List<Grinder> = emptyList()
        override val recommendations: List<GrindRecommendation> = emptyList()
    }

    private companion object {
        const val TOLERANCE = 0.001f
    }
}
