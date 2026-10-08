package com.adsamcik.starlitcoffee.notification

import androidx.core.app.NotificationCompat
import com.adsamcik.starlitcoffee.R

/** Applies the single, brand-consistent small icon used by every notification. */
internal fun NotificationCompat.Builder.withStarlitSmallIcon(): NotificationCompat.Builder =
    setSmallIcon(R.drawable.ic_notification_starlit)
