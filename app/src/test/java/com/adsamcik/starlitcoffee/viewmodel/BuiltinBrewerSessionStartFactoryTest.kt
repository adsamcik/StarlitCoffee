package com.adsamcik.starlitcoffee.viewmodel

import com.adsamcik.starlitcoffee.domain.brewing.BrewerProfileId
import com.adsamcik.starlitcoffee.domain.brewing.EquipmentConfiguration
import com.adsamcik.starlitcoffee.domain.brewing.FilterProfileId
import com.adsamcik.starlitcoffee.domain.brewing.FilterSelection
import com.adsamcik.starlitcoffee.domain.brewing.FilterStackEntry
import com.adsamcik.starlitcoffee.domain.brewing.HeatSourceClass
import com.adsamcik.starlitcoffee.domain.brewing.session.HarioSwitchWorkflow
import com.adsamcik.starlitcoffee.domain.brewing.session.StagePlanCompileResult
import com.adsamcik.starlitcoffee.domain.brewing.session.StagePlanValidationCode
import com.adsamcik.starlitcoffee.domain.brewing.session.StagePlanValidationIssue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class BuiltinBrewerSessionStartFactoryTest {

    @Test
    fun `creates an immutable Clever session with profile defaults and caller input`() {
        val sessionId = UUID.fromString("1c4e2984-37b1-44ea-a4f7-9a004a32dd67")
        val result = factory(sessionId).create(
            BuiltinBrewerSessionStartInput(
                brewerProfileId = BrewerProfileId("clever_style"),
                dryCoffeeDoseG = 20.0,
                inputWaterG = 340.0,
                equipment = EquipmentConfiguration(
                    brewerProfileId = BrewerProfileId("clever_style"),
                    filterSelection = paperFilter("cone_paper"),
                    capacityOverrideG = 400.0,
                ),
                temperatureC = 93,
                grinderId = "fellow_ode",
                grindSetting = "5.0",
                methodLabel = "My Clever",
                filterLabel = "Cone paper",
                isDecaf = true,
                notes = "  Sweet and round  ",
                coffeeBagId = 81L,
                sourceRecipeId = 12L,
            ),
        )

        val ready = result as BuiltinBrewerSessionStartResult.Ready
        val request = ready.request

        assertEquals(sessionId.toString(), request.sessionId.value)
        assertEquals("steep_and_release", request.recipe.methodFamilyId)
        assertEquals("clever_style", request.recipe.brewerProfileId)
        assertEquals("clever_style", request.recipe.equipment.brewerProfileId)
        assertEquals("STACK", request.recipe.equipment.filterSelection.mode)
        assertEquals("cone_paper", request.recipe.equipment.filterSelection.entries.single().filterProfileId)
        assertEquals(20.0, request.recipe.quantities.dryCoffeeDoseG, 0.001)
        assertEquals(340.0, requireNotNull(request.recipe.quantities.brewWaterInputG), 0.001)
        assertNull(request.recipe.quantities.reservoirInputG)
        assertEquals("DRY_COFFEE_DOSE", request.recipe.ratioDefinition.numerator)
        assertEquals("BREW_WATER_INPUT", request.recipe.ratioDefinition.denominator)
        assertEquals(17.0, requireNotNull(request.recipe.ratioValue), 0.001)
        assertEquals(93, request.recipe.temperatureC)
        assertEquals("fellow_ode", request.recipe.grinderId)
        assertEquals("5.0", request.recipe.grindSetting)
        assertEquals("builtin_clever_style", request.recipe.technique.stagePlanVariantId)
        assertEquals("BREW_WATER_MINUS_RETENTION", request.recipe.outputModel.kind)
        assertEquals(2.0, requireNotNull(request.recipe.outputModel.retainedWaterGPerCoffeeG), 0.001)
        assertTrue(request.recipe.isDecaf)
        assertEquals("Sweet and round", request.recipe.notes)
        assertEquals("builtin_clever_style", request.stagePlan.id.value)
        assertTrue(request.stagePlan.stages.isNotEmpty())

        assertEquals(81L, request.executionContext.coffeeBagId)
        assertEquals(12L, request.executionContext.sourceRecipeId)
        assertEquals("My Clever", request.executionContext.logPresentation.methodLabel)
        assertEquals(340.0, request.executionContext.logPresentation.waterG, 0.001)
        assertEquals("Cone paper", request.executionContext.logPresentation.filterLabel)
        assertTrue(request.executionContext.logPresentation.isDecaf)
        assertEquals("Sweet and round", request.executionContext.logPresentation.notes)
        assertTrue(ready.equipmentCompatibility.issues.isEmpty())
    }

    @Test
    fun `automatic brewer defaults use reservoir input and observed machine plan`() {
        val result = factory().create(
            BuiltinBrewerSessionStartInput(
                brewerProfileId = BrewerProfileId("automatic_batch_generic"),
                dryCoffeeDoseG = 20.0,
                equipment = EquipmentConfiguration(
                    brewerProfileId = BrewerProfileId("automatic_batch_generic"),
                    filterSelection = paperFilter("cone_paper"),
                    capacityOverrideG = 400.0,
                ),
            ),
        )

        val request = (result as BuiltinBrewerSessionStartResult.Ready).request

        assertEquals(320.0, requireNotNull(request.recipe.quantities.reservoirInputG), 0.001)
        assertNull(request.recipe.quantities.brewWaterInputG)
        assertEquals("RESERVOIR_INPUT", request.recipe.ratioDefinition.denominator)
        assertEquals("RESERVOIR_TO_ESTIMATED_OUTPUT", request.recipe.outputModel.kind)
        assertEquals("builtin_automatic_batch_generic", request.stagePlan.id.value)
        assertTrue(
            request.stagePlan.stages.any { stage ->
                stage.definition.id.value == "automatic_batch_generic_observe_machine_completion"
            },
        )
    }

    @Test
    fun `Hario Switch requires an explicit workflow and stores the chosen plan variant`() {
        var allocatedIds = 0
        val factory = BuiltinBrewerSessionStartFactory(
            newUuid = {
                allocatedIds += 1
                UUID.fromString("2c4e2984-37b1-44ea-a4f7-9a004a32dd67")
            },
        )
        val missingWorkflow = factory.create(
            BuiltinBrewerSessionStartInput(
                brewerProfileId = BrewerProfileId("hario_switch"),
                dryCoffeeDoseG = 20.0,
                equipment = EquipmentConfiguration(
                    brewerProfileId = BrewerProfileId("hario_switch"),
                    capacityOverrideG = 400.0,
                ),
            ),
        )

        val invalid = missingWorkflow as BuiltinBrewerSessionStartResult.InvalidSetup
        assertEquals(
            listOf(BuiltinBrewerSessionStartValidationCode.HARIO_SWITCH_WORKFLOW_REQUIRED),
            invalid.issues.map(BuiltinBrewerSessionStartValidationIssue::code),
        )
        assertEquals(0, allocatedIds)

        val explicitWorkflow = factory.create(
            BuiltinBrewerSessionStartInput(
                brewerProfileId = BrewerProfileId("hario_switch"),
                dryCoffeeDoseG = 20.0,
                equipment = EquipmentConfiguration(
                    brewerProfileId = BrewerProfileId("hario_switch"),
                    capacityOverrideG = 400.0,
                ),
                harioSwitchWorkflow = HarioSwitchWorkflow.MANUAL_GRAVITY,
            ),
        )

        val request = (explicitWorkflow as BuiltinBrewerSessionStartResult.Ready).request
        assertEquals("builtin_hario_switch_manual_gravity", request.stagePlan.id.value)
        assertEquals(
            "builtin_hario_switch_manual_gravity",
            request.recipe.technique.stagePlanVariantId,
        )
        assertTrue(
            request.stagePlan.stages.any { stage ->
                stage.definition.id.value == "hario_switch_open_valve_for_manual_gravity"
            },
        )
        assertEquals(1, allocatedIds)
    }

    @Test
    fun `Cezve setup compiles its explicit sugar and bounded foam choices`() {
        val result = factory().create(
            BuiltinBrewerSessionStartInput(
                brewerProfileId = BrewerProfileId("cezve_generic"),
                dryCoffeeDoseG = 8.0,
                equipment = EquipmentConfiguration(
                    brewerProfileId = BrewerProfileId("cezve_generic"),
                    filterSelection = FilterSelection.IntentionallyUnfiltered,
                    capacityOverrideG = 120.0,
                    heatSource = HeatSourceClass.HOB,
                ),
                cezveSetup = CezveSessionSetup(includeSugar = true, foamRiseCycles = 2),
            ),
        )

        val ready = result as BuiltinBrewerSessionStartResult.Ready
        val request = ready.request
        val stageIds = request.stagePlan.stages.map { stage -> stage.definition.id.value }

        assertEquals(80.0, requireNotNull(request.recipe.quantities.brewWaterInputG), 0.001)
        assertEquals(120.0, requireNotNull(request.recipe.equipment.capacityOverrideG), 0.001)
        assertEquals(HeatSourceClass.HOB.name, request.recipe.equipment.heatSource)
        assertEquals("PREPARED_UNFILTERED_VOLUME", request.recipe.outputModel.kind)
        assertTrue(stageIds.contains("cezve_generic_add_sugar_before_heating"))
        assertEquals(2, stageIds.count { id -> id == "cezve_generic_apply_gentle_heat" })
        assertTrue(
            ready.equipmentCompatibility.issues.any { issue -> issue.code == "heat_safety_required" },
        )
    }

    @Test
    fun `phin keeps concentrate semantics and accepts its compatible metal filter`() {
        val result = factory().create(
            BuiltinBrewerSessionStartInput(
                brewerProfileId = BrewerProfileId("vietnamese_phin"),
                dryCoffeeDoseG = 20.0,
                equipment = EquipmentConfiguration(
                    brewerProfileId = BrewerProfileId("vietnamese_phin"),
                    filterSelection = paperFilter("phin_metal"),
                    capacityOverrideG = 120.0,
                ),
            ),
        )

        val request = (result as BuiltinBrewerSessionStartResult.Ready).request

        assertEquals(100.0, requireNotNull(request.recipe.quantities.brewWaterInputG), 0.001)
        assertNull(request.recipe.quantities.finalServedBeverageG)
        assertEquals("COLLECTED_CONCENTRATE", request.recipe.outputModel.kind)
        assertEquals("builtin_vietnamese_phin", request.stagePlan.id.value)
    }

    @Test
    fun `unknown and known but unsupported profiles remain unavailable without a fallback`() {
        var allocatedIds = 0
        val factory = BuiltinBrewerSessionStartFactory(
            newUuid = {
                allocatedIds += 1
                UUID.fromString("3c4e2984-37b1-44ea-a4f7-9a004a32dd67")
            },
        )

        val unknown = factory.create(
            BuiltinBrewerSessionStartInput(
                brewerProfileId = BrewerProfileId("future_brewer"),
                dryCoffeeDoseG = 20.0,
            ),
        )
        val manualGravityWithoutP1Plan = factory.create(
            BuiltinBrewerSessionStartInput(
                brewerProfileId = BrewerProfileId("v60_02"),
                dryCoffeeDoseG = 20.0,
            ),
        )

        assertEquals(
            BuiltinBrewerSessionStartUnavailableReason.UNKNOWN_BREWER_PROFILE,
            (unknown as BuiltinBrewerSessionStartResult.Unavailable).reason,
        )
        assertEquals(
            BuiltinBrewerSessionStartUnavailableReason.NO_BUILTIN_STAGE_PLAN,
            (manualGravityWithoutP1Plan as BuiltinBrewerSessionStartResult.Unavailable).reason,
        )
        assertEquals(0, allocatedIds)
    }

    @Test
    fun `invalid setup and blocking equipment fail before allocating a session ID`() {
        var allocatedIds = 0
        val factory = BuiltinBrewerSessionStartFactory(
            newUuid = {
                allocatedIds += 1
                UUID.fromString("4c4e2984-37b1-44ea-a4f7-9a004a32dd67")
            },
        )

        val invalidCezve = factory.create(
            BuiltinBrewerSessionStartInput(
                brewerProfileId = BrewerProfileId("cezve_generic"),
                dryCoffeeDoseG = 8.0,
                equipment = EquipmentConfiguration(
                    brewerProfileId = BrewerProfileId("cezve_generic"),
                    capacityOverrideG = 120.0,
                    filterSelection = FilterSelection.IntentionallyUnfiltered,
                    heatSource = HeatSourceClass.HOB,
                ),
                cezveSetup = CezveSessionSetup(foamRiseCycles = 3),
            ),
        )
        val incompatibleFilter = factory.create(
            BuiltinBrewerSessionStartInput(
                brewerProfileId = BrewerProfileId("clever_style"),
                dryCoffeeDoseG = 20.0,
                equipment = EquipmentConfiguration(
                    brewerProfileId = BrewerProfileId("clever_style"),
                    filterSelection = FilterSelection.IntentionallyUnfiltered,
                    capacityOverrideG = 400.0,
                ),
            ),
        )

        assertEquals(
            listOf(BuiltinBrewerSessionStartValidationCode.INVALID_CEZVE_FOAM_RISE_CYCLES),
            (invalidCezve as BuiltinBrewerSessionStartResult.InvalidSetup)
                .issues
                .map(BuiltinBrewerSessionStartValidationIssue::code),
        )
        val equipmentIssue = (incompatibleFilter as BuiltinBrewerSessionStartResult.InvalidSetup)
            .issues
            .single()
        assertEquals(BuiltinBrewerSessionStartValidationCode.INCOMPATIBLE_EQUIPMENT, equipmentIssue.code)
        assertEquals(listOf("unfiltered_not_supported"), equipmentIssue.equipmentIssues.map { it.code })
        assertEquals(0, allocatedIds)
    }

    @Test
    fun `P1 capacity and Cezve heat safety are validated before persistence`() {
        val missingCapacity = factory().create(
            BuiltinBrewerSessionStartInput(
                brewerProfileId = BrewerProfileId("clever_style"),
                dryCoffeeDoseG = 20.0,
                equipment = EquipmentConfiguration(BrewerProfileId("clever_style")),
            ),
        )
        val invalidCapacity = factory().create(
            BuiltinBrewerSessionStartInput(
                brewerProfileId = BrewerProfileId("clever_style"),
                dryCoffeeDoseG = 20.0,
                equipment = EquipmentConfiguration(
                    brewerProfileId = BrewerProfileId("clever_style"),
                    capacityOverrideG = Double.NaN,
                ),
            ),
        )
        val defaultWaterExceedsCapacity = factory().create(
            BuiltinBrewerSessionStartInput(
                brewerProfileId = BrewerProfileId("clever_style"),
                dryCoffeeDoseG = 20.0,
                equipment = EquipmentConfiguration(
                    brewerProfileId = BrewerProfileId("clever_style"),
                    capacityOverrideG = 319.0,
                ),
            ),
        )
        val cezveWithoutHeat = factory().create(
            BuiltinBrewerSessionStartInput(
                brewerProfileId = BrewerProfileId("cezve_generic"),
                dryCoffeeDoseG = 8.0,
                equipment = EquipmentConfiguration(
                    brewerProfileId = BrewerProfileId("cezve_generic"),
                    capacityOverrideG = 120.0,
                    filterSelection = FilterSelection.IntentionallyUnfiltered,
                ),
            ),
        )

        assertEquals(
            listOf(BuiltinBrewerSessionStartValidationCode.MISSING_EQUIPMENT_CAPACITY),
            (missingCapacity as BuiltinBrewerSessionStartResult.InvalidSetup)
                .issues
                .map(BuiltinBrewerSessionStartValidationIssue::code),
        )
        assertEquals(
            listOf(BuiltinBrewerSessionStartValidationCode.INVALID_CAPACITY_OVERRIDE),
            (invalidCapacity as BuiltinBrewerSessionStartResult.InvalidSetup)
                .issues
                .map(BuiltinBrewerSessionStartValidationIssue::code),
        )
        assertEquals(
            listOf(BuiltinBrewerSessionStartValidationCode.INPUT_WATER_EXCEEDS_EQUIPMENT_CAPACITY),
            (defaultWaterExceedsCapacity as BuiltinBrewerSessionStartResult.InvalidSetup)
                .issues
                .map(BuiltinBrewerSessionStartValidationIssue::code),
        )
        assertEquals(
            listOf(BuiltinBrewerSessionStartValidationCode.CEZVE_HEAT_SOURCE_REQUIRED),
            (cezveWithoutHeat as BuiltinBrewerSessionStartResult.InvalidSetup)
                .issues
                .map(BuiltinBrewerSessionStartValidationIssue::code),
        )
    }

    @Test
    fun `compiler failures return inspectable invalid stage plans`() {
        val factory = BuiltinBrewerSessionStartFactory(
            compileStagePlan = { _, _ ->
                StagePlanCompileResult.Invalid(
                    listOf(StagePlanValidationIssue(StagePlanValidationCode.EMPTY_PLAN, "plan")),
                )
            },
        )

        val result = factory.create(
            BuiltinBrewerSessionStartInput(
                brewerProfileId = BrewerProfileId("clever_style"),
                dryCoffeeDoseG = 20.0,
                equipment = EquipmentConfiguration(
                    brewerProfileId = BrewerProfileId("clever_style"),
                    capacityOverrideG = 400.0,
                ),
            ),
        )

        val invalid = result as BuiltinBrewerSessionStartResult.InvalidStagePlan
        assertEquals(BrewerProfileId("clever_style"), invalid.brewerProfileId)
        assertEquals(
            listOf(StagePlanValidationIssue(StagePlanValidationCode.EMPTY_PLAN, "plan")),
            invalid.issues,
        )
    }

    private fun factory(sessionId: UUID = UUID.fromString("5c4e2984-37b1-44ea-a4f7-9a004a32dd67")):
        BuiltinBrewerSessionStartFactory = BuiltinBrewerSessionStartFactory(newUuid = { sessionId })

    private fun paperFilter(id: String): FilterSelection.Stack = FilterSelection.Stack(
        entries = listOf(FilterStackEntry(FilterProfileId(id), position = 0)),
    )
}
