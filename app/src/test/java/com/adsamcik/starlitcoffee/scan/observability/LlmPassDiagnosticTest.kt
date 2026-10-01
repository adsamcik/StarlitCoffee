package com.adsamcik.starlitcoffee.scan.observability

import com.adsamcik.starlitcoffee.domain.scandiagnostics.LlmPassDiagnostic
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** Pins the safe diagnostic schema, including removal of legacy private fields. */
class LlmPassDiagnosticTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `success diagnostic round-trips through json`() {
        val original = LlmPassDiagnostic(
            timestampMs = 1_700_000_000_000L,
            pass = LlmPassDiagnostic.Pass.TEXT,
            status = LlmPassDiagnostic.Status.SUCCESS,
            elapsedMs = 76_850L,
            maxTokens = 8192,
            promptCharLen = 1206,
            outputCharLen = 1087,
        )

        val decoded = json.decodeFromString<LlmPassDiagnostic>(json.encodeToString(original))

        assertEquals(original, decoded)
        assertNull(decoded.errorCode)
    }

    @Test
    fun `failure diagnostic keeps only the numeric error code`() {
        val original = LlmPassDiagnostic(
            timestampMs = 1_700_000_000_001L,
            pass = LlmPassDiagnostic.Pass.TEXT,
            status = LlmPassDiagnostic.Status.ERROR,
            elapsedMs = 1_500L,
            maxTokens = 4096,
            promptCharLen = 1206,
            outputCharLen = 0,
            errorCode = 3006,
        )

        val decoded = json.decodeFromString<LlmPassDiagnostic>(json.encodeToString(original))

        assertEquals(original, decoded)
        assertEquals(3006, decoded.errorCode)
    }

    @Test
    fun `legacy raw samples and errors cannot survive re-encoding`() {
        val legacy = """
            {
                "timestampMs": 1700000000001,
                "pass": "TEXT",
                "status": "ERROR",
                "elapsedMs": 1500,
                "maxTokens": 4096,
                "promptCharLen": 1206,
                "outputCharLen": 27,
                "outputSample": "PRIVATE_AI_OUTPUT_7c80",
                "errorMessage": "PRIVATE_EXCEPTION_MESSAGE_903a"
            }
        """.trimIndent()

        val decoded = json.decodeFromString<LlmPassDiagnostic>(legacy)
        val encoded = json.encodeToString(decoded)
        val fields = json.parseToJsonElement(encoded).jsonObject

        assertEquals(LlmPassDiagnostic.Pass.TEXT, decoded.pass)
        assertEquals(LlmPassDiagnostic.Status.ERROR, decoded.status)
        assertEquals(27, decoded.outputCharLen)
        assertNull(decoded.errorCode)
        assertFalse(fields.containsKey("outputSample"))
        assertFalse(fields.containsKey("errorMessage"))
        assertFalse(encoded.contains("PRIVATE_AI_OUTPUT_7c80"))
        assertFalse(encoded.contains("PRIVATE_EXCEPTION_MESSAGE_903a"))
    }

    @Test
    fun `unknown pass and status strings are rejected rather than logged`() {
        val original = LlmPassDiagnostic(
            timestampMs = 1L,
            pass = LlmPassDiagnostic.Pass.TEXT,
            status = LlmPassDiagnostic.Status.SUCCESS,
            elapsedMs = 2L,
            maxTokens = 4096,
            promptCharLen = 3,
            outputCharLen = 4,
        )
        val encoded = json.encodeToString(original)
        val injectedPass = encoded.replace("\"TEXT\"", "\"PRIVATE_INJECTED_PASS\"")
        val injectedStatus = encoded.replace("\"SUCCESS\"", "\"PRIVATE_INJECTED_STATUS\"")

        assertThrows(SerializationException::class.java) {
            json.decodeFromString<LlmPassDiagnostic>(injectedPass)
        }
        assertThrows(SerializationException::class.java) {
            json.decodeFromString<LlmPassDiagnostic>(injectedStatus)
        }
    }

    @Test
    fun `a list of passes round-trips in newest-first order`() {
        val passes = listOf(
            LlmPassDiagnostic(3L, LlmPassDiagnostic.Pass.COMBINE, LlmPassDiagnostic.Status.SUCCESS, 29_222L, 8192, 451, 447),
            LlmPassDiagnostic(2L, LlmPassDiagnostic.Pass.VISION, LlmPassDiagnostic.Status.SUCCESS, 48_529L, 8192, 516, 812),
            LlmPassDiagnostic(1L, LlmPassDiagnostic.Pass.TEXT, LlmPassDiagnostic.Status.SUCCESS, 76_850L, 8192, 1206, 1087),
        )

        val decoded = json.decodeFromString<List<LlmPassDiagnostic>>(json.encodeToString(passes))

        assertEquals(passes, decoded)
        assertEquals(LlmPassDiagnostic.Pass.COMBINE, decoded.first().pass)
    }
}
