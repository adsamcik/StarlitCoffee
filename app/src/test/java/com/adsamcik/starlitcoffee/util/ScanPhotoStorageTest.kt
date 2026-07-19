package com.adsamcik.starlitcoffee.util

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlinx.coroutines.test.runTest

class ScanPhotoStorageTest {
    private val noOpFileSync = FileSync { }
    private val noOpDirectorySync = DirectorySync { }

    @Test
    fun `capture staging fsyncs complete temp before atomic publication and parent directory`() {
        val stagingDir = temporaryDirectory()
        val calls = mutableListOf<String>()
        val bytes = "complete-photo".encodeToByteArray()
        try {
            val destination = ScanPhotoStorage.writeCaptureBytesToStorage(
                storageDir = stagingDir,
                fileName = "capture.jpg",
                bytes = bytes,
                fileSync = FileSync { temporary ->
                    calls += "file-sync"
                    assertArrayEquals(bytes, temporary.readBytes())
                    assertFalse(File(stagingDir, "capture.jpg").exists())
                },
                atomicReplace = { temporary, target ->
                    calls += "atomic-replace"
                    assertArrayEquals(bytes, temporary.readBytes())
                    assertFalse(target.exists())
                    Files.move(
                        temporary.toPath(),
                        target.toPath(),
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    )
                },
                directorySync = DirectorySync { directory ->
                    calls += "directory-sync"
                    assertEquals(stagingDir, directory)
                },
            )

            assertEquals(listOf("file-sync", "atomic-replace", "directory-sync"), calls)
            assertArrayEquals(bytes, destination.readBytes())
            assertTrue(stagingDir.listFiles()?.none { it.name.endsWith(".tmp") } == true)
        } finally {
            stagingDir.deleteRecursively()
        }
    }

    @Test
    fun `capture staging rejects lexical traversal destinations`() {
        val stagingDir = temporaryDirectory()
        val outside = File(stagingDir.parentFile, "${stagingDir.name}_outside.jpg")
        try {
            val result = runCatching {
                ScanPhotoStorage.writeCaptureBytesToStorage(
                    storageDir = stagingDir,
                    fileName = "..${File.separator}${outside.name}",
                    bytes = "photo".encodeToByteArray(),
                    fileSync = noOpFileSync,
                    directorySync = noOpDirectorySync,
                )
            }

            assertTrue(result.isFailure)
            assertFalse(outside.exists())
            assertTrue(stagingDir.listFiles().isNullOrEmpty())
        } finally {
            outside.delete()
            stagingDir.deleteRecursively()
        }
    }

    @Test
    fun `capture staging rejects symlink destination without changing its target`() {
        val stagingDir = temporaryDirectory()
        val outside = File(stagingDir.parentFile, "${stagingDir.name}_outside.jpg").apply {
            writeText("keep")
        }
        val link = File(stagingDir, "capture.jpg")
        try {
            val linkCreated = runCatching {
                Files.createSymbolicLink(link.toPath(), outside.toPath())
            }.isSuccess
            assumeTrue("Symbolic links are unavailable on this JVM", linkCreated)

            val result = runCatching {
                ScanPhotoStorage.writeCaptureBytesToStorage(
                    storageDir = stagingDir,
                    fileName = link.name,
                    bytes = "replace".encodeToByteArray(),
                    fileSync = noOpFileSync,
                    directorySync = noOpDirectorySync,
                )
            }

            assertTrue(result.isFailure)
            assertEquals("keep", outside.readText())
            assertTrue(Files.isSymbolicLink(link.toPath()))
        } finally {
            link.delete()
            outside.delete()
            stagingDir.deleteRecursively()
        }
    }

    @Test
    fun `capture file fsync failure never publishes destination`() {
        val stagingDir = temporaryDirectory()
        try {
            val result = runCatching {
                ScanPhotoStorage.writeCaptureBytesToStorage(
                    storageDir = stagingDir,
                    fileName = "capture.jpg",
                    bytes = "photo".encodeToByteArray(),
                    fileSync = FileSync { throw IOException("fsync failed") },
                    directorySync = noOpDirectorySync,
                )
            }

            assertTrue(result.isFailure)
            assertFalse(File(stagingDir, "capture.jpg").exists())
            assertTrue(stagingDir.listFiles().isNullOrEmpty())
        } finally {
            stagingDir.deleteRecursively()
        }
    }

    @Test
    fun `picker photos are copied to app staging before background work`() {
        val cacheDir = temporaryDirectory()
        try {
            val photos = mapOf(
                "content://picker/front" to "front-photo".encodeToByteArray(),
                "content://picker/back" to "back-photo".encodeToByteArray(),
            )

            val cachedFiles = ScanPhotoStorage.copyPhotoStreamsToCache(
                cacheDir = cacheDir,
                sourceUris = photos.keys.toList(),
                openInputStream = { source -> photos[source]?.let(::ByteArrayInputStream) },
                directorySync = noOpDirectorySync,
            )

            requireNotNull(cachedFiles)
            assertTrue(cachedFiles.all(File::isFile))
            assertArrayEquals(photos.getValue("content://picker/front"), cachedFiles[0].readBytes())
            assertArrayEquals(photos.getValue("content://picker/back"), cachedFiles[1].readBytes())
        } finally {
            cacheDir.deleteRecursively()
        }
    }

    @Test
    fun `failed picker import removes partial staging copies`() {
        val cacheDir = temporaryDirectory()
        try {
            val cachedFiles = ScanPhotoStorage.copyPhotoStreamsToCache(
                cacheDir = cacheDir,
                sourceUris = listOf("front", "missing-back"),
                openInputStream = { source ->
                    if (source == "front") ByteArrayInputStream("front-photo".encodeToByteArray()) else null
                },
                directorySync = noOpDirectorySync,
            )

            assertNull(cachedFiles)
            assertTrue(cacheDir.listFiles().isNullOrEmpty())
        } finally {
            cacheDir.deleteRecursively()
        }
    }

    @Test
    fun `failed permanent promotion removes partial copies`() {
        val permanentDir = temporaryDirectory()
        try {
            val permanentFiles = ScanPhotoStorage.copyPhotoStreamsToPermanentStorage(
                permanentDir = permanentDir,
                sourceUris = listOf("front", "missing-back"),
                openInputStream = { source ->
                    if (source == "front") ByteArrayInputStream("front-photo".encodeToByteArray()) else null
                },
                directorySync = noOpDirectorySync,
            )

            assertNull(permanentFiles)
            assertTrue(permanentDir.listFiles().isNullOrEmpty())
        } finally {
            permanentDir.deleteRecursively()
        }
    }

    @Test
    fun `oversized picker photo is rejected and partial file is removed`() {
        val cacheDir = temporaryDirectory()
        try {
            val cachedFiles = ScanPhotoStorage.copyPhotoStreamsToCache(
                cacheDir = cacheDir,
                sourceUris = listOf("oversized"),
                openInputStream = { RepeatingInputStream(33 * 1024 * 1024) },
                directorySync = noOpDirectorySync,
            )

            assertNull(cachedFiles)
            assertTrue(cacheDir.listFiles().isNullOrEmpty())
        } finally {
            cacheDir.deleteRecursively()
        }
    }

    @Test
    fun `expired staged captures are removed without touching active captures`() {
        val stagingDir = temporaryDirectory()
        try {
            val expired = File(stagingDir, "expired.jpg").apply {
                writeText("old")
                setLastModified(1_000L)
            }
            val active = File(stagingDir, "active.jpg").apply {
                writeText("new")
                setLastModified(3_000L)
            }

            ScanPhotoStorage.deleteStagedCapturesOlderThan(stagingDir, cutoffMillis = 2_000L)

            assertFalse(expired.exists())
            assertTrue(active.exists())
        } finally {
            stagingDir.deleteRecursively()
        }
    }

    @Test
    fun `staged deletion rejects files outside the owned root`() {
        val stagingDir = temporaryDirectory()
        val outside = File(stagingDir.parentFile, "${stagingDir.name}_outside.jpg").apply {
            writeText("keep")
        }
        try {
            assertFalse(
                ScanPhotoStorage.deleteOwnedPhoto(
                    ownedDirectory = stagingDir,
                    uriString = outside.toURI().toString(),
                ),
            )
            assertEquals("keep", outside.readText())
        } finally {
            outside.delete()
            stagingDir.deleteRecursively()
        }
    }

    @Test
    fun `staged deletion never deletes content URI imports`() {
        val stagingDir = temporaryDirectory()
        try {
            assertFalse(
                ScanPhotoStorage.deleteOwnedPhoto(
                    ownedDirectory = stagingDir,
                    uriString = "content://picker/photo",
                ),
            )
        } finally {
            stagingDir.deleteRecursively()
        }
    }

    @Test
    fun `staged deletion rejects traversal aliases`() {
        val stagingDir = temporaryDirectory()
        val outside = File(stagingDir.parentFile, "${stagingDir.name}_traversal.jpg").apply {
            writeText("keep")
        }
        val traversal = File(stagingDir, "..${File.separator}${outside.name}")
        try {
            assertTrue(traversal.toURI().rawPath.contains(".."))
            assertFalse(
                ScanPhotoStorage.deleteOwnedPhoto(
                    ownedDirectory = stagingDir,
                    uriString = traversal.toURI().toString(),
                ),
            )
            assertEquals("keep", outside.readText())
        } finally {
            outside.delete()
            stagingDir.deleteRecursively()
        }
    }

    @Test
    fun `staged deletion rejects symbolic links`() {
        val stagingDir = temporaryDirectory()
        val outside = File(stagingDir.parentFile, "${stagingDir.name}_symlink.jpg").apply {
            writeText("keep")
        }
        val link = File(stagingDir, "capture_link.jpg")
        try {
            val linkCreated = runCatching {
                Files.createSymbolicLink(link.toPath(), outside.toPath())
            }.isSuccess
            assumeTrue("Symbolic links are unavailable on this JVM", linkCreated)

            assertFalse(
                ScanPhotoStorage.deleteOwnedPhoto(
                    ownedDirectory = stagingDir,
                    uriString = link.toURI().toString(),
                ),
            )
            assertEquals("keep", outside.readText())
            assertTrue(link.exists())
        } finally {
            link.delete()
            outside.delete()
            stagingDir.deleteRecursively()
        }
    }

    @Test
    fun `failed durable replacement preserves previous destination`() {
        val directory = temporaryDirectory()
        val destination = File(directory, "bag_existing.webp").apply { writeText("old") }
        val temporary = File(directory, ".bag_existing.webp.tmp").apply { writeText("new") }
        try {
            val result = runCatching {
                ScanPhotoStorage.replaceFileDurably(
                    temporary = temporary,
                    destination = destination,
                    fileSync = noOpFileSync,
                    atomicReplace = { _, _ ->
                        throw AtomicMoveNotSupportedException(
                            temporary.path,
                            destination.path,
                            "not supported",
                        )
                    },
                    directorySync = noOpDirectorySync,
                )
            }

            assertTrue(result.isFailure)
            assertEquals("old", destination.readText())
            assertEquals("new", temporary.readText())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `interrupted fallback replacement restores previous destination`() {
        val directory = temporaryDirectory()
        val destination = File(directory, "bag_existing.webp")
        val backup = File(directory, ".bag_existing.webp.backup").apply { writeText("old") }
        try {
            ScanPhotoStorage.recoverInterruptedReplacements(directory, noOpDirectorySync)

            assertEquals("old", destination.readText())
            assertFalse(backup.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `expired cleanup retains old captures protected by active state`() {
        val stagingDir = temporaryDirectory()
        try {
            val orphan = File(stagingDir, "orphan.jpg").apply {
                writeText("old")
                setLastModified(1_000L)
            }
            val protected = File(stagingDir, "active-work.jpg").apply {
                writeText("active")
                setLastModified(1_000L)
            }

            ScanPhotoStorage.deleteStagedCapturesOlderThan(
                stagingDir = stagingDir,
                cutoffMillis = 2_000L,
                protectedStagedUris = setOf(protected.toURI().toString()),
            )

            assertFalse(orphan.exists())
            assertTrue(protected.exists())
        } finally {
            stagingDir.deleteRecursively()
        }
    }

    @Test
    fun `deleted bag cleanup journal preserves retry payload across process death`() {
        val deletion = PendingBagPhotoDeletion(
            deletionId = "42",
            bagId = 42L,
            sessionId = "scan-session",
            photoUrisCsv = "file:///bag/front.webp,file:///bag/back.webp",
        )

        assertEquals(
            deletion,
            ScanPhotoStorage.decodePendingBagPhotoDeletion(
                ScanPhotoStorage.encodePendingBagPhotoDeletion(deletion),
            ),
        )
    }

    @Test
    fun `process death before bag delete commit protects every currently referenced photo`() {
        val deletion = PendingBagPhotoDeletion(
            deletionId = "42",
            bagId = 42L,
            sessionId = "old-session",
            photoUrisCsv = "file:///photos/front.webp,file:///photos/back.webp",
        )
        val current = BagPhotoOwnership(
            bagId = 42L,
            sessionId = "old-session",
            photoUrisCsv = "file:///photos/front.webp,file:///photos/back.webp",
        )

        val plan = ScanPhotoStorage.cleanupPlan(deletion, current)

        assertNull(plan.sessionId)
        assertNull(plan.photoUrisCsv)
        assertEquals(
            setOf("file:///photos/front.webp", "file:///photos/back.webp"),
            plan.protectedPhotoUris,
        )
    }

    @Test
    fun `process death after bag delete commit cleans every journaled photo`() {
        val deletion = PendingBagPhotoDeletion(
            deletionId = "42",
            bagId = 42L,
            sessionId = "old-session",
            photoUrisCsv = "file:///photos/front.webp,file:///photos/back.webp",
        )

        val plan = ScanPhotoStorage.cleanupPlan(deletion, currentOwnership = null)

        assertEquals("old-session", plan.sessionId)
        assertEquals(
            "file:///photos/front.webp,file:///photos/back.webp",
            plan.photoUrisCsv,
        )
        assertTrue(plan.protectedPhotoUris.isEmpty())
    }

    @Test
    fun `committed photo replacement cleans only old unreferenced ownership`() {
        val deletion = PendingBagPhotoDeletion(
            deletionId = "replace:42:new-session",
            bagId = 42L,
            sessionId = "old-session",
            photoUrisCsv = "file:///photos/old-front.webp,file:///photos/shared.webp",
        )
        val current = BagPhotoOwnership(
            bagId = 42L,
            sessionId = "new-session",
            photoUrisCsv = "file:///photos/new-front.webp,file:///photos/shared.webp",
        )

        val plan = ScanPhotoStorage.cleanupPlan(deletion, current)

        assertEquals("old-session", plan.sessionId)
        assertEquals("file:///photos/old-front.webp", plan.photoUrisCsv)
        assertEquals(
            setOf("file:///photos/new-front.webp", "file:///photos/shared.webp"),
            plan.protectedPhotoUris,
        )
    }

    @Test
    fun `uncommitted photo replacement keeps old files still owned by the bag`() {
        val deletion = PendingBagPhotoDeletion(
            deletionId = "replace:42:new-session",
            bagId = 42L,
            sessionId = "old-session",
            photoUrisCsv = "file:///photos/old-front.webp,file:///photos/old-back.webp",
        )
        val current = BagPhotoOwnership(
            bagId = 42L,
            sessionId = "old-session",
            photoUrisCsv = "file:///photos/old-front.webp,file:///photos/old-back.webp",
        )

        val plan = ScanPhotoStorage.cleanupPlan(deletion, current)

        assertNull(plan.sessionId)
        assertNull(plan.photoUrisCsv)
    }

    @Test
    fun `mismatched Room ownership cannot clear a deletion journal`() {
        val deletion = PendingBagPhotoDeletion(
            deletionId = "42",
            bagId = 42L,
            sessionId = "old-session",
            photoUrisCsv = "file:///photos/old-front.webp",
        )
        val unexpectedOwner = BagPhotoOwnership(
            bagId = 7L,
            sessionId = "other-session",
            photoUrisCsv = "file:///photos/other-front.webp",
        )

        val plan = ScanPhotoStorage.cleanupPlan(deletion, unexpectedOwner)

        assertFalse(plan.canClearJournal)
    }

    @Test
    fun `permanent session deletion fsyncs bag photo directory`() {
        val directory = temporaryDirectory()
        val sessionId = "durable-delete"
        val storageKey = ScanPhotoStorage.storageKeyForSession(sessionId)
        val photo = File(directory, ScanPhotoStorage.permanentPhotoFileName(storageKey, 0))
            .apply { writeText("photo") }
        var syncedDirectory: File? = null
        try {
            val deleted = ScanPhotoStorage.deletePermanentPhotosForSession(
                permanentDir = directory,
                sessionId = sessionId,
                directorySync = DirectorySync { syncedDirectory = it },
            )

            assertTrue(deleted)
            assertFalse(photo.exists())
            assertEquals(directory, syncedDirectory)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `permanent deletion reports unlink failure and still syncs directory`() {
        val directory = temporaryDirectory()
        val sessionId = "unlink-failure"
        val storageKey = ScanPhotoStorage.storageKeyForSession(sessionId)
        val photo = File(directory, ScanPhotoStorage.permanentPhotoFileName(storageKey, 0))
            .apply { writeText("photo") }
        var syncCalled = false
        try {
            val deleted = ScanPhotoStorage.deletePermanentPhotosForSession(
                permanentDir = directory,
                sessionId = sessionId,
                directorySync = DirectorySync { syncCalled = true },
                deleteFile = { false },
            )

            assertFalse(deleted)
            assertTrue(photo.exists())
            assertTrue(syncCalled)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `pending deletion retry fsyncs directory even when files are already absent`() {
        val directory = temporaryDirectory()
        var syncCalls = 0
        try {
            val deleted = ScanPhotoStorage.deletePermanentPhotosForSession(
                permanentDir = directory,
                sessionId = "already-unlinked",
                directorySync = DirectorySync { syncCalls++ },
            )

            assertTrue(deleted)
            assertEquals(1, syncCalls)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `permanent deletion reports directory fsync failure`() {
        val directory = temporaryDirectory()
        val sessionId = "fsync-failure"
        val storageKey = ScanPhotoStorage.storageKeyForSession(sessionId)
        val photo = File(directory, ScanPhotoStorage.permanentPhotoFileName(storageKey, 0))
            .apply { writeText("photo") }
        try {
            val deleted = ScanPhotoStorage.deletePermanentPhotosForSession(
                permanentDir = directory,
                sessionId = sessionId,
                directorySync = DirectorySync { throw IOException("fsync failed") },
            )

            assertFalse(deleted)
            assertFalse(photo.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `explicit permanent photo deletion also fsyncs directory`() {
        val directory = temporaryDirectory()
        val photo = File(directory, "bag_explicit.webp").apply { writeText("photo") }
        var syncCalls = 0
        try {
            val deleted = ScanPhotoStorage.deletePermanentBagPhotos(
                permanentDir = directory,
                sessionId = null,
                photoUrisCsv = photo.toURI().toString(),
                directorySync = DirectorySync { syncCalls++ },
            )

            assertTrue(deleted)
            assertFalse(photo.exists())
            assertEquals(1, syncCalls)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `durable replacement keeps old destination until atomic replace and syncs metadata`() {
        val directory = temporaryDirectory()
        val destination = File(directory, "bag_existing.webp").apply { writeText("old") }
        val temporary = File(directory, ".bag_existing.webp.tmp").apply { writeText("new") }
        val calls = mutableListOf<String>()
        try {
            ScanPhotoStorage.replaceFileDurably(
                temporary = temporary,
                destination = destination,
                fileSync = FileSync { file ->
                    calls += "file-sync"
                    assertEquals(temporary, file)
                    assertEquals("old", destination.readText())
                },
                atomicReplace = { source, target ->
                    calls += "replace"
                    assertTrue("destination must exist until replacement", target.exists())
                    assertEquals("old", target.readText())
                    Files.move(
                        source.toPath(),
                        target.toPath(),
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    )
                },
                directorySync = DirectorySync {
                    calls += "directory-sync"
                    assertEquals("new", destination.readText())
                },
            )

            assertEquals(
                listOf("file-sync", "replace", "directory-sync", "directory-sync"),
                calls,
            )
            assertEquals("new", destination.readText())
            assertFalse(File(directory, ".bag_existing.webp.backup").exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `temporary fsync failure preserves old destination without attempting replacement`() {
        val directory = temporaryDirectory()
        val destination = File(directory, "bag_existing.webp").apply { writeText("old") }
        val temporary = File(directory, ".bag_existing.webp.tmp").apply { writeText("new") }
        var replacementCalled = false
        var directorySyncCalled = false
        try {
            val result = runCatching {
                ScanPhotoStorage.replaceFileDurably(
                    temporary = temporary,
                    destination = destination,
                    fileSync = FileSync { throw IOException("file fsync failed") },
                    atomicReplace = { _, _ -> replacementCalled = true },
                    directorySync = DirectorySync { directorySyncCalled = true },
                )
            }

            assertTrue(result.isFailure)
            assertEquals("old", destination.readText())
            assertEquals("new", temporary.readText())
            assertFalse(replacementCalled)
            assertFalse(directorySyncCalled)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `directory fsync failure atomically restores old destination and preserves new temporary`() {
        val directory = temporaryDirectory()
        val destination = File(directory, "bag_existing.webp").apply { writeText("old") }
        val temporary = File(directory, ".bag_existing.webp.tmp").apply { writeText("new") }
        var syncCalls = 0
        try {
            val result = runCatching {
                ScanPhotoStorage.replaceFileDurably(
                    temporary = temporary,
                    destination = destination,
                    directorySync = DirectorySync {
                        syncCalls++
                        if (syncCalls == 1) throw IOException("directory fsync failed")
                    },
                )
            }

            assertTrue(result.isFailure)
            assertEquals("old", destination.readText())
            assertEquals("new", temporary.readText())
            assertEquals(2, syncCalls)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `post commit photo cleanup failure still reports deletion success and keeps journal`() = runTest {
        var pending = false
        var rowDeleted = false

        val result = commitDeletionWithDeferredCleanup(
            markCleanupPending = { pending = true },
            deleteRecord = { rowDeleted = true },
            cleanupPhotos = { false },
            clearCleanupPending = { pending = false },
        )

        assertTrue(result)
        assertTrue(rowDeleted)
        assertTrue("cleanup journal must remain for startup retry", pending)
    }

    @Test
    fun `database deletion failure clears journal only after ownership reconciliation`() = runTest {
        var pending = false
        var cleanupCalled = false

        try {
            commitDeletionWithDeferredCleanup(
                markCleanupPending = { pending = true },
                deleteRecord = { error("database failure") },
                cleanupPhotos = {
                    cleanupCalled = true
                    true
                },
                clearCleanupPending = { pending = false },
            )
        } catch (_: IllegalStateException) {
            // Expected.
        }

        assertFalse(pending)
        assertTrue(cleanupCalled)
    }

    @Test
    fun `database deletion and reconciliation failure keeps cleanup journal`() = runTest {
        var pending = false

        try {
            commitDeletionWithDeferredCleanup(
                markCleanupPending = { pending = true },
                deleteRecord = { error("database failure") },
                cleanupPhotos = { false },
                clearCleanupPending = { pending = false },
            )
        } catch (_: IllegalStateException) {
            // Expected.
        }

        assertTrue(pending)
    }

    private fun temporaryDirectory(): File =
        File.createTempFile("scan-photo-storage", "").apply {
            check(delete() && mkdirs())
        }

    private class RepeatingInputStream(
        private var remaining: Int,
    ) : InputStream() {
        override fun read(): Int = if (remaining-- > 0) 0 else -1

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (remaining <= 0) return -1
            val count = minOf(remaining, length)
            buffer.fill(0, offset, offset + count)
            remaining -= count
            return count
        }
    }
}
