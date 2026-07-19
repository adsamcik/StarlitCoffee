package com.adsamcik.starlitcoffee.data.work

import com.adsamcik.starlitcoffee.util.DirectorySync
import com.adsamcik.starlitcoffee.util.FileSync
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class BagExtractionInputStoreTest {
    private val noOpFileSync = FileSync { }
    private val noOpDirectorySync = DirectorySync { }

    @Test
    fun `oversized multilingual input round trips outside WorkManager data`() {
        val directory = Files.createTempDirectory("bag-extraction-input").toFile()
        val multilingual = "káva-コーヒー-قهوة-".repeat(200)
        val photoUris = List(100) { index -> "file:///capture/$index/$multilingual.jpg" }
        val input = BagExtractionInput(
            photoUrisCsv = photoUris.joinToString(","),
            knownValuesJson = """{"names":["$multilingual"],"regions":["$multilingual"]}""",
            runLlm = false,
            sessionId = "session-large",
            generationId = "generation-large",
            reviewContext = BagReviewContext.rescan(42L),
            notifyOnCompletion = true,
            createdAtMillis = 42L,
        )

        try {
            val manifestPath = BagExtractionInputStore.writeToDirectory(
                directory = directory,
                input = input,
                token = "large",
                fileSync = noOpFileSync,
                directorySync = noOpDirectorySync,
            )
            val manifest = java.io.File(manifestPath)
            val workData = BagExtractionScheduler.buildInputData(
                manifestPath = manifestPath,
                runLlm = input.runLlm,
                sessionId = input.sessionId,
                generationId = input.generationId,
                notifyOnCompletion = input.notifyOnCompletion,
                reviewContext = input.reviewContext,
            )

            assertTrue(manifest.length() > 10_240L)
            assertEquals(input, BagExtractionInputStore.readFromDirectory(directory, manifestPath))
            assertEquals(manifestPath, workData.getString(BagExtractionWorker.KEY_INPUT_MANIFEST))
            assertEquals("session-large", workData.getString(BagExtractionWorker.KEY_SESSION_ID))
            assertEquals(
                input.reviewContext,
                decodeBagReviewContext(
                    workData.getString(BagExtractionWorker.KEY_REVIEW_CONTEXT_JSON),
                ),
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `input manifest deletion removes the durable payload`() {
        val directory = Files.createTempDirectory("bag-extraction-input").toFile()
        try {
            val manifestPath = BagExtractionInputStore.writeToDirectory(
                directory = directory,
                input = BagExtractionInput("file:///front.jpg", "{}"),
                token = "delete",
                fileSync = noOpFileSync,
                directorySync = noOpDirectorySync,
            )

            BagExtractionInputStore.deleteFromDirectory(
                directory,
                manifestPath,
                noOpDirectorySync,
            )

            assertFalse(java.io.File(manifestPath).exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `manifest path can be durably referenced before its file is written`() {
        val directory = Files.createTempDirectory("bag-extraction-pending").toFile()
        try {
            val manifestPath = BagExtractionInputStore.allocatePath(directory, "pending")

            assertFalse(java.io.File(manifestPath).exists())

            BagExtractionInputStore.writeToPath(
                directory = directory,
                path = manifestPath,
                input = BagExtractionInput("file:///front.jpg", "{}"),
                fileSync = noOpFileSync,
                directorySync = noOpDirectorySync,
            )

            assertTrue(java.io.File(manifestPath).isFile)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `active manifests and pending results protect their staged photos`() {
        val protected = BagExtractionScheduler.collectProtectedStagedPhotoUris(
            inputs = listOf(
                BagExtractionInput(
                    photoUrisCsv = "file:///staging/active-front.jpg,file:///staging/active-back.jpg",
                    knownValuesJson = "{}",
                ),
            ),
            resultPhotoUris = listOf(
                "file:///staging/review-front.jpg",
                null,
            ),
        )

        assertEquals(
            setOf(
                "file:///staging/active-front.jpg",
                "file:///staging/active-back.jpg",
                "file:///staging/review-front.jpg",
            ),
            protected,
        )
    }

    @Test
    fun `expiry retains manifests still owned by active work`() {
        val directory = Files.createTempDirectory("bag-extraction-active").toFile()
        try {
            val manifestPath = BagExtractionInputStore.writeToDirectory(
                directory = directory,
                input = BagExtractionInput("file:///staging/active.jpg", "{}"),
                token = "active",
                fileSync = noOpFileSync,
                directorySync = noOpDirectorySync,
            )
            java.io.File(manifestPath).setLastModified(1L)

            BagExtractionInputStore.deleteExpiredFromDirectory(
                directory = directory,
                protectedPaths = setOf(manifestPath),
                cutoffMillis = 2L,
                directorySync = noOpDirectorySync,
            )

            assertTrue(java.io.File(manifestPath).exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `manifest symlink cannot read or delete another active manifest`() {
        val directory = Files.createTempDirectory("bag-extraction-symlink").toFile()
        val activePath = BagExtractionInputStore.writeToDirectory(
            directory = directory,
            input = BagExtractionInput("file:///active.jpg", """{"active":true}"""),
            token = "active",
            fileSync = noOpFileSync,
            directorySync = noOpDirectorySync,
        )
        val active = java.io.File(activePath)
        val alias = java.io.File(directory, "input_alias.json")
        try {
            val linkCreated = runCatching {
                Files.createSymbolicLink(alias.toPath(), active.toPath())
            }.isSuccess
            assumeTrue("Symbolic links are unavailable on this JVM", linkCreated)

            assertTrue(
                runCatching {
                    BagExtractionInputStore.readFromDirectory(directory, alias.absolutePath)
                }.isFailure,
            )
            assertTrue(
                runCatching {
                    BagExtractionInputStore.deleteFromDirectory(directory, alias.absolutePath)
                }.isFailure,
            )
            assertTrue(active.exists())
            assertEquals(
                "file:///active.jpg",
                BagExtractionInputStore.readFromDirectory(directory, activePath).photoUrisCsv,
            )
        } finally {
            alias.delete()
            active.delete()
            directory.deleteRecursively()
        }
    }

    @Test
    fun `manifest write fsyncs contents before atomic rename and directory afterward`() {
        val directory = Files.createTempDirectory("bag-extraction-durable-write").toFile()
        val destination = java.io.File(
            BagExtractionInputStore.allocatePath(directory, "durable"),
        )
        val calls = mutableListOf<String>()
        try {
            BagExtractionInputStore.writeToPath(
                directory = directory,
                path = destination.absolutePath,
                input = BagExtractionInput("file:///front.jpg", "{}"),
                fileSync = FileSync { temporary ->
                    calls += "file-sync"
                    assertFalse(destination.exists())
                    assertTrue(temporary.readText().contains("file:///front.jpg"))
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
    fun `manifest file fsync failure preserves previous durable input`() {
        val directory = Files.createTempDirectory("bag-extraction-fsync-failure").toFile()
        val destinationPath = BagExtractionInputStore.writeToDirectory(
            directory = directory,
            input = BagExtractionInput("file:///old.jpg", """{"old":true}"""),
            token = "existing",
            fileSync = noOpFileSync,
            directorySync = noOpDirectorySync,
        )
        var replacementCalled = false
        try {
            val result = runCatching {
                BagExtractionInputStore.writeToPath(
                    directory = directory,
                    path = destinationPath,
                    input = BagExtractionInput("file:///new.jpg", """{"new":true}"""),
                    fileSync = FileSync { throw IOException("file fsync failed") },
                    directorySync = noOpDirectorySync,
                    atomicReplace = { _, _ -> replacementCalled = true },
                )
            }

            assertTrue(result.isFailure)
            assertFalse(replacementCalled)
            assertEquals(
                "file:///old.jpg",
                BagExtractionInputStore.readFromDirectory(directory, destinationPath).photoUrisCsv,
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `manifest directory fsync failure propagates after atomic rename`() {
        val directory = Files.createTempDirectory("bag-extraction-directory-fsync").toFile()
        val destinationPath = BagExtractionInputStore.allocatePath(directory, "directory-failure")
        try {
            val result = runCatching {
                BagExtractionInputStore.writeToPath(
                    directory = directory,
                    path = destinationPath,
                    input = BagExtractionInput("file:///new.jpg", "{}"),
                    fileSync = noOpFileSync,
                    directorySync = DirectorySync {
                        throw IOException("directory fsync failed")
                    },
                )
            }

            assertTrue(result.isFailure)
            assertEquals(
                "file:///new.jpg",
                BagExtractionInputStore.readFromDirectory(directory, destinationPath).photoUrisCsv,
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `manifest delete and expiry fsync directory metadata`() {
        val directory = Files.createTempDirectory("bag-extraction-delete-sync").toFile()
        val deletedPath = BagExtractionInputStore.writeToDirectory(
            directory = directory,
            input = BagExtractionInput("file:///delete.jpg", "{}"),
            token = "delete-sync",
            fileSync = noOpFileSync,
            directorySync = noOpDirectorySync,
        )
        val expiredPath = BagExtractionInputStore.writeToDirectory(
            directory = directory,
            input = BagExtractionInput("file:///expired.jpg", "{}"),
            token = "expiry-sync",
            fileSync = noOpFileSync,
            directorySync = noOpDirectorySync,
        )
        java.io.File(expiredPath).setLastModified(1L)
        var syncCalls = 0
        try {
            BagExtractionInputStore.deleteFromDirectory(
                directory = directory,
                path = deletedPath,
                directorySync = DirectorySync { syncCalls++ },
            )
            BagExtractionInputStore.deleteExpiredFromDirectory(
                directory = directory,
                protectedPaths = emptySet(),
                cutoffMillis = 2L,
                directorySync = DirectorySync { syncCalls++ },
            )

            assertEquals(2, syncCalls)
            assertFalse(java.io.File(deletedPath).exists())
            assertFalse(java.io.File(expiredPath).exists())
        } finally {
            directory.deleteRecursively()
        }
    }
}
