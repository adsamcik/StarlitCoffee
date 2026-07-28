package com.adsamcik.starlitcoffee.data.work

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BagExtractionStartupRecoveryTest {

    @Test
    fun `startup owners share one in-flight and completed reconciliation`() = runTest {
        val releaseRecovery = CompletableDeferred<Unit>()
        var recoveryCalls = 0
        val expectedProtectedUris = setOf("file:///staged/front.jpg")
        val recovery = BagExtractionStartupRecovery(backgroundScope) {
            recoveryCalls += 1
            releaseRecovery.await()
            expectedProtectedUris
        }

        val applicationStartup = async { recovery.await() }
        val activeWorkRestore = async { recovery.await() }
        val stagedPhotoCleanup = async { recovery.await() }
        runCurrent()

        assertEquals(1, recoveryCalls)

        releaseRecovery.complete(Unit)
        assertEquals(expectedProtectedUris, applicationStartup.await())
        assertEquals(expectedProtectedUris, activeWorkRestore.await())
        assertEquals(expectedProtectedUris, stagedPhotoCleanup.await())
        assertEquals(expectedProtectedUris, recovery.await())
        assertEquals(1, recoveryCalls)
    }

    @Test
    fun `cancelling one consumer does not cancel application scoped recovery`() = runTest {
        val releaseRecovery = CompletableDeferred<Unit>()
        var recoveryCalls = 0
        val expectedProtectedUris = setOf("file:///staged/back.jpg")
        val recovery = BagExtractionStartupRecovery(backgroundScope) {
            recoveryCalls += 1
            releaseRecovery.await()
            expectedProtectedUris
        }
        val cancelledConsumer = async { recovery.await() }
        runCurrent()

        cancelledConsumer.cancelAndJoin()
        releaseRecovery.complete(Unit)

        assertEquals(expectedProtectedUris, recovery.await())
        assertEquals(1, recoveryCalls)
    }
}
