package com.adsamcik.starlitcoffee.notification

private const val MINUTE_MILLIS = 60_000L
private const val SECOND_MILLIS = 1_000L
internal const val BREW_NOTIFICATION_SECONDS_THRESHOLD_MILLIS = 3 * MINUTE_MILLIS

/** Compact units avoid confusing a duration with a time of day. */
internal fun formatBrewNotificationTime(timeMillis: Long, includeSeconds: Boolean): String {
    val seconds = (timeMillis.coerceAtLeast(0L) / SECOND_MILLIS)
        .let { if (timeMillis > 0L && includeSeconds) it.coerceAtLeast(1L) else it }
    val hours = seconds / 3_600L
    val minutes = seconds / 60L % 60L
    return buildString {
        if (hours > 0L) append("${hours}h")
        if (minutes > 0L || !includeSeconds) append("${minutes}m")
        if (includeSeconds) append("${seconds % 60L}s")
    }
}

/** Refresh at the next displayed boundary, including the transition from 3m to 2m59s. */
internal fun brewNotificationRefreshDelay(timeMillis: Long, countdown: Boolean): Long {
    val interval = if (countdown && timeMillis < BREW_NOTIFICATION_SECONDS_THRESHOLD_MILLIS) {
        SECOND_MILLIS
    } else {
        MINUTE_MILLIS
    }
    val remainder = timeMillis.coerceAtLeast(0L) % interval
    return if (countdown) remainder + 1L else interval - remainder
}
