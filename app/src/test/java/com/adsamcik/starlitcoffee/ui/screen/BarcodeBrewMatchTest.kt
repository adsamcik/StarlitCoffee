package com.adsamcik.starlitcoffee.ui.screen

import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BarcodeBrewMatchTest {
    private val code = "8591234567890"

    @Test
    fun `only bags with coffee still in stock are returned`() {
        val bags = listOf(
            bag(1, status = "FINISHED", weight = 50f),
            bag(2, status = "OPEN", weight = 0f),
            bag(3, status = "OPEN", weight = -1f),
            bag(4, status = "OPEN", weight = 80f),
            bag(5, status = "SEALED", weight = null),
        )

        assertEquals(listOf(4L, 5L), inStockBagsForBarcode(code, bags).map { it.id })
        assertFalse(hasInStockBarcodeBags(bags.take(3)))
        assertTrue(hasInStockBarcodeBags(bags))
    }

    @Test
    fun `UPC EAN and GTIN representations match the same saved product`() {
        val saved = bag(1).copy(barcode = "036000291452")

        assertEquals(listOf(saved), inStockBagsForBarcode("0036000291452", listOf(saved)))
        assertEquals(listOf(saved), inStockBagsForBarcode("00036000291452", listOf(saved)))
        assertEquals(listOf(saved), inStockBagsForBarcode(" 0360-0029-1452 ", listOf(saved)))
    }

    @Test
    fun `a different product or QR URL cannot match by a barcode prefix or embedded digits`() {
        val saved = bag(1)

        assertTrue(inStockBagsForBarcode("8591234567891", listOf(saved)).isEmpty())
        assertTrue(inStockBagsForBarcode("https://coffee.example/$code", listOf(saved)).isEmpty())
        assertTrue(inStockBagsForBarcode("", listOf(saved)).isEmpty())
        assertTrue(inStockBagsForBarcode("859123", listOf(saved)).isEmpty())
    }

    @Test
    fun `all matching physical bags remain available for an explicit choice`() {
        val sealed = bag(1, status = "SEALED")
        val open = bag(2, status = "OPEN")
        val anotherOpen = bag(3, status = "OPEN")

        assertEquals(
            listOf(2L, 3L, 1L),
            inStockBagsForBarcode(code, listOf(sealed, anotherOpen, open)).map { it.id },
        )
    }

    @Test
    fun `custom product codes use a full exact match`() {
        val saved = bag(1).copy(barcode = "ROASTER-LOT-42")

        assertEquals(listOf(saved), inStockBagsForBarcode("ROASTER-LOT-42", listOf(saved)))
        assertTrue(inStockBagsForBarcode("ROASTER-LOT-43", listOf(saved)).isEmpty())
        assertFalse(hasInStockBarcodeBags(listOf(saved.copy(barcode = "  "))))
    }

    private fun bag(id: Long, status: String = "OPEN", weight: Float? = 250f) = CoffeeBagEntity(
        id = id,
        name = "Coffee $id",
        barcode = code,
        status = status,
        weightG = weight,
        createdAt = id,
    )
}
