package com.adsamcik.starlitcoffee.util

import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity
import com.adsamcik.starlitcoffee.data.model.InventoryAlertType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InventoryAlertEngineTest {

    // --- 4a: Freshness countdown ---

    @Test
    fun `bag approaching peak emits freshness alert`() {
        val now = 100L * DAYS
        val bag = openBag(id = 1, name = "Ethiopia", roastDate = now - 5 * DAYS)

        val alerts = InventoryAlertEngine.freshnessCountdownAlerts(listOf(bag), now)

        assertEquals(1, alerts.size)
        assertEquals(InventoryAlertType.FRESHNESS, alerts[0].type)
        assertTrue(alerts[0].message.contains("peak"))
    }

    @Test
    fun `bag at peak day 18 approaching mellowing emits alert`() {
        val now = 100L * DAYS
        val bag = openBag(id = 2, name = "Colombia", roastDate = now - 19 * DAYS)

        val alerts = InventoryAlertEngine.freshnessCountdownAlerts(listOf(bag), now)

        assertEquals(1, alerts.size)
        assertTrue(alerts[0].message.contains("mellowing"))
        assertTrue(alerts[0].message.contains("brew it now"))
    }

    @Test
    fun `bag approaching vintage emits alert`() {
        val now = 100L * DAYS
        val bag = openBag(id = 3, name = "Kenya", roastDate = now - 40 * DAYS)

        val alerts = InventoryAlertEngine.freshnessCountdownAlerts(listOf(bag), now)

        assertEquals(1, alerts.size)
        assertTrue(alerts[0].message.contains("vintage"))
    }

    @Test
    fun `bag in mid-peak emits no freshness alert`() {
        val now = 100L * DAYS
        val bag = openBag(id = 4, name = "Brazil", roastDate = now - 14 * DAYS)

        val alerts = InventoryAlertEngine.freshnessCountdownAlerts(listOf(bag), now)

        assertTrue(alerts.isEmpty())
    }

    @Test
    fun `bag without roast date is silently skipped`() {
        val now = 100L * DAYS
        val bag = openBag(id = 5, name = "Mystery", roastDate = null)

        val alerts = InventoryAlertEngine.freshnessCountdownAlerts(listOf(bag), now)

        assertTrue(alerts.isEmpty())
    }

    @Test
    fun `sealed bag is skipped for freshness countdown`() {
        val now = 100L * DAYS
        val bag = CoffeeBagEntity(id = 6, name = "Sealed", status = "SEALED", roastDate = now - 5 * DAYS)

        val alerts = InventoryAlertEngine.freshnessCountdownAlerts(listOf(bag), now)

        assertTrue(alerts.isEmpty())
    }

    // --- 4b: Expiry alerts ---

    @Test
    fun `bag expiring within 14 days emits expiry alert`() {
        val now = 100L * DAYS
        val bag = openBag(id = 10, name = "Guatemala", expiryDate = now + 7 * DAYS)

        val alerts = InventoryAlertEngine.expiryAlerts(listOf(bag), now)

        assertEquals(1, alerts.size)
        assertEquals(InventoryAlertType.EXPIRY, alerts[0].type)
        assertTrue(alerts[0].message.contains("7 days"))
    }

    @Test
    fun `expired bag emits expired alert`() {
        val now = 100L * DAYS
        val bag = openBag(id = 11, name = "Peru", expiryDate = now - 2 * DAYS)

        val alerts = InventoryAlertEngine.expiryAlerts(listOf(bag), now)

        assertEquals(1, alerts.size)
        assertTrue(alerts[0].message.contains("Expired"))
    }

    @Test
    fun `sealed bag with inferred expiry emits alert`() {
        val now = 400L * DAYS
        val bag = CoffeeBagEntity(
            id = 12, name = "Honduras", status = "SEALED",
            roastDate = now - 360 * DAYS,
        )

        val alerts = InventoryAlertEngine.expiryAlerts(listOf(bag), now)

        assertEquals(1, alerts.size)
        assertTrue(alerts[0].message.contains("estimated"))
    }

    @Test
    fun `bag with far-future expiry emits no alert`() {
        val now = 100L * DAYS
        val bag = openBag(id = 13, name = "Rwanda", expiryDate = now + 180 * DAYS)

        val alerts = InventoryAlertEngine.expiryAlerts(listOf(bag), now)

        assertTrue(alerts.isEmpty())
    }

    @Test
    fun `finished bag is excluded from expiry alerts`() {
        val now = 100L * DAYS
        val bag = CoffeeBagEntity(
            id = 14, name = "Done", status = "FINISHED",
            expiryDate = now + 3 * DAYS,
        )

        val alerts = InventoryAlertEngine.expiryAlerts(listOf(bag), now)

        assertTrue(alerts.isEmpty())
    }

    // --- 4b: Staleness alerts ---

    @Test
    fun `open bag opened over 30 days ago with weight emits staleness alert`() {
        val now = 100L * DAYS
        val bag = CoffeeBagEntity(
            id = 20, name = "Forgotten", status = "OPEN",
            openedDate = now - 45 * DAYS, weightG = 200f,
        )

        val alerts = InventoryAlertEngine.stalenessAlerts(listOf(bag), now)

        assertEquals(1, alerts.size)
        assertEquals(InventoryAlertType.STALENESS, alerts[0].type)
        assertTrue(alerts[0].message.contains("45 days ago"))
    }

    @Test
    fun `open bag with low weight is not stale`() {
        val now = 100L * DAYS
        val bag = CoffeeBagEntity(
            id = 21, name = "Almost done", status = "OPEN",
            openedDate = now - 45 * DAYS, weightG = 30f,
        )

        val alerts = InventoryAlertEngine.stalenessAlerts(listOf(bag), now)

        assertTrue(alerts.isEmpty())
    }

    // --- 4b: Aging sealed alerts ---

    @Test
    fun `sealed bag roasted 70 days ago emits aging alert`() {
        val now = 100L * DAYS
        val bag = CoffeeBagEntity(
            id = 30, name = "Old Sealed", status = "SEALED",
            roastDate = now - 70 * DAYS,
        )

        val alerts = InventoryAlertEngine.agingSealedAlerts(listOf(bag), now)

        assertEquals(1, alerts.size)
        assertEquals(InventoryAlertType.AGING_SEALED, alerts[0].type)
        assertTrue(alerts[0].message.contains("open or freeze"))
    }

    @Test
    fun `sealed bag roasted 30 days ago emits no aging alert`() {
        val now = 100L * DAYS
        val bag = CoffeeBagEntity(
            id = 31, name = "Fresh Sealed", status = "SEALED",
            roastDate = now - 30 * DAYS,
        )

        val alerts = InventoryAlertEngine.agingSealedAlerts(listOf(bag), now)

        assertTrue(alerts.isEmpty())
    }

    // --- 4d: Focus mode ---

    @Test
    fun `three open bags triggers focus alert on best bag`() {
        val now = 100L * DAYS
        val bags = listOf(
            openBag(id = 40, name = "Peak", roastDate = now - 14 * DAYS, weightG = 150f),
            openBag(id = 41, name = "Old", roastDate = now - 50 * DAYS, weightG = 200f),
            openBag(id = 42, name = "Vintage", roastDate = now - 80 * DAYS, weightG = 100f),
        )

        val alerts = InventoryAlertEngine.focusAlerts(bags, now)

        assertEquals(1, alerts.size)
        assertEquals(InventoryAlertType.FOCUS, alerts[0].type)
        assertEquals("Peak", alerts[0].bagName)
        assertTrue(alerts[0].message.contains("Focus on Peak"))
        assertTrue(alerts[0].message.contains("2 bags can wait"))
    }

    @Test
    fun `two open bags does not trigger focus alert`() {
        val now = 100L * DAYS
        val bags = listOf(
            openBag(id = 50, name = "A", roastDate = now - 10 * DAYS),
            openBag(id = 51, name = "B", roastDate = now - 20 * DAYS),
        )

        val alerts = InventoryAlertEngine.focusAlerts(bags, now)

        assertTrue(alerts.isEmpty())
    }

    @Test
    fun `focus picks bag with no roast date using UNKNOWN weight`() {
        val now = 100L * DAYS
        val bags = listOf(
            openBag(id = 60, name = "NoDate1", roastDate = null, weightG = 100f),
            openBag(id = 61, name = "NoDate2", roastDate = null, weightG = 200f),
            openBag(id = 62, name = "NoDate3", roastDate = null, weightG = 50f),
        )

        val alerts = InventoryAlertEngine.focusAlerts(bags, now)

        assertEquals(1, alerts.size)
        assertEquals(InventoryAlertType.FOCUS, alerts[0].type)
    }

    // --- Integration: buildAlerts ---

    @Test
    fun `buildAlerts aggregates all alert types`() {
        val now = 100L * DAYS
        val bags = listOf(
            openBag(id = 70, name = "Approaching", roastDate = now - 5 * DAYS),
            CoffeeBagEntity(
                id = 71, name = "Expiring", status = "OPEN",
                expiryDate = now + 5 * DAYS,
            ),
            CoffeeBagEntity(
                id = 72, name = "Stale", status = "OPEN",
                openedDate = now - 40 * DAYS, weightG = 150f,
            ),
            CoffeeBagEntity(
                id = 73, name = "Aging", status = "SEALED",
                roastDate = now - 90 * DAYS,
            ),
        )

        val alerts = InventoryAlertEngine.buildAlerts(bags, nowMillis = now)

        val types = alerts.map { it.type }.toSet()
        assertTrue(InventoryAlertType.FRESHNESS in types)
        assertTrue(InventoryAlertType.EXPIRY in types)
        assertTrue(InventoryAlertType.STALENESS in types)
        assertTrue(InventoryAlertType.AGING_SEALED in types)
    }

    // --- Helpers ---

    private fun openBag(
        id: Long,
        name: String,
        roastDate: Long? = null,
        openedDate: Long? = null,
        weightG: Float? = null,
        expiryDate: Long? = null,
    ) = CoffeeBagEntity(
        id = id,
        name = name,
        status = "OPEN",
        roastDate = roastDate,
        openedDate = openedDate,
        weightG = weightG,
        expiryDate = expiryDate,
    )

    private companion object {
        const val DAYS = 24L * 60 * 60 * 1000
    }
}
