package com.adsamcik.starlitcoffee.data.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.adsamcik.starlitcoffee.data.brewing.session.BrewSessionRuntime
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionEventId
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionId
import dev.tracebox.Tracebox
import dev.tracebox.api.LogTemplate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** Internal alarm intent. No screen, global timer or notification action owns progression. */
class BrewDeadlineReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_BREW_DEADLINE) return
        val values = listOf(EXTRA_SESSION, EXTRA_STAGE, EXTRA_TOKEN, EXTRA_EFFECT)
            .map { intent.getStringExtra(it)?.takeIf(String::isNotBlank) }
        if (values.any { it == null }) return
        val session = requireNotNull(values[0])
        val stage = requireNotNull(values[1])
        val token = requireNotNull(values[2])
        val effect = requireNotNull(values[3])
        val due = intent.getLongExtra(EXTRA_DUE, -1L).takeIf { it >= 0L } ?: return
        if (intent.data != brewAlarmIdentity(SessionId(session), token)) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withTimeout(RECEIVER_TIMEOUT_MILLIS) {
                    val runtime = BrewSessionRuntime.create(context.applicationContext)
                    runtime.coordinator.reconcileDeadline(SessionId(session), stage, token, due,
                        SessionEventId("deadline:$effect"), useMonotonicClock = true)
                    runtime.publishBackgroundStatus(SessionId(session))
                }
            } catch (error: Exception) {
                Tracebox.log.error(error, LogTemplate.of("Brew alarm reconciliation deferred to recovery work"))
            } finally { pending.finish() }
        }
    }

    private companion object { const val RECEIVER_TIMEOUT_MILLIS = 8_000L }
}
