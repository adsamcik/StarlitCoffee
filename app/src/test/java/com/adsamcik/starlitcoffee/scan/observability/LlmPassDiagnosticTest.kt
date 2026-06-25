package com.adsamcik.starlitcoffee.scan.observability

import com.adsamcik.starlitcoffee.domain.scandiagnostics.LlmPassDiagnostic
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the [LlmPassDiagnostic] serialization contract — the record is persisted
 * as JSON in [ScanLlmDiagnosticsStore] and shared verbatim in scan bug reports,
 * so a silent field rename or shape change would lose observability data.
 */
class LlmPassDiagnosticTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `success diagnostic round-trips through json`() {
        val original = LlmPassDiagnostic(
            timestampMs = 1_700_000_000_000L,
            pass = LlmPassDiagnostic.Pass.TEXT.name,
            status = LlmPassDiagnostic.Status.SUCCESS.name,
            elapsedMs = 76_850L,
            maxTokens = 8192,
            promptCharLen = 1206,
            outputCharLen = 1087,
            outputSample = "{\"fields\":{\"origin\":\"Colombia\"}}",
            errorMessage = null,
        )
        val decoded = json.decodeFromString<LlmPassDiagnostic>(json.encodeToString(original))
        assertEquals(original, decoded)
    }

    @Test
    fun `failure diagnostic keeps the error message and null output`() {
        val original = LlmPassDiagnostic(
            timestampMs = 1_700_000_000_001L,
            pass = LlmPassDiagnostic.Pass.TEXT.name,
            status = LlmPassDiagnostic.Status.ERROR.name,
            elapsedMs = 1_500L,
            maxTokens = 4096,
            promptCharLen = 1206,
            outputCharLen = 0,
            outputSample = null,
            errorMessage = "input_exceeds_context (reserved=3743, estimated_input=406, max=4096, remaining=353)",
        )
        val decoded = json.decodeFromString<LlmPassDiagnostic>(json.encodeToString(original))
        assertEquals(original, decoded)
        assertNull(decoded.outputSample)
        assertEquals(original.errorMessage, decoded.errorMessage)
    }

    @Test
    fun `a list of passes round-trips in newest-first order`() {
        val passes = listOf(
            LlmPassDiagnostic(3L, "COMBINE", "SUCCESS", 29_222L, 8192, 451, 447, "{...}", null),
            LlmPassDiagnostic(2L, "VISION", "SUCCESS", 48_529L, 8192, 516, 812, "{...}", null),
            LlmPassDiagnostic(1L, "TEXT", "SUCCESS", 76_850L, 8192, 1206, 1087, "{...}", null),
        )
        val decoded = json.decodeFromString<List<LlmPassDiagnostic>>(json.encodeToString(passes))
        assertEquals(passes, decoded)
        assertEquals("COMBINE", decoded.first().pass)
    }
}
