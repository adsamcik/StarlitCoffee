package com.adsamcik.starlitcoffee.ui.component

import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity
import com.adsamcik.starlitcoffee.util.CoffeeBagInsights
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BagCardPresentationTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `missing roast date prioritizes freshness warning and action`() {
        val bag = bag(
            roastDate = null,
            weightG = 250f,
            initialWeightG = 250f,
        )

        val summary = buildBagCardSummary(
            bag = bag,
            freshness = CoffeeBagInsights.freshnessInsight(
                roastDateMillis = bag.roastDate,
                nowMillis = NOW,
            ),
        )

        assertEquals("Add roast date", summary.primaryActionLabel)
        assertEquals(ChipEmphasis.WARNING, summary.freshnessEmphasis)
        assertTrue(summary.warningText.orEmpty().contains("roast date", ignoreCase = true))
        assertEquals("250g left", summary.stockLabel)
    }

    @Test
    fun `low stock highlights the bag and keeps compact stock math`() {
        val bag = bag(
            status = "OPEN",
            roastDate = NOW - DAYS * 10,
            weightG = 18f,
            initialWeightG = 250f,
        )

        val summary = buildBagCardSummary(
            bag = bag,
            freshness = CoffeeBagInsights.freshnessInsight(
                roastDateMillis = bag.roastDate,
                nowMillis = NOW,
            ),
        )

        assertEquals("Review bag", summary.primaryActionLabel)
        assertEquals(ChipEmphasis.CRITICAL, summary.stockEmphasis)
        assertTrue(summary.warningText.orEmpty().contains("low coffee", ignoreCase = true))
        assertTrue(summary.stockSupportingText.contains("250g", ignoreCase = true))
    }

    @Test
    fun `missing weight asks the user to add it before tracking stock`() {
        val bag = bag(
            roastDate = NOW - DAYS * 12,
            weightG = null,
            initialWeightG = null,
        )

        val summary = buildBagCardSummary(
            bag = bag,
            freshness = CoffeeBagInsights.freshnessInsight(
                roastDateMillis = bag.roastDate,
                nowMillis = NOW,
            ),
        )

        assertEquals("Add bag weight", summary.primaryActionLabel)
        assertEquals("Weight unknown", summary.stockLabel)
        assertNull(summary.stockProgress)
        assertTrue(summary.warningText.orEmpty().contains("weight", ignoreCase = true))
    }

    @Test
    fun `missing roast date and weight collapse into one follow-up action`() {
        val bag = bag(
            roastDate = null,
            weightG = null,
            initialWeightG = null,
        )

        val summary = buildBagCardSummary(
            bag = bag,
            freshness = CoffeeBagInsights.freshnessInsight(
                roastDateMillis = bag.roastDate,
                nowMillis = NOW,
            ),
        )

        assertEquals("Complete details", summary.primaryActionLabel)
        assertTrue(summary.warningText.orEmpty().contains("roast date", ignoreCase = true))
        assertTrue(summary.warningText.orEmpty().contains("weight", ignoreCase = true))
    }

    private fun bag(
        status: String = "SEALED",
        roastDate: Long?,
        weightG: Float?,
        initialWeightG: Float?,
    ) = CoffeeBagEntity(
        name = "Test Bag",
        status = status,
        roastDate = roastDate,
        weightG = weightG,
        initialWeightG = initialWeightG,
    )

    private companion object {
        const val DAYS = 24L * 60 * 60 * 1000
        const val NOW = DAYS * 30
    }
}
