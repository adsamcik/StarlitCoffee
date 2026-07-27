package com.adsamcik.starlitcoffee.domain.brewing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GrindGuidanceResolverTest {

    @Test
    fun `exact configuration match wins over profile and family guidance`() {
        val records = listOf(
            record(),
            record(profile = "v60_02"),
            record(profile = "v60_02", filters = listOf("cone_paper"), suggestedStart = 18.0),
        )
        val request = request(profile = "v60_02", filters = listOf("cone_paper"))

        val result = GrindGuidanceResolver.resolve(request, records)

        val specific = result as GrindGuidanceResolution.Specific
        assertEquals(18.0, specific.record.suggestedStart, 0.001)
    }

    @Test
    fun `unknown equipment receives generic guidance instead of false precision`() {
        val result = GrindGuidanceResolver.resolve(
            request(profile = "v60_02", filters = listOf("cone_paper"), equipmentKnown = false),
            listOf(record(profile = "v60_02", filters = listOf("cone_paper"))),
        )

        assertTrue(result is GrindGuidanceResolution.Generic)
    }

    private fun request(
        profile: String,
        filters: List<String>,
        equipmentKnown: Boolean = true,
    ) = GrindGuidanceRequest(
        grinderId = "test-grinder",
        methodFamilyId = MethodFamilyId("manual_gravity"),
        brewerProfileId = BrewerProfileId(profile),
        filterStack = filters.map(::FilterProfileId),
        equipmentIsKnown = equipmentKnown,
        genericDescriptor = GenericGrindDescriptor.MEDIUM_FINE,
    )

    private fun record(
        profile: String? = null,
        filters: List<String>? = null,
        suggestedStart: Double = 20.0,
    ) = GrindGuidanceRecord(
        grinderId = "test-grinder",
        methodFamilyId = MethodFamilyId("manual_gravity"),
        brewerProfileId = profile?.let(::BrewerProfileId),
        filterStack = filters?.map(::FilterProfileId),
        rangeStart = 10.0,
        rangeEnd = 30.0,
        suggestedStart = suggestedStart,
        adjustmentStepSize = 1.0,
        adjustmentNote = "Test",
        evidence = GrindEvidence(GrindEvidenceClass.GENERIC, EvidenceConfidence.LIMITED),
    )
}
