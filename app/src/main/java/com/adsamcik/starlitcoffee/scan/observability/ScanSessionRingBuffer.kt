package com.adsamcik.starlitcoffee.scan.observability

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object ScanSessionRingBuffer {

    private const val PREFS_NAME = "scan_session_history"
    private const val KEY_SESSIONS = "sessions"
    private const val MAX_SESSIONS = 10

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    private val prettyJson = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun save(context: Context, summary: ScanSessionSummary) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = getAll(context).toMutableList()
        existing.add(0, summary)
        val trimmed = existing.take(MAX_SESSIONS)
        prefs.edit {
            putString(KEY_SESSIONS, json.encodeToString(trimmed))
        }
    }

    fun getAll(context: Context): List<ScanSessionSummary> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_SESSIONS, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<ScanSessionSummary>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { remove(KEY_SESSIONS) }
    }

    fun getForReport(context: Context, count: Int = 5): String {
        val sessions = getAll(context).take(count)
        return if (sessions.isEmpty()) {
            "No recent scan sessions."
        } else {
            prettyJson.encodeToString(sessions)
        }
    }
}
