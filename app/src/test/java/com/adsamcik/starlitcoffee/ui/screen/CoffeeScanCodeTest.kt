package com.adsamcik.starlitcoffee.ui.screen

import com.google.mlkit.vision.barcode.common.Barcode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoffeeScanCodeTest {
    @Test
    fun `product codes keep leading zeroes and full identifiers`() {
        val raw = "000123456789012345678901234567890"
        assertEquals(ScannedCoffeeCode(raw, false), classifyCoffeeCode(raw, Barcode.TYPE_PRODUCT))
    }

    @Test
    fun `custom text identifiers preserve their complete content`() {
        val raw = " coffee:Las Flores / April #2026-10 "
        assertEquals(ScannedCoffeeCode(raw, false), classifyCoffeeCode(raw, Barcode.TYPE_TEXT))
        assertEquals(ScannedCoffeeCode(raw, false), classifyCoffeeCode(raw, Barcode.TYPE_UNKNOWN))
    }

    @Test
    fun `public QR identifiers retain their full value after link validation`() {
        assertEquals(
            ScannedCoffeeCode("HTTPS://EXAMPLE.COM/coffee#lot", true),
            classifyCoffeeCode("HTTPS://EXAMPLE.COM/coffee#lot", Barcode.TYPE_URL),
        )
        assertEquals(
            ScannedCoffeeCode(" http://example.com ", true),
            classifyCoffeeCode(" http://example.com ", Barcode.TYPE_TEXT),
        )
    }

    @Test
    fun `unsafe links never become custom identifiers`() {
        listOf(
            "https://localhost/coffee", "http://192.168.1.2/", "https://example.com:8443/",
            "https://user:password@example.com/", "https://", "file:///sdcard/coffee.jpg",
            "javascript:alert(1)",
        ).forEach { raw ->
            assertNull(raw, classifyCoffeeCode(raw, Barcode.TYPE_TEXT))
        }
        assertNull(classifyCoffeeCode("coffee://bag-42", Barcode.TYPE_URL))
    }

    @Test
    fun `structured noncoffee codes do not trigger capture`() {
        listOf(
            Barcode.TYPE_WIFI, Barcode.TYPE_CONTACT_INFO, Barcode.TYPE_EMAIL, Barcode.TYPE_PHONE,
            Barcode.TYPE_SMS, Barcode.TYPE_GEO, Barcode.TYPE_CALENDAR_EVENT, Barcode.TYPE_DRIVER_LICENSE,
        ).forEach { type -> assertNull(classifyCoffeeCode("not a coffee identifier", type)) }
        listOf("WIFI:T:WPA;S:Home;;", "BEGIN:VCARD\nFN:Coffee", "bitcoin:123", "mailto:coffee@example.com")
            .forEach { raw -> assertNull(classifyCoffeeCode(raw, Barcode.TYPE_TEXT)) }
    }

    @Test
    fun `empty results rearm and product identifier wins over a website`() {
        val website = ScannedCoffeeCode("https://example.com/", true)
        val product = ScannedCoffeeCode("0001234567890", false)
        assertNull(selectCoffeeCode(emptyList()))
        assertNull(classifyCoffeeCode("  ", Barcode.TYPE_TEXT))
        assertNull(classifyCoffeeCode(null, Barcode.TYPE_TEXT))
        assertEquals(product, selectCoffeeCode(listOf(website, product)))
        assertEquals(website, selectCoffeeCode(listOf(website)))
    }

    @Test
    fun `only one disposer can close a scanner and late setup releases instead`() {
        val policy = BarcodeCameraLifecyclePolicy()
        assertTrue(policy.dispose())
        assertFalse(policy.dispose())
        var released = 0
        assertFalse(policy.continueOrRelease { released++ })
        assertEquals(1, released)
        assertFalse(policy.isActive)
    }
}
