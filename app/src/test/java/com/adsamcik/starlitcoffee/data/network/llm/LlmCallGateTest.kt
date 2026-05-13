package com.adsamcik.starlitcoffee.data.network.llm

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LlmCallGateTest {

    // --- Serialization ---

    @Test
    fun `gate serializes overlapping calls`() = runTest {
        var active = 0
        var maxActive = 0

        val first = async { enterGate { active++; maxActive = maxOf(maxActive, active); delay(50); active-- } }
        val second = async { enterGate { active++; maxActive = maxOf(maxActive, active); delay(50); active-- } }

        first.await()
        second.await()

        assertEquals(1, maxActive)
    }

    private suspend fun enterGate(block: suspend () -> Unit) {
        MindlayerLlmCallGate.withPermit(block)
    }
}
