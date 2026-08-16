package com.adsamcik.starlitcoffee.data.inventory

import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoffeeUsagePlannerTest {
    @Test
    fun `manual use opens a sealed bag and subtracts its amount`() {
        val bag = bag(status = "SEALED", weightG = 250f)

        val result = CoffeeUsagePlanner.plan(bag, amountG = 18f, usedAt = USED_AT)
            as CoffeeUsagePlanResult.Planned

        assertEquals("OPEN", result.plan.updatedBag.status)
        assertEquals(USED_AT, result.plan.updatedBag.openedDate)
        assertEquals(232f, result.plan.updatedBag.weightG ?: 0f, 0.01f)
    }

    @Test
    fun `manual use finishes a bag when it consumes the remaining amount`() {
        val result = CoffeeUsagePlanner.plan(
            bag = bag(status = "OPEN", weightG = 18f),
            amountG = 18f,
            usedAt = USED_AT,
        ) as CoffeeUsagePlanResult.Planned

        assertEquals("FINISHED", result.plan.updatedBag.status)
        assertEquals(0f, result.plan.updatedBag.weightG ?: -1f, 0.01f)
    }

    @Test
    fun `manual use finishes a depleted frozen bag`() {
        val result = CoffeeUsagePlanner.plan(
            bag = bag(status = "FROZEN", weightG = 18f),
            amountG = 18f,
            usedAt = USED_AT,
        ) as CoffeeUsagePlanResult.Planned

        assertEquals("FINISHED", result.plan.updatedBag.status)
        assertEquals(0f, result.plan.updatedBag.weightG ?: -1f, 0.01f)
    }

    @Test
    fun `manual use retains unknown remaining weight while recording use`() {
        val result = CoffeeUsagePlanner.plan(
            bag = bag(status = "SEALED", weightG = null),
            amountG = 20f,
            usedAt = USED_AT,
        ) as CoffeeUsagePlanResult.Planned

        assertEquals("OPEN", result.plan.updatedBag.status)
        assertNull(result.plan.updatedBag.weightG)
    }

    @Test
    fun `manual use rejects an amount larger than tracked remaining weight`() {
        val result = CoffeeUsagePlanner.plan(
            bag = bag(status = "OPEN", weightG = 12f),
            amountG = 18f,
            usedAt = USED_AT,
        ) as CoffeeUsagePlanResult.Rejected

        assertEquals(CoffeeUsageRejection.EXCEEDS_REMAINING, result.reason)
        assertEquals(12f, result.remainingG ?: 0f, 0.01f)
    }

    @Test
    fun `manual use rejects nonpositive and nonfinite amounts`() {
        listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY).forEach { amount ->
            val result = CoffeeUsagePlanner.plan(bag(), amountG = amount, usedAt = USED_AT)

            assertTrue(result is CoffeeUsagePlanResult.Rejected)
            assertEquals(
                CoffeeUsageRejection.INVALID_AMOUNT,
                (result as CoffeeUsagePlanResult.Rejected).reason,
            )
        }
    }

    @Test
    fun `manual use rejects a finished bag`() {
        val result = CoffeeUsagePlanner.plan(
            bag = bag(status = "FINISHED", weightG = 0f),
            amountG = 18f,
            usedAt = USED_AT,
        ) as CoffeeUsagePlanResult.Rejected

        assertEquals(CoffeeUsageRejection.BAG_FINISHED, result.reason)
    }

    private fun bag(
        status: String = "OPEN",
        weightG: Float? = 250f,
    ) = CoffeeBagEntity(
        id = 7L,
        name = "Test coffee",
        status = status,
        weightG = weightG,
        initialWeightG = weightG,
    )

    private companion object {
        const val USED_AT = 1_723_808_000_000L
    }
}
