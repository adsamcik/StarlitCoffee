package com.adsamcik.starlitcoffee.ui.guidance

import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.domain.brewing.BrewerProfileId
import com.adsamcik.starlitcoffee.domain.brewing.InstructionAssetId
import com.adsamcik.starlitcoffee.domain.brewing.MethodFamilyId
import com.adsamcik.starlitcoffee.domain.brewing.StageContentId
import com.adsamcik.starlitcoffee.domain.brewing.StageId
import java.time.LocalDate
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class BuiltInInstructionAssetCatalogTest {

    @Test
    fun `canonical image alt is current asset bound and Czech uses current instruction caption`() {
        fun guide(directory: String, locale: String) = PulsarLearnGuideCatalog.decode(listOf(
            File("src/main/res/$directory/pulsar_learn_guidance.json"),
            File("app/src/main/res/$directory/pulsar_learn_guidance.json"),
        ).first(File::isFile).readText(Charsets.UTF_8), expectedLocaleTag = locale).content.first()
        val english = guide("raw", "en")
        val czech = guide("raw-cs", "cs")
        val source = asset(review = approvedReview()).copy(
            id = InstructionAssetId("instruction_${english.id.value}_default"), contentId = english.id,
            familyId = english.familyId, profileId = english.profileId, stageId = english.stageId,
            physicalFamilyId = english.familyId, physicalProfileId = english.profileId,
            namingConvention = InstructionAssetNamingConvention.EXACT_CONTENT_ID,
            altTextRes = null, companionInstructionRes = null,
        )
        val newAlt = "Water drains through the open upright Pulsar valve while a hand supports its base."
        val target = AdmittedGuidanceTarget(english.id.value, english.familyId.value,
            requireNotNull(english.profileId).value, "pulsar_learn", requireNotNull(english.visualContentRevision),
            source.id.value, newAlt)
        val catalog = InstructionAssetCatalog(listOf(source), admittedTargets = listOf(target))
        assertEquals(newAlt, catalog.approvedAltTextForContent(english.id, english.visualContentRevision, source.id))
        assertNull(catalog.approvedAltTextForContent(english.id, "0".repeat(64), source.id))
        assertNull(catalog.approvedAltTextForContent(english.id, english.visualContentRevision,
            InstructionAssetId("instruction_other_default")))
        assertEquals(czech.text.primaryInstruction, catalog.approvedAltTextForContent(czech.id,
            czech.visualContentRevision, source.id, currentInstruction = czech.text.primaryInstruction))
        assertNull(catalog.approvedAltTextForContent(czech.id, czech.visualContentRevision, source.id))
    }

    @Test
    fun `production source excludes retired legacy inputs`() {
        val retiredIds = setOf(
            "instruction_steep_and_release_clever_style_" +
                "clever_style_insert_and_rinse_filter_default",
            "instruction_steep_and_release_hario_switch_" +
                "hario_switch_add_coffee_default",
            "instruction_restricted_flow_gravity_concentrate_vietnamese_phin_" +
                "vietnamese_phin_place_on_stable_cup_default",
        )

        assertEquals(
            emptySet<String>(),
            BuiltInInstructionAssetCatalog.catalog.assets
                .mapTo(linkedSetOf()) { asset -> asset.id.value }
                .intersect(retiredIds),
        )
    }
    @Test
    fun `content lookup returns a reviewed default and never a pending record`() {
        val pending = asset(
            variant = InstructionAssetVariant("pending_review"),
            review = InstructionAssetReview(InstructionAssetReviewStatus.PENDING_REVIEW),
        )
        val approved = asset(review = approvedReview())
        val catalog = InstructionAssetCatalog(listOf(pending, approved))

        assertEquals(approved, catalog.findApprovedAssetForContent(CONTENT))
    }

    @Test
    fun `content lookup refuses an unreviewed sole record`() {
        val catalog = InstructionAssetCatalog(
            listOf(asset(review = InstructionAssetReview(InstructionAssetReviewStatus.DRAFT))),
        )

        assertNull(catalog.findApprovedAssetForContent(CONTENT))
    }

    @Test
    fun `content lookup refuses an ambiguous approved default`() {
        val first = asset(profileId = BrewerProfileId("v60_01"), review = approvedReview())
        val second = asset(profileId = BrewerProfileId("v60_02"), review = approvedReview())
        val catalog = InstructionAssetCatalog(listOf(first, second))

        assertNull(catalog.findApprovedAssetForContent(CONTENT))
    }

    @Test
    fun `explicit reviewed aliases reuse one source without changing its canonical identity`() {
        val source = asset(review = approvedReview())
        val first = alias(source, StageContentId("reviewed_first_pour"))
        val second = alias(source, StageContentId("reviewed_second_pour"))
        val catalog = InstructionAssetCatalog(listOf(source), listOf(first, second))

        assertEquals(source, catalog.findApprovedAssetForContent(first.contentId, first.targetContentRevision))
        assertEquals(source, catalog.findApprovedAssetForContent(second.contentId, second.targetContentRevision))
        assertEquals(source, catalog.findApprovedAssetForContent(CONTENT))
        assertNull(catalog.findApprovedAssetForContent(StageContentId("unbound_pour")))
    }

    @Test
    fun `aliases reject unknown unapproved and changed source artwork`() {
        val source = asset(review = approvedReview())
        val binding = alias(source)
        assertThrows(IllegalArgumentException::class.java) { InstructionAssetCatalog(emptyList(), listOf(binding)) }
        assertThrows(IllegalArgumentException::class.java) {
            InstructionAssetCatalog(listOf(source.copy(review = InstructionAssetReview(InstructionAssetReviewStatus.DRAFT))),
                listOf(binding))
        }
        assertThrows(IllegalArgumentException::class.java) {
            InstructionAssetCatalog(listOf(source.copy(resourceSha256 = "b".repeat(64))), listOf(binding))
        }
    }

    @Test
    fun `alias lookup refuses stale missing revisions or unreviewed alt text`() {
        val source = asset(review = approvedReview())
        val binding = alias(source)
        val target = AdmittedGuidanceTarget(binding.contentId.value, binding.familyId.value,
            binding.profileId.value, "test", binding.targetContentRevision, source.id.value,
            requireNotNull(binding.altText))
        val catalog = InstructionAssetCatalog(listOf(source), listOf(binding), listOf(target))
        assertNull(catalog.findApprovedAssetForContent(binding.contentId))
        assertNull(catalog.findApprovedAssetForContent(binding.contentId, "changed contract"))
        assertEquals(source, catalog.findApprovedAssetForContent(binding.contentId, binding.targetContentRevision))
        assertNull(catalog.approvedAltTextForContent(binding.contentId, "changed contract", source.id,
            currentInstruction = "A new localized instruction cannot restore a stale shared scene."))
        assertNull(InstructionAssetCatalog(listOf(source), listOf(binding.copy(altText = null)))
            .findApprovedAssetForContent(binding.contentId, binding.targetContentRevision))
    }

    @Test
    fun `aliases cannot silently cross scope replace canonical content or select two sources`() {
        val source = asset(review = approvedReview())
        val binding = alias(source)
        listOf(binding.copy(sourcePhysicalFamilyId = MethodFamilyId("immersion")),
            binding.copy(sourcePhysicalProfileId = BrewerProfileId("v60_01")),
            binding.copy(contentId = CONTENT)).forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) { InstructionAssetCatalog(listOf(source), listOf(invalid)) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            InstructionAssetCatalog(listOf(source), listOf(binding, binding))
        }
        val familySource = asset(profileId = null, review = approvedReview())
        assertThrows(IllegalArgumentException::class.java) {
            InstructionAssetCatalog(listOf(familySource), listOf(alias(familySource)))
        }
        val explicitlyCompatible = alias(familySource).copy(allowFamilySource = true, sourcePhysicalProfileId = null)
        assertEquals(familySource, InstructionAssetCatalog(listOf(familySource), listOf(explicitlyCompatible))
            .findApprovedAssetForContent(explicitlyCompatible.contentId, explicitlyCompatible.targetContentRevision))
    }

    @Test
    fun `alias binding must itself have a reviewed content contract and recorded evidence`() {
        val binding = alias(asset(review = approvedReview()))
        assertThrows(IllegalArgumentException::class.java) { binding.copy(targetContentRevision = "") }
        assertThrows(IllegalArgumentException::class.java) { binding.copy(review = approvedReview()) }
        assertThrows(IllegalArgumentException::class.java) {
            binding.copy(review = InstructionAssetReview(InstructionAssetReviewStatus.PENDING_REVIEW))
        }
    }

    @Test
    fun `exact recipe wrapper aliases retain reviewed physical scope`() {
        val source = asset(review = approvedReview())
        val wrapped = source.copy(id = InstructionAssetId("instruction_${CONTENT.value}_default"),
            familyId = MethodFamilyId("exact_recipe_family"), profileId = BrewerProfileId("exact_recipe_profile"),
            namingConvention = InstructionAssetNamingConvention.EXACT_CONTENT_ID,
            altTextRes = null, companionInstructionRes = null)
        val binding = alias(wrapped)
        assertEquals(wrapped, InstructionAssetCatalog(listOf(wrapped), listOf(binding))
            .findApprovedAssetForContent(binding.contentId, binding.targetContentRevision))
        assertThrows(IllegalArgumentException::class.java) {
            InstructionAssetCatalog(listOf(wrapped.copy(physicalProfileId = BrewerProfileId("v60_01"))), listOf(binding))
        }
    }

    @Test
    fun `reviewed taxonomy equivalence binds only the declared source hardware`() {
        val legacyProfile = BrewerProfileId("hario_v60_02_with_matching_paper")
        val source = asset(profileId = legacyProfile, review = approvedReview()).copy(
            physicalFamilyId = MethodFamilyId("manual_pour_over"),
        )
        val target = alias(source)
        assertThrows(IllegalArgumentException::class.java) {
            InstructionAssetCatalog(listOf(source), listOf(target))
        }
        val reviewedBinding = target.copy(
            sourcePhysicalFamilyId = source.physicalFamilyId,
            sourcePhysicalProfileId = legacyProfile,
            review = approvedReview().copy(notes =
                "manual_pour_over/hario_v60_02_with_matching_paper and manual_gravity/v60_02 identify the same " +
                    "conventional plastic Hario V60 02 and matching cone paper; reviewed reuse shows active " +
                    "pouring onto a wet bed with headroom."),
        )
        val catalog = InstructionAssetCatalog(listOf(source), listOf(reviewedBinding))
        assertEquals(source, catalog.findApprovedAssetForContent(reviewedBinding.contentId, reviewedBinding.targetContentRevision))
        assertNull(catalog.findApprovedAssetForContent(StageContentId("another_v60_pour")))
        assertThrows(IllegalArgumentException::class.java) {
            InstructionAssetCatalog(listOf(source.copy(physicalProfileId = BrewerProfileId("v60_01"))),
                listOf(reviewedBinding))
        }
        assertThrows(IllegalArgumentException::class.java) {
            InstructionAssetCatalog(listOf(source.copy(physicalFamilyId = MethodFamilyId("immersion"))),
                listOf(reviewedBinding))
        }
    }

    private fun alias(source: InstructionAssetRecord, target: StageContentId = StageContentId("reviewed_pour")) =
        InstructionAssetAlias(target, source.id, FAMILY, PROFILE, requireNotNull(source.resourceSha256),
            targetContentRevision = "sha256:${"c".repeat(64)}",
            review = approvedReview().copy(notes = "Reviewed identical V60 02 hardware, pouring action and wet-bed state."),
            altText = "Water pours onto coffee in a V60 02 with headroom.")

    private fun asset(
        profileId: BrewerProfileId? = PROFILE,
        variant: InstructionAssetVariant = InstructionAssetVariant.DEFAULT,
        review: InstructionAssetReview,
    ): InstructionAssetRecord = InstructionAssetRecord(
        id = InstructionAssetId(
            "instruction_${FAMILY.value}_${profileId?.value ?: "family"}_${CONTENT.value}_${variant.value}",
        ),
        familyId = FAMILY,
        profileId = profileId,
        stageId = STAGE,
        contentId = CONTENT,
        variant = variant,
        drawableRes = R.drawable.vessel_icon_mug,
        altTextRes = R.string.app_name,
        companionInstructionRes = R.string.instruction_pour_total,
        mandatoryForFullGuidance = true,
        safetySensitive = false,
        provenance = InstructionAssetProvenance(
            promptDocument = "docs/brewing/asset-production.md",
            promptRevision = "test-v1",
        ),
        review = review,
        resourceSha256 = "a".repeat(64),
    )

    private fun approvedReview(): InstructionAssetReview = InstructionAssetReview(
        status = InstructionAssetReviewStatus.APPROVED,
        reviewer = "QA",
        reviewedOn = LocalDate.of(2026, 7, 28),
    )

    private companion object {
        val FAMILY = MethodFamilyId("manual_gravity")
        val PROFILE = BrewerProfileId("v60_02")
        val STAGE = StageId("bloom")
        val CONTENT = StageContentId("pour_bloom")
    }
}
