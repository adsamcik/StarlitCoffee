package com.adsamcik.starlitcoffee.arch

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

/**
 * Guards bounded CPU session configuration at the canonical inference call sites.
 *
 * Creating the LLM engine with the GPU backend SIGSEGVs inside LiteRT-LM's
 * `nativeCreateEngine` on the x86_64 emulator's software GPU. CPU is selected
 * through the supported typed session option; legacy prewarm cannot bound its
 * context allocation. SDK bridge tests establish the wire configuration, while
 * this source guard catches new app call sites that omit those explicit options.
 */
class MindlayerInferencePrewarmGuardTest {

    @Test
    fun `every mindlayer inference session pins CPU and a bounded token budget`() {
        val mainSrc = resolveMainSrcDir()
        val offenders = mainSrc.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { file -> hasRealInferCall(file) && !hasBoundedCpuSessions(file) }
            .map { it.name }
            .toList()

        assertTrue(
            "Every inference session must explicitly select CPU and maxTokens in 128..8192. Offenders: $offenders",
            offenders.isEmpty(),
        )
    }

    @Test
    fun `inference call sites cannot restore unbounded legacy model prewarming`() {
        resolveMainSrcDir().walkTopDown()
            .filter { it.isFile && it.extension == "kt" && hasRealInferCall(it) }
            .forEach { file ->
                assertFalse("${file.name} must leave allocation to its bounded session", LEGACY_PREWARM.containsMatchIn(file.readText()))
            }
    }

    private fun hasRealInferCall(file: File): Boolean =
        file.readLines().any { line ->
            val trimmed = line.trim()
            !trimmed.startsWith("*") &&
                !trimmed.startsWith("//") &&
                !trimmed.startsWith("/*") &&
                INFER_CALL.containsMatchIn(trimmed)
        }

    private fun hasBoundedCpuSessions(file: File): Boolean {
        val text = file.readText()
        val scopes = SESSION_SCOPE.findAll(text).map { it.groupValues[1] }.toList()
        return scopes.isNotEmpty() && scopes.all { scope ->
            val tokenValue = TOKEN_BUDGET.find(scope)?.groupValues?.get(1)
            val tokens = tokenValue?.toIntOrNull() ?: tokenValue?.let { constant ->
                Regex("""const\s+val\s+$constant\s*=\s*(\d+)""").find(text)?.groupValues?.get(1)?.toIntOrNull()
            }
            CPU_BACKEND.containsMatchIn(scope) && tokens != null && tokens in 128..8192
        }
    }

    private fun resolveMainSrcDir(): File {
        val candidates = listOf(File("src/main/java"), File("app/src/main/java"))
        return candidates.firstOrNull { it.isDirectory }
            ?: error("Could not locate src/main/java (cwd=${File(".").absolutePath})")
    }

    private companion object {
        private val INFER_CALL = Regex("""\.infer\s*\{""")
        private val SESSION_SCOPE = Regex("""ephemeralSession\s*\{([^}]+)""")
        private val CPU_BACKEND = Regex("""\bbackend\s*=\s*InferenceBackend\.CPU\b""")
        private val TOKEN_BUDGET = Regex("""\bmaxTokens\s*=\s*([A-Za-z_][A-Za-z_0-9]*|\d+)""")
        private val LEGACY_PREWARM = Regex("""\bprewarm\s*\(\s*(?:InferenceBackend|PREWARM_BACKEND)""")
    }
}
