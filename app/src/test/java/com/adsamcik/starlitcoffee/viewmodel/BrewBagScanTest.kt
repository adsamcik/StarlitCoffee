package com.adsamcik.starlitcoffee.viewmodel

import com.adsamcik.starlitcoffee.data.network.ProductResult
import com.adsamcik.starlitcoffee.util.BagFieldCandidate
import com.adsamcik.starlitcoffee.util.BagFieldConfidence
import com.adsamcik.starlitcoffee.util.BagFieldSourceType
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
 * Characterization tests for the Open Food Facts barcode-enrichment branch of
 * the bag-photo pipeline (`addOpenFoodFactsCandidates`). They pin down the
 * confidence-gating and failure-degradation behavior before the scan pipeline
 * is extracted into a dedicated processor, using the injectable
 * [BrewViewModel] `openFoodFactsLookup` seam so no real network is involved.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BrewBagScanTest {

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun productResult(
        name: String? = null,
        brand: String? = null,
        origins: String? = null,
        countriesTags: List<String>? = null,
    ) = ProductResult(
        name = name,
        brand = brand,
        categories = null,
        imageUrl = null,
        quantity = null,
        origins = origins,
        countriesTags = countriesTags,
    )

    @Test
    fun `OFF lookup adds name and roaster candidates at HIGH confidence`() = runTest {
        val vm = BrewViewModel(openFoodFactsLookup = { productResult(name = "Espresso Blend", brand = "Roaster Co") })
        val candidates = mutableListOf<BagFieldCandidate>()

        val summary = vm.addOpenFoodFactsCandidates(candidates, "1234567890")

        val name = candidates.first { it.fieldName == "name" }
        assertEquals("Espresso Blend", name.value)
        assertEquals(BagFieldSourceType.BARCODE_LOOKUP, name.sourceType)
        assertEquals(BagFieldConfidence.HIGH, name.confidenceHint)

        val roaster = candidates.first { it.fieldName == "roaster" }
        assertEquals("Roaster Co", roaster.value)
        assertEquals(BagFieldConfidence.HIGH, roaster.confidenceHint)

        assertEquals("Espresso Blend", summary.name)
        assertEquals("Roaster Co", summary.brand)
    }

    @Test
    fun `OFF lookup adds free-text origins at MEDIUM confidence`() = runTest {
        val vm = BrewViewModel(openFoodFactsLookup = { productResult(name = "Blend", origins = "Ethiopia Yirgacheffe") })
        val candidates = mutableListOf<BagFieldCandidate>()

        vm.addOpenFoodFactsCandidates(candidates, "1234567890")

        val origin = candidates.first { it.fieldName == "origin" }
        assertEquals("Ethiopia Yirgacheffe", origin.value)
        assertEquals(BagFieldConfidence.MEDIUM, origin.confidenceHint)
    }

    @Test
    fun `OFF lookup detects a decaf marker in the product name`() = runTest {
        val vm = BrewViewModel(openFoodFactsLookup = { productResult(name = "Decaf House Blend", brand = "Roaster Co") })
        val candidates = mutableListOf<BagFieldCandidate>()

        vm.addOpenFoodFactsCandidates(candidates, "1234567890")

        val decaf = candidates.first { it.fieldName == "isDecaf" }
        assertEquals("Decaf", decaf.value)
        assertEquals(BagFieldSourceType.BARCODE_LOOKUP, decaf.sourceType)
    }

    @Test
    fun `OFF lookup returns empty summary and no candidates when product is missing`() = runTest {
        val vm = BrewViewModel(openFoodFactsLookup = { null })
        val candidates = mutableListOf<BagFieldCandidate>()

        val summary = vm.addOpenFoodFactsCandidates(candidates, "1234567890")

        assertTrue(candidates.isEmpty())
        assertNull(summary.name)
        assertNull(summary.brand)
    }

    @Test
    fun `OFF lookup is skipped entirely when no barcode was detected`() = runTest {
        var called = false
        val vm = BrewViewModel(openFoodFactsLookup = { called = true; null })
        val candidates = mutableListOf<BagFieldCandidate>()

        val summary = vm.addOpenFoodFactsCandidates(candidates, null)

        assertFalse("lookup must not run without a barcode", called)
        assertTrue(candidates.isEmpty())
        assertNull(summary.name)
    }

    @Test
    fun `OFF lookup failure degrades to an empty summary without crashing`() = runTest {
        val vm = BrewViewModel(openFoodFactsLookup = { throw RuntimeException("network down") })
        val candidates = mutableListOf<BagFieldCandidate>()

        val summary = vm.addOpenFoodFactsCandidates(candidates, "1234567890")

        assertTrue(candidates.isEmpty())
        assertNull(summary.name)
        assertNull(summary.brand)
    }
}
