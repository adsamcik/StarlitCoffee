package com.adsamcik.starlitcoffee.ui.screen

import com.adsamcik.starlitcoffee.ui.component.DecafFilter
import org.junit.Assert.assertEquals
import org.junit.Test

class SavedRecipesScreenPolicyTest {

    @Test
    fun `saved recipe decaf filter returns to all when selected category disappears`() {
        assertEquals(
            DecafFilter.ALL,
            normalizeSavedRecipeDecafFilter(
                selected = DecafFilter.REGULAR,
                regularCount = 0,
                decafCount = 2,
            ),
        )
        assertEquals(
            DecafFilter.REGULAR,
            normalizeSavedRecipeDecafFilter(
                selected = DecafFilter.REGULAR,
                regularCount = 0,
                decafCount = 0,
            ),
        )
    }
}
