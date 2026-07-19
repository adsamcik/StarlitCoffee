package com.adsamcik.starlitcoffee.ui.screen

import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity
import com.adsamcik.starlitcoffee.ui.component.DecafFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BagInventoryScreenPolicyTest {

    @Test
    fun `selected bag resolves from latest inventory state`() {
        val original = CoffeeBagEntity(
            id = 42L,
            name = "Fresh lot",
            status = "OPEN",
            weightG = 250f,
        )
        val updated = original.copy(status = "FINISHED", weightG = 0f)

        assertEquals(original, selectedInventoryBag(listOf(original), selectedBagId = 42L))
        assertEquals(updated, selectedInventoryBag(listOf(updated), selectedBagId = 42L))
        assertNull(selectedInventoryBag(emptyList(), selectedBagId = 42L))
    }

    @Test
    fun `inventory decaf filter returns to all when selected category disappears`() {
        assertEquals(
            DecafFilter.ALL,
            normalizeInventoryDecafFilter(
                selected = DecafFilter.REGULAR,
                regularCount = 0,
                decafCount = 2,
            ),
        )
        assertEquals(
            DecafFilter.ALL,
            normalizeInventoryDecafFilter(
                selected = DecafFilter.DECAF,
                regularCount = 2,
                decafCount = 0,
            ),
        )
    }

    @Test
    fun `empty inventory preserves selected filter policy`() {
        assertEquals(
            DecafFilter.DECAF,
            normalizeInventoryDecafFilter(
                selected = DecafFilter.DECAF,
                regularCount = 0,
                decafCount = 0,
            ),
        )
        assertEquals(
            DecafFilter.ALL,
            normalizeInventoryDecafFilter(
                selected = DecafFilter.ALL,
                regularCount = 0,
                decafCount = 0,
            ),
        )
    }
}
