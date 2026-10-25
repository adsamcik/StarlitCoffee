package com.adsamcik.starlitcoffee.ui.guidance

import com.adsamcik.starlitcoffee.data.brewing.guides.ReviewedMethodGuideLibrary
import com.adsamcik.starlitcoffee.domain.brewing.StageContentId
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Tests shipped source contracts, rather than trusting the admission inventory as its own oracle. */
class GeneratedGuidanceCatalogTest {
    @Test
    fun `every shipped physical step has current hash bound approved artwork`() {
        val expected = currentContracts()
        val admitted = GeneratedGuidanceCatalog.targets.filter { it.purpose == "instruction" }
        assertEquals("One admission per actual physical context", expected.keys, admitted.map { it.contentId }.toSet())
        assertEquals(admitted.size, admitted.map { it.contentId }.distinct().size)
        admitted.forEach { target ->
            val contract = requireNotNull(expected[target.contentId])
            assertEquals(target.contentId, contract, listOf(target.familyId, target.profileId, target.contentRevision))
            val asset = BuiltInInstructionAssetCatalog.catalog.findApprovedAssetForContent(
                StageContentId(target.contentId), target.contentRevision,
            )
            assertNotNull(target.contentId, asset)
            assertEquals(target.contentId, target.sourceAssetId, asset?.id?.value)
        }
    }

    @Test
    fun `changing a target instruction invalidates all shared scene bindings`() {
        val catalog = BuiltInInstructionAssetCatalog.catalog
        GeneratedGuidanceCatalog.aliases.forEach { alias ->
            assertNotNull(alias.contentId.value, catalog.findApprovedAssetForContent(alias.contentId, alias.targetContentRevision))
            assertNull(alias.contentId.value, catalog.findApprovedAssetForContent(alias.contentId, "0".repeat(64)))
            assertNull(alias.contentId.value, catalog.findApprovedAssetForContent(alias.contentId))
        }
    }

    private fun currentContracts(): Map<String, List<String>> = buildMap {
        val generic = LegacyBuiltInGuidanceCatalog.catalog.content.filter {
            it.placement in setOf(BuiltInGuidancePlacement.PREPARATION, BuiltInGuidancePlacement.LIVE_STAGE,
                BuiltInGuidancePlacement.COMPLETION) && !it.id.value.endsWith("_safety")
        } + P1BuiltInGuidanceCatalog.entries.filter { it.plannedVisualAsset != null }.map { it.content } +
            ColdBrewGuidanceCatalog.catalog.content
        generic.forEach { put(it.id.value, listOf(it.familyId.value, requireNotNull(it.profileId).value,
            requireNotNull(it.visualContentRevision))) }
        val guides = ReviewedMethodGuideLibrary.decode(source("assets/${ReviewedMethodGuideLibrary.ASSET_PATH}"))
        guides.flatMap { it.guidanceCatalog(InstructionAssetCatalog(emptyList())).content }.forEach {
            put(it.id.value, listOf(it.familyId.value, requireNotNull(it.profileId).value,
                requireNotNull(it.visualContentRevision)))
        }
        addCanonicalP1(this)
        addPulsarLearn(this)
    }

    private fun addCanonicalP1(expected: MutableMap<String, List<String>>) {
        val actual = BuiltInP1ExactGuidanceCatalog.decode(source("assets/p1_exact_guidance_2026_07_27.json"))
        val recipes = Json.parseToJsonElement(source("assets/p1_exact_guidance_2026_07_27.json"))
            .jsonObject.getValue("recipes").jsonArray
        recipes.forEach { element ->
            val recipe = element.jsonObject
            recipe.getValue("stages").jsonArray.forEach { stageElement ->
                val stage = stageElement.jsonObject
                val full = stage.getValue("guidance").jsonObject.getValue("full").jsonObject
                val recipeId = recipe.value("recipe_id")
                val contentId = "p1_${recipeId}_${stage.value("stage_id")}_instruction"
                val family = recipe.value("method_family_id")
                val profile = recipe.value("brewer_profile_id")
                val parts = listOf(recipeId, family, profile, stage.value("stage_id")) +
                    listOf("action", "start_time_or_preceding_condition", "target_duration_or_range",
                        "added_water_target", "cumulative_water_target", "beverage_yield_target",
                        "equipment_state", "completion_criterion").map { stage.value(it) } +
                    listOf("imperative_instruction", "concise_explanation", "observable_completion_cue",
                        "warning", "accessible_alt_text").map { full.value(it) }
                val revision = visualContractRevision(parts)
                val content = requireNotNull(actual.findStage(StageContentId(contentId))).toBuiltInGuidanceContent()
                assertEquals(contentId, revision, content.visualContentRevision)
                expected[contentId] = listOf(content.familyId.value, requireNotNull(content.profileId).value, revision)
            }
        }
    }

    private fun addPulsarLearn(expected: MutableMap<String, List<String>>) {
        val catalog = PulsarLearnGuideCatalog.decode(source("res/raw/pulsar_learn_guidance.json"))
        catalog.content.forEach { content ->
            val full = content.authoredPresentations.getValue(GuidancePresentationLevel.FULL)
            val t = content.text
            val family = content.familyId.value
            val profile = requireNotNull(content.profileId).value
            val revision = visualContractRevision(listOf("pulsar_learn", family, profile, content.id.value,
                content.placement.name, t.primaryInstruction, t.conciseInstruction.orEmpty(), t.explanation.orEmpty(),
                t.tip.orEmpty(), t.warning.orEmpty(), t.altText, full.target.orEmpty(), full.completionCue.orEmpty()))
            assertEquals(content.id.value, revision, content.visualContentRevision)
            expected[content.id.value] = listOf(family, profile, revision)
        }
    }

    private fun JsonObject.value(key: String): String = getValue(key).jsonPrimitive.content

    private fun source(path: String): String = listOf(File("src/main/$path"), File("app/src/main/$path"))
        .first(File::isFile).readText(Charsets.UTF_8)
}
