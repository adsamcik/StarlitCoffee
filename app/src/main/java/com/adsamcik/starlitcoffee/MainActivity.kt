package com.adsamcik.starlitcoffee

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.adsamcik.starlitcoffee.data.work.BagReviewContext
import com.adsamcik.starlitcoffee.data.work.decodeBagReviewContext
import com.adsamcik.starlitcoffee.data.work.encodeBagReviewContext
import com.adsamcik.starlitcoffee.navigation.StarlitNavHost
import com.adsamcik.starlitcoffee.notification.DeepLinkBus
import com.adsamcik.starlitcoffee.ui.adaptive.ProvideWindowWidthClass
import com.adsamcik.starlitcoffee.ui.theme.StarlitCoffeeTheme

class MainActivity : ComponentActivity() {

    /** Read by BrewTimerScreen to replace full controls with a timer-only PiP view. */
    var isShowingBrewPictureInPicture by mutableStateOf(false)
        private set
    private var brewPictureInPictureAvailable = false
    private var notificationPermissionRequested = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        consumeDeepLinkExtras(intent)
        setContent {
            StarlitCoffeeTheme {
                ProvideWindowWidthClass {
                    StarlitNavHost()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // The Activity is reused (launchMode=standard + singleTop-style nav),
        // so capture any deep-link extras the relaunch may carry.
        setIntent(intent)
        consumeDeepLinkExtras(intent)
    }

    /** Enables PiP only while a running brew timer is the visible screen. */
    fun setBrewPictureInPictureAvailable(available: Boolean) {
        brewPictureInPictureAvailable = available
        if (available) requestBrewNotificationPermissionIfNeeded()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val params = android.app.PictureInPictureParams.Builder()
                .setAspectRatio(Rational(1, 1))
                .apply {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        setAutoEnterEnabled(available)
                    }
                }
                .build()
            setPictureInPictureParams(params)
        }
    }

    /** Requests the platform alert permission when a durable brew begins. */
    fun requestBrewNotificationPermission() {
        requestBrewNotificationPermissionIfNeeded()
    }

    private fun requestBrewNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
            notificationPermissionRequested ||
            androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        notificationPermissionRequested = true
        requestPermissions(
            arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
            REQUEST_BREW_NOTIFICATION_PERMISSION,
        )
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (android.os.Build.VERSION.SDK_INT in android.os.Build.VERSION_CODES.O..
            android.os.Build.VERSION_CODES.R &&
            brewPictureInPictureAvailable &&
            !isInPictureInPictureMode
        ) {
            enterPictureInPictureMode(
                android.app.PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(1, 1))
                    .build(),
            )
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isShowingBrewPictureInPicture = isInPictureInPictureMode
    }

    private fun consumeDeepLinkExtras(intent: Intent?) {
        if (intent == null) return
        val brewLogId = intent.getLongExtra(EXTRA_BREW_LOG_ID, -1L)
        if (brewLogId > 0L) {
            DeepLinkBus.postBrewLogDetail(brewLogId)
            // Clear the extra so a configuration change (which re-delivers the
            // original intent) doesn't re-trigger navigation.
            intent.removeExtra(EXTRA_BREW_LOG_ID)
        }
        val brewSessionId = intent.getStringExtra(EXTRA_BREW_SESSION_ID)
        if (!brewSessionId.isNullOrBlank()) {
            DeepLinkBus.postBrewSession(brewSessionId)
            // The original intent can be delivered again after configuration
            // changes, so consume this one-shot navigation request here.
            intent.removeExtra(EXTRA_BREW_SESSION_ID)
        }

        if (intent.getBooleanExtra(EXTRA_OPEN_BAG_ANALYSIS, false)) {
            val workId = intent.getStringExtra(EXTRA_BAG_ANALYSIS_WORK_ID)
            if (!workId.isNullOrBlank()) {
                DeepLinkBus.postBagAnalysisReady(
                    workId,
                    decodeBagReviewContext(intent.getStringExtra(EXTRA_BAG_REVIEW_CONTEXT)),
                )
            }
            intent.removeExtra(EXTRA_OPEN_BAG_ANALYSIS)
            intent.removeExtra(EXTRA_BAG_ANALYSIS_WORK_ID)
            intent.removeExtra(EXTRA_BAG_REVIEW_CONTEXT)
        }
    }

    companion object {
        const val EXTRA_BREW_LOG_ID = "starlit.extra.BREW_LOG_ID"
        const val EXTRA_BREW_SESSION_ID = "starlit.extra.BREW_SESSION_ID"
        const val EXTRA_OPEN_BAG_ANALYSIS = "starlit.extra.OPEN_BAG_ANALYSIS"
        const val EXTRA_BAG_ANALYSIS_WORK_ID = "starlit.extra.BAG_ANALYSIS_WORK_ID"
        private const val REQUEST_BREW_NOTIFICATION_PERMISSION = 4_201
        const val EXTRA_BAG_REVIEW_CONTEXT = "starlit.extra.BAG_REVIEW_CONTEXT"

        fun buildOpenBrewIntent(context: Context): Intent = Intent(context, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
        }

        /** Builds an intent that returns to one exact durable brew session. */
        fun buildBrewSessionIntent(context: Context, sessionId: String): Intent =
            Intent(context, MainActivity::class.java).apply {
                putExtra(EXTRA_BREW_SESSION_ID, sessionId)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                )
            }

        /**
         * Builds an [Intent] that launches the app and deep-links to the brew
         * log detail for [brewLogId]. Used by the rating-reminder notification.
         */
        fun buildBrewLogDetailIntent(context: Context, brewLogId: Long): Intent =
            Intent(context, MainActivity::class.java).apply {
                putExtra(EXTRA_BREW_LOG_ID, brewLogId)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                )
            }

        /**
         * Builds an [Intent] that launches the app and opens the analyzed
         * coffee-bag form. Used by the "bag analysis complete" notification
         * posted when the user sent the AI extraction to the background.
         */
        fun buildBagAnalysisIntent(
            context: Context,
            workId: String,
            reviewContext: BagReviewContext? = null,
        ): Intent =
            Intent(context, MainActivity::class.java).apply {
                data = Uri.parse("starlitcoffee://bag-analysis/$workId")
                putExtra(EXTRA_OPEN_BAG_ANALYSIS, true)
                putExtra(EXTRA_BAG_ANALYSIS_WORK_ID, workId)
                encodeBagReviewContext(reviewContext)?.let { encoded ->
                    putExtra(EXTRA_BAG_REVIEW_CONTEXT, encoded)
                }
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                )
            }
    }
}
