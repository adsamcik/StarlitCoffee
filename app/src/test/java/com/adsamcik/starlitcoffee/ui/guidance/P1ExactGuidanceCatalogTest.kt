package com.adsamcik.starlitcoffee.ui.guidance

import com.adsamcik.starlitcoffee.domain.brewing.BuiltInP1RecipeCatalog
import com.adsamcik.starlitcoffee.domain.brewing.BuiltInRecipeId
import com.adsamcik.starlitcoffee.domain.brewing.StageContentId
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStageDefinition
import com.adsamcik.starlitcoffee.domain.brewing.session.BuiltInP1ExactStagePlanCatalog
import com.adsamcik.starlitcoffee.domain.brewing.session.StageInstanceId
import com.adsamcik.starlitcoffee.domain.brewing.session.StagePlanNode
import com.adsamcik.starlitcoffee.domain.brewing.session.StageRunStatus
import com.adsamcik.starlitcoffee.domain.brewing.session.StageSafetySeverity
import com.adsamcik.starlitcoffee.ui.session.BrewStageCompletionPresentation
import com.adsamcik.starlitcoffee.ui.session.CurrentBrewStagePresentation
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class P1ExactGuidanceCatalogTest {

    private val encodedAsset: String by lazy(::loadAsset)
    private val catalog: P1ExactGuidanceCatalog by lazy {
        BuiltInP1ExactGuidanceCatalog.decode(encodedAsset)
    }

    @Test
    fun `bundled manifest covers every exact recipe and ordered stage identity`() {
        assertEquals(BuiltInP1RecipeCatalog.SOURCE_SHA256, BuiltInP1ExactGuidanceCatalog.SOURCE_SHA256)
        assertEquals(20, catalog.recipes.size)
        assertEquals(114, catalog.stages.size)
        assertEquals(
            BuiltInP1RecipeCatalog.recipes.map { recipe -> recipe.id },
            catalog.recipes.map { recipe -> recipe.recipeId },
        )

        BuiltInP1RecipeCatalog.recipes.zip(catalog.recipes).forEach { (definition, guidance) ->
            val planStages = planStages(definition.id)

            assertEquals(definition.sourceMethodFamilyId, guidance.sourceMethodFamilyId)
            assertEquals(definition.sourceBrewerProfileId, guidance.sourceBrewerProfileId)
            assertEquals(definition.methodFamilyId, guidance.methodFamilyId)
            assertEquals(definition.brewerProfileId, guidance.brewerProfileId)
            assertEquals(definition.orderedStageCount, guidance.stages.size)
            assertEquals(planStages.size, guidance.stages.size)
            assertEquals(guidance, catalog.findRecipe(definition.id))
            assertTrue(guidance.recipeName.isNotBlank())
            assertTrue(guidance.recipeApproach.isNotBlank())

            guidance.stages.zip(planStages).forEachIndexed { index, (stage, planStage) ->
                assertEquals(index + 1, stage.order)
                assertEquals("stage_${(index + 1).toString().padStart(2, '0')}", stage.sourceStageId)
                assertEquals(planStage.id, stage.stageId)
                assertEquals(planStage.contentId, stage.contentId)
                assertEquals(planStage.instructionAssetId, stage.instructionAssetId)
                assertEquals(stage, catalog.findStage(stage.contentId))
                assertTrue(stage.evidenceSourceIds.isNotEmpty())
                assertTrue(stage.evidenceSourceIds.all { source -> source in definition.evidence.sourceIds })
            }
        }
    }

    @Test
    fun `every stage preserves all four canonical guidance densities`() {
        catalog.stages.forEach { stage ->
            val full = stage.presentation(GuidancePresentationLevel.FULL)
            val concise = stage.presentation(GuidancePresentationLevel.CONCISE)
            val focused = stage.presentation(GuidancePresentationLevel.FOCUSED)
            val utilities = stage.presentation(GuidancePresentationLevel.UTILITIES_ONLY)
            val custom = stage.presentation(GuidancePresentationLevel.CUSTOM)

            assertEquals(stage.full.imperativeInstruction, full.instruction)
            assertEquals(stage.full.conciseExplanation, full.explanation)
            assertEquals(stage.full.observableCompletionCue, full.completionCue)
            assertEquals(stage.concise.currentAction, concise.instruction)
            assertEquals(stage.concise.currentTarget, concise.target)
            assertEquals(stage.concise.completionCue, concise.completionCue)
            assertEquals(stage.focused.actionLabel, focused.instruction)
            assertEquals(stage.focused.numericalOrStateTarget, focused.target)
            assertEquals(stage.focused.nextAction, focused.nextAction)
            assertNull(utilities.instruction)
            assertNull(utilities.target)
            assertEquals(
                stage.utilitiesOnly.map(GuidanceOperationalCue::requireFromStableId),
                utilities.utilities,
            )
            assertEquals(stage.full.imperativeInstruction, custom.instruction)
            assertTrue(stage.full.textFreeIllustrationBrief.isNotBlank())
            assertTrue(stage.full.accessibleAltText.isNotBlank())
            assertEquals(stage.full.accessibleAltText, full.accessibleAltText)
        }
    }

    @Test
    fun `the exact source uses only the closed set of typed operational cues`() {
        val declaredCueIds = GuidanceOperationalCue.entries
            .mapTo(linkedSetOf()) { cue -> cue.stableId }
        val sourceCueIds = catalog.stages
            .flatMap(P1ExactStageGuidance::utilitiesOnly)
            .toSet()

        assertEquals(
            setOf(
                "beverage_yield_target",
                "countdown_or_timestamp",
                "cumulative_water_target",
                "current_pour_target",
                "elapsed_timer",
                "heat_state",
                "stage_advance",
                "valve_state",
            ),
            declaredCueIds,
        )
        assertEquals(declaredCueIds, sourceCueIds)
        assertNull(GuidanceOperationalCue.fromStableId("unknown_control"))

        catalog.stages.forEach { stage ->
            val expected = stage.utilitiesOnly.map(GuidanceOperationalCue::requireFromStableId)
            val focused = stage.presentation(GuidancePresentationLevel.FOCUSED)
            val utilities = stage.presentation(GuidancePresentationLevel.UTILITIES_ONLY)

            assertEquals(expected, focused.controlRequirements)
            assertTrue(focused.utilities.isEmpty())
            assertTrue(utilities.controlRequirements.isEmpty())
            assertEquals(expected, utilities.utilities)
        }
    }

    @Test
    fun `Learn resolves only the selected exact recipe without same-profile mixing`() {
        val selectedRecipeId = BuiltInRecipeId("clever_water_first_15_250")
        val siblingRecipeId = BuiltInRecipeId("clever_coffee_first_15_250")
        val selectedRecipe = requireNotNull(catalog.findRecipe(selectedRecipeId))
        val siblingContentIds = requireNotNull(catalog.findRecipe(siblingRecipeId))
            .stages
            .mapTo(linkedSetOf()) { stage -> stage.contentId }
        val resolver = LearnGuidanceCatalogResolver(
            guidanceCatalogs = listOf(requireNotNull(catalog.forRecipe(selectedRecipeId))),
        )

        val resolution = resolver.resolve(
            LearnGuidanceCatalogRequest(
                methodFamilyId = selectedRecipe.methodFamilyId.value,
                brewerProfileId = selectedRecipe.brewerProfileId.value,
                preferences = DurableBrewSessionGuidancePreferences(
                    sessionOverride = GuidancePresentationLevel.FULL,
                ),
                exactStageOrder = selectedRecipe.stages.map { stage -> stage.stageId },
            ),
        )

        assertEquals(LearnGuidanceCatalogAvailability.Available, resolution.availability)
        assertEquals(
            selectedRecipe.stages.map { stage -> stage.contentId },
            resolution.content.map { content -> content.id },
        )
        assertTrue(resolution.content.none { content -> content.id in siblingContentIds })
    }

    @Test
    fun `Learn and live Brew preserve every authored exact density field`() {
        val recipeId = BuiltInRecipeId("clever_coffee_first_15_250")
        val recipe = requireNotNull(catalog.findRecipe(recipeId))
        val stage = recipe.stages[1]
        val currentStage = planStage(stage).toCurrentPresentation()
        val recipeCatalog = requireNotNull(catalog.forRecipe(recipeId))
        val learnResolver = LearnGuidanceCatalogResolver(
            guidanceCatalogs = listOf(recipeCatalog),
        )
        val liveResolver = DurableBrewSessionGuidanceResolver(
            guidanceCatalogs = listOf(recipeCatalog),
        )
        assertTrue(stage.warning?.isNotBlank() == true)

        GuidancePresentationLevel.entries.forEach { level ->
            val expected = stage.presentation(level)
            val preferences = DurableBrewSessionGuidancePreferences(sessionOverride = level)
            val learnResolution = learnResolver.resolve(
                LearnGuidanceCatalogRequest(
                    methodFamilyId = recipe.methodFamilyId.value,
                    brewerProfileId = recipe.brewerProfileId.value,
                    preferences = preferences,
                    exactStageOrder = recipe.stages.map { recipeStage -> recipeStage.stageId },
                ),
            )
            val liveResolution = liveResolver.resolve(
                DurableBrewSessionGuidanceRequest(
                    methodFamilyId = recipe.methodFamilyId.value,
                    brewerProfileId = recipe.brewerProfileId.value,
                    currentStage = currentStage,
                    preferences = preferences,
                ),
            )
            val learnContent = learnResolution.content.single { content ->
                content.id == stage.contentId
            }
            val liveContent = liveResolution.routineContent.single { content ->
                content.id == stage.contentId
            }

            assertEquals(LearnGuidanceCatalogAvailability.Available, learnResolution.availability)
            assertEquals(DurableBrewGuidanceAvailability.Available, liveResolution.availability)
            assertEquals(expected.instruction.orEmpty(), learnContent.instruction)
            assertEquals(expected.target, learnContent.target)
            assertEquals(expected.completionCue, learnContent.completionCue)
            assertEquals(expected.explanation, learnContent.explanation)
            assertEquals(expected.practicalTip, learnContent.tip)
            assertEquals(expected.nextAction, learnContent.nextAction)
            assertEquals(expected.controlRequirements, learnContent.controlRequirements)
            assertEquals(expected.utilities, learnContent.utilities)
            assertEquals(expected.warning, learnContent.warning)
            assertEquals(expected.instruction.orEmpty(), liveContent.instruction)
            assertEquals(expected.target, liveContent.target)
            assertEquals(expected.completionCue, liveContent.completionCue)
            assertEquals(expected.explanation, liveContent.explanation)
            assertEquals(expected.practicalTip, liveContent.tip)
            assertEquals(expected.nextAction, liveContent.nextAction)
            assertEquals(expected.controlRequirements, liveContent.controlRequirements)
            assertEquals(expected.utilities, liveContent.utilities)
            assertEquals(expected.warning, liveContent.warning)
        }
    }

    @Test
    fun `source warnings and critical review classifications remain explicit`() {
        val warningStages = catalog.stages.filter { stage -> stage.warning != null }
        val criticalStages = catalog.stages.filter(
            P1ExactStageGuidance::requiresSafetyCriticalExpertReview,
        )
        val criticalWithAuthoredWarnings = criticalStages.filter { stage -> stage.warning != null }
        val criticalWithoutAuthoredWarnings = criticalStages.filter { stage -> stage.warning == null }

        assertEquals(37, warningStages.size)
        assertEquals(53, catalog.stages.count { it.visualPriority == P1ExactVisualPriority.MANDATORY })
        assertEquals(33, catalog.stages.count { it.visualPriority == P1ExactVisualPriority.OPTIONAL })
        assertEquals(28, criticalStages.size)
        assertEquals(23, criticalWithAuthoredWarnings.size)
        assertEquals(5, criticalWithoutAuthoredWarnings.size)

        warningStages.forEach { stage ->
            GuidancePresentationLevel.entries.forEach { level ->
                assertEquals(stage.warning, stage.presentation(level).warning)
            }
        }
        criticalWithAuthoredWarnings.forEach { stage ->
            val content = stage.toBuiltInGuidanceContent()
            assertTrue(content.safetyCritical)
            assertTrue(content.visibility.alwaysVisible)
            assertEquals(stage.warning, content.text.warning)
        }
        criticalWithoutAuthoredWarnings.forEach { stage ->
            val content = stage.toBuiltInGuidanceContent()
            assertFalse(content.safetyCritical)
            assertNull(content.text.warning)
            assertEquals(
                StageSafetySeverity.CRITICAL,
                planStage(stage).safetyMessages.single().severity,
            )
        }
    }

    @Test
    fun `compact live resolver never removes an authored stage warning`() {
        val stage = stage(BuiltInRecipeId("v60_official_15_250"), sourceStageNumber = 4)
        val planStage = planStage(stage)
        assertEquals(StageSafetySeverity.WARNING, planStage.safetyMessages.single().severity)

        val resolver = DurableBrewSessionGuidanceResolver(
            guidanceCatalogs = listOf(catalog.asBuiltInGuidanceCatalog()),
        )
        GuidancePresentationLevel.entries.forEach { level ->
            val resolution = resolver.resolve(
                DurableBrewSessionGuidanceRequest(
                    methodFamilyId = stage.methodFamilyId.value,
                    brewerProfileId = stage.brewerProfileId.value,
                    currentStage = planStage.toCurrentPresentation(),
                    preferences = DurableBrewSessionGuidancePreferences(sessionOverride = level),
                ),
            )
            val resolvedStage = resolution.routineContent.single { content ->
                content.id == stage.contentId
            }

            assertEquals(DurableBrewGuidanceAvailability.Available, resolution.availability)
            assertEquals(stage.warning, resolvedStage.warning)
        }
    }

    @Test
    fun `mechanism-sensitive source copy remains exact`() {
        val chemex = stage(BuiltInRecipeId("chemex_42_700"), sourceStageNumber = 1)
        assertEquals(
            "Place the folded filter with three layers over the spout.",
            chemex.full.imperativeInstruction,
        )
        assertEquals(
            "Air channel remains open; one paper layer opposite",
            chemex.focused.numericalOrStateTarget,
        )
        assertEquals(
            "A sealed air channel can stall or burp hot coffee",
            chemex.warning,
        )

        val phin = stage(BuiltInRecipeId("phin_screw_18_120"), sourceStageNumber = 2)
        assertEquals("Engage the screw insert lightly.", phin.full.imperativeInstruction)
        assertEquals(
            "Do not overtighten; the phin is gravity-driven and must not be converted into a sealed pressure vessel.",
            phin.warning,
        )
        assertEquals(listOf("elapsed_timer", "stage_advance"), phin.utilitiesOnly)
    }

    @Test
    fun `unknown operational cue in the source fails closed`() {
        val corrupted = encodedAsset.replaceFirst(
            "\"elapsed_timer\"",
            "\"unknown_control\"",
        )

        assertThrows(IllegalArgumentException::class.java) {
            BuiltInP1ExactGuidanceCatalog.decode(corrupted)
        }
    }

    @Test
    fun `source hash mismatch fails closed`() {
        val corrupted = encodedAsset.replace(
            BuiltInP1ExactGuidanceCatalog.SOURCE_SHA256,
            "0".repeat(64),
        )

        assertThrows(IllegalArgumentException::class.java) {
            BuiltInP1ExactGuidanceCatalog.decode(corrupted)
        }
    }

    private fun stage(
        recipeId: BuiltInRecipeId,
        sourceStageNumber: Int,
    ): P1ExactStageGuidance = requireNotNull(catalog.findRecipe(recipeId))
        .stages[sourceStageNumber - 1]

    private fun planStages(recipeId: BuiltInRecipeId): List<BrewStageDefinition> =
        requireNotNull(BuiltInP1ExactStagePlanCatalog.find(recipeId)).nodes.map { node ->
            require(node is StagePlanNode.Stage)
            node.definition
        }

    private fun planStage(stage: P1ExactStageGuidance): BrewStageDefinition =
        planStages(stage.recipeId).single { definition -> definition.id == stage.stageId }

    private fun BrewStageDefinition.toCurrentPresentation(): CurrentBrewStagePresentation =
        CurrentBrewStagePresentation(
            stageInstanceId = StageInstanceId(id, occurrence = 1),
            action = action,
            contentId = contentId,
            instructionAssetId = instructionAssetId,
            requiresIllustration = requiresIllustration,
            runStatus = StageRunStatus.ACTIVE,
            elapsedActiveMillis = 0L,
            completion = BrewStageCompletionPresentation.Manual,
            safetyMessages = safetyMessages,
        )

    private fun loadAsset(): String {
        val file = listOf(
            File("src/main/assets/${BuiltInP1ExactGuidanceCatalog.ASSET_NAME}"),
            File("app/src/main/assets/${BuiltInP1ExactGuidanceCatalog.ASSET_NAME}"),
        ).firstOrNull(File::isFile)
            ?: error("Exact P1 guidance asset not found (cwd=${File(".").absolutePath})")
        return file.readText()
    }
}
