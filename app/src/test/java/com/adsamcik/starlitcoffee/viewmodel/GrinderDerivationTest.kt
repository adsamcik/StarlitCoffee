package com.adsamcik.starlitcoffee.viewmodel

import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.CalibrationStyle
import com.adsamcik.starlitcoffee.data.model.FilterType
import com.adsamcik.starlitcoffee.data.model.GrinderDataSource
import com.adsamcik.starlitcoffee.data.model.grinderCatalogAsset
import org.junit.Assert.assertTrue
import org.junit.Test

class GrinderDerivationTest {
    private val catalog = GrinderDataSource.fromJson(grinderCatalogAsset())

    @Test
    fun `all published guidance remains physically settable with decaf and uncertain calibration`() {
        catalog.recommendations.forEach { recommendation ->
            listOf(false, true).forEach { decaf ->
                listOf(null, CalibrationStyle.FACTORY, CalibrationStyle.UNKNOWN).forEach { calibration ->
                    val state = BrewUiState(
                        method = BrewMethod.valueOf(recommendation.methodId),
                        filterType = recommendation.filterType,
                        selectedGrinderId = recommendation.grinderId,
                        manualDecafOverride = decaf,
                        calibrationStyle = calibration,
                    )
                    val result = BrewDerivation.derive(state, null, catalog, 0L).grindResult
                    assertTrue("${recommendation.grinderId}/${recommendation.methodId}", result is GrindResult.Specific)
                    val specific = result as GrindResult.Specific
                    val dial = requireNotNull(specific.grinder.dial)
                    val adjusted = specific.recommendation
                    assertTrue(dial.accepts(adjusted.suggestedStart))
                    assertTrue(dial.accepts(adjusted.rangeStart))
                    assertTrue(dial.accepts(adjusted.rangeEnd))
                    assertTrue("${recommendation.grinderId}/${recommendation.methodId}, " +
                        "decaf=$decaf, calibration=$calibration: $adjusted",
                        adjusted.suggestedStart in adjusted.rangeStart..adjusted.rangeEnd)
                }
            }
        }
    }

    @Test
    fun `retired grinders and unsupported methods or filters yield generic guidance`() {
        listOf(
            BrewUiState(selectedGrinderId = "df64", filterType = FilterType.PAPER),
            BrewUiState(selectedGrinderId = "1zpresso-zp6-special", method = BrewMethod.ESPRESSO),
            BrewUiState(selectedGrinderId = "fellow-ode-gen2", filterType = FilterType.METAL_40K),
        ).forEach { state ->
            assertTrue(BrewDerivation.derive(state, null, catalog, 0L).grindResult is GrindResult.Generic)
        }
    }
}
