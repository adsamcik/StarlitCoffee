package com.adsamcik.starlitcoffee.viewmodel

import com.adsamcik.starlitcoffee.data.brewing.snapshot.FilterSelectionSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.FilterStackEntrySnapshotV1
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.FilterType
import com.adsamcik.starlitcoffee.data.model.GrindRecommendation
import com.adsamcik.starlitcoffee.data.model.Grinder
import com.adsamcik.starlitcoffee.data.model.GrinderScaleType
import com.adsamcik.starlitcoffee.domain.brewing.CatalogResolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class LegacyBrewSessionStartFactoryTest {

    @Test
    fun `creates a UUID request that preserves current Pulsar configuration`() {
        val sessionUuid = UUID.fromString("b93b7d90-d815-4266-9249-2b1f6d2c66a9")
        val result = factory(sessionUuid).create(
            state = BrewUiState(
                method = BrewMethod.PULSAR,
                coffeeG = 18.5f,
                waterG = 314.5f,
                effectiveRatio = 17.0f,
                bloomG = 55.5f,
                effectivePulseCount = 5,
                effectiveBloomDurationSeconds = 48,
                tempC = "94",
                filterType = FilterType.PAPER,
                selectedGrinderId = "fellow_ode",
                grindResult = specificGrind(),
                isDecafBrew = true,
                feedbackNotes = "Sweet citrus finish",
            ),
            selectedCoffeeBagId = 71L,
            sourceRecipeId = 19L,
        )

        val request = (result as LegacyBrewSessionStartResult.Ready).request

        assertEquals(sessionUuid.toString(), request.sessionId.value)
        assertEquals("valve_controlled_no_bypass", request.recipe.methodFamilyId)
        assertEquals("pulsar_standard", request.recipe.brewerProfileId)
        assertEquals("pulsar_standard", request.recipe.equipment.brewerProfileId)
        assertEquals(
            FilterSelectionSnapshotV1(
                mode = "STACK",
                entries = listOf(
                    FilterStackEntrySnapshotV1(
                        filterProfileId = "pulsar_paper",
                        position = 0,
                        role = "PRIMARY",
                    ),
                ),
            ),
            request.recipe.equipment.filterSelection,
        )
        assertEquals(18.5, request.recipe.quantities.dryCoffeeDoseG)
        assertEquals(314.5, request.recipe.quantities.brewWaterInputG)
        assertEquals("DRY_COFFEE_DOSE", request.recipe.ratioDefinition.numerator)
        assertEquals("BREW_WATER_INPUT", request.recipe.ratioDefinition.denominator)
        assertEquals(17.0, request.recipe.ratioValue)
        assertEquals(94, request.recipe.temperatureC)
        assertEquals("fellow_ode", request.recipe.grinderId)
        assertEquals("4.0-6.0", request.recipe.grindSetting)
        assertEquals(55.5, request.recipe.technique.bloomWaterG)
        assertEquals(48, request.recipe.technique.bloomDurationSeconds)
        assertEquals("PULSED", request.recipe.technique.pourPattern)
        assertEquals(5, request.recipe.technique.pulseCount)
        assertEquals("BREW_WATER_MINUS_RETENTION", request.recipe.outputModel.kind)
        assertEquals(2.0, request.recipe.outputModel.retainedWaterGPerCoffeeG)
        assertTrue(request.recipe.isDecaf)
        assertEquals("Sweet citrus finish", request.recipe.notes)

        assertEquals(71L, request.executionContext.coffeeBagId)
        assertEquals(19L, request.executionContext.sourceRecipeId)
        assertEquals("PULSAR", request.executionContext.logPresentation.methodLabel)
        assertEquals(18.5, request.executionContext.logPresentation.doseG)
        assertEquals(314.5, request.executionContext.logPresentation.waterG)
        assertEquals(17.0, request.executionContext.logPresentation.ratio)
        assertEquals("4.0-6.0", request.executionContext.logPresentation.grindLabel)
        assertEquals("PAPER", request.executionContext.logPresentation.filterLabel)
        assertTrue(request.executionContext.logPresentation.isDecaf)
        assertEquals("Sweet citrus finish", request.executionContext.logPresentation.notes)

        assertEquals("legacy_pulsar", request.stagePlan.id.value)
        assertEquals(
            listOf("pulsar_bloom_1", "pulsar_manual_brew_1"),
            request.stagePlan.stageIds.map { it.persistentKey },
        )
    }

    @Test
    fun `each legacy method resolves its own catalog identity and compiled plan`() {
        val expectedIdentities = mapOf(
            BrewMethod.PULSAR to ("valve_controlled_no_bypass" to "pulsar_standard"),
            BrewMethod.V60 to ("manual_gravity" to "v60_unspecified"),
            BrewMethod.FRENCH_PRESS to ("full_immersion_press" to "french_press_generic"),
            BrewMethod.AEROPRESS to ("chamber_plunger" to "aeropress_standard"),
            BrewMethod.ESPRESSO to ("espresso" to "espresso_pump_generic"),
            BrewMethod.MOKA_POT to ("steam_pressure_multichamber" to "moka_generic_unspecified"),
            BrewMethod.COLD_BREW to ("cold_immersion" to "cold_immersion_generic"),
        )

        BrewMethod.entries.forEach { method ->
            val result = LegacyBrewSessionStartFactory().create(
                state = BrewUiState(
                    method = method,
                    coffeeG = 20f,
                    waterG = 320f,
                    effectiveRatio = method.defaultRatio,
                ),
                selectedCoffeeBagId = null,
                sourceRecipeId = null,
            )

            val request = (result as LegacyBrewSessionStartResult.Ready).request
            val (familyId, profileId) = requireNotNull(expectedIdentities[method])
            assertEquals(familyId, request.recipe.methodFamilyId)
            assertEquals(profileId, request.recipe.brewerProfileId)
            assertEquals("legacy_${method.name.lowercase()}", request.stagePlan.id.value)
            assertTrue(request.stagePlan.stages.isNotEmpty())
        }
    }

    @Test
    fun `invalid non-Pulsar filter remains an audit label instead of equipment`() {
        val result = LegacyBrewSessionStartFactory().create(
            state = BrewUiState(
                method = BrewMethod.V60,
                coffeeG = 20f,
                waterG = 320f,
                effectiveRatio = 16f,
                filterType = FilterType.PAPER,
            ),
            selectedCoffeeBagId = null,
            sourceRecipeId = null,
        )

        val request = (result as LegacyBrewSessionStartResult.Ready).request

        assertEquals("UNSPECIFIED", request.recipe.equipment.filterSelection.mode)
        assertTrue(request.recipe.equipment.filterSelection.entries.isEmpty())
        assertEquals("PAPER", request.executionContext.logPresentation.filterLabel)
    }

    @Test
    fun `unknown raw method stays unavailable without a Pulsar fallback`() {
        var allocatedIds = 0
        val factory = LegacyBrewSessionStartFactory(
            newUuid = {
                allocatedIds += 1
                UUID.fromString("0b3b7d90-d815-4266-9249-2b1f6d2c66a9")
            },
        )

        val result = factory.create(
            LegacyBrewSessionStartInput(
                state = BrewUiState(method = BrewMethod.PULSAR),
                selectedCoffeeBagId = 7L,
                sourceRecipeId = null,
                rawMethodId = "FUTURE_PRESS",
                rawFilterId = "FUTURE_MESH",
            ),
        )

        val unavailable = result as LegacyBrewSessionStartResult.Unavailable
        assertEquals(LegacyBrewSessionUnavailableReason.UNKNOWN_LEGACY_METHOD, unavailable.reason)
        assertEquals("FUTURE_PRESS", unavailable.rawMethodId)
        assertEquals("FUTURE_MESH", unavailable.rawFilterId)
        assertTrue(unavailable.legacyReference.brewerProfile is CatalogResolution.Unknown)
        assertEquals(0, allocatedIds)
    }

    @Test
    fun `unknown raw Pulsar filter remains visible in the durable log context`() {
        val result = LegacyBrewSessionStartFactory().create(
            LegacyBrewSessionStartInput(
                state = BrewUiState(
                    method = BrewMethod.PULSAR,
                    coffeeG = 20f,
                    waterG = 340f,
                    effectiveRatio = 17f,
                ),
                selectedCoffeeBagId = null,
                sourceRecipeId = null,
                rawMethodId = "PULSAR",
                rawFilterId = "FUTURE_MESH",
            ),
        )

        val request = (result as LegacyBrewSessionStartResult.Ready).request

        assertEquals("UNSPECIFIED", request.recipe.equipment.filterSelection.mode)
        assertEquals("FUTURE_MESH", request.executionContext.logPresentation.filterLabel)
    }

    private fun factory(sessionUuid: UUID): LegacyBrewSessionStartFactory = LegacyBrewSessionStartFactory(
        newUuid = { sessionUuid },
    )

    private fun specificGrind(): GrindResult.Specific = GrindResult.Specific(
        recommendation = GrindRecommendation(
            grinderId = "fellow_ode",
            methodId = BrewMethod.PULSAR.name,
            rangeStart = 4f,
            rangeEnd = 6f,
            suggestedStart = 5f,
            adjustmentStepSize = 0.5f,
            adjustmentNote = "Start at five",
        ),
        grinder = Grinder(
            id = "fellow_ode",
            brand = "Fellow",
            model = "Ode",
            isManual = false,
            scaleType = GrinderScaleType.NUMBERED_DIAL,
        ),
    )
}
