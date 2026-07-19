package com.adsamcik.starlitcoffee.data.work

import com.adsamcik.starlitcoffee.util.DirectorySync
import com.adsamcik.starlitcoffee.util.FileSync
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class BagExtractionResultStoreTest {
    private val noOpFileSync = FileSync { }
    private val noOpDirectorySync = DirectorySync { }

    @Test
    fun `stored result preserves payloads larger than WorkManager data`() {
        val directory = Files.createTempDirectory("bag-results").toFile()
        val workId = UUID.randomUUID().toString()
        val resultJson = "Výsledek ☕ ".repeat(2_000)
        val reviewContext = BagReviewContext.rescan(42L)

        try {
            BagExtractionResultStore.write(
                directory = directory,
                workId = workId,
                successful = true,
                resultJson = resultJson,
                reviewContext = reviewContext,
                nowMillis = 42L,
                fileSync = noOpFileSync,
                directorySync = noOpDirectorySync,
            )

            val stored = requireNotNull(BagExtractionResultStore.read(directory, workId))
            assertTrue(resultJson.toByteArray().size > 10_000)
            assertEquals(resultJson, stored.resultJson)
            assertEquals(true, stored.successful)
            assertEquals(42L, stored.createdAtMillis)
            assertEquals(reviewContext, stored.reviewContext)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `legacy stored result without review context keeps add new fallback`() {
        val directory = Files.createTempDirectory("bag-results-legacy").toFile()
        val workId = UUID.randomUUID().toString()
        try {
            directory.mkdirs()
            java.io.File(directory, "result_$workId.json").writeText(
                """{"successful":true,"resultJson":"{}","createdAtMillis":42}""",
            )

            assertEquals(null, BagExtractionResultStore.read(directory, workId)?.reviewContext)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `write if absent preserves successful terminal result during failed re-entry`() {
        val directory = Files.createTempDirectory("bag-results-success-reentry").toFile()
        val workId = UUID.randomUUID().toString()
        try {
            BagExtractionResultStore.write(
                directory = directory,
                workId = workId,
                successful = true,
                resultJson = """{"first":"success"}""",
                nowMillis = 1L,
                fileSync = noOpFileSync,
                directorySync = noOpDirectorySync,
            )

            val retained = BagExtractionResultStore.writeIfAbsent(
                directory = directory,
                workId = workId,
                successful = false,
                resultJson = """{"second":"failure"}""",
                nowMillis = 2L,
                fileSync = noOpFileSync,
                directorySync = noOpDirectorySync,
            )

            assertTrue(retained.successful)
            assertEquals("""{"first":"success"}""", retained.resultJson)
            assertEquals(1L, retained.createdAtMillis)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `write if absent preserves failed terminal result during successful re-entry`() {
        val directory = Files.createTempDirectory("bag-results-failure-reentry").toFile()
        val workId = UUID.randomUUID().toString()
        try {
            BagExtractionResultStore.write(
                directory = directory,
                workId = workId,
                successful = false,
                resultJson = """{"first":"failure"}""",
                nowMillis = 1L,
                fileSync = noOpFileSync,
                directorySync = noOpDirectorySync,
            )

            val retained = BagExtractionResultStore.writeIfAbsent(
                directory = directory,
                workId = workId,
                successful = true,
                resultJson = """{"second":"success"}""",
                nowMillis = 2L,
                fileSync = noOpFileSync,
                directorySync = noOpDirectorySync,
            )

            assertFalse(retained.successful)
            assertEquals("""{"first":"failure"}""", retained.resultJson)
            assertEquals(1L, retained.createdAtMillis)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `expiry retains results still owned by pending review`() {
        val directory = Files.createTempDirectory("bag-results-protected").toFile()
        val protectedWorkId = UUID.randomUUID().toString()
        val orphanWorkId = UUID.randomUUID().toString()
        try {
            BagExtractionResultStore.write(
                directory = directory,
                workId = protectedWorkId,
                successful = true,
                resultJson = "{}",
                nowMillis = 1L,
                fileSync = noOpFileSync,
                directorySync = noOpDirectorySync,
            )
            BagExtractionResultStore.write(
                directory = directory,
                workId = orphanWorkId,
                successful = true,
                resultJson = "{}",
                nowMillis = 1L,
                fileSync = noOpFileSync,
                directorySync = noOpDirectorySync,
            )
            directory.listFiles()?.forEach { it.setLastModified(1L) }

            BagExtractionResultStore.deleteExpired(
                directory = directory,
                protectedWorkIds = setOf(protectedWorkId),
                nowMillis = 8L * 24L * 60L * 60L * 1_000L,
                directorySync = noOpDirectorySync,
            )

            assertTrue(BagExtractionResultStore.read(directory, protectedWorkId) != null)
            assertTrue(BagExtractionResultStore.read(directory, orphanWorkId) == null)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `expired result ids are reported for coordinated lifecycle cleanup`() {
        val directory = Files.createTempDirectory("bag-results-expired-ids").toFile()
        val expiredWorkId = UUID.randomUUID().toString()
        val freshWorkId = UUID.randomUUID().toString()
        try {
            BagExtractionResultStore.write(
                directory = directory,
                workId = expiredWorkId,
                successful = true,
                resultJson = "{}",
                nowMillis = 1L,
                fileSync = noOpFileSync,
                directorySync = noOpDirectorySync,
            )
            BagExtractionResultStore.write(
                directory = directory,
                workId = freshWorkId,
                successful = true,
                resultJson = "{}",
                nowMillis = 8L * 24L * 60L * 60L * 1_000L,
                fileSync = noOpFileSync,
                directorySync = noOpDirectorySync,
            )

            assertEquals(
                setOf(expiredWorkId),
                BagExtractionResultStore.expiredWorkIds(
                    directory = directory,
                    nowMillis = 8L * 24L * 60L * 60L * 1_000L,
                ),
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `result symlink cannot read or delete another active result`() {
        val directory = Files.createTempDirectory("bag-results-symlink").toFile()
        val activeWorkId = UUID.randomUUID().toString()
        val aliasWorkId = UUID.randomUUID().toString()
        BagExtractionResultStore.write(
            directory = directory,
            workId = activeWorkId,
            successful = true,
            resultJson = """{"active":true}""",
            nowMillis = 42L,
            fileSync = noOpFileSync,
            directorySync = noOpDirectorySync,
        )
        val active = requireNotNull(directory.listFiles()?.single())
        val alias = java.io.File(directory, "result_$aliasWorkId.json")
        try {
            val linkCreated = runCatching {
                Files.createSymbolicLink(alias.toPath(), active.toPath())
            }.isSuccess
            assumeTrue("Symbolic links are unavailable on this JVM", linkCreated)

            assertTrue(BagExtractionResultStore.read(directory, aliasWorkId) == null)
            assertFalse(
                BagExtractionResultStore.delete(
                    directory,
                    aliasWorkId,
                    noOpDirectorySync,
                ),
            )
            assertTrue(active.exists())
            assertEquals(
                """{"active":true}""",
                requireNotNull(BagExtractionResultStore.read(directory, activeWorkId)).resultJson,
            )
        } finally {
            alias.delete()
            active.delete()
            directory.deleteRecursively()
        }
    }

    @Test
    fun `result write fsyncs contents before atomic rename and directory afterward`() {
        val directory = Files.createTempDirectory("bag-results-durable-write").toFile()
        val workId = UUID.randomUUID().toString()
        val destination = java.io.File(directory, "result_$workId.json")
        val calls = mutableListOf<String>()
        try {
            BagExtractionResultStore.write(
                directory = directory,
                workId = workId,
                successful = true,
                resultJson = """{"durable":true}""",
                nowMillis = 42L,
                fileSync = FileSync { temporary ->
                    calls += "file-sync"
                    assertFalse(destination.exists())
                    assertTrue(temporary.readText().contains("""\"durable\":true"""))
                },
                atomicReplace = { temporary, target ->
                    calls += "rename"
                    assertFalse(target.exists())
                    Files.move(
                        temporary.toPath(),
                        target.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                },
                directorySync = DirectorySync { syncedDirectory ->
                    calls += "directory-sync"
                    assertEquals(directory, syncedDirectory)
                    assertTrue(destination.isFile)
                },
            )

            assertEquals(listOf("file-sync", "rename", "directory-sync"), calls)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `result file fsync failure preserves previous durable result`() {
        val directory = Files.createTempDirectory("bag-results-fsync-failure").toFile()
        val workId = UUID.randomUUID().toString()
        BagExtractionResultStore.write(
            directory = directory,
            workId = workId,
            successful = true,
            resultJson = """{"old":true}""",
            nowMillis = 1L,
            fileSync = noOpFileSync,
            directorySync = noOpDirectorySync,
        )
        var replacementCalled = false
        try {
            val result = runCatching {
                BagExtractionResultStore.write(
                    directory = directory,
                    workId = workId,
                    successful = false,
                    resultJson = """{"new":true}""",
                    nowMillis = 2L,
                    fileSync = FileSync { throw IOException("file fsync failed") },
                    directorySync = noOpDirectorySync,
                    atomicReplace = { _, _ -> replacementCalled = true },
                )
            }

            assertTrue(result.isFailure)
            assertFalse(replacementCalled)
            assertEquals(
                """{"old":true}""",
                requireNotNull(BagExtractionResultStore.read(directory, workId)).resultJson,
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `result directory fsync failure propagates after atomic rename`() {
        val directory = Files.createTempDirectory("bag-results-directory-fsync").toFile()
        val workId = UUID.randomUUID().toString()
        try {
            val result = runCatching {
                BagExtractionResultStore.write(
                    directory = directory,
                    workId = workId,
                    successful = true,
                    resultJson = """{"new":true}""",
                    nowMillis = 42L,
                    fileSync = noOpFileSync,
                    directorySync = DirectorySync {
                        throw IOException("directory fsync failed")
                    },
                )
            }

            assertTrue(result.isFailure)
            assertEquals(
                """{"new":true}""",
                requireNotNull(BagExtractionResultStore.read(directory, workId)).resultJson,
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `result delete and expiry fsync directory metadata`() {
        val directory = Files.createTempDirectory("bag-results-delete-sync").toFile()
        val deletedWorkId = UUID.randomUUID().toString()
        val expiredWorkId = UUID.randomUUID().toString()
        BagExtractionResultStore.write(
            directory = directory,
            workId = deletedWorkId,
            successful = true,
            resultJson = "{}",
            nowMillis = 1L,
            fileSync = noOpFileSync,
            directorySync = noOpDirectorySync,
        )
        BagExtractionResultStore.write(
            directory = directory,
            workId = expiredWorkId,
            successful = true,
            resultJson = "{}",
            nowMillis = 1L,
            fileSync = noOpFileSync,
            directorySync = noOpDirectorySync,
        )
        directory.listFiles()
            ?.single { it.name == "result_$expiredWorkId.json" }
            ?.setLastModified(1L)
        var syncCalls = 0
        try {
            assertTrue(
                BagExtractionResultStore.delete(
                    directory = directory,
                    workId = deletedWorkId,
                    directorySync = DirectorySync { syncCalls++ },
                ),
            )
            BagExtractionResultStore.deleteExpired(
                directory = directory,
                protectedWorkIds = emptySet(),
                nowMillis = 8L * 24L * 60L * 60L * 1_000L,
                directorySync = DirectorySync { syncCalls++ },
            )

            assertEquals(2, syncCalls)
            assertTrue(BagExtractionResultStore.read(directory, deletedWorkId) == null)
            assertTrue(BagExtractionResultStore.read(directory, expiredWorkId) == null)
        } finally {
            directory.deleteRecursively()
        }
    }
}
