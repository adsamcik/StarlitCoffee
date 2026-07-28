package com.adsamcik.starlitcoffee.viewmodel

import com.adsamcik.starlitcoffee.domain.brewing.BasketProfileId
import com.adsamcik.starlitcoffee.domain.brewing.BrewerProfileId
import com.adsamcik.starlitcoffee.domain.brewing.BuiltInRecipeId
import com.adsamcik.starlitcoffee.domain.brewing.EquipmentConfiguration
import com.adsamcik.starlitcoffee.domain.brewing.FilterProfileId
import com.adsamcik.starlitcoffee.domain.brewing.FilterSelection
import com.adsamcik.starlitcoffee.domain.brewing.FilterStackEntry
import com.adsamcik.starlitcoffee.domain.brewing.HeatSourceClass
import com.adsamcik.starlitcoffee.domain.brewing.StageContentId
import com.adsamcik.starlitcoffee.domain.brewing.StageId
import com.adsamcik.starlitcoffee.domain.brewing.StagePlanId
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStageAction
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStageDefinition
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStagePlan
import com.adsamcik.starlitcoffee.domain.brewing.session.HarioSwitchWorkflow
import com.adsamcik.starlitcoffee.domain.brewing.session.StageCompletionMode
import com.adsamcik.starlitcoffee.domain.brewing.session.StagePlanNode
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
    fun `legacy Clever start remains unchanged without an exact recipe ID`() {
        val sessionId = UUID.fromString("1c4e2984-37b1-44ea-a4f7-9a004a32dd67")
        val result = factory(sessionId).create(
            BuiltinBrewerSessionStartInput(
                brewerProfileId = BrewerProfileId("clever_style"),
                dryCoffeeDoseG = 20.0,
                inputWaterG = 340.0,
                equipment = EquipmentConfiguration(
                    brewerProfileId = BrewerProfileId("clever_style"),
                    filterSelection = paperFilter("wedge_paper"),
                    capacityOverrideG = 400.0,
                ),
                temperatureC = 93,
                grinderId = "fellow_ode",
                grindSetting = "5.0",
                methodLabel = "My Clever",
                filterLabel = "Wedge paper",
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
        assertNull(request.recipe.builtInRecipeId)
        assertNull(request.recipe.sourceMetadata)
        assertEquals("clever_style", request.recipe.equipment.brewerProfileId)
        assertEquals("STACK", request.recipe.equipment.filterSelection.mode)
        assertEquals("wedge_paper", request.recipe.equipment.filterSelection.entries.single().filterProfileId)
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
        assertEquals("Wedge paper", request.executionContext.logPresentation.filterLabel)
        assertTrue(request.executionContext.logPresentation.isDecaf)
        assertEquals("Sweet and round", request.executionContext.logPresentation.notes)
        assertTrue(ready.equipmentCompatibility.issues.isEmpty())
    }

    @Test
    fun `exact flash recipe defaults source input and preserves all source metadata`() {
        val recipeId = BuiltInRecipeId("v60_kurasu_flash_16_150_70")
        val result = exactFactory(
            recipeId = recipeId,
            actions = listOf(
                BrewStageAction.PREPARE,
                BrewStageAction.ADD_COFFEE,
                BrewStageAction.ADD_WATER,
                BrewStageAction.POUR,
                BrewStageAction.OBSERVE,
                BrewStageAction.SERVE,
            ),
        ).create(
            BuiltinBrewerSessionStartInput(
                brewerProfileId = BrewerProfileId("v60_unspecified"),
                builtInRecipeId = recipeId,
                dryCoffeeDoseG = 16.0,
                equipment = EquipmentConfiguration(
                    brewerProfileId = BrewerProfileId("v60_unspecified"),
                    filterSelection = paperFilter("cone_paper"),
                ),
                temperatureC = 91,
            ),
        )

        val request = (result as BuiltinBrewerSessionStartResult.Ready).request
        val metadata = requireNotNull(request.recipe.sourceMetadata)

        assertEquals("builtin_recipe_v60_kurasu_flash_16_150_70", request.stagePlan.id.value)
        assertEquals(16.0, request.recipe.quantities.dryCoffeeDoseG, 0.001)
        assertEquals(150.0, requireNotNull(request.recipe.quantities.brewWaterInputG), 0.001)
        assertEquals(70.0, request.recipe.quantities.iceG, 0.001)
        assertEquals(9.375, requireNotNull(request.recipe.ratioValue), 0.001)
        assertEquals(150.0, request.executionContext.logPresentation.waterG, 0.001)
        assertEquals(2, request.recipe.ratioSemantics.size)
        assertEquals(9.375, requireNotNull(request.recipe.ratioSemantics[0].ratioValue), 0.001)
        assertEquals(
            listOf("BREW_WATER_INPUT", "ICE"),
            request.recipe.ratioSemantics[1].includedDenominatorRoles,
        )
        assertEquals("USER_EXACT", requireNotNull(request.recipe.temperatureSemantics).basis)
        assertEquals("DRAWDOWN_AND_BREW_ICE_MELT", request.recipe.completionSemantics)
        assertEquals("1.0.0", metadata.sourceSchemaVersion)
        assertEquals(
            "aa006a366297d659332986f8971b5442d77bf168eba30e520708742b3f76506d",
            metadata.sourceSha256,
        )
        assertEquals(recipeId.value, metadata.exactRecipeApproachId)
        assertEquals(6, metadata.orderedStageCount)
    }

    @Test
    fun `exact IDs resolve without fallback and require their exact app profile`() {
        val factory = factory()
        val unknown = factory.create(
            BuiltinBrewerSessionStartInput(
                brewerProfileId = BrewerProfileId("clever_style"),
                builtInRecipeId = BuiltInRecipeId("future_exact_recipe"),
                dryCoffeeDoseG = 15.0,
            ),
        )
        val profileMismatch = factory.create(
            BuiltinBrewerSessionStartInput(
                brewerProfileId = BrewerProfileId("hario_switch"),
                builtInRecipeId = BuiltInRecipeId("clever_coffee_first_15_250"),
                dryCoffeeDoseG = 15.0,
            ),
        )

        assertEquals(
            BuiltinBrewerSessionStartUnavailableReason.UNKNOWN_BUILTIN_RECIPE,
            (unknown as BuiltinBrewerSessionStartResult.Unavailable).reason,
        )
        assertEquals(
            listOf(BuiltinBrewerSessionStartValidationCode.BUILTIN_RECIPE_PROFILE_MISMATCH),
            (profileMismatch as BuiltinBrewerSessionStartResult.InvalidSetup)
                .issues
                .map(BuiltinBrewerSessionStartValidationIssue::code),
        )
    }

    @Test
    fun `exact source quantities equipment and temperature reject nearby substitutions`() {
        val recipeId = BuiltInRecipeId("clever_coffee_first_15_250")
        val exactEquipment = EquipmentConfiguration(
            brewerProfileId = BrewerProfileId("clever_style"),
            filterSelection = paperFilter("wedge_paper"),
            capacityOverrideG = 400.0,
        )
        val factory = factory()
        val doseMismatch = factory.create(
            BuiltinBrewerSessionStartInput(
                brewerProfileId = BrewerProfileId("clever_style"),
                builtInRecipeId = recipeId,
                dryCoffeeDoseG = 16.0,
                inputWaterG = 250.0,
                equipment = exactEquipment,
                temperatureC = 95,
            ),
        )
        val inputMismatch = factory.create(
            BuiltinBrewerSessionStartInput(
                brewerProfileId = BrewerProfileId("clever_style"),
                builtInRecipeId = recipeId,
                dryCoffeeDoseG = 15.0,
                inputWaterG = 251.0,
                equipment = exactEquipment,
                temperatureC = 95,
            ),
        )
        val equipmentMismatch = factory.create(
            BuiltinBrewerSessionStartInput(
                brewerProfileId = BrewerProfileId("clever_style"),
                builtInRecipeId = recipeId,
                dryCoffeeDoseG = 15.0,
                inputWaterG = 250.0,
                equipment = exactEquipment.copy(filterSelection = paperFilter("cone_paper")),
                temperatureC = 95,
            ),
        )
        val temperatureMismatch = factory.create(
            BuiltinBrewerSessionStartInput(
                brewerProfileId = BrewerProfileId("clever_style"),
                builtInRecipeId = recipeId,
                dryCoffeeDoseG = 15.0,
                inputWaterG = 250.0,
                equipment = exactEquipment,
                temperatureC = 94,
            ),
        )

        assertEquals(
            listOf(BuiltinBrewerSessionStartValidationCode.BUILTIN_RECIPE_DOSE_MISMATCH),
            (doseMismatch as BuiltinBrewerSessionStartResult.InvalidSetup)
                .issues
                .map(BuiltinBrewerSessionStartValidationIssue::code),
        )
        assertEquals(
            listOf(BuiltinBrewerSessionStartValidationCode.BUILTIN_RECIPE_INPUT_MISMATCH),
            (inputMismatch as BuiltinBrewerSessionStartResult.InvalidSetup)
                .issues
                .map(BuiltinBrewerSessionStartValidationIssue::code),
        )
        assertEquals(
            listOf(BuiltinBrewerSessionStartValidationCode.BUILTIN_RECIPE_EQUIPMENT_MISMATCH),
            (equipmentMismatch as BuiltinBrewerSessionStartResult.InvalidSetup)
                .issues
                .map(BuiltinBrewerSessionStartValidationIssue::code),
        )
        assertEquals(
            listOf(BuiltinBrewerSessionStartValidationCode.BUILTIN_RECIPE_TEMPERATURE_MISMATCH),
            (temperatureMismatch as BuiltinBrewerSessionStartResult.InvalidSetup)
                .issues
                .map(BuiltinBrewerSessionStartValidationIssue::code),
        )
    }

    @Test
    fun `Cup-One requires measured reservoir input while retaining unresolved source ratio`() {
        val recipeId = BuiltInRecipeId("auto_cupone_20_300")
        val factory = exactFactory(
            recipeId = recipeId,
            actions = listOf(
                BrewStageAction.PREPARE,
                BrewStageAction.ADD_COFFEE,
                BrewStageAction.ADD_WATER,
                BrewStageAction.OBSERVE,
                BrewStageAction.FILTER,
                BrewStageAction.SERVE,
            ),
        )
        val input = BuiltinBrewerSessionStartInput(
            brewerProfileId = BrewerProfileId("automatic_single_cup_generic"),
            builtInRecipeId = recipeId,
            dryCoffeeDoseG = 20.0,
            equipment = EquipmentConfiguration(
                brewerProfileId = BrewerProfileId("automatic_single_cup_generic"),
                filterSelection = paperFilter("number_one_paper"),
                basketId = BasketProfileId("automatic_number_one_basket"),
                capacityOverrideG = 350.0,
            ),
            temperatureC = 94,
        )

        val missingInput = factory.create(input)
        val suppliedInput = factory.create(input.copy(inputWaterG = 300.0))

        assertEquals(
            listOf(BuiltinBrewerSessionStartValidationCode.BUILTIN_RECIPE_INPUT_REQUIRED),
            (missingInput as BuiltinBrewerSessionStartResult.InvalidSetup)
                .issues
                .map(BuiltinBrewerSessionStartValidationIssue::code),
        )
        val request = (suppliedInput as BuiltinBrewerSessionStartResult.Ready).request
        assertEquals(300.0, requireNotNull(request.recipe.quantities.reservoirInputG), 0.001)
        assertNull(request.recipe.quantities.brewWaterInputG)
        assertEquals("RESERVOIR_INPUT", request.recipe.ratioDefinition.denominator)
        assertEquals(15.0, requireNotNull(request.recipe.ratioValue), 0.001)
        assertEquals(300.0, request.executionContext.logPresentation.waterG, 0.001)
        assertEquals(1, request.recipe.ratioSemantics.size)
        assertNull(request.recipe.ratioSemantics.single().ratioValue)
        assertEquals(
            "MACHINE_CONTROLLED_REPORTED_RANGE",
            requireNotNull(request.recipe.temperatureSemantics).basis,
        )
        assertTrue(requireNotNull(request.recipe.sourceMetadata).unresolvedFields.contains("beverage_output"))
    }

    @Test
    fun `exact recipes reject generic plans and contradictory canonical workflow order`() {
        val coffeeFirstId = BuiltInRecipeId("clever_coffee_first_15_250")
        val waterFirstId = BuiltInRecipeId("clever_water_first_15_250")
        val equipment = EquipmentConfiguration(
            brewerProfileId = BrewerProfileId("clever_style"),
            filterSelection = paperFilter("wedge_paper"),
            capacityOverrideG = 400.0,
        )
        val genericPlan = factory().create(
            BuiltinBrewerSessionStartInput(
                brewerProfileId = BrewerProfileId("clever_style"),
                builtInRecipeId = coffeeFirstId,
                dryCoffeeDoseG = 15.0,
                equipment = equipment,
                temperatureC = 95,
            ),
        )
        val wrongOrder = exactFactory(
            recipeId = waterFirstId,
            actions = listOf(
                BrewStageAction.PREPARE,
                BrewStageAction.ADD_COFFEE,
                BrewStageAction.ADD_WATER,
                BrewStageAction.STEEP,
                BrewStageAction.RELEASE,
                BrewStageAction.SERVE,
            ),
        ).create(
            BuiltinBrewerSessionStartInput(
                brewerProfileId = BrewerProfileId("clever_style"),
                builtInRecipeId = waterFirstId,
                dryCoffeeDoseG = 15.0,
                equipment = equipment,
                temperatureC = 96,
            ),
        )

        assertEquals(
            BuiltinBrewerSessionStartUnavailableReason.BUILTIN_RECIPE_STAGE_PLAN_MISMATCH,
            (genericPlan as BuiltinBrewerSessionStartResult.Unavailable).reason,
        )
        assertEquals(
            BuiltinBrewerSessionStartUnavailableReason.BUILTIN_RECIPE_STAGE_PLAN_MISMATCH,
            (wrongOrder as BuiltinBrewerSessionStartResult.Unavailable).reason,
        )
    }

    @Test
    fun `exact Switch infers supported workflow without inventing unspecified temperature`() {
        val recipeId = BuiltInRecipeId("switch_official_20_240")
        var observedWorkflow: HarioSwitchWorkflow? = null
        val plan = exactPlan(
            recipeId = recipeId,
            actions = listOf(
                BrewStageAction.PREPARE,
                BrewStageAction.ADD_COFFEE,
                BrewStageAction.ADD_WATER,
                BrewStageAction.STEEP,
                BrewStageAction.RELEASE,
            ),
        )
        val factory = BuiltinBrewerSessionStartFactory(
            stagePlanFor = { _, workflow ->
                observedWorkflow = workflow
                plan
            },
            newUuid = { UUID.fromString("6c4e2984-37b1-44ea-a4f7-9a004a32dd67") },
        )
        val baseInput = BuiltinBrewerSessionStartInput(
            brewerProfileId = BrewerProfileId("hario_switch"),
            builtInRecipeId = recipeId,
            dryCoffeeDoseG = 20.0,
            equipment = EquipmentConfiguration(
                brewerProfileId = BrewerProfileId("hario_switch"),
                filterSelection = paperFilter("cone_paper"),
                capacityOverrideG = 400.0,
            ),
        )

        val inferred = factory.create(baseInput)
        val workflowMismatch = factory.create(
            baseInput.copy(harioSwitchWorkflow = HarioSwitchWorkflow.MANUAL_GRAVITY),
        )
        val inventedTemperature = factory.create(baseInput.copy(temperatureC = 95))

        val request = (inferred as BuiltinBrewerSessionStartResult.Ready).request
        assertEquals(HarioSwitchWorkflow.STEEP_AND_RELEASE, observedWorkflow)
        assertEquals(240.0, requireNotNull(request.recipe.quantities.brewWaterInputG), 0.001)
        assertNull(request.recipe.temperatureC)
        assertEquals("HOT_UNSPECIFIED", requireNotNull(request.recipe.temperatureSemantics).basis)
        assertEquals(
            listOf(BuiltinBrewerSessionStartValidationCode.BUILTIN_RECIPE_WORKFLOW_MISMATCH),
            (workflowMismatch as BuiltinBrewerSessionStartResult.InvalidSetup)
                .issues
                .map(BuiltinBrewerSessionStartValidationIssue::code),
        )
        assertEquals(
            listOf(
                BuiltinBrewerSessionStartValidationCode.BUILTIN_RECIPE_TEMPERATURE_NOT_APPLICABLE,
            ),
            (inventedTemperature as BuiltinBrewerSessionStartResult.InvalidSetup)
                .issues
                .map(BuiltinBrewerSessionStartValidationIssue::code),
        )
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

    private fun exactFactory(
        recipeId: BuiltInRecipeId,
        actions: List<BrewStageAction>,
        sessionId: UUID = UUID.fromString("7c4e2984-37b1-44ea-a4f7-9a004a32dd67"),
    ): BuiltinBrewerSessionStartFactory {
        val plan = exactPlan(recipeId, actions)
        return BuiltinBrewerSessionStartFactory(
            stagePlanFor = { _, _ -> plan },
            newUuid = { sessionId },
        )
    }

    private fun exactPlan(
        recipeId: BuiltInRecipeId,
        actions: List<BrewStageAction>,
    ): BrewStagePlan = BrewStagePlan(
        id = StagePlanId("builtin_recipe_${recipeId.value}"),
        version = 1,
        nodes = actions.mapIndexed { index, action ->
            val stageId = "${recipeId.value}_stage_${index + 1}"
            StagePlanNode.Stage(
                BrewStageDefinition(
                    id = StageId(stageId),
                    action = action,
                    contentId = StageContentId(stageId),
                    completionMode = StageCompletionMode.Manual,
                ),
            )
        },
    )

    private fun factory(sessionId: UUID = UUID.fromString("5c4e2984-37b1-44ea-a4f7-9a004a32dd67")):
        BuiltinBrewerSessionStartFactory = BuiltinBrewerSessionStartFactory(newUuid = { sessionId })

    private fun paperFilter(id: String): FilterSelection.Stack = FilterSelection.Stack(
        entries = listOf(FilterStackEntry(FilterProfileId(id), position = 0)),
    )
}
