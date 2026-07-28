package com.adsamcik.starlitcoffee.ui.brewerprofile

import com.adsamcik.starlitcoffee.domain.brewing.BrewerProfileId
import org.junit.Assert.assertEquals
import org.junit.Test

class P1BrewerProfileReleaseGateTest {

    @Test
    fun `setup factory exposes only profiles admitted by the release gate`() {
        val approvedProfile = BrewerProfileId("cezve_generic")

        val state = P1BrewerProfileSetupStateFactory.create(
            visibleProfileIds = setOf(approvedProfile),
        )

        assertEquals(listOf(approvedProfile), state.profiles.map { option -> option.profileId })
    }
}
