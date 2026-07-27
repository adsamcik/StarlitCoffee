package com.adsamcik.starlitcoffee.ui.guidance

import com.adsamcik.starlitcoffee.domain.brewing.StageContentId
import com.adsamcik.starlitcoffee.domain.brewing.StageId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PulsarBuiltInGuidanceCatalogTest {

    private val catalog = PulsarBuiltInGuidanceCatalog.catalog

    @Test
    fun `manifest is scoped to the built-in Pulsar family and standard profile`() {
        assertTrue(catalog.content.isNotEmpty())
        assertTrue(catalog.content.all { item ->
            item.familyId == PulsarBuiltInGuidanceCatalog.familyId &&
                item.profileId == PulsarBuiltInGuidanceCatalog.profileId
        })
    }

    @Test
    fun `current legacy Pulsar stage IDs resolve from the shared catalog`() {
        val bloom = catalog.find(StageContentId("pulsar_bloom"))
        val manualBrew = catalog.find(StageContentId("pulsar_manual_brew"))

        assertNotNull(bloom)
        assertNotNull(manualBrew)
        assertEquals(StageId("pulsar_bloom"), bloom?.stageId)
        assertEquals(StageId("pulsar_manual_brew"), manualBrew?.stageId)
    }

    @Test
    fun `live stage draws current action utility and global safety from one source`() {
        val policy = GuidancePolicyResolver.resolve(
            GuidancePolicyContext(
                methodFamilyId = PulsarBuiltInGuidanceCatalog.familyId,
                brewerProfileId = PulsarBuiltInGuidanceCatalog.profileId,
                sessionOverride = GuidancePresentationLevel.UTILITIES_ONLY,
            ),
        )

        val contentIds = catalog.forLiveStage(
            stageId = StageId("pulsar_manual_brew"),
            policy = policy,
        ).map { item -> item.id }

        assertTrue(StageContentId("pulsar_manual_brew") in contentIds)
        assertTrue(StageContentId("pulsar_live_targets") in contentIds)
        assertTrue(StageContentId("pulsar_hot_liquid_safety") in contentIds)
        assertTrue(StageContentId("pulsar_overflow_safety") in contentIds)
    }

    @Test
    fun `every record has meaningful instructional and accessibility metadata`() {
        catalog.content.forEach { item ->
            assertTrue(item.text.primaryInstruction.isNotBlank())
            assertTrue(item.text.altText.isNotBlank())
        }
    }

    @Test
    fun `safety content is visible at every guidance level`() {
        val safetyItems = catalog.content.filter { item -> item.safetyCritical }

        assertTrue(safetyItems.isNotEmpty())
        GuidancePresentationLevel.entries.forEach { level ->
            val policy = GuidancePolicyResolver.resolve(
                GuidancePolicyContext(
                    methodFamilyId = PulsarBuiltInGuidanceCatalog.familyId,
                    brewerProfileId = PulsarBuiltInGuidanceCatalog.profileId,
                    sessionOverride = level,
                ),
            )

            safetyItems.forEach { item ->
                assertTrue(policy.isVisible(item.visibility, item.safetyCritical))
                assertTrue(item.visibility.alwaysVisible)
                assertTrue(item.text.warning?.isNotBlank() == true)
            }
        }
    }

    @Test
    fun `catalogue makes no unsupported metal-filter technical claims`() {
        val allCopy = catalog.content.joinToString(" ") { item ->
            listOfNotNull(
                item.text.primaryInstruction,
                item.text.conciseInstruction,
                item.text.explanation,
                item.text.tip,
                item.text.warning,
                item.text.altText,
            ).joinToString(" ")
        }.lowercase()

        assertFalse(allCopy.contains("19k"))
        assertFalse(allCopy.contains("40k"))
    }
}
