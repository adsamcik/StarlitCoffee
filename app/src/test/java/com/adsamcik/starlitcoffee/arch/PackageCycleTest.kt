package com.adsamcik.starlitcoffee.arch

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Fitness function guarding the dependency direction between the scan pipeline
 * and the on-device LLM layer.
 *
 * The intended architecture is a one-way edge: `scan.*` (and the view models)
 * may depend on `data.network.llm`, but `data.network.llm` must NOT depend back
 * on `scan.*`. The shared field-attribution contract (`FieldContext` /
 * `FieldSource`) was deliberately moved to the neutral, dependency-free package
 * `domain.scanfield` to break the previous `scan <-> data.network.llm` package
 * cycle. This test fails if that cycle is reintroduced.
 */
class PackageCycleTest {

    @Test
    fun `data,network,llm does not depend on scan`() {
        val llmDir = resolveSourceDir("com/adsamcik/starlitcoffee/data/network/llm")
        val offenders = llmDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .mapNotNull { file ->
                val badImports = file.readLines()
                    .map { it.trim() }
                    .filter { it.startsWith("import com.adsamcik.starlitcoffee.scan.") }
                if (badImports.isEmpty()) null else file.name to badImports
            }
            .toList()

        assertTrue(
            "data.network.llm must not import from scan.* (would recreate the " +
                "scan <-> llm package cycle). Offenders: $offenders",
            offenders.isEmpty(),
        )
    }

    private fun resolveSourceDir(packagePath: String): File {
        val candidates = listOf(
            File("src/main/java/$packagePath"),
            File("app/src/main/java/$packagePath"),
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: error("Could not locate source dir for $packagePath (cwd=${File(".").absolutePath})")
    }
}
