package com.adsamcik.starlitcoffee.util

import android.content.Context
import android.content.Intent
import com.adsamcik.starlitcoffee.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull

/** A missing or expired setup action falls back to Mindlayer; cancellation never opens another app. */
internal suspend fun resolveMindlayerSetupLaunch(
    queryAction: suspend () -> (() -> Unit)?,
    openApp: suspend () -> Boolean,
    timeoutMillis: Long = 10_000L,
    onFailure: (Exception) -> Unit = {},
): ModelSetupLaunchOutcome {
    var failed = false
    var action: (() -> Unit)? = null
    try {
        val queried = withTimeoutOrNull(timeoutMillis) {
            action = queryAction()
            true
        }
        failed = queried != true
    } catch (error: Exception) {
        failed = true
        recordSetupFailure(error, onFailure)
    }
    currentCoroutineContext().ensureActive()
    if (action != null) {
        try {
            checkNotNull(action).invoke()
            return ModelSetupLaunchOutcome.OPENED_SETUP
        } catch (error: Exception) {
            failed = true
            recordSetupFailure(error, onFailure)
        }
    }
    currentCoroutineContext().ensureActive()
    return try {
        when {
            openApp() -> ModelSetupLaunchOutcome.OPENED_APP
            failed -> ModelSetupLaunchOutcome.FAILED
            else -> ModelSetupLaunchOutcome.UNAVAILABLE
        }
    } catch (error: Exception) {
        recordSetupFailure(error, onFailure)
        ModelSetupLaunchOutcome.FAILED
    }
}

/** The SDK may translate caller cancellation to an ordinary error; neither case may launch fallback. */
private suspend fun recordSetupFailure(error: Exception, onFailure: (Exception) -> Unit) {
    currentCoroutineContext().ensureActive()
    if (error is CancellationException) throw error
    runCatching { onFailure(error) }
}

/** Uses launch intents for installed, visible Mindlayer apps rather than internal component names. */
internal fun openInstalledMindlayerApp(context: Context): Boolean {
    val packages = buildList {
        add(MindlayerInstallLink.PACKAGE_NAME)
        if (BuildConfig.DEBUG) {
            add("com.adsamcik.mindlayer.debug")
            add("com.adsamcik.mindlayer.service.debug")
        }
    }
    val intent = packages.firstNotNullOfOrNull(context.packageManager::getLaunchIntentForPackage) ?: return false
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    return true
}
