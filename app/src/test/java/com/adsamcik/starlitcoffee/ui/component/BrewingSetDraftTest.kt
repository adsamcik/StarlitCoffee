package com.adsamcik.starlitcoffee.ui.component

import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.BrewingSet
import com.adsamcik.starlitcoffee.data.model.CalcOp
import com.adsamcik.starlitcoffee.data.model.CalcToken
import com.adsamcik.starlitcoffee.data.model.GrinderDataSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class BrewingSetDraftTest {
    private val data = GrinderDataSource.fromJson(File("src/main/assets/grinders.json").readText())
    private val pulsar = BrewingSet("home", method = BrewMethod.PULSAR, setup = initialBrewingSetup(BrewMethod.PULSAR))

    @Test
    fun `starting doses fit each supported brewer capacity`() {
        BrewMethod.entries.forEach { method ->
            val setup = initialBrewingSetup(method)
            val dose = (setup.tokens.single() as CalcToken.Number).floatValue
            org.junit.Assert.assertTrue(method.capacityMaxG == null || dose * setup.ratio <= requireNotNull(method.capacityMaxG))
        }
    }

    @Test
    fun `unnamed set is valid and incomplete text survives restoration`() {
        assertNull(BrewingSetDraft(pulsar).build(data)?.name)
        val draft = BrewingSetDraft(pulsar, name = "Home", ratio = "1.", amount = "bad")
        assertEquals(draft, BrewingSetDraft.decode(draft.encode()))
        assertEquals(listOf(draft), BrewingSetDraft.decodeList(BrewingSetDraft.encodeList(listOf(draft))))
        assertNull(draft.build(data))
    }

    @Test
    fun `same method retains equipment while different method uses its own recipe`() {
        val metal = BrewingSetDraft(pulsar.copy(setup = pulsar.setup.copy(filterType = "METAL_19K")), name = "Work")
        assertEquals(metal, metal.withMethod(BrewMethod.PULSAR))
        val espresso = requireNotNull(metal.withMethod(BrewMethod.ESPRESSO).build(data))
        assertEquals("Work", espresso.name)
        assertEquals(2f, espresso.setup.ratio, 0f)
        assertEquals(listOf(CalcToken.Number("18")), espresso.setup.tokens)
        assertNull(espresso.setup.filterType)
    }

    @Test
    fun `equipment edits keep a compound calculation until explicitly replaced`() {
        val tokens = listOf(CalcToken.Number("10"), CalcToken.Operator(CalcOp.ADD), CalcToken.Number("10"))
        val draft = BrewingSetDraft(pulsar.copy(setup = pulsar.setup.copy(tokens = tokens)))
        assertEquals(tokens, requireNotNull(draft.copy(name = "Office").build(data)).setup.tokens)
        val replaced = requireNotNull(draft.copy(amount = "25,5", ratio = "16").build(data))
        assertEquals(listOf(CalcToken.Number("25.5")), replaced.setup.tokens)
        assertEquals(16f, replaced.setup.ratio, 0f)
    }

    @Test
    fun `unsupported grinder stays in draft for returning to paper but is not persisted`() {
        val paper = BrewingSetDraft(pulsar.copy(setup = pulsar.setup.copy(grinderId = "1zpresso-zp6-special")))
        val metal = paper.copy(set = paper.set.copy(setup = paper.set.setup.copy(filterType = "METAL_19K")))
        assertEquals("1zpresso-zp6-special", metal.set.setup.grinderId)
        assertNull(requireNotNull(metal.build(data)).setup.grinderId)
        assertEquals("1zpresso-zp6-special", requireNotNull(paper.build(data)).setup.grinderId)
    }
}
