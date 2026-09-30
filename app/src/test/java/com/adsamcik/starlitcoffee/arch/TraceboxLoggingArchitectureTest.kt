package com.adsamcik.starlitcoffee.arch

import java.io.File
import dev.tracebox.api.LogTemplate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TraceboxLoggingArchitectureTest {
    @Test
    fun `production log templates are static and valid for the typed API`() {
        val sourceRoot = listOf(File("src/main/java"), File("app/src/main/java"))
            .firstOrNull(File::isDirectory)
            ?: error("Could not locate production sources")
        val factory = Regex("""\bLogTemplate\.of\s*\(""")
        val literal = Regex("""LogTemplate\.of\(\s*("(?:[^"\\]|\\.)*")\s*,?\s*\)""")
        sourceRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            val source = file.readText()
            val templates = literal.findAll(source).toList()
            assertEquals("Templates must use static literals in $file", factory.findAll(source).count(), templates.size)
            templates.forEach { match ->
                val value = Json.parseToJsonElement(match.groupValues[1]).jsonPrimitive.content
                assertTrue("Runtime values belong in arguments in $file", '$' !in value)
                LogTemplate.of(value)
            }
        }
    }

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
