package com.adsamcik.starlitcoffee.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MindlayerModelSetupTest {
    @Test
    fun `successful setup action does not open a second app`() = runTest {
        var sent = false
        val result = resolveMindlayerSetupLaunch(
            queryAction = { { sent = true } },
            openApp = { error("Unexpected fallback") },
        )
        assertTrue(sent)
        assertEquals(ModelSetupLaunchOutcome.OPENED_SETUP, result)
    }

    @Test
    fun `unsupported setup opens the installed app`() = runTest {
        assertEquals(
            ModelSetupLaunchOutcome.OPENED_APP,
            resolveMindlayerSetupLaunch(queryAction = { null }, openApp = { true }),
        )
    }

    @Test
    fun `expired action falls back to the installed app`() = runTest {
        var recorded = 0
        val result = resolveMindlayerSetupLaunch(
            queryAction = { { throw IllegalStateException("Expired action") } },
            openApp = { true },
            onFailure = { recorded++ },
        )
        assertEquals(ModelSetupLaunchOutcome.OPENED_APP, result)
        assertEquals(1, recorded)
    }

    @Test
    fun `missing service and app report unavailable`() = runTest {
        assertEquals(
            ModelSetupLaunchOutcome.UNAVAILABLE,
            resolveMindlayerSetupLaunch(queryAction = { null }, openApp = { false }),
        )
    }

    @Test
    fun `query errors and launch errors report failure`() = runTest {
        assertEquals(
            ModelSetupLaunchOutcome.FAILED,
            resolveMindlayerSetupLaunch(
                queryAction = { error("Service unavailable") },
                openApp = { false },
            ),
        )
        assertEquals(
            ModelSetupLaunchOutcome.FAILED,
            resolveMindlayerSetupLaunch(
                queryAction = { null },
                openApp = { error("Launch denied") },
            ),
        )
    }

    @Test
    fun `owned setup query timeout still tries the installed app`() = runTest {
        assertEquals(
            ModelSetupLaunchOutcome.OPENED_APP,
            resolveMindlayerSetupLaunch(
                queryAction = { awaitCancellation() },
                openApp = { true },
                timeoutMillis = 20,
            ),
        )
        assertEquals(20L, testScheduler.currentTime)
    }

    @Test
    fun `owned timeout without a fallback reports failure`() = runTest {
        assertEquals(
            ModelSetupLaunchOutcome.FAILED,
            resolveMindlayerSetupLaunch(
                queryAction = { awaitCancellation() },
                openApp = { false },
                timeoutMillis = 20,
            ),
        )
    }

    @Test
    fun `caller cancellation never launches fallback`() = runTest {
        var opened = false
        val job = launch {
            resolveMindlayerSetupLaunch(
                queryAction = { awaitCancellation() },
                openApp = { opened = true; true },
            )
        }
        runCurrent()
        job.cancelAndJoin()
        assertFalse(opened)
    }

    @Test
    fun `outer deadline and translated cancellation never launch fallback`() = runTest {
        var opened = false
        val result = CompletableDeferred<Throwable>()
        launch {
            try {
                withTimeout(20) {
                    resolveMindlayerSetupLaunch(
                        queryAction = {
                            try {
                                awaitCancellation()
                            } catch (_: CancellationException) {
                                error("SDK translated cancellation")
                            }
                        },
                        openApp = { opened = true; true },
                    )
                }
            } catch (error: Throwable) {
                result.complete(error)
            }
        }
        assertTrue(result.await() is CancellationException)
        assertFalse(opened)
    }

    @Test
    fun `diagnostic sink failure does not prevent recovery`() = runTest {
        assertEquals(
            ModelSetupLaunchOutcome.OPENED_APP,
            resolveMindlayerSetupLaunch(
                queryAction = { error("Missing action") },
                openApp = { true },
                onFailure = { error("Diagnostics unavailable") },
            ),
        )
    }
}
