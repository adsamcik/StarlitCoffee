package com.adsamcik.starlitcoffee

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.adsamcik.starlitcoffee.navigation.StarlitNavHost
import com.adsamcik.starlitcoffee.service.RatingReminderWorker
import com.adsamcik.starlitcoffee.ui.theme.StarlitCoffeeTheme

class MainActivity : ComponentActivity() {

    var pendingRatingBrewId by mutableStateOf<Long?>(null)
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleRatingIntent(intent)
        setContent {
            StarlitCoffeeTheme {
                StarlitNavHost()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleRatingIntent(intent)
    }

    private fun handleRatingIntent(intent: Intent?) {
        val logId = intent?.getLongExtra(RatingReminderWorker.EXTRA_RATE_BREW_ID, -1L) ?: -1L
        if (logId > 0L) {
            pendingRatingBrewId = logId
        }
    }

    fun clearPendingRating() {
        pendingRatingBrewId = null
    }
}
