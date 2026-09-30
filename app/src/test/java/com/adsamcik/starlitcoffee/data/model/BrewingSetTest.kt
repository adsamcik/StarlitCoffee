package com.adsamcik.starlitcoffee.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrewingSetTest {
    private val home = BrewingSet("home", "Home", BrewMethod.PULSAR,
        CalculatorSetup(18f, tokens = listOf(CalcToken.Number("20")), filterType = "PAPER"))
    private val work = home.copy(id = "work", name = "Work")

    @Test
    fun `codec keeps good records when another record is damaged or from a future version`() {
        val encoded = BrewingSetCodec.encode(listOf(home, work))
        val mixed = encoded.dropLast(1) + ", {\"id\":\"future\",\"method\":\"FUTURE\"}, {\"id\":\"broken\"}]"
        assertEquals(listOf(home, work), BrewingSetCodec.decode(mixed))
        assertEquals(emptyList<BrewingSet>(), BrewingSetCodec.decode("{}"))
        assertEquals(listOf(home), BrewingSetCodec.decode(BrewingSetCodec.encode(listOf(home, home))))
        assertNull(home.copy(name = " ").validated())
    }

    @Test
    fun `equipment edit retains latest input and ignores a queued write from the previous revision`() {
        val latest = home.copy(setup = home.setup.copy(ratio = 16f, tokens = listOf(CalcToken.Number("25"))))
        val edited = BrewingSetSelection(listOf(latest, work), home.id).upsert(
            home.copy(name = "Home filter", setup = home.setup.copy(grinderId = "fellow-ode-gen2")),
        )
        assertEquals(16f, edited.active.setup.ratio, 0f)
        assertEquals(latest.setup.tokens, edited.active.setup.tokens)
        assertEquals("fellow-ode-gen2", edited.active.setup.grinderId)
        assertEquals(1, edited.active.revision)
        assertEquals(edited, edited.remember(home.id, 0, home.setup))
    }

    @Test
    fun `changing method starts at the method default and active deletion picks a remaining set`() {
        val changed = BrewingSetSelection(listOf(home, work), home.id).upsert(
            home.copy(method = BrewMethod.ESPRESSO, setup = CalculatorSetup(BrewMethod.ESPRESSO.defaultRatio)),
        )
        assertEquals(2f, changed.active.setup.ratio, 0f)
        assertEquals(emptyList<CalcToken>(), changed.active.setup.tokens)
        val remaining = changed.remove(home.id)
        assertEquals(work.id, remaining.activeId)
        assertEquals(remaining, remaining.remove(work.id))
        assertEquals(remaining, remaining.select("unknown"))
    }
}
