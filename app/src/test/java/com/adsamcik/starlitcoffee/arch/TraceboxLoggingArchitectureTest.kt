package com.adsamcik.starlitcoffee.arch

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class TraceboxLoggingArchitectureTest {
    @Test
    fun `production code does not bypass Tracebox with Android logging`() {
        val sourceRoot = listOf(File("src/main/java"), File("app/src/main/java"))
            .firstOrNull(File::isDirectory)
            ?: error("Could not locate src/main/java (cwd=${File(".").absolutePath})")
        val violations = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension in setOf("kt", "java") }
            .flatMap { file ->
                file.readLines().asSequence().mapIndexedNotNull { index, line ->
                    val bypassesTracebox = "android.util.Log" in line ||
                        ANDROID_LOG_CALL.containsMatchIn(line)
                    if (bypassesTracebox) "${file.relativeTo(sourceRoot)}:${index + 1}" else null
                }
            }
            .toList()

        assertTrue(
            "Production logging must use Tracebox directly: ${violations.joinToString()}",
            violations.isEmpty(),
        )
    }

    private companion object {
        val ANDROID_LOG_CALL = Regex("""\bLog\.[vdiew]\s*\(""")
    }
}
