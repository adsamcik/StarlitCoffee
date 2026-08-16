package com.adsamcik.starlitcoffee.ui.component

import com.adsamcik.starlitcoffee.data.db.entity.BrewLogEntity
import com.adsamcik.starlitcoffee.data.db.entity.CoffeeUsageEntryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogCoffeeUseDialogTest {
    @Test
    fun `valid amount accepts decimal comma`() {
        val result = validateCoffeeUseInput("18,5", remainingG = 100f)

        assertTrue(result.isValid)
        assertEquals(18.5f, result.amountG ?: 0f, 0.01f)
    }

    @Test
    fun `blank zero negative and nonnumeric amounts are invalid`() {
        listOf("", "0", "-2", "coffee").forEach { text ->
            val result = validateCoffeeUseInput(text, remainingG = 100f)

            assertFalse(result.isValid)
            assertEquals(CoffeeUseInputError.INVALID_AMOUNT, result.error)
        }
    }

    @Test
    fun `amount above remaining weight is rejected`() {
        val result = validateCoffeeUseInput("18", remainingG = 12f)

        assertFalse(result.isValid)
        assertEquals(CoffeeUseInputError.EXCEEDS_REMAINING, result.error)
    }

    @Test
    fun `amount remains valid when bag weight is unknown`() {
        assertTrue(validateCoffeeUseInput("18", remainingG = null).isValid)
    }

    @Test
    fun `suggestion uses the most recent manual or guided amount`() {
        val brew = brewLog(doseG = 20f, createdAt = 100L)
        val use = usage(amountG = 18f, createdAt = 200L)

        assertEquals(18f, suggestedCoffeeUseAmount(listOf(brew), listOf(use)), 0.01f)
        assertEquals(
            20f,
            suggestedCoffeeUseAmount(
                listOf(brew.copy(createdAt = 300L)),
                listOf(use),
            ),
            0.01f,
        )
    }

    @Test
    fun `estimated amount averages manual and guided usage`() {
        val average = averageLoggedCoffeeAmount(
            brewLogs = listOf(brewLog(doseG = 20f, createdAt = 100L)),
            coffeeUsageEntries = listOf(usage(amountG = 18f, createdAt = 200L)),
        )

        assertEquals(19f, average, 0.01f)
    }

    @Test
    fun `suggestion and average default to twenty grams without history`() {
        assertEquals(20f, suggestedCoffeeUseAmount(emptyList(), emptyList()), 0.01f)
        assertEquals(20f, averageLoggedCoffeeAmount(emptyList(), emptyList()), 0.01f)
    }

    private fun brewLog(doseG: Float, createdAt: Long) = BrewLogEntity(
        method = "V60",
        doseG = doseG,
        waterG = doseG * 16f,
        ratio = 16f,
        createdAt = createdAt,
    )

    private fun usage(amountG: Float, createdAt: Long) = CoffeeUsageEntryEntity(
        id = createdAt,
        coffeeBagId = 7L,
        amountG = amountG,
        createdAt = createdAt,
    )
}
