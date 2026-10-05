package com.adsamcik.starlitcoffee.ui.screen

import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.model.BrewVibrationTheme
import com.adsamcik.starlitcoffee.data.work.canSchedulePreciseBrewAlarms
import com.adsamcik.starlitcoffee.data.work.enqueueBrewClockRecovery
import com.adsamcik.starlitcoffee.notification.NotificationChannels

/** Relevant only beside a real running clock, with no permanent setup requirement. */
@Composable
internal fun BrewReminderAccess(theme: BrewVibrationTheme) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var access by remember(theme) { mutableStateOf(brewReminderAccess(context, theme)) }
    DisposableEffect(lifecycle, context, theme) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val refreshed = brewReminderAccess(context, theme)
                if (refreshed != access) enqueueBrewClockRecovery(context)
                access = refreshed
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    if (!access.notifications) {
        Text(stringResource(R.string.msg_brew_notifications_disabled))
        TextButton(onClick = {
            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        }) { Text(stringResource(R.string.action_allow_brew_notifications)) }
    } else if (!access.precise && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Text(stringResource(R.string.msg_brew_reminders_may_be_late))
        TextButton(onClick = {
            context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                "package:${context.packageName}".toUri()))
        }) { Text(stringResource(R.string.action_allow_precise_brew_reminders)) }
    }
}

private data class BrewReminderAccessState(val notifications: Boolean, val precise: Boolean)

private fun brewReminderAccess(context: android.content.Context, theme: BrewVibrationTheme): BrewReminderAccessState {
    val channel = context.getSystemService(NotificationManager::class.java)
        .getNotificationChannel(NotificationChannels.brewAlertsId(theme))
    return BrewReminderAccessState(NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        channel?.importance != NotificationManager.IMPORTANCE_NONE, canSchedulePreciseBrewAlarms(context))
}
