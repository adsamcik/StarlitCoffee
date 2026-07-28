package com.adsamcik.starlitcoffee.notification

import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrewSessionNotifierLifetimeTest {

    @Test
    fun `close unregisters observer cancels collection and clears session once`() {
        val collectionJob = SupervisorJob()
        var unregisterCalls = 0
        var cancelCalls = 0
        var clearCalls = 0
        val lifetime = BrewSessionNotifierLifetime(
            unregisterLifecycleObserver = { unregisterCalls += 1 },
            cancelCollectionScope = {
                cancelCalls += 1
                collectionJob.cancel()
            },
            onClosed = { clearCalls += 1 },
        )

        lifetime.close()
        lifetime.close()

        assertTrue(lifetime.isClosed)
        assertFalse(collectionJob.isActive)
        assertEquals(1, unregisterCalls)
        assertEquals(1, cancelCalls)
        assertEquals(1, clearCalls)
    }

    @Test
    fun `close still cancels collection and clears session if observer removal fails`() {
        val collectionJob = SupervisorJob()
        var clearCalls = 0
        val lifetime = BrewSessionNotifierLifetime(
            unregisterLifecycleObserver = { error("observer removal failed") },
            cancelCollectionScope = collectionJob::cancel,
            onClosed = { clearCalls += 1 },
        )

        val failure = runCatching(lifetime::close).exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(lifetime.isClosed)
        assertFalse(collectionJob.isActive)
        assertEquals(1, clearCalls)
    }
}
