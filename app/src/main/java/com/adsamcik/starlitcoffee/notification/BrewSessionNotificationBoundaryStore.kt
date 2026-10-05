package com.adsamcik.starlitcoffee.notification

import android.content.Context

/** Durable lifecycle boundary shared by the live screen and notification worker. */
internal class BrewSessionNotificationBoundaryStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun markBackgrounded(
        sessionId: String,
        wallClockMillis: Long = System.currentTimeMillis(),
    ) {
        if (sessionId.isBlank()) return
        preferences.edit().putLong(key(sessionId), wallClockMillis).apply()
    }

    fun backgroundedAtWallClockMillis(sessionId: String): Long? {
        if (sessionId.isBlank()) return null
        val key = key(sessionId)
        return if (preferences.contains(key)) preferences.getLong(key, 0L) else null
    }

    private fun key(sessionId: String): String = "$BACKGROUND_KEY_PREFIX$sessionId"

    private companion object {
        const val PREFERENCES_NAME = "brew_session_notification_boundaries"
        const val BACKGROUND_KEY_PREFIX = "backgrounded_at:"
    }
}
