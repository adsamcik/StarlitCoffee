package com.adsamcik.starlitcoffee

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.adsamcik.starlitcoffee.navigation.StarlitNavHost
import com.adsamcik.starlitcoffee.notification.DeepLinkBus
import com.adsamcik.starlitcoffee.ui.theme.StarlitCoffeeTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        consumeDeepLinkExtras(intent)
        setContent {
            StarlitCoffeeTheme {
                StarlitNavHost()
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

    private fun consumeDeepLinkExtras(intent: Intent?) {
        if (intent == null) return
        val brewLogId = intent.getLongExtra(EXTRA_BREW_LOG_ID, -1L)
        if (brewLogId > 0L) {
            DeepLinkBus.postBrewLogDetail(brewLogId)
            // Clear the extra so a configuration change (which re-delivers the
            // original intent) doesn't re-trigger navigation.
            intent.removeExtra(EXTRA_BREW_LOG_ID)
        }
        if (intent.getBooleanExtra(EXTRA_OPEN_BAG_ANALYSIS, false)) {
            DeepLinkBus.postBagAnalysisReady()
            intent.removeExtra(EXTRA_OPEN_BAG_ANALYSIS)
        }
    }

    companion object {
        const val EXTRA_BREW_LOG_ID = "starlit.extra.BREW_LOG_ID"
        const val EXTRA_OPEN_BAG_ANALYSIS = "starlit.extra.OPEN_BAG_ANALYSIS"

        /**
         * Builds an [Intent] that launches the app and deep-links to the brew
         * log detail for [brewLogId]. Used by the rating-reminder notification.
         */
        fun buildBrewLogDetailIntent(context: Context, brewLogId: Long): Intent =
            Intent(context, MainActivity::class.java).apply {
                putExtra(EXTRA_BREW_LOG_ID, brewLogId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

        /**
         * Builds an [Intent] that launches the app and opens the analyzed
         * coffee-bag form. Used by the "bag analysis complete" notification
         * posted when the user sent the AI extraction to the background.
         */
        fun buildBagAnalysisIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java).apply {
                putExtra(EXTRA_OPEN_BAG_ANALYSIS, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
    }
}

