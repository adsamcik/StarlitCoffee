package com.adsamcik.starlitcoffee.ui.component

import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BagWeightPolicyTest {

    @Test
    fun `clearing existing weight persists null for current and initial weight`() {
        val bag = CoffeeBagEntity(name = "Lot 1", weightG = 180f, initialWeightG = 250f)
        val validation = validateBagWeightInput("  ")

        val updated = applyValidatedBagWeight(bag, validation)

        assertTrue(validation.isValid)
        assertNull(updated?.weightG)
        assertNull(updated?.initialWeightG)
    }

    @Test
    fun `malformed nonblank weight is validation failure and cannot update bag`() {
        val bag = CoffeeBagEntity(name = "Lot 1", weightG = 180f)
        val validation = validateBagWeightInput("two bags")

        assertFalse(validation.isValid)
        assertNull(applyValidatedBagWeight(bag, validation))
    }

    @Test
    fun `valid weight parses units before updating`() {
        val validation = validateBagWeightInput("1 kg")

        assertTrue(validation.isValid)
        assertEquals(1000f, validation.valueGrams ?: 0f, 0.01f)
    }

    @Test
    fun `first known positive weight establishes current and initial baseline`() {
        val bag = CoffeeBagEntity(name = "Lot 1", weightG = null, initialWeightG = null)

        val updated = applyValidatedBagWeight(bag, validateBagWeightInput("250 g"))

        assertEquals(250f, updated?.weightG ?: 0f, 0.01f)
        assertEquals(250f, updated?.initialWeightG ?: 0f, 0.01f)
    }

    @Test
    fun `editing known weight preserves existing initial baseline`() {
        val bag = CoffeeBagEntity(name = "Lot 1", weightG = 180f, initialWeightG = 250f)

        val updated = applyValidatedBagWeight(bag, validateBagWeightInput("150 g"))

        assertEquals(150f, updated?.weightG ?: 0f, 0.01f)
        assertEquals(250f, updated?.initialWeightG ?: 0f, 0.01f)
    }

    @Test
    fun `zero weight is not a valid known weight`() {
        val validation = validateBagWeightInput("0 g")

        assertFalse(validation.isValid)
        assertNull(validation.valueGrams)
    }
}
