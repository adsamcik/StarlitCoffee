package com.adsamcik.starlitcoffee.domain

import com.adsamcik.starlitcoffee.data.model.BrewMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BeverageOutputEstimatorTest {

    @Test
    fun `supported methods use research-backed central coefficients`() {
        val expected = mapOf(
            BrewMethod.PULSAR to 2.2f,
            BrewMethod.V60 to 2.1f,
            BrewMethod.FRENCH_PRESS to 2.2f,
            BrewMethod.AEROPRESS to 1.8f,
            BrewMethod.COLD_BREW to 2.0f,
        )

        expected.forEach { (method, coefficient) ->
            assertEquals(
                coefficient,
                requireNotNull(BeverageOutputEstimator.modelFor(method)).apparentLossGPerCoffeeG,
                0.001f,
            )
        }
    }

    @Test
    fun `moka and espresso do not use the generic water-loss model`() {
        assertNull(BeverageOutputEstimator.modelFor(BrewMethod.MOKA_POT))
        assertNull(BeverageOutputEstimator.modelFor(BrewMethod.ESPRESSO))
    }

    @Test
    fun `estimate output subtracts apparent loss from water input`() {
        val plan = requireNotNull(
            BeverageOutputEstimator.estimateOutput(
                method = BrewMethod.V60,
                coffeeDoseG = 20f,
                brewWaterG = 300f,
            ),
        )

        assertEquals(42f, plan.apparentLossG, 0.001f)
        assertEquals(258f, plan.beverageOutputG, 0.001f)
    }

    @Test
    fun `inverse plan adds enough water to target collected beverage`() {
        val plan = requireNotNull(
            BeverageOutputEstimator.planForOutput(
                method = BrewMethod.V60,
                beverageOutputG = 300f,
                brewRatio = 16f,
            ),
        )

        assertEquals(300f / (16f - 2.1f), plan.coffeeDoseG, 0.001f)
        assertEquals(plan.coffeeDoseG * 16f, plan.brewWaterG, 0.001f)
        assertEquals(300f, plan.brewWaterG - plan.apparentLossG, 0.001f)
    }

    @Test
    fun `inverse plan rejects ratios that cannot produce a positive output`() {
        assertNull(
            BeverageOutputEstimator.planForOutput(
                method = BrewMethod.V60,
                beverageOutputG = 100f,
                brewRatio = 2f,
            ),
        )
    }

    @Test
    fun `french press model flags unmeasured decant residual`() {
        val model = requireNotNull(BeverageOutputEstimator.modelFor(BrewMethod.FRENCH_PRESS))

        assertTrue(model.caveat == BeverageOutputEstimator.Caveat.EXCLUDES_DECANT_RESIDUAL)
    }
}
