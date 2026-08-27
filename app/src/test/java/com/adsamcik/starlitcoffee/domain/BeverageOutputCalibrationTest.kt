package com.adsamcik.starlitcoffee.domain

import com.adsamcik.starlitcoffee.data.model.BrewMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BeverageOutputCalibrationTest {
    @Test
    fun `built-in model is used before measurements exist`() {
        val profile = resolve()

        assertEquals(BeverageOutputCalibration.Scope.BUILT_IN, profile.scope)
        assertEquals(2.1f, profile.apparentLossGPerCoffeeG, 0.001f)
        assertEquals(0, profile.sampleCount)
    }

    @Test
    fun `first two measurements share equal weight with prior`() {
        val first = resolve(observations = listOf(observation(loss = 2.5f, createdAt = 1L)))
        val second = resolve(
            observations = listOf(
                observation(loss = 2.5f, createdAt = 1L),
                observation(loss = 3f, createdAt = 2L),
            ),
        )

        assertEquals((2.1f + 2.5f) / 2f, first.apparentLossGPerCoffeeG, 0.001f)
        assertEquals((2.1f + 2.5f + 3f) / 3f, second.apparentLossGPerCoffeeG, 0.001f)
    }

    @Test
    fun `third measurement drops prior and weights recent observations`() {
        val profile = resolve(
            observations = listOf(
                observation(loss = 2f, createdAt = 1L),
                observation(loss = 2.5f, createdAt = 2L),
                observation(loss = 3f, createdAt = 3L),
            ),
        )
        val expected = (3f + 2.5f * 0.85f + 2f * 0.85f * 0.85f) /
            (1f + 0.85f + 0.85f * 0.85f)

        assertEquals(expected, profile.apparentLossGPerCoffeeG, 0.001f)
    }

    @Test
    fun `bean uses other matching process observations only as its prior`() {
        val profile = resolve(
            coffeeBagId = 7L,
            observations = listOf(
                observation(loss = 2.8f, coffeeBagId = 7L, createdAt = 3L),
                observation(loss = 2.2f, coffeeBagId = 9L, createdAt = 2L),
                observation(loss = 3.8f, coffeeBagId = 9L, processKey = "other", createdAt = 1L),
            ),
        )

        val processPrior = (2.1f + 2.2f) / 2f
        assertEquals((processPrior + 2.8f) / 2f, profile.apparentLossGPerCoffeeG, 0.001f)
        assertEquals(BeverageOutputCalibration.Scope.PROCESS_AND_BEAN, profile.scope)
        assertEquals(1, profile.sampleCount)
        assertEquals(1, profile.priorSampleCount)
    }

    @Test
    fun `invalid and physically implausible observations are ignored`() {
        val profile = resolve(
            observations = listOf(
                observation(loss = 2f, water = Float.NaN),
                observation(loss = 2f, dose = 0f),
                observation(loss = -1f),
                observation(loss = 4.1f),
            ),
        )

        assertEquals(BeverageOutputCalibration.Scope.BUILT_IN, profile.scope)
        assertEquals(2.1f, profile.apparentLossGPerCoffeeG, 0.001f)
    }

    @Test
    fun `unsupported methods never produce a calibration profile`() {
        assertNull(
            BeverageOutputCalibration.resolve(
                method = BrewMethod.ESPRESSO,
                processKey = PROCESS,
                coffeeBagId = null,
                observations = emptyList(),
            ),
        )
    }

    private fun resolve(
        coffeeBagId: Long? = null,
        observations: List<BeverageOutputCalibration.Observation> = emptyList(),
    ) = requireNotNull(
        BeverageOutputCalibration.resolve(
            method = BrewMethod.V60,
            processKey = PROCESS,
            coffeeBagId = coffeeBagId,
            observations = observations,
        ),
    )

    private fun observation(
        loss: Float,
        coffeeBagId: Long? = null,
        processKey: String = PROCESS,
        dose: Float = 20f,
        water: Float = 340f,
        createdAt: Long = 1L,
    ) = BeverageOutputCalibration.Observation(
        processKey = processKey,
        coffeeBagId = coffeeBagId,
        coffeeDoseG = dose,
        measuredWaterInputG = water,
        measuredBeverageOutputG = water - dose * loss,
        createdAt = createdAt,
    )

    private companion object {
        const val PROCESS = "v60_unspecified|paper"
    }
}
