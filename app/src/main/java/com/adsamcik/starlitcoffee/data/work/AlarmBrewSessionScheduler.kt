package com.adsamcik.starlitcoffee.data.work

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.core.net.toUri
import androidx.work.WorkManager
import com.adsamcik.starlitcoffee.domain.brewing.session.LongSessionScheduler
import com.adsamcik.starlitcoffee.domain.brewing.session.LongSessionWorkCanceller
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionEffectId
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionId
import com.adsamcik.starlitcoffee.domain.brewing.session.StageInstanceId

/** AlarmManager wakes the existing owner; WorkManager remains an independent recovery prompt. */
// Checked synchronous journal writes are required to report storage failure before acknowledging effects.
@SuppressLint("UseKtx")
class AlarmBrewSessionScheduler(context: Context) : LongSessionScheduler, LongSessionWorkCanceller {
    private val appContext = context.applicationContext
    private val alarms = appContext.getSystemService(AlarmManager::class.java)
    private val journal = appContext.getSharedPreferences("brew_alarm_identities", Context.MODE_PRIVATE)
    private val recovery = WorkManagerLongSessionScheduler(WorkManager.getInstance(appContext),
        LongBrewCompletionWorker::class.java)

    override fun schedule(sessionId: SessionId, stageInstanceId: StageInstanceId, scheduleToken: String,
        dueAtWallClockMillis: Long, effectId: SessionEffectId) {
        recovery.schedule(sessionId, stageInstanceId, scheduleToken, dueAtWallClockMillis, effectId)
        val identity = brewAlarmIdentity(sessionId, scheduleToken)
        val intent = Intent(appContext, BrewDeadlineReceiver::class.java).apply {
            action = ACTION_BREW_DEADLINE
            data = identity
            putExtra(EXTRA_SESSION, sessionId.value)
            putExtra(EXTRA_STAGE, stageInstanceId.persistentKey)
            putExtra(EXTRA_TOKEN, scheduleToken)
            putExtra(EXTRA_DUE, dueAtWallClockMillis)
            putExtra(EXTRA_EFFECT, effectId.value)
        }
        synchronized(JOURNAL_LOCK) {
            val identities = journal.getStringSet(sessionId.value, emptySet()).orEmpty() + identity.toString()
            check(journal.edit().putStringSet(sessionId.value, identities).commit())
            val pending = PendingIntent.getBroadcast(appContext, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val delay = LongSessionWork.initialDelayMillis(dueAtWallClockMillis, System.currentTimeMillis())
            val uptime = SystemClock.elapsedRealtime()
            val trigger = if (uptime > Long.MAX_VALUE - delay) Long.MAX_VALUE else uptime + delay
            try {
                if (canSchedulePreciseBrewAlarms(appContext)) {
                    alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pending)
                } else alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pending)
            } catch (_: SecurityException) {
                // A permission revocation between preflight and scheduling keeps the fallback intact.
                alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pending)
            }
        }
    }

    override fun cancel(sessionId: SessionId, scheduleToken: String, effectId: SessionEffectId) {
        recovery.cancel(sessionId, scheduleToken, effectId)
        synchronized(JOURNAL_LOCK) {
            val identity = brewAlarmIdentity(sessionId, scheduleToken).toString()
            cancelIdentity(identity)
            val identities = journal.getStringSet(sessionId.value, emptySet()).orEmpty() - identity
            check(journal.edit().putStringSet(sessionId.value, identities).commit())
        }
    }

    override fun cancelAllForSession(sessionId: SessionId, effectId: SessionEffectId) {
        recovery.cancelAllForSession(sessionId, effectId)
        synchronized(JOURNAL_LOCK) {
            journal.getStringSet(sessionId.value, emptySet()).orEmpty().forEach(::cancelIdentity)
            check(journal.edit().remove(sessionId.value).commit())
        }
    }

    private fun cancelIdentity(identity: String) {
        val intent = Intent(appContext, BrewDeadlineReceiver::class.java).apply {
            action = ACTION_BREW_DEADLINE
            data = identity.toUri()
        }
        PendingIntent.getBroadcast(appContext, 0, intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let {
            alarms.cancel(it)
            it.cancel()
        }
    }

    private companion object { val JOURNAL_LOCK = Any() }
}

fun canSchedulePreciseBrewAlarms(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

internal fun brewAlarmIdentity(sessionId: SessionId, token: String): Uri = Uri.Builder()
    .scheme("starlitcoffee").authority("brew-deadline").appendPath(sessionId.value).appendPath(token).build()

internal const val ACTION_BREW_DEADLINE = "com.adsamcik.starlitcoffee.BREW_DEADLINE"
internal const val EXTRA_SESSION = "brewSession"
internal const val EXTRA_STAGE = "brewStage"
internal const val EXTRA_TOKEN = "brewToken"
internal const val EXTRA_DUE = "brewDue"
internal const val EXTRA_EFFECT = "brewEffect"
