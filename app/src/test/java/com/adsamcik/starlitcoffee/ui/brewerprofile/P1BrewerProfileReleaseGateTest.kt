package com.adsamcik.starlitcoffee.ui.brewerprofile

import com.adsamcik.starlitcoffee.domain.brewing.BrewerProfileId
import com.adsamcik.starlitcoffee.domain.brewing.BuiltInRecipeId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class P1BrewerProfileReleaseGateTest {

    @Test
    fun `setup factory exposes only exact recipes admitted by the release gate`() {
        val eligibleRecipeId = BuiltInRecipeId("clever_water_first_15_250")
        val sameProfileSiblingId = BuiltInRecipeId("clever_coffee_first_15_250")

        val state = P1BrewerProfileSetupStateFactory.create(
            executableRecipeIds = setOf(eligibleRecipeId),
        )
        val profile = state.profiles.single()

        assertEquals(BrewerProfileId("clever_style"), profile.profileId)
        assertEquals(listOf(eligibleRecipeId), profile.recipes.map { recipe -> recipe.id })
        assertFalse(profile.recipes.any { recipe -> recipe.id == sameProfileSiblingId })
    }
}
