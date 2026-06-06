package com.adsamcik.starlitcoffee.test.corpus

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Regression guard: the repo must contain no paths or links to the retired
 * real-photo datasets. The synthetic WebP corpus is the only committed bag
 * dataset, so any reappearance of the old real-photo paths is a mistake.
 *
 * Forbidden patterns (real-photo paths/links only — NOT brand-name strings,
 * which legitimately appear as OCR test fixtures, and NOT the device push
 * target `/data/local/tmp/coffee-bags` or the synthetic
 * `testdata/synthetic-coffee-bag-corpus`):
 *  - `testdata/coffee-bags`        (retired real-photo symlink dir)
 *  - `coffee-bags-corpus`          (retired real-photo corpus dir)
 *  - `/sdcard/Download/coffee-bags` (retired on-device real-photo path)
 *  - `My files/Coffee bags`        (the original author's local real-photo path)
 */
class NoRealPhotoReferencesTest {

    private val forbidden = listOf(
        Regex("""testdata[\\/]coffee-bags"""),
        Regex("""coffee-bags-corpus"""),
        Regex("""/sdcard/Download/coffee-bags"""),
        Regex("""My files[\\/]Coffee bags"""),
    )

    private val scanRoots = listOf("app/src", "docs", ".github", "testdata", "tools", "gradle")
    private val rootFiles = listOf(".gitignore", "settings.gradle.kts", "build.gradle.kts", "app/build.gradle.kts")
    private val textExtensions =
        setOf("kt", "kts", "java", "md", "json", "gradle", "py", "xml", "txt", "pro", "properties", "yml", "yaml")
    private val pruneDirs =
        setOf("build", ".git", ".gradle", ".idea", ".kotlin", "eval", "prototypes", "node_modules")

    @Test
    fun `repo contains no references to retired real-photo datasets`() {
        val root = resolveRepoRoot()
        assumeTrue("Repo root not locatable from ${System.getProperty("user.dir")}", root != null)

        val hits = mutableListOf<String>()
        val files = buildList {
            scanRoots.forEach { collectTextFiles(File(root, it), this) }
            rootFiles.forEach { rel -> File(root, rel).takeIf { it.isFile }?.let { add(it) } }
        }

        for (file in files) {
            if (file.name == SELF_FILE_NAME) continue
            file.readText().lineSequence().forEachIndexed { index, line ->
                if (forbidden.any { it.containsMatchIn(line) }) {
                    hits.add("${file.relativeTo(root!!).path}:${index + 1}: ${line.trim()}")
                }
            }
        }

        assertTrue(
            "Found references to retired real-photo datasets — repoint to the synthetic " +
                "corpus:\n${hits.joinToString("\n")}",
            hits.isEmpty(),
        )
    }

    private fun collectTextFiles(dir: File, out: MutableList<File>) {
        if (!dir.isDirectory) return
        dir.walkTopDown()
            .onEnter { it.name !in pruneDirs }
            .filter { it.isFile }
            .filter { it.extension.lowercase() in textExtensions || it.name == ".gitignore" }
            .forEach { out.add(it) }
    }

    private fun resolveRepoRoot(): File? {
        var current: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(MAX_UPWARD_SEARCH) {
            if (current != null && File(current, "settings.gradle.kts").isFile) return current
            current = current?.parentFile
        }
        return null
    }

    private companion object {
        const val SELF_FILE_NAME = "NoRealPhotoReferencesTest.kt"
        const val MAX_UPWARD_SEARCH = 6
    }
}
