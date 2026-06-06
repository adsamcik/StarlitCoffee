package com.adsamcik.starlitcoffee.test.corpus

import com.adsamcik.starlitcoffee.data.network.llm.MindlayerLlmInferenceProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the [CorpusFields] contract against drift from the production
 * extractor and keeps the gate selection honest.
 */
class FieldSpecTest {

    @Test
    fun `field spec mapping matches the production provider field mapping`() {
        // metadataKey -> appFieldName here must equal the LLM-side mapping the
        // real extractor uses, or scoring would compare the wrong slots.
        val expected = MindlayerLlmInferenceProvider.fieldMapping
        val actual = CorpusFields.ALL.associate { it.metadataKey to it.appFieldName }
        assertEquals(expected, actual)
    }

    @Test
    fun `gate fields are a meaningful non-trivial subset`() {
        val gate = CorpusFields.gateFields.map { it.metadataKey }.toSet()
        // Core identity fields a studio-perfect label must yield.
        assertTrue(gate.containsAll(listOf("name", "roaster", "origin", "process", "roastLevel", "weight")))
        // Free-text / formatting-variant fields must stay out of the hard gate.
        assertTrue("tastingNotes must not be hard-gated", "tastingNotes" !in gate)
        assertTrue("altitude must not be hard-gated", "altitude" !in gate)
        assertTrue("farm must not be hard-gated", "farm" !in gate)
        assertTrue("gate must be a strict subset", gate.size < CorpusFields.ALL.size)
    }

    @Test
    fun `every field has a unique key on both sides`() {
        assertEquals(CorpusFields.ALL.size, CorpusFields.metadataKeys.toSet().size)
        assertEquals(CorpusFields.ALL.size, CorpusFields.appFieldNames.toSet().size)
    }
}
