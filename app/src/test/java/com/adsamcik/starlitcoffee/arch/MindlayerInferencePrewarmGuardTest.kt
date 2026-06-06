package com.adsamcik.starlitcoffee.arch

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Fitness function guarding the "prewarm CPU before infer" invariant for the
 * Mindlayer on-device LLM.
 *
 * Creating the LLM engine with the GPU backend SIGSEGVs inside LiteRT-LM's
 * `nativeCreateEngine` on the x86_64 emulator's software GPU (it crashes while
 * log-formatting the engine config; tracked as LiteRT-LM #1686 / #2028). Every
 * `mindlayer.infer { ... }` call therefore MUST be preceded by a
 * `prewarm(InferenceBackend.CPU)` so the engine is pinned to the safe CPU
 * backend before creation.
 *
 * This invariant was previously a convention repeated at each call site, and a
 * diagnostic ("Run Test Prompt") regressed by calling `infer` without the
 * prewarm — silently reintroducing the crash. This test fails if any source
 * file issues a real `infer { }` call without also pinning the CPU backend, so
 * the regression cannot return unnoticed.
 */
class MindlayerInferencePrewarmGuardTest {

    @Test
    fun `every mindlayer infer call site pins the CPU backend`() {
        val mainSrc = resolveMainSrcDir()
        val offenders = mainSrc.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { file -> hasRealInferCall(file) && !pinsCpuBackend(file) }
            .map { it.name }
            .toList()

        assertTrue(
            "Every file that calls mindlayer.infer { } must first pin the CPU backend " +
                "(prewarm(InferenceBackend.CPU) / PREWARM_BACKEND) to avoid the LiteRT-LM " +
                "GPU-init SIGSEGV. Offending file(s) missing the prewarm: $offenders",
            offenders.isEmpty(),
        )
    }

    private fun hasRealInferCall(file: File): Boolean =
        file.readLines().any { line ->
            val trimmed = line.trim()
            !trimmed.startsWith("*") &&
                !trimmed.startsWith("//") &&
                !trimmed.startsWith("/*") &&
                INFER_CALL.containsMatchIn(trimmed)
        }

    private fun pinsCpuBackend(file: File): Boolean {
        val text = file.readText()
        val hasPrewarm = PREWARM_CALL.containsMatchIn(text)
        val referencesCpu = text.contains("InferenceBackend.CPU") || text.contains("PREWARM_BACKEND")
        return hasPrewarm && referencesCpu
    }

    private fun resolveMainSrcDir(): File {
        val candidates = listOf(File("src/main/java"), File("app/src/main/java"))
        return candidates.firstOrNull { it.isDirectory }
            ?: error("Could not locate src/main/java (cwd=${File(".").absolutePath})")
    }

    private companion object {
        private val INFER_CALL = Regex("""\.infer\s*\{""")
        private val PREWARM_CALL = Regex("""prewarm\s*\(""")
    }
}
