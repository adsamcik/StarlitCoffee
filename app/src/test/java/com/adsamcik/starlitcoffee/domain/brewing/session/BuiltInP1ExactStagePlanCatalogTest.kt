package com.adsamcik.starlitcoffee.domain.brewing.session

import com.adsamcik.starlitcoffee.domain.brewing.BuiltInP1RecipeCatalog
import com.adsamcik.starlitcoffee.domain.brewing.BuiltInRecipeId
import com.adsamcik.starlitcoffee.domain.brewing.QuantityRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltInP1ExactStagePlanCatalogTest {

    @Test
    fun `catalog exactly matches the canonical stage manifest`() {
        val actual = BuiltInP1RecipeCatalog.recipes.associate { recipe ->
            val plan = requireNotNull(BuiltInP1ExactStagePlanCatalog.find(recipe.id))
            recipe.id.value to plan.directStages().map { it.signature() }
        }

        assertEquals(
            expectedStageSignatures.mapValues { (_, signatures) ->
                signatures.map { signature -> signature.withoutSafetySignatureSegment() }
            },
            actual.mapValues { (_, signatures) ->
                signatures.map { signature -> signature.withoutSafetySignatureSegment() }
            },
        )
    }

    @Test
    fun `catalog covers all canonical recipes with stable exact identities`() {
        val recipes = BuiltInP1RecipeCatalog.recipes
        val plans = BuiltInP1ExactStagePlanCatalog.plans

        assertEquals("aa006a366297d659332986f8971b5442d77bf168eba30e520708742b3f76506d", BuiltInP1RecipeCatalog.SOURCE_SHA256)
        assertEquals(20, plans.size)
        assertEquals(recipes.map { it.id }.toSet(), BuiltInP1ExactStagePlanCatalog.recipeIds)
        assertEquals(114, plans.sumOf { it.nodes.size })
        assertNull(BuiltInP1ExactStagePlanCatalog.find(BuiltInRecipeId("unknown_recipe")))

        recipes.forEach { recipe ->
            val plan = requireNotNull(BuiltInP1ExactStagePlanCatalog.find(recipe.id))
            val stages = plan.directStages()
            assertEquals("builtin_recipe_${recipe.id.value}", plan.id.value)
            assertEquals(2, plan.version)
            assertEquals(recipe.orderedStageCount, stages.size)

            stages.forEachIndexed { index, stage ->
                val sourceStageId = "stage_${(index + 1).toString().padStart(2, '0')}"
                val identity = "p1_${recipe.id.value}_$sourceStageId"
                assertEquals(identity, stage.id.value)
                assertEquals("${identity}_instruction", stage.contentId.value)
                assertEquals("instruction_${identity}_instruction_default", stage.instructionAssetId?.value)
                assertEquals(
                    "p1_equipment_${recipe.id.value}_$sourceStageId",
                    stage.equipmentRequirement?.requiredState?.value,
                )
                assertFalse(stage.isSkippable)
                assertTrue(stage.safetyMessages.size <= 1)
                stage.safetyMessages.singleOrNull()?.let { safety ->
                    assertTrue(safety.code.matches(Regex("[a-z0-9_]+")))
                    assertFalse(safety.code.startsWith("p1_warning_"))
                }
                stage.referenceTargets.timeTargets.forEachIndexed { targetIndex, target ->
                    assertEquals(
                        "p1_target_${recipe.id.value}_${sourceStageId}_time_${targetIndex + 1}",
                        target.id.value,
                    )
                }
                stage.referenceTargets.massTargets.forEachIndexed { targetIndex, target ->
                    assertEquals(
                        "p1_target_${recipe.id.value}_${sourceStageId}_mass_${targetIndex + 1}",
                        target.id.value,
                    )
                    assertEquals(QuantityRole.BREW_WATER_INPUT, target.role)
                }
                (stage.completionMode as? StageCompletionMode.ObservedEvent)?.let { completion ->
                    assertEquals(
                        "p1_obs_${recipe.id.value}_$sourceStageId",
                        completion.observationId.value,
                    )
                }
            }
        }
    }

    @Test
    fun `all exact plans validate and compile without changing source order`() {
        BuiltInP1ExactStagePlanCatalog.plans.forEach { plan ->
            val validation = StagePlanValidator.validate(plan)
            assertTrue("${plan.id.value}: ${validation.issues}", validation.isValid)

            val result = StagePlanCompiler.compile(plan)
            assertTrue("${plan.id.value}: $result", result is StagePlanCompileResult.Compiled)
            val compiled = (result as StagePlanCompileResult.Compiled).value
            assertEquals(plan.directStages().map { it.id }, compiled.stages.map { it.definition.id })
        }
    }

    @Test
    fun `trigger and reference totals retain observation driven completion and model gaps`() {
        val stages = BuiltInP1ExactStagePlanCatalog.plans.flatMap { it.directStages() }

        assertEquals(41, stages.count { it.completionMode is StageCompletionMode.Manual })
        assertEquals(48, stages.count { it.completionMode is StageCompletionMode.ObservedEvent })
        assertEquals(23, stages.count { it.completionMode is StageCompletionMode.CumulativeAmount })
        assertEquals(2, stages.count { it.completionMode is StageCompletionMode.Countdown })
        assertEquals(69, stages.sumOf { it.referenceTargets.timeTargets.size })
        assertEquals(74, stages.sumOf { it.referenceTargets.massTargets.size })
        assertEquals(42, stages.count { it.referenceTargets.temperatureTarget != null })
        assertEquals(81, stages.count(BrewStageDefinition::requiresIllustration))
        assertEquals(
            21,
            stages.flatMap(BrewStageDefinition::safetyMessages)
                .count { it.severity == StageSafetySeverity.CRITICAL },
        )
        assertEquals(
            12,
            stages.flatMap(BrewStageDefinition::safetyMessages)
                .count { it.severity == StageSafetySeverity.WARNING },
        )

        assertTrue(stage("switch_official_20_240", 5).referenceTargets.timeTargets.isEmpty())
        assertTrue(stage("cezve_bounded_repeated_rise_12_130", 6).referenceTargets.timeTargets.isEmpty())
        assertTrue(stage("phin_screw_18_120", 5).referenceTargets.timeTargets.isEmpty())

        val approximateMassStage = stage("generic_conical_low_agitation_20_320", 3)
        assertTrue(approximateMassStage.completionMode is StageCompletionMode.Manual)
        assertEquals(
            StageTargetQualifier.APPROXIMATE,
            approximateMassStage.referenceTargets.massTargets.single().qualifier,
        )
    }

    private fun stage(recipeId: String, stageNumber: Int): BrewStageDefinition =
        requireNotNull(BuiltInP1ExactStagePlanCatalog.find(BuiltInRecipeId(recipeId)))
            .directStages()[stageNumber - 1]

    private fun BrewStagePlan.directStages(): List<BrewStageDefinition> = nodes.map { node ->
        assertTrue(node is StagePlanNode.Stage)
        (node as StagePlanNode.Stage).definition
    }

    private fun BrewStageDefinition.signature(): String = listOf(
        action.name,
        completionMode.signature(),
        referenceTargets.timeTargets.joinToString(",") { target ->
            "${target.reference}:${target.qualifier}:${target.minimumMillis}:${target.maximumMillis}"
        }.ifEmpty { "-" },
        referenceTargets.massTargets.joinToString(",") { target ->
            "${target.reference.matrixName()}:${target.qualifier}:${target.minimumGrams}:${target.maximumGrams}"
        }.ifEmpty { "-" },
        referenceTargets.temperatureTarget?.let { target ->
            "${target.qualifier}:${target.minimumC}:${target.maximumC}"
        } ?: "-",
        safetyMessages.singleOrNull()?.severity?.name ?: "NONE",
        requiresIllustration.toString(),
    ).joinToString("|")

    private fun String.withoutSafetySignatureSegment(): String {
        val segments = split('|').toMutableList()
        check(segments.size == 7)
        segments.removeAt(5)
        return segments.joinToString("|")
    }

    @Test
    fun `source timing boundaries become qualified no-early-advance constraints`() {
        assertEquals(
            StageAdvanceConstraint(notBeforeStageElapsedMillis = 30_000L),
            stage("v60_official_15_250", 3).advanceConstraint,
        )
        assertEquals(
            45_000L,
            stage("v60_kasuya_4_6_20_300", 2).advanceConstraint.notBeforeBrewElapsedMillis,
        )
        assertEquals(
            90_000L,
            stage("v60_kasuya_4_6_20_300", 3).advanceConstraint.notBeforeBrewElapsedMillis,
        )
        assertEquals(
            150_000L,
            stage("clever_water_first_15_250", 4).advanceConstraint.notBeforeBrewElapsedMillis,
        )
        assertEquals(
            240_000L,
            stage("auto_cupone_20_300", 4).advanceConstraint.notBeforeStageElapsedMillis,
        )
        assertTrue(stage("v60_official_15_250", 5).advanceConstraint.isConstrained.not())
    }

    private fun StageCompletionMode.signature(): String = when (this) {
        StageCompletionMode.Manual -> "M"
        is StageCompletionMode.ObservedEvent -> "O"
        is StageCompletionMode.Countdown -> "T$durationMillis"
        is StageCompletionMode.CumulativeAmount -> "C$targetGrams"
        StageCompletionMode.Immediate -> "IMMEDIATE"
        is StageCompletionMode.ElapsedRange -> "R$minimumMillis:$maximumMillis"
        is StageCompletionMode.AddedAmount -> "A$targetGrams"
        is StageCompletionMode.BeverageYield -> "Y$targetGrams"
        is StageCompletionMode.ExternalMarker -> "E${markerId.value}"
    }

    private fun StageMassReference.matrixName(): String = when (this) {
        StageMassReference.STAGE_ADDED -> "ADDED_WATER"
        StageMassReference.BREW_CUMULATIVE -> "CUMULATIVE_WATER"
        StageMassReference.RECIPE_TOTAL -> "BEVERAGE_YIELD"
    }

    private val expectedStageSignatures = mapOf(
        "v60_official_15_250" to listOf(
            "RINSE|M|-|-|-|NONE|true",
            "ADD_COFFEE|M|-|-|-|NONE|false",
            "BLOOM|O|STAGE_DURATION:APPROXIMATE:30000:30000|-|RANGE:92.0:96.0|NONE|true",
            "POUR|C250.0|-|CUMULATIVE_WATER:EXACT:250.0:250.0|RANGE:92.0:96.0|WARNING|true",
            "OBSERVE|O|BREW_ELAPSED_AT_COMPLETION:APPROXIMATE:150000:150000|-|-|NONE|false",
        ),
        "v60_rao_20_330" to listOf(
            "PREPARE|M|-|-|-|NONE|true",
            "BLOOM|O|BREW_ELAPSED_AT_COMPLETION:EXACT:40000:40000|ADDED_WATER:EXACT:60.0:60.0,CUMULATIVE_WATER:EXACT:60.0:60.0|EXACT:97.0:97.0|WARNING|true",
            "POUR|C200.0|BREW_ELAPSED_AT_START:EXACT:40000:40000|ADDED_WATER:EXACT:140.0:140.0,CUMULATIVE_WATER:EXACT:200.0:200.0|EXACT:97.0:97.0|NONE|true",
            "AGITATE|M|STAGE_DURATION:NO_LATER_THAN:1000:1000|-|-|NONE|false",
            "POUR|C330.0|-|ADDED_WATER:EXACT:130.0:130.0,CUMULATIVE_WATER:EXACT:330.0:330.0|EXACT:97.0:97.0|NONE|true",
            "AGITATE|O|BREW_ELAPSED_AT_COMPLETION:RANGE:240000:270000|-|-|NONE|false",
        ),
        "v60_kasuya_4_6_20_300" to listOf(
            "RINSE|M|-|-|-|NONE|false",
            "POUR|O|BREW_ELAPSED_AT_START:EXACT:0:0,BREW_ELAPSED_AT_COMPLETION:EXACT:45000:45000|ADDED_WATER:EXACT:50.0:50.0,CUMULATIVE_WATER:EXACT:50.0:50.0|EXACT:92.0:92.0|NONE|true",
            "POUR|O|BREW_ELAPSED_AT_START:EXACT:45000:45000,BREW_ELAPSED_AT_COMPLETION:EXACT:90000:90000|ADDED_WATER:EXACT:70.0:70.0,CUMULATIVE_WATER:EXACT:120.0:120.0|EXACT:92.0:92.0|NONE|true",
            "POUR|C180.0|BREW_ELAPSED_AT_START:EXACT:90000:90000|ADDED_WATER:EXACT:60.0:60.0,CUMULATIVE_WATER:EXACT:180.0:180.0|EXACT:92.0:92.0|NONE|false",
            "POUR|C240.0|BREW_ELAPSED_AT_START:EXACT:130000:130000|ADDED_WATER:EXACT:60.0:60.0,CUMULATIVE_WATER:EXACT:240.0:240.0|EXACT:92.0:92.0|NONE|false",
            "POUR|C300.0|BREW_ELAPSED_AT_START:EXACT:160000:160000|ADDED_WATER:EXACT:60.0:60.0,CUMULATIVE_WATER:EXACT:300.0:300.0|EXACT:92.0:92.0|NONE|false",
            "OBSERVE|O|BREW_ELAPSED_AT_START:APPROXIMATE:210000:210000,BREW_ELAPSED_AT_COMPLETION:APPROXIMATE:210000:210000|-|-|NONE|false",
        ),
        "v60_kurasu_flash_16_150_70" to listOf(
            "PREPARE|M|-|-|-|NONE|true",
            "POUR|O|BREW_ELAPSED_AT_START:EXACT:0:0,STAGE_DURATION:APPROXIMATE:10000:10000,BREW_ELAPSED_AT_COMPLETION:EXACT:40000:40000|ADDED_WATER:EXACT:40.0:40.0,CUMULATIVE_WATER:EXACT:40.0:40.0|EXACT:91.0:91.0|NONE|true",
            "POUR|C100.0|BREW_ELAPSED_AT_START:EXACT:40000:40000|ADDED_WATER:EXACT:60.0:60.0,CUMULATIVE_WATER:EXACT:100.0:100.0|EXACT:91.0:91.0|NONE|false",
            "POUR|C150.0|BREW_ELAPSED_AT_START:EXACT:70000:70000|ADDED_WATER:EXACT:50.0:50.0,CUMULATIVE_WATER:EXACT:150.0:150.0|EXACT:91.0:91.0|NONE|false",
            "AGITATE|O|BREW_ELAPSED_AT_COMPLETION:APPROXIMATE:130000:130000|-|-|NONE|true",
            "SERVE|M|-|-|-|NONE|false",
        ),
        "wave185_ozone_25_400" to listOf(
            "RINSE|M|-|-|-|NONE|true",
            "BLOOM|O|BREW_ELAPSED_AT_START:EXACT:0:0,STAGE_DURATION:EXACT:30000:30000|ADDED_WATER:EXACT:50.0:50.0,CUMULATIVE_WATER:EXACT:50.0:50.0|EXACT:93.0:93.0|NONE|true",
            "POUR|C160.0|BREW_ELAPSED_AT_START:EXACT:30000:30000|ADDED_WATER:EXACT:110.0:110.0,CUMULATIVE_WATER:EXACT:160.0:160.0|EXACT:93.0:93.0|NONE|false",
            "POUR|C220.0|BREW_ELAPSED_AT_START:EXACT:45000:45000|ADDED_WATER:EXACT:60.0:60.0,CUMULATIVE_WATER:EXACT:220.0:220.0|EXACT:93.0:93.0|NONE|false",
            "POUR|C280.0|BREW_ELAPSED_AT_START:EXACT:60000:60000|ADDED_WATER:EXACT:60.0:60.0,CUMULATIVE_WATER:EXACT:280.0:280.0|EXACT:93.0:93.0|NONE|false",
            "POUR|C340.0|BREW_ELAPSED_AT_START:EXACT:75000:75000|ADDED_WATER:EXACT:60.0:60.0,CUMULATIVE_WATER:EXACT:340.0:340.0|EXACT:93.0:93.0|NONE|false",
            "POUR|C400.0|BREW_ELAPSED_AT_START:EXACT:105000:105000|ADDED_WATER:EXACT:60.0:60.0,CUMULATIVE_WATER:EXACT:400.0:400.0|EXACT:93.0:93.0|NONE|false",
            "OBSERVE|O|BREW_ELAPSED_AT_COMPLETION:APPROXIMATE:180000:180000|-|-|NONE|false",
        ),
        "wedge_pulse_23_5_400" to listOf(
            "RINSE|M|-|-|-|NONE|true",
            "BLOOM|O|BREW_ELAPSED_AT_START:EXACT:0:0,STAGE_DURATION:EXACT:40000:40000|ADDED_WATER:EXACT:50.0:50.0,CUMULATIVE_WATER:EXACT:50.0:50.0|APPROXIMATE:91.0:96.0|NONE|true",
            "POUR|O|-|ADDED_WATER:EXACT:50.0:50.0,CUMULATIVE_WATER:EXACT:100.0:100.0|APPROXIMATE:91.0:96.0|NONE|false",
            "POUR|O|-|ADDED_WATER:EXACT:100.0:100.0,CUMULATIVE_WATER:EXACT:200.0:200.0|APPROXIMATE:91.0:96.0|NONE|false",
            "POUR|O|-|ADDED_WATER:EXACT:100.0:100.0,CUMULATIVE_WATER:EXACT:300.0:300.0|APPROXIMATE:91.0:96.0|NONE|false",
            "POUR|O|BREW_ELAPSED_AT_COMPLETION:APPROXIMATE:210000:240000|ADDED_WATER:EXACT:100.0:100.0,CUMULATIVE_WATER:EXACT:400.0:400.0|APPROXIMATE:91.0:96.0|WARNING|true",
        ),
        "chemex_42_700" to listOf(
            "PREPARE|M|-|-|-|CRITICAL|true",
            "RINSE|M|-|-|-|NONE|false",
            "BLOOM|O|BREW_ELAPSED_AT_START:EXACT:0:0,STAGE_DURATION:EXACT:45000:45000|ADDED_WATER:RANGE:100.0:125.0,CUMULATIVE_WATER:RANGE:100.0:125.0|STARTING_POINT:94.0:96.0|NONE|true",
            "POUR|C400.0|-|CUMULATIVE_WATER:EXACT:400.0:400.0|STARTING_POINT:94.0:96.0|NONE|true",
            "POUR|C700.0|-|CUMULATIVE_WATER:EXACT:700.0:700.0|STARTING_POINT:94.0:96.0|NONE|false",
            "OBSERVE|O|BREW_ELAPSED_AT_COMPLETION:STARTING_POINT:300000:390000|-|-|NONE|false",
            "AGITATE|M|-|-|-|NONE|false",
        ),
        "generic_conical_low_agitation_20_320" to listOf(
            "RINSE|M|-|-|-|NONE|true",
            "BLOOM|O|BREW_ELAPSED_AT_START:EXACT:0:0,STAGE_DURATION:RANGE:30000:45000|ADDED_WATER:RANGE:50.0:60.0,CUMULATIVE_WATER:RANGE:50.0:60.0|STARTING_POINT:93.0:96.0|NONE|true",
            "POUR|M|-|CUMULATIVE_WATER:APPROXIMATE:200.0:200.0|STARTING_POINT:93.0:96.0|NONE|true",
            "POUR|C320.0|-|CUMULATIVE_WATER:EXACT:320.0:320.0|STARTING_POINT:93.0:96.0|NONE|false",
            "OBSERVE|O|BREW_ELAPSED_AT_COMPLETION:STARTING_POINT:150000:240000|-|-|NONE|false",
        ),
        "clever_water_first_15_250" to listOf(
            "RINSE|M|-|-|-|NONE|true",
            "ADD_WATER|C250.0|BREW_ELAPSED_AT_START:EXACT:0:0|ADDED_WATER:EXACT:250.0:250.0,CUMULATIVE_WATER:EXACT:250.0:250.0|APPROXIMATE:95.0:100.0|NONE|true",
            "ADD_COFFEE|O|-|-|-|NONE|true",
            "STEEP|O|BREW_ELAPSED_AT_COMPLETION:APPROXIMATE:120000:120000,BREW_ELAPSED_AT_COMPLETION:EXACT:150000:150000|-|-|NONE|false",
            "RELEASE|O|BREW_ELAPSED_AT_START:EXACT:150000:150000,STAGE_DURATION:APPROXIMATE:60000:60000,BREW_ELAPSED_AT_COMPLETION:APPROXIMATE:210000:210000|-|-|CRITICAL|true",
            "SERVE|M|-|-|-|NONE|true",
        ),
        "clever_coffee_first_15_250" to listOf(
            "PREPARE|M|-|-|-|NONE|true",
            "ADD_WATER|O|BREW_ELAPSED_AT_START:EXACT:0:0,STAGE_DURATION:APPROXIMATE:15000:15000|ADDED_WATER:EXACT:250.0:250.0,CUMULATIVE_WATER:EXACT:250.0:250.0|EXACT:95.0:95.0|WARNING|true",
            "STEEP|M|BREW_ELAPSED_AT_COMPLETION:APPROXIMATE:135000:135000|-|-|NONE|false",
            "RELEASE|O|BREW_ELAPSED_AT_COMPLETION:APPROXIMATE:180000:210000|-|-|CRITICAL|true",
        ),
        "switch_official_20_240" to listOf(
            "RINSE|M|-|-|-|CRITICAL|true",
            "ADD_WATER|C240.0|BREW_ELAPSED_AT_START:EXACT:0:0|ADDED_WATER:EXACT:240.0:240.0,CUMULATIVE_WATER:EXACT:240.0:240.0|-|NONE|true",
            "STEEP|T120000|STAGE_DURATION:EXACT:120000:120000|-|-|NONE|false",
            "RELEASE|O|BREW_ELAPSED_AT_START:EXACT:120000:120000|-|-|CRITICAL|true",
            "OBSERVE|O|-|-|-|NONE|true",
        ),
        "switch_ole_boen_hybrid_16_5_240" to listOf(
            "BLOOM|T40000|BREW_ELAPSED_AT_START:EXACT:0:0,STAGE_DURATION:EXACT:40000:40000|ADDED_WATER:EXACT:50.0:50.0,CUMULATIVE_WATER:EXACT:50.0:50.0|EXACT:96.0:96.0|CRITICAL|true",
            "POUR|C150.0|BREW_ELAPSED_AT_START:EXACT:40000:40000|ADDED_WATER:EXACT:100.0:100.0,CUMULATIVE_WATER:EXACT:150.0:150.0|EXACT:96.0:96.0|NONE|true",
            "POUR|C240.0|BREW_ELAPSED_AT_START:APPROXIMATE:90000:90000|ADDED_WATER:EXACT:90.0:90.0,CUMULATIVE_WATER:EXACT:240.0:240.0|EXACT:96.0:96.0|CRITICAL|true",
            "RELEASE|O|BREW_ELAPSED_AT_START:APPROXIMATE:130000:130000|-|-|CRITICAL|true",
            "OBSERVE|O|BREW_ELAPSED_AT_COMPLETION:RANGE:180000:195000|-|-|NONE|false",
        ),
        "switch_gravity_15_250" to listOf(
            "RINSE|M|-|-|-|CRITICAL|true",
            "BLOOM|O|BREW_ELAPSED_AT_START:EXACT:0:0,STAGE_DURATION:APPROXIMATE:30000:30000|-|RANGE:92.0:96.0|NONE|true",
            "POUR|C250.0|-|CUMULATIVE_WATER:EXACT:250.0:250.0|RANGE:92.0:96.0|NONE|true",
            "OBSERVE|O|BREW_ELAPSED_AT_COMPLETION:STARTING_POINT:150000:150000|-|-|NONE|false",
        ),
        "cezve_turkish_single_rise_6_65" to listOf(
            "PREPARE|M|-|-|-|CRITICAL|true",
            "AGITATE|M|-|-|-|NONE|true",
            "HEAT|O|-|-|-|CRITICAL|true",
            "HEAT|O|-|-|-|CRITICAL|true",
            "SERVE|M|-|-|-|WARNING|true",
            "OBSERVE|O|STAGE_DURATION:APPROXIMATE:60000:120000|-|-|WARNING|false",
        ),
        "cezve_bounded_repeated_rise_12_130" to listOf(
            "PREPARE|M|-|-|-|CRITICAL|true",
            "AGITATE|M|-|-|-|NONE|true",
            "HEAT|O|-|-|-|CRITICAL|true",
            "HEAT|O|-|-|-|CRITICAL|true",
            "HEAT|O|-|-|-|CRITICAL|true",
            "SERVE|O|-|-|-|WARNING|true",
        ),
        "auto_batch_500_30" to listOf(
            "ADD_COFFEE|M|-|-|-|CRITICAL|true",
            "ADD_WATER|M|-|-|-|WARNING|true",
            "CUSTOM|O|-|-|-|CRITICAL|true",
            "OBSERVE|O|-|-|-|NONE|true",
            "AGITATE|M|-|-|-|WARNING|true",
        ),
        "auto_batch_1000_60" to listOf(
            "ADD_COFFEE|M|-|-|-|CRITICAL|true",
            "ADD_WATER|M|-|-|-|NONE|true",
            "CUSTOM|O|-|-|-|NONE|true",
            "AGITATE|M|-|-|-|WARNING|true",
        ),
        "auto_cupone_20_300" to listOf(
            "PREPARE|M|-|-|-|CRITICAL|true",
            "ADD_COFFEE|M|-|-|-|NONE|true",
            "ADD_WATER|M|-|-|-|CRITICAL|true",
            "CUSTOM|O|STAGE_DURATION:APPROXIMATE:240000:240000|-|APPROXIMATE:92.0:96.0|CRITICAL|true",
            "OBSERVE|O|-|-|-|WARNING|true",
            "CLEAN_UP|M|-|-|-|CRITICAL|true",
        ),
        "phin_gravity_14_118" to listOf(
            "ADD_COFFEE|M|-|-|-|CRITICAL|true",
            "PREPARE|M|-|-|-|WARNING|true",
            "BLOOM|O|BREW_ELAPSED_AT_START:EXACT:0:0,STAGE_DURATION:EXACT:45000:45000|ADDED_WATER:EXACT:30.0:30.0,CUMULATIVE_WATER:EXACT:30.0:30.0|RANGE:91.0:96.0|NONE|true",
            "ADD_WATER|C118.0|BREW_ELAPSED_AT_START:EXACT:45000:45000|ADDED_WATER:EXACT:88.0:88.0,CUMULATIVE_WATER:EXACT:118.0:118.0|RANGE:91.0:96.0|NONE|true",
            "OBSERVE|O|BREW_ELAPSED_AT_COMPLETION:NO_LATER_THAN:120000:120000|-|-|WARNING|true",
            "OBSERVE|O|BREW_ELAPSED_AT_COMPLETION:APPROXIMATE:300000:300000|-|-|NONE|true",
            "SERVE|M|-|-|-|CRITICAL|true",
        ),
        "phin_screw_18_120" to listOf(
            "ADD_COFFEE|M|-|-|-|CRITICAL|true",
            "PREPARE|M|-|-|-|CRITICAL|true",
            "BLOOM|O|BREW_ELAPSED_AT_START:EXACT:0:0,STAGE_DURATION:RANGE:30000:45000|ADDED_WATER:EXACT:25.0:25.0,CUMULATIVE_WATER:EXACT:25.0:25.0|STARTING_POINT:94.0:98.0|NONE|true",
            "ADD_WATER|C120.0|-|ADDED_WATER:EXACT:95.0:95.0,CUMULATIVE_WATER:EXACT:120.0:120.0|STARTING_POINT:94.0:98.0|NONE|true",
            "OBSERVE|O|-|-|-|CRITICAL|true",
            "SERVE|M|-|-|-|WARNING|true",
        ),
    )
}
