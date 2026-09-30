package com.adsamcik.starlitcoffee.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GrinderSettingFormatterTest {
    private val catalog = GrinderDataSource.fromJson(grinderCatalogAsset())

    @Test
    fun `Ode printed subdivisions survive coarsening across a numbered boundary`() {
        val grinder = catalog.grinders.single { it.id == "fellow-ode-gen2" }
        val dial = requireNotNull(grinder.dial)
        val step = requireNotNull(dial.stepSize)
        val recommendation = requireNotNull(catalog.recommendationFor(grinder.id, BrewMethod.PULSAR, FilterType.PAPER))
        assertEquals("5.2", GrinderSettingFormatter.label(grinder, recommendation.suggestedStart))
        assertEquals("5 + 2 clicks", GrinderSettingFormatter.format(grinder, recommendation.suggestedStart).breakdown)
        assertEquals("1 click", GrinderSettingFormatter.adjustment(grinder, recommendation.adjustmentStepSize))
        assertEquals("6", GrinderSettingFormatter.label(grinder, recommendation.suggestedStart + step))
        assertEquals("5.1", GrinderSettingFormatter.label(grinder, recommendation.suggestedStart - step))
    }

    @Test
    fun `ZP6 describes total clicks and real printed marks after a full rotation`() {
        val grinder = catalog.grinders.single { it.id == "1zpresso-zp6-special" }
        assertEquals("5.2", GrinderSettingFormatter.label(grinder, 5.2f))
        assertEquals("52 clicks from zero", GrinderSettingFormatter.format(grinder, 5.2f).breakdown)
        assertEquals("1 × 360° · 0.1", GrinderSettingFormatter.label(grinder, 9.1f))
        assertEquals("91 clicks from zero", GrinderSettingFormatter.format(grinder, 9.1f).breakdown)
        assertEquals("2 clicks", GrinderSettingFormatter.adjustment(grinder, 0.2f))
    }

    @Test
    fun `C40 counts clicks while Niche keeps its continuous dial scale`() {
        val c40 = catalog.grinders.single { it.id == "comandante-c40" }
        val niche = catalog.grinders.single { it.id == "niche-zero" }
        assertEquals("24 clicks", GrinderSettingFormatter.label(c40, 24f))
        assertEquals("from zero", GrinderSettingFormatter.format(c40, 24f).breakdown)
        assertEquals("15.5", GrinderSettingFormatter.label(niche, 15.5f))
        assertEquals("1", GrinderSettingFormatter.adjustment(niche, 1f))
    }

    @Test
    fun `expanded ranges stay on real Ode steps and within its dial`() {
        val dial = requireNotNull(catalog.grinders.single { it.id == "fellow-ode-gen2" }.dial)
        assertEquals(1f, dial.lowerBound(-2f), 0f)
        assertEquals(11f, dial.upperBound(12f), 0f)
        assertTrue(dial.accepts(dial.lowerBound(3.75f)))
        assertTrue(dial.accepts(dial.upperBound(8.25f)))
        val odePoint = 5f + 2f / 3f
        assertEquals(odePoint, dial.lowerBound(odePoint), 0.00001f)
        assertEquals(odePoint, dial.upperBound(odePoint), 0.00001f)
    }
}
